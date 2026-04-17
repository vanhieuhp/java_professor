import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080/api/products';

const payload = JSON.stringify({
    name: 'Widget',
    price: 99.99,
    stock: 50
});

const headers = { 'Content-Type': 'application/json' };

export const options = {
    scenarios: {
        // Scenario 1 — broken: 10 concurrent users, pool exhausts fast
        broken: {
            executor: 'constant-vus',
            vus: 10,
            duration: '15s',
            exec: 'brokenScenario',
            startTime: '0s',
        },
        // Scenario 2 — fixed: 10 concurrent users, no pool exhaustion
        fixed: {
            executor: 'constant-vus',
            vus: 10,
            duration: '15s',
            exec: 'fixedScenario',
            startTime: '20s',
        },
    },
};

// ❌ BROKEN — connections exhausted, requests timeout
export function brokenScenario() {
    const res = http.post(`${BASE_URL}/broken`, payload, {
        headers,
        timeout: '10s',
    });

    check(res, {
        'status 200': (r) => r.status === 200,
        'no timeout': (r) => r.status !== 500,
    });

    console.log(`[BROKEN] status=${res.status} duration=${res.timings.duration}ms`);
    sleep(0.1);
}

// ✅ FIXED — connections released quickly, no timeout
export function fixedScenario() {
    const res = http.post(`${BASE_URL}/fixed`, payload, {
        headers,
        timeout: '10s',
    });

    check(res, {
        'status 200': (r) => r.status === 200,
        'no timeout': (r) => r.status !== 500,
    });

    console.log(`[FIXED] status=${res.status} duration=${res.timings.duration}ms`);
    sleep(0.1);
}