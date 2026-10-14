# Module 10: System Design and Production Architecture

## 1. The 4-Stage System Design Interview Framework

Senior system design interviews evaluate engineering judgment, requirements scoping, trade-off evaluation, and failure recovery under ambiguity:

```
+---------------------------------------------------------------------------+
| Stage 1: Clarify Scope, Scale & Functional / Non-Functional Requirements  |
|          (Read/Write ratio, QPS, latency SLAs, availability, durability)  |
+---------------------------------------------------------------------------+
                                     |
                                     v
+---------------------------------------------------------------------------+
| Stage 2: High-Level Architecture & Core Data Contracts                    |
|          (API endpoints, data schemas, ingress routing, major components) |
+---------------------------------------------------------------------------+
                                     |
                                     v
+---------------------------------------------------------------------------+
| Stage 3: Deep-Dive Component Engineering & Bottleneck Resolution          |
|          (Database partitioning, indexing, caching tiers, concurrency)    |
+---------------------------------------------------------------------------+
                                     |
                                     v
+---------------------------------------------------------------------------+
| Stage 4: Resilience, Fault Domains & Operational Triage                   |
|          (Circuit breakers, failover, blast radius, monitoring, recovery) |
+---------------------------------------------------------------------------+
```

---

## 2. Back-of-the-Envelope Estimation Formulas

1. **Seconds in a Day:** $86,400 \approx 10^5$ seconds ($86,400 \approx 8.64 \times 10^4$).
2. **Queries Per Second (QPS):**
   $$\text{Average QPS} = \frac{\text{Daily Active Users (DAU)} \times \text{Requests per User}}{86,400}$$
   $$\text{Peak QPS} = \text{Average QPS} \times 2 \text{ (or } 3\text{)}$$
3. **Storage Over 5 Years:**
   $$\text{Storage} = \text{Daily Writes} \times \text{Row Size (Bytes)} \times 365 \times 5$$
4. **Memory Caching Tier (Pareto 80/20 Rule):**
   $$\text{Cache RAM} = (\text{Daily Read Volume in Bytes}) \times 0.20$$
5. **Availability Nines Table:**

| Availability % | Downtime per Year | Downtime per Month | Architectural Redundancy Required |
| :--- | :---: | :---: | :--- |
| **99.0% (Two Nines)** | 3.65 days | 7.3 hours | Single instance, manual backup restore |
| **99.9% (Three Nines)** | 8.76 hours | 43.8 minutes | Multi-AZ deployment, automated health checks |
| **99.99% (Four Nines)** | 52.6 minutes | 4.38 minutes | Multi-AZ active-passive failover, automated DNS cutover |
| **99.999% (Five Nines)**| 5.26 minutes | 26.3 seconds | Multi-Region active-active, zero single point of failure |

---

## 3. Data Tier Selection & Storage Decision Matrix

| Database Class | Archetype Engines | Optimal Workload Characteristics | Trade-offs & Limitations |
| :--- | :--- | :--- | :--- |
| **Relational (RDBMS)** | PostgreSQL, MySQL | Strong ACID, complex multi-table joins, relational schemas. | Vertical scale ceiling; cross-shard joins are slow and complex. |
| **Key-Value** | Redis, DynamoDB | Point lookups by key ($< 2$ms), session tokens, caching. | No rich secondary indexing or multi-key cross-table querying. |
| **Document** | MongoDB, Couchbase | Semi-structured JSON, polymorphic catalogs, rapid schema churn. | Weak cross-collection joins; potential write skew under scale. |
| **Wide-Column** | Cassandra, ScyllaDB | Massive append-only writes, time-series events, multi-datacenter. | No ad-hoc SQL joins; query patterns must dictate partition keys. |
| **Columnar (OLAP)** | ClickHouse, BigQuery | Analytical aggregations over billions of rows (`SUM`, `AVG`). | Horrendous point write latency; not suited for OLTP transactions. |

---

## 4. Horizontal Database Partitioning & Consistent Hashing

