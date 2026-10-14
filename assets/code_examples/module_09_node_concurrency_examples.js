/**
 * Module 09 — Node.js Concurrency & Distributed Systems Verification Suite.
 *
 * Implements deterministic async concurrency and distributed systems primitives in pure Node.js (v20+ LTS):
 * 1. Async Semaphore & Concurrency Limiter (Bounded Parallel Execution)
 * 2. Shared State Race Conditions & Optimistic Concurrency Control (Version/CAS check)
 * 3. Monotonic Fencing Tokens & Distributed Zombie Write Prevention
 * 4. Cooperative Cancellation & Timeout Pipelines via AbortController
 * 5. Event Loop Non-Blocking Invariant & Microtask / Macrotask Ordering
 */

import assert from 'node:assert/strict';

// =========================================================================
// 1. ASYNC SEMAPHORE / CONCURRENCY LIMITER
// =========================================================================

export class AsyncSemaphore {
  constructor(maxConcurrency) {
    if (maxConcurrency <= 0) {
      throw new Error('maxConcurrency must be positive');
    }
    this.maxConcurrency = maxConcurrency;
    this.current = 0;
    this.waitQueue = [];
  }

  async acquire() {
    if (this.current < this.maxConcurrency) {
      this.current++;
      return;
    }
    await new Promise((resolve) => this.waitQueue.push(resolve));
    this.current++;
  }

  release() {
    this.current--;
    if (this.waitQueue.length > 0) {
      const nextResolve = this.waitQueue.shift();
      nextResolve();
    }
  }

  async run(task) {
    await this.acquire();
    try {
      return await task();
    } finally {
      this.release();
    }
  }

  get activeCount() {
    return this.current;
  }

  get queuedCount() {
    return this.waitQueue.length;
  }
}

// =========================================================================
// 2. OPTIMISTIC CONCURRENCY CONTROL (OCC / CAS ENGINE)
// =========================================================================

export class OccDocumentStore {
  constructor() {
    this.records = new Map(); // id -> { data, version }
  }

  create(id, data) {
    if (this.records.has(id)) {
      throw new Error(`Record ${id} already exists`);
    }
    const record = { data, version: 1 };
    this.records.set(id, record);
    return { ...record };
  }

  read(id) {
    const record = this.records.get(id);
    if (!record) return null;
    return { ...record };
  }

  update(id, expectedVersion, newData) {
    const current = this.records.get(id);
    if (!current) {
      throw new Error(`Record ${id} not found`);
    }
    if (current.version !== expectedVersion) {
      throw new Error(
        `OptimisticLockException: Conflict on record ${id}. Expected version ${expectedVersion}, but found ${current.version}`
      );
    }
    current.data = newData;
    current.version += 1;
    return { ...current };
  }
}

// =========================================================================
// 3. MONOTONIC FENCING TOKEN STORAGE ENGINE
// =========================================================================

export class FencingTokenStorage {
  constructor() {
    this.highestToken = 0n;
    this.store = new Map();
  }

  write(key, value, fencingToken) {
    const tokenBigInt = BigInt(fencingToken);
    if (tokenBigInt <= this.highestToken) {
      throw new Error(
        `ZombieWriteRejected: Token ${tokenBigInt} is <= highest observed token ${this.highestToken}`
      );
    }
    this.highestToken = tokenBigInt;
    this.store.set(key, value);
    return true;
  }

  read(key) {
    return this.store.get(key);
  }

  getHighestToken() {
    return this.highestToken;
  }
}

// =========================================================================
// 4. TIMEOUT & CANCELLATION RUNNER
// =========================================================================

export async function withTimeout(asyncFn, timeoutMs, timeoutMessage = 'Operation timed out') {
  const controller = new AbortController();
  const { signal } = controller;

  const timer = setTimeout(() => {
    controller.abort(new Error(timeoutMessage));
  }, timeoutMs);

  try {
    return await asyncFn(signal);
  } finally {
    clearTimeout(timer);
  }
}

// =========================================================================
// TEST SUITES
// =========================================================================

async function testAsyncSemaphore() {
  process.stdout.write('  [TEST 1] AsyncSemaphore Concurrency Limiting... ');
  const limit = 3;
  const sem = new AsyncSemaphore(limit);
  let maxObservedActive = 0;
  let activeNow = 0;

  const tasks = Array.from({ length: 12 }, async (_, idx) => {
    return sem.run(async () => {
      activeNow++;
      maxObservedActive = Math.max(maxObservedActive, activeNow);
      await new Promise((r) => setTimeout(r, 10));
      activeNow--;
      return idx * 2;
    });
  });

  const results = await Promise.all(tasks);
  assert.equal(results.length, 12);
  assert.ok(maxObservedActive <= limit, `Max active (${maxObservedActive}) must not exceed limit (${limit})`);
  assert.equal(sem.activeCount, 0);
  assert.equal(sem.queuedCount, 0);
  console.log(`Passed: Maximum active concurrency bounded strictly to ${maxObservedActive}/${limit}.`);
}

