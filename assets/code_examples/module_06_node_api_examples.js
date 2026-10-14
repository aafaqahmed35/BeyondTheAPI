/**
 * Module 06 - Node.js API Engineering & Security Verification Harness (Node.js v20+ / v24).
 *
 * Demonstrates and verifies:
 * 1. Trust Boundary Validation: Runtime schema parsing and unknown-property rejection (anti-mass assignment).
 * 2. Express-style Security Middleware Pipeline:
 *    - Bearer Token AuthN middleware (verifying HMAC signature, expiration).
 *    - RBAC AuthZ middleware (evaluating principal authorities).
 *    - Object-Level Authorization (BOLA/IDOR prevention).
 * 3. Parameterized SQL vs Unsafe SQL Injection demonstration.
 * 4. Sliding-Window Rate Limiter (sliding log, burst control, HTTP 429 + Retry-After headers).
 * 5. Concurrent Idempotency Key reservation and replay deduplication.
 */

const crypto = require('node:crypto');
const assert = require('node:assert');

console.log('=================================================================');
console.log('  MODULE 06 NODE.JS API SECURITY VERIFICATION TEST SUITE         ');
console.log('=================================================================');

// =========================================================================
// 1. Trust Boundary Validation & Unknown Field Rejection (Anti-Mass Assignment)
// =========================================================================

function validateUpdateUserPayload(rawInput) {
    if (typeof rawInput !== 'object' || rawInput === null || Array.isArray(rawInput)) {
        return { valid: false, errors: ['Payload must be a JSON object'] };
    }

    const ALLOWED_FIELDS = new Set(['displayName', 'bio', 'locale']);
    const errors = [];
    const sanitized = {};

    // Check for mass assignment / unknown field injection
    for (const key of Object.keys(rawInput)) {
        if (!ALLOWED_FIELDS.has(key)) {
            errors.push(`Unknown field rejected: '${key}' (mass assignment prevention)`);
        }
    }

    if (rawInput.displayName !== undefined) {
        if (typeof rawInput.displayName !== 'string' || rawInput.displayName.trim().length === 0) {
            errors.push("displayName: must be a non-empty string");
        } else if (rawInput.displayName.length > 50) {
            errors.push("displayName: maximum length is 50 characters");
        } else {
            sanitized.displayName = rawInput.displayName.trim();
        }
    }

    if (rawInput.bio !== undefined) {
        if (typeof rawInput.bio !== 'string' || rawInput.bio.length > 250) {
            errors.push("bio: must be a string of at most 250 characters");
        } else {
            sanitized.bio = rawInput.bio;
        }
    }

    if (rawInput.locale !== undefined) {
        if (!/^[a-z]{2}(-[A-Z]{2})?$/.test(rawInput.locale)) {
            errors.push("locale: must match format 'en' or 'en-US'");
        } else {
            sanitized.locale = rawInput.locale;
        }
    }

    if (errors.length > 0) {
        return { valid: false, errors };
    }
    return { valid: true, data: sanitized };
}

(function testTrustBoundaryValidation() {
    console.log('  [TEST 1] Trust Boundary Validation & Anti-Mass Assignment...');

    // Attempted mass assignment: Attacker injects 'role: admin' and 'isVerified: true'
    const hostilePayload = {
        displayName: 'Evil Hacker',
        role: 'ADMIN',
        isVerified: true
    };

    const result = validateUpdateUserPayload(hostilePayload);
    assert.strictEqual(result.valid, false, 'Hostile payload with injected role must be rejected');
    assert.strictEqual(result.errors.length, 2, 'Should reject both unknown fields');
    assert(result.errors[0].includes('Unknown field rejected: \'role\''));

    // Legitimate payload
    const validPayload = {
        displayName: '  Alice Smith  ',
        bio: 'Backend Architect',
        locale: 'en-US'
    };
    const validResult = validateUpdateUserPayload(validPayload);
    assert.strictEqual(validResult.valid, true);
    assert.strictEqual(validResult.data.displayName, 'Alice Smith', 'Whitespace should be trimmed');
    assert.strictEqual(validResult.data.role, undefined, 'No injected attributes should exist');

    console.log('    -> Passed: Unknown fields rejected at gateway, protecting domain entities.');
})();

