-- =============================================================
-- platform_db — Master schema
-- Run this ONCE manually before the first application startup.
-- This database is NOT managed by Flyway (tenant DBs are).
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- enables gen_random_uuid()

-- -------------------------------------------------------------
-- ORGANIZATIONS
-- One row per tenant (company/workspace).
-- subdomain is the routing key used in the X-Tenant-Id header.
-- Example: subdomain = 'acme'  →  tenant DB = acme_crm
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS organizations (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,                -- Display name, e.g. "Acme Corp"
    subdomain   VARCHAR(100) NOT NULL UNIQUE,         -- Routing key, e.g. "acme" (lowercase, no spaces)
    active      BOOLEAN      NOT NULL DEFAULT TRUE,   -- FALSE = org suspended, all logins blocked
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------
-- TENANT_DATASOURCES
-- Connection details for each org's dedicated PostgreSQL database.
-- TenantDataSourceManager reads this table on startup and builds
-- a HikariCP pool per active row.
-- One org has exactly one datasource row.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenant_datasources (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID        NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    db_url          VARCHAR(500) NOT NULL,   -- Full JDBC URL, e.g. jdbc:postgresql://localhost:5432/acme_crm
    db_name         VARCHAR(100) NOT NULL,   -- DB name only, e.g. acme_crm
    db_username     VARCHAR(100) NOT NULL,
    db_password     VARCHAR(255) NOT NULL,   -- Store encrypted in production (Vault / AWS Secrets Manager)
    active          BOOLEAN      NOT NULL DEFAULT TRUE,  -- FALSE = stops routing, pool is closed on next restart
    UNIQUE (organization_id)                -- One datasource per org enforced at DB level
);

-- -------------------------------------------------------------
-- PLATFORM_USERS
-- Global identity store — one row per real person, shared across
-- ALL organizations they belong to.
-- Credentials (email + bcrypt password) live here ONLY.
-- Changing a password here takes effect in every org instantly.
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_users (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,        -- Login identity, globally unique
    password    VARCHAR(255) NOT NULL,               -- BCrypt hash — never store plaintext
    active      BOOLEAN      NOT NULL DEFAULT TRUE,  -- FALSE = cannot log in to ANY org
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------
-- ORGANIZATION_MEMBERS
-- Many-to-many join between platform_users and organizations.
-- A user can be a member of multiple orgs.
-- Active = FALSE revokes access to that specific org only
-- (the user can still log in to other orgs they belong to).
--
-- The corresponding tenant DB has a `users` row with the
-- SAME UUID as platform_user_id (cross-DB identity link, no FK).
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS organization_members (
    id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_user_id UUID      NOT NULL REFERENCES platform_users(id)  ON DELETE CASCADE,
    organization_id  UUID      NOT NULL REFERENCES organizations(id)    ON DELETE CASCADE,
    active           BOOLEAN   NOT NULL DEFAULT TRUE,  -- FALSE = user kicked from this org only
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (platform_user_id, organization_id)         -- One membership row per (user, org)
);

-- Indexes for common lookups
CREATE INDEX IF NOT EXISTS idx_members_user ON organization_members(platform_user_id);
CREATE INDEX IF NOT EXISTS idx_members_org  ON organization_members(organization_id);
CREATE INDEX IF NOT EXISTS idx_ds_org       ON tenant_datasources(organization_id);
