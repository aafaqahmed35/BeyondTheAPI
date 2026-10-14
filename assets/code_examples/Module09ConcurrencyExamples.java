package assets.code_examples;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

/**
 * Module 09 — Concurrency & Distributed Systems Verification Suite.
 *
 * Implements deterministic simulations and verification of core concurrency
 * and distributed systems invariants in pure Java 21 LTS:
 * 1. Race Conditions & Correction: Unsynchronized vs. AtomicInteger CAS vs. ReentrantLock
 * 2. Producer-Consumer Coordination: BoundedBuffer with Explicit ReentrantLock & Conditions
 * 3. Deadlock Prevention: Strict Lock Ordering vs. Timed tryLock Backoff
 * 4. Asynchronous Composition: CompletableFuture Pipelines, Timeouts, and Exception Propagation
 * 5. Distributed Fencing Tokens: Monotonic Token Storage Defeating Zombie Writes After Lease Expiration
 * 6. Optimistic Concurrency: StampedLock Optimistic Reads with Pessimistic Fallback
 * 7. Virtual Threads: High-Concurrency Virtual Task Execution & Non-Blocking I/O Emulation
 */
public class Module09ConcurrencyExamples {

    // =========================================================================
    // 1. COUNTER ABSTRACTIONS (RACE CONDITION VS SYNCHRONIZATION VS CAS)
    // =========================================================================

    public static class UnsafeCounter {
        private int count = 0;

        public void increment() {
            count++; // Non-atomic read-modify-write
        }

        public int get() {
            return count;
        }
    }

    public static class SynchronizedCounter {
        private int count = 0;

        public synchronized void increment() {
            count++;
        }

        public synchronized int get() {
            return count;
        }
    }

    public static class AtomicCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        public void increment() {
            count.incrementAndGet();
        }

        public int get() {
            return count.get();
        }

