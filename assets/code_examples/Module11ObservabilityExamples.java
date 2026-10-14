package assets.code_examples;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 11 — Observability & Production Diagnostics Verification Suite.
 *
 * Implements deterministic simulations and verification of core observability primitives in pure Java 21 LTS:
 * 1. W3C Trace Context (traceparent) Parser, Validator, and Injector
 * 2. In-Memory Distributed Tracing Engine (Spans, Hierarchy, Self-Time, Events)
 * 3. Metric Registry: Counters, Gauges, and Histogram with Linear Interpolation Quantiles
 * 4. Structured JSON Log Formatter with MDC Context Injection and PII Masking
 * 5. Google SRE Multi-Window Multi-Burn-Rate Alert Evaluator
 */
public class Module11ObservabilityExamples {

    // =========================================================================
    // 1. W3C TRACE CONTEXT (TRACEPARENT) SPECIFICATION
    // =========================================================================

    public record TraceParent(String version, String traceId, String parentId, String traceFlags) {
        private static final Pattern TRACEPARENT_PATTERN =
                Pattern.compile("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

        public static TraceParent parse(String header) {
            if (header == null) {
                throw new IllegalArgumentException("Traceparent header cannot be null");
            }
            Matcher matcher = TRACEPARENT_PATTERN.matcher(header.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid W3C traceparent format: " + header);
            }
            String version = matcher.group(1);
            String traceId = matcher.group(2);
            String parentId = matcher.group(3);
            String flags = matcher.group(4);

            // W3C spec: version 'ff' is forbidden, all-zeros traceId or parentId is invalid
            if ("ff".equalsIgnoreCase(version)) {
                throw new IllegalArgumentException("Traceparent version 'ff' is forbidden");
            }
            if ("00000000000000000000000000000000".equals(traceId)) {
                throw new IllegalArgumentException("TraceId cannot be all zeros");
            }
            if ("0000000000000000".equals(parentId)) {
                throw new IllegalArgumentException("ParentId cannot be all zeros");
            }

            return new TraceParent(version, traceId, parentId, flags);
        }

        public boolean isSampled() {
            int flagInt = Integer.parseInt(traceFlags, 16);
            return (flagInt & 0x01) == 0x01;
        }

        public String serialize() {
            return String.format("%s-%s-%s-%s", version, traceId, parentId, traceFlags);
        }

        public static TraceParent createNew(boolean sampled) {
            SecureRandom random = new SecureRandom();
            byte[] traceBytes = new byte[16];
            byte[] parentBytes = new byte[8];
            random.nextBytes(traceBytes);
            random.nextBytes(parentBytes);

            StringBuilder traceHex = new StringBuilder();
            for (byte b : traceBytes) traceHex.append(String.format("%02x", b));

            StringBuilder parentHex = new StringBuilder();
            for (byte b : parentBytes) parentHex.append(String.format("%02x", b));

            return new TraceParent("00", traceHex.toString(), parentHex.toString(), sampled ? "01" : "00");
        }
    }

    // =========================================================================
    // 2. IN-MEMORY DISTRIBUTED TRACING ENGINE
    // =========================================================================

    public enum SpanKind { SERVER, CLIENT, PRODUCER, CONSUMER, INTERNAL }
    public enum StatusCode { UNSET, OK, ERROR }

    public static class SpanEvent {
        public final String name;
        public final long timestampNanos;
        public final Map<String, String> attributes;

        public SpanEvent(String name, long timestampNanos, Map<String, String> attributes) {
            this.name = name;
            this.timestampNanos = timestampNanos;
            this.attributes = Map.copyOf(attributes);
        }
    }

    public static class Span {
        public final String traceId;
        public final String spanId;
        public final String parentSpanId;
        public final String name;
        public final SpanKind kind;
        public final long startTimeNanos;
        private long endTimeNanos;
        private StatusCode statusCode = StatusCode.UNSET;
        private String statusDescription = "";
        private final Map<String, String> attributes = new ConcurrentHashMap<>();
        private final List<SpanEvent> events = new CopyOnWriteArrayList<>();
        private final List<Span> children = new CopyOnWriteArrayList<>();

