import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const successCount     = new Counter('cb_success');
const circuitOpenCount = new Counter('cb_circuit_open');
const bulkheadCount    = new Counter('cb_bulkhead_reject');
const failureCount     = new Counter('cb_failure');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS  = { 'Content-Type': 'application/json' };

export const options = {
    scenarios: {
        // Phase 1 — normal load, circuit stays CLOSED
        // phase1_normal: {
        //     executor: 'constant-vus',
        //     vus: 3,
        //     duration: '15s',
        //     startTime: '0s',
        //     exec: 'normalRequest',
        //     tags: { phase: 'normal' },
        // },
        // Phase 2 — inject failures, trip circuit OPEN
        phase2_failures: {
            executor: 'constant-vus',
            vus: 3,
            duration: '15s',
            startTime: '18s',
            exec: 'failingRequest',
            tags: { phase: 'failure' },
        },
        // Phase 3 — normal again, observe HALF_OPEN → CLOSED recovery
        // phase3_recovery: {
        //     executor: 'constant-vus',
        //     vus: 3,
        //     duration: '15s',
        //     startTime: '38s',
        //     exec: 'normalRequest',
        //     tags: { phase: 'recovery' },
        // },
    },
};

export function normalRequest() {
    const res = call(false);
    trackResult(res);
    sleep(0.5);
}

export function failingRequest() {
    const res = call(true);   // simulateFailure=true → forces RuntimeException
    trackResult(res);
    sleep(0.5);
}

function call(simulateFailure) {
    return http.post(
        `${BASE_URL}/api/payments/gateway`,
        JSON.stringify({
            accountId: 1,
            amount: 100.00,
            idempotencyKey: `key-${__VU}-${__ITER}-${Date.now()}`,
            simulateFailure: simulateFailure,
        }),
        { headers: HEADERS }
    );
}

function trackResult(res) {
    check(res, { 'no 500': (r) => r.status !== 500 });

    const body = JSON.parse(res.body || '{}');
    const msg  = body.message || '';

    if (body.status === 'SUCCESS')          successCount.add(1);
    else if (msg === 'CIRCUIT_OPEN')        circuitOpenCount.add(1);
    else if (msg === 'TOO_MANY_REQUESTS')   bulkheadCount.add(1);
    else                                    failureCount.add(1);

    console.log(`[VU:${__VU} ITER:${__ITER}] HTTP ${res.status} → ${msg || body.status}`);
}

export function handleSummary(data) {
    const m   = data.metrics;
    const val = (key) => m[key]?.values?.count ?? 0;

    const summary = `
╔══════════════════════════════════════════╗
║      Resilience4j — Test Summary         ║
╠══════════════════════════════════════════╣
  Phase 1 (0-15s):  normal load
  Phase 2 (18-33s): forced failures → trip circuit
  Phase 3 (38-53s): recovery → HALF_OPEN → CLOSED
╠══════════════════════════════════════════╣
  SUCCESS          : ${val('cb_success')}
  CIRCUIT_OPEN     : ${val('cb_circuit_open')}
  BULKHEAD_REJECT  : ${val('cb_bulkhead_reject')}
  OTHER_FAILURE    : ${val('cb_failure')}
╚══════════════════════════════════════════╝
`;
    console.log(summary);
    return { stdout: summary };
}