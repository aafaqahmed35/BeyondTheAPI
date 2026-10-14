package assets.code_examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Module 13 — Linux, Containers, Deployment & Infrastructure Verification Suite.
 *
 * Implements deterministic simulations and verification of core systems and infrastructure concepts in Java 21 LTS:
 * 1. Process Execution & Subprocess Lifecycle Management (Exit Codes, Standard Streams)
 * 2. Linux cgroups v2 Memory & CPU Limit Simulation (Container Memory Headroom Calculations)
 * 3. Graceful Shutdown Lifecycle & Cooperative SIGTERM Signal Traps with Deadlines
 * 4. Kubernetes Probe State Machine (Distinguishing Startup, Liveness, and Readiness Semantics)
 * 5. Horizontal Pod Autoscaling (HPA) Capacity & Sizing Mathematics
 */
public class Module13InfraExamples {

    // =========================================================================
    // 1. PROCESS LIFECYCLE & SUBPROCESS EXECUTION
    // =========================================================================

    public record ProcessExecutionResult(int exitCode, String stdout, String stderr) {}

    public static class SubprocessRunner {
        public static ProcessExecutionResult runCommand(List<String> command, long timeoutMs) throws Exception {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            CompletableFuture<Void> stdoutFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });

            CompletableFuture<Void> stderrFuture = CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TimeoutException("Subprocess timed out after " + timeoutMs + "ms");
            }

            CompletableFuture.allOf(stdoutFuture, stderrFuture).get(1, TimeUnit.SECONDS);
            return new ProcessExecutionResult(process.exitValue(), stdout.toString().trim(), stderr.toString().trim());
        }
    }

    // =========================================================================
    // 2. LINUX CGROUPS V2 & CONTAINER MEMORY SIZING MODEL
    // =========================================================================

    public record ContainerResourceSpec(long memoryLimitBytes, double cpuCores) {}

    public static class CgroupMemoryCalculator {
        // Container OOMKilled risk formula:
        // Memory Limit > JVM Max Heap (-Xmx) + Metaspace + Direct Memory + Thread Stacks + OS Overhead
        public static boolean isSizingSafe(long containerLimitBytes,
                                          long maxHeapBytes,
                                          long metaspaceBytes,
                                          int threadCount,
                                          long stackPerThreadBytes,
                                          long directMemoryBytes,
                                          long nativeOsHeadroomBytes) {
            long totalEstimatedDemand = maxHeapBytes
                    + metaspaceBytes
                    + directMemoryBytes
                    + ((long) threadCount * stackPerThreadBytes)
                    + nativeOsHeadroomBytes;

            return totalEstimatedDemand <= containerLimitBytes;
        }

        public static double calculateCpuQuotaPeriod(double desiredCores, long periodMicros) {
            // Linux cgroups v2: cpu.max = "$QUOTA $PERIOD"
            // Quota = Cores * Period
            return desiredCores * periodMicros;
        }
    }

    // =========================================================================
    // 3. GRACEFUL SHUTDOWN & SIGTERM SIGNAL HANDLING
    // =========================================================================

    public static class GracefulServer {
        private final AtomicBoolean acceptingTraffic = new AtomicBoolean(true);
        private final AtomicInteger inFlightRequests = new AtomicInteger(0);
        private final CountDownLatch drainedLatch = new CountDownLatch(1);

        public boolean handleRequest(Runnable work) {
            if (!acceptingTraffic.get()) {
                return false; // Fast-fail new traffic once SIGTERM received
            }
            inFlightRequests.incrementAndGet();
            try {
                work.run();
                return true;
            } finally {
                int remaining = inFlightRequests.decrementAndGet();
                if (!acceptingTraffic.get() && remaining == 0) {
                    drainedLatch.countDown();
                }
            }
        }

        public boolean initiateShutdown(long drainTimeoutMs) throws InterruptedException {
            // 1. Mark server as refusing new connections (K8s endpoint removal window)
            acceptingTraffic.set(false);

            if (inFlightRequests.get() == 0) {
                return true;
            }

            // 2. Wait for in-flight requests to drain within grace period
            return drainedLatch.await(drainTimeoutMs, TimeUnit.MILLISECONDS);
        }

        public boolean isAcceptingTraffic() {
            return acceptingTraffic.get();
        }

        public int getInFlightRequests() {
            return inFlightRequests.get();
        }
    }

    // =========================================================================
    // 4. KUBERNETES PROBE STATE MACHINE
    // =========================================================================

    public enum ProbeStatus { HEALTHY, UNHEALTHY }

    public static class KubernetesProbeManager {
        private volatile boolean applicationInitialized = false;
        private volatile boolean databaseConnected = false;
        private volatile boolean deadlockDetected = false;

        public void setApplicationInitialized(boolean val) { this.applicationInitialized = val; }
        public void setDatabaseConnected(boolean val) { this.databaseConnected = val; }
        public void setDeadlockDetected(boolean val) { this.deadlockDetected = val; }

        // Startup Probe: Protects slow-starting containers; while failing, liveness & readiness are muted
        public ProbeStatus evaluateStartupProbe() {
            return applicationInitialized ? ProbeStatus.HEALTHY : ProbeStatus.UNHEALTHY;
        }

        // Liveness Probe: Checks if container is deadlocked or in an unrecoverable crash loop.
        // CRITICAL RULE: NEVER check external dependencies in liveness probe!
        public ProbeStatus evaluateLivenessProbe() {
            if (!applicationInitialized) {
                return ProbeStatus.UNHEALTHY;
            }
            return deadlockDetected ? ProbeStatus.UNHEALTHY : ProbeStatus.HEALTHY;
        }

        // Readiness Probe: Checks if pod is able to serve user traffic.
        // Validates local readiness AND connection to necessary storage/downstream systems.
        public ProbeStatus evaluateReadinessProbe() {
            if (!applicationInitialized || deadlockDetected) {
                return ProbeStatus.UNHEALTHY;
            }
            return databaseConnected ? ProbeStatus.HEALTHY : ProbeStatus.UNHEALTHY;
        }
    }

    // =========================================================================
    // 5. HORIZONTAL POD AUTOSCALER (HPA) MATHEMATICS
    // =========================================================================

    public static class HpaCalculator {
        // Kubernetes HPA formula:
        // desiredReplicas = ceil[ currentReplicas * ( currentMetricValue / targetMetricValue ) ]
        public static int calculateDesiredReplicas(int currentReplicas,
                                                   double currentMetricValue,
                                                   double targetMetricValue,
                                                   int minReplicas,
                                                   int maxReplicas) {
            if (targetMetricValue <= 0.0) {
                throw new IllegalArgumentException("Target metric must be positive");
            }
            double ratio = currentMetricValue / targetMetricValue;
            int desired = (int) Math.ceil(currentReplicas * ratio);

            if (desired < minReplicas) return minReplicas;
            return Math.min(desired, maxReplicas);
        }
    }

    // =========================================================================
    // MAIN VERIFICATION HARNESS
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================================");
        System.out.println(" MODULE 13: LINUX, CONTAINERS & INFRASTRUCTURE VERIFICATION      ");
        System.out.println("=================================================================\n");

        testSubprocessExecution();
        testCgroupMemoryAndCpuSizing();
        testGracefulShutdownLifecycle();
        testKubernetesProbeSemantics();
        testHpaCapacityCalculations();

        System.out.println("\n=================================================================");
        System.out.println(" ALL MODULE 13 INFRASTRUCTURE SUITES PASSED PERFECTLY (5/5)      ");
        System.out.println("=================================================================");
    }

    private static void testSubprocessExecution() throws Exception {
        System.out.println("  [TEST 1] Subprocess Execution, Standard Streams & Exit Codes...");

        // Execute local echo command
        ProcessExecutionResult result = SubprocessRunner.runCommand(List.of("echo", "HELLO_LINUX_CONTAINER"), 2000);
        assert result.exitCode() == 0 : "Expected exit code 0, got: " + result.exitCode();
        assert result.stdout().contains("HELLO_LINUX_CONTAINER") : "Unexpected stdout: " + result.stdout();

        System.out.println("    -> Passed: Subprocess successfully executed with deterministic stream capture.");
    }

    private static void testCgroupMemoryAndCpuSizing() {
        System.out.println("  [TEST 2] Linux cgroups v2 Memory & CPU Sizing Calculations...");

        // 1 GB container limit (1024 MB)
        long limitBytes = 1024L * 1024L * 1024L;

        // Configuration A: Safe JVM sizing (Heap 512MB, Metaspace 128MB, 50 threads * 1MB, Direct 64MB, OS 100MB = 854MB < 1024MB)
        boolean safeConfig = CgroupMemoryCalculator.isSizingSafe(
                limitBytes,
                512L * 1024L * 1024L,
                128L * 1024L * 1024L,
                50,
                1024L * 1024L,
                64L * 1024L * 1024L,
                100L * 1024L * 1024L
        );
        assert safeConfig : "512MB heap on 1GB container must be safe";

        // Configuration B: Hazardous JVM sizing (Heap 900MB on 1GB container -> Guaranteed OOMKilled by Linux kernel)
        boolean unsafeConfig = CgroupMemoryCalculator.isSizingSafe(
                limitBytes,
                900L * 1024L * 1024L,
                128L * 1024L * 1024L,
                100,
                1024L * 1024L,
                64L * 1024L * 1024L,
                100L * 1024L * 1024L
        );
        assert !unsafeConfig : "900MB heap on 1GB container must trigger unsafe sizing detection";

        // CPU Quota check: 2.5 cores on 100,000 micros period -> 250,000 micros quota
        double quota = CgroupMemoryCalculator.calculateCpuQuotaPeriod(2.5, 100_000);
        assert quota == 250_000.0 : "Expected 250000 quota, got: " + quota;

        System.out.println("    -> Passed: Cgroups memory safety bounds and CPU quota/period verified.");
    }

    private static void testGracefulShutdownLifecycle() throws Exception {
        System.out.println("  [TEST 3] Graceful Shutdown & In-Flight Request Draining...");

        GracefulServer server = new GracefulServer();
        ExecutorService requestPool = Executors.newFixedThreadPool(4);

        // Submit 2 in-flight requests that take 50ms each
        CountDownLatch startLatch = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            requestPool.submit(() -> {
                server.handleRequest(() -> {
                    startLatch.countDown();
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                });
            });
        }

        startLatch.await(); // Ensure both requests are inside handleRequest
        assert server.getInFlightRequests() == 2 : "Expected 2 in-flight requests";

        // Trigger shutdown in background thread
        CompletableFuture<Boolean> shutdownFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return server.initiateShutdown(2000);
            } catch (Exception e) {
                return false;
            }
        });

        // Verify server now immediately rejects new requests
        boolean newRequestAccepted = server.handleRequest(() -> {});
        assert !newRequestAccepted : "Server must reject new traffic during shutdown drain phase";

        // Wait for drain to complete
        boolean cleanDrain = shutdownFuture.get(3, TimeUnit.SECONDS);
        assert cleanDrain : "Server must cleanly drain existing requests within timeout";
        assert server.getInFlightRequests() == 0 : "All requests must be drained";

        requestPool.shutdown();
        System.out.println("    -> Passed: Graceful shutdown drained active workers and fast-failed new ingress.");
    }

    private static void testKubernetesProbeSemantics() {
        System.out.println("  [TEST 4] Kubernetes Probe State Machine (Startup, Liveness, Readiness)...");

        KubernetesProbeManager probes = new KubernetesProbeManager();

        // 1. Initial State: Container starting up
        assert probes.evaluateStartupProbe() == ProbeStatus.UNHEALTHY : "Startup probe should fail initially";
        assert probes.evaluateLivenessProbe() == ProbeStatus.UNHEALTHY : "Liveness should not pass before startup";
        assert probes.evaluateReadinessProbe() == ProbeStatus.UNHEALTHY : "Readiness should fail initially";

        // 2. Application initialized, but Database connection not yet established
        probes.setApplicationInitialized(true);
        assert probes.evaluateStartupProbe() == ProbeStatus.HEALTHY : "Startup probe should pass once initialized";
        assert probes.evaluateLivenessProbe() == ProbeStatus.HEALTHY : "Liveness should pass (process is alive)";
        assert probes.evaluateReadinessProbe() == ProbeStatus.UNHEALTHY : "Readiness must fail if DB is not connected";

        // 3. Database connected -> Pod fully ready to receive traffic
        probes.setDatabaseConnected(true);
        assert probes.evaluateReadinessProbe() == ProbeStatus.HEALTHY : "Readiness should pass with DB connected";

        // 4. Downstream Database temporarily drops:
        // Liveness must REMAIN HEALTHY (do NOT restart pod!), Readiness must FAIL (stop routing traffic!)
        probes.setDatabaseConnected(false);
        assert probes.evaluateLivenessProbe() == ProbeStatus.HEALTHY : "CRITICAL: Liveness must not fail on external DB outage!";
        assert probes.evaluateReadinessProbe() == ProbeStatus.UNHEALTHY : "Readiness must fail and pull pod from Service endpoints";

        // 5. Internal Deadlock detected:
        // Liveness must FAIL to trigger kubelet container restart
        probes.setDeadlockDetected(true);
        assert probes.evaluateLivenessProbe() == ProbeStatus.UNHEALTHY : "Liveness must fail on internal deadlock";

        System.out.println("    -> Passed: Liveness/Readiness semantic separation strictly verified.");
    }

    private static void testHpaCapacityCalculations() {
        System.out.println("  [TEST 5] Kubernetes HPA (Horizontal Pod Autoscaler) Mathematics...");

        // Current: 2 replicas at 80% CPU. Target: 50% CPU.
        // Desired = ceil(2 * (80 / 50)) = ceil(3.2) = 4 replicas
        int desiredReplicas = HpaCalculator.calculateDesiredReplicas(2, 80.0, 50.0, 1, 10);
        assert desiredReplicas == 4 : "Expected 4 replicas, got: " + desiredReplicas;

        // Current: 8 replicas at 20% CPU. Target: 50% CPU.
        // Desired = ceil(8 * (20 / 50)) = ceil(3.2) = 4 replicas (scale down)
        int scaleDown = HpaCalculator.calculateDesiredReplicas(8, 20.0, 50.0, 2, 10);
        assert scaleDown == 4 : "Expected 4 replicas, got: " + scaleDown;

        // Bounded by maxReplicas
        int cappedMax = HpaCalculator.calculateDesiredReplicas(5, 200.0, 50.0, 1, 8);
        assert cappedMax == 8 : "Expected capped at maxReplicas=8, got: " + cappedMax;

        // Bounded by minReplicas
        int cappedMin = HpaCalculator.calculateDesiredReplicas(2, 5.0, 50.0, 3, 10);
        assert cappedMin == 3 : "Expected capped at minReplicas=3, got: " + cappedMin;

        System.out.println("    -> Passed: Kubernetes HPA scaling math and boundary constraints verified.");
    }
}
