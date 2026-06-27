# Backend Implementation Progress

## Status: Initial Architecture Complete

**Last updated:** 2026-06-06
**Stack:** Spring Boot 3.5.14 · Java 21 · PostgreSQL · Redis (L2) · Caffeine (L1) · JJWT 0.12.x · Flyway

---

## Quick Reference — Key Design Decisions

| Decision | Choice | Reason |
|---|---|---|
| Multi-tenancy | Database-per-org via `AbstractRoutingDataSource` | Full data isolation |
| Tenant resolution | `X-Tenant-Id` HTTP header at filter layer | Business code is zero-aware of tenancy |
| JPA split | Two separate EMFs (platform + tenant) | platform_db and tenant DBs are independent |
| User identity | `PlatformUser` in platform_db (one per person) | Same credentials work across all orgs |
| Org membership | `OrganizationMember` join table | One user → many orgs, each with own profile |
| JWT subject | `userId UUID` (not email) | Stable identity; email can change |
| L1 cache keys | Prefixed with tenantId by `TenantAwareKeyGenerator` | Prevents cross-tenant cache pollution |
| Flyway | Applied programmatically per-tenant at onboarding | Not at Spring Boot startup |
| Password storage | Only in `platform_users.password` (BCrypt) | Tenant DBs have no password column |

---

## Completed

### Infrastructure

| Item | Detail |
|---|---|
| `build.gradle` | Added: Redis, Caffeine, JJWT 0.12.x, Flyway, Actuator |
| `application.yml` | Replaces `.properties`; platform DB, Redis, JWT config, Caffeine spec, `flyway.enabled: false` |
| `CrmaiApplication` | Excludes `DataSourceAutoConfiguration` + `HibernateJpaAutoConfiguration` (both JPA configs are fully manual) |

---

### Multi-Tenancy Core

| Class | Role |
|---|---|
| `multitenancy/TenantContext` | `ThreadLocal<String>` — holds current tenant ID per request thread; always cleared in `finally` |
| `multitenancy/TenantDataSourceRouter` | Extends `AbstractRoutingDataSource`; `ConcurrentHashMap` of tenant→DataSource; `addTenant()` re-initialises router atomically; throws `TenantNotFoundException` if lookup key is unknown |
| `multitenancy/TenantDataSourceManager` | `@Component`; on `ApplicationReadyEvent` loads all `active = true` rows from `tenant_datasources`, builds HikariCP pool per tenant, runs Flyway migrations, registers in router; `onboardTenant()` for new sign-ups |

**Request flow (business code is completely tenant-unaware):**
```
Every request
  ↓ X-Tenant-Id: acme
  TenantResolutionFilter (@Order 1)
    → validates header present + tenant known
    → TenantContext.set("acme")
    → finally: TenantContext.clear()
  ↓
  JwtAuthFilter (@Order 2)
    → parses JWT, asserts jwt.tenantId == header value
    → loads User from tenant DB by UUID
    → sets SecurityContext
  ↓
  Controller → Service → Repository
    → JPA → TenantDataSourceRouter.determineCurrentLookupKey()
    → routes to correct DB transparently
```

---

### Platform Database (`platform_db`)

**Java entities + repositories**

| Class | Role |
|---|---|
| `platform/entity/Organization` | `id UUID, name, subdomain (unique), active, createdAt` |
| `platform/entity/TenantDatasource` | `organization FK, dbUrl, dbName, dbUsername, dbPassword, active` |
| `platform/entity/PlatformUser` | Global user identity: `id UUID, email (unique), password (BCrypt), active` — shared across all orgs |
| `platform/entity/OrganizationMember` | `platformUser FK, organization FK, active` — unique per (user, org) pair |
| `platform/repository/OrganizationRepository` | `findBySubdomain`, `existsBySubdomain` |
| `platform/repository/TenantDatasourceRepository` | `findAllByActiveTrue`, `findByOrganizationSubdomain` |
| `platform/repository/PlatformUserRepository` | `findByEmail`, `existsByEmail` |
| `platform/repository/OrganizationMemberRepository` | `existsByPlatformUserIdAndOrganizationIdAndActiveTrue`, `findByPlatformUserIdAndActiveTrue` |
| `config/datasource/PlatformJpaConfig` | `@Primary` EMF; reads `spring.datasource.*`; scans `platform.entity` + `platform.repository` |

**Multi-org user model:**
```
platform_db
  platform_users        (id UUID, email, password)   ← one row per real person
  organization_members  (platformUserId, orgId)       ← which orgs they belong to

acme_crm / globex_crm / ...
  users  (id = same UUID as platform_users.id,        ← cross-DB identity (no FK)
          email denormalized, profile_id)              ← org-specific role, NO password column
```
- Password change in `platform_users` takes effect in all orgs instantly
- Disabling `platform_users.active = false` blocks all org logins
- Disabling `organization_members.active = false` removes access to one org only

**SQL scripts (`src/main/resources/db/platform/`)**

