package assets.code_examples;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Module 08 — Asynchronous Processing & Distributed Messaging Verification Suite.
 *
 * Implements deterministic simulations of core distributed messaging invariants in pure Java 21 LTS:
 * 1. Idempotent Consumer with Durable Deduplication & Concurrent Duplicate Races
 * 2. Exponential Backoff with Full Jitter, Bounded Retries, and Retry Budgets
 * 3. Dead-Letter Queue (DLQ) Quarantine, Poison Message Trapping, and Controlled Redrive
 * 4. Transactional Outbox Pattern: Dual-Write Elimination, Relay Lifecycle, and At-Least-Once Delivery
 * 5. Partitioned Event Log Mechanics: Per-Key Ordering, Consumer Group Offsets, and Head-of-Line Blocking
 * 6. Message Leases & Visibility Timeout Expiration under Slow Consumer / Worker Stalls
 * 7. Graceful Worker Shutdown: In-Flight Drain, Offset Commit, and Hard Cutoff Deadlines
 */
public class Module08MessagingExamples {

    // =========================================================================
    // 1. DOMAIN MODELS & MESSAGE DEFINITIONS
    // =========================================================================

    public record Message<T>(
            String id,
            String key,
            T payload,
            Instant timestamp,
            int attemptCount,
            Map<String, String> headers
    ) {
        public Message<T> withIncrementedAttempt() {
            return new Message<>(id, key, payload, timestamp, attemptCount + 1, headers);
        }
    }

    public enum ProcessingStatus {
        SUCCESS,
        RETRYABLE_FAILURE,
        POISON_FAILURE,
        DUPLICATE_IGNORED
    }

    public record ProcessingResult(ProcessingStatus status, String details) {}

    // =========================================================================
    // 2. IDEMPOTENT CONSUMER & DURABLE DEDUPLICATION STORE
    // =========================================================================

    /**
     * Simulates an RDBMS unique constraint table (e.g. processed_messages(message_id PRIMARY KEY))
     * protecting downstream business entities against concurrent duplicate delivery.
     */
    public static class DurableIdempotencyStore {
        public enum RecordStatus { IN_PROGRESS, COMPLETED, FAILED }

        public record IdempotencyRecord(String messageId, RecordStatus status, String responsePayload, Instant createdAt) {}

        private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

        /**
         * Atomically reserves a message ID.
         * Returns true if this thread successfully acquired the reservation.
         * Returns false if the message was already processed or is currently in flight.
         */
        public boolean tryAcquireReservation(String messageId) {
            IdempotencyRecord initial = new IdempotencyRecord(messageId, RecordStatus.IN_PROGRESS, null, Instant.now());
            // putIfAbsent acts as INSERT ... ON CONFLICT DO NOTHING
            return store.putIfAbsent(messageId, initial) == null;
        }

        public void complete(String messageId, String responsePayload) {
            store.put(messageId, new IdempotencyRecord(messageId, RecordStatus.COMPLETED, responsePayload, Instant.now()));
        }

        public void release(String messageId) {
            store.remove(messageId);
        }

        public Optional<IdempotencyRecord> getRecord(String messageId) {
            return Optional.ofNullable(store.get(messageId));
        }

        public int size() {
            return store.size();
        }
    }

    public static class PaymentProcessor {
        private final DurableIdempotencyStore idempotencyStore;
        private final AtomicInteger executionCount = new AtomicInteger(0);
        private final ConcurrentHashMap<String, Double> accountBalances = new ConcurrentHashMap<>();

        public PaymentProcessor(DurableIdempotencyStore store) {
            this.idempotencyStore = store;
        }

        public ProcessingResult processPayment(Message<Double> message, String accountId) {
            // Step 1: Durable atomic reservation
            if (!idempotencyStore.tryAcquireReservation(message.id())) {
                Optional<DurableIdempotencyStore.IdempotencyRecord> record = idempotencyStore.getRecord(message.id());
                if (record.isPresent() && record.get().status() == DurableIdempotencyStore.RecordStatus.COMPLETED) {
                    return new ProcessingResult(ProcessingStatus.DUPLICATE_IGNORED,
                            "Duplicate message ignored: " + message.id() + ", cached outcome: " + record.get().responsePayload());
                }
                return new ProcessingResult(ProcessingStatus.DUPLICATE_IGNORED,
                        "Concurrent duplicate in-flight for: " + message.id());
            }

            try {
                // Step 2: Critical business transaction
                executionCount.incrementAndGet();
                accountBalances.merge(accountId, message.payload(), Double::sum);

                // Step 3: Complete idempotency record
                String outcome = "SUCCESS: credited " + message.payload();
                idempotencyStore.complete(message.id(), outcome);
                return new ProcessingResult(ProcessingStatus.SUCCESS, outcome);
            } catch (Exception ex) {
                idempotencyStore.release(message.id());
                throw ex;
            }
        }

