-- seed a default CIF
INSERT INTO cif (code, name) VALUES
    ('CIF-0001', 'BankOS Demo Company');

-- seed a default wallet under that CIF
INSERT INTO account_wallets (cif_id, code, name, currency)
SELECT id, 'WALLET-0001', 'Main Wallet', 'VND'
FROM cif WHERE code = 'CIF-0001';

-- seed an admin user  (password = 'Admin@123' bcrypt hashed)
INSERT INTO users (username, email, password, full_name) VALUES
    ('admin', 'admin@bankos.dev',
     '$2a$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
     'System Admin');

-- assign admin to wallet
INSERT INTO wallet_users (wallet_id, user_id)
SELECT w.id, u.id
FROM account_wallets w, users u
WHERE w.code = 'WALLET-0001' AND u.username = 'admin';

-- create default groups for the wallet
INSERT INTO groups (wallet_id, name, description)
SELECT w.id, g.name, g.description
FROM account_wallets w
         CROSS JOIN (VALUES
                         ('Viewer',        'Read-only access to all features'),
                         ('Maker',         'Can create requests for all features'),
                         ('Checker',       'Can approve requests for all features'),
                         ('Full Operator', 'Full access to all features'),
                         ('Wallet Admin',  'Manages users and groups in this wallet')
) AS g(name, description)
WHERE w.code = 'WALLET-0001';

-- give Full Operator all 25 permissions
INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id
FROM groups g, permissions p
WHERE g.name = 'Full Operator';

-- give Viewer: LIST + READ_DETAIL across all features
INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id
FROM groups g
         JOIN permissions p ON p.code LIKE '%:LIST'
    OR p.code LIKE '%:READ_DETAIL'
WHERE g.name = 'Viewer';

-- give Maker: LIST + READ_DETAIL + CREATE_REQUEST
INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id
FROM groups g
         JOIN permissions p ON p.code LIKE '%:LIST'
    OR p.code LIKE '%:READ_DETAIL'
    OR p.code LIKE '%:CREATE_REQUEST'
WHERE g.name = 'Maker';

-- give Checker: LIST + READ_DETAIL + APPROVE
INSERT INTO group_permissions (group_id, permission_id)
SELECT g.id, p.id
FROM groups g
         JOIN permissions p ON p.code LIKE '%:LIST'
    OR p.code LIKE '%:READ_DETAIL'
    OR p.code LIKE '%:APPROVE'
WHERE g.name = 'Checker';

-- assign admin user to Full Operator group
INSERT INTO wallet_user_groups (wallet_id, user_id, group_id)
SELECT w.id, u.id, g.id
FROM account_wallets w
         JOIN users u ON u.username = 'admin'
         JOIN groups g ON g.wallet_id = w.id AND g.name = 'Full Operator'
WHERE w.code = 'WALLET-0001';