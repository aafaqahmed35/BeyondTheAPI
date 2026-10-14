package assets.code_examples;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Module 12 — Debugging & Testing Verification Suite.
 *
 * Implements deterministic simulations and verification of core testing and debugging concepts in Java 21 LTS:
 * 1. Test Doubles Taxonomy: Dummy, Stub, Fake (In-Memory Repository), Mock, and Spy
 * 2. Deterministic Concurrency Testing with CyclicBarrier and CountDownLatch (No Thread.sleep)
 * 3. Flaky Test Isolation and Clock Injection (Time-Freezing Determinism)
 * 4. Mutation Testing Engine & Weak Coverage vs High-Confidence Assertion Proof
 * 5. Property-Based Invariant Verification with Seeded Generative Fuzzing
 */
public class Module12TestingExamples {

    // =========================================================================
    // 1. TEST DOUBLES TAXONOMY: DUMMY, STUB, FAKE, MOCK, SPY
    // =========================================================================

    public record User(String id, String email, boolean active) {}
    public record Order(String orderId, String userId, double amount) {}

    public interface UserRepository {
        void save(User user);
        Optional<User> findById(String id);
    }

    public interface PaymentGateway {
        boolean charge(String userId, double amount);
    }

    public interface NotificationService {
        void sendOrderConfirmation(String email, String orderId);
    }

    public interface AuditLogger {
        void log(String message);
    }

    // Dummy: Passed simply to satisfy method signature, never invoked in the test scenario
    public static class DummyAuditLogger implements AuditLogger {
        @Override
        public void log(String message) {
            throw new UnsupportedOperationException("Dummy should never be called in this test scenario!");
        }
    }

    // Stub: Returns canned answers to calls made during test, no state tracking
    public static class StubPaymentGateway implements PaymentGateway {
        private final boolean response;

        public StubPaymentGateway(boolean response) {
            this.response = response;
        }

        @Override
        public boolean charge(String userId, double amount) {
            return response;
        }
    }

    // Fake: Working implementation with shortcuts suitable for testing (in-memory map)
    public static class FakeUserRepository implements UserRepository {
        private final Map<String, User> store = new ConcurrentHashMap<>();

        @Override
        public void save(User user) {
            store.put(user.id(), user);
        }

        @Override
        public Optional<User> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        public int size() {
            return store.size();
        }
    }

    // Mock: Configured with expectations, verifies invocation count and arguments
    public static class MockNotificationService implements NotificationService {
        private final List<String> sentConfirmations = new CopyOnWriteArrayList<>();
        private int expectedInvocations = 0;

        public void expectInvocations(int count) {
            this.expectedInvocations = count;
        }

        @Override
        public void sendOrderConfirmation(String email, String orderId) {
            sentConfirmations.add(email + ":" + orderId);
        }

        public void verify() {
            if (sentConfirmations.size() != expectedInvocations) {
                throw new AssertionError("Mock verification failed: expected " + expectedInvocations +
                        " calls, but received " + sentConfirmations.size());
            }
        }

        public boolean wasCalledWith(String email, String orderId) {
            return sentConfirmations.contains(email + ":" + orderId);
        }
    }

    // Spy: Wraps real implementation or records method invocations while delegating behavior
    public static class SpyPaymentGateway implements PaymentGateway {
        private final PaymentGateway delegate;
        private final List<Double> recordedAmounts = new ArrayList<>();

        public SpyPaymentGateway(PaymentGateway delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean charge(String userId, double amount) {
            recordedAmounts.add(amount);
            return delegate.charge(userId, amount);
        }

        public List<Double> getRecordedAmounts() {
            return Collections.unmodifiableList(recordedAmounts);
        }
    }

    public static class OrderProcessingService {
        private final UserRepository userRepository;
        private final PaymentGateway paymentGateway;
        private final NotificationService notificationService;
        private final AuditLogger auditLogger;

        public OrderProcessingService(UserRepository userRepository,
                                      PaymentGateway paymentGateway,
                                      NotificationService notificationService,
                                      AuditLogger auditLogger) {
            this.userRepository = userRepository;
            this.paymentGateway = paymentGateway;
            this.notificationService = notificationService;
            this.auditLogger = auditLogger;
        }

        public boolean processOrder(Order order) {
            Optional<User> userOpt = userRepository.findById(order.userId());
            if (userOpt.isEmpty() || !userOpt.get().active()) {
                return false;
            }
            User user = userOpt.get();

            boolean charged = paymentGateway.charge(user.id(), order.amount());
            if (!charged) {
                return false;
            }

            notificationService.sendOrderConfirmation(user.email(), order.orderId());
            // Note: auditLogger not called on successful light checkout in this flow
            return true;
        }
    }