// =========================================================================
// 2. Express-Style Security Middleware Pipeline (AuthN, RBAC, IDOR Defense)
// =========================================================================

const JWT_SECRET = 'node_jwt_test_secret_key_32_bytes_length!!';

function createToken(payload) {
    const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
    const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
    const signature = crypto.createHmac('sha256', JWT_SECRET).update(`${header}.${body}`).digest('base64url');
    return `${header}.${body}.${signature}`;
}

function verifyToken(token) {
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const [header, body, sig] = parts;
    const expectedSig = crypto.createHmac('sha256', JWT_SECRET).update(`${header}.${body}`).digest('base64url');
    if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expectedSig))) {
        return null; // Signature verification failed
    }
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf-8'));
    if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) {
        return null; // Token expired
    }
    return payload;
}

// In-memory simulated database
const documentsDb = new Map([
    ['doc_101', { id: 'doc_101', ownerId: 'user_alice', content: 'Secret Financial Plan' }],
    ['doc_102', { id: 'doc_102', ownerId: 'user_bob', content: 'HR Salary Review' }]
]);

function runMiddlewarePipeline(req, res, middlewares, handler) {
    let index = 0;
    function next(err) {
        if (err) {
            res.statusCode = err.status || 500;
            res.body = { error: err.message, code: err.code };
            return;
        }
        if (index < middlewares.length) {
            const mw = middlewares[index++];
            mw(req, res, next);
        } else {
            handler(req, res);
        }
    }
    next();
}

// Middleware 1: Authentication
function authenticate(req, res, next) {
    const authHeader = req.headers['authorization'];
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return next({ status: 401, code: 'UNAUTHENTICATED', message: 'Missing Bearer token' });
    }
    const token = authHeader.slice(7);
    const principal = verifyToken(token);
    if (!principal) {
        return next({ status: 401, code: 'INVALID_TOKEN', message: 'Token is invalid or expired' });
    }
    req.user = principal;
    next();
}

// Middleware 2: Role Authorization Factory
function requireRole(role) {
    return (req, res, next) => {
        if (!req.user || !req.user.roles || !req.user.roles.includes(role)) {
            return next({ status: 403, code: 'FORBIDDEN', message: `Required role '${role}' missing` });
        }
        next();
    };
}

// Middleware 3: Object-Level Authorization (BOLA/IDOR prevention)
function authorizeDocumentOwnership(req, res, next) {
    const docId = req.params.id;
    const doc = documentsDb.get(docId);
    if (!doc) {
        return next({ status: 404, code: 'NOT_FOUND', message: 'Document not found' });
    }
    // Check if current user is owner OR has AUDITOR role
    const isOwner = doc.ownerId === req.user.sub;
    const isAuditor = req.user.roles && req.user.roles.includes('AUDITOR');
    if (!isOwner && !isAuditor) {
        return next({ status: 403, code: 'OBJECT_ACCESS_DENIED', message: 'Not authorized to access this document' });
    }
    req.document = doc;
    next();
}

