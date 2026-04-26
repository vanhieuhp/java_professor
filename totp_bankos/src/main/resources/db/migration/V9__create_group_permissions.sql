-- V9__create_group_permissions.sql  (updated)
CREATE TABLE group_permissions (
                                   id            BIGSERIAL PRIMARY KEY,         -- surrogate key
                                   group_id      BIGINT    NOT NULL REFERENCES groups(id)      ON DELETE CASCADE,
                                   permission_id BIGINT    NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
                                   granted_by    BIGINT    REFERENCES users(id),
                                   granted_at    TIMESTAMP NOT NULL DEFAULT NOW(),

                                   CONSTRAINT uq_group_permissions UNIQUE (group_id, permission_id)
);

CREATE INDEX idx_group_permissions_group_id ON group_permissions(group_id);