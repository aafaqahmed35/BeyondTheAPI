/**
 * Module 07: Caching & Performance Verification Test Suite (Node.js).
 *
 * Demonstrates deterministic asynchronous caching patterns in Node.js:
 *  1. In-process bounded TTL cache with injected clock.
 *  2. Async Cache-Aside pipeline with simulated database & write-invalidation.
 *  3. Single-Flight request coalescing under concurrent async misses.
 *  4. Negative caching for nonexistent keys (penetration defense).
 *  5. Timeout resilience & graceful fallback on cache failure.
 */

import assert from 'node:assert/strict';

// =========================================================================
// 1. In-Process Bounded TTL Cache with Deterministic Clock Injection
// =========================================================================

class BoundedTtlCache {
  constructor({ maxCapacity = 100, clock = () => Date.now() } = {}) {
    this.maxCapacity = maxCapacity;
    this.clock = clock;
    this.store = new Map(); // Preserves insertion order in JS Map
  }

  get(key) {
    const entry = this.store.get(key);
    if (!entry) return null;

    if (this.clock() >= entry.expiresAt) {
      // Lazy eviction
      this.store.delete(key);
      return null;
    }

    // Refresh LRU order on access by re-inserting
    this.store.delete(key);
    this.store.set(key, entry);
    return entry.value;
  }

  set(key, value, ttlMillis) {
    const expiresAt = this.clock() + ttlMillis;

    // If key already exists, delete first to refresh position
    if (this.store.has(key)) {
      this.store.delete(key);
    } else if (this.store.size >= this.maxCapacity) {
      // Evict eldest entry (first key in Map iterator)
      const eldestKey = this.store.keys().next().value;
      this.store.delete(eldestKey);
    }

    this.store.set(key, { value, expiresAt });
  }

  delete(key) {
    return this.store.delete(key);
  }

  size() {
    return this.store.size;
  }
}

function testBoundedTtlCache() {
  console.log('  [TEST 1] In-Process Bounded TTL Cache & Access-Order Eviction...');
  let currentTime = 1000;
  const cache = new BoundedTtlCache({ maxCapacity: 2, clock: () => currentTime });

  cache.set('a', 'alpha', 500);
  cache.set('b', 'beta', 500);
  assert.equal(cache.size(), 2);

  // Access 'a', making 'b' the eldest
  assert.equal(cache.get('a'), 'alpha');

  // Insert 'c' -> should evict 'b'
  cache.set('c', 'gamma', 500);
  assert.equal(cache.size(), 2);
  assert.equal(cache.get('b'), null, 'Key b should be evicted as LRU');
  assert.equal(cache.get('a'), 'alpha', 'Key a should remain valid');
  assert.equal(cache.get('c'), 'gamma', 'Key c should be present');

  // Advance clock past TTL (500ms)
  currentTime += 600;
  assert.equal(cache.get('a'), null, 'Key a should be expired after TTL');
  assert.equal(cache.size(), 1, 'Key a was lazily evicted on get()');

  console.log('    -> Passed: Bounded capacity and deterministic TTL expiration verified.');
}

// =========================================================================
// 2. Async Cache-Aside Pattern with Database Simulation
// =========================================================================

class MockDatabase {
  constructor() {
    this.data = new Map();
    this.reads = 0;
    this.writes = 0;
  }

  async find(id) {
    this.reads++;
    return this.data.get(id) || null;
  }

  async save(id, value) {
    this.writes++;
    this.data.set(id, value);
  }
}

class CacheAsideService {
  constructor(db, cache) {
    this.db = db;
    this.cache = cache;
  }

  async getEntity(id, ttlMillis = 60000) {
    // Step 1: Check cache
    const cached = this.cache.get(id);
    if (cached !== null) {
      return { data: cached, source: 'CACHE' };
    }

    // Step 2: Cache miss -> query DB
    const record = await this.db.find(id);
    if (record !== null) {
      this.cache.set(id, record, ttlMillis);
    }
    return { data: record, source: 'DB' };
  }

  async updateEntity(id, newValue) {
    // Invalidate pattern: write to DB first, then delete cache
    await this.db.save(id, newValue);
    this.cache.delete(id);
  }
}

async function testCacheAsidePattern() {
  console.log('  [TEST 2] Async Cache-Aside Pipeline & Write-Invalidation...');
  const db = new MockDatabase();
  await db.save('user:101', { name: 'Alice', tier: 'PRO' });

  const cache = new BoundedTtlCache({ maxCapacity: 100 });
  const service = new CacheAsideService(db, cache);

  // First call: Miss -> DB
  const r1 = await service.getEntity('user:101');
  assert.equal(r1.source, 'DB');
  assert.equal(r1.data.name, 'Alice');
  assert.equal(db.reads, 1);

  // Second call: Hit -> Cache
  const r2 = await service.getEntity('user:101');
  assert.equal(r2.source, 'CACHE');
  assert.equal(r2.data.name, 'Alice');
  assert.equal(db.reads, 1, 'Second lookup must not query DB');

  // Mutation: updates DB, evicts cache
  await service.updateEntity('user:101', { name: 'Alice Smith', tier: 'ENTERPRISE' });
  assert.equal(db.writes, 2);

  // Third call: Miss -> re-reads fresh data from DB
  const r3 = await service.getEntity('user:101');
  assert.equal(r3.source, 'DB');
  assert.equal(r3.data.tier, 'ENTERPRISE');
  assert.equal(db.reads, 2, 'Must query DB after invalidation');

  console.log('    -> Passed: Cache-aside read miss, cache hit, and write invalidation verified.');
}

// =========================================================================
// 3. Single-Flight Request Coalescing (Stampede Defense)
// =========================================================================

