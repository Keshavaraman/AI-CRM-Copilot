# CODEBASE.md — File-by-File Reference

> Every source file in the project: what it is, what it does, and why it exists.
> Stack: **Spring Boot 3.5 · Java 21 · PostgreSQL · Redis · Caffeine · JJWT 0.12.x · Flyway · Lombok**

---

## Table of Contents

1. [Entry Point](#1-entry-point)
2. [Configuration — Datasource / JPA](#2-configuration--datasource--jpa)
3. [Configuration — Cache](#3-configuration--cache)
4. [Configuration — Redis](#4-configuration--redis)
5. [Configuration — Security](#5-configuration--security)
6. [Multi-Tenancy Core](#6-multi-tenancy-core)
7. [Platform Layer](#7-platform-layer)
8. [Security Layer](#8-security-layer)
9. [CRM Domain — Contacts](#9-crm-domain--contacts)
10. [CRM Domain — Tickets](#10-crm-domain--tickets)
11. [Dynamic Modules](#11-dynamic-modules)
12. [Common / Shared](#12-common--shared)
13. [Resources & SQL](#13-resources--sql)

---

## 1. Entry Point

### `CrmaiApplication.java`

**Package:** `com.keshava.crmai`

Spring Boot bootstrap class. The `main()` method starts the application context.

**Key implementation:**
```java
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
```
Both JPA auto-configurations are excluded because the project defines **two separate EntityManagerFactory beans** manually — one for `platform_db` and one for the tenant routing datasource. If Spring Boot's auto-config ran, it would conflict with the manual setup.

---

## 2. Configuration — Datasource / JPA

### `config/datasource/PlatformJpaConfig.java`

**Package:** `com.keshava.crmai.config.datasource`

Creates the **primary** JPA stack for `platform_db` — the global database that stores organisations, users, and credentials.

**What is implemented:**
- `@Primary` `DataSourceProperties` bean reading `spring.datasource.*` from `application.yml`
- `HikariDataSource` bean (`platformDataSource`) with connection pool
- `LocalContainerEntityManagerFactoryBean` (`platformEntityManagerFactory`) scanning `platform.entity`
- `JpaTransactionManager` (`platformTransactionManager`)
- `@EnableJpaRepositories` bound to `platform.repository` package
- `CamelCaseToUnderscoresNamingStrategy` so Java camelCase fields map to SQL snake_case columns automatically

**Why separate from tenant JPA:** Platform entities (Organisation, PlatformUser, OrganizationMember, TenantDatasource) live in a completely separate database and must never share a transaction with tenant data.

---

### `config/datasource/TenantJpaConfig.java`

**Package:** `com.keshava.crmai.config.datasource`

Creates the **tenant** JPA stack backed by `TenantDataSourceRouter` — all CRM, security, and module entities use this.

**What is implemented:**
- `TenantDataSourceRouter` bean (the routing datasource itself)
- `LocalContainerEntityManagerFactoryBean` (`tenantEntityManagerFactory`) scanning `security.entity`, `crm.contact.entity`, `crm.ticket.entity`, `module.entity`, `common.entity`
- `JpaTransactionManager` (`tenantTransactionManager`)
- `@EnableJpaRepositories` bound to `security.repository`, `crm`, `module.repository`
- `hibernate.boot.allow_jdbc_metadata_access=false` — prevents Hibernate from trying to probe the router for JDBC metadata at startup (the router has no connections yet at that point); dialect is set explicitly as a result
- `CamelCaseToUnderscoresNamingStrategy` for consistent snake_case column mapping

**Rule:** All services that use tenant repositories must annotate transactions with `@Transactional(transactionManager = "tenantTransactionManager")`.

---

## 3. Configuration — Cache

### `config/cache/CacheConfig.java`

**Package:** `com.keshava.crmai.config.cache`

Configures the **L1 in-process Caffeine cache** and registers the tenant-aware key generator as the default for all `@Cacheable` annotations.

**What is implemented:**
- `@EnableCaching` activates Spring's cache abstraction
- `CaffeineCacheManager` with three named caches:
  - `profilePermissions` — caches a user's resolved RBAC permissions
  - `moduleMetadata` — caches `DynamicModule` definitions
  - `fieldMetadata` — caches `DynamicField` definitions per module
- Max 1000 entries, TTL 10 minutes per cache
- `@Bean("keyGenerator")` registers `TenantAwareKeyGenerator` as the **default** key generator so every `@Cacheable` call is automatically tenant-scoped without extra annotation

---

### `config/cache/TenantAwareKeyGenerator.java`

**Package:** `com.keshava.crmai.config.cache`

Custom `KeyGenerator` that **prefixes every Caffeine cache key with the current tenant ID**.

**What is implemented:**
- Implements Spring's `KeyGenerator` interface
- Reads `TenantContext.getCurrentTenant()` to get the active tenant
- Returns `"{tenantId}:{SimpleKeyGenerator result}"` — e.g. `"acme:SimpleKey [uuid-123]"`
- Falls back to prefix `"shared"` when called outside a request thread (e.g., background jobs)

**Why this matters:** Caffeine is JVM-level. Without tenant prefixing, `profilePermissions[uuid-123]` from tenant `acme` would collide with the same key from tenant `globex` on the same server. This generator eliminates cross-tenant cache pollution at zero cost to call sites — no annotation changes needed.

---

### `config/cache/CacheInvalidationMessage.java`

**Package:** `com.keshava.crmai.config.cache`

Plain Java record: the payload published to the Redis invalidation channel.

```java
record CacheInvalidationMessage(String cacheName, String tenantId, String key)
```

`key = "*"` signals a full cache clear for the tenant. Any other value is treated as a specific entry to evict (`{tenantId}:{key}`).

---

### `config/cache/CacheInvalidationPublisher.java`

**Package:** `com.keshava.crmai.config.cache`

Publishes Caffeine cache invalidation events to the Redis pub/sub channel `"cache:invalidation"` so **all running server instances** receive and act on them.

**What is implemented:**
- `publishEvict(cacheName, tenantId, key)` — evict a single entry on all servers
- `publishClear(cacheName, tenantId)` — clear all tenant entries from a cache on all servers
- Uses `StringRedisTemplate.convertAndSend()` with JSON-serialised `CacheInvalidationMessage`
- Failures are logged as warnings and do not propagate (Redis unavailability must not block writes)

**Usage:** Call after any write that invalidates cached data, e.g. after updating a profile's permissions: `publisher.publishClear("profilePermissions", tenantId)`.

---

### `config/cache/CacheInvalidationListener.java`

**Package:** `com.keshava.crmai.config.cache`

Receives invalidation messages from Redis and evicts the matching entry from **this server's local Caffeine cache**.

**What is implemented:**
- `handleMessage(String message)` — entry point called by `MessageListenerAdapter` (registered in `RedisConfig`)
- Deserialises the JSON payload into `CacheInvalidationMessage`
- Calls `cache.clear()` for wildcard key (`*`) or `cache.evict(tenantId + ":" + key)` for specific keys
- Errors are caught and logged; they must not crash the listener thread

**Flow:** Publisher (any server) → Redis channel → all server instances receive → each server evicts its own Caffeine entry.

---

## 4. Configuration — Redis

### `config/redis/RedisConfig.java`

**Package:** `com.keshava.crmai.config.redis`

Configures all Redis infrastructure: the main template, and the pub/sub listener container.

**What is implemented:**

**`RedisMessageListenerContainer`** — Spring's async Redis subscription container:
- Connected to Redis via `RedisConnectionFactory`
- Subscribes `CacheInvalidationListener` to the `"cache:invalidation"` channel via `MessageListenerAdapter`
- The adapter maps incoming messages to `CacheInvalidationListener.handleMessage(String)` automatically handling byte→String conversion

**`RedisTemplate<String, Object>`** — general-purpose template for storing structured data (sessions, AI summaries, dashboard metrics):
- Keys serialised as plain strings via `StringRedisSerializer`
- Values serialised as JSON via `Jackson2JsonRedisSerializer` with `JavaTimeModule` and `activateDefaultTyping` (preserves Java type information so objects can be deserialised back to the correct class)
- Hash keys and values use the same serialisers

---

## 5. Configuration — Security

### `config/security/SecurityConfig.java`

**Package:** `com.keshava.crmai.config.security`

Defines the Spring Security filter chain — authentication rules, session policy, CORS, and filter ordering.

**What is implemented:**
- CSRF disabled (stateless JWT API, no browser form submissions)
- CORS open (`allowedOriginPatterns("*")`) — tighten per-environment in production
- `SessionCreationPolicy.STATELESS` — no `HttpSession` is ever created; every request is self-contained
- Public endpoints: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /actuator/health`
- All other requests require a valid JWT
- Filter chain order:
  1. `TenantResolutionFilter` (@Order 1) — runs before Spring Security filters
  2. `JwtAuthFilter` (@Order 2) — runs immediately after
- `DaoAuthenticationProvider` wired with `BCryptPasswordEncoder` and `UserDetailsServiceImpl`
- `@EnableMethodSecurity` enables `@PreAuthorize` on service methods (for future RBAC enforcement)

---

## 6. Multi-Tenancy Core

### `multitenancy/TenantContext.java`

**Package:** `com.keshava.crmai.multitenancy`

Thread-local holder for the current request's tenant ID.

**What is implemented:**
- `private static final ThreadLocal<String> TENANT_ID` — one value per request thread
- `setCurrentTenant(String)` / `getCurrentTenant()` / `clear()`
- `clear()` must always be called in `finally` to prevent thread-pool leaks

**Why ThreadLocal:** The tenant ID set in the filter layer must be readable by JPA at the framework level without passing it through every method parameter. ThreadLocal achieves this zero-intrusion requirement.

---

### `multitenancy/TenantDataSourceRouter.java`

**Package:** `com.keshava.crmai.multitenancy`

The heart of database-per-tenant multi-tenancy. Extends Spring's `AbstractRoutingDataSource` to route every JPA call to the correct tenant database transparently.

**What is implemented:**
- `ConcurrentHashMap<String, DataSource> tenantDataSources` — in-memory registry of all active tenant pools
- `determineCurrentLookupKey()` — returns `TenantContext.getCurrentTenant()` (null if no tenant set; handled gracefully at startup)
- `determineTargetDataSource()` — if key is null → `TenantNotFoundException`; if key not in map → `TenantNotFoundException`; otherwise returns the HikariCP pool for that tenant
- `addTenant(String, DataSource)` — synchronized; re-calls `afterPropertiesSet()` to make the new datasource live immediately (used at startup and on new org onboarding)
- `hasTenant(String)` — used by `TenantResolutionFilter` to validate the header value

**Key design:** Returning null from `determineCurrentLookupKey()` (instead of throwing) allows Hibernate to fall back gracefully during EMF initialisation at startup, when no tenant context exists yet.

---

### `multitenancy/TenantDataSourceManager.java`

**Package:** `com.keshava.crmai.multitenancy`

Orchestrates tenant lifecycle: loads all active tenants at startup, applies Flyway migrations per tenant, and registers them in the router.

**What is implemented:**
- `@EventListener(ApplicationReadyEvent.class) loadAllTenants()` — runs after the full Spring context is up; queries `platform_db.tenant_datasources` for all `active=true` rows; for each: builds HikariCP pool → runs Flyway migrations → registers in router
- `buildDataSource(TenantDatasource)` — creates a `HikariDataSource` from the stored JDBC config
- `applyMigrations(DataSource)` — runs `Flyway.configure().dataSource(ds).locations("classpath:db/migration/tenant").load().migrate()` programmatically
- `onboardTenant(TenantDatasource)` — public method called during new organisation signup: applies migrations to a freshly created DB and adds it to the live router

**Why ApplicationReadyEvent:** Using this event (instead of `@PostConstruct`) ensures all JPA repositories are fully initialised before we query `tenant_datasources`.

---

## 7. Platform Layer

### `platform/entity/Organization.java`

Represents a tenant organisation in `platform_db`.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `name` | String | Display name |
| `subdomain` | String (unique) | Routing key — matches the `X-Tenant-Id` header value |
| `active` | boolean | `false` = org suspended, all logins blocked |
| `createdAt` | LocalDateTime | Set in `@PrePersist` |

---

### `platform/entity/TenantDatasource.java`

Stores the JDBC connection details for each tenant's dedicated database. Read by `TenantDataSourceManager` at startup.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `organization` | `@ManyToOne Organization` | FK to owning org |
| `dbUrl` | String | Full JDBC URL, e.g. `jdbc:postgresql://localhost:5432/acme_crm` |
| `dbName` | String | DB name only |
| `dbUsername` / `dbPassword` | String | Connection credentials |
| `active` | boolean | `false` = pool closed, org unreachable |

One datasource per org enforced via `UNIQUE (organization_id)` at DB level.

---

### `platform/entity/PlatformUser.java`

Global identity record. **One row per real person**, shared across all organisations they belong to.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Same UUID used in every tenant DB's `users` table |
| `email` | String (unique) | Login identity, globally unique |
| `password` | String | BCrypt hash — **only place passwords are stored** |
| `active` | boolean | `false` = blocked from ALL orgs |

Changing the password here applies instantly to all org logins. Disabling `active` here blocks the user everywhere.

---

### `platform/entity/OrganizationMember.java`

Many-to-many join between `PlatformUser` and `Organization`. Controls which orgs a user can access.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | |
| `platformUser` | `@ManyToOne` | FK to `platform_users` |
| `organization` | `@ManyToOne` | FK to `organizations` |
| `active` | boolean | `false` = user removed from this org only |

Unique constraint on `(platform_user_id, organization_id)` — one membership row per (user, org) pair.

---

### `platform/repository/OrganizationRepository.java`

Spring Data JPA repository for `Organization` (platform JPA).

- `findBySubdomain(String)` — used during login to resolve the org from `X-Tenant-Id`
- `existsBySubdomain(String)` — used during org onboarding to prevent duplicate subdomains

---

### `platform/repository/TenantDatasourceRepository.java`

Spring Data JPA repository for `TenantDatasource` (platform JPA).

- `findAllByActiveTrue()` — loads all active tenant configs at startup
- `findByOrganizationSubdomain(String)` — resolves datasource config for a given tenant

---

### `platform/repository/PlatformUserRepository.java`

Spring Data JPA repository for `PlatformUser` (platform JPA).

- `findByEmail(String)` — used in login step 1 to look up the global user record
- `existsByEmail(String)` — used during user registration

---

### `platform/repository/OrganizationMemberRepository.java`

Spring Data JPA repository for `OrganizationMember` (platform JPA).

- `existsByPlatformUserIdAndOrganizationIdAndActiveTrue(UUID, UUID)` — login step 2: is this user an active member of the requested org?
- `findByPlatformUserIdAndActiveTrue(UUID)` — list all active orgs a user belongs to (for "switch org" UI)
- `findByPlatformUserIdAndOrganizationSubdomain(UUID, String)` — look up a specific membership

---

## 8. Security Layer

### `security/entity/User.java`

Tenant-scoped user record. Implements Spring Security's `UserDetails`.

**What is implemented:**
- `id UUID` — **same UUID as `PlatformUser.id`** (cross-DB identity link, no FK constraint needed)
- `email String` — denormalised from platform_db for display; **not used for auth**
- `profile @ManyToOne Profile` — the user's RBAC profile in this org
- `active boolean`
- **No `password` field** — credentials live only in `platform_db.platform_users`
- `getPassword()` returns `null` (Spring Security does not use it; auth is handled by `AuthService` against `PlatformUser`)
- `getUsername()` returns the email string (Spring Security convention)
- `getAuthorities()` builds `GrantedAuthority` list from the profile's permissions

---

### `security/entity/Profile.java`

RBAC profile. A collection of permissions assigned to users in a tenant.

**What is implemented:**
- `name String` — e.g. "Admin", "Sales Rep", "Read Only"
- `description String`
- `@OneToMany(fetch = EAGER) permissions` — loaded with the profile so permission checks are a single query
- `hasPermission(String moduleName, Action action)` — utility method; returns true if any permission in the list matches the module/action pair (used in service-layer `@PreAuthorize`)

---

### `security/entity/Permission.java`

A single module-level permission entry belonging to a `Profile`.

| Field | Type | Notes |
|---|---|---|
| `profile` | `@ManyToOne Profile` | |
| `moduleName` | String | e.g. `"contacts"`, `"tickets"` |
| `action` | enum | `READ`, `WRITE`, `DELETE`, `ALL` |

---

### `security/entity/SharingRule.java`

Stub entity for record-level access control (not yet enforced in service layer).

| Field | Type | Notes |
|---|---|---|
| `moduleName` | String | Which module the rule applies to |
| `ownerId` | UUID | Record owner |
| `sharedWithId` | UUID | User the record is shared with |
| `accessLevel` | enum | `READ`, `WRITE` |

---

### `security/filter/TenantResolutionFilter.java`

**`@Order(1)`** — runs first, before all Spring Security filters.

**What is implemented:**
- Reads the `X-Tenant-Id` HTTP header from every request
- Returns `400 Bad Request` if header is missing or blank
- Returns `400 Bad Request` if the tenant ID is not registered in `TenantDataSourceRouter` (unknown org)
- Sets `TenantContext.setCurrentTenant(tenantId)` so the routing datasource can use it
- **Always calls `TenantContext.clear()` in `finally`** to prevent thread-pool contamination
- Public paths (`/api/auth/**`, `/actuator/health`) are allowed to bypass the check if needed but still pass through

---

### `security/filter/JwtAuthFilter.java`

**`@Order(2)`** — runs immediately after `TenantResolutionFilter`.

**What is implemented:**
- Skips requests with no `Authorization: Bearer` header (lets them fall through to Spring Security's 401 logic)
- **Blacklist check**: calls `SessionService.isRevoked(token)` before any parsing — immediately rejects tokens that have been logged out
- Extracts `userId` (JWT `sub`) and `tenantId` (JWT claim)
- **Tenant cross-check**: asserts `jwt.tenantId == X-Tenant-Id` header; mismatches return `403 Forbidden` (prevents a user from using a token issued for org A to access org B by changing the header)
- Loads `UserDetails` from tenant DB by UUID via `UserDetailsServiceImpl`
- Validates token signature and expiry with `JwtService.isTokenValid()`
- Sets `SecurityContextHolder` with `UsernamePasswordAuthenticationToken` on success

---

### `security/service/JwtService.java`

Handles all JWT operations using **JJWT 0.12.x API**.

**What is implemented:**
- `generateToken(User, tenantId)` — builds a signed JWT with:
  - `jti`: random UUID (used as blacklist key — short, unique per token)
  - `sub`: `user.getId().toString()` (UUID, not email — stable cross-org identity)
  - claim `email`: for UI display
  - claim `tenantId`: for cross-validation in `JwtAuthFilter`
  - Signed with HMAC-SHA from base64-decoded secret in `application.yml`
- `isTokenValid(token, userDetails)` — verifies signature, expiry, and that `sub` matches the loaded user's UUID
- `extractUserId(token)` — extracts `sub` claim
- `extractTenantId(token)` — extracts `tenantId` claim
- `extractEmail(token)` — extracts `email` claim
- `extractJti(token)` — extracts `jti` (used for blacklisting)
- `extractExpiration(token)` — extracts expiry `Date` (used to compute Redis TTL for the blacklist entry)

---

### `security/service/AuthService.java`

Handles the **three-step login flow** for a multi-org user.

**What is implemented:**

**Step 1 — Verify credentials (platform_db):**
Query `platform_users` by email → check `active` flag → BCrypt-compare submitted password

**Step 2 — Verify org membership (platform_db):**
Resolve org by `X-Tenant-Id` subdomain → check `organization_members` for active membership

**Step 3 — Load tenant context (tenant_db):**
Load the `users` row by UUID (same UUID as `PlatformUser`) → get org-specific profile and permissions

**On success:**
- Calls `jwtService.generateToken()` — token contains `jti`, `sub=userId`, `tenantId`, `email`
- Calls `sessionService.store()` — writes `SessionInfo` to Redis with TTL = JWT expiry; includes IP and User-Agent for audit visibility
- Returns `AuthResponse(token, email, userId, tenantId, expiresIn)`

---

### `security/service/UserDetailsServiceImpl.java`

Spring Security's `UserDetailsService` bridge for the tenant JPA stack.

**What is implemented:**
- `loadUserByUsername(userId)` — `userId` is the JWT `sub` claim (UUID string)
- Parses the UUID string → queries `userRepository.findById(uuid)` on the current tenant DB
- Throws `UsernameNotFoundException` if not found (Spring Security converts this to 401)
- Called by `JwtAuthFilter` on every authenticated request

---

### `security/service/SessionService.java`

Provides **distributed session stickiness** using Redis. Any server instance can check, create, or revoke a session.

**What is implemented:**

**`store(token, SessionInfo)`** — called at login:
- Extracts `jti` and expiry from token
- Stores `SessionInfo` JSON at `session:{tenantId}:{userId}:{jti}` with TTL = remaining token lifetime
- `SessionInfo` contains: userId, email, tenantId, ipAddress, userAgent, loginAt, expiresAt

**`revoke(token)`** — called at logout:
- Writes `"revoked"` to `jwt:blacklist:{jti}` with TTL = remaining token lifetime (auto-expires with the token)
- Deletes the `session:*` key
- **All servers** will reject this token on the next request because every `JwtAuthFilter` checks the blacklist

**`isRevoked(token)`** — called by `JwtAuthFilter` on every request:
- Checks `jwt:blacklist:{jti}` in Redis
- Returns true → token is rejected immediately regardless of signature/expiry validity

---

### `security/controller/AuthController.java`

REST controller for authentication endpoints. All paths under `/api/auth/**` are public (no JWT required to reach them).

**Endpoints:**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/login` | Authenticates user; returns JWT + session metadata |
| `POST` | `/api/auth/logout` | Extracts JWT from `Authorization` header, revokes it in Redis; returns `204 No Content` |

---

### `security/dto/AuthRequest.java`

```java
record AuthRequest(String email, String password)
```

The tenant ID comes from the `X-Tenant-Id` header, not the request body.

---

### `security/dto/AuthResponse.java`

```java
record AuthResponse(String token, String email, String userId, String tenantId, long expiresIn)
```

`userId` is the UUID string that matches `PlatformUser.id` across all org databases.

---

### `security/dto/SessionInfo.java`

```java
record SessionInfo(String userId, String email, String tenantId,
                   String ipAddress, String userAgent,
                   Instant loginAt, Instant expiresAt)
```

Stored in Redis under `session:{tenantId}:{userId}:{jti}`. Useful for admin session listing, audit logs, and forced logout by an administrator.

---

### `security/repository/UserRepository.java`

Spring Data JPA repository for `User` (tenant JPA).

- `findByEmail(String)` — lookup by email (used in some flows)
- Inherits `findById(UUID)` from `JpaRepository` — primary lookup in `UserDetailsServiceImpl`

---

### `security/repository/ProfileRepository.java`

Spring Data JPA repository for `Profile` (tenant JPA).

- Used to load profiles when assigning roles during user invite flow

---

## 9. CRM Domain — Contacts

### `crm/contact/entity/Contact.java`

Tenant-scoped CRM contact. Extends `BaseEntity` (UUID PK, audit timestamps).

| Field | Notes |
|---|---|
| `firstName`, `lastName` | Required |
| `email` | Unique per tenant |
| `phone`, `company`, `jobTitle`, `description` | Optional |
| `ownerId UUID` | References the `User.id` who owns this record |

---

### `crm/contact/repository/ContactRepository.java`

Spring Data JPA repository for `Contact` (tenant JPA).

- `findByEmail(String)` — duplicate check
- `findByOwnerId(UUID)` — contacts owned by a user
- `@Query` JPQL `search(String keyword, Pageable)` — full-text search across firstName, lastName, email, company using `LOWER(CONCAT(...)) LIKE %keyword%`

---

### `crm/contact/service/ContactService.java`

Business logic for contacts. All methods are `@Transactional(transactionManager = "tenantTransactionManager")`.

**Methods:**
- `findAll(Pageable)` — paginated list
- `search(String keyword, Pageable)` — delegates to repository JPQL search
- `findById(UUID)` — throws `AppException(404)` if not found
- `create(ContactDto)` — validates no duplicate email, saves
- `update(UUID, ContactDto)` — partial update, saves
- `delete(UUID)` — hard delete

---

### `crm/contact/controller/ContactController.java`

REST controller. All paths under `/api/contacts`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/contacts?search=` | Paginated list; uses search if `search` param provided |
| `GET` | `/api/contacts/{id}` | Single contact |
| `POST` | `/api/contacts` | Create |
| `PUT` | `/api/contacts/{id}` | Update |
| `DELETE` | `/api/contacts/{id}` | Delete |

---

## 10. CRM Domain — Tickets

### `crm/ticket/entity/Ticket.java`

Tenant-scoped support ticket. Extends `BaseEntity`.

| Field | Notes |
|---|---|
| `subject` | Required |
| `description` | Optional |
| `status` | Enum: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `priority` | Enum: `LOW`, `MEDIUM`, `HIGH`, `URGENT` |
| `assigneeId UUID` | Assigned agent (references `User.id`) |
| `contactId UUID` | Linked contact (`@ManyToOne Contact`) |

---

### `crm/ticket/repository/TicketRepository.java`

Spring Data JPA repository for `Ticket` (tenant JPA).

- `findByStatus(TicketStatus, Pageable)` — filter by status with pagination
- `findByAssigneeId(UUID)` — all tickets assigned to an agent
- `findByContactId(UUID)` — all tickets for a contact

---

### `crm/ticket/service/TicketService.java`

Business logic for tickets. All methods `@Transactional(transactionManager = "tenantTransactionManager")`.

**Methods:** `findAll(Pageable)`, `findByStatus(TicketStatus, Pageable)`, `findById(UUID)`, `create(TicketDto)`, `update(UUID, TicketDto)`, `delete(UUID)`

---

### `crm/ticket/controller/TicketController.java`

REST controller. All paths under `/api/tickets`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/tickets?status=` | Paginated list; filters by status if provided |
| `GET` | `/api/tickets/{id}` | Single ticket |
| `POST` | `/api/tickets` | Create |
| `PUT` | `/api/tickets/{id}` | Update |
| `DELETE` | `/api/tickets/{id}` | Delete |

---

## 11. Dynamic Modules

### `module/entity/DynamicModule.java`

Defines a logical CRM module (both built-in system modules and user-created custom modules).

| Field | Notes |
|---|---|
| `apiName` | Unique programmatic name, e.g. `"contacts"` |
| `displayName` | UI label |
| `type` | Enum: `SYSTEM` (cannot be deleted) or `CUSTOM` |
| `tableName` | Physical PostgreSQL table this module maps to (unique) |
| `active` | Soft-disable without deleting |
| `fields` | `@OneToMany DynamicField` list |

---

### `module/entity/DynamicField.java`

A single field definition within a `DynamicModule`.

| Field | Notes |
|---|---|
| `apiName` | Programmatic name, unique per module |
| `displayName` | UI label |
| `fieldType` | Enum: `TEXT`, `NUMBER`, `DATE`, `DATETIME`, `PICKLIST`, `MULTI_PICKLIST`, `LOOKUP`, `BOOLEAN`, `EMAIL`, `PHONE`, `URL` |
| `required` | Validation flag |
| `active` | Soft-disable |
| `defaultValue` | Optional default |

---

### `module/repository/DynamicModuleRepository.java`

Spring Data JPA repository for `DynamicModule` (tenant JPA).

- `findByApiName(String)` — `@Cacheable("moduleMetadata")` — cached; call `CacheInvalidationPublisher.publishEvict("moduleMetadata", ...)` after module updates
- `findAllByActiveTrue()` — all active modules

---

### `module/repository/DynamicFieldRepository.java`

Spring Data JPA repository for `DynamicField` (tenant JPA).

- `findByModuleId(UUID)` — `@Cacheable("fieldMetadata")` — all fields for a module, cached
- `findByModuleIdAndActiveTrue(UUID)` — only active fields

---

## 12. Common / Shared

### `common/entity/BaseEntity.java`

`@MappedSuperclass` extended by all tenant entities.

**What is implemented:**
- `id UUID` — `@GeneratedValue(strategy = GenerationType.UUID)` — Hibernate 6 generates a UUID; if the id is pre-set (e.g. for `users` whose UUID comes from `PlatformUser`), Hibernate respects the pre-set value
- `createdAt`, `updatedAt` — `LocalDateTime`, set in `@PrePersist` / `@PreUpdate`
- `createdBy`, `updatedBy` — String, reads principal name from `SecurityContextHolder.getContext().getAuthentication()` at persist/update time

**Not used by:** `Organization`, `TenantDatasource`, `PlatformUser`, `OrganizationMember` — these are platform entities with their own `@PrePersist` and simpler audit needs.

---

### `common/exception/AppException.java`

`RuntimeException` that carries an `HttpStatus`. Thrown from service/filter code to signal domain errors (404 not found, 403 forbidden, etc.). Caught and formatted by `GlobalExceptionHandler`.

---

### `common/exception/TenantNotFoundException.java`

Specialisation of `AppException` with `HttpStatus.BAD_REQUEST`. Thrown by `TenantDataSourceRouter` when no tenant context is set during a request, or when the requested tenant ID is not registered.

---

### `common/exception/GlobalExceptionHandler.java`

`@RestControllerAdvice` — converts exceptions to structured JSON error responses.

**What is implemented:**
- `handleAppException(AppException)` → HTTP status from exception, message in body
- `handleValidation(MethodArgumentNotValidException)` → `400` with field-level validation errors
- `handleGeneric(Exception)` → `500` with sanitised message

**Response format:** Java record `ErrorResponse(int status, String message, Object details, LocalDateTime timestamp)`

---

## 13. Resources & SQL

### `application.yml`

Central configuration file.

| Section | Purpose |
|---|---|
| `spring.datasource.*` | Platform DB JDBC URL, credentials, HikariCP pool settings — read by `PlatformJpaConfig` |
| `spring.data.redis.*` | Redis host and port |
| `spring.cache.type=caffeine` | Activates Caffeine as the Spring Cache provider |
| `spring.cache.caffeine.spec` | `maximumSize=1000,expireAfterWrite=10m` |
| `spring.flyway.enabled=false` | Disables Spring Boot's auto-Flyway; migrations are applied programmatically per-tenant |
| `app.jwt.secret` | Base64-encoded 256-bit HMAC secret |
| `app.jwt.expiration` | Token lifetime in milliseconds (86400000 = 24 h) |

---

### `db/migration/tenant/V1__init_tenant_schema.sql`

Flyway migration applied to **each new tenant database** when it is onboarded. Creates all tenant-level tables:

`profiles` → `permissions` → `users` (id = PlatformUser UUID, no password column) → `sharing_rules` → `contacts` → `tickets` → `dynamic_modules` → `dynamic_fields` + all indexes.

---

### `db/migration/tenant/V2__seed_system_modules.sql`

Seeds the two built-in SYSTEM modules (`contacts`, `tickets`) with their default field definitions into every new tenant database after `V1` runs.

---

### `db/platform/V1__init_platform_schema.sql`

**Run once manually** to create the `platform_db` schema. Not managed by Flyway. Creates:

- `organizations` — org registry with UUID PK and subdomain routing key
- `tenant_datasources` — JDBC connection config per org
- `platform_users` — global user identity with BCrypt password
- `organization_members` — user-to-org membership join table

All tables have detailed inline SQL comments explaining each column's role.

---

### `db/platform/fix_schema_migration.sql`

**One-time remediation script.** Drops the three tables that were created with the wrong `BIGINT` id schema from a previous migration attempt and recreates them correctly with `UUID` primary and foreign keys. Run this only if `V1__init_platform_schema.sql` was previously executed against a database that already had conflicting BIGINT-based tables.

---

### `db/platform/platform_operations.sql`

**Reference DML script** — not a migration, not run automatically. Contains annotated SQL blocks for all common operational tasks:

- CREATE org → register datasource → create platform user → add membership
- READ: user's orgs, org's users
- UPDATE: password change, global user disable, remove from one org, suspend org
- DELETE: membership, global user (cascades)

Use these blocks as copy-paste templates when operating the platform database directly.