        public boolean compareAndSet(int expected, int update) {
            return count.compareAndSet(expected, update);
        }
    }

    // =========================================================================
    // 2. BOUNDED BUFFER (PRODUCER-CONSUMER WITH EXPLICIT LOCK & CONDITIONS)
    // =========================================================================

    public static class BoundedBuffer<T> {
        private final Object[] items;
        private int head = 0;
        private int tail = 0;
        private int count = 0;

        private final ReentrantLock lock;
        private final Condition notEmpty;
        private final Condition notFull;

        public BoundedBuffer(int capacity, boolean fair) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("Capacity must be positive");
            }
            this.items = new Object[capacity];
            this.lock = new ReentrantLock(fair);
            this.notEmpty = lock.newCondition();
            this.notFull = lock.newCondition();
        }

        public void put(T item) throws InterruptedException {
            Objects.requireNonNull(item, "Null items not permitted");
            lock.lockInterruptibly();
            try {
                // Must use while-loop to defend against spurious wakeups
                while (count == items.length) {
                    notFull.await();
                }
                items[tail] = item;
                if (++tail == items.length) {
                    tail = 0;
                }
                count++;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        public T take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (count == 0) {
                    notEmpty.await();
                }
                T item = (T) items[head];
                items[head] = null; // Prevent loitering memory leak
                if (++head == items.length) {
                    head = 0;
                }
                count--;
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            lock.lock();
            try {
                return count;
            } finally {
                lock.unlock();
            }
        }

        public int capacity() {
            return items.length;
        }
    }

    // =========================================================================
    // 3. DEADLOCK PREVENTION: ORDERED LOCKS VS TIMED TRY-LOCK
    // =========================================================================

    public static class Account {
        private final int id;
        private int balance;
        final ReentrantLock lock = new ReentrantLock();

        public Account(int id, int initialBalance) {
            this.id = id;
            this.balance = initialBalance;
        }

        public int id() {
            return id;
        }

        public int getBalance() {
            lock.lock();
            try {
                return balance;
            } finally {
                lock.unlock();
            }
        }

        // Ordered lock acquisition prevents circular wait invariant
        public static boolean transferOrdered(Account from, Account to, int amount) {
            Account first = from.id < to.id ? from : to;
            Account second = from.id < to.id ? to : from;

            first.lock.lock();
            try {
                second.lock.lock();
                try {
                    if (from.balance >= amount) {
                        from.balance -= amount;
                        to.balance += amount;
                        return true;
                    }
                    return false;
                } finally {
                    second.lock.unlock();
                }
            } finally {
                first.lock.unlock();
            }
        }

        // Timed tryLock prevents indefinite circular blocking by backing off
        public static boolean transferTimedTryLock(Account from, Account to, int amount, long timeoutMs)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            ThreadLocalRandom random = ThreadLocalRandom.current();

            while (System.currentTimeMillis() < deadline) {
                if (from.lock.tryLock(5, TimeUnit.MILLISECONDS)) {
                    try {
                        if (to.lock.tryLock(5, TimeUnit.MILLISECONDS)) {
                            try {
                                if (from.balance >= amount) {
                                    from.balance -= amount;
                                    to.balance += amount;
                                    return true;
                                }
                                return false;
                            } finally {
                                to.lock.unlock();
                            }
                        }
                    } finally {
                        from.lock.unlock();
                    }
                }
                // Backoff jitter to prevent livelock lock-step retry oscillations
                Thread.sleep(random.nextInt(2, 10));
            }
            return false; // Timed out without acquiring both locks
        }
    }

    // =========================================================================
    // 4. DISTRIBUTED FENCING TOKEN STORAGE ENGINE & LEASE SIMULATOR
    // =========================================================================

    public static class StaleFencingTokenException extends RuntimeException {
        public StaleFencingTokenException(String message) {
            super(message);
        }
    }

    public static class DistributedStorageEngine {
        private final AtomicLong highestObservedFencingToken = new AtomicLong(0);
        private final Map<String, String> dataStore = new ConcurrentHashMap<>();

        public synchronized void write(String key, String value, long fencingToken) {
            long currentHighest = highestObservedFencingToken.get();
            if (fencingToken <= currentHighest) {
                throw new StaleFencingTokenException(
                    "Write rejected! Provided fencing token " + fencingToken +
                    " <= highest observed token " + currentHighest + " (Zombie write defeated)"
                );
            }
            highestObservedFencingToken.set(fencingToken);
            dataStore.put(key, value);
        }

        public String read(String key) {
            return dataStore.get(key);
        }

        public long getHighestObservedFencingToken() {
            return highestObservedFencingToken.get();
        }
    }

    public static class LeaseCoordinator {
        private final AtomicLong monotonicTokenGenerator = new AtomicLong(100);
        private final AtomicReference<LeaseGrant> currentGrant = new AtomicReference<>(null);

        public record LeaseGrant(String clientId, long fencingToken, long expiresAtMs) {}

        public LeaseGrant acquireLease(String clientId, long durationMs) {
            long now = System.currentTimeMillis();
            long token = monotonicTokenGenerator.incrementAndGet();
            LeaseGrant grant = new LeaseGrant(clientId, token, now + durationMs);
            currentGrant.set(grant);
            return grant;
        }

        public boolean isLeaseValid(LeaseGrant grant) {
            LeaseGrant active = currentGrant.get();
            return active != null
                    && active.clientId().equals(grant.clientId())
                    && active.fencingToken() == grant.fencingToken()
                    && System.currentTimeMillis() <= grant.expiresAtMs();
        }
    }

    // =========================================================================
    // 5. STAMPEDLOCK OPTIMISTIC READING COORDINATOR
    // =========================================================================

    public static class OptimisticPoint {
        private double x;
        private double y;
        private final StampedLock sl = new StampedLock();

        public OptimisticPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public void move(double deltaX, double deltaY) {
            long stamp = sl.writeLock();
            try {
                x += deltaX;
                y += deltaY;
            } finally {
                sl.unlockWrite(stamp);
            }
        }

        public double distanceFromOrigin() {
            long stamp = sl.tryOptimisticRead();
            double curX = x;
            double curY = y;
            if (!sl.validate(stamp)) {
                // Optimistic read invalidated by concurrent write; fall back to pessimistic read lock
                stamp = sl.readLock();
                try {
                    curX = x;
                    curY = y;
                } finally {
                    sl.unlockRead(stamp);
                }
            }
            return Math.hypot(curX, curY);
        }
    }

    // =========================================================================
    // MAIN VERIFICATION RUNNER & TEST SUITES
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println(" MODULE 09: CONCURRENCY & DISTRIBUTED SYSTEMS VERIFICATION SUITE ");
        System.out.println("=================================================================\n");

        testRaceConditionsAndAtomicGuarantees();
        testProducerConsumerBoundedBuffer();
        testDeadlockPreventionOrderedAndTimed();
        testCompletableFuturePipelinesAndTimeouts();
        testDistributedFencingTokensDefeatingZombies();
        testStampedLockOptimisticReading();
        testVirtualThreadsExecution();

        System.out.println("\n=================================================================");
        System.out.println(" ALL MODULE 09 JAVA CONCURRENCY TESTS PASSED PERFECTLY (7/7)");
        System.out.println("=================================================================");
    }

    private static void testRaceConditionsAndAtomicGuarantees() throws Exception {
        System.out.println("  [TEST 1] Shared Mutable State, Race Conditions & Atomic Guarantees...");
        final int THREADS = 8;
        final int ITERATIONS_PER_THREAD = 10_000;
        final int EXPECTED_TOTAL = THREADS * ITERATIONS_PER_THREAD;

        UnsafeCounter unsafe = new UnsafeCounter();
        SynchronizedCounter synchronizedCounter = new SynchronizedCounter();
        AtomicCounter atomicCounter = new AtomicCounter();

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        for (int t = 0; t < THREADS; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                        unsafe.increment();
                        synchronizedCounter.increment();
                        atomicCounter.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Fire all threads simultaneously
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        assert finished : "Execution should finish within 5 seconds";
        executor.shutdownNow();

        // Synchronized and Atomic counters must be mathematically exact
        assert synchronizedCounter.get() == EXPECTED_TOTAL :
            "Synchronized counter must equal exact expected total: " + EXPECTED_TOTAL + " but got " + synchronizedCounter.get();
        assert atomicCounter.get() == EXPECTED_TOTAL :
            "Atomic counter must equal exact expected total: " + EXPECTED_TOTAL + " but got " + atomicCounter.get();

        // Unsafe counter is uncoordinated; verify it does not exceed theoretical total
        assert unsafe.get() <= EXPECTED_TOTAL :
            "Unsafe counter cannot exceed theoretical iterations";

        // Verify CAS primitive semantics
        boolean casSuccess = atomicCounter.compareAndSet(EXPECTED_TOTAL, EXPECTED_TOTAL + 1);
        assert casSuccess : "CAS must succeed when expected value matches current state";
        assert atomicCounter.get() == EXPECTED_TOTAL + 1;

        boolean casFailure = atomicCounter.compareAndSet(EXPECTED_TOTAL, EXPECTED_TOTAL + 99);
        assert !casFailure : "CAS must fail when expected value does not match current state";
        assert atomicCounter.get() == EXPECTED_TOTAL + 1;

        System.out.println("    -> Passed: Synchronized & CAS counters maintained exact totals; CAS semantics verified.");
    }

    private static void testProducerConsumerBoundedBuffer() throws Exception {
        System.out.println("  [TEST 2] BoundedBuffer Coordination with ReentrantLock & Conditions...");
        final int CAPACITY = 5;
        final int TOTAL_ITEMS = 50;
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(CAPACITY, true);

        List<Integer> consumedItems = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch producerDone = new CountDownLatch(1);
        CountDownLatch consumerDone = new CountDownLatch(1);

        // Producer thread
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= TOTAL_ITEMS; i++) {
                    buffer.put(i);
                }
            } catch (InterruptedException ignored) {
            } finally {
                producerDone.countDown();
            }
        });

        // Consumer thread
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= TOTAL_ITEMS; i++) {
                    int val = buffer.take();
                    consumedItems.add(val);
                }
            } catch (InterruptedException ignored) {
            } finally {
                consumerDone.countDown();
            }
        });

        producer.start();
        consumer.start();

        assert producerDone.await(5, TimeUnit.SECONDS) : "Producer should finish within timeout";
        assert consumerDone.await(5, TimeUnit.SECONDS) : "Consumer should finish within timeout";

        assert buffer.size() == 0 : "Buffer should be completely empty after all items consumed";
        assert consumedItems.size() == TOTAL_ITEMS : "All items must be consumed";

        // Verify FIFO preservation
        for (int i = 0; i < TOTAL_ITEMS; i++) {
            assert consumedItems.get(i) == i + 1 : "Item sequence order must match FIFO producer order";
        }

        System.out.println("    -> Passed: BoundedBuffer maintained capacity boundary and delivered exact FIFO stream.");
    }

    private static void testDeadlockPreventionOrderedAndTimed() throws Exception {
        System.out.println("  [TEST 3] Deadlock Avoidance: Ordered Lock Acquisition & Timed tryLock...");
        Account a1 = new Account(1, 1000);
        Account a2 = new Account(2, 1000);

        final int ITERATIONS = 500;
        CountDownLatch latch = new CountDownLatch(2);

        // Thread 1: Transferred a1 -> a2
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                Account.transferOrdered(a1, a2, 1);
            }
            latch.countDown();
        });

        // Thread 2: Transferred a2 -> a1 concurrently (reverse direction)
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                Account.transferOrdered(a2, a1, 1);
            }
            latch.countDown();
        });

        t1.start();
        t2.start();

        boolean finishedWithoutDeadlock = latch.await(5, TimeUnit.SECONDS);
        assert finishedWithoutDeadlock : "Ordered lock transfers must finish without deadlock";
        assert (a1.getBalance() + a2.getBalance()) == 2000 : "Total money must be conserved";

        // Test timed tryLock backoff mechanism
        Account a3 = new Account(3, 500);
        Account a4 = new Account(4, 500);
        boolean timedTransferOk = Account.transferTimedTryLock(a3, a4, 100, 200);
        assert timedTransferOk : "Timed transfer should succeed under low contention";
        assert a3.getBalance() == 400;
        assert a4.getBalance() == 600;

        System.out.println("    -> Passed: Ordered locking prevented circular deadlock; timed tryLock transferred safely.");
    }

    private static void testCompletableFuturePipelinesAndTimeouts() throws Exception {
        System.out.println("  [TEST 4] CompletableFuture Pipelines, Chaining, Timeouts & Error Propagation...");

        // 1. Successful pipeline chaining
        CompletableFuture<String> pipeline = CompletableFuture.supplyAsync(() -> "order_123")
                .thenApply(orderId -> "verified_" + orderId)
                .thenCompose(verified -> CompletableFuture.supplyAsync(() -> verified + "_billed"))
                .thenApply(String::toUpperCase);

        String result = pipeline.get(2, TimeUnit.SECONDS);
        assert "VERIFIED_ORDER_123_BILLED".equals(result) : "Pipeline output must match expected composed string";

        // 2. Exception propagation and recovery via exceptionally
        CompletableFuture<String> failedPipeline = CompletableFuture.<String>supplyAsync(() -> {
            throw new IllegalStateException("Payment gateway unreachable");
        }).exceptionally(ex -> {
            assert ex.getCause() instanceof IllegalStateException;
            return "fallback_cached_payment";
        });

        String recoveredResult = failedPipeline.get(2, TimeUnit.SECONDS);
        assert "fallback_cached_payment".equals(recoveredResult) : "Exceptionally must intercept and recover";

        // 3. Timeout handling via orTimeout (Java 9+)
        CompletableFuture<String> slowTask = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            return "slow_response";
        }).orTimeout(50, TimeUnit.MILLISECONDS);

        boolean timeoutCaught = false;
        try {
            slowTask.join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                timeoutCaught = true;
            }
        }
        assert timeoutCaught : "Task taking 500ms with 50ms orTimeout must fail with TimeoutException";

        // 4. completeOnTimeout fallback
        CompletableFuture<String> resilientSlowTask = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            return "slow_response";
        }).completeOnTimeout("circuit_breaker_default", 50, TimeUnit.MILLISECONDS);

        String fallbackVal = resilientSlowTask.join();
        assert "circuit_breaker_default".equals(fallbackVal) : "completeOnTimeout must return default value";

        System.out.println("    -> Passed: CompletableFuture pipelines, chaining, exception recovery, and timeouts verified.");
    }

    private static void testDistributedFencingTokensDefeatingZombies() throws Exception {
        System.out.println("  [TEST 5] Distributed Leases & Monotonic Fencing Tokens (Zombie Write Defeat)...");
        DistributedStorageEngine storage = new DistributedStorageEngine();
        LeaseCoordinator coordinator = new LeaseCoordinator();

        // 1. Client 1 acquires lease with initial token (e.g. 101)
        LeaseCoordinator.LeaseGrant client1Grant = coordinator.acquireLease("client-1", 100);
        long token1 = client1Grant.fencingToken();
        assert token1 == 101 : "First acquired token should be 101";

        // Client 1 writes successfully with token 101
        storage.write("leader_lock", "client-1 payload", token1);
        assert "client-1 payload".equals(storage.read("leader_lock"));
        assert storage.getHighestObservedFencingToken() == 101;

        // 2. Client 1 experiences long Stop-The-World GC pause or network stall
        // The lease expires in the coordinator's time domain.
        // Client 2 detects expiration and acquires new lease with monotonic token (102)
        LeaseCoordinator.LeaseGrant client2Grant = coordinator.acquireLease("client-2", 200);
        long token2 = client2Grant.fencingToken();
        assert token2 == 102 : "Second acquired token must be strictly monotonic: 102";
        assert token2 > token1 : "New token must be strictly greater than previous token";

        // Client 2 writes successfully to storage with token 102
        storage.write("leader_lock", "client-2 payload", token2);
        assert "client-2 payload".equals(storage.read("leader_lock"));
        assert storage.getHighestObservedFencingToken() == 102;

        // 3. Client 1's GC pause ends. Client 1 wakes up unaware that its lease expired.
        // Client 1 attempts to write its pending update to storage using its old token (101).
        boolean writeRejected = false;
        try {
            storage.write("leader_lock", "stale zombie write from client-1", token1);
        } catch (StaleFencingTokenException ex) {
            writeRejected = true;
        }

        assert writeRejected : "Storage MUST REJECT write from Client 1 with stale fencing token 101 <= 102!";
        assert "client-2 payload".equals(storage.read("leader_lock")) :
            "Storage state must remain preserved as Client 2's valid payload";

        System.out.println("    -> Passed: Storage engine rejected zombie write using monotonic fencing token check.");
    }

    private static void testStampedLockOptimisticReading() throws Exception {
        System.out.println("  [TEST 6] StampedLock Optimistic Reading vs Pessimistic Fallback...");
        OptimisticPoint point = new OptimisticPoint(3.0, 4.0);

        // Initial distance: sqrt(3^2 + 4^2) = 5.0
        double dist = point.distanceFromOrigin();
        assert Math.abs(dist - 5.0) < 1e-9 : "Initial distance must be 5.0";

        // Concurrent mutation while reading
        int writerCount = 4;
        int readerCount = 8;
        ExecutorService exec = Executors.newFixedThreadPool(writerCount + readerCount);
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch doneLatch = new CountDownLatch(readerCount);

        for (int i = 0; i < writerCount; i++) {
            exec.submit(() -> {
                while (running.get()) {
                    point.move(1.0, 1.0);
                    point.move(-1.0, -1.0);
                }
            });
        }

        for (int i = 0; i < readerCount; i++) {
            exec.submit(() -> {
                try {
                    for (int r = 0; r < 1000; r++) {
                        double d = point.distanceFromOrigin();
                        assert d >= 0 : "Distance must always be non-negative";
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean readersDone = doneLatch.await(3, TimeUnit.SECONDS);
        running.set(false);
        exec.shutdownNow();

        assert readersDone : "All optimistic readers should finish successfully under write load";
        System.out.println("    -> Passed: StampedLock optimistic reads and pessimistic fallbacks executed with zero corruption.");
    }

    private static void testVirtualThreadsExecution() throws Exception {
        System.out.println("  [TEST 7] Java 21 Virtual Threads & High-Concurrency Task Throughput...");
        final int TASK_COUNT = 1_000;
        AtomicInteger completedTasks = new AtomicInteger(0);

        Instant start = Instant.now();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>(TASK_COUNT);
            for (int i = 0; i < TASK_COUNT; i++) {
                final int taskId = i;
                futures.add(executor.submit(() -> {
                    // Simulate non-blocking blocking I/O (virtual thread yields carrier thread)
                    Thread.sleep(10);
                    completedTasks.incrementAndGet();
                    return null;
                }));
            }

            for (var f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        }
        Duration elapsed = Duration.between(start, Instant.now());

        assert completedTasks.get() == TASK_COUNT : "All 1,000 virtual thread tasks must complete successfully";
        assert elapsed.toMillis() < 4000 : "1,000 virtual threads sleeping 10ms should finish well under 4 seconds";

        System.out.println("    -> Passed: 1,000 virtual threads completed in " + elapsed.toMillis() + "ms without carrier thread exhaustion.");
    }
}
