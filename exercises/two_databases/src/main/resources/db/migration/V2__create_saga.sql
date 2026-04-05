CREATE TYPE saga_status AS ENUM (
    'STARTED',
    'DEBIT_COMPLETED',
    'CREDIT_COMPLETED',
    'COMPENSATING',
    'COMPENSATED',
    'FAILED'
);

CREATE TABLE transfer_sagas
(
    id               UUID                    DEFAULT gen_random_uuid() PRIMARY KEY,
    from_account_id  BIGINT         NOT NULL,
    to_account_id    BIGINT         NOT NULL,
    amount           DECIMAL(19, 2) NOT NULL,
    status           saga_status    NOT NULL DEFAULT 'STARTED',
    failure_reason   TEXT,
    compensation_key VARCHAR(255) UNIQUE, -- idempotency key for compensation
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saga_status ON transfer_sagas (status);