// k6/saga_oversell_test.js
import http from 'k6/http';
import {check} from 'k6';
import {Counter} from 'k6/metrics';

const completed   = new Counter('saga_completed');
const failed      = new Counter('saga_failed');
const compensated = new Counter('saga_compensated');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS  = { 'Content-Type': 'application/json' };

// Runs once before all VUs start — seeds fresh products and returns their IDs
export function setup() {
    const r1 = http.post(`${BASE_URL}/api/products/seed?stock=1`);
    const r2 = http.post(`${BASE_URL}/api/products/seed?stock=5`);

    const productId1 = parseInt(r1.body.match(/id=(\d+)/)?.[1]);
    const productId2 = parseInt(r2.body.match(/id=(\d+)/)?.[1]);

    console.log(`[setup] Seeded oversell product id=${productId1} stock=1`);
    console.log(`[setup] Seeded compensation product id=${productId2} stock=5`);

    return { productId1, productId2 };
}

export const options = {
    scenarios: {
        // 20 users race to buy the same product — only 1 should win
        oversell_test: {
            executor: 'per-vu-iterations',
            vus: 20,
            iterations: 1,      // each VU fires exactly once
            exec: 'buyProduct',
        },
        // Compensation test — payment always fails
        compensation_test: {
            executor: 'per-vu-iterations',
            vus: 5,
            iterations: 1,
            startTime: '2s',
            exec: 'buyWithFailure',
        },
    },
};

export function buyProduct(data) {
    const res = http.post(`${BASE_URL}/api/orders`,
        JSON.stringify({
            userId: __VU,
            productId: data.productId1,
            quantity: 1,
            amount: 100.00,
            simulatePaymentFailure: false,
        }),
        { headers: HEADERS }
    );

    const body = JSON.parse(res.body || '{}');
    check(res, { 'no 500': (r) => r.status < 500 });

    if (body.status === 'COMPLETED')   completed.add(1);
    else if (body.status === 'FAILED') failed.add(1);

    console.log(`[VU:${__VU}] ${body.status} — ${body.message}`);
}

export function buyWithFailure(data) {
    const res = http.post(`${BASE_URL}/api/orders`,
        JSON.stringify({
            userId: __VU,
            productId: data.productId2,
            quantity: 1,
            amount: 100.00,
            simulatePaymentFailure: true,
        }),
        { headers: HEADERS }
    );

    const body = JSON.parse(res.body || '{}');
    check(res, { 'no 500': (r) => r.status < 500 });

    if (body.status === 'COMPENSATED') compensated.add(1);

    console.log(`[VU:${__VU}] ${body.status} — ${body.message}`);
}

export function handleSummary(data) {
    const v = (key) => data.metrics[key]?.values?.count ?? 0;
    const summary = `
╔══════════════════════════════════════════╗
║       Saga Oversell Test Summary         ║
╠══════════════════════════════════════════╣
  Oversell test (20 VUs, 1 item):
  COMPLETED   : ${v('saga_completed')}  ← must be 1
  FAILED      : ${v('saga_failed')}     ← must be 19

  Compensation test:
  COMPENSATED : ${v('saga_compensated')} ← all should compensate
╚══════════════════════════════════════════╝
`;
    console.log(summary);
    return { stdout: summary };
}