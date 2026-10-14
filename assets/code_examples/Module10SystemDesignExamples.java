package assets.code_examples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Module 10 — System Design & Production Architecture Verification Suite.
 *
 * Implements deterministic simulations and verification of core architectural primitives in pure Java 21 LTS:
 * 1. Consistent Hashing Ring with Virtual Nodes and Minimal Key Churn
 * 2. Token Bucket Rate Limiter with Fractional Replenishment and Burst Capacity
 * 3. Circuit Breaker State Machine (Closed -> Open -> Half-Open -> Closed)
 * 4. 64-Bit Snowflake Distributed ID Generator & Base62 URL Encoder
 * 5. Single-Flight Request Coalescing (Cache Stampede / Thundering Herd Defense)
 */
public class Module10SystemDesignExamples {

    // =========================================================================
    // 1. CONSISTENT HASHING RING WITH VIRTUAL NODES
    // =========================================================================

    public static class ConsistentHashRouter<T> {
        private final int numberOfReplicas; // Virtual nodes per physical node
        private final SortedMap<Long, T> circle = new ConcurrentSkipListMap<>();

        public ConsistentHashRouter(int numberOfReplicas, Collection<T> nodes) {
            this.numberOfReplicas = numberOfReplicas;
            for (T node : nodes) {
                add(node);
            }
        }

