-- ============================================================
-- Bankos Seed Data
-- PostgreSQL | run after Hibernate has created the schema
-- ============================================================
create table if not exists accounts (
  id SERIAL PRIMARY KEY,
  owner VARCHAR(255) NOT NULL,
  balance NUMERIC(15,2) NOT NULL
);

create table if not exists exchange_rates (
  currency_pair VARCHAR(255) PRIMARY KEY,
  rate NUMERIC(15,2) NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

create table if not exists products (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  price NUMERIC(15,2) NOT NULL,
  stock INT NOT NULL,
  status VARCHAR(255) NOT NULL,
  version INT NOT NULL
);

create table if not exists orders (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL,
  product_id INT NOT NULL,
  status VARCHAR(255) NOT NULL,
  saga_id VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
create table if not exists payments (
  id SERIAL PRIMARY KEY,
  account_id INT NOT NULL,
  amount NUMERIC(15,2) NOT NULL,
  status VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL
);
create table if not exists idempotency_keys (
  idempotency_key VARCHAR(255) PRIMARY KEY,
  payment_id INT NOT NULL,
  processed_at TIMESTAMP NOT NULL
);
create table if not exists outbox_events (
  id SERIAL PRIMARY KEY,
  aggregate_type VARCHAR(255) NOT NULL,
  aggregate_id INT NOT NULL,
  event_type VARCHAR(255) NOT NULL,
  payload JSONB NOT NULL,
  status VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP,
  retry_count INT NOT NULL
);

-- -------------------------------------------------------
-- accounts
-- -------------------------------------------------------
INSERT INTO accounts (owner, balance) VALUES
  ('Alice Nguyen',   5000000.00),
  ('Bob Tran',       1200000.00),
  ('Charlie Le',    15000000.00),
  ('Diana Pham',      800000.00),
  ('Edward Hoang',   3500000.00);

-- -------------------------------------------------------
-- exchange_rates
-- -------------------------------------------------------
INSERT INTO exchange_rates (currency_pair, rate, updated_at) VALUES
  ('USD_VND', 25400.00,  NOW()),
  ('EUR_VND', 27800.00,  NOW()),
  ('JPY_VND',   165.50,  NOW()),
  ('GBP_VND', 32100.00,  NOW()),
  ('VND_USD',     0.000039, NOW());

-- -------------------------------------------------------
-- products  (sequence: product_seq, allocationSize = 50)
-- Advance the sequence so Hibernate's next batch starts at 101
-- -------------------------------------------------------
create sequence if not exists product_seq start with 1 increment by 1;
SELECT setval('product_seq', 100, true);

INSERT INTO products (id, name, price, stock, status, version) VALUES
  (1,  'Laptop Pro 15',    25000000.00, 50,  'AVAILABLE',   0),
  (2,  'Wireless Mouse',     450000.00, 200, 'AVAILABLE',   0),
  (3,  'Mechanical Keyboard',950000.00, 150, 'AVAILABLE',   0),
  (4,  'USB-C Hub',          380000.00, 300, 'AVAILABLE',   0),
  (5,  'Monitor 27"',      8500000.00,  30, 'AVAILABLE',   0),
  (6,  'Webcam HD',          750000.00, 80,  'AVAILABLE',   0),
  (7,  'SSD 1TB',           2200000.00, 60,  'AVAILABLE',   0),
  (8,  'Headphones',        1500000.00, 90,  'AVAILABLE',   0),
  (9,  'Phone Stand',        120000.00, 500, 'AVAILABLE',   0),
  (10, 'Vintage Keyboard',   550000.00, 0,   'OUT_OF_STOCK',0);

-- -------------------------------------------------------
-- orders
-- -------------------------------------------------------
INSERT INTO orders (user_id, product_id, status, saga_id, created_at, updated_at) VALUES
  -- completed flow
  (1, 1, 'COMPLETED',   'saga-aaa-001', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
  (2, 2, 'COMPLETED',   'saga-aaa-002', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
  (3, 5, 'COMPLETED',   'saga-aaa-003', NOW() - INTERVAL '1 day',  NOW() - INTERVAL '1 day'),
  -- payment failed → compensated
  (4, 7, 'COMPENSATED', 'saga-bbb-001', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
  (5, 3, 'COMPENSATED', 'saga-bbb-002', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
  -- in-progress
  (1, 4, 'STOCK_RESERVED',    'saga-ccc-001', NOW() - INTERVAL '10 minutes', NOW() - INTERVAL '10 minutes'),
  (2, 6, 'PAYMENT_COMPLETED', 'saga-ccc-002', NOW() - INTERVAL '5 minutes',  NOW() - INTERVAL '5 minutes'),
  -- freshly created
  (3, 8, 'CREATED', 'saga-ddd-001', NOW(), NOW());

-- -------------------------------------------------------
-- payments  (tied to accounts; amounts reflect product prices)
-- -------------------------------------------------------
-- INSERT INTO payments (account_id, amount, status, created_at, idempotency_key) VALUES
--   (1, 25000000.00, 'COMPLETED', NOW() - INTERVAL '3 days', 'idem-pay-001'),
--   (2,   450000.00, 'COMPLETED', NOW() - INTERVAL '2 days', 'idem-pay-002'),
--   (3,  8500000.00, 'COMPLETED', NOW() - INTERVAL '1 day',  'idem-pay-003'),
--   (4,  2200000.00, 'FAILED',    NOW() - INTERVAL '5 days', 'idem-pay-004'),
--   (5,   950000.00, 'FAILED',    NOW() - INTERVAL '4 days', 'idem-pay-005'),
--   (1,   380000.00, 'PENDING',   NOW() - INTERVAL '10 minutes', 'idem-pay-006'),
--   (2,   750000.00, 'PENDING',   NOW() - INTERVAL '5 minutes',  'idem-pay-007');
--
-- -- -------------------------------------------------------
-- -- idempotency_keys  (mirrors completed/failed payments)
-- -- -------------------------------------------------------
-- INSERT INTO idempotency_keys (idempotency_key, payment_id, processed_at) VALUES
--   ('idem-pay-001', 1, NOW() - INTERVAL '3 days'),
--   ('idem-pay-002', 2, NOW() - INTERVAL '2 days'),
--   ('idem-pay-003', 3, NOW() - INTERVAL '1 day'),
--   ('idem-pay-004', 4, NOW() - INTERVAL '5 days'),
--   ('idem-pay-005', 5, NOW() - INTERVAL '4 days');
--
-- -- -------------------------------------------------------
-- -- outbox_events
-- -- -------------------------------------------------------
-- INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at, retry_count) VALUES
--   ('Payment', 1, 'PAYMENT_COMPLETED',
--    '{"paymentId":1,"accountId":1,"amount":25000000,"sagaId":"saga-aaa-001"}',
--    'PUBLISHED', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', 0),
--
--   ('Payment', 2, 'PAYMENT_COMPLETED',
--    '{"paymentId":2,"accountId":2,"amount":450000,"sagaId":"saga-aaa-002"}',
--    'PUBLISHED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 0),
--
--   ('Payment', 3, 'PAYMENT_COMPLETED',
--    '{"paymentId":3,"accountId":3,"amount":8500000,"sagaId":"saga-aaa-003"}',
--    'PUBLISHED', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', 0),
--
--   ('Payment', 4, 'PAYMENT_FAILED',
--    '{"paymentId":4,"accountId":4,"amount":2200000,"sagaId":"saga-bbb-001"}',
--    'PUBLISHED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days', 0),
--
--   ('Payment', 5, 'PAYMENT_FAILED',
--    '{"paymentId":5,"accountId":5,"amount":950000,"sagaId":"saga-bbb-002"}',
--    'PUBLISHED', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days', 0),
--
--   -- pending events (not yet published)
--   ('Payment', 6, 'PAYMENT_PENDING',
--    '{"paymentId":6,"accountId":1,"amount":380000,"sagaId":"saga-ccc-001"}',
--    'PENDING', NOW() - INTERVAL '10 minutes', NULL, 0),
--
--   ('Payment', 7, 'PAYMENT_PENDING',
--    '{"paymentId":7,"accountId":2,"amount":750000,"sagaId":"saga-ccc-002"}',
--    'PENDING', NOW() - INTERVAL '5 minutes', NULL, 1);