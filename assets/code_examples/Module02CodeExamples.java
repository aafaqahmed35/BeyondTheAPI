package assets.code_examples;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Module 02 Code Examples & Algorithmic Verification Harness.
 *
 * Demonstrates idiomatic, production-grade Java 21 implementations of core
 * Operating Systems and Computer Networking concepts taught in Module 2:
 *
 * 1. Race Conditions & Atomic Counter Synchronization (OS Concurrency).
 * 2. Deadlock Prevention via Global Canonical Lock Ordering (Coffman Conditions).
 * 3. TCP Stream Message Framing: Length-Prefix Encoder/Decoder (Network Framing).
 *
 * Each implementation contains explicit preconditions, invariants,
 * boundary condition checks, and assertions verifying correctness.
 */
public class Module02CodeExamples {

    // =========================================================================
    // 1. CONCURRENCY: Race Condition Simulation vs. Atomic Synchronization
    // =========================================================================

    /**
     * Demonstrates how non-atomic read-modify-write operations (count++)
     * result in lost updates due to thread interleaving, and how AtomicInteger
     * guarantees atomicity via hardware-level Compare-And-Swap (CAS).
     */
    public static class ConcurrencyDemo {

        private int unsafeCount = 0;
        private final AtomicInteger safeCount = new AtomicInteger(0);

        public void incrementUnsafe() {
            // Three discrete CPU operations: LOAD, ADD 1, STORE.
            // Interleaved threads cause lost updates.
            unsafeCount++;
        }

        public void incrementSafe() {
            safeCount.incrementAndGet();
        }

        public int getUnsafeCount() {
            return unsafeCount;
        }

        public int getSafeCount() {
            return safeCount.get();
        }

        public void reset() {
            unsafeCount = 0;
            safeCount.set(0);
        }
    }

    // =========================================================================
    // 2. DEADLOCK PREVENTION: Global Resource Ordering
    // =========================================================================

    /**
     * Represents a financial account protected by an explicit ReentrantLock.
     */
    public static class BankAccount {
        private final long id;
        private long balance;
        public final ReentrantLock lock = new ReentrantLock();

        public BankAccount(long id, long initialBalance) {
            this.id = id;
            this.balance = initialBalance;
        }

        public long getId() {
            return id;
        }

        public long getBalance() {
            return balance;
        }

        public void deposit(long amount) {
            balance += amount;
        }

        public void withdraw(long amount) {
            balance -= amount;
        }
    }

    /**
     * Executes money transfers between accounts.
     * Prevents the Circular Wait Coffman condition by enforcing a strict,
     * deterministic global lock acquisition order based on Account ID.
     */
    public static class TransferService {

        /**
         * Transfers amount from source to target safely without deadlock risk.
         * Precondition: source and target must not be null and must have distinct IDs.
         * Invariant: Total system balance is strictly preserved.
         */
        public static boolean transfer(BankAccount from, BankAccount to, long amount) {
            if (from == null || to == null) {
                throw new IllegalArgumentException("Accounts must not be null");
            }
            if (from.getId() == to.getId()) {
                throw new IllegalArgumentException("Cannot transfer to the same account");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("Transfer amount must be positive");
            }

            // Enforce Global Lock Ordering to eliminate Circular Wait
            BankAccount firstLock = from.getId() < to.getId() ? from : to;
            BankAccount secondLock = from.getId() < to.getId() ? to : from;

            firstLock.lock.lock();
            try {
                secondLock.lock.lock();
                try {
                    if (from.getBalance() < amount) {
                        return false; // Insufficient funds
                    }
                    from.withdraw(amount);
                    to.deposit(amount);
                    return true;
                } finally {
                    secondLock.lock.unlock();
                }
            } finally {
                firstLock.lock.unlock();
            }
        }
    }

    // =========================================================================
    // 3. NETWORKING: TCP Length-Prefix Message Framing
    // =========================================================================

    /**
     * Solves the TCP stream fragmentation / coalescing problem where TCP does not
     * preserve application message boundaries.
     *
     * Frame Format:
     * +------------------------+---------------------------------------+
     * | Length (4 bytes, int)  | Payload (UTF-8 bytes of length Length)|
     * +------------------------+---------------------------------------+
     */
    public static class LengthPrefixFraming {