    // =========================================================================
    // 2. DETERMINISTIC CONCURRENCY TESTING (NO ARBITRARY THREAD.SLEEP)
    // =========================================================================

    public static class ConcurrentIdempotencyCache<K, V> {
        private final ConcurrentMap<K, CompletableFuture<V>> cache = new ConcurrentHashMap<>();
        private final AtomicInteger computationCount = new AtomicInteger(0);

        public V computeIfAbsent(K key, java.util.function.Function<K, V> mappingFunction) {
            CompletableFuture<V> future = cache.computeIfAbsent(key, k -> {
                CompletableFuture<V> f = new CompletableFuture<>();
                try {
                    computationCount.incrementAndGet();
                    V val = mappingFunction.apply(k);
                    f.complete(val);
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
                return f;
            });

            try {
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                cache.remove(key);
                throw new RuntimeException("Computation failed or timed out", e);
            }
        }

        public int getComputationCount() {
            return computationCount.get();
        }
    }

    // =========================================================================
    // 3. FLAKY TEST ISOLATION & INJECTABLE CLOCK REPRODUCTION
    // =========================================================================

    public static class SubscriptionService {
        private final Clock clock;

        public SubscriptionService(Clock clock) {
            this.clock = clock;
        }

        public boolean isTrialActive(Instant registrationTime, Duration trialDuration) {
            Instant now = clock.instant();
            Instant expirationTime = registrationTime.plus(trialDuration);
            return now.isBefore(expirationTime);
        }
    }

    // =========================================================================
    // 4. MUTATION TESTING ENGINE & ASSERTION QUALITY VERIFICATION
    // =========================================================================

    public static class DiscountCalculator {
        // Original implementation under test
        public double calculateDiscount(double cartTotal) {
            if (cartTotal > 100.0) {
                return cartTotal * 0.10;
            }
            return 0.0;
        }
    }

    public interface DiscountMutant {
        double calculateDiscount(double cartTotal);
        String description();
    }

    public static class MutationEngine {
        private final List<DiscountMutant> mutants = new ArrayList<>();

        public MutationEngine() {
            // Mutant 1: Boundary condition mutation (> to >=)
            mutants.add(new DiscountMutant() {
                @Override public double calculateDiscount(double total) {
                    if (total >= 100.0) return total * 0.10;
                    return 0.0;
                }
                @Override public String description() { return "Mutant 1: Changed `>` to `>=` (Boundary)"; }
            });

            // Mutant 2: Discount multiplier scalar mutation (0.10 to 0.05)
            mutants.add(new DiscountMutant() {
                @Override public double calculateDiscount(double total) {
                    if (total > 100.0) return total * 0.05;
                    return 0.0;
                }
                @Override public String description() { return "Mutant 2: Changed `0.10` to `0.05` (Scalar)"; }
            });

            // Mutant 3: Inverted condition (> to <)
            mutants.add(new DiscountMutant() {
                @Override public double calculateDiscount(double total) {
                    if (total < 100.0) return total * 0.10;
                    return 0.0;
                }
                @Override public String description() { return "Mutant 3: Inverted conditional to `<`"; }
            });

            // Mutant 4: Return value replacement (always return 0.0)
            mutants.add(new DiscountMutant() {
                @Override public double calculateDiscount(double total) {
                    return 0.0;
                }
                @Override public String description() { return "Mutant 4: Void/Zero return value"; }
            });
        }

        public double evaluateTestSuite(java.util.function.Consumer<DiscountMutant> testSuite) {
            int killed = 0;
            for (DiscountMutant mutant : mutants) {
                try {
                    testSuite.accept(mutant);
                    // If no assertion error thrown, mutant SURVIVED
                } catch (AssertionError e) {
                    killed++;
                }
            }
            return (double) killed / mutants.size();
        }
    }

    // =========================================================================
    // 5. PROPERTY-BASED INVARIANT VERIFICATION
    // =========================================================================

    public static class SortingPropertyVerifier {
        public static boolean isSorted(int[] arr) {
            for (int i = 0; i < arr.length - 1; i++) {
                if (arr[i] > arr[i + 1]) return false;
            }
            return true;
        }

        public static Map<Integer, Integer> frequencyMap(int[] arr) {
            Map<Integer, Integer> map = new HashMap<>();
            for (int val : arr) {
                map.merge(val, 1, Integer::sum);
            }
            return map;
        }
    }

    // =========================================================================
    // MAIN EXECUTION & DETERMINISTIC TEST RUNNER
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println(" MODULE 12: DEBUGGING & TESTING VERIFICATION HARNESS (JAVA 21)   ");
        System.out.println("=================================================================\n");

