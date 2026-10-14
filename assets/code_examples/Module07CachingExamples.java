package assets.code_examples;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Module 07: Caching & Performance Verification Test Suite (Java 21 LTS).
 *
 * Demonstrates deterministic, production-grade caching mechanics using pure
 * Java standard library primitives:
 *  1. Bounded LRU eviction invariants.
 *  2. Injected-clock TTL expiration and lazy eviction.
 *  3. Cache-aside lifecycle with persistent database simulation.
 *  4. Negative caching for nonexistent keys (penetration defense).
 *  5. Single-flight request coalescing (stampede / thundering herd defense).
 *  6. Dual-write invalidation race condition and versioned mitigation.
 *  7. Graceful degradation and fallback resilience on cache failure.
 */
public class Module07CachingExamples {

    // =========================================================================
    // 1. Bounded LRU Cache (Access-Order Invariants)
    // =========================================================================

    public static class BoundedLruCache<K, V> {
        private final int capacity;
        private final LinkedHashMap<K, V> map;

        public BoundedLruCache(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
            this.capacity = capacity;
            // accessOrder = true enforces LRU order upon get/put
            this.map = new LinkedHashMap<>(capacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                    return size() > BoundedLruCache.this.capacity;
                }
            };
        }

        public synchronized V get(K key) {
            return map.get(key);
        }

        public synchronized void put(K key, V value) {
            map.put(key, value);
        }

        public synchronized boolean containsKey(K key) {
            return map.containsKey(key);
        }

        public synchronized int size() {
            return map.size();
        }