        public Span(String traceId, String spanId, String parentSpanId, String name, SpanKind kind, long startTimeNanos) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.name = name;
            this.kind = kind;
            this.startTimeNanos = startTimeNanos;
        }

        public Span setAttribute(String key, String value) {
            if (key != null && value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        public Span addEvent(String eventName, Map<String, String> eventAttributes) {
            events.add(new SpanEvent(eventName, System.nanoTime(), eventAttributes != null ? eventAttributes : Map.of()));
            return this;
        }

        public Span setStatus(StatusCode code, String description) {
            this.statusCode = code;
            this.statusDescription = description != null ? description : "";
            return this;
        }

        public void end(long endTimeNanos) {
            this.endTimeNanos = endTimeNanos;
        }

        public void addChild(Span child) {
            children.add(child);
        }

        public long getDurationNanos() {
            return endTimeNanos - startTimeNanos;
        }

        public long getDurationMillis() {
            return getDurationNanos() / 1_000_000;
        }

        /**
         * Calculates Self-Time: Total duration minus the duration of direct child spans.
         */
        public long getSelfTimeNanos() {
            long total = getDurationNanos();
            long childTime = 0;
            for (Span child : children) {
                childTime += child.getDurationNanos();
            }
            return Math.max(0, total - childTime);
        }

        public Map<String, String> getAttributes() { return Collections.unmodifiableMap(attributes); }
        public List<SpanEvent> getEvents() { return Collections.unmodifiableList(events); }
        public StatusCode getStatusCode() { return statusCode; }
        public String getStatusDescription() { return statusDescription; }
        public List<Span> getChildren() { return Collections.unmodifiableList(children); }
    }

    // =========================================================================
    // 3. METRIC REGISTRY (COUNTERS, GAUGES, HISTOGRAMS)
    // =========================================================================

    public static class Counter {
        private final AtomicLong count = new AtomicLong(0);

        public void increment() { count.incrementAndGet(); }
        public void add(long delta) {
            if (delta < 0) throw new IllegalArgumentException("Counters can only increase");
            count.addAndGet(delta);
        }
        public long get() { return count.get(); }
    }

    public static class Gauge {
        private final AtomicLong value = new AtomicLong(0);

        public void set(long val) { value.set(val); }
        public long get() { return value.get(); }
    }

    public static class Histogram {
        private final double[] upperBounds; // Bucket boundaries in milliseconds (sorted ascending)
        private final AtomicLong[] bucketCounts;
        private final AtomicLong totalCount = new AtomicLong(0);
        private final DoubleAdder totalSum = new DoubleAdder();

        public Histogram(double[] upperBounds) {
            this.upperBounds = Arrays.copyOf(upperBounds, upperBounds.length);
            Arrays.sort(this.upperBounds);
            this.bucketCounts = new AtomicLong[this.upperBounds.length + 1]; // +1 for +Inf
            for (int i = 0; i < bucketCounts.length; i++) {
                bucketCounts[i] = new AtomicLong(0);
            }
        }

        public void observe(double value) {
            totalSum.add(value);
            totalCount.incrementAndGet();

            for (int i = 0; i < upperBounds.length; i++) {
                if (value <= upperBounds[i]) {
                    bucketCounts[i].incrementAndGet();
                }
            }
            // +Inf bucket includes all observations
            bucketCounts[bucketCounts.length - 1].incrementAndGet();
        }

        public long getCount() { return totalCount.get(); }
        public double getSum() { return totalSum.sum(); }
        public double[] getUpperBounds() { return Arrays.copyOf(upperBounds, upperBounds.length); }
        public long getBucketCount(int index) { return bucketCounts[index].get(); }

        /**
         * Calculates estimated quantile (e.g. 0.50, 0.95, 0.99) via linear interpolation
         * across cumulative histogram buckets, matching the Prometheus histogram_quantile() algorithm.
         */
        public double quantile(double q) {
            if (q <= 0.0 || q > 1.0) throw new IllegalArgumentException("Quantile must be in (0, 1]");
            long n = totalCount.get();
            if (n == 0) return 0.0;

            double rank = q * n;
            double lowerBound = 0.0;
            long prevCount = 0;

            for (int i = 0; i < upperBounds.length; i++) {
                long countAtBucket = bucketCounts[i].get();
                if (countAtBucket >= rank) {
                    double upperBound = upperBounds[i];
                    long countInBucket = countAtBucket - prevCount;
                    if (countInBucket == 0) return upperBound;
                    // Linear interpolation within bucket
                    double fraction = (rank - prevCount) / (double) countInBucket;
                    return lowerBound + fraction * (upperBound - lowerBound);
                }
                lowerBound = upperBounds[i];
                prevCount = countAtBucket;
            }

            // Exceeds highest finite bound; return highest bound
            return upperBounds[upperBounds.length - 1];
        }

        /**
         * Merge another histogram's bucket counts and sum into a new combined histogram.
         * Demonstrates why Histograms can be mathematically aggregated across nodes while Summaries cannot!
         */
        public static Histogram merge(Histogram a, Histogram b) {
            if (!Arrays.equals(a.upperBounds, b.upperBounds)) {
                throw new IllegalArgumentException("Cannot merge histograms with mismatched bucket boundaries");
            }
            Histogram merged = new Histogram(a.upperBounds);
            merged.totalCount.set(a.totalCount.get() + b.totalCount.get());
            merged.totalSum.add(a.totalSum.sum() + b.totalSum.sum());

            for (int i = 0; i < merged.bucketCounts.length; i++) {
                merged.bucketCounts[i].set(a.bucketCounts[i].get() + b.bucketCounts[i].get());
            }
            return merged;
        }
    }

    // =========================================================================
    // 4. STRUCTURED LOGGING WITH MDC & PII MASKING
    // =========================================================================

    public static class MdcContext {
        private static final ThreadLocal<Map<String, String>> contextMap =
                ThreadLocal.withInitial(HashMap::new);

        public static void put(String key, String value) {
            contextMap.get().put(key, value);
        }

        public static String get(String key) {
            return contextMap.get().get(key);
        }

        public static void clear() {
            contextMap.get().clear();
        }

        public static Map<String, String> getCopyOfContextMap() {
            return new HashMap<>(contextMap.get());
        }
    }

    public static class StructuredJsonLogFormatter {
        // PII Patterns: Credit Cards and Authorization Bearer Tokens
        private static final Pattern CREDIT_CARD_PATTERN =
                Pattern.compile("\\b(?:\\d{4}[-\\s]?){3}\\d{4}\\b");
        private static final Pattern BEARER_TOKEN_PATTERN =
                Pattern.compile("Bearer\\s+[A-Za-z0-9\\-\\._~\\+\\/]+=*", Pattern.CASE_INSENSITIVE);

        public static String maskSensitiveData(String input) {
            if (input == null) return null;
            String masked = CREDIT_CARD_PATTERN.matcher(input).replaceAll("****-****-****-****");
            masked = BEARER_TOKEN_PATTERN.matcher(masked).replaceAll("Bearer [REDACTED]");
            return masked;
        }

        public static String format(String level, String message, Map<String, String> attributes) {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"timestamp\":\"").append(Instant.now().toString()).append("\",");
            sb.append("\"level\":\"").append(level).append("\",");
            sb.append("\"message\":\"").append(maskSensitiveData(message)).append("\"");

            // Attach MDC context
            Map<String, String> mdc = MdcContext.getCopyOfContextMap();
            for (Map.Entry<String, String> entry : mdc.entrySet()) {
                sb.append(",\"").append(entry.getKey()).append("\":\"")
                  .append(maskSensitiveData(entry.getValue())).append("\"");
            }

            // Attach explicit attributes
            if (attributes != null) {
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    sb.append(",\"").append(entry.getKey()).append("\":\"")
                      .append(maskSensitiveData(entry.getValue())).append("\"");
                }
            }

            sb.append("}");
            return sb.toString();
        }
    }

