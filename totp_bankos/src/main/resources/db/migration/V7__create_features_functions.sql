CREATE TABLE features (
                          id        BIGSERIAL   PRIMARY KEY,
                          code      VARCHAR(50) NOT NULL UNIQUE,
                          name      VARCHAR(100) NOT NULL,
                          is_active BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE functions (
                           id        BIGSERIAL   PRIMARY KEY,
                           code      VARCHAR(50) NOT NULL UNIQUE,
                           name      VARCHAR(100) NOT NULL,
                           is_active BOOLEAN     NOT NULL DEFAULT TRUE
);

-- seed features
INSERT INTO features (code, name) VALUES
                                      ('CASHIN',      'Cash In'),
                                      ('CASHOUT',     'Cash Out'),
                                      ('TRANSFER',    'Transfer'),
                                      ('LINK_BANK',   'Link Bank Account'),
                                      ('UNLINK_BANK', 'Unlink Bank Account');

-- seed functions
INSERT INTO functions (code, name) VALUES
                                       ('LIST',           'List'),
                                       ('READ_DETAIL',    'Read Detail'),
                                       ('CREATE_REQUEST', 'Create Request'),
                                       ('APPROVE',        'Approve'),
                                       ('EXPORT_EXCEL',   'Export Excel');