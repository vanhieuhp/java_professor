-- Add ADMIN feature
INSERT INTO features (code, name) VALUES ('ADMIN', 'Administration');

-- Add admin-specific functions (not shared with CASHIN/TRANSFER/etc.)
INSERT INTO functions (code, name) VALUES
    ('CREATE_CIF',    'Create CIF'),
    ('CREATE_WALLET', 'Create Wallet'),
    ('CREATE_USER',   'Create User'),
    ('ASSIGN_USER',   'Assign User'),
    ('MANAGE_GROUP',  'Manage Group');

-- Create only the ADMIN:* permissions (no cross-join with other features)
INSERT INTO permissions (feature_id, function_id, code, description)
SELECT f.id, fn.id, 'ADMIN:' || fn.code, 'Administration: ' || fn.name
FROM features f
CROSS JOIN functions fn
WHERE f.code = 'ADMIN'
  AND fn.code IN ('CREATE_CIF', 'CREATE_WALLET', 'CREATE_USER', 'ASSIGN_USER', 'MANAGE_GROUP');

-- Grant all ADMIN:* permissions to Full Operator group
INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id
FROM groups g, permissions p
WHERE g.name = 'Full Operator'
  AND p.code LIKE 'ADMIN:%';

-- Grant all ADMIN:* permissions to Wallet Admin group
INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id
FROM groups g, permissions p
WHERE g.name = 'Wallet Admin'
  AND p.code LIKE 'ADMIN:%';
