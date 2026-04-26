CREATE TABLE users (
                       id         BIGSERIAL    PRIMARY KEY,
                       username   VARCHAR(100) NOT NULL UNIQUE,
                       email      VARCHAR(255) NOT NULL UNIQUE,
                       password   VARCHAR(255) NOT NULL,
                       full_name  VARCHAR(255),
                       is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);