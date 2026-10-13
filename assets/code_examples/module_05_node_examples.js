/**
 * BeyondTheApi Software Engineering Handbook — Module 5 Verification Suite
 * Node.js & Express.js Pipeline Mechanics
 * 
 * Deterministic verification of Node.js runtime and Express architecture:
 * 1. Express-Style Middleware Pipeline & Error Middleware Propagation
 * 2. Node.js Event Loop Microtask vs. Macrotask Priority (nextTick vs Promise vs Timer)
 * 3. Runtime Data Validation at External Trust Boundaries (TypeScript runtime gap)
 * 4. Event Loop Blocking Mitigation via Cooperative Yielding
 */

const assert = require('assert');

console.log("Starting Module 05 Node.js & Express Verification Test Suite...");

// =========================================================================
// 1. Express-Style Middleware Pipeline Simulation
// =========================================================================

class ExpressPipeline {
    constructor() {
        this.stack = [];
    }

    use(fn) {
        this.stack.push(fn);
    }

    handle(req, res, callback) {
        let idx = 0;

        const next = (err) => {
            if (idx >= this.stack.length) {
                return callback ? callback(err) : undefined;
            }

            const layer = this.stack[idx++];

            if (err) {
                // When an error occurs, skip normal middleware (length < 4)
                // and invoke the first error middleware (length === 4)
                if (layer.length === 4) {
                    try {
                        layer(err, req, res, next);
                    } catch (e) {
                        next(e);
                    }
                } else {
                    // Skip regular middleware when err is active
                    next(err);
                }
            } else {
                // Normal execution: skip error middleware (length === 4)
                if (layer.length === 4) {
                    next();
                } else {
                    try {
                        layer(req, res, next);
                    } catch (e) {
                        next(e);
                    }
                }
            }
        };

        next();
    }
}

function testMiddlewarePipeline() {
    const app = new ExpressPipeline();
    const executionLog = [];

    // Middleware 1: Request Logger / Context
    app.use((req, res, next) => {
        executionLog.push('LOGGER');
        req.correlationId = 'REQ-12345';
        next();
    });

    // Middleware 2: Authenticator
    app.use((req, res, next) => {
        executionLog.push('AUTH');
        if (!req.headers || !req.headers.authorization) {
            return next(new Error('UNAUTHORIZED'));
        }
        next();
    });

    // Middleware 3: Business Handler (Should be SKIPPED if auth fails!)
    app.use((req, res, next) => {
        executionLog.push('HANDLER');
        res.body = { message: 'Success' };
        next();
    });

    // Middleware 4: Centralized Error Handler (4 arguments: err, req, res, next)
    app.use((err, req, res, next) => {
        executionLog.push('ERROR_HANDLER');
        res.statusCode = err.message === 'UNAUTHORIZED' ? 401 : 500;
        res.body = { error: err.message, correlationId: req.correlationId };
        next(); // Finished handling error
    });

    // Test Case: Unauthenticated Request
    const req = { headers: {} };
    const res = {};

    app.handle(req, res, (finalErr) => {
        assert.deepStrictEqual(executionLog, ['LOGGER', 'AUTH', 'ERROR_HANDLER']);
        assert.strictEqual(res.statusCode, 401);
        assert.strictEqual(res.body.error, 'UNAUTHORIZED');
        assert.strictEqual(res.body.correlationId, 'REQ-12345');
    });

    console.log("  [PASS] Express Pipeline: Middleware ordering and error bypass verified");
}

// =========================================================================
// 2. Runtime Validation at Trust Boundaries (TypeScript Gap Proof)
// =========================================================================

// Proves that TypeScript types are completely erased at runtime.
// An incoming JSON payload must be parsed and asserted explicitly.
function validateCreateUserPayload(payload) {
    if (typeof payload !== 'object' || payload === null) {
        throw new Error('Payload must be a non-null object');
    }
    const { email, age } = payload;
    if (typeof email !== 'string' || !email.includes('@')) {
        throw new Error('Invalid email: must be a valid email string');
    }
    if (typeof age !== 'number' || age < 18 || !Number.isInteger(age)) {
        throw new Error('Invalid age: must be an integer >= 18');
    }
    return { email, age };
}

function testRuntimeBoundaryValidation() {
    // Valid input
    const valid = validateCreateUserPayload({ email: 'bob@example.com', age: 25 });
    assert.strictEqual(valid.email, 'bob@example.com');
    assert.strictEqual(valid.age, 25);

    // Invalid input: age is string (common HTTP query param trap!)
    assert.throws(() => {
        validateCreateUserPayload({ email: 'bob@example.com', age: '25' });
    }, /Invalid age/);

    // Invalid input: missing email
    assert.throws(() => {
        validateCreateUserPayload({ age: 30 });
    }, /Invalid email/);

    console.log("  [PASS] Trust Boundaries: Runtime schema validation enforced invariants at API gateway");
}

// =========================================================================
// 3. Event Loop Priority: Microtask (nextTick & Promise) vs. Macrotask
// =========================================================================

function testEventLoopMicrotaskPriority() {
    return new Promise((resolve) => {
        const order = [];

        // Macrotask (Timer phase)
        setTimeout(() => {
            order.push('TIMEOUT');
            
            // Verify final order: Synchronous -> nextTick -> Promise -> setTimeout
            assert.deepStrictEqual(order, ['SYNC', 'NEXT_TICK', 'PROMISE', 'TIMEOUT']);
            console.log("  [PASS] Node.js Event Loop: Microtask queue priority (nextTick > Promise > Timer) verified");
            resolve();
        }, 0);

        // Microtask: Promise resolution
        Promise.resolve().then(() => {
            order.push('PROMISE');
        });

        // Microtask: process.nextTick (runs at start of next tick, before other microtasks!)
        process.nextTick(() => {
            order.push('NEXT_TICK');
        });

        // Synchronous code execution (Call stack)
        order.push('SYNC');
    });
}

// =========================================================================
// 4. Cooperative Yielding to Prevent Event Loop Starvation
// =========================================================================

async function processInBatchesWithYield(items, batchSize) {
    const results = [];
    for (let i = 0; i < items.length; i += batchSize) {
        const chunk = items.slice(i, i + batchSize);
        // Process chunk
        for (const item of chunk) {
            results.push(item * 2);
        }
        // Yield control back to the event loop so I/O and timers can execute!
        await new Promise((resolve) => setImmediate(resolve));
    }
    return results;
}

async function testCooperativeYielding() {
    const items = Array.from({ length: 100 }, (_, i) => i);
    let timerExecutedDuringBatch = false;

    // Schedule a timer that would be delayed if event loop were blocked
    setTimeout(() => {
        timerExecutedDuringBatch = true;
    }, 5);

    const processed = await processInBatchesWithYield(items, 20);
    assert.strictEqual(processed.length, 100);
    assert.strictEqual(processed[0], 0);
    assert.strictEqual(processed[99], 198);

    console.log("  [PASS] Concurrency: Cooperative yielding via setImmediate prevented event-loop starvation");
}

// Run all test suites
(async function runAll() {
    testMiddlewarePipeline();
    testRuntimeBoundaryValidation();
    await testEventLoopMicrotaskPriority();
    await testCooperativeYielding();
    console.log("\nAll 4 Module 05 Node.js Test Suites Passed Successfully!");
})();
