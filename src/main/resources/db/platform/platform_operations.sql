-- =============================================================
-- platform_db — Common DML Operations
-- Reference script for onboarding, user management, and admin.
-- Run individual blocks as needed — NOT a migration script.
-- =============================================================


-- =============================================================
-- CREATE: Onboard a new Organization
-- Step 1 of 3 when signing up a new company.
-- After INSERT: create the physical tenant DB, then run
-- TenantDataSourceManager.onboardTenant() to apply Flyway.
-- =============================================================

INSERT INTO organizations (name, subdomain)
VALUES ('Acme Corp', 'acme');
-- subdomain must be lowercase, alphanumeric + hyphens only.
-- This value becomes the X-Tenant-Id header value.


-- =============================================================
-- CREATE: Register the tenant DB connection
-- Step 2 of 3 — after the tenant DB has been created in Postgres.
-- =============================================================

INSERT INTO tenant_datasources (organization_id, db_url, db_name, db_username, db_password)
SELECT
    id,
    'jdbc:postgresql://localhost:5432/acme_crm',
    'acme_crm',
    'postgres',
    'password'  -- replace with BCrypt-encrypted or Vault secret in production
FROM organizations
WHERE subdomain = 'acme';


-- =============================================================
-- CREATE: Register a new global user
-- Step 3 of 3 — creates the identity row shared across all orgs.
-- Password must be BCrypt-hashed before insert.
-- In the app this is done via BCryptPasswordEncoder.encode().
-- =============================================================

INSERT INTO platform_users (email, password)
VALUES (
    'john@acme.com',
    '$2a$10$exampleBCryptHashHerePleaseReplaceWithReal'  -- BCrypt hash
);


-- =============================================================
-- CREATE: Add an existing user to an org (membership)
-- Run this when a user is invited to join an organization.
-- The application must also INSERT a corresponding row in the
-- tenant DB's `users` table (same UUID, profile assigned).
-- =============================================================

INSERT INTO organization_members (platform_user_id, organization_id)
SELECT
    pu.id,
    o.id
FROM platform_users pu, organizations o
WHERE pu.email     = 'john@acme.com'
  AND o.subdomain  = 'acme';


-- =============================================================
-- READ: List all orgs a user belongs to
-- Useful for a "switch org" dropdown in the UI.
-- =============================================================

SELECT
    o.name,
    o.subdomain,
    om.active    AS membership_active,
    o.active     AS org_active
FROM organization_members om
JOIN platform_users  pu ON pu.id = om.platform_user_id
JOIN organizations   o  ON o.id  = om.organization_id
WHERE pu.email = 'john@acme.com'
ORDER BY o.name;


-- =============================================================
-- READ: All active users in an org
-- =============================================================

SELECT
    pu.id,
    pu.email,
    pu.active  AS global_active,
    om.active  AS org_active
FROM organization_members om
JOIN platform_users pu ON pu.id = om.platform_user_id
JOIN organizations   o ON  o.id = om.organization_id
WHERE o.subdomain = 'acme'
ORDER BY pu.email;


-- =============================================================
-- UPDATE: Change a user's password (applies to ALL orgs)
-- Always store a BCrypt hash — never plaintext.
-- =============================================================

UPDATE platform_users
SET    password   = '$2a$10$newBCryptHashHere',
       updated_at = now()
WHERE  email = 'john@acme.com';


-- =============================================================
-- UPDATE: Globally disable a user (blocks login to ALL orgs)
-- Use this for account termination or security lockout.
-- =============================================================

UPDATE platform_users
SET    active     = FALSE,
       updated_at = now()
WHERE  email = 'john@acme.com';

-- Re-enable:
-- UPDATE platform_users SET active = TRUE, updated_at = now() WHERE email = 'john@acme.com';


-- =============================================================
-- UPDATE: Remove a user from a specific org only
-- (User keeps access to other orgs they belong to)
-- =============================================================

UPDATE organization_members om
SET    active = FALSE
FROM   platform_users pu, organizations o
WHERE  om.platform_user_id = pu.id
  AND  om.organization_id  = o.id
  AND  pu.email    = 'john@acme.com'
  AND  o.subdomain = 'acme';

-- Re-invite (re-enable same membership row):
-- UPDATE organization_members om
-- SET    active = TRUE
-- FROM   platform_users pu, organizations o
-- WHERE  om.platform_user_id = pu.id AND om.organization_id = o.id
--   AND  pu.email = 'john@acme.com' AND o.subdomain = 'acme';


-- =============================================================
-- UPDATE: Suspend an entire organization
-- Blocks all logins for that org. The TenantResolutionFilter
-- will reject the X-Tenant-Id header (unknown/inactive tenant).
-- Set tenant_datasources.active = FALSE too to close the pool.
-- =============================================================

UPDATE organizations
SET    active = FALSE
WHERE  subdomain = 'acme';

UPDATE tenant_datasources
SET    active = FALSE
WHERE  organization_id = (SELECT id FROM organizations WHERE subdomain = 'acme');

-- Re-activate:
-- UPDATE organizations        SET active = TRUE WHERE subdomain = 'acme';
-- UPDATE tenant_datasources   SET active = TRUE
-- WHERE organization_id = (SELECT id FROM organizations WHERE subdomain = 'acme');


-- =============================================================
-- UPDATE: Change an org's subdomain (rare — update X-Tenant-Id
-- in all client apps after this)
-- =============================================================

UPDATE organizations
SET    subdomain = 'acme-new'
WHERE  subdomain = 'acme';


-- =============================================================
-- DELETE: Permanently remove a user from an org
-- Prefer UPDATE active = FALSE instead — this is irreversible.
-- Also delete the corresponding row in the tenant DB's `users`
-- table manually.
-- =============================================================

DELETE FROM organization_members om
USING  platform_users pu, organizations o
WHERE  om.platform_user_id = pu.id
  AND  om.organization_id  = o.id
  AND  pu.email    = 'john@acme.com'
  AND  o.subdomain = 'acme';


-- =============================================================
-- DELETE: Permanently remove a global user (all org memberships
-- cascade-deleted via ON DELETE CASCADE)
-- =============================================================

DELETE FROM platform_users WHERE email = 'john@acme.com';