| File | Purpose |
|---|---|
| `V1__init_platform_schema.sql` | Full CREATE TABLE with inline comments — run once to set up platform_db |
| `fix_schema_migration.sql` | One-time fix: drops old BIGINT tables, recreates with UUID schema (run if pre-existing tables had BIGINT ids) |
| `platform_operations.sql` | Reference DML — CREATE / READ / UPDATE / DELETE patterns for org + user management |

---

### Tenant JPA Configuration

| Class | Role |
|---|---|
| `config/datasource/TenantJpaConfig` | EMF backed by `TenantDataSourceRouter`; scans `security.entity`, `crm.contact.entity`, `crm.ticket.entity`, `module.entity`, `common.entity` |

> All tenant services must use `@Transactional(transactionManager = "tenantTransactionManager")`.

---

### Security

| Class | Role |
|---|---|
| `security/entity/User` | Implements `UserDetails`; `id = PlatformUser.id (same UUID)`; `email` (denormalized, read-only); `profile FK`; `active` — **no password column** |
| `security/entity/Profile` | `name, description`; `@OneToMany permissions (EAGER)`; `hasPermission(moduleName, action)` helper |
| `security/entity/Permission` | `profile FK, moduleName, action enum (READ/WRITE/DELETE/ALL)` |
| `security/entity/SharingRule` | Stub entity: `moduleName, ownerId, sharedWithId, accessLevel (READ/WRITE)` |
| `security/service/JwtService` | JJWT 0.12.x; JWT `sub = userId UUID`; claims: `email`, `tenantId`; `generateToken`, `isTokenValid`, `extractUserId`, `extractTenantId` |
| `security/service/AuthService` | Three-step login: ① verify BCrypt in `platform_db` ② check `organization_members` ③ load tenant `User` by UUID |
| `security/service/UserDetailsServiceImpl` | `loadUserByUsername(userId)` — parses UUID string from JWT `sub`, loads tenant `User` by id |
| `security/filter/TenantResolutionFilter` | `@Order(1)` — rejects missing/unknown `X-Tenant-Id` header; sets `TenantContext`; clears in `finally` |
| `security/filter/JwtAuthFilter` | `@Order(2)` — validates JWT; asserts `jwt.tenantId == X-Tenant-Id` header (prevents tenant switching); sets `SecurityContext` |
| `security/dto/AuthRequest` | `{ email, password }` — tenantId comes from `X-Tenant-Id` header only |
| `security/dto/AuthResponse` | `{ token, email, userId, tenantId, expiresIn }` |
| `security/controller/AuthController` | `POST /api/auth/login` |
| `config/security/SecurityConfig` | Stateless, CSRF off, CORS open; public paths: `/api/auth/**`, `/actuator/health` |

---

### CRM Domain

**Contacts** (`crm/contact/`)

| Layer | Key points |
|---|---|
| `Contact` entity | `firstName, lastName, email (unique), phone, company, jobTitle, description, ownerId UUID` |
| `ContactRepository` | `findByEmail`, `findByOwnerId`, JPQL `search()` — name/email full-text with `Pageable` |
| `ContactService` | `findAll(page)`, `search(q, page)`, `findById`, `create`, `update`, `delete` |
| `ContactController` | `GET /api/contacts?search=`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` |

**Tickets** (`crm/ticket/`)

| Layer | Key points |
|---|---|
| `Ticket` entity | `subject, description, status enum (OPEN/IN_PROGRESS/RESOLVED/CLOSED), priority enum (LOW/MEDIUM/HIGH/URGENT), assigneeId UUID, contactId UUID` |
| `TicketRepository` | `findByStatus(page)`, `findByAssigneeId`, `findByContactId` |
| `TicketService` | Full CRUD + filter by status |
| `TicketController` | `GET /api/tickets?status=`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` |

---

### Dynamic Modules Skeleton

| Class | Role |
|---|---|
| `module/entity/DynamicModule` | `apiName (unique), displayName, type (SYSTEM/CUSTOM), tableName (unique), active, @OneToMany fields` |
| `module/entity/DynamicField` | `module FK, apiName, displayName, fieldType (TEXT/NUMBER/DATE/DATETIME/PICKLIST/MULTI_PICKLIST/LOOKUP/BOOLEAN/EMAIL/PHONE/URL), required, active, defaultValue` |
| `module/repository/DynamicModuleRepository` | `findByApiName` (`@Cacheable("moduleMetadata")`), `findAllByActiveTrue` |
| `module/repository/DynamicFieldRepository` | `findByModuleId` (`@Cacheable("fieldMetadata")`), `findByModuleIdAndActiveTrue` |

---

### Cache

