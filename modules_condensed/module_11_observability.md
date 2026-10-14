# Module 11: Observability and Production Diagnostics

## 1. Control Theory Foundations & The Telemetry Triad

- **Observability:** In modern software engineering (derived from Rudolf Kalman's control theory), observability is a measure of how well internal system states can be inferred solely from knowledge of its external outputs:
  $$\text{State } x(t) \text{ can be reconstructed from external outputs } y(t)$$
- **Monitoring vs. Observability:**
  - *Monitoring:* Asks "Is the system broken?" Tracks known failure modes with predefined dashboards and threshold alerts.
  - *Observability:* Asks "Why is the system behaving this way?" Enables engineers to interrogate novel, emergent failure modes without deploying new code.

### 1.1 The Telemetry Triad + Profiles
1. **Metrics:** Aggregatable numerical time-series measuring system physics (rate, errors, duration, memory). Low storage cost; ideal for alerting.
2. **Logs:** Discrete, timestamped structured event records providing rich local context (stack traces, parameters).
3. **Distributed Traces:** Tracks the lifecycle of a request as it traverses distributed network boundaries, capturing latency and causal DAG graphs.
4. **Continuous Profiling:** Low-overhead statistical CPU and memory allocation profiling (Async-profiler, eBPF) mapping execution directly to line-level code.

---

## 2. Structured Logging & Context Propagation

Plain unstructured string logging (`log.info("User logged in: " + user)`) is unparseable at scale. Production systems mandate **Structured JSON Logging**:
```json
{
  "timestamp": "2026-10-15T04:12:00.123Z",
  "level": "ERROR",
  "service": "order-service",
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7",
  "user_id": "usr_987",
  "tenant_id": "ten_42",
  "message": "Payment processing failed",
  "error": {
    "class": "PaymentGatewayException",
    "message": "Connection timeout after 3000ms"
  }
}
```

### 2.1 Mapped Diagnostic Context (MDC) Mechanics
In Java, correlation context (`trace_id`, `tenant_id`) is attached to logging threads via **MDC** (`ThreadLocal` storage):
```java
// Production MDC Lifecycle Pattern
public void handleRequest(HttpRequest request) {
    try {
        MDC.put("trace_id", request.getHeader("X-Trace-Id"));
        MDC.put("tenant_id", request.getHeader("X-Tenant-Id"));
        service.process(request);
    } finally {
        MDC.clear(); // MANDATORY INVARIANT: Prevents thread pool context leakage!
    }
}
```

---

## 3. Metrics Fundamentals & The Cardinality Trap

### 3.1 The 4 Core Metric Types
- **Counter:** Monotonically increasing cumulative value (e.g., `http_requests_total`). Resets to zero only on process restart. Always query using `rate()` or `irate()`.
- **Gauge:** Point-in-time snapshot of an arbitrary variable that can fluctuate up and down (e.g., `jvm_memory_used_bytes`, `active_db_connections`).
- **Histogram:** Samples observations into configurable boundary buckets and computes counts/sums. Crucial: Histograms are **aggregatable across instances** via PromQL (`histogram_quantile()`).
- **Summary:** Computes client-side quantiles ($p50, p95, p99$). **Mathematical Invariant:** Summaries **cannot be aggregated across multiple servers**! The average of the 99th percentiles is mathematically meaningless. Use Histograms for distributed metrics.

### 3.2 Cardinality Explosion Mathematics
Metric Cardinality ($C$) is the Cartesian product of all unique label values:
$$C = \prod_{i=1}^{k} |V_i| = |V_{\text{method}}| \times |V_{\text{status}}| \times |V_{\text{endpoint}}|$$
- If an engineer adds `user_id` ($1,000,000$ values) or `order_id` as a Prometheus metric label, cardinality explodes into millions of distinct time-series.
- Time-Series Databases (TSDB) crash with `OutOfMemoryError` due to inverted index exhaustion. Never use high-cardinality values as metric labels!

### 3.3 RED vs. USE Methods
- **RED Method (Request-Driven Microservices):**
  - **R**ate: Requests processed per second.
  - **E**rrors: Failed requests per second.
  - **D**uration: Latency distribution of requests.
- **USE Method (Resource-Driven Infrastructure - CPU, Memory, Disk):**
  - **U**tilization: Percentage of time resource was busy.
  - **S**aturation: Queue depth of extra work waiting for resource.
  - **E**rrors: Hardware or driver error events.

---

## 4. Distributed Tracing & OpenTelemetry (OTel)

A distributed trace models the execution graph of a request as a Directed Acyclic Graph (DAG) of **Spans**:
- **Span:** A single unit of work (e.g., executing an SQL query, an RPC call). Contains: `TraceId`, `SpanId`, `ParentSpanId`, start/end timestamps, attributes, and events.

### 4.1 W3C TraceContext Propagation Standard
Trace state traverses network boundaries via standard HTTP headers:
```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
              ^  ^                                ^                ^
        Version  Trace ID (16 bytes hex)          Span ID (8 bytes)Flags (01 = sampled)
```

### 4.2 Head-Based vs. Tail-Based Sampling
- **Head-Based Sampling:** The root ingress service decides whether to sample the trace at initiation (e.g., sample $1\%$ of requests). Low overhead; problem: drops $99\%$ of rare production errors and tail latency spikes!
- **Tail-Based Sampling:** All spans are forwarded to an intermediate collector (OpenTelemetry Collector) and buffered in memory until the entire trace completes. The collector evaluates sampling rules: **retain $100\%$ of traces containing HTTP 5xx errors or latency $> 2000$ms**, while sampling fast 200 OK traces at $0.1\%$.

---

## 5. Site Reliability Engineering: SLIs, SLOs & Alert Design

- **Service Level Indicator (SLI):** A quantifiable metric of service performance:
  $$\text{SLI} = \frac{\text{Good Events}}{\text{Total Valid Events}} \times 100\%$$
- **Service Level Objective (SLO):** The target reliability goal agreed with the business:
  $$\text{SLO} = 99.9\% \text{ of HTTP requests must return 200 in } < 200\text{ms over 30 rolling days}$$
- **Error Budget:** Permissible unreliability: $\text{Error Budget} = 100\% - \text{SLO} = 0.1\%$.

### 5.1 Multi-Window Multi-Burn-Rate Alerting
Alerting on simple static thresholds causes alert fatigue. Google SRE best practice uses **Burn Rate Alerting**:
- A burn rate of $1\times$ exhausts $100\%$ of the error budget over exactly 30 days.
- **Critical Page (14.4x Burn Rate):** Consumes $2\%$ of error budget in 1 hour. Immediate wake-up page.
- **Ticket Alert (6x Burn Rate):** Consumes $5\%$ of error budget in 6 hours. Generates next-day ticket.

---

## 6. Failure Modes & Operational Triage

| Failure Pattern | Signal / Symptom | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **Cardinality Meltdown** | Prometheus TSDB crashes with OOM; scrape timeouts. | High-cardinality labels (`user_id`, UUID) injected into metrics. | Drop high-cardinality labels at collector; move IDs to structured logs. |
| **Context Leakage** | Logs report User A's `user_id` on requests executed by User B. | `MDC.clear()` omitted in thread pool worker `finally` block. | Enforce `try { ... } finally { MDC.clear(); }` in all request filters. |
| **Missing Trace Propagation** | Traces appear as broken, isolated single-span islands. | Downstream HTTP client omitted forwarding `traceparent` headers. | Use auto-instrumented HTTP clients (OTel OkHttp, Spring WebClient). |
| **Telemetry Storm** | Logging pipeline saturates network and disk during incident. | Debug logs enabled globally during massive cascade failure. | Enforce dynamic log level filtering and client-side log rate limiters. |
---

## 7. High-Bar Observability Interview Scenarios

### Scenario 1: The Fallacy of Averages in Production Latency
- **Interview Question:** "Your service dashboard shows an average response time of 45ms, yet customer support reports enterprise customers experiencing frequent 30-second timeouts. Explain how the metric hid the failure."
- **Deep Technical Answer:**
  1. **Arithmetic Mean Fallacy:** The mean $\mu = rac{1}{N} \sum X_i$ is distorted by massive volumes of cheap, fast requests (e.g., health checks returning in 1ms).
  2. If 99 requests take 1ms and 1 request takes 30,000ms, the average is:
     $$	ext{Average} = rac{99 	imes 1 + 30000}{100} = rac{30099}{100} pprox 301	ext{ms}$$
     A dashboard reporting 300ms hides the reality that customer requests are completely timing out.
  3. **Remedy:** Never evaluate production SLAs using mean latency. Enforce **Percentile Histograms ($p95, p99, p99.9$)** and plot cumulative distribution functions (CDF).

### Scenario 2: Emergency Triage of Metric Cardinality Explosion
- **Interview Question:** "Prometheus crashes with Out of Memory 10 minutes after a production release. The TSDB head block is growing exponentially. How do you triage and fix it?"
- **Deep Technical Answer:**
  1. **Root Cause:** A developer added high-cardinality values (e.g., `user_id`, `order_id`, or raw HTTP request path with unparameterized IDs `/users/12345`) as a Prometheus metric label.
  2. **Emergency Mitigation:**
     - Update OpenTelemetry Collector / Prometheus scraping configuration to apply **Metric Relabeling**: drop the offending label before ingestion.
     - Restart Prometheus TSDB with memory limits.
  3. **Permanent Fix:** Standardize metric semantic conventions via CI linting rules. High-cardinality attributes belong exclusively in structured logs and distributed tracing span attributes, never in metrics time-series.

### Scenario 3: Context Propagation Across Asynchronous Kafka Queues
- **Interview Question:** "How do you maintain continuous distributed tracing spans when a synchronous HTTP request publishes an event to Kafka, processed 10 minutes later by a worker?"
- **Deep Technical Answer:**
  1. **Context Injection:** When the producer writes to Kafka, the OpenTelemetry SDK injects the active trace context into the **Kafka Record Headers**:
     ```java
     record.headers().add("traceparent", traceParentHeaderBytes);
     ```
  2. **Context Extraction:** When the consumer polls the record, it extracts the `traceparent` header bytes and initializes a new Span with `ParentContext` linked to the producer's trace ID.
  3. **Span Link vs. Parent:** If the worker processes events in batches, creating child spans can distort trace duration. OTel **Span Links** connect the worker span to the producer span without implying synchronous execution.
---

## 8. Deep-Dive PromQL Master Reference & OpenTelemetry Pipelines

### 8.1 Essential PromQL Query Patterns
- **Request Rate per Second:**
  ```promql
  sum(rate(http_requests_total{status=~"5.."}[5m])) 
  / 
  sum(rate(http_requests_total[5m])) * 100
  ```
  Computes the 5-minute rolling percentage of HTTP 5xx errors across all service instances.
- **Aggregated 99th Percentile Latency (from Histogram):**
  ```promql
  histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket[5m])) by (le))
  ```
  *Rule:* Always compute `rate()` over buckets before summing and passing to `histogram_quantile()`.
- **CPU Saturation (USE Method):**
  ```promql
  100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
  ```

### 8.2 OpenTelemetry Collector Pipeline (`otel-collector-config.yaml`)
```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  memory_limiter:
    check_interval: 1s
    limit_percentage: 75
    spike_limit_percentage: 20
  batch:
    send_batch_size: 1000
    timeout: 10s
  tail_sampling:
    decision_wait: 10s
    expected_trajectories: 100000
    policies:
      - name: drop-health-checks
        type: string_attribute
        string_attribute: { key: http.route, values: ["/healthz", "/metrics"], enabled_regex_matching: false, invert_match: true }
      - name: keep-errors
        type: status_code
        status_code: { status_codes: [ERROR] }
      - name: keep-slow-traces
        type: latency
        latency: { threshold_ms: 2000 }

exporters:
  prometheus:
    endpoint: 0.0.0.0:8889
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, tail_sampling, batch]
      exporters: [otlp/tempo]
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [prometheus]
```
