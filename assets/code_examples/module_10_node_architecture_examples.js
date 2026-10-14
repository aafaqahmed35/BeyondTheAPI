/**
 * Module 10 — Node.js Production Architecture & System Design Verification Suite.
 *
 * Implements deterministic architectural primitives in pure Node.js (v20+ LTS):
 * 1. Consistent Hashing Router with Virtual Nodes
 * 2. Sliding-Window Counter Rate Limiter
 * 3. Circuit Breaker State Machine with Probing
 * 4. Speculative Request Hedging (Tail Latency Trimming)
 */

import assert from 'node:assert/strict';
import crypto from 'node:crypto';

// =========================================================================
// 1. CONSISTENT HASHING ROUTER WITH VIRTUAL NODES
// =========================================================================

export class ConsistentHashRing {
  constructor(virtualNodesPerPhysical = 100) {
    this.virtualNodesPerPhysical = virtualNodesPerPhysical;
    this.ring = []; // Array of { hash, node }
  }

  _hash(val) {
    return crypto.createHash('md5').update(val).digest().readBigUInt64BE(0);
  }

  addNode(node) {
    for (let i = 0; i < this.virtualNodesPerPhysical; i++) {
      const vNodeKey = `${node}#vn_${i}`;
      const hash = this._hash(vNodeKey);
      this.ring.push({ hash, node });
    }
    this.ring.sort((a, b) => (a.hash < b.hash ? -1 : a.hash > b.hash ? 1 : 0));
  }

  getNode(key) {
    if (this.ring.length === 0) return null;
    const hash = this._hash(key);

    // Binary search for first node with hash >= key's hash
    let low = 0;
    let high = this.ring.length - 1;
    let targetIdx = 0;

    if (hash > this.ring[high].hash) {
      targetIdx = 0; // Wrap around to first node
    } else {
      while (low <= high) {
        const mid = Math.floor((low + high) / 2);
        if (this.ring[mid].hash >= hash) {
          targetIdx = mid;
          high = mid - 1;
        } else {
          low = mid + 1;
        }
      }
    }
    return this.ring[targetIdx].node;
  }
}

// =========================================================================
// 2. SLIDING-WINDOW LOG RATE LIMITER
// =========================================================================

export class SlidingWindowRateLimiter {
  constructor(windowMs, maxRequests) {
    this.windowMs = windowMs;
    this.maxRequests = maxRequests;
    this.logs = new Map(); // clientId -> array of timestamps
  }

  allow(clientId) {
    const now = Date.now();
    const threshold = now - this.windowMs;

    let timestamps = this.logs.get(clientId) || [];
    // Evict expired timestamps
    timestamps = timestamps.filter((t) => t > threshold);

    if (timestamps.length < this.maxRequests) {
      timestamps.push(now);
      this.logs.set(clientId, timestamps);
      return true;
    }

    this.logs.set(clientId, timestamps);
    return false;
  }
}

// =========================================================================
// 3. CIRCUIT BREAKER
// =========================================================================

export class NodeCircuitBreaker {
  constructor({ failureThreshold = 3, resetTimeoutMs = 100 }) {
    this.failureThreshold = failureThreshold;
    this.resetTimeoutMs = resetTimeoutMs;
    this.state = 'CLOSED'; // 'CLOSED', 'OPEN', 'HALF_OPEN'
    this.failureCount = 0;
    this.lastFailureTime = 0;
  }

  async execute(fn, fallbackFn) {
    const now = Date.now();

    if (this.state === 'OPEN') {
      if (now - this.lastFailureTime >= this.resetTimeoutMs) {
        this.state = 'HALF_OPEN';
      } else {
        return fallbackFn ? fallbackFn() : Promise.reject(new Error('CircuitBreaker: OPEN'));
      }
    }

    try {
      const res = await fn();
      if (this.state === 'HALF_OPEN') {
        this.state = 'CLOSED';
        this.failureCount = 0;
      }
      return res;
    } catch (err) {
      this.failureCount++;
      this.lastFailureTime = Date.now();
      if (this.state === 'HALF_OPEN' || this.failureCount >= this.failureThreshold) {
        this.state = 'OPEN';
      }
      if (fallbackFn) {
        return fallbackFn();
      }
      throw err;
    }
  }
}

// =========================================================================
// 4. SPECULATIVE REQUEST HEDGING (TAIL LATENCY TRIMMING)
// =========================================================================

export async function executeWithHedging(primaryCall, backupCall, delayMs = 30) {
  return new Promise((resolve, reject) => {
    let resolved = false;
    let timer = null;

    const onResult = (val) => {
      if (!resolved) {
        resolved = true;
        if (timer) clearTimeout(timer);
        resolve(val);
      }
    };

    // Launch primary call immediately
    primaryCall().then(onResult).catch((err) => {
      // If primary fails early, launch backup immediately
      if (!resolved) {
        if (timer) clearTimeout(timer);
        backupCall().then(onResult).catch(reject);
      }
    });

    // Schedule speculative backup after threshold delay
    timer = setTimeout(() => {
      if (!resolved) {
        backupCall().then(onResult).catch((err) => {
          // If backup fails, wait for primary or fail if both failed
        });
      }
    }, delayMs);
  });
}

// =========================================================================
// TEST SUITES
// =========================================================================