| Class | Role |
|---|---|
| `config/cache/CacheConfig` | `@EnableCaching`; Caffeine L1; named caches: `profilePermissions`, `moduleMetadata`, `fieldMetadata` (max 1000 entries, TTL 10 min); registers `TenantAwareKeyGenerator` as the default Spring Cache `keyGenerator` bean |
| `config/cache/TenantAwareKeyGenerator` | Wraps `SimpleKeyGenerator`; prepends `TenantContext.getCurrentTenant()` to every key → `"acme:SimpleKey [uuid-123]"`; uses `"shared"` prefix when called outside a request thread |
| `config/redis/RedisConfig` | `RedisTemplate<String, Object>` with Jackson2Json serializer + `JavaTimeModule`; used for L2 (sessions, AI summaries, dashboard metrics) |

---

### Common

| Class | Role |
|---|---|
| `common/entity/BaseEntity` | `@MappedSuperclass`; UUID PK via `@GeneratedValue(UUID)` (Hibernate 6 — pre-set UUID is respected); `createdAt/updatedAt/createdBy/updatedBy` via `@PrePersist`/`@PreUpdate`; reads actor from `SecurityContextHolder` |
| `common/exception/AppException` | `RuntimeException` carrying `HttpStatus` |
| `common/exception/TenantNotFoundException` | Extends `AppException(400)` |
| `common/exception/GlobalExceptionHandler` | `@RestControllerAdvice`; handles `AppException`, `MethodArgumentNotValidException`, generic `Exception` |

---

### Flyway Tenant Migrations (`src/main/resources/db/migration/tenant/`)

Applied programmatically by `TenantDataSourceManager` when a new tenant is onboarded — never at Spring Boot startup.

| File | Contents |
|---|---|
| `V1__init_tenant_schema.sql` | All tenant tables: `profiles, permissions, users` (id = PlatformUser UUID, no password column), `sharing_rules, contacts, tickets, dynamic_modules, dynamic_fields` + indexes |
| `V2__seed_system_modules.sql` | Inserts `contacts` and `tickets` system modules with all default fields |

---

## File Tree

```
src/main/
├── java/com/keshava/crmai/
│   ├── CrmaiApplication.java
│   ├── common/
│   │   ├── entity/BaseEntity.java
│   │   └── exception/{AppException, TenantNotFoundException, GlobalExceptionHandler}.java
│   ├── config/
│   │   ├── cache/{CacheConfig, TenantAwareKeyGenerator}.java
│   │   ├── datasource/{PlatformJpaConfig, TenantJpaConfig}.java
│   │   ├── redis/RedisConfig.java
│   │   └── security/SecurityConfig.java
│   ├── crm/
│   │   ├── contact/{entity,repository,service,controller}/Contact*.java
│   │   └── ticket/{entity,repository,service,controller}/Ticket*.java
│   ├── module/
│   │   ├── entity/{DynamicModule, DynamicField}.java
│   │   └── repository/{DynamicModule,DynamicField}Repository.java
│   ├── multitenancy/
│   │   ├── TenantContext.java
│   │   ├── TenantDataSourceRouter.java
│   │   └── TenantDataSourceManager.java
│   ├── platform/
│   │   ├── entity/{Organization, TenantDatasource, PlatformUser, OrganizationMember}.java
│   │   └── repository/{Organization,TenantDatasource,PlatformUser,OrganizationMember}Repository.java
│   └── security/
│       ├── controller/AuthController.java
│       ├── dto/{AuthRequest, AuthResponse}.java
│       ├── entity/{User, Profile, Permission, SharingRule}.java
│       ├── filter/{TenantResolutionFilter, JwtAuthFilter}.java
│       ├── repository/{User,Profile}Repository.java
│       └── service/{JwtService, AuthService, UserDetailsServiceImpl}.java
└── resources/
    ├── application.yml
    └── db/
        ├── migration/tenant/
        │   ├── V1__init_tenant_schema.sql
        │   └── V2__seed_system_modules.sql
        └── platform/
            ├── V1__init_platform_schema.sql
            ├── fix_schema_migration.sql
            └── platform_operations.sql
```

---

## Pending / Next Steps

| Area | What's needed |
|---|---|
| **Organization onboarding API** | Controller: create `Organization` + `TenantDatasource` in platform_db, create physical DB, call `TenantDataSourceManager.onboardTenant()`, insert `users` row in tenant DB |
| **User invite flow** | Add user to org: insert `OrganizationMember` in platform_db + `users` row in tenant DB (same UUID, assign profile) |
| **RBAC enforcement** | Wire `profile.hasPermission()` into service layer via `@PreAuthorize` or AOP aspect |
| **Sharing Rules** | Implement record-level visibility filter using `SharingRule` table |
| **Audit Log** | `AuditLog` entity (module, recordId, action, before/after JSON, userId) + service |
| **JWT Blacklist** | Store invalidated tokens in Redis on logout |
| **Refresh Token** | Short-lived access token + long-lived refresh token flow |
| **AI Copilot — Phase 1** | Ticket Summary + Reply Generator via OpenAI/Gemini client |
| **Dynamic Module DDL** | Service to CREATE/ALTER physical PostgreSQL table when a custom module is defined |