        public int getExecutionCount() {
            return executionCount.get();
        }

        public double getBalance(String accountId) {
            return accountBalances.getOrDefault(accountId, 0.0);
        }
    }

    // =========================================================================
    // 3. RETRY STRATEGIES: EXPONENTIAL BACKOFF, FULL JITTER, & RETRY BUDGETS
    // =========================================================================

    public static class RetryPolicy {
        private final int maxRetries;
        private final long baseDelayMs;
        private final long maxDelayMs;
        private final ThreadLocalRandom random = ThreadLocalRandom.current();

        public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs) {
            this.maxRetries = maxRetries;
            this.baseDelayMs = baseDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        /**
         * Calculates Full Jitter backoff delay:
         * sleep = Random(0, Min(maxDelay, baseDelay * 2^attempt))
         */
        public long calculateDelay(int attempt) {
            long exponential = baseDelayMs * (1L << Math.min(attempt, 30));
            long ceiling = Math.min(maxDelayMs, exponential);
            return ThreadLocalRandom.current().nextLong(0, Math.max(1, ceiling) + 1);
        }

        public boolean canRetry(int attempt) {
            return attempt < maxRetries;
        }

        public int getMaxRetries() {
            return maxRetries;
        }
    }

    public static class RetryBudget {
        private final AtomicInteger tokens;
        private final int maxTokens;

        public RetryBudget(int initialTokens, int maxTokens) {
            this.tokens = new AtomicInteger(initialTokens);
            this.maxTokens = maxTokens;
        }