(function testSecurityMiddlewarePipeline() {
    console.log('  [TEST 2] Express Security Middleware Pipeline & BOLA Defense...');

    const aliceToken = createToken({ sub: 'user_alice', roles: ['USER'], exp: Math.floor(Date.now() / 1000) + 3600 });
    const bobToken = createToken({ sub: 'user_bob', roles: ['USER'], exp: Math.floor(Date.now() / 1000) + 3600 });

    const pipeline = [authenticate, authorizeDocumentOwnership];
    const handler = (req, res) => {
        res.statusCode = 200;
        res.body = { document: req.document };
    };

    // Case A: Alice accesses her own document (doc_101) -> 200 OK
    const req1 = { headers: { authorization: `Bearer ${aliceToken}` }, params: { id: 'doc_101' } };
    const res1 = {};
    runMiddlewarePipeline(req1, res1, pipeline, handler);
    assert.strictEqual(res1.statusCode, 200);
    assert.strictEqual(res1.body.document.id, 'doc_101');

    // Case B: Alice accesses Bob's document (doc_102) -> 403 Forbidden (BOLA blocked!)
    const req2 = { headers: { authorization: `Bearer ${aliceToken}` }, params: { id: 'doc_102' } };
    const res2 = {};
    runMiddlewarePipeline(req2, res2, pipeline, handler);
    assert.strictEqual(res2.statusCode, 403);
    assert.strictEqual(res2.body.code, 'OBJECT_ACCESS_DENIED');

    // Case C: Unauthenticated request -> 401 Unauthorized
    const req3 = { headers: {}, params: { id: 'doc_101' } };
    const res3 = {};
    runMiddlewarePipeline(req3, res3, pipeline, handler);
    assert.strictEqual(res3.statusCode, 401);

    console.log('    -> Passed: 401, 403, and BOLA defense verified in middleware sequence.');
})();

// =========================================================================
// 3. Parameterized SQL vs Unsafe SQL Injection Demonstration
// =========================================================================

(function testSqlInjectionDefense() {
    console.log('  [TEST 3] Parameterized SQL Queries vs SQL Injection...');

    const simulatedRows = [
        { id: 1, email: 'alice@example.com', pass_hash: 'hash_123', is_admin: false },
        { id: 2, email: 'admin@example.com', pass_hash: 'hash_admin', is_admin: true }
    ];

    const attackerInput = "' OR '1'='1";

    // Unsafe string concatenation query simulation:
    // SELECT * FROM users WHERE email = '' OR '1'='1'
    const unsafeSql = `SELECT * FROM users WHERE email = '${attackerInput}'`;
    // In SQL parser, (' OR '1'='1) evaluates to true for EVERY row!
    const unsafeResult = simulatedRows.filter(() => true); // Attacker exfiltrates entire table!
    assert.strictEqual(unsafeResult.length, 2, 'SQL Injection successfully dumped entire database!');

    // Safe Parameterized Query simulation:
    // Query: SELECT * FROM users WHERE email = $1, Params: [attackerInput]
    const parameterizedQuery = {
        text: 'SELECT * FROM users WHERE email = $1',
        values: [attackerInput]
    };

    // The database engine treats $1 as a literal string value, not executable SQL syntax
    const safeResult = simulatedRows.filter(row => row.email === parameterizedQuery.values[0]);
    assert.strictEqual(safeResult.length, 0, 'Zero rows returned because no user has email "\' OR \'1\'=\'1"');

    console.log('    -> Passed: Parameterized queries isolate untrusted data from the query AST.');
})();

// =========================================================================
// 4. Sliding-Window Rate Limiter Logic
// =========================================================================

class SlidingWindowRateLimiter {
    constructor(maxRequests, windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.clientLogs = new Map();
    }

    tryAcquire(clientId, now = Date.now()) {
        if (!this.clientLogs.has(clientId)) {
            this.clientLogs.set(clientId, []);
        }
        const timestamps = this.clientLogs.get(clientId);

        // Slide the window: remove timestamps older than (now - windowMs)
        const windowStart = now - this.windowMs;
        while (timestamps.length > 0 && timestamps[0] <= windowStart) {
            timestamps.shift();
        }

        if (timestamps.length < this.maxRequests) {
            timestamps.push(now);
            return {
                allowed: true,
                remaining: this.maxRequests - timestamps.length,
                retryAfterSec: 0
            };
        } else {
            // Rejected
            const earliestTimestamp = timestamps[0];
            const retryAfterSec = Math.ceil((earliestTimestamp + this.windowMs - now) / 1000);
            return {
                allowed: false,
                remaining: 0,
                retryAfterSec: Math.max(1, retryAfterSec)
            };
        }
    }
}

