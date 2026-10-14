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

// Fail-safe configuration validation at startup
const JWT_SECRET = process.env.JWT_SECRET || 'node_jwt_test_secret_key_32_bytes_length!!';
if (!JWT_SECRET || typeof JWT_SECRET !== 'string' || JWT_SECRET.length < 32) {
    throw new Error('FATAL: Missing or insecure JWT_SECRET configuration. Must be >= 32 chars.');
}

function createToken(payload) {
    const defaultClaims = {
        iss: 'https://auth.example.com',
        aud: 'https://api.example.com',
        exp: Math.floor(Date.now() / 1000) + 3600
    };
    const fullPayload = { ...defaultClaims, ...payload };
    const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url');
    const body = Buffer.from(JSON.stringify(fullPayload)).toString('base64url');
    const signature = crypto.createHmac('sha256', JWT_SECRET).update(`${header}.${body}`).digest('base64url');
    return `${header}.${body}.${signature}`;
}

function verifyToken(token) {
    if (typeof token !== 'string') return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const [header, body, sig] = parts;
    const expectedSig = crypto.createHmac('sha256', JWT_SECRET).update(`${header}.${body}`).digest('base64url');
    if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expectedSig))) {
        return null; // Signature verification failed
    }
    let payload;
    try {
        payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf-8'));
    } catch {
        return null;
    }
    // Trust Boundary Claim Verification (sub, role, iss, aud, exp validation)
    if (typeof payload !== 'object' || payload === null) return null;
    if (typeof payload.sub !== 'string' || payload.sub.trim().length === 0) return null;
    if (!Array.isArray(payload.roles) || payload.roles.length === 0) return null;
    if (payload.iss !== 'https://auth.example.com') return null;
    if (payload.aud !== 'https://api.example.com') return null;
    if (typeof payload.exp !== 'number' || payload.exp < Math.floor(Date.now() / 1000)) {
        return null; // Token expired or invalid expiration
    }

    return {
        sub: payload.sub,
        roles: payload.roles,
        tenantId: payload.tenantId || 'tenant_default',
        iss: payload.iss,
        aud: payload.aud,
        exp: payload.exp
    };
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
        return next({ status: 401, code: 'INVALID_TOKEN', message: 'Token is invalid, expired, or malformed' });
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

    const aliceToken = createToken({ sub: 'user_alice', roles: ['USER'] });
    const bobToken = createToken({ sub: 'user_bob', roles: ['USER'] });

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

    // Case D: Invalid claims (e.g. wrong audience or missing sub) -> 401 Unauthorized
    const invalidAudToken = createToken({ sub: 'user_alice', roles: ['USER'], aud: 'https://wrong-service.com' });
    const req4 = { headers: { authorization: `Bearer ${invalidAudToken}` }, params: { id: 'doc_101' } };
    const res4 = {};
    runMiddlewarePipeline(req4, res4, pipeline, handler);
    assert.strictEqual(res4.statusCode, 401);

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
// 4. Sliding-Window Rate Limiter Logic & Redis ZSET Collision Prevention
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

/**
 * Emulates Redis Sorted Set (ZSET) behavior to demonstrate same-millisecond collision
 * bug and verify the unique-member fix.
 */
class SimulatedRedisZsetRateLimiter {
    constructor(maxRequests, windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.zsets = new Map(); // key -> Map(member -> score)
    }

    // Flawed implementation: uses timestamp alone as ZSET member
    tryAcquireFlawed(key, now) {
        if (!this.zsets.has(key)) this.zsets.set(key, new Map());
        const zset = this.zsets.get(key);
        const clearBefore = now - this.windowMs;
        for (const [member, score] of zset.entries()) {
            if (score <= clearBefore) zset.delete(member);
        }
        if (zset.size < this.maxRequests) {
            // BUG: In Redis ZSET, members must be unique. Using timestamp alone causes collision!
            zset.set(String(now), now);
            return { allowed: true, count: zset.size };
        }
        return { allowed: false, count: zset.size };
    }

    // Fixed implementation: appends unique requestId / nonce to member
    tryAcquireFixed(key, now, requestId) {
        if (!this.zsets.has(key)) this.zsets.set(key, new Map());
        const zset = this.zsets.get(key);
        const clearBefore = now - this.windowMs;
        for (const [member, score] of zset.entries()) {
            if (score <= clearBefore) zset.delete(member);
        }
        if (zset.size < this.maxRequests) {
            // FIX: Unique member `now:requestId` guarantees distinct entries per request
            const member = `${now}:${requestId}`;
            zset.set(member, now);
            return { allowed: true, count: zset.size };
        }
        return { allowed: false, count: zset.size };
    }
}

