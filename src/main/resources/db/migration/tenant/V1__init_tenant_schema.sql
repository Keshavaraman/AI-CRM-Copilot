-- =============================================================
-- V1: Tenant schema — all tables created in each tenant DB
-- =============================================================

-- Profiles
CREATE TABLE IF NOT EXISTS profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

-- Permissions
CREATE TABLE IF NOT EXISTS permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id  UUID NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
    module_name VARCHAR(100) NOT NULL,
    action      VARCHAR(20)  NOT NULL CHECK (action IN ('READ','WRITE','DELETE','ALL')),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

-- Users (org-specific record — id matches platform_users.id for cross-DB identity)
-- Credentials (password) are stored only in platform_db.platform_users
CREATE TABLE IF NOT EXISTS users (
    id          UUID PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    profile_id  UUID REFERENCES profiles(id),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

-- Sharing Rules
CREATE TABLE IF NOT EXISTS sharing_rules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_name      VARCHAR(100) NOT NULL,
    owner_id         UUID NOT NULL,
    shared_with_id   UUID NOT NULL,
    access_level     VARCHAR(10) NOT NULL CHECK (access_level IN ('READ','WRITE')),
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP NOT NULL DEFAULT now(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255)
);

-- Contacts
CREATE TABLE IF NOT EXISTS contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name  VARCHAR(100) NOT NULL,
    last_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(255) UNIQUE,
    phone       VARCHAR(50),
    company     VARCHAR(255),
    job_title   VARCHAR(255),
    description TEXT,
    owner_id    UUID,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

-- Tickets
CREATE TABLE IF NOT EXISTS tickets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject     VARCHAR(500) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','CLOSED')),
    priority    VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM'
                    CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    assignee_id UUID,
    contact_id  UUID REFERENCES contacts(id) ON DELETE SET NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

-- Dynamic Modules
CREATE TABLE IF NOT EXISTS dynamic_modules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_name     VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    type         VARCHAR(10)  NOT NULL DEFAULT 'CUSTOM'
                     CHECK (type IN ('SYSTEM','CUSTOM')),
    table_name   VARCHAR(100) NOT NULL UNIQUE,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    updated_by   VARCHAR(255)
);

-- Dynamic Fields
CREATE TABLE IF NOT EXISTS dynamic_fields (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    module_id     UUID NOT NULL REFERENCES dynamic_modules(id) ON DELETE CASCADE,
    api_name      VARCHAR(100) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    field_type    VARCHAR(20)  NOT NULL
                      CHECK (field_type IN ('TEXT','NUMBER','DATE','DATETIME',
                                            'PICKLIST','MULTI_PICKLIST','LOOKUP',
                                            'BOOLEAN','EMAIL','PHONE','URL')),
    required      BOOLEAN NOT NULL DEFAULT FALSE,
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    default_value TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    UNIQUE (module_id, api_name)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_contacts_owner   ON contacts(owner_id);
CREATE INDEX IF NOT EXISTS idx_contacts_email   ON contacts(email);
CREATE INDEX IF NOT EXISTS idx_tickets_status   ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_assignee ON tickets(assignee_id);
CREATE INDEX IF NOT EXISTS idx_tickets_contact  ON tickets(contact_id);
CREATE INDEX IF NOT EXISTS idx_fields_module    ON dynamic_fields(module_id);