        public static final int MAX_FRAME_SIZE = 16 * 1024 * 1024; // 16 MB boundary guard

        /**
         * Encodes an application payload into a length-prefixed byte array.
         */
        public static byte[] encodeFrame(String message) {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            if (payload.length > MAX_FRAME_SIZE) {
                throw new IllegalArgumentException("Payload exceeds maximum permitted frame size");
            }
            ByteBuffer buffer = ByteBuffer.allocate(4 + payload.length);
            buffer.putInt(payload.length); // 4-byte big-endian header
            buffer.put(payload);
            return buffer.array();
        }

        /**
         * Simulates streaming decode from a contiguous byte buffer that may contain
         * partial frames, multiple coalesced frames, or exact frames.
         */
        public static List<String> decodeFrames(ByteArrayInputStream stream) throws IOException {
            List<String> messages = new ArrayList<>();
            DataInputStream dis = new DataInputStream(stream);

            while (stream.available() >= 4) {
                stream.mark(MAX_FRAME_SIZE + 4);
                int length = dis.readInt();
                if (length < 0 || length > MAX_FRAME_SIZE) {
                    throw new IOException("Corrupted frame: invalid length header " + length);
                }

                if (stream.available() < length) {
                    // Incomplete frame in buffer; rewind stream and await further network packets
                    stream.reset();
                    break;
                }

                byte[] payload = new byte[length];
                dis.readFully(payload);
                messages.add(new String(payload, StandardCharsets.UTF_8));
            }

            return messages;
        }
    }

    // =========================================================================
    // 4. TEST HARNESS & VERIFICATION SUITES
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Module 02 OS & Networking Verification Test Suite...");

        testAtomicVsUnsafeConcurrency();
        testDeadlockFreeAccountTransfers();
        testTcpLengthPrefixFramingCoalescing();
        testTcpLengthPrefixFramingFragmentation();
        testLoopbackSocketEcho();