        testTestDoublesTaxonomy();
        testDeterministicConcurrencyWithoutSleep();
        testFlakyTestIsolationAndClockInjection();
        testMutationTestingAndAssertionQuality();
        testPropertyBasedInvariantVerification();

        System.out.println("\n=================================================================");
        System.out.println(" ALL MODULE 12 TESTING & DEBUGGING SUITES PASSED PERFECTLY (5/5) ");
        System.out.println("=================================================================");
    }

    private static void testTestDoublesTaxonomy() {
        System.out.println("  [TEST 1] Test Doubles Taxonomy (Dummy, Stub, Fake, Mock, Spy)...");

        // 1. Setup Fake Repository
        FakeUserRepository fakeRepo = new FakeUserRepository();
        fakeRepo.save(new User("usr_100", "alice@example.com", true));
        fakeRepo.save(new User("usr_inactive", "bob@example.com", false));

        // 2. Setup Stub Gateway
        StubPaymentGateway stubPayment = new StubPaymentGateway(true);

        // 3. Setup Spy Gateway wrapping Stub
        SpyPaymentGateway spyPayment = new SpyPaymentGateway(stubPayment);

        // 4. Setup Mock Notification
        MockNotificationService mockNotification = new MockNotificationService();
        mockNotification.expectInvocations(1);

        // 5. Setup Dummy Logger
        DummyAuditLogger dummyLogger = new DummyAuditLogger();

        OrderProcessingService service = new OrderProcessingService(fakeRepo, spyPayment, mockNotification, dummyLogger);

        // Execute valid order
        Order order = new Order("ord_555", "usr_100", 250.0);
        boolean success = service.processOrder(order);

        // Verify outcomes
        assert success : "Order processing should succeed for active user";
        mockNotification.verify();
        assert mockNotification.wasCalledWith("alice@example.com", "ord_555") : "Notification must match recipient & order";
        assert spyPayment.getRecordedAmounts().size() == 1 : "Payment spy must record exactly 1 charge";
        assert spyPayment.getRecordedAmounts().get(0) == 250.0 : "Charged amount must match order";

        // Inactive user check: should fail before payment
        Order inactiveOrder = new Order("ord_556", "usr_inactive", 100.0);
        boolean inactiveSuccess = service.processOrder(inactiveOrder);
        assert !inactiveSuccess : "Inactive user order must fail";
        assert spyPayment.getRecordedAmounts().size() == 1 : "Inactive user must NOT trigger payment charge";

        System.out.println("    -> Passed: Dummy, Stub, Fake, Mock, and Spy verified in cohesive domain flow.");
    }