### 4.1 Sharding Strategies
1. **Range-Based Sharding:** Shard based on key range (e.g., User IDs 1–10000 on Shard 1). *Risk:* Extreme hotspots on sequential IDs.
2. **Hash-Based Sharding:** $\text{Shard} = \text{Hash}(\text{Key}) \pmod N$. Distributes load evenly. *Limitation:* Resharding when $N$ changes requires migrating nearly all data.
3. **Directory-Based Sharding:** A central lookup service maps keys to shard IDs. Flexible; lookup service becomes a single point of failure.

### 4.2 Consistent Hashing with Virtual Nodes
Consistent Hashing maps both servers and data keys to a circular ring ($0 \text{ to } 2^{32}-1$):
- A key is stored on the first server encountered moving clockwise along the ring.
- **Node Addition / Removal:** When a server is added or removed, **only $K/N$ keys** must be migrated (where $K$ is total keys, $N$ is total servers).
- **Virtual Nodes:** To prevent non-uniform load distribution (hotspots), each physical server is mapped to 100–300 **Virtual Nodes** distributed randomly across the ring:

```
                      Consistent Hash Ring (0 to 2^32 - 1)
                                   [0]
                              Node A (vnode 1)
                           *                   *
                      *                             *
              Node C (vnode 2)                Node B (vnode 1)
             *                                                *
      Key 1 ----------> encounters Node B (stored here!)       *
            *                                                  *
             *                                                *
              Node B (vnode 2)                Node A (vnode 2)
                      *                             *
                           *                   *
                              Node C (vnode 1)
```

---

## 5. Resilience Patterns & Fault Isolation

### 5.1 Circuit Breakers
A Circuit Breaker prevents cascading system failures by failing fast when downstream services are degraded:
- **CLOSED:** Normal state. Calls pass through. Failures are tracked. If failure rate exceeds threshold (e.g., $50\%$ over 10 seconds), transitions to **OPEN**.
- **OPEN:** Calls fail immediately with fallback/exception without attempting network I/O. Prevents thread pool exhaustion. After a sleep window (e.g., 30s), transitions to **HALF-OPEN**.
- **HALF-OPEN:** Permits a trial batch of requests to downstream. If successful, resets to **CLOSED**; if failed, returns to **OPEN**.

### 5.2 Bulkheads & Load Shedding
- **Bulkheads:** Isolates resources (thread pools, connection pools) per downstream dependency. A failure in the Recommendation service cannot consume all threads and crash the Checkout service.
- **Load Shedding:** When server CPU or queue depth exceeds safe thresholds, immediately drop low-priority traffic with HTTP 429/503 to preserve core business availability.

---

## 6. End-to-End Architectural Case Studies

### 6.1 Distributed URL Shortener (TinyURL)
- **Scale:** 100M new URLs/month, 100:1 read-to-write ratio ($10\text{B reads/month} \implies 3,800\text{ Read QPS}$).
- **Token Generation:** 7-character Base62 string (`[a-zA-Z0-9]`). $62^7 \approx 3.52 \text{ Trillion}$ unique tokens.
- **Architecture:** Pre-generated range token service (Zookeeper assigns ranges of $1,000,000$ sequential integers to worker nodes). Workers convert integers to Base62 locally with zero inter-service network contention. Read tier uses Redis Cache-Aside in front of partitioned PostgreSQL.

### 6.2 High-Throughput Notification Service
- **Scale:** 500M notifications/day across Email, SMS, Push.
- **Architecture:** Priority Queues in Kafka (`high-priority` for OTP login codes; `low-priority` for marketing). Rate limiters per user (maximum 3 notifications/hour). Outbound workers use third-party provider failovers (Twilio $\to$ AWS SNS). Idempotent deduplication keys prevent duplicate customer sends.

### 6.3 Flux Real-Time Collaborative Workspace
- **Concurrency Problem:** Concurrent edits by multiple distributed users on the identical document.
- **Architecture:** WebSocket connection gateway tier backed by Redis Pub/Sub backplane. Employs **Conflict-Free Replicated Data Types (CRDTs)**:
  - *Operation-based CRDTs (RGA):* Commutative edit operations merge deterministically without a central lock coordinator.
  - Snapshots persisted asynchronously to object storage (S3) via Kafka.

