-- V13__create_transfer_requests.sql

CREATE TABLE transfer_requests (
                                   id              BIGSERIAL       PRIMARY KEY,
                                   wallet_id       BIGINT          NOT NULL REFERENCES account_wallets(id),

    -- maker who created the request
                                   created_by      BIGINT          NOT NULL REFERENCES users(id),
                                   created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),

    -- checker who approved or rejected
                                   reviewed_by     BIGINT          REFERENCES users(id),
                                   reviewed_at     TIMESTAMP,
                                   rejection_note  TEXT,

    -- transfer details
                                   amount          DECIMAL(18,2)   NOT NULL CHECK (amount > 0),
                                   currency        VARCHAR(10)     NOT NULL DEFAULT 'VND',
                                   to_account      VARCHAR(100)    NOT NULL,   -- destination account / IBAN
                                   description     TEXT,

    -- lifecycle: PENDING → APPROVED | REJECTED
                                   status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                                       CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE INDEX idx_tr_wallet_id  ON transfer_requests(wallet_id);
CREATE INDEX idx_tr_created_by ON transfer_requests(created_by);
CREATE INDEX idx_tr_status     ON transfer_requests(status);