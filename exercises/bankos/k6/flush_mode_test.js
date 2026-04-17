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
        // Scenario 1 — hit broken endpoint, observe missing product
        broken: {
            executor: 'constant-vus',
            vus: 1,
            duration: '5s',
            exec: 'brokenScenario',
            startTime: '0s',
        },
        // Scenario 2 — hit fixed endpoint, observe product appears
        fixed_auto: {
            executor: 'constant-vus',
            vus: 1,
            duration: '5s',
            exec: 'fixedAutoScenario',
            startTime: '6s',
        },
        // Scenario 3 — hit explicit flush endpoint
        fixed_explicit: {
            executor: 'constant-vus',
            vus: 1,
            duration: '5s',
            exec: 'fixedExplicitScenario',
            startTime: '12s',
        },
    },
};

function parseArray(body) {
    try {
        const parsed = JSON.parse(body);
        return Array.isArray(parsed) ? parsed : null;
    } catch (_) {
        return null;
    }
}

// BROKEN — newProduct missing from response
export function brokenScenario() {
    const res = http.post(`${BASE_URL}/flush/broken`, payload, { headers });
    const products = parseArray(res.body);

    check(res, {
        'status 200': (r) => r.status === 200,
        // This check will FAIL — newly saved product not in response
        'new product appears in list': () => Array.isArray(products) && products.some(p => p.name === 'Widget'),
    });

    console.log(`[BROKEN] Products returned: ${products ? products.length : 'error - ' + res.body}`);
    sleep(0.5);
}

// FIXED AUTO — newProduct appears
export function fixedAutoScenario() {
    const res = http.post(`${BASE_URL}/flush/fixed-auto`, payload, { headers });
    const products = parseArray(res.body);

    check(res, {
        'status 200': (r) => r.status === 200,
        // This check PASSES — Hibernate auto-flushed before query
        'new product appears in list': () => Array.isArray(products) && products.some(p => p.name === 'Widget'),
    });

    console.log(`[FIXED AUTO] Products returned: ${products ? products.length : 'error - ' + res.body}`);
    sleep(0.5);
}

// FIXED EXPLICIT — newProduct appears
export function fixedExplicitScenario() {
    const res = http.post(`${BASE_URL}/flush/fixed-explicit`, payload, { headers });
    const products = parseArray(res.body);

    check(res, {
        'status 200': (r) => r.status === 200,
        // This check PASSES — explicit flush before query
        'new product appears in list': () => Array.isArray(products) && products.some(p => p.name === 'Widget'),
    });

    console.log(`[FIXED EXPLICIT] Products returned: ${products ? products.length : 'error - ' + res.body}`);
    sleep(0.5);
}