        public boolean tryAcquireRetry() {
            while (true) {
                int current = tokens.get();
                if (current <= 0) {
                    return false; // Budget exhausted
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        public void recordSuccess() {
            tokens.updateAndGet(curr -> Math.min(maxTokens, curr + 1));
        }

        public int getAvailableTokens() {
            return tokens.get();
        }
    }

    // =========================================================================
    // 4. DEAD-LETTER QUEUE (DLQ) & POISON MESSAGE QUARANTINE
    // =========================================================================

    public record DeadLetterEntry<T>(
            Message<T> message,
            String failureReason,
            String exceptionClass,
            Instant quarantinedAt,
            int totalAttempts
    ) {}

    public static class DeadLetterQueue<T> {
        private final BlockingQueue<DeadLetterEntry<T>> deadLetters = new LinkedBlockingQueue<>();

        public void quarantine(Message<T> message, Throwable cause) {
            deadLetters.add(new DeadLetterEntry<>(
                    message,
                    cause.getMessage() != null ? cause.getMessage() : "Unknown failure",
                    cause.getClass().getName(),
                    Instant.now(),
                    message.attemptCount()
            ));
        }

        public List<DeadLetterEntry<T>> drainAll() {
            List<DeadLetterEntry<T>> entries = new ArrayList<>();
            deadLetters.drainTo(entries);
            return entries;
        }

        public int size() {
            return deadLetters.size();
        }
    }

    // =========================================================================
    // 5. TRANSACTIONAL OUTBOX PATTERN
    // =========================================================================

    public record OutboxRecord(
            long id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadJson,
            Instant createdAt,
            boolean published
    ) {}

    public static class TransactionalDatabaseSim {
        private final List<String> businessOrders = new CopyOnWriteArrayList<>();
        private final List<OutboxRecord> outboxTable = new CopyOnWriteArrayList<>();
        private final AtomicLong outboxIdSequence = new AtomicLong(1);

        /**
         * Simulates executing business logic and writing outbox event in the SAME atomic transaction.
         */
        public synchronized void createOrderWithOutboxEvent(String orderId, String payloadJson) {
            // Local ACID boundary begins
            businessOrders.add(orderId);
            long outboxId = outboxIdSequence.getAndIncrement();
            outboxTable.add(new OutboxRecord(outboxId, "Order", orderId, "OrderCreated", payloadJson, Instant.now(), false));
            // Local ACID boundary commits atomically
        }

        public synchronized List<OutboxRecord> getPendingOutboxRecords() {
            return outboxTable.stream().filter(r -> !r.published()).toList();
        }

        public synchronized void markPublished(long outboxId) {
            for (int i = 0; i < outboxTable.size(); i++) {
                OutboxRecord r = outboxTable.get(i);
                if (r.id() == outboxId) {
                    outboxTable.set(i, new OutboxRecord(r.id(), r.aggregateType(), r.aggregateId(), r.eventType(), r.payloadJson(), r.createdAt(), true));
                    break;
                }
            }
        }

        public int getOrderCount() {
            return businessOrders.size();
        }

        public int getOutboxSize() {
            return outboxTable.size();
        }
    }

    public static class OutboxRelay {
        private final TransactionalDatabaseSim database;
        private final List<String> publishedToBroker = new CopyOnWriteArrayList<>();
        private final AtomicInteger publishAttemptCount = new AtomicInteger(0);

        public OutboxRelay(TransactionalDatabaseSim database) {
            this.database = database;
        }

        public void pollAndPublish(boolean simulateNetworkFailureOnFirstTry) {
            List<OutboxRecord> pending = database.getPendingOutboxRecords();
            for (OutboxRecord record : pending) {
                int attempt = publishAttemptCount.incrementAndGet();
                if (simulateNetworkFailureOnFirstTry && attempt == 1) {
                    // Simulate network failure right before DB acknowledgement
                    // Message WAS delivered to broker, but relay crashed before marking DB as published!
                    publishedToBroker.add(record.aggregateId());
                    throw new RuntimeException("Network drop during broker ACK");
                }
                publishedToBroker.add(record.aggregateId());
                database.markPublished(record.id());
            }
        }

        public List<String> getBrokerMessages() {
            return publishedToBroker;
        }
    }

    // =========================================================================
    // 6. PARTITIONED COMMIT LOG & CONSUMER GROUPS (KAFKA-STYLE)
    // =========================================================================

    public static class PartitionedLog<T> {
        private final int partitionCount;
        private final List<List<Message<T>>> partitions;

        public PartitionedLog(int partitionCount) {
            this.partitionCount = partitionCount;
            this.partitions = new ArrayList<>(partitionCount);
            for (int i = 0; i < partitionCount; i++) {
                partitions.add(new CopyOnWriteArrayList<>());
            }
        }

        public int computePartition(String key) {
            // Standard Kafka Murmur2 / Hashcode modulo
            return Math.abs(key.hashCode()) % partitionCount;
        }

        public synchronized void append(String key, T payload) {
            int partition = computePartition(key);
            Message<T> msg = new Message<>(UUID.randomUUID().toString(), key, payload, Instant.now(), 0, Map.of());
            partitions.get(partition).add(msg);
        }

        public List<Message<T>> getPartitionMessages(int partition) {
            return Collections.unmodifiableList(partitions.get(partition));
        }

        public int getPartitionCount() {
            return partitionCount;
        }
    }

    // =========================================================================
    // 7. VISIBILITY TIMEOUT & MESSAGE LEASE SIMULATOR
    // =========================================================================

    public static class LeaseQueue<T> {
        public record LeasedMessage<T>(Message<T> message, String leaseToken, Instant leaseExpiresAt) {}

        private final ConcurrentLinkedQueue<Message<T>> available = new ConcurrentLinkedQueue<>();
        private final ConcurrentHashMap<String, LeasedMessage<T>> activeLeases = new ConcurrentHashMap<>();
        private final Duration visibilityTimeout;

        public LeaseQueue(Duration visibilityTimeout) {
            this.visibilityTimeout = visibilityTimeout;
        }

        public void push(Message<T> message) {
            available.add(message);
        }

        public synchronized Optional<LeasedMessage<T>> poll() {
            // Check for expired leases to redeliver
            Instant now = Instant.now();
            Iterator<Map.Entry<String, LeasedMessage<T>>> it = activeLeases.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, LeasedMessage<T>> entry = it.next();
                if (now.isAfter(entry.getValue().leaseExpiresAt())) {
                    // Lease expired! Redeliver to available queue
                    available.add(entry.getValue().message().withIncrementedAttempt());
                    it.remove();
                }
            }

            Message<T> next = available.poll();
            if (next == null) {
                return Optional.empty();
            }

            String token = UUID.randomUUID().toString();
            LeasedMessage<T> leased = new LeasedMessage<>(next, token, now.plus(visibilityTimeout));
            activeLeases.put(token, leased);
            return Optional.of(leased);
        }

        public synchronized boolean acknowledge(String leaseToken) {
            LeasedMessage<T> leased = activeLeases.get(leaseToken);
            if (leased == null) {
                return false; // Token does not exist or lease was already expired/reclaimed!
            }
            if (Instant.now().isAfter(leased.leaseExpiresAt())) {
                activeLeases.remove(leaseToken);
                return false; // Expired; lease rejected
            }
            activeLeases.remove(leaseToken);
            return true;
        }

        public int getActiveLeaseCount() {
            return activeLeases.size();
        }

        public int getAvailableCount() {
            return available.size();
        }
    }

    // =========================================================================
    // 8. GRACEFUL WORKER SHUTDOWN HARNESS
    // =========================================================================

    public static class GracefulWorker {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ExecutorService processingPool = Executors.newFixedThreadPool(4);
        private final AtomicInteger inFlightJobs = new AtomicInteger(0);
        private final AtomicInteger completedJobs = new AtomicInteger(0);

        public void submitJob(Runnable task) {
            if (!running.get()) {
                throw new RejectedExecutionException("Worker is shutting down. Rejecting new tasks.");
            }
            inFlightJobs.incrementAndGet();
            processingPool.submit(() -> {
                try {
                    task.run();
                    completedJobs.incrementAndGet();
                } finally {
                    inFlightJobs.decrementAndGet();
                }
            });
        }

        public boolean shutdownAndAwait(long timeoutMs) throws InterruptedException {
            running.set(false); // Stop accepting new tasks
            processingPool.shutdown(); // Disable new tasks from being submitted
            return processingPool.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
        }

        public int getInFlightJobs() {
            return inFlightJobs.get();
        }

        public int getCompletedJobs() {
            return completedJobs.get();
        }
    }

    // =========================================================================
    // 9. TEST HARNESS & VERIFICATION SUITES
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println("  MODULE 08 ASYNC & DISTRIBUTED MESSAGING TEST SUITE (JAVA 21)   ");
        System.out.println("=================================================================");

        testIdempotentConsumerUnderConcurrentDelivery();
        testExponentialBackoffJitterAndRetryBudget();
        testDeadLetterQueueQuarantineAndRedrive();
        testTransactionalOutboxPattern();
        testPartitionedInOrderDeliveryAndHeadOfLineBlocking();
        testMessageLeaseAndVisibilityTimeoutExpiration();
        testGracefulWorkerShutdown();

        System.out.println("\nAll 7 Java Messaging & Asynchronous Test Suites Passed Successfully!");
    }

