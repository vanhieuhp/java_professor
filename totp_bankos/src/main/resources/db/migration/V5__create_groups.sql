CREATE TABLE groups (
                        id          BIGSERIAL    PRIMARY KEY,
                        wallet_id   BIGINT       NOT NULL REFERENCES account_wallets(id),
                        name        VARCHAR(100) NOT NULL,
                        description TEXT,
                        is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
                        created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

                        CONSTRAINT uq_groups_wallet_name UNIQUE (wallet_id, name)
);

CREATE INDEX idx_groups_wallet_id ON groups(wallet_id);