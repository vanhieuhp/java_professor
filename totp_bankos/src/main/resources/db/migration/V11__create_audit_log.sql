CREATE TABLE audit_log (
                           id              BIGSERIAL    PRIMARY KEY,
                           user_id         BIGINT       REFERENCES users(id),
                           wallet_id       BIGINT       REFERENCES account_wallets(id),
                           feature_code    VARCHAR(50),
                           function_code   VARCHAR(50),
                           permission_code VARCHAR(100),
                           target_id       VARCHAR(255),
                           granted         BOOLEAN      NOT NULL,
                           denial_reason   TEXT,
                           ip_address      VARCHAR(45),
                           user_agent      TEXT,
                           created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- audit log is append-only and queried by user, wallet, and time
CREATE INDEX idx_audit_log_user_id   ON audit_log(user_id);
CREATE INDEX idx_audit_log_wallet_id ON audit_log(wallet_id);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);