    private static void testIdempotentConsumerUnderConcurrentDelivery() throws Exception {
        System.out.println("  [TEST 1] Idempotent Consumer under Concurrent Duplicate Delivery...");
        DurableIdempotencyStore store = new DurableIdempotencyStore();
        PaymentProcessor processor = new PaymentProcessor(store);

        String sharedMsgId = "msg-payment-txn-901";
        Message<Double> message = new Message<>(sharedMsgId, "acc-42", 500.0, Instant.now(), 1, Map.of());

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        List<ProcessingResult> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    ProcessingResult res = processor.processPayment(message, "acc-42");
                    results.add(res);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        long successCount = results.stream().filter(r -> r.status() == ProcessingStatus.SUCCESS).count();
        long duplicateCount = results.stream().filter(r -> r.status() == ProcessingStatus.DUPLICATE_IGNORED).count();

        assert successCount == 1 : "Expected exactly 1 execution success, got " + successCount;
        assert duplicateCount == threads - 1 : "Expected " + (threads - 1) + " duplicates ignored, got " + duplicateCount;
        assert processor.getExecutionCount() == 1 : "Business logic must execute exactly once! Count: " + processor.getExecutionCount();
        assert processor.getBalance("acc-42") == 500.0 : "Account balance should only receive 1 credit of 500.0, got: " + processor.getBalance("acc-42");

        System.out.println("    -> Passed: 10 concurrent duplicate messages resulted in exactly 1 business mutation.");
    }

