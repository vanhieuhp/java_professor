CREATE TABLE wallet_users (
                              id          BIGSERIAL PRIMARY KEY,
                              wallet_id   BIGINT    NOT NULL REFERENCES account_wallets(id),
                              user_id     BIGINT    NOT NULL REFERENCES users(id),
                              assigned_by BIGINT    REFERENCES users(id),
                              assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
                              is_active   BOOLEAN   NOT NULL DEFAULT TRUE,

                              CONSTRAINT uq_wallet_users UNIQUE (wallet_id, user_id)
);

CREATE INDEX idx_wallet_users_wallet_id ON wallet_users(wallet_id);
CREATE INDEX idx_wallet_users_user_id   ON wallet_users(user_id);