-- =============================================================
-- V2: Seed system modules — Contacts and Tickets with default fields
-- =============================================================

-- System module: Contacts
INSERT INTO dynamic_modules (id, api_name, display_name, type, table_name, active)
VALUES (
    gen_random_uuid(),
    'contacts',
    'Contacts',
    'SYSTEM',
    'contacts',
    TRUE
) ON CONFLICT (api_name) DO NOTHING;

-- System module: Tickets
INSERT INTO dynamic_modules (id, api_name, display_name, type, table_name, active)
VALUES (
    gen_random_uuid(),
    'tickets',
    'Tickets',
    'SYSTEM',
    'tickets',
    TRUE
) ON CONFLICT (api_name) DO NOTHING;

-- ---- Contacts fields ----
INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'firstName',   'First Name',   'TEXT',  TRUE  FROM dynamic_modules m WHERE m.api_name = 'contacts'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'lastName',    'Last Name',    'TEXT',  TRUE  FROM dynamic_modules m WHERE m.api_name = 'contacts'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'email',       'Email',        'EMAIL', FALSE FROM dynamic_modules m WHERE m.api_name = 'contacts'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'phone',       'Phone',        'PHONE', FALSE FROM dynamic_modules m WHERE m.api_name = 'contacts'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'company',     'Company',      'TEXT',  FALSE FROM dynamic_modules m WHERE m.api_name = 'contacts'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'jobTitle',    'Job Title',    'TEXT',  FALSE FROM dynamic_modules m WHERE m.api_name = 'contacts'
ON CONFLICT (module_id, api_name) DO NOTHING;

-- ---- Tickets fields ----
INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'subject',     'Subject',      'TEXT',     TRUE  FROM dynamic_modules m WHERE m.api_name = 'tickets'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'description', 'Description',  'TEXT',     FALSE FROM dynamic_modules m WHERE m.api_name = 'tickets'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'status',      'Status',       'PICKLIST', TRUE  FROM dynamic_modules m WHERE m.api_name = 'tickets'
ON CONFLICT (module_id, api_name) DO NOTHING;

INSERT INTO dynamic_fields (id, module_id, api_name, display_name, field_type, required)
SELECT gen_random_uuid(), m.id, 'priority',    'Priority',     'PICKLIST', TRUE  FROM dynamic_modules m WHERE m.api_name = 'tickets'
ON CONFLICT (module_id, api_name) DO NOTHING;
