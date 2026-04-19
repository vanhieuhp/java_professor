// k6/redis_idempotency_test.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const successCount   = new Counter('safe_payment_success');
const duplicateSame  = new Counter('safe_payment_same_id');    // good — deduped
const duplicateDiff  = new Counter('safe_payment_diff_id');    // bad  — not deduped

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        // Phase 1 — race condition test
        // 20 VUs all send the SAME idempotency key simultaneously
        // Expected: exactly 1 payment created, all return same payment ID
        race_condition: {
            executor: 'constant-vus',
            vus: 20,
            duration: '10s',
            startTime: '0s',
            exec: 'raceRequest',
        },
        // Phase 2 — unique keys test
        // Each VU uses its own key — all should succeed independently
        unique_keys: {
            executor: 'constant-vus',
            vus: 10,
            duration: '10s',
            startTime: '15s',
            exec: 'uniqueRequest',
        },
    },
};

// Track the first payment ID seen for the shared key
let firstPaymentId = null;

export function raceRequest() {
    // ALL VUs use the same key — this is the race condition
    const sharedKey = 'race-key-fixed';

    const res = http.post(
        `${BASE_URL}/api/payments/safe`,
        JSON.stringify({ accountId: 1, amount: 10.00 }),
        { headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': sharedKey,
            }}
    );

    check(res, { 'no 500': (r) => r.status < 500 });

    if (res.status === 200) {
        const body = JSON.parse(res.body || '{}');
        successCount.add(1);

        if (firstPaymentId === null) {
            firstPaymentId = body.id;
        }

        // All responses must return the same payment ID
        if (body.id === firstPaymentId) {
            duplicateSame.add(1);   // ✅ correctly deduped
        } else {
            duplicateDiff.add(1);   // ❌ duplicate payment created
        }

        console.log(`[VU:${__VU}] paymentId=${body.id} expected=${firstPaymentId}`);
    }
    sleep(0.1);
}

export function uniqueRequest() {
    // Each VU has its own key — all should succeed independently
    const uniqueKey = `unique-key-vu-${__VU}-${__ITER}`;

    const res = http.post(
        `${BASE_URL}/api/payments/safe`,
        JSON.stringify({ accountId: 1, amount: 5.00 }),
        { headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': uniqueKey,
            }}
    );

    check(res, {
        'unique payment ok': (r) => r.status === 200,
    });

    if (res.status === 200) successCount.add(1);
    sleep(0.5);
}

export function handleSummary(data) {
    const v = (key) => data.metrics[key]?.values?.count ?? 0;

    const same = v('safe_payment_same_id');
    const diff = v('safe_payment_diff_id');
    const deduped = diff === 0 ? '✅ PASS' : '❌ FAIL — duplicates created!';

    const summary = `
╔══════════════════════════════════════════╗
║     Redis Idempotency Test Summary       ║
╠══════════════════════════════════════════╣
  Race condition test (20 VUs, 1 key):
  Responses with same payment ID : ${same}  ← all should match
  Responses with diff payment ID : ${diff}  ← must be 0
  Deduplication result           : ${deduped}

  Unique keys test:
  Successful payments            : ${v('safe_payment_success')}
╚══════════════════════════════════════════╝
`;
    console.log(summary);
    return { stdout: summary };
}