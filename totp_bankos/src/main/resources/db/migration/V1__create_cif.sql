CREATE TABLE if not exists cif (
                     id         BIGSERIAL    PRIMARY KEY,
                     code       VARCHAR(50)  NOT NULL UNIQUE,
                     name       VARCHAR(255) NOT NULL,
                     is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
                     created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
                     updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);