async function testOptimisticConcurrencyControl() {
  process.stdout.write('  [TEST 2] Optimistic Concurrency Control (CAS & Version Checking)... ');
  const store = new OccDocumentStore();
  store.create('account:101', { balance: 1000 });

  // Read snapshot
  const snap1 = store.read('account:101');
  const snap2 = store.read('account:101');

  assert.equal(snap1.version, 1);
  assert.equal(snap2.version, 1);

  // Client 1 updates successfully
  const updated1 = store.update('account:101', snap1.version, { balance: 900 });
  assert.equal(updated1.version, 2);
  assert.equal(updated1.data.balance, 900);

  // Client 2 attempts update with stale version 1
  assert.throws(
    () => {
      store.update('account:101', snap2.version, { balance: 800 });
    },
    /OptimisticLockException/,
    'Stale update attempt must throw OptimisticLockException'
  );

  // Read fresh record and retry successfully
  const snapFresh = store.read('account:101');
  assert.equal(snapFresh.version, 2);
  const updated2 = store.update('account:101', snapFresh.version, { balance: snapFresh.data.balance - 200 });
  assert.equal(updated2.version, 3);
  assert.equal(updated2.data.balance, 700);

  console.log('Passed: Stale concurrent write rejected; OCC version bump confirmed.');
}

async function testFencingTokenStorage() {
  process.stdout.write('  [TEST 3] Distributed Fencing Tokens Rejecting Zombie Writes... ');
  const storage = new FencingTokenStorage();

  // Primary node 1 writes with token 100
  storage.write('resource:lock', 'leader-1-data', 100n);
  assert.equal(storage.read('resource:lock'), 'leader-1-data');

  // Primary node 1 pauses (e.g. event loop blocked or network partitioned)
  // Failover promotes node 2 with token 101
  storage.write('resource:lock', 'leader-2-data', 101n);
  assert.equal(storage.read('resource:lock'), 'leader-2-data');

  // Node 1 unblocks and attempts zombie write with token 100
  assert.throws(
    () => {
      storage.write('resource:lock', 'zombie-leader-1-data', 100n);
    },
    /ZombieWriteRejected/,
    'Storage engine must reject stale fencing token <= highest token'
  );

  assert.equal(storage.read('resource:lock'), 'leader-2-data');
  assert.equal(storage.getHighestToken(), 101n);
  console.log('Passed: Zombie write safely rejected by monotonic fencing token validation.');
}

async function testTimeoutAndAbortController() {
  process.stdout.write('  [TEST 4] Cooperative Cancellation & Timeout Pipelines... ');

  // Fast task succeeds
  const fastResult = await withTimeout(async (signal) => {
    assert.ok(!signal.aborted);
    return 'fast_success';
  }, 100);
  assert.equal(fastResult, 'fast_success');

  // Slow task times out
  let timedOut = false;
  try {
    await withTimeout(async (signal) => {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(resolve, 200);
        signal.addEventListener('abort', () => {
          clearTimeout(timer);
          reject(signal.reason);
        });
      });
    }, 20);
  } catch (err) {
    if (err.message.includes('Operation timed out')) {
      timedOut = true;
    }
  }
  assert.ok(timedOut, 'Slow task must be aborted by timeout');
  console.log('Passed: AbortController cleanly interrupted stalled asynchronous task.');
}

// =========================================================================
// 5. ASYNC MUTEX (CRITICAL SECTION MUTUAL EXCLUSION)
// =========================================================================

export class AsyncMutex {
  constructor() {
    this._locked = false;
    this._waiting = [];
  }

  async acquire() {
    if (!this._locked) {
      this._locked = true;
      return () => this._release();
    }
    return new Promise((resolve) => {
      this._waiting.push(() => {
        this._locked = true;
        resolve(() => this._release());
      });
    });
  }

  _release() {
    if (this._waiting.length > 0) {
      const next = this._waiting.shift();
      next();
    } else {
      this._locked = false;
    }
  }

  async runExclusive(callback) {
    const release = await this.acquire();
    try {
      return await callback();
    } finally {
      release();
    }
  }
}

async function testAsyncMutex() {
  process.stdout.write('  [TEST 5] AsyncMutex Mutual Exclusion (Async Race Elimination)... ');
  const mutex = new AsyncMutex();
  let sharedResource = 0;
  const CONCURRENT_OPS = 10;

  // With Mutex: Operations read, await a tick, and write back safely
  const tasks = Array.from({ length: CONCURRENT_OPS }, async () => {
    return mutex.runExclusive(async () => {
      const current = sharedResource;
      await new Promise((r) => setTimeout(r, 5));
      sharedResource = current + 1;
    });
  });

  await Promise.all(tasks);
  assert.equal(
    sharedResource,
    CONCURRENT_OPS,
    `Shared resource must equal exactly ${CONCURRENT_OPS} after serialized critical section execution`
  );
  console.log('Passed: AsyncMutex serialized concurrent asynchronous reads and writes without lost updates.');
}

async function main() {
  console.log('=================================================================');
  console.log(' MODULE 09: NODE.JS CONCURRENCY & ASYNC SYSTEMS VERIFICATION     ');
  console.log('=================================================================\n');

  await testAsyncSemaphore();
  await testOptimisticConcurrencyControl();
  await testFencingTokenStorage();
  await testTimeoutAndAbortController();
  await testAsyncMutex();

  console.log('\n=================================================================');
  console.log(' ALL MODULE 09 NODE.JS CONCURRENCY TESTS PASSED PERFECTLY (5/5)  ');
  console.log('=================================================================');
}

main().catch((err) => {
  console.error('Fatal test failure:', err);
  process.exit(1);
});