(function testSlidingWindowRateLimiter() {
    console.log('  [TEST 4] Sliding-Window Rate Limiter & HTTP 429 Invariants...');

    const limiter = new SlidingWindowRateLimiter(3, 1000); // 3 requests per 1000ms
    const clientId = 'ip_198.51.100.42';
    const baseTime = 100000;

    // Requests 1, 2, 3: Allowed
    assert.strictEqual(limiter.tryAcquire(clientId, baseTime).allowed, true);
    assert.strictEqual(limiter.tryAcquire(clientId, baseTime + 100).allowed, true);
    assert.strictEqual(limiter.tryAcquire(clientId, baseTime + 200).allowed, true);

    // Request 4 (within same window): Rejected with 429
    const blocked = limiter.tryAcquire(clientId, baseTime + 300);
    assert.strictEqual(blocked.allowed, false);
    assert.strictEqual(blocked.remaining, 0);
    assert(blocked.retryAfterSec > 0, 'Must provide Retry-After delta');

    // Request 5 (after window slides past baseTime): Allowed
    const allowedLater = limiter.tryAcquire(clientId, baseTime + 1050);
    assert.strictEqual(allowedLater.allowed, true, 'Window slide should evict oldest request');

    console.log('    -> Passed: Sliding window prevents boundary bursting and calculates Retry-After.');
})();

// =========================================================================
// 5. Concurrent Idempotency Key Locking & Deduplication
// =========================================================================

class AsyncIdempotencyCoordinator {
    constructor() {
        this.cache = new Map(); // key -> { status: 'PENDING'|'DONE', result: any }
    }

    async process(key, workerFn) {
        const existing = this.cache.get(key);
        if (existing) {
            if (existing.status === 'PENDING') {
                return { status: 409, body: { error: 'CONCURRENT_REQUEST_IN_PROGRESS' }, replayed: false };
            }
            if (existing.status === 'DONE') {
                return { status: 200, body: existing.result, replayed: true };
            }
        }

        // Reserve key atomically
        this.cache.set(key, { status: 'PENDING' });

        try {
            const result = await workerFn();
            this.cache.set(key, { status: 'DONE', result });
            return { status: 200, body: result, replayed: false };
        } catch (err) {
            this.cache.delete(key); // Release key on error so client can retry
            throw err;
        }
    }
}

(async function testConcurrentIdempotency() {
    console.log('  [TEST 5] Concurrent Idempotency Locking & Replay Deduplication...');

    const coordinator = new AsyncIdempotencyCoordinator();
    let counter = 0;

    const slowOperation = () => new Promise(resolve => {
        setTimeout(() => resolve({ orderId: 'ord_999', chargeAmount: 1200, seq: ++counter }), 20);
    });

    const key = 'idem_txn_abc_123';

    // Fire 2 concurrent requests with the identical idempotency key
    const p1 = coordinator.process(key, slowOperation);
    const p2 = coordinator.process(key, slowOperation);

    const [res1, res2] = await Promise.all([p1, p2]);

    // One request must win the reservation (status 200), and the second must be rejected with 409 Conflict
    const has200 = res1.status === 200 || res2.status === 200;
    const has409 = res1.status === 409 || res2.status === 409;

    assert(has200, 'One request must succeed');
    assert(has409, 'Concurrent duplicate request must receive 409 Conflict');
    assert.strictEqual(counter, 1, 'Underlying payment operation must execute only once!');

    // Sequential subsequent request: Replays cached result
    const res3 = await coordinator.process(key, slowOperation);
    assert.strictEqual(res3.status, 200);
    assert.strictEqual(res3.replayed, true, 'Subsequent call must return replayed cached response');
    assert.strictEqual(res3.body.orderId, 'ord_999');
    assert.strictEqual(counter, 1, 'Counter remained at 1 across all calls');

    console.log('    -> Passed: Concurrency lock prevents double-spend race condition.');
    console.log('\nAll 5 Node.js API Engineering & Security Test Suites Passed Successfully!');
})();
