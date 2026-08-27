-- One row per (user, idempotency key). This table is the durable log of an
-- in-flight request: what was asked for (request_hash), how far the work has
-- got (recovery_point), and what to hand back on a replay (response).
CREATE TABLE idempotency_keys
(
    id                     BIGSERIAL PRIMARY KEY,

    user_id                BIGINT       NOT NULL,
    idempotency_key        VARCHAR(255) NOT NULL,

    -- Fingerprint of the request body. A second request under the same key but
    -- with a different hash is a client bug, not a retry, and is rejected.
    request_hash           VARCHAR(64)  NOT NULL,

    status                 VARCHAR(32)  NOT NULL,
    recovery_point         VARCHAR(64)  NOT NULL,

    -- Lease held by whichever worker is currently advancing this key. Not the
    -- row lock: the row lock lives only for one phase transaction, while this
    -- spans the gaps between phases, including the Stripe call.
    locked_at              TIMESTAMP,

    -- Written once at insert so every retry sends Stripe the same key, then
    -- filled in with whatever the charge came back as.
    stripe_idempotency_key VARCHAR(255),
    stripe_charge_id       VARCHAR(255),

    -- Set when the payments row is created, so a crash after PAYMENT_CREATED
    -- can find its way back to the right payment on resume.
    payment_id             BIGINT,

    response               JSONB,

    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,

    CONSTRAINT uq_idempotency_user_key
        UNIQUE (user_id, idempotency_key)
);