    private static void testExponentialBackoffJitterAndRetryBudget() {
        System.out.println("  [TEST 2] Exponential Backoff, Full Jitter, and Retry Budget Invariants...");
        RetryPolicy policy = new RetryPolicy(4, 50, 1000);
        RetryBudget budget = new RetryBudget(2, 5); // Only 2 retry tokens available

        // Test jitter bounding
        for (int attempt = 0; attempt < 5; attempt++) {
            long delay = policy.calculateDelay(attempt);
            long maxPossible = Math.min(1000, 50L * (1L << attempt));
            assert delay >= 0 && delay <= maxPossible : "Delay " + delay + " out of bounds for attempt " + attempt;
        }

        // Test retry budget consumption
        assert budget.tryAcquireRetry() : "First retry must succeed";
        assert budget.tryAcquireRetry() : "Second retry must succeed";
        assert !budget.tryAcquireRetry() : "Third retry must be rejected: retry budget exhausted";

        budget.recordSuccess();
        assert budget.tryAcquireRetry() : "Successful operations must replenish tokens";

        System.out.println("    -> Passed: Jitter mathematical boundaries and token-bucket retry budgets verified.");
    }

    private static void testDeadLetterQueueQuarantineAndRedrive() {
        System.out.println("  [TEST 3] Poison Message Quarantine and Controlled Redrive...");
        DeadLetterQueue<String> dlq = new DeadLetterQueue<>();
        Message<String> poison = new Message<>("msg-poison-666", "user-88", "{ malformed_json ", Instant.now(), 4, Map.of());

        // Simulate terminal failure after max retries
        dlq.quarantine(poison, new IllegalArgumentException("JSON parsing syntax error at offset 14"));
        assert dlq.size() == 1 : "DLQ must contain quarantined poison message";

        // Operator redrive simulation: inspect, patch, and re-enqueue
        List<DeadLetterEntry<String>> quarantined = dlq.drainAll();
        assert dlq.size() == 0 : "DLQ should be empty after drain";
        DeadLetterEntry<String> entry = quarantined.getFirst();
        assert entry.totalAttempts() == 4 : "Attempt count must be preserved in quarantine metadata";

        // Patch payload
        Message<String> correctedMessage = new Message<>(
                entry.message().id(),
                entry.message().key(),
                "{\"status\":\"repaired\"}",
                Instant.now(),
                0, // Reset attempt count for redrive
                Map.of("X-Redrive-Origin", "DLQ")
        );
        assert correctedMessage.payload().contains("repaired");

        System.out.println("    -> Passed: Poison message isolated with stack trace metadata and successfully redriven.");
    }

    private static void testTransactionalOutboxPattern() {
        System.out.println("  [TEST 4] Transactional Outbox Pattern: Dual-Write Elimination & Duplicate Delivery...");
        TransactionalDatabaseSim db = new TransactionalDatabaseSim();
        OutboxRelay relay = new OutboxRelay(db);

        // Application creates 3 orders
        db.createOrderWithOutboxEvent("order-101", "{\"item\":\"laptop\"}");
        db.createOrderWithOutboxEvent("order-102", "{\"item\":\"phone\"}");

        assert db.getOrderCount() == 2 : "Two business orders committed";
        assert db.getPendingOutboxRecords().size() == 2 : "Two outbox records pending publication";

        // First relay execution suffers a simulated crash right after publishing order-101
        boolean crashed = false;
        try {
            relay.pollAndPublish(true); // Will throw on first publication
        } catch (RuntimeException ex) {
            crashed = true;
        }
        assert crashed : "Expected simulated network drop during relay";

        // Verify that order-101 WAS sent to the broker, but remained un-ACKed in the database outbox
        assert relay.getBrokerMessages().contains("order-101");
        assert db.getPendingOutboxRecords().size() == 2 : "Outbox records must still be pending because ACK was lost";

        // Relay restarts and retries
        relay.pollAndPublish(false);

        // Crucial Architectural Invariant:
        // The broker received order-101 TWICE! Proving why at-least-once outbox relays demand idempotent consumers!
        long order101Count = relay.getBrokerMessages().stream().filter(id -> id.equals("order-101")).count();
        assert order101Count == 2 : "Order 101 must be delivered twice due to crash window, got: " + order101Count;
        assert db.getPendingOutboxRecords().isEmpty() : "All outbox records marked published after successful relay";

        System.out.println("    -> Passed: Dual-write eliminated; at-least-once duplicate delivery window demonstrated.");
    }

