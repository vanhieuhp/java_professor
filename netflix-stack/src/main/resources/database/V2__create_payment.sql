CREATE TABLE payments
(
    id               BIGSERIAL PRIMARY KEY,

    user_id          BIGINT         NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    currency         VARCHAR(3)     NOT NULL,

    stripe_charge_id VARCHAR(255),

    status           VARCHAR(32)    NOT NULL,

    created_at       TIMESTAMP      NOT NULL,
    updated_at       TIMESTAMP      NOT NULL
);