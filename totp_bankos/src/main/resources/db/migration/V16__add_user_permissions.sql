-- Remove ADMIN permissions from groups (V15 seeded these — admin perms are user-level only)
DELETE FROM group_permissions
WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'ADMIN:%');

-- Direct user-to-permission assignments (no wallet / group needed)
CREATE TABLE user_permissions (
    id            BIGSERIAL   PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    permission_id BIGINT      NOT NULL REFERENCES permissions(id),
    granted_by    BIGINT      REFERENCES users(id),
    granted_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_permissions UNIQUE (user_id, permission_id)
);

CREATE INDEX idx_user_permissions_user_id ON user_permissions(user_id);

-- Assign all ADMIN:* permissions directly to the admin user
INSERT INTO user_permissions (user_id, permission_id, granted_by)
SELECT u.id, p.id, u.id
FROM users u, permissions p
WHERE u.username = 'admin'
  AND p.code LIKE 'ADMIN:%';