(function testSlidingWindowRateLimiter() {
    console.log('  [TEST 4] Sliding-Window Rate Limiter & Redis ZSET Collision Invariants...');

    // 1. In-memory sliding log verification
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

    // 2. Redis ZSET Same-Millisecond Member Collision Verification
    const redisLimiter = new SimulatedRedisZsetRateLimiter(2, 1000); // Limit 2 requests
    const redisKey = 'rate:user_42';
    const sameMsTime = 200000;

    // Flawed limiter: 3 requests at the exact same millisecond all pass because members collide!
    const f1 = redisLimiter.tryAcquireFlawed(redisKey, sameMsTime);
    const f2 = redisLimiter.tryAcquireFlawed(redisKey, sameMsTime);
    const f3 = redisLimiter.tryAcquireFlawed(redisKey, sameMsTime);
    assert.strictEqual(f1.allowed, true);
    assert.strictEqual(f2.allowed, true);
    assert.strictEqual(f3.allowed, true, 'Flawed limiter permits burst bypass due to ZSET member overwrite');

    // Fixed limiter: each request receives unique member (now:requestId)
    const fixedKey = 'rate:user_fixed_42';
    const r1 = redisLimiter.tryAcquireFixed(fixedKey, sameMsTime, 'req_001');
    const r2 = redisLimiter.tryAcquireFixed(fixedKey, sameMsTime, 'req_002');
    const r3 = redisLimiter.tryAcquireFixed(fixedKey, sameMsTime, 'req_003');
    assert.strictEqual(r1.allowed, true, 'Request 1 allowed');
    assert.strictEqual(r2.allowed, true, 'Request 2 allowed (hits limit)');
    assert.strictEqual(r3.allowed, false, 'Request 3 correctly rejected: unique member prevented collision');

    console.log('    -> Passed: Sliding window and Redis ZSET collision fix verified.');
})();

// =========================================================================
// 5. Concurrent Idempotency Key Locking, Durable Failures & Deduplication
// =========================================================================

class AsyncIdempotencyCoordinator {
    constructor() {
        this.cache = new Map(); // key -> { status: 'PENDING'|'DONE'|'RECONCILIATION_REQUIRED', result: any }
    }

    async process(key, workerFn, options = { hasExternalSideEffects: false }) {
        const existing = this.cache.get(key);
        if (existing) {
            if (existing.status === 'PENDING') {
                return { status: 409, body: { error: 'CONCURRENT_REQUEST_IN_PROGRESS' }, replayed: false };
            }
            if (existing.status === 'DONE') {
                return { status: 200, body: existing.result, replayed: true };
            }
            if (existing.status === 'RECONCILIATION_REQUIRED') {
                return { status: 409, body: { error: 'TRANSACTION_PENDING_RECONCILIATION', downstreamKey: existing.downstreamKey }, replayed: false };
            }
        }

        // Reserve key atomically with creation timestamp
        this.cache.set(key, { status: 'PENDING', createdAt: Date.now() });

        try {
            const result = await workerFn();
            this.cache.set(key, { status: 'DONE', result });
            return { status: 200, body: result, replayed: false };
        } catch (err) {
            if (options.hasExternalSideEffects) {
                // When external side-effects (payment dispatch, RPCs) might have occurred,
                // do NOT delete the in-progress reservation! Retain for reconciliation.
                this.cache.set(key, {
                    status: 'RECONCILIATION_REQUIRED',
                    error: err.message,
                    downstreamKey: `ext_prov_${key}`
                });
            } else {
                // Purely local failure before external side-effects: safe to release key
                this.cache.delete(key);
            }
            throw err;
        }
    }
}

(async function testConcurrentIdempotency() {
    console.log('  [TEST 5] Concurrent Idempotency Locking & Durable Reconciliation...');

    const coordinator = new AsyncIdempotencyCoordinator();
    let counter = 0;

    const slowOperation = () => new Promise(resolve => {
        setTimeout(() => resolve({ orderId: 'ord_999', chargeAmount: 1200, seq: ++counter }), 20);
    });

    const key = 'idem_txn_abc_123';

    // 1. Fire 2 concurrent requests with the identical idempotency key
    const p1 = coordinator.process(key, slowOperation);
    const p2 = coordinator.process(key, slowOperation);

    const [res1, res2] = await Promise.all([p1, p2]);

    // One request must win the reservation (status 200), and the second must be rejected with 409 Conflict
    const has200 = res1.status === 200 || res2.status === 200;
    const has409 = res1.status === 409 || res2.status === 409;

    assert(has200, 'One request must succeed');
    assert(has409, 'Concurrent duplicate request must receive 409 Conflict');
    assert.strictEqual(counter, 1, 'Underlying payment operation must execute only once!');

    // 2. Sequential subsequent request: Replays cached result
    const res3 = await coordinator.process(key, slowOperation);
    assert.strictEqual(res3.status, 200);
    assert.strictEqual(res3.replayed, true, 'Subsequent call must return replayed cached response');
    assert.strictEqual(res3.body.orderId, 'ord_999');
    assert.strictEqual(counter, 1, 'Counter remained at 1 across all calls');

    // 3. Ambiguous failure with external side effects: Key must NOT be deleted
    const failKey = 'idem_txn_external_timeout_456';
    const failingExternalOp = () => new Promise((_, reject) => {
        setTimeout(() => reject(new Error('Gateway read timeout after payment dispatch')), 10);
    });

    try {
        await coordinator.process(failKey, failingExternalOp, { hasExternalSideEffects: true });
        assert.fail('Expected operation to throw');
    } catch (e) {
        assert(e.message.includes('Gateway read timeout'));
    }

    // Subsequent client retry must receive RECONCILIATION_REQUIRED, preventing double-charge
    const retryRes = await coordinator.process(failKey, slowOperation);
    assert.strictEqual(retryRes.status, 409);
    assert.strictEqual(retryRes.body.error, 'TRANSACTION_PENDING_RECONCILIATION');
    assert(retryRes.body.downstreamKey.includes(failKey));

    console.log('    -> Passed: Concurrency lock, cached replay, and durable reconciliation verified.');
    console.log('\nAll 5 Node.js API Engineering & Security Test Suites Passed Successfully!');
})();