### 6.4 ArgusIQ Real-Time Fraud & Anomaly Detection
- **Scale:** 100,000 payment transactions/second. Sub-50ms fraud scoring SLA.
- **Architecture:** Stateful stream processing via **Apache Flink**:
  - Sliding event-time windows (e.g., "count transactions from card in last 5 minutes").
  - RocksDB incremental state backend.
  - Online feature store (Redis) feeds precomputed features to embedded ML inference model (ONNX Runtime). Flagged transactions trigger circuit breakers held in payment gateway.

---

## 7. The 6 Inviolable Production Architecture Rules

1. **No Single Point of Failure (SPOF):** Every tier (load balancer, app server, database, cache) must be redundant across independent physical failure domains (Availability Zones).
2. **Enforce Timeouts Everywhere:** Every network call (HTTP, RPC, database query, Redis command) must have an explicit connection and read timeout. Unbounded timeouts guarantee thread pool collapse.
3. **Make All State Mutations Idempotent:** Network calls fail ambiguously; clients will retry. APIs must accept idempotency keys and enforce unique constraints.
4. **Never Use the Database as a Message Queue at Scale:** Relational polling (`SELECT ... FOR UPDATE`) triggers catastrophic table locks, index bloat, and connection exhaustion past modest volumes.
5. **Bound All Queries and Allocations:** Never issue unbounded `SELECT *` queries or unbounded thread pool queues. Enforce strict `LIMIT` pagination and bounded blocking queues.
6. **Decouple Synchronous Workflows with Asynchronous Events:** Never execute heavy side effects (sending confirmation emails, updating search indexes, computing analytics) synchronously in the user request path.

---

## 8. Failure Modes & Production Architecture Triage

| Failure Pattern | Symptom / Signal | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **Cascading Failure** | Entire backend platform crashes simultaneously across all services. | Downstream service slowed down; upstream services blocked holding threads, exhausting thread pools. | Enforce strict timeouts, circuit breakers, and bulkhead thread pool isolation. |
| **Hot Shard Saturation** | 1 database shard at 100% CPU; remaining 15 shards idle at 5% CPU. | Non-uniform shard key (e.g., sharding by celebrity `merchant_id` or date). | Salt shard key with random suffix, or employ consistent hashing with virtual nodes. |
| **Split-Brain Disaster** | Database cluster writes two diverging branches of data simultaneously. | Network partition severed link between two datacenters; both promoted read replicas to master. | Require majority quorum ($\lfloor N/2 \rfloor + 1$) for replica promotion; use fencing tokens. |
| **Retry Storm** | Downstream service recovering from outage is immediately re-crushed. | Thousands of upstream clients retry simultaneously without backoff or jitter. | Enforce Exponential Backoff with Full Jitter and server-side Load Shedding. |
---

## 9. High-Bar System Design Interview Scenarios

### Scenario 1: Distributed Rate Limiter for 500,000 QPS
- **Interview Question:** "Design a global distributed rate limiter enforcing 100 requests/minute per user across a cluster receiving 500,000 requests/second."
- **Deep Technical Answer:**
  1. **Two-Tier Architecture:**
     - **Tier 1 (In-Memory Local Rate Limiter):** Deployed inside each API gateway instance. Uses a local token bucket to absorb massive bursts and drop obvious abuse with zero network overhead.
     - **Tier 2 (Global Distributed Counter):** Backed by a Redis Cluster sharded by `userId`.
  2. **Redis Atomic Token Bucket:** Implemented via an atomic Lua script (`HMSET` tracking tokens and `last_updated`).
  3. **Network Optimization:** Gateways pre-allocate batches of tokens (e.g., request 10 tokens at once) from Redis, reducing Redis calls from 500,000 QPS down to 50,000 QPS.

