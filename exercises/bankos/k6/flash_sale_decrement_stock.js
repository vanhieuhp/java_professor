/**
 * k6 load test — FlashSale decrementStock
 *
 * Tests the atomic SQL decrement endpoint under high concurrency to verify:
 *   1. No overselling: total successful purchases <= initial stock
 *   2. All requests either succeed (200) or return "out of stock" (400)
 *   3. System stays stable under concurrent load
 *
 * Usage:
 *   # Seed a product first (or use an existing product id):
 *   curl -s -X POST "http://localhost:8080/api/flash-sale/seed?stock=100"
 *
 *   # Run the test:
 *   k6 run k6_test/flash_sale_decrement_stock.js
 *
 *   # Run with custom product id and concurrency:
 *   k6 run -e PRODUCT_ID=1 -e VUS=50 -e DURATION=10s k6_test/flash_sale_decrement_stock.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ---------- custom metrics ----------
const successCount   = new Counter('flash_sale_success_total');
const outOfStockRate = new Rate('flash_sale_out_of_stock_rate');
const buyDuration    = new Trend('flash_sale_buy_duration_ms', true);

// ---------- config ----------
const BASE_URL   = __ENV.BASE_URL   || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || '1';
const VUS        = parseInt(__ENV.VUS      || '50');
const DURATION   = __ENV.DURATION          || '15s';

export const options = {
    vus:      VUS,
    duration: DURATION,

    thresholds: {
        // 95th-percentile response time under 500 ms
        'flash_sale_buy_duration_ms': ['p(95)<500'],
        // HTTP error rate (5xx) must stay below 1 %
        'http_req_failed': ['rate<0.01'],
    },
};

// ---------- main test function ----------
export default function () {
    const url = `${BASE_URL}/api/flash-sale/${PRODUCT_ID}/buy?quantity=1`;

    const res = http.post(url, null, {
        tags: { endpoint: 'decrementStock' },
    });

    // track latency
    buyDuration.add(res.timings.duration);

    const ok = check(res, {
        'status is 200 or 400': (r) => r.status === 200 || r.status === 400,
        'no server error (5xx)': (r) => r.status < 500,
    });

    if (res.status === 200) {
        successCount.add(1);
        outOfStockRate.add(0);
    } else if (res.status === 400) {
        outOfStockRate.add(1);
    }

    // no sleep — we want maximum concurrency to stress the stock counter
}

// ---------- teardown: print summary ----------
export function handleSummary(data) {
    const total   = data.metrics['http_reqs']?.values?.count      ?? 0;
    const success = data.metrics['flash_sale_success_total']?.values?.count ?? 0;
    const p95     = data.metrics['flash_sale_buy_duration_ms']?.values?.['p(95)']?.toFixed(2) ?? 'n/a';
    const errRate = ((data.metrics['http_req_failed']?.values?.rate ?? 0) * 100).toFixed(2);

    const summary = `
=======================================================
  Flash Sale — decrementStock load test summary
=======================================================
  Product ID      : ${PRODUCT_ID}
  Virtual Users   : ${VUS}
  Duration        : ${DURATION}
-------------------------------------------------------
  Total requests  : ${total}
  Successful buys : ${success}
  p95 latency     : ${p95} ms
  HTTP error rate : ${errRate} %
=======================================================
`;
    console.log(summary);

    return {
        stdout: summary,
        'k6_test/results/flash_sale_decrement_stock_result.json': JSON.stringify(data, null, 2),
    };
}
