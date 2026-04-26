CREATE TABLE active_wallet_sessions (
                                        id           BIGSERIAL    PRIMARY KEY,
                                        user_id      BIGINT       NOT NULL REFERENCES users(id) UNIQUE,
                                        wallet_id    BIGINT       NOT NULL REFERENCES account_wallets(id),
                                        jwt_token_id VARCHAR(255) NOT NULL,
                                        activated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
                                        expires_at   TIMESTAMP    NOT NULL,

    -- one active wallet per user at a time
                                        CONSTRAINT uq_active_session_user UNIQUE (user_id)
);

CREATE INDEX idx_aws_user_id   ON active_wallet_sessions(user_id);
CREATE INDEX idx_aws_wallet_id ON active_wallet_sessions(wallet_id);