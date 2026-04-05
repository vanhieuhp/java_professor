CREATE TABLE outbox_events
(
    id              UUID                  DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL, -- e.g. 'TRANSFER'
    aggregate_id    VARCHAR(100) NOT NULL, -- e.g. transfer id
    event_type      VARCHAR(100) NOT NULL, -- e.g. 'DEBIT_COMPLETED'
    payload         JSONB        NOT NULL, -- full event data
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP
);

CREATE INDEX idx_outbox_status
    ON outbox_events (status) WHERE status = 'PENDING';