class SingleFlightCoalescer {
  constructor() {
    this.inFlight = new Map();
  }

  async execute(key, asyncLoader) {
    const existing = this.inFlight.get(key);
    if (existing) {
      return existing; // Return shared in-flight Promise!
    }

    const promise = (async () => {
      try {
        return await asyncLoader(key);
      } finally {
        this.inFlight.delete(key);
      }
    })();

    this.inFlight.set(key, promise);
    return promise;
  }
}

async function testSingleFlightCoalescing() {
  console.log('  [TEST 3] Single-Flight Request Coalescing under Concurrent Misses...');
  const coalescer = new SingleFlightCoalescer();
  let dbExecutions = 0;

  const expensiveDbLoader = async (key) => {
    dbExecutions++;
    await new Promise((resolve) => setTimeout(resolve, 30));
    return `AGGREGATED_METRICS_FOR_${key}`;
  };

  // Launch 15 simultaneous concurrent requests for the SAME missing key
  const concurrency = 15;
  const tasks = Array.from({ length: concurrency }, () =>
    coalescer.execute('dashboard:global_kpis', expensiveDbLoader)
  );

  const results = await Promise.all(tasks);

  assert.equal(results.length, concurrency);
  for (const res of results) {
    assert.equal(res, 'AGGREGATED_METRICS_FOR_dashboard:global_kpis');
  }

  // Verification: Database was invoked only ONCE for all 15 requests
  assert.equal(dbExecutions, 1, '15 concurrent requests must coalesce into exactly 1 DB execution!');

  console.log('    -> Passed: 15 concurrent misses coalesced into exactly 1 database query.');
}

// =========================================================================
// 4. Negative Caching for Nonexistent Keys (Penetration Defense)
// =========================================================================

async function testNegativeCaching() {
  console.log('  [TEST 4] Negative Caching for Nonexistent Entities...');
  const db = new MockDatabase();
  let time = 1000;
  const cache = new BoundedTtlCache({ maxCapacity: 100, clock: () => time });

  const NULL_SENTINEL = Symbol('__NULL_SENTINEL__');

  async function getWithNegativeCache(id) {
    const cached = cache.get(id);
    if (cached === NULL_SENTINEL) return null;
    if (cached !== null) return cached;

    const fromDb = await db.find(id);
    if (fromDb !== null) {
      cache.set(id, fromDb, 60000); // 60s for real data
      return fromDb;
    } else {
      cache.set(id, NULL_SENTINEL, 5000); // 5s short TTL for negative hit
      return null;
    }
  }

  // First call for nonexistent key: hits DB
  const r1 = await getWithNegativeCache('nonexistent_id_999');
  assert.equal(r1, null);
  assert.equal(db.reads, 1);

  // Subsequent 5 calls within negative TTL: hit cache sentinel, DB reads remain 1!
  for (let i = 0; i < 5; i++) {
    const r = await getWithNegativeCache('nonexistent_id_999');
    assert.equal(r, null);
  }
  assert.equal(db.reads, 1, 'Negative cache must protect DB from repeated nonexistent lookups');

  // Advance time past negative TTL
  time += 6000;
  await getWithNegativeCache('nonexistent_id_999');
  assert.equal(db.reads, 2, 'Negative entry must expire and allow fresh check');

  console.log('    -> Passed: Negative caching successfully absorbed repeated penetration queries.');
}

// =========================================================================
// 5. Timeout & Graceful Degradation on Cache Outage
// =========================================================================

class FaultyCacheClient {
  constructor(shouldTimeout = false) {
    this.shouldTimeout = shouldTimeout;
  }

  async get(key, timeoutMs = 50) {
    if (this.shouldTimeout) {
      return new Promise((_, reject) =>
        setTimeout(() => reject(new Error('ETIMEDOUT: Redis connection hung')), timeoutMs + 10)
      );
    }
    return null;
  }
}

async function testGracefulCacheDegradation() {
  console.log('  [TEST 5] Graceful Degradation & Timeout Defense on Cache Outage...');
  const db = new MockDatabase();
  await db.save('item_secure', { secret: 'alpha_omega' });

  const faultyCache = new FaultyCacheClient(true); // Always times out
  let cacheErrorsLogged = 0;

  async function resilientFetch(id) {
    // Defensive wrapper: Cache failure must NEVER crash or block the request indefinitely!
    let cached = null;
    try {
      // Race cache against a strict 20ms timeout
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(() => reject(new Error('Cache read deadline exceeded')), 20)
      );
      cached = await Promise.race([faultyCache.get(id), timeoutPromise]);
    } catch (err) {
      cacheErrorsLogged++;
      // Fallback seamlessly to database!
    }

    if (cached !== null) return cached;
    return await db.find(id);
  }

  const result = await resilientFetch('item_secure');
  assert.equal(result.secret, 'alpha_omega', 'Must return valid DB data despite cache crash');
  assert.equal(cacheErrorsLogged, 1, 'Cache timeout was trapped and logged');
  assert.equal(db.reads, 1, 'Database successfully served traffic when cache failed');

  console.log('    -> Passed: Cache timeout safely handled with zero user-facing errors.');
}

// =========================================================================
// Master Runner
// =========================================================================

async function main() {
  console.log('=================================================================');
  console.log('  MODULE 07 NODE.JS CACHING & PERFORMANCE TEST SUITE            ');
  console.log('=================================================================');

  testBoundedTtlCache();
  await testCacheAsidePattern();
  await testSingleFlightCoalescing();
  await testNegativeCaching();
  await testGracefulCacheDegradation();

  console.log('\nAll 5 Node.js Caching & Performance Test Suites Passed Successfully!');
}

main().catch((err) => {
  console.error('Test execution failed:', err);
  process.exit(1);
});
