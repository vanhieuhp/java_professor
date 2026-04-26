CREATE TABLE wallet_user_groups (
                                    id          BIGSERIAL PRIMARY KEY,
                                    wallet_id   BIGINT    NOT NULL REFERENCES account_wallets(id),
                                    user_id     BIGINT    NOT NULL REFERENCES users(id),
                                    group_id    BIGINT    NOT NULL REFERENCES groups(id),
                                    assigned_by BIGINT    REFERENCES users(id),
                                    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                    is_active   BOOLEAN   NOT NULL DEFAULT TRUE,

    -- one group per user per wallet
                                    CONSTRAINT uq_wallet_user_groups UNIQUE (wallet_id, user_id)
);

CREATE INDEX idx_wug_wallet_id ON wallet_user_groups(wallet_id);
CREATE INDEX idx_wug_user_id   ON wallet_user_groups(user_id);
CREATE INDEX idx_wug_group_id  ON wallet_user_groups(group_id);