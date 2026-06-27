-- =============================================================
-- FIX SCRIPT — run once to reconcile pre-existing BIGINT tables
-- with the UUID-based schema required by the application.
--
-- What happened:
--   organizations    → existed with id BIGINT + different column names
--   tenant_datasources → existed with organization_id BIGINT + different column names
--   platform_users   → created correctly (UUID) — DO NOT touch
--   organization_members → failed to create (FK type mismatch)
--
-- This script drops the mismatched tables and recreates them cleanly.
-- platform_users is NOT touched.
-- =============================================================

-- Step 1: Drop in dependency order (children before parents)
DROP TABLE IF EXISTS organization_members  CASCADE;
DROP TABLE IF EXISTS tenant_datasources    CASCADE;
DROP TABLE IF EXISTS organizations         CASCADE;

-- Step 2: Recreate organizations with UUID id
CREATE TABLE organizations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    subdomain   VARCHAR(100) NOT NULL UNIQUE,          -- routing key used in X-Tenant-Id header
    active      BOOLEAN      NOT NULL DEFAULT TRUE,    -- FALSE = org suspended, all logins blocked
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

-- Step 3: Recreate tenant_datasources with UUID FK and corrected column names
CREATE TABLE tenant_datasources (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    db_url          VARCHAR(500) NOT NULL,   -- full JDBC URL e.g. jdbc:postgresql://localhost:5432/acme_crm
    db_name         VARCHAR(100) NOT NULL,
    db_username     VARCHAR(100) NOT NULL,
    db_password     VARCHAR(255) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (organization_id)
);

-- Step 4: Create organization_members (previously failed)
CREATE TABLE organization_members (
    id               UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    platform_user_id UUID      NOT NULL REFERENCES platform_users(id)  ON DELETE CASCADE,
    organization_id  UUID      NOT NULL REFERENCES organizations(id)    ON DELETE CASCADE,
    active           BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (platform_user_id, organization_id)
);

-- Step 5: Indexes
CREATE INDEX idx_members_user ON organization_members(platform_user_id);
CREATE INDEX idx_members_org  ON organization_members(organization_id);
CREATE INDEX idx_ds_org       ON tenant_datasources(organization_id);
