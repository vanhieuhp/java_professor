CREATE TABLE account_wallets (
                                 id         BIGSERIAL      PRIMARY KEY,
                                 cif_id     BIGINT         NOT NULL REFERENCES cif(id),
                                 code       VARCHAR(50)    NOT NULL UNIQUE,
                                 name       VARCHAR(255)   NOT NULL,
                                 balance    DECIMAL(18,2)  NOT NULL DEFAULT 0,
                                 currency   VARCHAR(10)    NOT NULL DEFAULT 'VND',
                                 is_active  BOOLEAN        NOT NULL DEFAULT TRUE,
                                 created_at TIMESTAMP      NOT NULL DEFAULT NOW(),
                                 updated_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_account_wallets_cif_id ON account_wallets(cif_id);