### Scenario 2: Tail Latency Amplification in Fan-Out Microservices
- **Interview Question:** "A user request fans out to 50 backend microservices in parallel. Each microservice has a $p99$ latency of 50ms, yet user-facing $p99$ latency exceeds 2,000ms. How do you resolve this?"
- **Deep Technical Answer:**
  1. **The Math:** $P(	ext{user request suffers tail latency}) = 1 - (1 - 0.01)^{50} pprox 39.5\%$. Four out of every ten user requests experience tail latency.
  2. **Architectural Remedies (Jeffrey Dean / Google SRE):**
     - **Hedged Requests:** If a downstream service does not respond within $p95$ duration (e.g., 20ms), immediately send an identical duplicate request to another replica. Cancel the slower request upon first response. Cuts 99th percentile latency by $80\%$.
     - **Tied Requests:** Encode cross-server queue cancellation tokens.
     - **Micro-Timeouts & Degraded Fallbacks:** Return partial search results if secondary ranking services exceed budget.

### Scenario 3: Zero-Downtime Migration of a 10TB Database Table
- **Interview Question:** "How do you migrate a critical 10TB database table to a sharded database cluster without any user-facing downtime?"
- **Deep Technical Answer:**
  1. **Phase 1 (Dual-Write):** Modify application to write all new inserts, updates, and deletes to both the old database and the new sharded database. Read traffic continues querying the old database.
  2. **Phase 2 (Historical Backfill):** Run an asynchronous ETL pipeline streaming historical records from old DB to new shards, ignoring records already updated by Phase 1.
  3. **Phase 3 (Shadow Reads & Verification):** Route $100\%$ of read traffic to old DB and asynchronously duplicate reads to new DB. Compare results and verify zero data drift.
  4. **Phase 4 (Cutover):** Switch primary reads to the new sharded database.
  5. **Phase 5 (Deprecation):** Terminate writes to the old database and decommission.
---

## 7. Deep-Dive Case Studies: Schemas, Data Flows & Bottlenecks

### 7.1 Case Study 1: Distributed URL Shortener (TinyURL)
- **Scale Requirements:** 100M URLs created/month ($38\text{ writes/sec}$), 100:1 read-to-write ratio ($3,800\text{ read QPS}$, $11.4\text{B reads/month}$).
- **Base62 Encoding Physics:** Characters `[a-zA-Z0-9]` ($62$ characters). Using a 7-character string generates $62^7 = 3,521,614,606,208$ ($3.52\text{ Trillion}$) unique keys.
- **Relational Storage Schema:**
  ```sql
  CREATE TABLE short_urls (
      id BIGINT PRIMARY KEY, -- 64-bit integer from range token generator
      short_key VARCHAR(7) UNIQUE NOT NULL, -- Base62 representation of id
      long_url VARCHAR(2048) NOT NULL,
      user_id UUID NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
      expires_at TIMESTAMP WITH TIME ZONE
  );
  CREATE INDEX idx_short_urls_key ON short_urls (short_key);
  ```
- **Component Data Flow:**
  ```
  [Client] ---> [Load Balancer] ---> [API Gateway Tier]
                                           |
                   +-----------------------+-----------------------+
                   | (Write: Generate URL)                         | (Read: Redirect)
                   v                                               v
        [Token Range Service]                             [Redis Cache Tier]
        (Allocates 1M ID blocks via ZooKeeper)            (99% Hit Ratio - < 2ms)
                   |                                               | (Cache Miss)
                   v                                               v
        [URL Shortener Worker]                            [Read Replicas DB]
        (Encodes ID to Base62; writes to DB & Cache)      (PostgreSQL Shards)
  ```
- **Bottlenecks & Mitigations:**
  - *Single Point of Failure in ID Generator:* Worker nodes pre-fetch blocks of 1,000,000 IDs from ZooKeeper (`[1..1000000]`, `[1000001..2000000]`). If a worker crashes, remaining range is lost, but no collisions ever occur.
  - *Hot Key Read Stampede:* High-profile short URLs cached in Redis with probabilistic early expiration (XFetch) and local L1 Caffeine near-caches.