    // =========================================================================
    // 5. GOOGLE SRE MULTI-WINDOW MULTI-BURN-RATE ALERT EVALUATOR
    // =========================================================================

    public enum AlertSeverity { NONE, TICKET, PAGE }

    public static class MultiWindowBurnRateEvaluator {
        private final double targetSlo; // e.g. 0.999 (99.9%)
        private final double errorBudgetFraction; // 1.0 - 0.999 = 0.001

        public MultiWindowBurnRateEvaluator(double targetSlo) {
            this.targetSlo = targetSlo;
            this.errorBudgetFraction = 1.0 - targetSlo;
        }

        /**
         * Calculates burn rate given total requests and failed requests over a window.
         * BurnRate = (failedRequests / totalRequests) / errorBudgetFraction
         */
        public double calculateBurnRate(long totalRequests, long failedRequests) {
            if (totalRequests == 0) return 0.0;
            double actualErrorRate = (double) failedRequests / totalRequests;
            return actualErrorRate / errorBudgetFraction;
        }

        /**
         * Evaluates multi-window burn rate alert condition:
         * Fast Burn (Page): Long Window 1h BurnRate >= 14.4 AND Short Window 5m BurnRate >= 14.4
         * Slow Burn (Page): Long Window 6h BurnRate >= 6.0 AND Short Window 30m BurnRate >= 6.0
         * Slow Burn (Ticket): Long Window 3d BurnRate >= 1.0 AND Short Window 6h BurnRate >= 1.0
         */
        public AlertSeverity evaluateAlert(double longWindowBurnRate, double shortWindowBurnRate, double threshold) {
            if (longWindowBurnRate >= threshold && shortWindowBurnRate >= threshold) {
                if (threshold >= 6.0) {
                    return AlertSeverity.PAGE;
                } else if (threshold >= 1.0) {
                    return AlertSeverity.TICKET;
                }
            }
            return AlertSeverity.NONE;
        }
    }