        private long hash(String key) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
                // Convert first 8 bytes of MD5 digest to long
                long h = 0;
                for (int i = 0; i < 8; i++) {
                    h = (h << 8) | (digest[i] & 0xFF);
                }
                return h;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("MD5 not available", e);
            }
        }

        public void add(T node) {
            for (int i = 0; i < numberOfReplicas; i++) {
                circle.put(hash(node.toString() + "#VN_" + i), node);
            }
        }

        public void remove(T node) {
            for (int i = 0; i < numberOfReplicas; i++) {
                circle.remove(hash(node.toString() + "#VN_" + i));
            }
        }

        public T get(String key) {
            if (circle.isEmpty()) {
                return null;
            }
            long hash = hash(key);
            if (!circle.containsKey(hash)) {
                SortedMap<Long, T> tailMap = circle.tailMap(hash);
                hash = tailMap.isEmpty() ? circle.firstKey() : tailMap.firstKey();
            }
            return circle.get(hash);
        }

        public int size() {
            return circle.size();
        }
    }

    // =========================================================================
    // 2. TOKEN BUCKET RATE LIMITER
    // =========================================================================

    public static class TokenBucketRateLimiter {
        private final long capacity;
        private final double refillRatePerSecond;
        private double availableTokens;
        private long lastRefillTimestampNanos;
        private final ReentrantLock lock = new ReentrantLock();

        public TokenBucketRateLimiter(long capacity, double refillRatePerSecond) {
            if (capacity <= 0 || refillRatePerSecond <= 0) {
                throw new IllegalArgumentException("Capacity and refill rate must be positive");
            }
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.availableTokens = capacity;
            this.lastRefillTimestampNanos = System.nanoTime();
        }

        public boolean tryConsume(int tokensToConsume) {
            lock.lock();
            try {
                refill();
                if (availableTokens >= tokensToConsume) {
                    availableTokens -= tokensToConsume;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillTimestampNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                double addedTokens = elapsedSeconds * refillRatePerSecond;
                availableTokens = Math.min(capacity, availableTokens + addedTokens);
                lastRefillTimestampNanos = now;
            }
        }

        public double getAvailableTokens() {
            lock.lock();
            try {
                refill();
                return availableTokens;
            } finally {
                lock.unlock();
            }
        }
    }

    // =========================================================================
    // 3. CIRCUIT BREAKER STATE MACHINE
    // =========================================================================

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public static class CircuitBreaker {
        private final int failureThreshold;
        private final Duration sleepWindow;
        private final int halfOpenTrialSuccessTarget;

        private CircuitState state = CircuitState.CLOSED;
        private int failureCount = 0;
        private int consecutiveSuccesses = 0;
        private Instant lastStateTransitionTime = Instant.now();
        private final ReentrantLock lock = new ReentrantLock();

        public CircuitBreaker(int failureThreshold, Duration sleepWindow, int halfOpenTrialSuccessTarget) {
            this.failureThreshold = failureThreshold;
            this.sleepWindow = sleepWindow;
            this.halfOpenTrialSuccessTarget = halfOpenTrialSuccessTarget;
        }

        public boolean allowExecution() {
            lock.lock();
            try {
                Instant now = Instant.now();
                if (state == CircuitState.OPEN) {
                    if (Duration.between(lastStateTransitionTime, now).compareTo(sleepWindow) >= 0) {
                        state = CircuitState.HALF_OPEN;
                        consecutiveSuccesses = 0;
                        lastStateTransitionTime = now;
                        return true;
                    }
                    return false;
                }
                return true; // CLOSED or HALF_OPEN accepts probes
            } finally {
                lock.unlock();
            }
        }

        public void recordSuccess() {
            lock.lock();
            try {
                if (state == CircuitState.HALF_OPEN) {
                    consecutiveSuccesses++;
                    if (consecutiveSuccesses >= halfOpenTrialSuccessTarget) {
                        state = CircuitState.CLOSED;
                        failureCount = 0;
                        consecutiveSuccesses = 0;
                        lastStateTransitionTime = Instant.now();
                    }
                } else if (state == CircuitState.CLOSED) {
                    failureCount = 0;
                }
            } finally {
                lock.unlock();
            }
        }

        public void recordFailure() {
            lock.lock();
            try {
                lastStateTransitionTime = Instant.now();
                if (state == CircuitState.HALF_OPEN) {
                    state = CircuitState.OPEN;
                    failureCount = failureThreshold;
                    consecutiveSuccesses = 0;
                } else if (state == CircuitState.CLOSED) {
                    failureCount++;
                    if (failureCount >= failureThreshold) {
                        state = CircuitState.OPEN;
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        public CircuitState getState() {
            lock.lock();
            try {
                return state;
            } finally {
                lock.unlock();
            }
        }
    }

    // =========================================================================
    // 4. DISTRIBUTED SNOWFLAKE 64-BIT ID & BASE62 ENCODER
    // =========================================================================

    public static class SnowflakeIdGenerator {
        private static final long EPOCH = 1704067200000L; // 2024-01-01 00:00:00 UTC
        private static final long WORKER_ID_BITS = 10L;
        private static final long SEQUENCE_BITS = 12L;
        private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

        private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

        private final long workerId;
        private long lastTimestamp = -1L;
        private long sequence = 0L;
        private final ReentrantLock lock = new ReentrantLock();

        public SnowflakeIdGenerator(long workerId) {
            if (workerId < 0 || workerId > MAX_WORKER_ID) {
                throw new IllegalArgumentException("Worker ID must be between 0 and " + MAX_WORKER_ID);
            }
            this.workerId = workerId;
        }

        public long nextId() {
            lock.lock();
            try {
                long currentTimestamp = System.currentTimeMillis();
                if (currentTimestamp < lastTimestamp) {
                    throw new IllegalStateException("Clock moved backwards! Rejecting generation.");
                }

                if (currentTimestamp == lastTimestamp) {
                    sequence = (sequence + 1) & MAX_SEQUENCE;
                    if (sequence == 0) {
                        // Sequence exhausted within same millisecond; spin wait for next ms
                        while (currentTimestamp <= lastTimestamp) {
                            currentTimestamp = System.currentTimeMillis();
                        }
                    }
                } else {
                    sequence = 0L;
                }

                lastTimestamp = currentTimestamp;
                return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT)
                        | (workerId << WORKER_ID_SHIFT)
                        | sequence;
            } finally {
                lock.unlock();
            }
        }
    }

    public static class Base62Encoder {
        private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        public static String encode(long value) {
            if (value == 0) {
                return "0";
            }
            StringBuilder sb = new StringBuilder();
            long num = Math.abs(value);
            while (num > 0) {
                int rem = (int) (num % 62);
                sb.append(ALPHABET.charAt(rem));
                num /= 62;
            }
            return sb.reverse().toString();
        }

        public static long decode(String str) {
            long result = 0;
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                int val = ALPHABET.indexOf(c);
                if (val == -1) {
                    throw new IllegalArgumentException("Invalid Base62 character: " + c);
                }
                result = result * 62 + val;
            }
            return result;
        }
    }

    // =========================================================================
    // 5. SINGLE-FLIGHT REQUEST COALESCER (STAMPEDE DEFENSE)
    // =========================================================================

    public static class SingleFlightCoordinator<K, V> {
        private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

        public V execute(K key, Callable<V> loader) throws Exception {
            CompletableFuture<V> future = new CompletableFuture<>();
            CompletableFuture<V> existing = inFlight.putIfAbsent(key, future);

            if (existing != null) {
                // Another thread is already fetching this key; join the in-flight future
                return existing.get();
            }

            try {
                V result = loader.call();
                future.complete(result);
                return result;
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
                throw (ex instanceof Exception) ? (Exception) ex : new RuntimeException(ex);
            } finally {
                inFlight.remove(key, future);
            }
        }

        public int activeFlightCount() {
            return inFlight.size();
        }
    }

    // =========================================================================
    // MAIN VERIFICATION SUITE
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println(" MODULE 10: SYSTEM DESIGN & PRODUCTION ARCHITECTURE VERIFICATION ");
        System.out.println("=================================================================\n");

        testConsistentHashingRing();
        testTokenBucketRateLimiter();
        testCircuitBreakerStateMachine();
        testSnowflakeAndBase62Encoding();
        testSingleFlightCoalescing();

        System.out.println("\n=================================================================");
        System.out.println(" ALL MODULE 10 ARCHITECTURAL PRIMITIVES PASSED PERFECTLY (5/5)   ");
        System.out.println("=================================================================");
    }

    private static void testConsistentHashingRing() {
        System.out.println("  [TEST 1] Consistent Hashing Ring with Virtual Nodes & Minimal Churn...");
        List<String> nodes = new ArrayList<>(List.of("node-A", "node-B", "node-C"));
        ConsistentHashRouter<String> router = new ConsistentHashRouter<>(100, nodes);

        // Map 10,000 keys across initial 3 nodes
        Map<String, String> keyAssignmentsBefore = new HashMap<>();
        Map<String, Integer> nodeCountsBefore = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            String key = "session_user_" + i;
            String node = router.get(key);
            keyAssignmentsBefore.put(key, node);
            nodeCountsBefore.put(node, nodeCountsBefore.getOrDefault(node, 0) + 1);
        }

        // Verify all 3 nodes receive traffic (no starving nodes)
        assert nodeCountsBefore.size() == 3 : "All 3 nodes must have key assignments";
        for (String node : nodes) {
            int count = nodeCountsBefore.getOrDefault(node, 0);
            assert count > 2000 && count < 5000 : "Virtual nodes should achieve rough distribution: " + node + " got " + count;
        }

        // Add a 4th node: "node-D"
        router.add("node-D");

        int remappedKeys = 0;
        for (int i = 0; i < 10_000; i++) {
            String key = "session_user_" + i;
            String newNode = router.get(key);
            if (!newNode.equals(keyAssignmentsBefore.get(key))) {
                remappedKeys++;
                // In consistent hashing, keys remapped should ONLY move to the new node
                assert "node-D".equals(newNode) : "Remapped keys must migrate to the new node, not shuffle between existing nodes";
            }
        }

        // In theoretical consistent hashing, adding 1 node to 3 nodes should remap ~ 1/4 = 25% of keys
        double remappedPercentage = (remappedKeys / 10000.0) * 100.0;
        assert remappedPercentage > 15.0 && remappedPercentage < 35.0 :
            "Adding 4th node should remap ~25% of keys (actual: " + remappedPercentage + "%)";

        System.out.printf("    -> Passed: 3-node distribution verified. Adding node-D remapped %.1f%% of keys (expected ~25%%).%n", remappedPercentage);
    }

    private static void testTokenBucketRateLimiter() throws Exception {
        System.out.println("  [TEST 2] Token Bucket Rate Limiter (Burst Allowance & Fractional Refill)...");
        // Capacity = 5 tokens, refill rate = 10 tokens/sec
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 10.0);

        // Immediate burst of 5 tokens should all succeed
        for (int i = 0; i < 5; i++) {
            assert limiter.tryConsume(1) : "Token " + i + " in initial burst must succeed";
        }

        // 6th request immediately after must fail (bucket exhausted)
        assert !limiter.tryConsume(1) : "6th request exceeding bucket capacity must be rejected";

        // Wait 250ms (refill 10 * 0.25 = 2.5 tokens)
        Thread.sleep(250);

        assert limiter.tryConsume(2) : "Refilled tokens must allow 2 subsequent requests";
        assert !limiter.tryConsume(2) : "Excessive request above refilled tokens must be rejected";

        System.out.println("    -> Passed: Token bucket handled 5-token burst, enforced depletion, and refilled accurately.");
    }

    private static void testCircuitBreakerStateMachine() throws Exception {
        System.out.println("  [TEST 3] Circuit Breaker State Machine (Closed -> Open -> Half-Open -> Closed)...");
        // Threshold = 3 failures, sleep window = 100ms, half-open target = 2 successes
        CircuitBreaker cb = new CircuitBreaker(3, Duration.ofMillis(100), 2);

        assert cb.getState() == CircuitState.CLOSED : "Initial state must be CLOSED";
        assert cb.allowExecution();

        // Record 2 failures (threshold not reached)
        cb.recordFailure();
        cb.recordFailure();
        assert cb.getState() == CircuitState.CLOSED : "State must remain CLOSED below failure threshold";

        // 3rd failure trips the breaker to OPEN
        cb.recordFailure();
        assert cb.getState() == CircuitState.OPEN : "Breaker must trip to OPEN on 3rd failure";

        // Execution immediately rejected while OPEN
        assert !cb.allowExecution() : "Calls must fail fast when circuit is OPEN";

        // Wait for sleep window to expire (150ms > 100ms)
        Thread.sleep(150);

        // Probe call should transition breaker to HALF_OPEN
        assert cb.allowExecution() : "Probe request must be allowed after sleep window expires";
        assert cb.getState() == CircuitState.HALF_OPEN : "Circuit must transition to HALF_OPEN";

        // First successful probe
        cb.recordSuccess();
        assert cb.getState() == CircuitState.HALF_OPEN : "Breaker remains HALF_OPEN until trial target (2) reached";

        // Second successful probe heals the breaker to CLOSED
        cb.recordSuccess();
        assert cb.getState() == CircuitState.CLOSED : "Breaker must heal to CLOSED after 2 consecutive probe successes";
        assert cb.allowExecution() : "Normal execution resumed";

        System.out.println("    -> Passed: Circuit breaker correctly navigated CLOSED -> OPEN -> HALF_OPEN -> CLOSED lifecycle.");
    }

    private static void testSnowflakeAndBase62Encoding() {
        System.out.println("  [TEST 4] Snowflake 64-Bit ID Generator & Base62 Short URL Encoding...");
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7); // Worker ID 7

        Set<Long> generatedIds = new HashSet<>();
        final int COUNT = 10_000;
        for (int i = 0; i < COUNT; i++) {
            long id = generator.nextId();
            assert generatedIds.add(id) : "Snowflake generator must produce globally unique IDs: Collision on " + id;
        }

        assert generatedIds.size() == COUNT : "Must generate exactly 10,000 distinct IDs";

        // Test Base62 encoding and round-trip decoding
        long testId = 1704067200000L + 999999L;
        String encoded = Base62Encoder.encode(testId);
        long decoded = Base62Encoder.decode(encoded);

        assert decoded == testId : "Base62 decode must match original long ID: " + testId + " != " + decoded;
        assert encoded.length() <= 11 : "64-bit integer encodes to at most 11 Base62 characters";

        System.out.println("    -> Passed: 10,000 unique Snowflake IDs generated with zero collisions; Base62 round-trip verified.");
    }

    private static void testSingleFlightCoalescing() throws Exception {
        System.out.println("  [TEST 5] Single-Flight Request Coalescing (Thundering Herd Defense)...");
        SingleFlightCoordinator<String, String> coordinator = new SingleFlightCoordinator<>();
        AtomicInteger databaseQueryCount = new AtomicInteger(0);

        final int CONCURRENT_CALLERS = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_CALLERS);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CALLERS);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < CONCURRENT_CALLERS; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String val = coordinator.execute("hot_product_catalog_99", () -> {
                        // Simulate slow DB query taking 50ms
                        Thread.sleep(50);
                        databaseQueryCount.incrementAndGet();
                        return "product_payload_cached";
                    });
                    results.add(val);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all 20 threads simultaneously
        boolean finished = doneLatch.await(3, TimeUnit.SECONDS);
        assert finished : "All concurrent callers should complete within timeout";
        executor.shutdownNow();

        assert results.size() == CONCURRENT_CALLERS : "All 20 callers must receive results";
        for (String r : results) {
            assert "product_payload_cached".equals(r);
        }

        // Single-Flight invariant: Despite 20 concurrent misses, the DB query should only execute ONCE
        assert databaseQueryCount.get() == 1 :
            "Thundering herd must be coalesced into exactly 1 DB query, but saw: " + databaseQueryCount.get();

        System.out.println("    -> Passed: 20 concurrent requests for identical key coalesced into exactly 1 database execution.");
    }
}