        public synchronized List<K> getKeysInAccessOrder() {
            return new ArrayList<>(map.keySet());
        }
    }

    public static void testBoundedLruEviction() {
        System.out.println("  [TEST 1] Bounded LRU Cache Eviction & Access-Order Invariants...");
        BoundedLruCache<String, String> cache = new BoundedLruCache<>(3);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");
        assert cache.size() == 3 : "Cache should be full at capacity 3";

        // Access k1, making it most-recently used; k2 is now eldest
        cache.get("k1");
        List<KEntry> order = cache.getKeysInAccessOrder().stream().map(KEntry::new).toList();
        assert order.get(2).key.equals("k1") : "k1 should be MRU";
        assert order.get(0).key.equals("k2") : "k2 should be LRU";

        // Insert k4 -> k2 should be evicted
        cache.put("k4", "v4");
        assert cache.size() == 3 : "Capacity must remain bounded at 3";
        assert !cache.containsKey("k2") : "k2 must be evicted as LRU";
        assert cache.containsKey("k1") : "k1 must be preserved because it was recently accessed";
        assert cache.containsKey("k3") : "k3 must be preserved";
        assert cache.containsKey("k4") : "k4 must be present";

        System.out.println("    -> Passed: Bounded capacity preserved and eldest entry evicted on access order.");
    }

    private record KEntry(String key) {}

    // =========================================================================
    // 2. Injected-Clock TTL Expiration & Lazy Eviction
    // =========================================================================

    public static class TtlCache<K, V> {
        private record CacheEntry<V>(V value, long expiresAtMillis) {}

        private final Map<K, CacheEntry<V>> store = new ConcurrentHashMap<>();
        private final Supplier<Long> clockMillis;

        public TtlCache(Supplier<Long> clockMillis) {
            this.clockMillis = clockMillis;
        }

        public void put(K key, V value, long ttlMillis) {
            long expiresAt = clockMillis.get() + ttlMillis;
            store.put(key, new CacheEntry<>(value, expiresAt));
        }

        public V get(K key) {
            CacheEntry<V> entry = store.get(key);
            if (entry == null) return null;

            if (clockMillis.get() >= entry.expiresAtMillis) {
                // Lazy eviction: clean up expired entry on access
                store.remove(key, entry);
                return null;
            }
            return entry.value;
        }

        public boolean containsKey(K key) {
            return get(key) != null;
        }

        public int rawStoreSize() {
            return store.size();
        }
    }

    public static void testTtlExpirationWithInjectedClock() {
        System.out.println("  [TEST 2] Deterministic TTL Expiration & Lazy Eviction...");
        AtomicLong simulatedClock = new AtomicLong(1_000_000L);
        TtlCache<String, String> cache = new TtlCache<>(simulatedClock::get);

        // Put with 5000ms TTL (expires at 1_005_000)
        cache.put("session_101", "user_alice", 5000);

        // Access at 1_002_000 (valid)
        simulatedClock.set(1_002_000L);
        assert "user_alice".equals(cache.get("session_101")) : "Session should be valid before TTL";

        // Advance clock past expiration (1_005_001)
        simulatedClock.set(1_005_001L);
        assert cache.get("session_101") == null : "Session must expire past TTL";
        assert cache.rawStoreSize() == 0 : "Lazy eviction must clean up store entry upon access";

        System.out.println("    -> Passed: Injected-time TTL expiration and lazy memory cleanup verified.");
    }

    // =========================================================================
    // 3. Cache-Aside Lifecycle with Database Simulation
    // =========================================================================

    public static class MockDatabase {
        private final Map<String, String> records = new ConcurrentHashMap<>();
        private final AtomicInteger readCount = new AtomicInteger(0);
        private final AtomicInteger writeCount = new AtomicInteger(0);

        public void save(String id, String payload) {
            writeCount.incrementAndGet();
            records.put(id, payload);
        }

        public String findById(String id) {
            readCount.incrementAndGet();
            return records.get(id);
        }

        public int getReadCount() { return readCount.get(); }
        public int getWriteCount() { return writeCount.get(); }
    }

    public static class CacheAsideService {
        private final MockDatabase db;
        private final Map<String, String> cache = new ConcurrentHashMap<>();

        public CacheAsideService(MockDatabase db) {
            this.db = db;
        }

        public String getEntity(String id) {
            // 1. Check cache first
            String cached = cache.get(id);
            if (cached != null) {
                return cached; // Cache Hit
            }

            // 2. Cache Miss: load from persistent database
            String fromDb = db.findById(id);
            if (fromDb != null) {
                cache.put(id, fromDb);
            }
            return fromDb;
        }

        public void updateEntity(String id, String newPayload) {
            // Write-flow in cache-aside:
            // Step 1: Update persistent database
            db.save(id, newPayload);
            // Step 2: Invalidate (evict) cache entry to eliminate stale reads
            cache.remove(id);
        }

        public boolean isCached(String id) {
            return cache.containsKey(id);
        }
    }

    public static void testCacheAsidePattern() {
        System.out.println("  [TEST 3] Cache-Aside Pattern Lifecycle (Hit, Miss, Invalidation)...");
        MockDatabase db = new MockDatabase();
        db.save("prod_42", "{\"name\":\"Mechanical Keyboard\",\"price\":120}");

        CacheAsideService service = new CacheAsideService(db);

        // First call: Cache Miss -> reads from DB
        String res1 = service.getEntity("prod_42");
        assert res1 != null && res1.contains("Mechanical Keyboard");
        assert db.getReadCount() == 1 : "First access must hit DB";
        assert service.isCached("prod_42") : "Item must now be cached";

        // Second call: Cache Hit -> served from memory without touching DB
        String res2 = service.getEntity("prod_42");
        assert res2.equals(res1);
        assert db.getReadCount() == 1 : "Second access must NOT touch DB (Cache Hit)";

        // Update Entity: writes to DB, evicts cache
        service.updateEntity("prod_42", "{\"name\":\"Mechanical Keyboard\",\"price\":99}");
        assert db.getWriteCount() == 2;
        assert !service.isCached("prod_42") : "Cache entry must be invalidated after DB write";

        // Third call: Cache Miss after invalidation -> reloads updated entity from DB
        String res3 = service.getEntity("prod_42");
        assert res3.contains("\"price\":99");
        assert db.getReadCount() == 2 : "Access after invalidation must read fresh data from DB";

        System.out.println("    -> Passed: Cache-aside read miss, memory hit, and write invalidation verified.");
    }

    // =========================================================================
    // 4. Cache Penetration Defense via Negative Caching
    // =========================================================================

    public static class PenetrationProtectedCache {
        private static final String NEGATIVE_SENTINEL = "__NULL_SENTINEL__";
        private final MockDatabase db;
        private final TtlCache<String, String> cache;
        private final long normalTtlMillis;
        private final long negativeTtlMillis;

        public PenetrationProtectedCache(MockDatabase db, Supplier<Long> clock, long normalTtlMillis, long negativeTtlMillis) {
            this.db = db;
            this.cache = new TtlCache<>(clock);
            this.normalTtlMillis = normalTtlMillis;
            this.negativeTtlMillis = negativeTtlMillis;
        }

        public String get(String id) {
            String cached = cache.get(id);
            if (cached != null) {
                if (NEGATIVE_SENTINEL.equals(cached)) {
                    return null; // Negative Cache Hit (prevents DB query)
                }
                return cached;
            }

            // Miss: query DB
            String fromDb = db.findById(id);
            if (fromDb != null) {
                cache.put(id, fromDb, normalTtlMillis);
                return fromDb;
            } else {
                // Nonexistent entity: Cache negative sentinel with short TTL
                cache.put(id, NEGATIVE_SENTINEL, negativeTtlMillis);
                return null;
            }
        }
    }

    public static void testNegativeCachingPenetrationDefense() {
        System.out.println("  [TEST 4] Cache Penetration Defense via Negative Caching...");
        AtomicLong clock = new AtomicLong(100_000L);
        MockDatabase db = new MockDatabase(); // Empty database
        PenetrationProtectedCache cache = new PenetrationProtectedCache(db, clock::get, 60_000, 5_000);

        String nonExistentKey = "user_nonexistent_999";

        // First attempt: Misses cache, queries DB (returns null), writes negative sentinel
        String r1 = cache.get(nonExistentKey);
        assert r1 == null;
        assert db.getReadCount() == 1 : "First lookup must query DB";

        // Second, third, fourth attempts: Hit negative sentinel in cache, DB query count remains 1!
        for (int i = 0; i < 5; i++) {
            assert cache.get(nonExistentKey) == null;
        }
        assert db.getReadCount() == 1 : "Repeated misses for nonexistent key must NOT reach the DB!";

        // Fast-forward past negative TTL (5000ms)
        clock.addAndGet(6000);
        // Next lookup re-queries DB after negative TTL expiry
        assert cache.get(nonExistentKey) == null;
        assert db.getReadCount() == 2 : "Negative cache entry should expire after short TTL";

        System.out.println("    -> Passed: Negative caching blocked repeated nonexistent key queries from hitting DB.");
    }

    // =========================================================================
    // 5. Single-Flight Request Coalescing (Stampede Defense)
    // =========================================================================

    public static class SingleFlightCoordinator<K, V> {
        private final ConcurrentMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

        public V execute(K key, Function<K, V> loader) throws ExecutionException, InterruptedException {
            while (true) {
                CompletableFuture<V> existing = inFlight.get(key);
                if (existing != null) {
                    try {
                        return existing.get();
                    } catch (CancellationException | ExecutionException e) {
                        // Stale failure: remove only this exact failed future instance and loop to retry
                        inFlight.remove(key, existing);
                        continue;
                    }
                }

                CompletableFuture<V> newFuture = new CompletableFuture<>();
                CompletableFuture<V> previous = inFlight.putIfAbsent(key, newFuture);
                if (previous != null) {
                    // Lost registration race; join the winner's in-flight execution
                    try {
                        return previous.get();
                    } catch (CancellationException | ExecutionException e) {
                        inFlight.remove(key, previous);
                        continue;
                    }
                }

                // Won registration: execute loader synchronously on this thread
                try {
                    V result = loader.apply(key);
                    newFuture.complete(result);
                    return result;
                } catch (Throwable t) {
                    newFuture.completeExceptionally(t);
                    throw new ExecutionException("Loader failed in single-flight execution", t);
                } finally {
                    // Crucial: 2-argument CAS remove ensures we only remove THIS exact future
                    inFlight.remove(key, newFuture);
                }
            }
        }

        public int inFlightCount() {
            return inFlight.size();
        }
    }

    public static void testSingleFlightRequestCoalescing() throws Exception {
        System.out.println("  [TEST 5] Single-Flight Request Coalescing (Cache Stampede Defense)...");
        SingleFlightCoordinator<String, String> coordinator = new SingleFlightCoordinator<>();
        AtomicInteger expensiveDbCalls = new AtomicInteger(0);

        Function<String, String> expensiveLoader = key -> {
            expensiveDbCalls.incrementAndGet();
            try {
                Thread.sleep(50); // Simulate expensive multi-table join
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "COMPUTED_DATA_FOR_" + key;
        };

        // Sub-test A: Launch 20 concurrent threads for the EXACT SAME hot missing key
        int concurrency = 20;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrency);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await(); // Thundering herd trigger
                    String res = coordinator.execute("hot_leaderboard_key", expensiveLoader);
                    results.add(res);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release all 20 threads simultaneously
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assert completed : "All concurrent requests should complete";
        assert results.size() == concurrency : "All 20 callers must receive a response";
        for (String r : results) {
            assert "COMPUTED_DATA_FOR_hot_leaderboard_key".equals(r);
        }

        // The critical invariant: Despite 20 concurrent threads, the database was called EXACTLY ONCE!
        assert expensiveDbCalls.get() == 1 : "Single-flight coalescing must collapse 20 requests into 1 DB query! Found: " + expensiveDbCalls.get();
        assert coordinator.inFlightCount() == 0 : "In-flight map must be empty after completion";

        // Sub-test B: Exceptional completion cleanup & retry verification
        boolean threw = false;
        try {
            coordinator.execute("failing_key", k -> {
                throw new IllegalStateException("Database query timeout");
            });
        } catch (ExecutionException e) {
            threw = true;
            assert e.getCause() instanceof IllegalStateException;
        }
        assert threw : "Loader exception must be propagated to caller";
        assert coordinator.inFlightCount() == 0 : "In-flight map must clean up on exceptional completion";

        // Verification: Subsequent execution succeeds without being blocked by previous failure
        String retryResult = coordinator.execute("failing_key", k -> "RECOVERED_VALUE");
        assert "RECOVERED_VALUE".equals(retryResult);

        System.out.println("    -> Passed: 20 concurrent misses coalesced into 1 DB query; exceptional cleanup verified.");
    }

    // =========================================================================
    // 6. Dual-Write Invalidation Race Condition & Versioned Mitigation
    // =========================================================================

    public static class VersionedEntity {
        final String id;
        final String data;
        final long version;

        public VersionedEntity(String id, String data, long version) {
            this.id = id;
            this.data = data;
            this.version = version;
        }
    }

    public static class VersionedCache {
        private final ConcurrentMap<String, VersionedEntity> store = new ConcurrentHashMap<>();
        // Watermark tracking: Solves the deleted-key CAS race where an empty cache key
        // would otherwise accept stale writes because previous version metadata was deleted!
        private final ConcurrentMap<String, Long> versionWatermarks = new ConcurrentHashMap<>();

        public VersionedEntity get(String id) {
            return store.get(id);
        }

        public boolean putIfNewer(VersionedEntity newEntity) {
            // Check tombstone watermark: reject any read whose version is strictly older than the invalidation watermark!
            Long watermark = versionWatermarks.get(newEntity.id);
            if (watermark != null && newEntity.version < watermark) {
                // Reject stale write: database was already updated to a newer version than this read!
                return false;
            }

            VersionedEntity updated = store.compute(newEntity.id, (k, existing) -> {
                if (existing != null && newEntity.version <= existing.version) {
                    return existing; // Reject older or duplicate write
                }
                return newEntity;
            });

            return updated == newEntity;
        }

        /**
         * Evicts cached payload while recording a monotonic version watermark tombstone.
         * This prevents a delayed read miss from repopulating the evicted key with old data.
         */
        public void evictWithWatermark(String id, long committedDbVersion) {
            versionWatermarks.compute(id, (k, existing) ->
                existing == null ? committedDbVersion : Math.max(existing, committedDbVersion)
            );
            store.remove(id);
        }
    }

    public static void testVersionedCacheRaceMitigation() {
        System.out.println("  [TEST 6] Invalidation Race Condition & Versioned Cache Mitigation...");
        VersionedCache cache = new VersionedCache();

        // Scenario:
        // 1. Thread A reads Version 1 from DB (slow / delayed write-back to cache).
        VersionedEntity entityV1 = new VersionedEntity("user_99", "email: alice_old@example.com", 1L);

        // 2. Thread B updates DB to Version 2 and evicts cache with version watermark tombstone.
        VersionedEntity entityV2 = new VersionedEntity("user_99", "email: alice_new@example.com", 2L);
        cache.evictWithWatermark("user_99", 2L);

        // Verify key is empty in cache
        assert cache.get("user_99") == null : "Payload must be evicted from cache";

        // 3. Thread A (delayed) attempts to write back its stale Version 1 into the EMPTY cache!
        // In naive CAS, this write succeeds because key is absent.
        // With watermark tombstones, the stale write is safely REJECTED!
        boolean v1Accepted = cache.putIfNewer(entityV1);
        assert !v1Accepted : "Stale V1 write must be rejected by version watermark even when cache is empty!";
        assert cache.get("user_99") == null : "Cache must not hold stale V1 data";

        // 4. Thread B or future read puts fresh Version 2
        boolean v2Accepted = cache.putIfNewer(entityV2);
        assert v2Accepted : "Fresh V2 write must be accepted";
        assert cache.get("user_99").version == 2L;
        assert cache.get("user_99").data.contains("alice_new");

        System.out.println("    -> Passed: Version watermark tombstone rejected stale write into evicted cache key.");
    }

    // =========================================================================
    // 7. Graceful Degradation on Cache Failure
    // =========================================================================

    public static class UnreliableCache {
        private final AtomicBoolean isHealthy = new AtomicBoolean(true);

        public void simulateCrash() { isHealthy.set(false); }
        public void simulateRecovery() { isHealthy.set(true); }

        public String get(String key) {
            if (!isHealthy.get()) {
                throw new RuntimeException("Redis connection timed out (ECONNREFUSED)!");
            }
            return null; // Cache miss
        }

        public void put(String key, String val) {
            if (!isHealthy.get()) {
                throw new RuntimeException("Redis connection timed out (ECONNREFUSED)!");
            }
        }
    }

    public static class ResilientRepository {
        private final UnreliableCache cache;
        private final MockDatabase db;
        private final AtomicInteger cacheFailureCount = new AtomicInteger(0);

        public ResilientRepository(UnreliableCache cache, MockDatabase db) {
            this.cache = cache;
            this.db = db;
        }

        public String findData(String id) {
            // Step 1: Attempt cache read with defensive fallback
            try {
                String cached = cache.get(id);
                if (cached != null) return cached;
            } catch (Exception e) {
                // Log metric and continue to DB without failing the request!
                cacheFailureCount.incrementAndGet();
            }

            // Step 2: Reliable fallback to primary database
            String data = db.findById(id);

            // Step 3: Best-effort cache write
            if (data != null) {
                try {
                    cache.put(id, data);
                } catch (Exception e) {
                    // Non-fatal: write to cache failed, but user request succeeds
                    cacheFailureCount.incrementAndGet();
                }
            }

            return data;
        }

        public int getCacheFailureCount() { return cacheFailureCount.get(); }
    }

    public static void testGracefulCacheDegradation() {
        System.out.println("  [TEST 7] Graceful Cache Degradation (Circuit-Breaker & DB Fallback)...");
        MockDatabase db = new MockDatabase();
        db.save("item_777", "{\"item\":\"Critical Report\",\"status\":\"READY\"}");

        UnreliableCache cache = new UnreliableCache();
        ResilientRepository repo = new ResilientRepository(cache, db);

        // Crash the cache cluster
        cache.simulateCrash();

        // Read must succeed via DB fallback despite total cache outage
        String result = repo.findData("item_777");
        assert result != null && result.contains("Critical Report") : "Service must remain available when cache crashes";
        assert repo.getCacheFailureCount() > 0 : "Cache failures were logged defensively";
        assert db.getReadCount() == 1 : "Primary database served the request";

        System.out.println("    -> Passed: Total cache outage safely absorbed via graceful database fallback.");
    }

    // =========================================================================
    // Master Test Runner
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  MODULE 07 CACHING & PERFORMANCE TEST SUITE (JAVA 21 LTS)      ");
        System.out.println("=================================================================");

        testBoundedLruEviction();
        testTtlExpirationWithInjectedClock();
        testCacheAsidePattern();
        testNegativeCachingPenetrationDefense();
        try {
            testSingleFlightRequestCoalescing();
        } catch (Exception e) {
            throw new RuntimeException("Single-flight test failed", e);
        }
        testVersionedCacheRaceMitigation();
        testGracefulCacheDegradation();

        System.out.println("\nAll 7 Java Caching & Performance Test Suites Passed Successfully!");
    }
}
