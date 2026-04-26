CREATE TABLE permissions (
                             id          BIGSERIAL    PRIMARY KEY,
                             feature_id  BIGINT       NOT NULL REFERENCES features(id),
                             function_id BIGINT       NOT NULL REFERENCES functions(id),
                             code        VARCHAR(100) NOT NULL UNIQUE,  -- 'TRANSFER:APPROVE'
                             description TEXT,

                             CONSTRAINT uq_permissions UNIQUE (feature_id, function_id)
);

CREATE INDEX idx_permissions_code ON permissions(code);

-- seed all 25 combinations
INSERT INTO permissions (feature_id, function_id, code)
SELECT
    f.id,
    fn.id,
    f.code || ':' || fn.code
FROM features f
         CROSS JOIN functions fn;