### 7.2 Case Study 2: High-Throughput Notification Service
- **Scale Requirements:** 500M notifications/day across Email, SMS, Push ($5,800\text{ msgs/sec}$ avg, $25,000\text{ msgs/sec}$ peak). Sub-5s delivery for critical OTP codes.
- **Relational Storage Schema:**
  ```sql
  CREATE TABLE notifications (
      id UUID PRIMARY KEY,
      idempotency_key VARCHAR(128) UNIQUE NOT NULL,
      user_id UUID NOT NULL,
      channel VARCHAR(16) NOT NULL, -- EMAIL, SMS, PUSH
      priority VARCHAR(16) NOT NULL, -- HIGH, DEFAULT, LOW
      payload JSONB NOT NULL,
      status VARCHAR(16) NOT NULL, -- PENDING, DELIVERED, FAILED
      retry_count INT NOT NULL DEFAULT 0,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
      delivered_at TIMESTAMP WITH TIME ZONE
  );
  CREATE INDEX idx_notif_user_time ON notifications (user_id, created_at DESC);
  ```
- **Component Data Flow:**
  ```
  [Client Services] ---> [Notification Ingestion API]
                               |
                               v
                     [Kafka Ingestion Bus]
         +---------------------+---------------------+
         | (high-priority-otp) | (bulk-marketing)    |
         v                     v                     v
  [Priority Workers]    [Standard Workers]    [Marketing Workers]
         |                     |                     |
         +---------------------+---------------------+
                               |
                               v
                    [Third-Party Provider Tier]
         (Twilio / SendGrid / AWS SNS / Apple APNs / Google FCM)
                               |
                      (Provider Down?)
                               v
                    [Automatic Failover Router]
  ```
- **Bottlenecks & Mitigations:**
  - *Downstream Provider Throttling:* Third parties impose strict rate limits. Outbound workers use token bucket rate limiters per provider, backoff on HTTP 429, and fail over dynamically to backup vendors (e.g., Twilio $\to$ AWS SNS).

### 7.3 Case Study 3: Flux Real-Time Collaborative Workspace
- **Scale Requirements:** 1M concurrent documents, 50,000 active collaborative editing rooms, sub-50ms keystroke synchronization.
- **Concurrency Conflict Resolution: CRDTs vs. OT:**
  - *Operational Transformation (OT):* Requires centralized server to order and transform operation indices. High coordination overhead.
  - *Conflict-Free Replicated Data Types (CRDTs - RGA):* Commutative, associative operations merge deterministically across peers without central coordination.
- **Component Data Flow:**
  ```
  [Browser Client A] <==== WebSocket ====> [WebSocket Gateway Tier]
                                                  |
  [Browser Client B] <==== WebSocket ====>        v
                                          [Redis Pub/Sub Backplane]
                                          (Topic per Document ID)
                                                  |
                                                  v
                                      [Document State Worker]
                                      (CRDT In-Memory Engine)
                                                  |
                                                  v
                                      [Kafka Commit Log Queue]
                                                  |
                                                  v
                                      [Object Storage (S3 / DB)]
                                      (Periodic Document Snapshots)
  ```
- **Bottlenecks & Mitigations:**
  - *WebSocket Statefulness:* Gateway nodes hold stateful TCP connections. When a node terminates, clients reconnect with exponential backoff and jitter. Clients resynchronize document state via vector clocks.

### 7.4 Case Study 4: ArgusIQ Real-Time Fraud Detection
- **Scale Requirements:** 100,000 financial card transactions/second. Sub-50ms fraud scoring SLA. False positive rate $< 0.01\%$.
- **Streaming Architecture:**
  ```
  [Payment Gateway] ---> [Kafka: payment-transactions]
                                |
                                v
                    [Apache Flink Stream Cluster]
                    +------------------------------------+
                    | Sliding Event-Time Window:         |
                    | "Count tx by card in last 5 min"   |
                    | State stored in RocksDB            |
                    +------------------------------------+
                                | (Enriched Feature Vector)
                                v
                    [Inference Tier (ONNX Model)]
                    +------------------------------------+
                    | Real-time XGBoost / Neural Net     |
                    | Evaluates Fraud Probability        |
                    +------------------------------------+
                                |
                 +--------------+--------------+
                 | (Fraud Score > 0.85)        | (Fraud Score <= 0.85)
                 v                             v
      [Block Payment RPC]             [Allow & Persist]
  ```
- **Bottlenecks & Mitigations:**
  - *Stateful Checkpoint Overhead:* Flink maintains large in-memory state in RocksDB. Incremental checkpointing flushes modified SST files to distributed object storage, eliminating long GC pauses.