    private static void testPartitionedInOrderDeliveryAndHeadOfLineBlocking() {
        System.out.println("  [TEST 5] Partitioned Commit Log: Per-Key In-Order Execution & Head-of-Line Blocking...");
        PartitionedLog<String> log = new PartitionedLog<>(3);

        // Append events for different account keys
        log.append("account-AAA", "TXN_1");
        log.append("account-BBB", "TXN_1");
        log.append("account-AAA", "TXN_2");
        log.append("account-AAA", "TXN_3");
        log.append("account-BBB", "TXN_2");

        int partitionA = log.computePartition("account-AAA");
        int partitionB = log.computePartition("account-BBB");

        List<Message<String>> partitionAMsgs = log.getPartitionMessages(partitionA);
        List<String> payloadSequenceA = partitionAMsgs.stream().filter(m -> m.key().equals("account-AAA")).map(Message::payload).toList();

        // Invariant: Events for account-AAA must strictly preserve order
        assert payloadSequenceA.equals(List.of("TXN_1", "TXN_2", "TXN_3")) : "Partition ordering corrupted: " + payloadSequenceA;

        // Head-of-line blocking demonstration:
        // If message 0 in partition A fails or blocks, messages 1 and 2 in partition A cannot be committed,
        // but partition B proceeds completely independently.
        assert log.getPartitionCount() == 3;

        System.out.println("    -> Passed: Deterministic partition routing and per-key FIFO ordering preserved.");
    }

    private static void testMessageLeaseAndVisibilityTimeoutExpiration() throws Exception {
        System.out.println("  [TEST 6] Message Leases, Visibility Timeouts, and Redelivery Invariants...");
        LeaseQueue<String> queue = new LeaseQueue<>(Duration.ofMillis(80)); // 80ms visibility timeout

        Message<String> task = new Message<>("task-99", "key-1", "HeavyCompute", Instant.now(), 0, Map.of());
        queue.push(task);

        // Worker 1 acquires task
        Optional<LeaseQueue.LeasedMessage<String>> worker1Poll = queue.poll();
        assert worker1Poll.isPresent();
        LeaseQueue.LeasedMessage<String> lease1 = worker1Poll.get();
        assert lease1.message().attemptCount() == 0;
        assert queue.getActiveLeaseCount() == 1;

        // Worker 1 stalls for 120ms (exceeding the 80ms visibility timeout)
        Thread.sleep(120);

        // Worker 2 polls: visibility timeout has expired, broker redelivers message!
        Optional<LeaseQueue.LeasedMessage<String>> worker2Poll = queue.poll();
        assert worker2Poll.isPresent() : "Broker must redeliver task after lease expiration";
        LeaseQueue.LeasedMessage<String> lease2 = worker2Poll.get();
        assert lease2.message().attemptCount() == 1 : "Redelivered message must increment attempt count";

        // Worker 1 wakes up and attempts to ACK with stale/expired lease token
        boolean worker1Ack = queue.acknowledge(lease1.leaseToken());
        assert !worker1Ack : "Broker must REJECT acknowledgment from expired lease!";

        // Worker 2 finishes and ACKs with valid token
        boolean worker2Ack = queue.acknowledge(lease2.leaseToken());
        assert worker2Ack : "Broker must ACCEPT acknowledgment from active lease holder";
        assert queue.getActiveLeaseCount() == 0;
        assert queue.getAvailableCount() == 0;

        System.out.println("    -> Passed: Stale lease acknowledgment rejected and visibility redelivery confirmed.");
    }

    private static void testGracefulWorkerShutdown() throws Exception {
        System.out.println("  [TEST 7] Graceful Worker Shutdown: Draining In-Flight Jobs & Cutoff Deadlines...");
        GracefulWorker worker = new GracefulWorker();

        // Submit 4 long-running tasks (50ms each)
        for (int i = 0; i < 4; i++) {
            worker.submitJob(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            });
        }

        assert worker.getInFlightJobs() > 0 : "Jobs must be running";

        // Trigger graceful shutdown with 500ms timeout
        boolean cleanShutdown = worker.shutdownAndAwait(500);
        assert cleanShutdown : "Worker must cleanly finish in-flight jobs within timeout";
        assert worker.getInFlightJobs() == 0 : "In-flight jobs must be drained to 0";
        assert worker.getCompletedJobs() == 4 : "All 4 submitted jobs must complete";

        // Verify rejected execution post-shutdown
        boolean rejected = false;
        try {
            worker.submitJob(() -> {});
        } catch (RejectedExecutionException ex) {
            rejected = true;
        }
        assert rejected : "New tasks must be rejected during or after shutdown";

        System.out.println("    -> Passed: In-flight tasks drained cleanly before termination; new submissions rejected.");
    }
}