        System.out.println("\nAll 5 OS & Networking Test Suites Passed Successfully!");
    }

    private static void testAtomicVsUnsafeConcurrency() throws InterruptedException {
        ConcurrencyDemo demo = new ConcurrencyDemo();
        int threadCount = 10;
        int incrementsPerThread = 5_000;
        int expectedTotal = threadCount * incrementsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        demo.incrementUnsafe();
                        demo.incrementSafe();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release threads simultaneously to maximize contention
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assert finished : "Concurrency test timed out";
        assert demo.getSafeCount() == expectedTotal : 
            "Safe atomic counter must exactly equal " + expectedTotal + ", got " + demo.getSafeCount();

        // Note: unsafeCount may intermittently match or be less than expectedTotal depending on OS thread scheduling;
        // safeCount is mathematically guaranteed to equal expectedTotal.
        System.out.println("  [PASS] Concurrency: AtomicInteger preserved atomicity under contention (" + 
                           demo.getSafeCount() + " == " + expectedTotal + ")");
    }

    private static void testDeadlockFreeAccountTransfers() throws InterruptedException {
        BankAccount acc1 = new BankAccount(101L, 10_000);
        BankAccount acc2 = new BankAccount(202L, 10_000);
        long initialTotal = acc1.getBalance() + acc2.getBalance();

        int transferCount = 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch latch = new CountDownLatch(transferCount);

        // Intentionally create bidirectional transfer contention:
        // Thread A transfers from acc1 to acc2, Thread B transfers from acc2 to acc1
        for (int i = 0; i < transferCount; i++) {
            final boolean forward = (i % 2 == 0);
            executor.submit(() -> {
                try {
                    if (forward) {
                        TransferService.transfer(acc1, acc2, 5);
                    } else {
                        TransferService.transfer(acc2, acc1, 5);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completedWithoutDeadlock = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assert completedWithoutDeadlock : "Deadlock detected: transfer threads failed to terminate within timeout";
        assert (acc1.getBalance() + acc2.getBalance()) == initialTotal : 
            "Total balance invariant violated: expected " + initialTotal + ", got " + (acc1.getBalance() + acc2.getBalance());

        System.out.println("  [PASS] Concurrency: Deadlock avoided via global lock ordering across " + 
                           transferCount + " concurrent transactions");
    }

    private static void testTcpLengthPrefixFramingCoalescing() throws IOException {
        // Test Case 1: Multiple frames coalesced into a single network packet buffer
        ByteArrayOutputStream networkWire = new ByteArrayOutputStream();
        String msg1 = "HTTP/1.1 200 OK";
        String msg2 = "Content-Type: application/json";
        String msg3 = "{\"status\":\"healthy\",\"orders\":42}";

        networkWire.write(LengthPrefixFraming.encodeFrame(msg1));
        networkWire.write(LengthPrefixFraming.encodeFrame(msg2));
        networkWire.write(LengthPrefixFraming.encodeFrame(msg3));

        ByteArrayInputStream receiverBuffer = new ByteArrayInputStream(networkWire.toByteArray());
        List<String> decoded = LengthPrefixFraming.decodeFrames(receiverBuffer);

        assert decoded.size() == 3 : "Expected 3 decoded frames, got " + decoded.size();
        assert decoded.get(0).equals(msg1) : "Frame 1 mismatch: " + decoded.get(0);
        assert decoded.get(1).equals(msg2) : "Frame 2 mismatch: " + decoded.get(1);
        assert decoded.get(2).equals(msg3) : "Frame 3 mismatch: " + decoded.get(2);

        System.out.println("  [PASS] Networking: TCP stream packet coalescing resolved via length-prefix framing");
    }

    private static void testTcpLengthPrefixFramingFragmentation() throws IOException {
        // Test Case 2: Packet fragmentation across network chunks
        String fullMessage = "OrderId=987654321;Customer=Alice;Total=150.00";
        byte[] completeFrame = LengthPrefixFraming.encodeFrame(fullMessage);

        // Sender transmits first 10 bytes only (fragmented packet)
        byte[] fragment1 = Arrays.copyOfRange(completeFrame, 0, 10);
        ByteArrayInputStream receiverBuffer1 = new ByteArrayInputStream(fragment1);
        List<String> partialDecoded = LengthPrefixFraming.decodeFrames(receiverBuffer1);
        assert partialDecoded.isEmpty() : "Must not decode frame until all bytes have arrived";

        // Sender transmits remaining bytes
        byte[] fragment2 = Arrays.copyOfRange(completeFrame, 10, completeFrame.length);
        ByteArrayOutputStream reassembled = new ByteArrayOutputStream();
        reassembled.write(fragment1);
        reassembled.write(fragment2);

        ByteArrayInputStream receiverBuffer2 = new ByteArrayInputStream(reassembled.toByteArray());
        List<String> completeDecoded = LengthPrefixFraming.decodeFrames(receiverBuffer2);

        assert completeDecoded.size() == 1 : "Expected 1 complete frame upon reassembly";
        assert completeDecoded.get(0).equals(fullMessage) : "Reassembled message mismatch";

        System.out.println("  [PASS] Networking: TCP stream packet fragmentation handled without boundary distortion");
    }

    private static void testLoopbackSocketEcho() throws Exception {
        // Verify real OS TCP socket creation, binding, loopback 3-way handshake, and teardown
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int port = serverSocket.getLocalPort();

            CountDownLatch serverReady = new CountDownLatch(1);
            CompletableFuture<String> serverFuture = CompletableFuture.supplyAsync(() -> {
                serverReady.countDown();
                try (Socket clientConn = serverSocket.accept();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(clientConn.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientConn.getOutputStream(), StandardCharsets.UTF_8))) {
                    String line = reader.readLine();
                    writer.write("ACK:" + line + "\n");
                    writer.flush();
                    return line;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            serverReady.await(2, TimeUnit.SECONDS);

            try (Socket clientSocket = new Socket("127.0.0.1", port);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8))) {
                
                String payload = "PING_REQUEST_HEALTH";
                writer.write(payload + "\n");
                writer.flush();

                String response = reader.readLine();
                assert ("ACK:" + payload).equals(response) : "Socket echo response mismatch: " + response;
            }

            String serverReceived = serverFuture.get(2, TimeUnit.SECONDS);
            assert "PING_REQUEST_HEALTH".equals(serverReceived) : "Server received payload mismatch";

            System.out.println("  [PASS] Networking: Real POSIX loopback TCP socket connection handshake & echo passed");
        }
    }
}
