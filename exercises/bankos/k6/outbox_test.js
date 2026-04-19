// k6/outbox_test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const paymentsOk    = new Counter('outbox_payments_ok');
const duplicates    = new Counter('outbox_duplicates_caught');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        // Phase 1 — normal payments, each with unique idempotency key
        normal_payments: {
            executor: 'constant-vus',
            vus: 5,
            duration: '2s',
            startTime: '0s',
            exec: 'normalPayment',
        },
        // Phase 2 — retry simulation: same key sent twice
        retry_simulation: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 1,
            startTime: '2s',
            exec: 'duplicatePayment',
        },
    },
};

export function normalPayment() {
    const key = `key-${__VU}-${__ITER}-${Date.now()}`;
    const res = http.post(
        `${BASE_URL}/api/payments/with-outbox`,
        JSON.stringify({ accountId: 1, amount: 10.00 }),
        { headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': key,
            }}
    );
    check(res, { 'payment ok': (r) => r.status === 200 });
    if (res.status === 200) paymentsOk.add(1);
    sleep(0.5);
}

export function duplicatePayment() {
    // Same key sent twice — second should return cached result
    const key = `dup-key-${__VU}`;
    const payload = JSON.stringify({ accountId: 1, amount: 50.00 });
    const headers = {
        'Content-Type': 'application/json',
        'Idempotency-Key': key,
    };

    const first  = http.post(`${BASE_URL}/api/payments/with-outbox`, payload, { headers });
    const second = http.post(`${BASE_URL}/api/payments/with-outbox`, payload, { headers });

    const body1 = JSON.parse(first.body  || '{}');
    const body2 = JSON.parse(second.body || '{}');

    // Both must return same payment ID — idempotency working
    const sameId = body1.id === body2.id;
    check(first,  { 'first ok':  (r) => r.status === 200 });
    check(second, { 'second ok': (r) => r.status === 200 });

    if (sameId) duplicates.add(1);
    console.log(`[VU:${__VU}] first=${body1.id} second=${body2.id} sameId=${sameId}`);
}

export function handleSummary(data) {
    const v = (key) => data.metrics[key]?.values?.count ?? 0;
    const summary = `
╔══════════════════════════════════════════╗
║         Outbox Pattern Test Summary      ║
╠══════════════════════════════════════════╣
  Payments processed : ${v('outbox_payments_ok')}
  Duplicates caught  : ${v('outbox_duplicates_caught')} / 5 expected
╚══════════════════════════════════════════╝
`;
    console.log(summary);
    return { stdout: summary };
}