    private static void testDeterministicConcurrencyWithoutSleep() throws Exception {
        System.out.println("  [TEST 2] Deterministic Concurrency Testing with CyclicBarrier (No Sleep)...");

        ConcurrentIdempotencyCache<String, String> cache = new ConcurrentIdempotencyCache<>();
        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<Future<String>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            results.add(executor.submit(() -> {
                try {
                    barrier.await(); // Synchronize all threads at the exact starting gate
                    return cache.computeIfAbsent("payment_id_999", key -> {
                        // Simulated slow computation (deterministic work)
                        long sum = 0;
                        for (int j = 0; j < 50_000; j++) sum += j;
                        return "COMPUTED_RESULT_" + sum;
                    });
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        // Wait deterministically for all threads to complete
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        assert finished : "All concurrent threads must complete within timeout";

        // Verify computation was executed EXACTLY ONCE despite 16 simultaneous threads
        assert cache.getComputationCount() == 1 : "Expected exactly 1 computation, got: " + cache.getComputationCount();

        // Verify all 16 threads received the identical result
        String expected = results.get(0).get();
        for (Future<String> fut : results) {
            assert expected.equals(fut.get()) : "Thread result mismatch: expected " + expected + ", got: " + fut.get();
        }

        executor.shutdown();
        System.out.println("    -> Passed: 16 concurrent threads synchronized via CyclicBarrier; exact-once execution verified.");
    }

    private static void testFlakyTestIsolationAndClockInjection() {
        System.out.println("  [TEST 3] Flaky Test Isolation & Injectable Clock (Time-Freezing Determinism)...");

        // Freeze time at 2026-10-14T12:00:00Z
        Instant frozenNow = Instant.parse("2026-10-14T12:00:00Z");
        Clock fixedClock = Clock.fixed(frozenNow, ZoneOffset.UTC);

        SubscriptionService service = new SubscriptionService(fixedClock);

        // Case A: Registration 13 days ago, 14-day trial -> Active
        Instant regActive = frozenNow.minus(Duration.ofDays(13));
        assert service.isTrialActive(regActive, Duration.ofDays(14)) : "13-day old registration must have active trial";

        // Case B: Registration exactly 14 days and 1 second ago -> Expired
        Instant regExpired = frozenNow.minus(Duration.ofDays(14)).minusSeconds(1);
        assert !service.isTrialActive(regExpired, Duration.ofDays(14)) : "14-day + 1s registration must be expired";

        // Case C: Clock boundary precisely at expiration
        Instant regBoundary = frozenNow.minus(Duration.ofDays(14));
        // At exact expiration instant, isBefore is false
        assert !service.isTrialActive(regBoundary, Duration.ofDays(14)) : "Exact expiration instant is not active";

        System.out.println("    -> Passed: Injectable Clock verified; eliminates non-deterministic timing and timezone flakiness.");
    }

    private static void testMutationTestingAndAssertionQuality() {
        System.out.println("  [TEST 4] Mutation Testing & Assertion Quality (Weak Line Coverage vs High Score)...");

        MutationEngine engine = new MutationEngine();

        // 1. WEAK TEST SUITE: Achieves 100% line coverage on the positive path, but asserts weakly!
        // Weak test only tests cartTotal = 200, asserts discount > 0
        java.util.function.Consumer<DiscountMutant> weakSuite = mutant -> {
            double discount = mutant.calculateDiscount(200.0);
            assert discount > 0 : "Discount must be positive"; // Weak assertion!
        };

        double weakScore = engine.evaluateTestSuite(weakSuite);
        // Weak suite fails to kill Mutant 1 (> to >=) and Mutant 2 (0.10 to 0.05)!
        assert weakScore <= 0.50 : "Weak test suite should kill <= 50% of mutants, got: " + (weakScore * 100) + "%";

        // 2. STRONG TEST SUITE: Tests boundary values and exact expected mathematical outputs
        java.util.function.Consumer<DiscountMutant> strongSuite = mutant -> {
            // Boundary tests:
            // total = 100.0 -> must be exactly 0.0
            double d100 = mutant.calculateDiscount(100.0);
            assert d100 == 0.0 : "100.0 must have zero discount";

            // total = 200.0 -> must be exactly 20.0
            double d200 = mutant.calculateDiscount(200.0);
            assert Math.abs(d200 - 20.0) < 0.0001 : "200.0 must yield exactly 20.0 discount";

            // total = 50.0 -> must be 0.0
            double d50 = mutant.calculateDiscount(50.0);
            assert d50 == 0.0 : "50.0 must yield 0.0 discount";
        };

        double strongScore = engine.evaluateTestSuite(strongSuite);
        assert strongScore == 1.0 : "Strong test suite must kill 100% of mutants, got: " + (strongScore * 100) + "%";

        System.out.println("    -> Passed: Mutation testing demonstrated; weak assertion kills " +
                (int)(weakScore * 100) + "% vs strong suite kills " + (int)(strongScore * 100) + "%.");
    }

    private static void testPropertyBasedInvariantVerification() {
        System.out.println("  [TEST 5] Property-Based Invariant Verification (Generative Fuzzing)...");

        Random random = new Random(42); // Seeded for deterministic repeatability
        int testCases = 500;

        for (int i = 0; i < testCases; i++) {
            // Generate array of random length [0..100] with random integers
            int len = random.nextInt(101);
            int[] arr = new int[len];
            for (int j = 0; j < len; j++) {
                arr[j] = random.nextInt(2001) - 1000; // [-1000, 1000]
            }

            // Capture initial frequency map
            Map<Integer, Integer> initialFreq = SortingPropertyVerifier.frequencyMap(arr);

            // Execute sort
            int[] copy = Arrays.copyOf(arr, arr.length);
            Arrays.sort(copy);

            // Invariant 1: Array must be sorted in ascending order
            assert SortingPropertyVerifier.isSorted(copy) : "Sorted invariant violated for case " + i;

            // Invariant 2: Length must be invariant
            assert copy.length == arr.length : "Length invariant violated for case " + i;

            // Invariant 3: Element conservation (permutation property)
            Map<Integer, Integer> sortedFreq = SortingPropertyVerifier.frequencyMap(copy);
            assert initialFreq.equals(sortedFreq) : "Element conservation invariant violated for case " + i;

            // Invariant 4: Idempotency (sort(sort(x)) == sort(x))
            int[] doubleSorted = Arrays.copyOf(copy, copy.length);
            Arrays.sort(doubleSorted);
            assert Arrays.equals(copy, doubleSorted) : "Idempotency invariant violated for case " + i;
        }

        System.out.println("    -> Passed: 500 generated test cases verified for sort invariant, permutation, and idempotency.");
    }
}