    // =========================================================================
    // VERIFICATION TEST HARNESS (JDK 21 LTS)
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println(" MODULE 11: OBSERVABILITY & PRODUCTION DIAGNOSTICS HARNESS       ");
        System.out.println("=================================================================\n");

        testW3CTraceContext();
        testInMemoryTracingAndSelfTime();
        testHistogramQuantilesAndMerging();
        testStructuredLoggingAndPiiMasking();
        testMultiWindowBurnRateAlerting();

        System.out.println("\n=================================================================");
        System.out.println(" ALL MODULE 11 OBSERVABILITY SUITES PASSED PERFECTLY (5/5)       ");
        System.out.println("=================================================================");
    }

    private static void testW3CTraceContext() {
        System.out.println("  [TEST 1] W3C Traceparent Header Parsing, Validation & Injection...");

        // 1. Valid sampled traceparent
        String validHeader = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        TraceParent tp = TraceParent.parse(validHeader);
        assert "00".equals(tp.version()) : "Version mismatch";
        assert "4bf92f3577b34da6a3ce929d0e0e4736".equals(tp.traceId()) : "TraceId mismatch";
        assert "00f067aa0ba902b7".equals(tp.parentId()) : "ParentId mismatch";
        assert "01".equals(tp.traceFlags()) : "TraceFlags mismatch";
        assert tp.isSampled() : "Should be marked sampled";
        assert validHeader.equals(tp.serialize()) : "Serialization round-trip failed";

        // 2. Invalid headers rejection
        try {
            TraceParent.parse("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"); // version ff
            assert false : "Should have rejected version ff";
        } catch (IllegalArgumentException expected) {}

        try {
            TraceParent.parse("00-00000000000000000000000000000000-00f067aa0ba902b7-01"); // all zeros traceId
            assert false : "Should have rejected all-zero traceId";
        } catch (IllegalArgumentException expected) {}

        // 3. New randomized creation
        TraceParent fresh = TraceParent.createNew(true);
        assert fresh.traceId().length() == 32 : "Generated traceId must be 32 hex chars";
        assert fresh.parentId().length() == 16 : "Generated parentId must be 16 hex chars";
        assert fresh.isSampled() : "Fresh span must be sampled";

        System.out.println("    -> Passed: W3C traceparent parsing, format validation, and serialization verified.");
    }

    private static void testInMemoryTracingAndSelfTime() {
        System.out.println("  [TEST 2] In-Memory Span Hierarchy, Events, and Self-Time Calculation...");

        long t0 = 100_000_000L; // nanoseconds baseline
        Span root = new Span("trace-1", "span-root", null, "POST /orders", SpanKind.SERVER, t0);

        // Child 1: DB Query takes 40ms
        Span dbChild = new Span("trace-1", "span-db", "span-root", "SELECT users", SpanKind.CLIENT, t0 + 10_000_000L);
        dbChild.setAttribute("db.system", "postgresql");
        dbChild.end(t0 + 50_000_000L); // 40ms
        root.addChild(dbChild);

        // Child 2: Kafka Publish takes 20ms
        Span kafkaChild = new Span("trace-1", "span-kafka", "span-root", "orders.published", SpanKind.PRODUCER, t0 + 60_000_000L);
        kafkaChild.setAttribute("messaging.system", "kafka");
        kafkaChild.end(t0 + 80_000_000L); // 20ms
        root.addChild(kafkaChild);

        // Root completes at t0 + 100ms
        root.end(t0 + 100_000_000L); // total duration 100ms
        root.setStatus(StatusCode.OK, "Success");

        assert root.getDurationMillis() == 100 : "Root total duration must be 100ms, got: " + root.getDurationMillis();
        assert dbChild.getDurationMillis() == 40 : "DB duration must be 40ms";
        assert kafkaChild.getDurationMillis() == 20 : "Kafka duration must be 20ms";

        // Self-time of root = 100ms - (40ms + 20ms) = 40ms
        long selfTimeMs = root.getSelfTimeNanos() / 1_000_000L;
        assert selfTimeMs == 40 : "Root self-time must be 40ms, got: " + selfTimeMs;

        System.out.println("    -> Passed: Span hierarchy, attribute storage, and self-time calculation verified.");
    }

    private static void testHistogramQuantilesAndMerging() {
        System.out.println("  [TEST 3] Histogram Cumulative Buckets, Linear Interpolation & Multi-Node Merging...");

        double[] buckets = new double[]{10.0, 25.0, 50.0, 100.0, 250.0, 500.0};
        Histogram nodeA = new Histogram(buckets);
        Histogram nodeB = new Histogram(buckets);

        // Record 100 fast requests on Node A (all <= 20ms)
        for (int i = 1; i <= 100; i++) {
            nodeA.observe(i * 0.2); // 0.2ms to 20ms
        }

        // Record 10 slow requests on Node B (100ms to 400ms)
        for (int i = 1; i <= 10; i++) {
            nodeB.observe(100.0 + i * 30.0);
        }

        // Node A p50 should be ~10ms
        double p50A = nodeA.quantile(0.50);
        assert p50A > 0.0 && p50A < 20.0 : "Node A p50 expected < 20ms, got: " + p50A;

        // Merge Node A and Node B
        Histogram clusterHistogram = Histogram.merge(nodeA, nodeB);
        assert clusterHistogram.getCount() == 110 : "Merged count must be 110, got: " + clusterHistogram.getCount();

        // Calculate Cluster p99
        double clusterP99 = clusterHistogram.quantile(0.99);
        assert clusterP99 > 100.0 : "Cluster p99 must reflect the slow Node B outliers (>100ms), got: " + clusterP99;

        System.out.println("    -> Passed: Histogram cumulative buckets merged accurately; cluster p99 reflects true tail.");
    }

    private static void testStructuredLoggingAndPiiMasking() {
        System.out.println("  [TEST 4] Structured JSON Logging, MDC ThreadLocal & PII Masking...");

        MdcContext.clear();
        MdcContext.put("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736");
        MdcContext.put("user_id", "usr_10492");

        String sensitiveMsg = "User attempted payment with card 4111-2222-3333-4444 using token Bearer eyJhbGciOiJIUzI1NiJ9.test";
        String formattedJson = StructuredJsonLogFormatter.format("ERROR", sensitiveMsg, Map.of("service", "payment-service"));

        // Verify PII is masked
        assert !formattedJson.contains("4111-2222-3333-4444") : "Credit card must be redacted!";
        assert formattedJson.contains("****-****-****-****") : "Credit card must be masked!";
        assert !formattedJson.contains("Bearer eyJhbGciOiJIUzI1NiJ9.test") : "Bearer token must be redacted!";
        assert formattedJson.contains("Bearer [REDACTED]") : "Bearer token mask missing!";

        // Verify MDC fields attached
        assert formattedJson.contains("\"trace_id\":\"4bf92f3577b34da6a3ce929d0e0e4736\"") : "TraceId missing from log!";
        assert formattedJson.contains("\"user_id\":\"usr_10492\"") : "UserId missing from log!";

        MdcContext.clear();
        assert MdcContext.get("trace_id") == null : "MDC clear failed!";

        System.out.println("    -> Passed: Structured JSON log formatted, MDC attached, and sensitive PII masked.");
    }

    private static void testMultiWindowBurnRateAlerting() {
        System.out.println("  [TEST 5] Google SRE Multi-Window Multi-Burn-Rate Alert Evaluation...");

        // 99.9% SLO -> 0.1% (0.001) error budget
        MultiWindowBurnRateEvaluator evaluator = new MultiWindowBurnRateEvaluator(0.999);

        // 1. Normal traffic (0.05% error rate): Burn rate = 0.0005 / 0.001 = 0.5x
        double normalBurn = evaluator.calculateBurnRate(100_000, 50);
        assert Math.abs(normalBurn - 0.5) < 0.001 : "Expected 0.5x burn rate, got: " + normalBurn;
        assert evaluator.evaluateAlert(normalBurn, normalBurn, 14.4) == AlertSeverity.NONE : "Should not alert on 0.5x";

        // 2. Fast Burn Catastrophe: 2.0% error rate (20x error budget)
        // 1h BurnRate = 20x, 5m BurnRate = 20x -> Trips 14.4x Page Alert!
        double fastBurnLong = evaluator.calculateBurnRate(100_000, 2_000); // 2%
        double fastBurnShort = evaluator.calculateBurnRate(10_000, 200);   // 2%
        AlertSeverity pageAlert = evaluator.evaluateAlert(fastBurnLong, fastBurnShort, 14.4);
        assert pageAlert == AlertSeverity.PAGE : "Expected PAGE alert on 20x fast burn, got: " + pageAlert;

        // 3. Transient Spike (Long window elevated to 15x, but short window ceased to 0.5x)
        // Must NOT page because short window proves outage has ceased!
        AlertSeverity transientAlert = evaluator.evaluateAlert(15.0, 0.5, 14.4);
        assert transientAlert == AlertSeverity.NONE : "Should NOT page if short window has recovered!";

        System.out.println("    -> Passed: Multi-window multi-burn-rate evaluator correctly distinguished pages vs transient spikes.");
    }
}