async function testConsistentHashing() {
  process.stdout.write('  [TEST 1] Consistent Hashing Node Distribution & Minimal Churn... ');
  const ring = new ConsistentHashRing(100);
  ring.addNode('shard-1');
  ring.addNode('shard-2');
  ring.addNode('shard-3');

  const before = new Map();
  const counts = new Map();

  for (let i = 0; i < 3000; i++) {
    const key = `user_acc_${i}`;
    const node = ring.getNode(key);
    before.set(key, node);
    counts.set(node, (counts.get(node) || 0) + 1);
  }

  // All 3 shards receive traffic
  assert.equal(counts.size, 3);
  for (const [node, c] of counts.entries()) {
    assert.ok(c > 600 && c < 1400, `Shard ${node} count ${c} within acceptable spread`);
  }

  // Add 4th shard
  ring.addNode('shard-4');
  let remapped = 0;
  for (let i = 0; i < 3000; i++) {
    const key = `user_acc_${i}`;
    const newNode = ring.getNode(key);
    if (newNode !== before.get(key)) {
      remapped++;
      assert.equal(newNode, 'shard-4', 'Remapped keys must only move to new shard');
    }
  }

  const pct = (remapped / 3000) * 100;
  assert.ok(pct > 15 && pct < 35, `Remapped percent ~25% (got ${pct.toFixed(1)}%)`);
  console.log(`Passed: 4th shard addition remapped ${pct.toFixed(1)}% of keys.`);
}

async function testSlidingWindowRateLimiter() {
  process.stdout.write('  [TEST 2] Sliding Window Rate Limiter Invariants... ');
  // 3 requests allowed per 50ms window
  const limiter = new SlidingWindowRateLimiter(50, 3);
  const client = 'client-101';

  assert.equal(limiter.allow(client), true);
  assert.equal(limiter.allow(client), true);
  assert.equal(limiter.allow(client), true);
  // 4th request in same window must fail
  assert.equal(limiter.allow(client), false);

  // Wait 60ms for window to slide
  await new Promise((r) => setTimeout(r, 60));

  assert.equal(limiter.allow(client), true, 'Request must succeed after window slides');
  console.log('Passed: Rate limiter capped burst to 3 and recovered after window expired.');
}

async function testCircuitBreaker() {
  process.stdout.write('  [TEST 3] Circuit Breaker Fast-Failure & Probing... ');
  const cb = new NodeCircuitBreaker({ failureThreshold: 2, resetTimeoutMs: 50 });

  let callCount = 0;
  const failingService = async () => {
    callCount++;
    throw new Error('Service Unavailable');
  };

  // Fail 1
  await assert.rejects(() => cb.execute(failingService));
  assert.equal(cb.state, 'CLOSED');

  // Fail 2 trips breaker to OPEN
  await assert.rejects(() => cb.execute(failingService));
  assert.equal(cb.state, 'OPEN');

  // 3rd call fails fast without invoking service
  const callsBeforeFastFail = callCount;
  const fallbackVal = await cb.execute(failingService, () => 'fallback_response');
  assert.equal(fallbackVal, 'fallback_response');
  assert.equal(callCount, callsBeforeFastFail, 'Underlying failing service must NOT be called while OPEN');

  // Wait for reset timeout
  await new Promise((r) => setTimeout(r, 60));

  // Probe succeeds and heals breaker
  const healthyService = async () => 'healthy_data';
  const healthyVal = await cb.execute(healthyService);
  assert.equal(healthyVal, 'healthy_data');
  assert.equal(cb.state, 'CLOSED');

  console.log('Passed: Circuit breaker fast-failed and healed to CLOSED after trial success.');
}

async function testRequestHedging() {
  process.stdout.write('  [TEST 4] Speculative Request Hedging (Tail Latency Trimming)... ');

  // Primary replica takes 80ms (slow tail latency)
  const primarySlow = () => new Promise((resolve) => setTimeout(() => resolve('primary_80ms'), 80));

  // Backup replica takes 10ms
  const backupFast = () => new Promise((resolve) => setTimeout(() => resolve('backup_10ms'), 10));

  const start = Date.now();
  // Hedging threshold = 20ms: after 20ms of primary waiting, backup is dispatched
  // Total expected elapsed time ≈ 20ms (wait) + 10ms (backup) ≈ 30-40ms (well under 80ms)
  const res = await executeWithHedging(primarySlow, backupFast, 20);
  const elapsed = Date.now() - start;

  assert.equal(res, 'backup_10ms');
  assert.ok(elapsed < 65, `Hedging must trim tail latency: elapsed was ${elapsed}ms (< 65ms)`);
  console.log(`Passed: Hedged backup resolved slow tail in ${elapsed}ms instead of 80ms.`);
}

async function main() {
  console.log('=================================================================');
  console.log(' MODULE 10: NODE.JS SYSTEM ARCHITECTURE VERIFICATION             ');
  console.log('=================================================================\n');

  await testConsistentHashing();
  await testSlidingWindowRateLimiter();
  await testCircuitBreaker();
  await testRequestHedging();

  console.log('\n=================================================================');
  console.log(' ALL MODULE 10 NODE.JS ARCHITECTURE TESTS PASSED PERFECTLY (4/4) ');
  console.log('=================================================================');
}

main().catch((err) => {
  console.error('Fatal test failure:', err);
  process.exit(1);
});
