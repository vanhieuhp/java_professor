-- V14__create_cashin_requests.sql

CREATE TABLE cashin_requests (
                                 id              BIGSERIAL       PRIMARY KEY,
                                 wallet_id       BIGINT          NOT NULL REFERENCES account_wallets(id),

                                 created_by      BIGINT          NOT NULL REFERENCES users(id),
                                 created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

                                 reviewed_by     BIGINT          REFERENCES users(id),
                                 reviewed_at     TIMESTAMP,
                                 rejection_note  TEXT,

                                 amount          DECIMAL(18,2)   NOT NULL CHECK (amount > 0),
                                 currency        VARCHAR(10)     NOT NULL DEFAULT 'VND',
                                 from_account    VARCHAR(100)    NOT NULL,   -- source account / bank ref
                                 description     TEXT,

                                 status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                     CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE INDEX idx_cr_wallet_id  ON cashin_requests(wallet_id);
CREATE INDEX idx_cr_created_by ON cashin_requests(created_by);
CREATE INDEX idx_cr_status     ON cashin_requests(status);