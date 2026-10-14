# Module 07 — Caching and Performance

> **Curriculum Track:** Core Backend Engineering & Scalable Systems
> **Target Audience:** Graduating Computer Science Engineers & Backend SDE Candidates
> **Prerequisites:** Module 01 (DSA), Module 02 (OS & Networks), Module 03 (Java & Runtimes), Module 04 (Database Engineering & SQL), Module 05 (Backend Frameworks), Module 06 (API Engineering & Security)
> **Runtimes & Frameworks:** Redis 7.x, OpenJDK 21 LTS, Spring Boot 3.3.x / Spring Cache, Node.js 20+ LTS / Express, PostgreSQL 16
> **Standard Specifications:** RFC 9111 (HTTP Caching), RFC 9110 (HTTP Semantics)

---

## Executive Overview and Architecture

In distributed backend engineering, **caching** is the deliberate exploitation of spatial and temporal locality to trade volatile memory capacity for dramatic reductions in access latency, downstream compute saturation, and database disk I/O. However, introducing a cache fundamentally transforms a single-source-of-truth system into an eventually consistent distributed system subject to the dual-write hazard, memory fragmentation, thundering herds, and cache-invalidation races.

```
+---------------------------------------------------------------------------------------------------+
|                                 MULTI-TIER CACHING TOPOLOGY                                       |
+---------------------------------------------------------------------------------------------------+
|                                                                                                   |
|  [ Client Browser ] ----(HTTP / CDN Edge)----> [ Cloudflare / Fastly CDN ]                        |
|                                                       | (Edge Cache Hit: ~10-30ms)                |
|                                                       v (Origin Miss)                             |
|  [ Reverse Proxy / Ingress ] -----------------> [ Nginx / API Gateway ] (HTTP Reverse Proxy Cache)|
|                                                       | (~2-5ms)                                  |
|                                                       v                                           |
|  [ Backend Application Instances ] -----------> [ L1: In-Process Cache (Caffeine / Node Map) ]   |
|                                                       | (~50-200ns, Zero Network Serialization)   |
|                                                       v (L1 Miss)                                 |
|  [ Distributed Caching Tier ] ----------------> [ L2: Redis 7.x Cluster / Sentinel ]             |
|                                                       | (~0.5-2.0ms, Shared Across All Pods)       |
|                                                       v (L2 Miss / Cache Aside)                   |
|  [ Primary Persistence Tier ] ----------------> [ PostgreSQL 16 / InnoDB Buffer Pool ]           |
|                                                       | (Shared Buffers: ~5-15ms; Disk: ~50ms+)   |
|                                                       v                                           |
|                                                 [ NVMe / SSD Physical Storage ]                   |
+---------------------------------------------------------------------------------------------------+
```

This module establishes the first-principles physics of memory hierarchies, dissects the five canonical caching patterns, exposes the mathematical realities of cache invalidation races, and examines production-grade Redis operations, Spring Cache abstractions, Node.js asynchronous caching pipelines, HTTP caching contracts (RFC 9111), and empirical performance engineering.

---

## Table of Contents

- [Section 1 — Why Caching Exists](#section-1--why-caching-exists)
- [Section 2 — Cache Architectures](#section-2--cache-architectures)
- [Section 3 — Cache-Aside and Other Caching Patterns](#section-3--cache-aside-and-other-caching-patterns)
- [Section 4 — Cache Invalidation and Consistency](#section-4--cache-invalidation-and-consistency)
- [Section 5 — TTL, Eviction, and Memory Management](#section-5--ttl-eviction-and-memory-management)
- [Section 6 — Redis Fundamentals](#section-6--redis-fundamentals)
- [Section 7 — Redis Internals and Operational Behavior](#section-7--redis-internals-and-operational-behavior)
- [Section 8 — Cache Stampede, Thundering Herd, and Hot Keys](#section-8--cache-stampede-thundering-herd-and-hot-keys)
- [Section 9 — Cache Penetration and Negative Caching](#section-9--cache-penetration-and-negative-caching)
- [Section 10 — Redis for Rate Limiting and Coordination](#section-10--redis-for-rate-limiting-and-coordination)
- [Section 11 — Caching with Spring Boot](#section-11--caching-with-spring-boot)
- [Section 12 — Caching with Node.js and Express](#section-12--caching-with-nodejs-and-express)
- [Section 13 — HTTP Caching](#section-13--http-caching)
- [Section 14 — Database Performance and Caching Trade-Offs](#section-14--database-performance-and-caching-trade-offs)
- [Section 15 — Performance Engineering](#section-15--performance-engineering)
- [Section 16 — Cache Design Case Studies](#section-16--cache-design-case-studies)
- [Section 17 — Interview Question Bank](#section-17--interview-question-bank)
- [Section 18 — Module 7 Cheatsheet: Caching & Performance](#section-18--module-7-cheatsheet-caching--performance)

---

## Section 1 — Why Caching Exists

### 1.1 The Fundamental Mental Model: Caching vs. Persistence vs. Databases

A **cache** is a transient, high-speed data storage layer that stores a subset of data—typically transient in nature or duplicated from an authoritative storage engine—so that future requests for that data are served significantly faster than fetching it from the primary source.

To understand caching, an engineer must distinguish three concepts frequently conflated in superficial interview discussions:

| Dimension | Caching | Persistence | Database Management System (DBMS) |
| :--- | :--- | :--- | :--- |
| **Primary Objective** | Minimize retrieval latency and offload downstream compute/disk subsystems. | Guarantee data survival across process crashes, power failures, and restarts. | Maintain relational invariants, referential integrity, ACID transactional boundaries, and arbitrary indexing. |
| **Authority** | **Derivative:** A cache holds disposable copies. The authoritative truth lives elsewhere. | **Authoritative:** The persistent store is the canonical record. Loss of persistent data is data corruption. | **Authoritative & Structural:** Enforces schemas, constraints, relations, and ACID transaction isolation. |
| **Storage Medium** | Volatile DRAM (main memory) or ultra-fast CPU caches. | Non-volatile storage (NVMe SSDs, spinning magnetic disks, cloud block storage). | Non-volatile disk with memory-mapped caching buffers (e.g. PostgreSQL `shared_buffers`). |
| **Data Loss Tolerance** | **High:** Key loss is an operational performance penalty (cache miss), never a business failure. | **Zero:** Data lost after commit violates the Durability guarantee of ACID. | **Zero:** Data must be durably committed to the Write-Ahead Log (WAL) before acknowledging success. |
| **Access Semantics** | Point lookups via unique opaque keys (`O(1)` or $O(\log N)$ hash/btree lookups). | Sequential block I/O or filesystem chunk reads. | Complex declarative queries (`SQL`), relational joins, filtering, grouping, aggregations, secondary indexes. |

> [!IMPORTANT]
> **The Golden Rule of Caching:** If your system cannot completely reconstruct the contents of a cache from authoritative persistent storage upon total cache annihilation, you do not have a cache—you have an unbacked, fragile, non-durable primary database.

---

### 1.2 The Physics of Memory: Latency Numbers Every Backend Engineer Must Know

Software engineers often treat data access as an abstract algorithmic step. In physical reality, computing hardware is governed by electromagnetic latency, spatial distance, bus widths, and mechanical/solid-state controller characteristics:

```
+----------------------------------------------------------------------------------------+
|                             THE ACCESS LATENCY LADDER                                   |
+----------------------------------------------------------------------------------------+
| Level 1 (L1) CPU Cache Reference:           ~1 ns       | [■]                          |
| Level 2 (L2) CPU Cache Reference:           ~4 ns       | [■■]                         |
| Level 3 (L3) CPU Cache Reference:           ~10 ns      | [■■■■]                       |
| Main Memory (DRAM) Reference:               ~100 ns     | [■■■■■■■■■■■■■■■■]           |
| Solid-State Drive (NVMe SSD) I/O:           ~10-50 µs   | (100x to 500x slower than RAM|
| Rotational Disk Seek (HDD):                 ~5-10 ms    | (50,000x to 100,000x slower) |
| Intra-Datacenter Network Round Trip (RTT):  ~0.5-2 ms   | (5,000x to 20,000x slower)   |
| Cross-Region WAN Round Trip (NYC to LON):   ~75-100 ms  | (750,000x to 1,000,000x slower)|
+----------------------------------------------------------------------------------------+
```

When an application queries PostgreSQL on disk without hitting an index or the buffer pool, the database engine must issue system calls (`pread()`) causing the operating system kernel to request blocks over the PCIe or SATA bus. Main memory DRAM access operates at ~100 nanoseconds, whereas NVMe disk reads take tens of microseconds, and network round-trips take milliseconds. Caching exploits this orders-of-magnitude physical disparity.

#### Local In-Process Memory vs. Remote Distributed Cache
A common interview mistake is assuming all caches are identical. We must bifurcate application-local memory from network-attached remote caches:
- **Local In-Process Cache (e.g., Java Caffeine, Guava, Node.js Map):** Resides in the virtual address space of the application process. Data access requires zero network hops, zero operating system context switching, and zero serialization/deserialization. Retrieval occurs in **tens of nanoseconds**. However, memory is bounded by the process heap, is duplicated across horizontally scaled instances, and vanishes when the pod or JVM restarts.
- **Remote Distributed Cache (e.g., Redis Cluster, Memcached):** A standalone network daemon accessible over TCP/Unix domain sockets. Retrieval incurs network serialization (JSON, Protobuf, or Redis serialization protocol RESP), socket buffer copying, and network latency (~0.5ms to 2.0ms). However, the cache is shared globally across hundreds of application pods, survives application redeployments, and scales horizontally independent of application compute.

---

### 1.3 Locality of Reference, Working Sets, and Workload Profiles

Caching functions effectively only because real-world software access patterns are non-uniform. They exhibit two fundamental physical properties:

1. **Temporal Locality:** If a specific data item is accessed at time $t$, it is highly probable that the identical data item will be accessed again at time $t + \Delta t$. Examples include a breaking news article, a trending video metadata object, or a user's authentication session during an active browsing session.
2. **Spatial Locality:** If a specific memory address or data entity is accessed, proximate data items are likely to be accessed soon thereafter. Examples include sequential table scans, fetching subsequent pages of an order history, or retrieving consecutive fields within a Redis Hash structure.

#### The Working Set Concept
In any production system holding terabytes of persistent data, the **working set** is the active subset of data that accounts for the overwhelming majority of incoming requests during a designated time window.

```
       TOTAL DATASET VOLUME: 10,000,000 Entities (100% Volume / 500 GB on Disk)
+-----------------------------------------------------------------------------------+
|  Cold Data (80%): Historical archives, dormant accounts, inactive catalog items  |
|                                                                                   |
|                   +---------------------------------------------------------------+
|                   |  Warm Data (15%): Accessed occasionally (Weekly / Monthly)    |
|                   |                                                               |
|                   |        +------------------------------------------------------+
|                   |        | >>> THE WORKING SET (5% of keys = 80-90% traffic) <<<|
|                   |        | Hot user sessions, trending products, active feeds   |
+-------------------+--------+------------------------------------------------------+
```

Under a **Zipfian or Pareto distribution** (the 80/20 rule, or in web applications often 95/5), caching just 5% of the total dataset in high-speed RAM can serve 80% to 95% of all client requests, effectively shielding the relational database from saturation.

#### Read-Heavy vs. Write-Heavy Workloads
- **Read-Heavy Workloads (Read:Write Ratio $\ge$ 10:1):** Canonical candidates for caching (e.g. social media profile reads, e-commerce product catalogs, public news sites, DNS resolution). The high hit rate offsets the small overhead of cache management.
- **Write-Heavy Workloads (Read:Write Ratio $\le$ 1:1):** Poor candidates for caching (e.g. high-frequency telemetry logging, IoT sensor ingestion, financial ledger audit trails). Caching introduces double-write latency and severe cache-invalidation churn, where cached entries are repeatedly evicted or overwritten before ever being read.

---

### 1.4 The Complete Request Path: Hit Path vs. Miss Path

The end-to-end traversal of a client request across an application utilizing a cache reveals the stark architectural bifurcation between a **cache hit** and a **cache miss**:

```
[ CLIENT ]
    |
    | 1. HTTP GET /products/8821
    v
[ APPLICATION SERVICE ]
    |
    | 2. Check Cache: GET cache:product:8821
    v
[ CACHE TIER (REDIS) ]
   / \
  /   \
 [ HIT ] [ MISS ]
  |       |
  |       | 3. Cache Miss: Query DB
  |       v
  |    [ DATABASE (POSTGRESQL) ]
  |       |
  |       | 4. Return SQL Record
  |       v
  |    [ APPLICATION SERVICE ]
  |       |
  |       | 5. Populate Cache: SETEX cache:product:8821 3600 <data>
  |       v
  |    [ CACHE TIER (REDIS) ]
  |       |
  +-------+
  |
  | 6. Return JSON Response to Client
  v
[ CLIENT ]
```

#### Step-by-Step Path Analysis
- **Cache Hit Path (Optimal Path):**
  1. Client sends request to Application.
  2. Application queries Cache (`GET cache:product:8821`).
  3. Cache locates entity in memory and returns serialized payload.
  4. Application deserializes payload and returns HTTP 200 to client.
  - **Latency:** ~0.5ms to 2.0ms total. Database CPU utilization: 0%. Database disk I/O: 0 bytes.
- **Cache Miss Path (Degraded / Loading Path):**
  1. Client sends request to Application.
  2. Application queries Cache (`GET cache:product:8821`).
  3. Cache inspects index, finds key non-existent or expired, and returns `nil`.
  4. Application issues SQL query to Database (`SELECT * FROM products WHERE id = 8821`).
  5. Database parses query, performs B-Tree index scan, retrieves row from disk/buffer pool, and returns result set over TCP.
  6. Application constructs domain entity, serializes it to JSON, and asynchronously or synchronously writes it into the cache (`SETEX cache:product:8821 3600 payload`).
  7. Application returns HTTP 200 to client.
  - **Latency:** ~15ms to 60ms total. The miss path is strictly slower than having no cache at all because it incurs the latency of the cache check *plus* the database query *plus* the cache write-back!

---

### 1.5 Mathematical Derivation of Expected Latency and Production Caveats

Let:
- $H \in [0.0, 1.0]$ be the **Cache Hit Ratio** ($\text{Hits} / (\text{Hits} + \text{Misses})$).
- $M = 1 - H$ be the **Cache Miss Ratio**.
- $L_{\text{cache}}$ be the latency of a cache access (network + read).
- $L_{\text{db}}$ be the latency of a database read query.
- $L_{\text{write}}$ be the latency of writing the missed record back into the cache.

The **Expected Average Latency** $E[L]$ of the system is derived as:

$$E[L] = H \times L_{\text{cache}} + (1 - H) \times (L_{\text{cache}} + L_{\text{db}} + L_{\text{write}})$$

Simplifying algebraically:

$$E[L] = L_{\text{cache}} + (1 - H) \times (L_{\text{db}} + L_{\text{write}})$$

#### Concrete Numerical Example
Assume typical cloud production latencies:
- $L_{\text{cache}} = 1.0\text{ ms}$
- $L_{\text{db}} = 50.0\text{ ms}$
- $L_{\text{write}} = 1.0\text{ ms}$
- Total Miss Penalty: $L_{\text{miss}} = 1.0 + 50.0 + 1.0 = 52.0\text{ ms}$

We evaluate expected latency across four operational hit ratios:

| Cache Hit Ratio ($H$) | Miss Ratio ($1 - H$) | Calculation | Expected Latency $E[L]$ | Speedup Factor vs. No Cache ($50\text{ ms}$) |
| :---: | :---: | :--- | :--- | :---: |
| **0.00 (0%)** | 1.00 (100%) | $1.0 + 1.00 \times 51.0$ | **$52.0\text{ ms}$** | **$0.96\times$ (Degradation)** |
| **0.50 (50%)** | 0.50 (50%) | $1.0 + 0.50 \times 51.0$ | **$26.5\text{ ms}$** | **$1.88\times$** |
| **0.90 (90%)** | 0.10 (10%) | $1.0 + 0.10 \times 51.0$ | **$6.1\text{ ms}$** | **$8.20\times$** |
| **0.99 (99%)** | 0.01 (1%) | $1.0 + 0.01 \times 51.0$ | **$1.51\text{ ms}$** | **$33.1\times$** |

```
EXPECTED LATENCY vs CACHE HIT RATIO
Latency (ms)
  52 +--* (0% Hit -> 52ms: Cache overhead penalty)
     |   \
  40 |    \
     |     \
  26 |      \---* (50% Hit -> 26.5ms)
     |           \
  10 |            \
     |             \------* (90% Hit -> 6.1ms)
   1 +---------------------* (99% Hit -> 1.51ms)
     +----+----+----+----+----+----+----+----+----+----+
     0   10   20   30   40   50   60   70   80   90  100
                         Hit Ratio (%)
```

> [!WARNING]
> **Production Reality Check:** The equation above is an idealized analytical model. In real production deployments, expected latency is **not** purely linear because:
> 1. **Queueing Saturation:** Under heavy traffic, as hit rate drops, database connection pools saturate. Database latency does not remain flat at $50\text{ms}$; it climbs exponentially to tens of seconds due to thread starvation and queueing delays (Little's Law).
> 2. **Tail Latency Dominance:** The 1% of cache misses dictate your application's p99 latency. Even if p50 latency is $1\text{ms}$, your p99 latency will remain pinned to $50\text{ms}+$.
> 3. **Garbage Collection and Serialization:** High-throughput JSON deserialization of large cached objects creates memory allocation churn in Node.js and JVM garbage collectors, inducing intermittent latency spikes unrelated to network transmission.

---

### 1.6 The Cost of Caching: Consistency, Memory, and Operational Fragility

Senior engineers understand that caching is not a universal optimization; it is an architectural trade-off that introduces three immense failure modes:

1. **Consistency Hazards (Stale Reads):** The instant data is written to a cache, two copies of reality exist in your architecture. If the primary database is mutated and the cache is not invalidated synchronously and atomically, clients will read stale, incorrect, or corrupted business state.
2. **Memory Exhaustion and Eviction Thrashing:** DRAM costs roughly $3–5\times$ more per gigabyte than NVMe storage. When cache data volume exceeds physical memory limits, the cache engine must evict keys. Suboptimal eviction policies trigger thrashing, where hot keys are continually purged and reloaded.
3. **Operational Fragility (The Cold Start & Crash Stampede):** When a cache cluster restarts or fails, the application hit rate instantly drops to 0%. If your primary database was sized under the assumption that 95% of queries would be absorbed by the cache, the resulting raw traffic will instantly overwhelm database connection pools, inducing total cascading service failure.

---

## Section 2 — Cache Architectures

### 2.1 The Multi-Tier Caching Spectrum

Modern web architectures deploy caching at nearly every layer of the compute stack. An engineer must select the appropriate tier based on sharing boundaries, latency requirements, and operational risk:

```
[ User Browser / Device ]
   |-- Browser Cache (Private to 1 user, zero network latency, validated via ETag/304)
   v
[ Edge Network / CDN ]
   |-- CDN Edge Caches (Public, shared across region, geodistributed, cached via Cache-Control)
   v
[ Infrastructure Ingress ]
   |-- Reverse Proxy Cache (Nginx / Varnish: shared across origin cluster, microsecond HTTP caching)
   v
[ Application Cluster (Pods) ]
   |-- In-Process L1 Cache (Caffeine / Node Map: private to pod, ~50ns access, volatile across redeploys)
   |-- Distributed L2 Cache (Redis Cluster: shared across all pods, ~1ms access, survives redeploys)
   v
[ Database Engine ]
   |-- Database Buffer Pool (PostgreSQL shared_buffers / InnoDB Buffer Pool: transparent to app)
   v
[ Physical Storage NVMe ]
```

---

### 2.2 Deep Architectural Comparison Matrix

| Architectural Tier | Latency | Sharing Scope | Consistency Model | Primary Failure Mode | Operational Complexity | Ideal Workload |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **CPU Cache (L1/L2/L3)** | 1–10 ns | Single CPU Core / Die | Hardware Coherence (MESI) | Cache miss / Pipeline stall | Zero (Managed by Hardware) | Instruction and register execution. |
| **In-Process Cache (Caffeine/Map)** | 50–200 ns | Single Application Process | Process-isolated (High drift across pods) | Pod crash, JVM GC pause, OOM heap exhaustion | Low (Application library) | Immutable configuration, static metadata, ultra-hot localized read loops. |
| **Distributed Cache (Redis/Memcached)** | 0.5–2.0 ms | All instances across cluster | Centralized (Eventual consistency vs DB) | Network partition, master failover lag, connection pool exhaustion | Moderate to High (Requires cluster management) | Shared session state, user profiles, dynamic entity caching, distributed rate limits. |
| **Reverse Proxy (Nginx/Varnish)** | 1–5 ms | All traffic reaching ingress | Time-bounded HTTP expiration | Stale page delivery, header misconfiguration | Moderate | Public REST responses, anonymous landing pages, static assets. |
| **Edge CDN (Cloudflare/Fastly)** | 10–30 ms | Globally distributed by POP | Eventual (Global purge delay ~150ms–5s) | Origin shielding failure, cache poisoning, SSL handshake spikes | Low to Moderate (Managed SaaS) | Static web bundles, product images, geo-localized public API responses. |
| **Database Buffer Pool** | 0.1–0.5 ms | Database Engine Process | Strictly Consistent (ACID WAL synchronization) | Buffer pool churn from large unindexed sequential scans | Zero (Integrated in DBMS) | Relational table pages, B-Tree index pages. |

---

### 2.3 Why Application-Local Caches and Redis are Not Interchangeable

Junior engineers frequently ask: *"Why deploy Redis when we can just use a `ConcurrentHashMap` in Java or a `Map` in Node.js?"*

There are four architectural reasons why in-process caches cannot substitute for a distributed cache in production:

#### 1. Multi-Instance State Divergence (The Cluster Drift Problem)
When your backend runs across 20 Kubernetes pods behind a load balancer, each pod maintaining its own in-process cache creates 20 divergent views of reality. If Pod A updates a user's permission in the database and updates its local cache, Pods B through T continue serving the old permission until their local TTLs expire. A user refreshing their browser will observe erratic, non-deterministic state depending on which pod the load balancer routes their request to.

#### 2. Heap and Garbage Collection Pressure
An in-process cache lives inside the application runtime heap. In Java, storing millions of objects in a `ConcurrentHashMap` dramatically expands the JVM young and old generation heaps. During GC cycles, the Garbage Collector must traverse every live reference, causing severe **Stop-The-World (STW)** pause times that degrade tail latency. Redis, written in C, manages its own memory outside application runtimes without GC pauses.

#### 3. Rolling Deployments and Cold Start Shocks
In modern CI/CD pipelines, applications are deployed multiple times daily. When a rolling update replaces 100% of your application pods, all in-process caches are wiped instantly. The newly booted pods experience a 100% cache miss rate simultaneously, unleashing an unshielded thundering herd directly against the primary database. Redis lives on dedicated infrastructure independent of application pod lifecycles, ensuring a 95% hit rate is preserved seamlessly across application deployments.

#### 4. Serialization Costs vs. Object Identity
Conversely, an in-process cache possesses one enormous advantage: **zero serialization**. In Java, fetching an object from Caffeine returns a direct pointer reference (`4 bytes` or `8 bytes` compressed OOP) in ~50 nanoseconds. Fetching from Redis requires sending a socket frame, Redis serializing data into RESP, the OS handling TCP packet queues, and the Java/Node client parsing bytes and deserializing JSON or binary protobufs into new heap objects (~1–2 milliseconds).

---

### 2.4 Multi-Level Caching: The L1 / L2 Hybrid Pattern

High-throughput systems (such as high-frequency trading, real-time gaming, and ultra-high-scale social feeds) frequently combine both architectures into a **Multi-Level Cache (L1/L2)**:
- **L1 Cache (Local In-Process):** Stores the hottest 1,000 keys directly in Caffeine or a local Map with an aggressive, ultra-short TTL (e.g., 5 to 15 seconds). Serves traffic in nanoseconds.
- **L2 Cache (Remote Redis):** Stores the broader working set (millions of keys) with a longer TTL (e.g., 1 hour).
- **Invalidation Bus:** When an entity is updated, the mutating instance updates the database, deletes the key in Redis (L2), and broadcasts an invalidation message over **Redis Pub/Sub** to all running application pods, instructing them to instantly purge their local L1 entries.

---

## Section 3 — Cache-Aside and Other Caching Patterns

### 3.1 Pattern Taxonomy Overview

Backend architectures employ five primary patterns for coordinating data movement between application code, caching engines, and persistent databases:

```
1. CACHE-ASIDE (LAZY LOADING)
   App manages both Cache and DB explicitly. Cache is unaware of DB.

2. READ-THROUGH
   App queries Cache abstraction. Cache transparently loads from DB on miss.

3. WRITE-THROUGH
   App writes to Cache. Cache synchronously writes to DB before acknowledging.

4. WRITE-BEHIND (WRITE-BACK)
   App writes to Cache. Cache acknowledges immediately, then flushes asynchronously to DB.

5. REFRESH-AHEAD
   Cache proactively reloads hot keys from DB before TTL expiration based on access frequency.
```

---

### 3.2 Deep Dive: Cache-Aside (Lazy Loading)

Cache-Aside is the ubiquitous standard pattern for general-purpose web APIs. The application coordinates reads and writes directly between the caching tier and the database tier.

```
                   +-------------------+
                   |    APPLICATION    |
                   +-------------------+
                     /        |        \
         1. Check   /         |         \  4. Write Miss
           Cache   /          |          \     Back
                  v           | 2. DB     v
            +-------+         |    Query +-------+
            | CACHE |         v          | CACHE |
            +-------+   +------------+   +-------+
                        |  DATABASE  |
                        +------------+
```

#### Read Protocol:
1. Application receives request for entity `K`.
2. Application issues `GET K` to the cache.
3. If hit: Application immediately returns cached value.
4. If miss: Application executes SQL query `SELECT * FROM tbl WHERE id = K`.
5. If record exists: Application writes value to cache with a TTL (`SETEX K ttl payload`).
6. Application returns payload to caller.

#### Write Protocol:
1. Application executes mutation on the database (`UPDATE tbl SET ... WHERE id = K`) inside an ACID transaction.
2. Database commits successfully.
3. Application explicitly invalidates (deletes) the cache entry (`DEL K`).

#### Why Deletion Trumps Mutation in Cache-Aside
A pervasive beginner bug is attempting to **update** the cache on writes (`SET K new_val`) instead of **deleting** it (`DEL K`). Updating the cache on write creates severe write-order race conditions:

```
[ Thread 1 (Update to 'A') ]          [ Thread 2 (Update to 'B') ]
           |                                     |
    1. Write DB -> 'A'                           |
           |                              2. Write DB -> 'B'
           |                              3. Write Cache -> 'B'
    4. Write Cache -> 'A' (Delayed!)             |
           v                                     v
   DATABASE VALUE = 'B'                  CACHE VALUE = 'A'  <-- PERMANENT DESYNCHRONIZATION!
```

Because network delays and thread scheduling vary, Thread 1's cache write can arrive *after* Thread 2's cache write, even though Thread 2 won the database race. Deleting the key on write ensures that subsequent readers pull the latest committed database state on their next miss.

> [!WARNING]
> **Key Invalidation Does Not Eliminate Every Race:** While deleting on write eliminates concurrent write-write order races, it does **not** eliminate the concurrent read-miss/write interleave race (where a slow reader reads an old DB record, the writer commits and deletes the cache key, and the slow reader writes back the old record). That race requires versioned compare-and-set or watermark tombstones (detailed in Section 4.3).

---

### 3.3 Comparative Analysis of Caching Patterns

| Pattern | Read Path Mechanics | Write Path Mechanics | Consistency Guarantees | Data Loss Risk | Operational Complexity | Best Suited Workloads |
| :--- | :--- | :--- | :--- | :---: | :--- | :--- |
| **Cache-Aside** | App checks cache; on miss, loads from DB and populates cache. | App writes to DB, then invalidates (`DEL`) cache key. | Eventual. Resilient to cache crashes. | **Zero** (DB is always written first). | Low (Standard backend application code). | General web APIs, variable read patterns, systems with mixed entities. |
| **Read-Through** | App requests entity from cache library; library loads from DB on miss transparently. | Independent of read path (often paired with Write-Through). | Eventual. Encapsulated in caching plugin. | **Zero** | Moderate (Requires DB plugin/provider integration). | Standardized data access layers where caching is hidden behind an ORM/repository interface. |
| **Write-Through** | Identical to Read-Through. | App writes to cache; cache synchronously writes to DB before returning success. | High perceived freshness, but **no cross-system 2PC atomicity**. If DB write fails, cache and DB diverge unless compensated. | **Zero** (if DB write succeeds before ACK). | Moderate to High | Workloads where written data is immediately re-read by other clients (e.g., active user status). |
| **Write-Behind (Write-Back)** | Identical to Read-Through. | App writes to cache; cache acknowledges immediately. Async worker batches writes to DB. | Eventual. High temporary divergence between cache and DB. | **CRITICAL** (If cache crashes before async DB flush, committed data is lost permanently). | Very High (Requires durable message queuing, dead-letter queues, reconciliation). | Write-heavy ingestion, analytics event counters, gaming leaderboards where throughput supersedes absolute durability. |
| **Refresh-Ahead** | App reads from cache. Cache automatically evaluates access frequency and reloads expiring keys. | Decoupled (Cache-Aside or Write-Through). | High freshness for hot keys. | **Zero** | High (Requires predictive access modeling and background polling threads). | Predictably hot items (e.g. homepage featured products, market indices) where read misses must never occur. |

---

### 3.4 Production Pseudocode: Cache-Aside Implementation

#### Java 21 / Spring-Style Architecture
```java
public class ProductService {
    private final ProductRepository dbRepository;
    private final RedisTemplate<String, ProductDto> redisTemplate;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    public ProductDto getProductById(String productId) {
        String cacheKey = "cache:products:" + productId;

        // 1. Attempt retrieval from distributed cache
        try {
            ProductDto cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached; // Cache Hit
            }
        } catch (Exception e) {
            // Defensive engineering: Cache failures must never take down the business request!
            log.warn("Cache read failed for key {}, falling back to DB: {}", cacheKey, e.getMessage());
        }

        // 2. Cache Miss: Query authoritative database
        Product entity = dbRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        ProductDto dto = ProductDto.fromEntity(entity);

        // 3. Best-effort population of cache with TTL
        try {
            redisTemplate.opsForValue().set(cacheKey, dto, CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to populate cache for key {}: {}", cacheKey, e.getMessage());
        }

        return dto;
    }

    @Transactional
    public ProductDto updateProduct(String productId, UpdateProductRequest request) {
        String cacheKey = "cache:products:" + productId;

        // 1. Mutate authoritative persistent storage inside ACID boundary
        Product entity = dbRepository.findByIdForUpdate(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        entity.updateAttributes(request);
        Product saved = dbRepository.save(entity);
        ProductDto dto = ProductDto.fromEntity(saved);

        // 2. Invalidate cache after successful DB commit
        // In production, execute in a TransactionSynchronization.afterCommit hook!
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.error("Failed to evict cache key {}. Stale reads possible until TTL expiry!", cacheKey, e);
        }

        return dto;
    }
}
```

#### Node.js / TypeScript Architecture
```typescript
interface ProductDto {
  id: string;
  name: string;
  priceCents: number;
}

export class ProductService {
  constructor(
    private readonly db: DatabasePool,
    private readonly redis: RedisClient,
    private readonly ttlSeconds: number = 1800
  ) {}

  async getProduct(productId: string): Promise<ProductDto> {
    const cacheKey = `cache:products:${productId}`;

    // 1. Query Cache
    try {
      const raw = await this.redis.get(cacheKey);
      if (raw !== null) {
        return JSON.parse(raw) as ProductDto; // Cache Hit
      }
    } catch (err) {
      logger.warn(`Redis GET failed for ${cacheKey}, falling back to PostgreSQL:`, err);
    }

    // 2. Cache Miss: Query PostgreSQL
    const res = await this.db.query<ProductDto>(
      'SELECT id, name, price_cents FROM products WHERE id = $1',
      [productId]
    );

    if (res.rows.length === 0) {
      throw new NotFoundError(`Product ${productId} not found`);
    }

    const product = res.rows[0];

    // 3. Populate Cache
    try {
      await this.redis.set(cacheKey, JSON.stringify(product), 'EX', this.ttlSeconds);
    } catch (err) {
      logger.error(`Redis SETEX failed for ${cacheKey}:`, err);
    }

    return product;
  }

  async updateProduct(productId: string, newPriceCents: number): Promise<void> {
    const cacheKey = `cache:products:${productId}`;

    // 1. Mutate Database First
    await this.db.query(
      'UPDATE products SET price_cents = $1, updated_at = NOW() WHERE id = $2',
      [newPriceCents, productId]
    );

    // 2. Invalidate Cache Entry
    try {
      await this.redis.del(cacheKey);
    } catch (err) {
      logger.error(`Failed to delete cache key ${cacheKey} after DB write:`, err);
    }
  }
}
```

---

## Section 4 — Cache Invalidation and Consistency

### 4.1 The Dual-Write Hazard and The Invalidation Race

Whenever an application modifies both a database and a cache without distributed two-phase commit (2PC) transactions, concurrent execution interweaves operations into race conditions.

#### The Classic Invalidation Race Condition (Step-by-Step Breakdown)
Consider two concurrent requests operating on the same entity (`id = 42`):
- **Request A:** A read request that experiences a cache miss.
- **Request B:** A write request updating the entity.

```
TIME   REQUEST A (Reader)                 REQUEST B (Writer)                 DATABASE       CACHE
 |
 t1    1. Check Cache -> MISS
 |
 t2    2. SELECT * FROM user:42
 |        Reads: balance = 100
 |        [Thread A pre-empted / paused by OS/GC]
 |
 t3                                       3. UPDATE user SET bal = 200        bal = 200
 |                                           [Commits successfully]
 |
 t4                                       4. DEL cache:user:42                              (empty)
 |
 t5    5. Resumes execution!
 |        Writes its STALE read to cache:
 |        SET cache:user:42 val = 100                                                       bal = 100
 v
                                  CRITICAL FAILURE:
            Database holds '200', but Cache permanently holds stale '100'!
```

#### Why Does This Occur?
This failure occurs because Step 2 (reading the database) and Step 5 (writing to the cache) in Request A are **not atomic**. Request B's complete write cycle (Steps 3 and 4) was executed entirely in the temporal gap between Request A reading from the database and writing back to the cache!

---

### 4.2 Practical Mitigations and Trade-offs

No single silver bullet eliminates distributed cache consistency anomalies. Production engineering employs five complementary defensive techniques:

#### 1. Order of Operations: Update Database First, Then Delete Cache
Never delete the cache *before* updating the database. If you delete the cache first:
1. Thread B deletes cache.
2. Thread A reads cache (miss), reads old database record, and writes old record into cache.
3. Thread B updates database.
Result: The window for the race condition extends across the entire duration of the database write! By updating the database first and deleting the cache second, the race window shrinks to the microseconds between the database commit and the cache deletion.

#### 2. Short Safety-Net TTLs
Even if an invalidation race occurs and stale data is written to the cache, a bounded TTL (e.g., 60 seconds to 5 minutes) guarantees that the inconsistency has an absolute expiration ceiling. The system exhibits **bounded staleness**.

#### 3. Versioned Cache Keys and Compare-and-Set (CAS)
Attach a monotonic entity version number or microsecond timestamp to the entity schema in PostgreSQL. When writing back to Redis on a cache miss, an application can utilize a Redis Lua script to perform a conditional compare-and-set:

```lua
-- Naive Versioned write-back script: updates ONLY if new_version > existing_version
local existingVersion = tonumber(redis.call('HGET', KEYS[1], '_version') or '0')
local newVersion = tonumber(ARGV[2])

if newVersion > existingVersion then
    redis.call('HSET', KEYS[1], '_data', ARGV[1], '_version', newVersion)
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
    return 1
else
    return 0 -- Stale write rejected!
end
```

> [!CAUTION]
> **The Critical Deleted-Key Flaw of Naive CAS:**
> The naive script above **fails** if Request B invalidated the cache by deleting the key (`DEL cache:user:42`)!
> When a key is deleted, Redis purges all version metadata. When delayed Request A arrives with stale Version 1, `redis.call('HGET', ...)` returns `nil`. The script evaluates `tonumber(nil or '0')`, yielding `0`. Because $1 > 0$, the script blindly writes stale Version 1 into the empty key!
>
> **Defensible Solutions in Production:**
> 1. **Version Watermark Tombstones:** When invalidating, the application or CDC worker does not simply delete the key; it records an invalidation watermark tombstone (e.g. `SET cache:user:42:ver 2 EX 30`). The write-back Lua script checks BOTH the payload hash and the watermark key (`KEYS[2]`). If `incoming_version < watermark_version`, the stale write is rejected even though the payload cache is empty!
> 2. **Leases / Generation Tokens:** When Request A experiences a cache miss, Redis issues a short-lived 64-bit generation lease token. If Request B invalidates the key, all outstanding leases for that key are invalidated. When Request A attempts to write back, Redis verifies that the lease is still valid; if invalidated, the write is aborted.
> 3. **CDC Log-Driven Populators:** Bypasses application write-back entirely. Readers only read from cache; all cache updates are performed by a single asynchronous consumer stream strictly ordered by the database WAL.

#### 4. The Transactional Outbox & Event-Driven Invalidation
In microservice architectures, cache invalidation should not rely on transient application thread memory. Instead, use the **Transactional Outbox Pattern** (from Module 5):
1. In the same ACID transaction that updates the business entity in PostgreSQL, insert an invalidation event record into an `outbox` table.
2. A reliable Change Data Capture (CDC) worker (such as Debezium or an asynchronous polling worker) reads the outbox log and publishes an invalidation event to Apache Kafka or RabbitMQ.
3. A dedicated cache consumer service receives the event and issues `DEL` commands to Redis with retry policies.

```
[ App Service ]
   |
   |-- 1. BEGIN TX
   |-- 2. UPDATE products SET price = 99 WHERE id = 42;
   |-- 3. INSERT INTO outbox (topic, payload) VALUES ('cache_evict', 'product:42');
   |-- 4. COMMIT TX (Atomic Local Invariant)
   v
[ PostgreSQL WAL / Outbox Table ]
   |
   | 5. CDC / Debezium Stream
   v
[ Kafka Message Bus ]
   |
   | 6. Reliable Event Consumption
   v
[ Cache Eviction Worker ] ----> 7. DEL cache:products:42 ----> [ Redis Cluster ]
```

> [!CAUTION]
> **Event-Driven Reality:** Publishing an invalidation event does not make invalidation instantaneous. Network transit, Kafka partition consumer lag, and worker scheduling introduce a latency window of 5ms to 500ms during which the cache remains temporarily stale. Systems requiring immediate read-after-write consistency must bypass the cache for the mutating user.

---

## Section 5 — TTL, Eviction, and Memory Management

### 5.1 TTL Expiration vs. Memory Eviction

Candidates frequently confuse key expiration with memory eviction. They are entirely separate mechanisms:
- **Expiration (Time-Based):** Keys are removed because their configured lifespan (TTL / Time-To-Live) has elapsed. The key is logically invalid regardless of whether the cache has 1 MB or 100 GB of free memory.
- **Eviction (Capacity-Based):** Keys are forcefully purged because the cache has consumed its allocated memory ceiling (`maxmemory`) and space must be reclaimed to accommodate incoming write commands.

---

### 5.2 How Redis Implements Expiration: Lazy vs. Active

Redis does not spawn a separate OS timer thread for every expiring key. Tracking millions of individual timers would consume prohibitive CPU and memory overhead. Instead, Redis combines two probabilistic mechanisms:

#### 1. Passive / Lazy Expiration
Whenever a client issues any read or write command accessing a key (`GET`, `HGET`, `EXISTS`), Redis inspects the key's metadata dictionary. If an expiration timestamp exists and `current_time >= expires_at`, Redis destroys the key immediately, reclaims memory, and returns `nil` to the client.

#### 2. Active / Periodic Expiration (Background Sampling)
If keys are written with a TTL but never accessed again, passive expiration would allow them to leak memory indefinitely. To prevent this, Redis implements an active expiration engine operating via two distinct cycles:
- **Slow Cycle (`serverCron`):** Executes 10 times per second (`hz 10`). It randomly samples **20 keys** (`ACTIVE_EXPIRE_CYCLE_LOOKUPS_PER_LOOP`) from the dictionary of expiring keys. If an expired key is found, it is deleted. If **more than 25%** (5 keys) of the sampled batch were expired, Redis immediately repeats the sampling in a loop. To preserve server responsiveness, this loop is capped by an adaptive CPU time limit (default 25% of the CPU cycle, or ~25 milliseconds) so it cannot starve client command execution.
- **Fast Cycle (`beforeSleep`):** Before the single-threaded event loop enters kernel polling sleep (`epoll_wait`/`kqueue`), Redis executes a short burst of active expiration. This fast cycle is capped by a strict 1-millisecond deadline to guarantee ultra-low latency for incoming requests.

---

### 5.3 Memory Limits and Eviction Policies

When Redis reaches its configured `maxmemory` threshold and a client issues a memory-allocating command (`SET`, `HSET`, `LPUSH`), Redis synchronously evaluates its configured `maxmemory-policy`:

| Eviction Policy | Candidates Evaluated | Eviction Algorithm | Behavior When All Keys Exhausted | Recommended Production Use Case |
| :--- | :--- | :--- | :--- | :--- |
| **`noeviction`** | None | None | Returns `OOM command not allowed` error on writes. | Datastore or rate limiter where data loss is unacceptable. |
| **`allkeys-lru`** | Every key in dataset | Approximated Least Recently Used (LRU) | Evicts oldest accessed keys across the entire database. | **Standard web cache:** Ideal when key access follows a power-law distribution. |
| **`volatile-lru`** | Only keys with TTL | Approximated Least Recently Used (LRU) | Evicts oldest accessed keys that have an explicit TTL set. | Shared Redis instances hosting both volatile cache and non-expiring configuration keys. |
| **`allkeys-lfu`** | Every key in dataset | Least Frequently Used (LFU) logarithmic counter | Evicts least frequently accessed keys across entire database. | Workloads prone to sequential scan pollution (protects true hot keys). |
| **`volatile-lfu`** | Only keys with TTL | Least Frequently Used (LFU) logarithmic counter | Evicts least frequently accessed keys that have an explicit TTL set. | Mixed datasets where only TTL-bearing keys should be frequency-evaluated. |
| **`allkeys-random`** | Every key in dataset | Uniform Random | Evicts completely random keys. | Rarely recommended; disregards temporal locality entirely. |
| **`volatile-random`** | Only keys with TTL | Uniform Random | Evicts random keys among those with a TTL. | Uniform workload testing. |
| **`volatile-ttl`** | Only keys with TTL | Shortest Remaining TTL | Evicts keys with the nearest upcoming expiration timestamp. | Workloads where shortest-lived entries represent least valuable data. |

#### The Mechanics of Approximated LRU vs. LFU
To save memory, Redis does **not** implement true academic LRU using a doubly linked list, which would require two 64-bit pointers (16 bytes) per key. Instead, Redis allocates a compact **24-bit field** inside each Redis Object (`robj`).

- **In LRU Mode:** These 24 bits store a timestamp clock (resolution in seconds). During eviction, Redis samples $N$ random keys (configured by `maxmemory-samples`, default 5), evaluates their 24-bit idle times against the current global clock, and evicts the key with the largest idle time. At `maxmemory-samples 10`, Redis LRU approximation closely tracks theoretical true LRU with negligible memory overhead.
- **In LFU Mode (Introduced in Redis 4.0):** Those 24 bits are partitioned into:
  - **16 bits (Last Decay Time - LDT):** Lower 16 bits of current minute timestamp, used to decay the counter over time.
  - **8 bits (Logistic Counter - LOG_C):** A Morris logarithmic counter (values 0–255). Rather than incrementing on every access, the counter increments probabilistically ($\frac{1}{\text{counter} \times \text{lfu-log-factor} + 1}$). Under inactivity, the counter decays based on `lfu-decay-time`, preventing historic bursts from permanently shielding obsolete keys from eviction.

---

### 5.4 Memory Budgeting: A Rigorous Mathematical Worked Example

An essential interview skill is calculating how much physical RAM an application requires in Redis.

#### Sizing Formula
$$\text{Memory}_{\text{total}} = N \times \left( \text{Len}(\text{Key}) + \text{Len}(\text{Value}) + \text{Overhead}_{\text{dict}} + \text{Overhead}_{\text{robj}} \right) \times \left( 1 + \text{Frag}_{\text{ratio}} \right)$$

Where:
- $N$: Number of cached entities.
- $\text{Overhead}_{\text{dict}}$: Hash table bucket and `dictEntry` node struct overhead (~24 to 32 bytes in 64-bit architecture).
- $\text{Overhead}_{\text{robj}}$: Redis Object wrapper struct (`type`, `encoding`, `lru` clock, `refcount`, `ptr`) (~16 bytes).
- $\text{Frag}_{\text{ratio}}$: Memory fragmentation ratio introduced by `jemalloc` allocating power-of-two memory chunks (typically 1.25 to 1.35).

#### The Worked Scenario
A backend engineering team must cache **10,000,000 active user authentication sessions** in Redis.
- Average Key: `session:uuid_v4` = 45 bytes (String).
- Average Value: Serialized JSON session payload = 350 bytes (String).
- Per-key Redis internal overhead: ~55 bytes (`robj` + `dictEntry` + SDS string header).
- Total raw bytes per key: $45 + 350 + 55 = 450\text{ bytes}$.
- Raw Dataset Size: $10,000,000 \times 450\text{ bytes} = 4,500,000,000\text{ bytes} \approx 4.50\text{ GB}$.
- Accounting for jemalloc fragmentation buffer ($\text{Frag}_{\text{ratio}} = 1.30$):
  $$4.50\text{ GB} \times 1.30 \approx 5.85\text{ GB}$$
- Operating System & Forking Headroom (for RDB snapshots / AOF rewrites without OOM): Add 25–30% safety headroom.
  $$\text{Recommended Server Provisioning} \ge 8.0\text{ GB RAM}$$

#### TTL Jitter: Eliminating Synchronized Expirations
If a batch job seeds 1,000,000 products into Redis at 02:00 AM with a fixed TTL of exactly 1 hour (`3600 seconds`), all 1,000,000 keys will expire simultaneously at 03:00:00 AM. The cache hit rate crashes to 0% in a single millisecond, unleashing an avalanche on the database.

**Solution:** Always inject pseudo-random **jitter** into cache TTLs:

$$\text{TTL}_{\text{actual}} = \text{TTL}_{\text{base}} \pm \text{Random}(0, \text{JitterWindow})$$

```java
// Java Implementation
long baseTtlSeconds = 3600;
long jitterSeconds = ThreadLocalRandom.current().nextLong(0, 300); // 0 to 5 minutes
long actualTtl = baseTtlSeconds + jitterSeconds;
redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(actualTtl));
```

---

## Section 6 — Redis Fundamentals

### 6.1 Redis as a Data Structure Server

Unlike simple key-value stores that treat values as opaque byte blobs (e.g., standard Memcached), Redis is an in-memory **data structures server**. The value associated with a key is a rich data structure that Redis manipulates natively via dedicated atomic operations:

```
+---------------------------------------------------------------------------------------+
|                                REDIS CORE DATA STRUCTURES                             |
+---------------------------------------------------------------------------------------+
| 1. STRINGS       | Binary-safe byte arrays up to 512MB. Text, numbers, JSON, bitmaps. |
| 2. HASHES        | Field-value pairs. Represents objects; update individual attributes|
| 3. LISTS         | Linked list / quicklist of strings. FIFO queues, capped feeds.     |
| 4. SETS          | Unordered unique strings. Fast membership checks, intersections.   |
| 5. SORTED SETS   | Unique strings ordered by floating-point score (SkipList + Dict).  |
| 6. BITMAPS       | Bit-level operations on strings. Daily active user tracking.       |
| 7. HYPERLOGLOG   | Probabilistic cardinality estimator. Millions of unique items/12KB|
| 8. GEOSPATIAL    | Radius and bounding-box queries based on Geohash on Sorted Sets.   |
| 9. STREAMS       | Append-only log with consumer groups (Kafka-like message bus).     |
+---------------------------------------------------------------------------------------+
```

---

### 6.2 Data Structure Selection Guide & Essential Commands

#### 1. Strings
- **When to Use:** Entire serialized JSON entities, HTML page fragments, atomic counters, binary buffers, distributed locks.
- **Key Commands:**
  - `SET key value [EX seconds] [NX|XX]`: Sets key. `NX` sets only if key does not exist; `XX` sets only if key exists.
  - `GET key`: Retrieves string value. Time complexity: $O(1)$.
  - `INCR key` / `INCRBY key amount`: Atomically increments integer string. Time complexity: $O(1)$.
  - `MGET key1 key2 ...`: Batched multi-key retrieval. Time complexity: $O(N)$.

#### 2. Hashes
- **When to Use:** Representing domain objects with multiple fields (e.g. `user:101` with fields `username`, `email`, `role`, `tier`). Allows updating a single field without deserializing, modifying, and re-serializing a massive JSON document.
- **Key Commands:**
  - `HSET key field value [field value ...]`: Sets fields in hash. Time complexity: $O(K)$.
  - `HGET key field`: Retrieves single field. Time complexity: $O(1)$.
  - `HGETALL key`: Retrieves all fields and values. Time complexity: $O(N)$ where $N$ is field count.
  - `HINCRBY key field increment`: Atomically increments a specific field counter.

#### 3. Lists
- **When to Use:** Activity feeds, task queues, recent event history capped by length.
- **Key Commands:**
  - `LPUSH key value`: Prepends element to list head. Time complexity: $O(1)$.
  - `RPOP key`: Removes and returns element from list tail. Time complexity: $O(1)$.
  - `BRPOP key timeout`: Blocking right pop (sleeps until an item arrives or timeout elapses; ideal for worker queues).
  - `LTRIM key start stop`: Truncates list to designated index range (e.g., maintaining only the last 100 events).

#### 4. Sets
- **When to Use:** Unordered unique collections, tagging systems, mutual friend calculations, IP allowlists.
- **Key Commands:**
  - `SADD key member [member ...]`: Adds unique members. Time complexity: $O(1)$ per member.
  - `SISMEMBER key member`: Tests membership. Time complexity: $O(1)$.
  - `SMEMBERS key`: Returns all members. Time complexity: $O(N)$. (Caution on large sets!).
  - `SINTER key1 key2`: Calculates mathematical set intersection across sets. Time complexity: $O(N \times M)$.

#### 5. Sorted Sets (ZSET)
- **When to Use:** Real-time leaderboards, priority queues, delayed execution queues, sliding-window rate limiters.
- **Key Commands:**
  - `ZADD key score member`: Inserts member with floating-point score. Time complexity: $O(\log N)$.
  - `ZRANGEBYSCORE key min max [WITHSCORES]`: Retrieves elements within score range. Time complexity: $O(\log N + M)$.
  - `ZREMRANGEBYSCORE key min max`: Deletes elements within score range. Time complexity: $O(\log N + M)$.
  - `ZCARD key`: Returns total number of members in sorted set. Time complexity: $O(1)$.

---

### 6.3 Atomicity: Single Commands vs. Lua vs. Multi-System Transactions

A foundational interview question explores the boundary of Redis atomicity:

1. **Individual Command Atomicity:** Because Redis executes commands sequentially on a single thread, every native command (such as `INCR`, `HSET`, `LPUSH`, or `SET NX`) is **completely atomic**. No other operation can interleave during its execution.
2. **Compound Operations via Lua Scripts:** A series of commands executed individually over network round-trips is **not** atomic. Another client can mutate keys between your `GET` and your `SET`. To execute compound read-modify-write logic atomically, an application bundles commands into a **Lua script** (`EVAL`). Redis guarantees that the entire Lua script executes from start to finish without any intervening command or script execution.
3. **Multi-System Reality:** An atomic Redis operation does **not** provide atomicity across an external PostgreSQL database. If your application updates Redis atomically via Lua and then fails before committing to PostgreSQL, the distributed system is left in an inconsistent state. Redis has no knowledge of JDBC or PostgreSQL transaction contexts.

---

### 6.4 Pipelining vs. Transactions (`MULTI` / `EXEC`)

- **Pipelining (Transport-Level Optimization):** Pipelining sends multiple commands over the network socket buffer simultaneously without waiting for individual replies. The server executes each command and bundles all responses in a single return packet. Pipelining reduces network RTT from $N \times \text{RTT}$ to $1 \times \text{RTT}$. However, pipelined commands **can interleave** with commands from other clients on the server!
- **Transactions (`MULTI` / `EXEC` / `WATCH`):** All commands between `MULTI` and `EXEC` are queued on the server and executed consecutively in a single atomic block without interleaving. Optimistic locking is achieved via `WATCH key`, which aborts the transaction (`EXEC` returns `nil`) if the watched key was modified by another client prior to execution.
- **Redis Transaction Limitation:** Redis transactions **do not support rollback** on runtime command execution errors. If command 3 in a 5-command transaction fails due to a data-type mismatch, commands 1, 2, 4, and 5 still commit to memory!

---

## Section 7 — Redis Internals and Operational Behavior

### 7.1 Engine Architecture: Why Redis is Fast

Redis achieves 100,000+ operations per second on a single CPU core due to three architectural design choices:

```
[ Incoming Network Sockets (Clients 1..N) ]
                   |
                   v
     [ I/O Multiplexing (epoll / kqueue) ]
                   |
     Non-blocking Socket Event Notification
                   |
                   v
   [ Single-Threaded Command Execution Loop ]
   - Zero Thread Context Switching
   - Zero Lock Contention / Mutex Overheads
   - CPU L1/L2 Cache Line Efficiency
                   |
                   v
     [ In-Memory Native Data Structures ]
     (SDS, Dict with Progressive Rehashing,
      Listpack, Quicklist, SkipList)
```

1. **Pure In-Memory Operations:** All data resides in physical DRAM. Data access requires zero disk head movements, zero flash page erase cycles, and zero kernel page faults.
2. **Non-Blocking I/O Multiplexing:** Redis utilizes modern kernel notification interfaces (`epoll` on Linux, `kqueue` on macOS/BSD). A single thread monitors thousands of client file descriptors for readability and writability without blocking on any single slow connection.
3. **Single-Threaded Execution Core:** All command execution runs on a single main thread. This completely eliminates CPU thread context switching, thread synchronization locks (`pthread_mutex`, synchronized blocks), deadlocks, and volatile memory barriers.
4. **I/O Threading (Redis 6.0+):** While command execution remains strictly single-threaded, Redis 6+ offloads network socket read/write buffer parsing and serialization to background helper threads (`io-threads`), scaling network throughput across multi-core servers.

---

### 7.2 Native Data Structures Under the Hood

- **Simple Dynamic Strings (SDS):** Instead of standard null-terminated C strings, Redis wraps byte arrays in an SDS struct containing explicit `len`, `alloc`, and `flags` fields. This provides $O(1)$ length lookup, prevents buffer overflows, and allows binary-safe storage of arbitrary byte streams (e.g. gzip buffers, protobufs).
- **Dict (Hash Table) with Progressive Rehashing:** When a hash table needs to resize, Redis does not rehash millions of keys in one massive blocking operation. Instead, it maintains two internal hash tables (`ht[0]` and `ht[1]`). During normal command execution, every incoming request migrates a small bucket index from `ht[0]` to `ht[1]`, spreading rehashing CPU costs across thousands of operations without latency spikes.
- **SkipLists:** Sorted Sets are implemented using a probabilistic balanced hierarchy of linked lists (SkipList) paired with a Hash table. SkipLists provide $O(\log N)$ search, insertion, and deletion complexity matching AVL/Red-Black trees but require significantly simpler rebalancing algorithms and execute with high CPU cache locality.

---

### 7.3 Dangerous Commands and Production Operational Traps

Because Redis command execution is single-threaded, any command with $O(N)$ computational complexity blocks the **entire server**. While an expensive command runs, all other clients across the organization hang:

```
+---------------------------------------------------------------------------------------+
| DANGEROUS COMMAND: KEYS *                                                             |
| Complexity: O(N) where N is total keys in database.                                   |
| Impact: Scans entire keyspace dictionary. In a 10M key database, freezes Redis for    |
|         3 to 10 seconds! Triggers cluster node failovers, client timeouts, crashes.   |
| PRODUCTION FIX: Use SCAN (cursor-based, non-blocking chunked iteration).              |
+---------------------------------------------------------------------------------------+
| DANGEROUS COMMAND: HGETALL / SMEMBERS on Large Collections                            |
| Complexity: O(N) where N is elements in collection.                                   |
| Impact: If a hash contains 500,000 fields, serializing the RESP packet blocks the     |
|         event loop for hundreds of milliseconds.                                      |
| PRODUCTION FIX: Use HSCAN, SSCAN, or store bounded sub-collections.                   |
+---------------------------------------------------------------------------------------+
| DANGEROUS COMMAND: FLUSHALL / FLUSHDB (Synchronous)                                   |
| Complexity: O(N) memory deallocation.                                                 |
| Impact: Reclaiming gigabytes of memory pages synchronously freezes the main thread.  |
| PRODUCTION FIX: Use FLUSHALL ASYNC (deallocates memory in background thread).         |
+---------------------------------------------------------------------------------------+
```

#### Big Keys, Hot Keys, and SCAN Mechanics
- **SCAN Operational Invariants:** `SCAN cursor [MATCH pattern] [COUNT count]` provides non-blocking chunked iteration across the keyspace:
  - `COUNT` is a work-effort **hint**, not a guarantee of returned element count. A single `SCAN` invocation may return 0 keys while returning a non-zero cursor!
  - Iteration is complete only when the returned cursor equals `'0'`.
  - **Progressive Rehashing & Duplicate Keys:** Because hash tables dynamically expand and shrink, keys can be returned multiple times across cursor steps. Client applications must maintain a deduplication set if unique keys are required.
- **Big Key:** A single key containing a massive value (e.g. a 50MB string or a List with 2,000,000 elements). Big keys cause severe memory fragmentation, network interface saturation, and slow deallocation on eviction. Identify via `redis-cli --bigkeys`.
- **Hot Key:** A key that receives extreme query frequency (e.g. 50,000 QPS on `cache:homepage:featured`). Because Redis partitions clusters by key hash slots, a single hot key concentrates all traffic onto one physical CPU core of one Redis shard. Identify via `redis-cli --hotkeys` (requires LFU maxmemory-policy).
  - **Mitigation:** Spread hot keys by appending random partition suffixes (`featured:1` ... `featured:8`) and having clients read from random shards, or cache hot keys locally in an L1 in-process cache.

---

### 7.4 Redis Persistence: RDB vs. AOF

Redis can operate as a pure in-memory cache with persistence disabled, or as a semi-durable store using two persistence engines:

```
1. RDB (SNAPSHOT)
   At configured intervals (e.g. every 15 min), Redis calls fork() to create a child
   process. The child process writes point-in-time memory pages to an 'rdb' file via
   OS Copy-On-Write (COW).
   - Pros: Compact single-file backup; maximum server restart speed.
   - Cons: Data loss between snapshots (up to 15 minutes of mutations lost on crash).
   - Trap: fork() on massive 30GB+ memory heaps can cause momentary OS page table
     cloning latency spikes (~50-200ms).

2. AOF (APPEND-ONLY FILE)
   Logs every write command to disk in RESP format (Redis 7+ uses Multi-Part AOF).
   - Durability Modes:
     * appendfsync always: fsync() after every write (ultra-safe, severe disk I/O hit).
     * appendfsync everysec: fsync() once per second in background thread (Standard).
     * appendfsync no: OS controls buffer flush.
   - Pros: Maximum 1 to 2 seconds of data loss under 'everysec'.
   - Cons: Larger file size; slower restart recovery. Requires background rewrite
     (BGREWRITEAOF) to compact historical command logs.
```

> [!WARNING]
> **Semi-Durability vs. Relational ACID Durability:**
> Redis persistence and asynchronous replication do **not** equal relational ACID durability. Under `appendfsync everysec`, if disk I/O stalls, Redis can delay writes and lose up to 2 seconds of mutations during an OS crash. Furthermore, standard Redis cluster replication is **asynchronous**; a failover to a replica after an abrupt primary crash will lose any writes that were not yet transmitted over the wire. Redis is an ultra-fast in-memory cache and data-structure server, not a distributed ACID datastore.

---

## Section 8 — Cache Stampede, Thundering Herd, and Hot Keys

### 8.1 Distinct Failure Mode Taxonomy

Engineers often loosely use the terms "stampede," "avalanche," and "penetration" as synonyms. In technical interviews, confusing them demonstrates a lack of architectural precision:

```
+-------------------+------------------------------------------------------------------+
| FAILURE MODE      | EXACT DEFINITION AND MECHANICS                                   |
+-------------------+------------------------------------------------------------------+
| Cache Stampede    | A SINGLE hot cached key expires. Thousands of concurrent requests|
| (Thundering Herd) | experience a cache miss simultaneously and all rush to query the |
|                   | database to recompute the value, crushing the DB connection pool.|
+-------------------+------------------------------------------------------------------+
| Cache Avalanche   | A MASSIVE SWATH of diverse keys expire at the exact same second   |
|                   | (or the cache cluster crashes), shifting 100% of broad read      |
|                   | traffic onto the database simultaneously.                        |
+-------------------+------------------------------------------------------------------+
| Cache Penetration | Continuous requests arrive for NON-EXISTENT keys that are absent |
|                   | from both cache and database. Every request bypasses the cache   |
|                   | and hits the database, which returns empty results.              |
+-------------------+------------------------------------------------------------------+
| Hot Key           | A single valid key receives disproportionate traffic volume      |
| Saturation        | exceeding the network or CPU capacity of its hosting Redis node. |
+-------------------+------------------------------------------------------------------+
```

---

### 8.2 Stampede Defense 1: Single-Flight Request Coalescing (Mutex Locking)

When a hot cache entry expires, the application must ensure that **only one** worker thread queries the database and refreshes the cache, while all other concurrent requests wait for that single result to complete.

```
Request 1 (0.00ms) --[ Cache Miss ]--> Acquired Mutex -> Querying DB -> Populating Cache --+
Request 2 (0.01ms) --[ Cache Miss ]--> Wait for Mutex -------------------------------------+--> Shared Result
Request 3 (0.02ms) --[ Cache Miss ]--> Wait for Mutex -------------------------------------+    Returned!
Request 4 (0.03ms) --[ Cache Miss ]--> Wait for Mutex -------------------------------------+
```

In Java, this is implemented cleanly via `CompletableFuture` / `ConcurrentHashMap.computeIfAbsent`:
```java
public class SingleFlightCoordinator<K, V> {
    private final ConcurrentMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();

    public V execute(K key, Function<K, V> loader) throws ExecutionException, InterruptedException {
        while (true) {
            CompletableFuture<V> existing = inFlight.get(key);
            if (existing != null) {
                try {
                    return existing.get();
                } catch (CancellationException | ExecutionException e) {
                    inFlight.remove(key, existing); // Stale failure cleanup
                    continue;
                }
            }

            CompletableFuture<V> newFuture = new CompletableFuture<>();
            CompletableFuture<V> prev = inFlight.putIfAbsent(key, newFuture);
            if (prev != null) {
                try {
                    return prev.get();
                } catch (CancellationException | ExecutionException e) {
                    inFlight.remove(key, prev);
                    continue;
                }
            }

            try {
                V result = loader.apply(key);
                newFuture.complete(result);
                return result;
            } catch (Throwable t) {
                newFuture.completeExceptionally(t);
                throw new ExecutionException(t);
            } finally {
                // Invariant: 2-argument CAS remove only removes THIS exact future instance
                inFlight.remove(key, newFuture);
            }
        }
    }
}
```

> [!NOTE]
> **In-Process vs. Fleet-Wide Distributed Single-Flight:**
> The `SingleFlight` constructs above are **in-process (pod-local)**. If your application runs across 10 Kubernetes pods, each pod will collapse its local concurrent misses into 1 query, resulting in 10 database queries (one per pod). Distributed single-flight across an entire fleet requires distributed coordination (e.g., Redis distributed mutex with leases, or NGINX edge request coalescing via `proxy_cache_use_stale updating`).

In Node.js, asynchronous Promise coalescing operates identically using an in-flight Promise registry with exact-instance cleanup:
```typescript
class SingleFlightCoalescer {
  private inFlight = new Map<string, Promise<any>>();

  execute<T>(key: string, loader: () => Promise<T>): Promise<T> {
    const existing = this.inFlight.get(key);
    if (existing) {
      return existing as Promise<T>; // Return shared in-flight Promise
    }

    let promise: Promise<T>;
    try {
      // Promise.resolve().then(...) safely traps both synchronous throws and async rejections
      const execution = Promise.resolve().then(() => loader());
      promise = execution.finally(() => {
        // Invariant: Only delete if the map still holds THIS exact in-flight promise instance!
        if (this.inFlight.get(key) === promise) {
          this.inFlight.delete(key);
        }
      });
      this.inFlight.set(key, promise);
      return promise;
    } catch (syncErr) {
      if (this.inFlight.get(key) === promise) {
        this.inFlight.delete(key);
      }
      throw syncErr;
    }
  }
}
```

---

### 8.3 Stampede Defense 2: Probabilistic Early Refresh (The XFetch Algorithm)

Published in academic literature (Vattani et al., *Optimal Probabilistic Cache Synchronization*), the **XFetch / Probabilistic Early Refresh (PER)** algorithm eliminates stampedes without locks or coordination bottlenecks.

#### The Mathematical Formula
Instead of waiting for a key to expire at $t = \text{TTL}$, an application reading the cache tests whether it should probabilistically refresh the key in the background:

$$\text{Recompute Early if: } -\beta \times \delta \times \ln(\text{Random}(0, 1)) > \text{TTL}_{\text{remaining}}$$

Where:
- $\beta > 0$: Aggressiveness parameter (typically set to $1.0$).
- $\delta$: The computation duration (in seconds) required to query the database and construct the entity, recorded during the previous computation.
- $\text{Random}(0, 1)$: A uniform floating-point random number between 0 and 1.
- $\text{TTL}_{\text{remaining}}$: Seconds remaining until hard expiration.

```
Probability of Refresh
  1.0 +                                             * (TTL = 0s: 100% chance)
      |                                           *
      |                                         *
  0.5 |                                       *
      |                                     *
  0.1 |                             * * * *
  0.0 +-----------------------------+---------------+
      20s                           5s              0s
               Remaining TTL (Approaching Expiration)
```

As the key nears expiration, the probability of any given read request triggering an asynchronous background refresh climbs exponentially. Under high traffic (e.g. 10,000 QPS), one single client will probabilistically trigger the refresh a few seconds *before* the key expires, repopulating the cache smoothly while all other clients continue reading valid cached data.

> [!WARNING]
> **XFetch Operational Limitations:**
> While XFetch dramatically reduces stampede risk under steady high traffic, it does **not** provide a 100% guarantee that every key is refreshed before hard expiration. If traffic to the key is sparse right before expiration, if computation time $\delta$ suddenly spikes beyond the remaining TTL, or if the background worker encounters an exception, the key will expire and callers will experience a hard miss.

---

## Section 9 — Cache Penetration and Negative Caching

### 9.1 The Mechanics of Cache Penetration

Cache penetration occurs when an application receives sustained traffic querying IDs that **do not exist** in either the cache or the database:

```
[ Attacker / Bad Actor ]
   |
   | Issues 10,000 QPS: GET /users/uuid-nonexistent-9999
   v
[ Application ]
   |
   | 1. GET cache:users:uuid-nonexistent-9999 -> MISS
   v
[ Redis Cache ]
   |
   | 2. SELECT * FROM users WHERE id = 'uuid-nonexistent-9999'
   v
[ PostgreSQL Database ] -> Returns EMPTY (No rows found)
   |
   | 3. Application does NOT populate cache (because result was null!)
   v
[ Application ] -> Returns HTTP 404 to Client
```

Because null results are traditionally not cached, every single query penetrates directly to the database. An attacker generating randomized non-existent UUIDs can force 100% of traffic to execute expensive B-Tree lookups on disk, exhausting database connection pools.

---

### 9.2 Defense 1: Negative Caching with Short TTL

The simplest defense is **Negative Caching**. When the database reports that a requested entity does not exist, the application caches a lightweight sentinel marker:

```
SETEX cache:users:uuid-nonexistent-9999 30 "__NULL_SENTINEL__"
```

Subsequent requests for that non-existent ID hit the cache, recognize the sentinel marker, and return HTTP 404 immediately without touching the database.
- **Critical Requirement:** The negative cache TTL must be **short** (e.g. 30 to 60 seconds). If a user creates an account with that ID moments later, a long negative TTL would cause them to receive 404s until the entry expires.

---

### 9.3 Defense 2: Bloom Filters

A **Bloom Filter** is a space-efficient probabilistic data structure used to test whether an element is a member of a set. It is positioned as an architectural guard in front of the cache:

```
[ Incoming Request for ID: X ]
              |
              v
     [ BLOOM FILTER ]
       /            \
  "Definitely      "Might Be
     NOT             Present"
   Present"             |
      |                 v
      |         [ Check Cache / DB ]
      v
 Return 404
 Immediately!
(Zero DB Access)
```

#### Mathematical Invariants of Bloom Filters
1. **Zero False Negatives (Under Strict Assumptions):** If the Bloom filter returns `false`, the entity is claimed not to exist.
2. **False Positives Possible:** If the Bloom filter returns `true`, the entity **might** exist. The application proceeds to check the cache and database.

> [!WARNING]
> **Production Preconditions for Zero False Negatives:**
> The mathematical guarantee of zero false negatives holds **only** under three strict operational preconditions:
> 1. **Complete Initial Population:** Every existing entity in PostgreSQL was successfully hashed and registered in the bit array.
> 2. **No Deletions:** Standard Bloom filters do **not** support deletions. If a user deletes their account from PostgreSQL, its bits cannot be cleared without corrupting other entries. Over time, deleted entities accumulate as permanent false positives. (Solutions: Counting Bloom Filters or periodic background rebuilds).
> 3. **Synchronized Write Path:** If an entity is created in PostgreSQL but the server crashes before adding it to the Bloom filter (or if replication lag delays update), the Bloom filter will return `false` for a valid existing entity! This produces a **catastrophic False Negative** where legitimate users are rejected with 404s. Therefore, a Bloom filter is an optimization guard, not an authoritative source of truth.

#### Sizing a Bloom Filter
Given $n$ expected keys and a target false-positive probability $p$:
- **Required Bits ($m$):**
  $$m = -\frac{n \times \ln(p)}{(\ln 2)^2} \approx -1.44 \times n \times \log_2(p)$$
- **Optimal Number of Hash Functions ($k$):**
  $$k = \frac{m}{n} \times \ln 2 \approx 0.7 \times \frac{m}{n}$$

For $10,000,000$ users with a target false-positive rate of $1\%$ ($p = 0.01$):
- $m \approx 95,850,583\text{ bits} \approx 11.42\text{ MB of RAM}$.
- $k \approx 7\text{ hash functions}$.

By allocating just **11.4 MB of memory** in Redis (via RedisBloom `BF.ADD` / `BF.EXISTS`), the system can filter out 99% of all malicious penetration attacks before they touch the relational database!

---

## Section 10 — Redis for Rate Limiting and Coordination

### 10.1 Redis Rate Limiting Algorithms: Implementation Mechanics

Module 06 established the security threat models of API rate limiting. Here, we examine the physical data-structure mechanics inside Redis:

#### 1. Fixed-Window Counter
- **Data Structure:** Redis String.
- **The Crash Hazard of Naive `INCR` + `EXPIRE`:** In naive implementations, an app executes `INCR key`. If the count is 1, it issues `EXPIRE key 60`. If the app crashes, is killed, or suffers a network drop between `INCR` and `EXPIRE`, the key is created with **NO TTL**! It permanently lives in Redis without expiring, locking out the user indefinitely!
- **Atomic Lua Solution:**
```lua
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
if current <= tonumber(ARGV[1]) then
    return {1, current} -- Allowed
else
    return {0, current} -- Denied
end
```
- **Trade-off:** Minimal memory ($O(1)$) and ultra-fast. Vulnerable to **boundary burst doubling** (a user sends 100 requests at 17:30:59 and 100 requests at 17:31:00, successfully executing 200 requests in 2 seconds).

#### 2. Sliding-Window Log via Sorted Sets (ZSET)
- **Data Structure:** Redis Sorted Set (`ZSET`).
- **Mechanics:** Scores are millisecond timestamps. Elements represent individual requests.
- **The Millisecond Collision Flaw & The Fix:** If elements are stored using the timestamp alone (`ZADD key now now`), multiple requests arriving within the exact same millisecond overwrite each other's score instead of adding new members, allowing limit bypass!
  - **The Fix:** The member string must be a composite nonce: `now .. ':' .. requestId`.
- **Lua Script Execution:**
```lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local requestId = ARGV[4]

-- 1. Remove expired timestamps older than sliding window
redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)

-- 2. Check current cardinality
local currentCount = redis.call('ZCARD', key)

if currentCount < limit then
    -- 3. Add unique composite member (now:requestId)
    redis.call('ZADD', key, now, now .. ':' .. requestId)
    redis.call('PEXPIRE', key, window + 1000)
    return {1, limit - currentCount - 1} -- Allowed
else
    return {0, 0} -- Denied
end
```
- **Trade-off:** Mathematically perfect sliding window. High memory consumption: storing 1,000 requests in a ZSET consumes ~60KB per user.

---

### 10.2 Distributed Locking: Leases, Pauses, and Fencing Tokens

Redis is frequently employed for lightweight distributed mutual exclusion. However, a distributed lock over network-attached volatile memory is subject to deep physical limitations:

#### Safe Lock Acquisition and Release
```bash
# Acquire Lock: Set key only if not exists (NX), with 30,000ms millisecond lease (PX)
SET lock:order:8821 <unique_random_owner_token> NX PX 30000
```

Upon task completion, the lock must be released **only if the caller still owns it**:
```lua
-- Safe Lock Release Lua Script
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0 -- Lock was lost to lease expiration or stolen; do NOT delete!
end
```

#### The Process Pause Flaw and Fencing Tokens
Martin Kleppmann (author of *Designing Data-Intensive Applications*) famously proved that a distributed lock without storage-level fencing cannot guarantee mutual exclusion in the presence of process pauses:

```
[ Client 1 ]                    [ Redis Lock ]                    [ Storage / DB ]
     |                                |                                  |
     |-- 1. Acquire Lock (Lease 30s) -> |                                  |
     |   [Lock Granted]               |                                  |
     |                                |                                  |
     |=== [ JVM STW GC Pause / Network Stall for 35 seconds! ] ===       |
     |                                |                                  |
     |   [Lease Expires at t=30s]     |                                  |
     |                                | <-- 2. Client 2 Acquires Lock    |
     |                                |        [Lock Granted to Client 2]|
     |                                |                                  |-- 3. Client 2 Writes DB
     |                                |                                  |      [Write Succeeded!]
     |                                |                                  |
     |-- 4. Wakes up from GC! --------|--------------------------------->|-- 5. Client 1 Writes DB!
     |   (Believes it still holds lock!)                                 |      CRITICAL DATA CORRUPTION!
```

#### The Solution: Monotonic Fencing Tokens
A distributed lock service must issue a strictly increasing **fencing token** (e.g. token = 101, 102, 103) upon each acquisition:
- Client 2 writes with `token = 102`. Database updates its `max_token = 102`.
- Client 1 wakes up from GC and submits write with `token = 101`.
- Database rejects Client 1's write: `ERROR: Stale fencing token 101 < 102`!

> [!IMPORTANT]
> **How Fencing Tokens are Obtained and Enforced:**
> 1. **Redis Does Not Automatically Issue Fencing Tokens:** Standard `SET key token NX PX ttl` only provides mutual exclusion and a lease; it does not generate monotonic tokens. To obtain a fencing token in Redis, acquisition must atomically increment a dedicated monotonic counter key (via Lua script) alongside `SET NX`. In consensus-backed systems like Apache ZooKeeper or etcd, sequential znode versions (`zxid`) or `mod_revision` counters serve as monotonic fencing tokens.
> 2. **Enforcement Lies in the Storage Tier, Not the Lock:** A distributed lock service *cannot* prevent a stalled client from waking up and issuing a network write. Correctness is achieved **only** because the protected downstream storage tier (e.g., PostgreSQL `UPDATE accounts SET balance = $1, fencing_token = $2 WHERE id = $3 AND fencing_token < $2`) evaluates and rejects writes bearing obsolete tokens!

---

## Section 11 — Caching with Spring Boot

### 11.1 The Spring Cache Abstraction Architecture

Spring Framework decouples caching logic from physical cache engines via two core interfaces:
1. `org.springframework.cache.CacheManager`: Registry and lifecycle manager of named cache instances.
2. `org.springframework.cache.Cache`: Contract defining basic storage operations (`get`, `put`, `evict`).

Supported providers include `ConcurrentMapCacheManager` (testing), `CaffeineCacheManager` (in-memory), and `RedisCacheManager` (`spring-data-redis`).

---

### 11.2 Core Annotations & SpEL Attributes

```java
@Service
public class OrderService {

    // 1. @Cacheable: Checks cache; if miss, executes method and stores result
    @Cacheable(
        value = "orders",
        key = "#orderId",
        unless = "#result == null",                // Do not cache null results!
        condition = "#orderId.length() > 5",       // Filter invalid keys
        sync = true                                // Enables thread synchronization (stampede defense)
    )
    public OrderDto getOrder(String orderId) {
        return loadFromDatabase(orderId);
    }

    // 2. @CachePut: Always executes method, updating the cache with return value
    @CachePut(value = "orders", key = "#result.id")
    public OrderDto updateOrder(UpdateOrderRequest req) {
        return persistUpdate(req);
    }

    // 3. @CacheEvict: Evicts entry from cache upon completion
    @CacheEvict(
        value = "orders",
        key = "#orderId",
        beforeInvocation = false                   // Evict ONLY if method completes without exception!
    )
    public void cancelOrder(String orderId) {
        markCancelledInDb(orderId);
    }
}
```

> [!NOTE]
> **The Scope of `@Cacheable(sync = true)`:**
> Setting `sync = true` activates **in-process (JVM-local) thread synchronization**. If 20 concurrent threads inside the same Spring Boot instance query the same missing key `#orderId`, only 1 thread executes `loadFromDatabase(orderId)` while the other 19 wait for the result.
> **Critical Distributed Limitation:** `sync = true` does **NOT** synchronize across multiple application instances! If your service runs across 10 Kubernetes pods and a hot key expires, each of the 10 pods will execute a database query (resulting in 10 simultaneous queries to the database, one per pod). Achieving fleet-wide distributed single-flight requires distributed coordination (such as a Redis distributed lock with leases, or reverse-proxy coalescing via NGINX `proxy_cache_use_stale updating`).

---

### 11.3 The Spring Cache Proxy Trap

Just like `@Transactional` (Module 05), Spring Cache functions via **Spring AOP Dynamic Proxies**.

```
[ External Caller ] ---> [ Spring Proxy Object (Interprets @Cacheable) ] ---> [ Target OrderService Bean ]
```

If method `A()` inside `OrderService` directly invokes `@Cacheable` method `B()` within the same class (`this.B()`), the call bypasses the Spring proxy! **No caching interceptor executes**, and the database is queried directly every time.
- **Solution:** Inject the self-bean, extract caching to a dedicated service layer, or configure AspectJ compile-time weaving.

---

## Section 12 — Caching with Node.js and Express

### 12.1 In-Process vs. Distributed Caching in Node.js

Node.js executes application logic on a single-threaded V8 event loop.
- **In-Process Cache:** Storing items in a JavaScript `Map` is synchronous and instantaneous (~20ns). However, Node.js process memory is capped by `--max-old-space-size` (default ~2GB to 4GB). Exceeding this limit causes V8 to crash with `FATAL ERROR: Ineffective mark-compacts near heap limit Allocation failed - JavaScript heap out of memory`.
- **Process Isolation:** In production, Node.js applications run across multiple cluster worker processes (via PM2) or multiple Docker containers. A local `Map` in Worker 1 is completely invisible to Worker 2. Distributed caching via `ioredis` is mandatory for shared cluster state.

---

### 12.2 Production Resilience: Timeouts and Circuit Breaking

In Node.js, if a Redis query hangs due to a network partition, an unhandled Promise can tie up application resources. A resilient cache wrapper must enforce strict timeouts and graceful fallbacks:

```typescript
import Redis from 'ioredis';

export class ResilientCache {
  private redis: Redis;

  constructor() {
    this.redis = new Redis({
      host: process.env.REDIS_HOST || '127.0.0.1',
      port: 6379,
      connectTimeout: 2000,
      maxRetriesPerRequest: 1, // Fail fast!
      enableReadyCheck: false
    });
  }

  async getWithFallback<T>(
    key: string,
    dbLoader: () => Promise<T>,
    ttlSeconds: number = 300,
    timeoutMs: number = 50
  ): Promise<T> {
    // 1. Race cache read against strict deadline with timer cleanup
    try {
      let timerId: NodeJS.Timeout;
      const timeoutPromise = new Promise<null>((_, reject) => {
        timerId = setTimeout(() => reject(new Error('Cache timeout')), timeoutMs);
      });

      const raw = await Promise.race([this.redis.get(key), timeoutPromise]).finally(() => {
        clearTimeout(timerId); // Prevent timer handle leaks in event loop!
      });

      if (raw !== null) {
        return JSON.parse(raw) as T; // Cache Hit
      }
    } catch (err) {
      // Defensive: Cache failure must NEVER block the user request!
      console.warn(`Cache read bypassed for ${key}:`, (err as Error).message);
    }

    // 2. Fallback to primary database
    // Warning: High-traffic clusters must evaluate circuit breaker state before database query!
    const freshData = await dbLoader();

    // 3. Best-effort async write-back (unawaited)
    if (freshData !== null && freshData !== undefined) {
      this.redis.set(key, JSON.stringify(freshData), 'EX', ttlSeconds)
        .catch(err => console.error(`Async cache write failed for ${key}:`, err));
    }

    return freshData;
  }
}
```

> [!WARNING]
> **Two Critical Timeout Realities in Node.js:**
> 1. **`Promise.race()` Does NOT Cancel Background Operations:** In JavaScript, promises are not cancellable. When `timeoutPromise` rejects, the underlying `this.redis.get(key)` network socket read **continues running in the background** until socket closure or data arrival. A caller-side timeout gives latency control to the client, but does *not* relieve Redis or the network of work!
> 2. **Fallback Overload Hazard & Circuit Breaking:** If Redis latency climbs under heavy load (e.g. 20,000 QPS) and requests begin timing out after 50ms, naively falling back to PostgreSQL will suddenly dump 20,000 QPS onto the relational database. Because database queries are 10x–50x slower, database connection pools (HikariCP / pg-pool) will instantly saturate, thread starvation will cascade across the cluster, and the entire API will experience a total outage. Production systems **must** pair cache timeouts with a **Circuit Breaker** (e.g. tripping after consecutive timeouts to fail-fast or return cached stale data, shielding the database).

---

## Section 13 — HTTP Caching

### 13.1 RFC 9111 Directives & Semantics

HTTP caching (governed by RFC 9111, obsoleting RFC 7234) allows origin servers to instruct browsers, corporate forward proxies, and global CDNs on how to cache HTTP representations.

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: public, max-age=3600, s-maxage=86400, stale-while-revalidate=60
ETag: "9b8a7c2e1f4"
Vary: Accept-Encoding, Origin
```

#### Core Directives Decoded
- **`max-age=<seconds>`:** Freshness duration for client browsers. The browser will serve the cached copy from disk/RAM without issuing a network request for the specified duration.
- **`s-maxage=<seconds>`:** Freshness duration specifically for **shared / intermediary caches** (CDNs, reverse proxies). Overrides `max-age` for CDNs while keeping browser caching short.
- **`no-cache`:** **CRITICAL INTERVIEW DISTINCTION:** `no-cache` does **NOT** mean "do not cache"! It means: *"You may store this response, but you MUST revalidate with the origin server (using ETag or If-Modified-Since) before serving it to a client."*
- **`no-store`:** Under RFC 9111 Section 5.2.2.5, `no-store` instructs that the cache **MUST NOT store any part of this response or the request that produced it in any storage tier** (neither volatile RAM nor persistent disk). Every request must round-trip to the origin. Mandatory for sensitive personal data, banking credentials, and cryptographic tokens.
- **`public`:** Indicates that any intermediary (CDN, proxy) may cache the response.
- **`private`:** Indicates that the response is intended solely for a single user (the browser). Shared CDNs must **never** store a `private` response.
- **`stale-while-revalidate=<seconds>`:** Instructs the cache to immediately return a stale response if one is available, while asynchronously dispatching a background request to the origin to fetch fresh data.

---

### 13.2 Validation: ETags and Conditional Requests

When a cached response becomes stale, the client can issue a **conditional request** to verify whether the representation has changed:

```
CLIENT                                                           ORIGIN SERVER
  |                                                                    |
  | 1. Initial Request: GET /api/v1/articles/99                        |
  |------------------------------------------------------------------->|
  |                                                                    |
  | 2. Response: 200 OK                                                |
  |    ETag: "v5_hash_9a8b"                                            |
  |    Cache-Control: max-age=300                                      |
  |<-------------------------------------------------------------------|
  |                                                                    |
  | === [ 300 Seconds Elapse: Representation Becomes Stale ] ===       |
  |                                                                    |
  | 3. Conditional Revalidation:                                       |
  |    GET /api/v1/articles/99                                         |
  |    If-None-Match: "v5_hash_9a8b"                                   |
  |------------------------------------------------------------------->|
  |                                                                    |
  | 4. Server verifies hash: Entity unchanged!                         |
  |    HTTP/1.1 304 Not Modified                                       |
  |    (Empty Body: Zero Payload Bytes Transmitted!)                   |
  |<-------------------------------------------------------------------|
```

- **Strong ETag (`"abc"`):** Guarantees byte-for-byte binary identity of the representation. Mandatory for byte-range requests (`206 Partial Content`) and concurrency guards (`If-Match`).
- **Weak ETag (`W/"abc"`):** Guarantees semantic equivalence (RFC 9110 Section 8.8.1). Valid for full-body `If-None-Match` revalidations (GET), but RFC 9110 Section 13.1.2 explicitly forbids weak ETags for byte-range requests (`Range: bytes=...`) or optimistic locking updates (`If-Match`).

---

## Section 14 — Database Performance and Caching Trade-Offs

### 14.1 The Cardinal Rule: Optimize Before Caching

> [!CAUTION]
> **Architectural Anti-Pattern:** Slapping a cache in front of a slow, unindexed database query is architectural malpractice. It hides query inefficiency during low traffic, only to cause catastrophic cascading database failure the instant the cache expires, restarts, or thrashes.

Before introducing a cache, an engineer must execute this **Eight-Step Optimization Sequence**:

```
[ STEP 1: MEASURE ]
Profile slow query logs (pg_stat_statements, MySQL slow query log).
       |
       v
[ STEP 2: INSPECT EXECUTION PLAN ]
Run EXPLAIN (ANALYZE, BUFFERS). Identify Sequential Scans, Hash Joins, Spills to Disk.
       |
       v
[ STEP 3: ELIMINATE REDUNDANT WORK ]
Stop SELECT *; project only needed columns. Eliminate redundant table joins.
       |
       v
[ STEP 4: ADD SARGABLE COMPOSITE INDEXES ]
Transform O(N) sequential table scans into O(log N) B-Tree index range scans.
       |
       v
[ STEP 5: FIX N+1 QUERY DEFECTS ]
Replace loops of single-row queries with batch IN (...) lookups or JOIN FETCH.
       |
       v
[ STEP 6: EVALUATE CONNECTION POOLING ]
Tune HikariCP pool sizing to match CPU cores and avoid thread contention.
       |
       v
[ STEP 7: JUSTIFY CACHING ]
Is the query still CPU/IO bottlenecked after indexing? Does workload exhibit temporal locality?
If YES -> Implement Cache-Aside with TTL & Invalidation.
       |
       v
[ STEP 8: RE-MEASURE ]
Verify p95/p99 latency improvements and database CPU offload under load testing.
```

---

### 14.2 Database Buffer Cache vs. Application Cache

Relational databases already possess sophisticated in-memory caching systems:
- **PostgreSQL `shared_buffers`:** Caches $8\text{KB}$ disk pages directly in memory.
- **MySQL InnoDB Buffer Pool:** Caches $16\text{KB}$ data pages and index pages using an adaptive midpoint insertion LRU algorithm.

If a database query utilizes a proper B-Tree index and the active index and table pages fit entirely within `shared_buffers`, PostgreSQL executes the lookup in **under 1 millisecond** directly in memory. In many cases, an index lookup against PostgreSQL `shared_buffers` is faster and infinitely more reliable than making an external network hop to a distributed Redis cache!

---

## Section 15 — Performance Engineering

### 15.1 Laws and Metrics: Little's Law and Tail Latency

Performance engineering is governed by mathematical queueing theory:

#### Little's Law
In any stable queueing system, the average number of concurrent requests in the system ($L$) equals the arrival rate ($\lambda$) multiplied by the average time a request spends in the system ($W$):

$$L = \lambda \times W$$

- **Implication for Connection Sizing:** If your API processes $\lambda = 1,000\text{ requests/sec}$ and average request latency is $W = 50\text{ ms} = 0.05\text{ s}$, the average number of concurrent requests being processed is:
  $$L = 1000 \times 0.05 = 50\text{ concurrent requests}$$
- If an unindexed query causes latency to spike to $W = 500\text{ ms} = 0.5\text{ s}$, the system suddenly demands:
  $$L = 1000 \times 0.5 = 500\text{ concurrent requests!}$$
  Your thread pools and database connection pools instantly saturate, requests queue up, and the service collapses.

#### The Tyranny of the Tail: Why Averages are a Lie
A backend system never experiences an "average" user. In a modern microservice architecture, a single user request to an API gateway may spawn $M$ parallel sub-requests to internal microservices and caches.

If each downstream call has a 99th percentile latency spike probability of $1\%$ ($p = 0.01$), the probability that the user's overall request experiences a p99 latency spike across $M = 20$ downstream calls is:

$$P(\text{User Experiences Spike}) = 1 - (1 - p)^M = 1 - (0.99)^{20} \approx 1 - 0.8179 \approx \mathbf{18.2\%!}$$

Almost **one out of every five requests** suffers a severe latency spike, even though every individual downstream component boasts a "99% success rate"! Optimizing p95, p99, and p99.9 latency is paramount.

---

### 15.2 Coordinated Omission in Benchmarking

Coordinated Omission (identified by Gil Tene) is a catastrophic measurement flaw present in naive load-testing tools (such as ApacheBench, old wrk scripts, or custom while-loops):
- If a server experiences a 5-second Stop-The-World GC pause, a naive client that sends a request, waits for a response, and then sends the next request will be **blocked** from sending requests during those 5 seconds.
- The load generator records *one* slow 5-second request.
- In physical reality, thousands of real-world users attempted to connect during those 5 seconds and queued up, experiencing delays of 5s, 4.9s, 4.8s, etc.
- By coordinating request issuance with server response times, the benchmark **omitted** the very latency spikes it was designed to measure!
- **Solution:** Always utilize non-coordinated load testing tools (such as `wrk2`, `k6`, or `Gatling`) that schedule request transmissions against an independent wall clock.

---

## Section 16 — Cache Design Case Studies

### Case Study 1: High-Volume E-Commerce Product Catalog
- **Workload Profile:** Extreme read-to-write ratio ($1,000 : 1$). Millions of catalog views; updates occur only when merchants alter price or stock.
- **Freshness Requirement:** Soft consistency (tolerates 1–5 seconds of price change propagation lag).
- **Architecture:** Multi-Tier Caching.
  - **L0 Edge:** Cloudflare CDN caches static product images and HTML shells (`public, max-age=86400`).
  - **L1 In-Process:** Caffeine cache inside Spring Boot holds top 500 trending products (`TTL = 30s`).
  - **L2 Distributed:** Redis Cluster holds full active catalog (`TTL = 24h`, JSON string representations).
- **Invalidation Strategy:** Event-Driven Invalidation via Transactional Outbox. Merchant price updates commit to PostgreSQL, Debezium streams CDC event to Kafka, and an eviction worker executes `DEL cache:product:{id}` in Redis and broadcasts eviction on Redis Pub/Sub to clear pod L1 caches.
- **Stampede Defense:** Single-flight request coalescing on cache misses.

---

### Case Study 2: User Profile and Session Management
- **Workload Profile:** Frequent reads on every authenticated API request; occasional updates (user updates name or avatar).
- **Freshness Requirement:** Strict **Read-After-Write** consistency for the updating user; eventual consistency for third parties.
- **Architecture:** Redis Distributed Hash (`HSET user:{id}`).
- **Key Design:** Hash structure storing individual user attributes (`username`, `email`, `tier`, `auth_version`).
- **Read-After-Write Mitigation:** When User 101 updates their profile, the application writes to PostgreSQL, updates the Redis hash directly, and sets a temporary session cookie flag `just_updated=true`. Subsequent requests within 5 seconds from that specific user bypass the cache entirely, ensuring they immediately observe their own update.

---

### Case Study 3: Executive Analytics KPI Dashboard
- **Workload Profile:** Multi-table analytical SQL query aggregating $50,000,000$ rows across orders, refunds, and shipments. Execution time: $12\text{ seconds}$ of 100% database CPU.
- **Freshness Requirement:** Bounded staleness (KPIs need only update every 15 minutes).
- **Architecture:** **Refresh-Ahead / Precomputation Worker**.
- **Design:** Application requests **never** trigger the 12-second SQL query on demand! Instead, a scheduled Quartz/Spring background task executes the query every 10 minutes, formats the final JSON summary, and writes it to Redis (`SET dashboard:kpi:global <json> EX 900`).
- **Failure Mode Defense:** If the background calculation fails or times out, the old cached snapshot remains valid (`stale-if-error`), ensuring the executive dashboard never hangs.

---

### Case Study 4: When NOT to Cache: Real-Time Financial Balance & Inventory Decrement
- **Workload Profile:** Flash sale ticket purchase or banking balance transfer. 50,000 concurrent requests competing for the last 5 inventory seats.
- **Freshness Requirement:** Strict Serializability / ACID Correctness. Overselling inventory or double-spending a balance results in financial and legal liability.
- **Why Naive Caching is Fatal:** Caching inventory counts in an application-level Cache-Aside pattern introduces lost-update race conditions (e.g. concurrent read-modify-write cycles).
- **Correct Production Design (Database Authority):** Route mutations directly to the primary relational database utilizing atomic guarded SQL updates:
```sql
UPDATE inventory
SET available_stock = available_stock - 1, version = version + 1
WHERE product_id = 9021 AND available_stock >= 1;
```
If the database updates 1 row, inventory was successfully reserved atomically. If 0 rows were updated, stock was exhausted. Correctness is guaranteed by the guarded `WHERE available_stock >= 1` predicate, PostgreSQL row-level tuple write-locks (`XMAX` tracking), check constraints (`CHECK (available_stock >= 0)`), and write-ahead logging (WAL) serialization—not page-level locks.

- **High-Throughput Admission Control (Redis Pre-decrement with Durability Caveats):**
In extreme flash sales (e.g. 500,000 requests competing for 100 items), routing all traffic directly to PostgreSQL would overwhelm connection pools. A common architectural compromise uses an atomic Redis Lua script for admission control. However, engineers must understand:
> [!CRITICAL]
> **Redis Decrement is NOT a Durable Reservation:**
> 1. **Guarded Decrement:** The Redis Lua script must never decrement stock below zero on rejection (checking `available_stock >= requested_qty` before calling `DECRBY`).
> 2. **The Lost-Reservation Hazard:** An in-memory Redis decrement does not guarantee atomicity with database writes or message publication. If the client or application pod crashes after the Redis decrement but before creating the database order, that inventory slot becomes stranded/lost (reducing apparent inventory without creating a sale).
> 3. **Required Mitigations:** Production systems pairing Redis admission control with database checkout must implement:
>    - **Durable Reservation Leases:** Reservations are stored with an expiration timestamp and token in a durable store.
>    - **Compensating Transactions:** If the database write fails or times out, an explicit rollback increments Redis stock back.
>    - **Reconciliation Workers:** Periodic background sweeps reconcile committed database orders against Redis inventory counters to restore unconsumed reservations.

---

## Section 17 — Interview Question Bank

### Question 1: What is the fundamental difference between Cache-Aside and Write-Through, and how do you choose between them?
**Model Answer:**
In **Cache-Aside**, the application code directly orchestrates data movement. On reads, the app checks the cache; on a miss, it queries the database and populates the cache. On writes, the app writes to the database first and explicitly invalidates (deletes) the cache key. The cache has zero awareness of the underlying database.
In **Write-Through**, the application treats the cache as the primary data store. The application writes directly to the cache, and the cache engine/plugin synchronously writes the data to the persistent database before acknowledging success to the caller.
**Trade-off & Selection:**
Choose Cache-Aside for general-purpose web applications with unpredictable query patterns, mixed entities, and systems that must remain available even if the cache crashes (resilient database fallback). Choose Write-Through for workloads with high data re-read rates immediately after writes (e.g. real-time chat state), where write latency can absorb the penalty of updating both stores synchronously.

---

### Question 2: Why should you delete a cache key on database updates instead of updating it?
**Model Answer:**
Updating the cache on writes introduces concurrent **write-order race conditions**. Suppose Thread 1 updates the database with Value A, and concurrent Thread 2 updates the database with Value B. Due to thread scheduling, network jitter, or context switching, Thread 2 might write Value B to the cache first, followed by Thread 1 writing Value A to the cache second. The database now holds Value B, but the cache permanently holds Value A!
Deleting the key on write eliminates this write-write ordering race: regardless of write ordering, the key is evicted, and the next incoming read loads the authoritative database state.
**Important Boundary Qualification:** While deleting the cache key eliminates write-write ordering anomalies, it does **not** eliminate every invalidation race. Specifically, it does not prevent the classic read-miss / concurrent write race condition (see Question 3), where a concurrent reader fetches a stale database snapshot before the write commits and writes it back to the cache after the key deletion.

---

### Question 3: Walk through the classic Cache-Aside invalidation race condition and explain how to mitigate it.
**Model Answer:**
The classic race occurs when a read miss and a write interleave:
1. Client A queries the cache: Miss.
2. Client A queries the database: Reads old Value 1. Client A is pre-empted by OS scheduling.
3. Client B updates the database to Value 2.
4. Client B deletes the cache key.
5. Client A resumes execution and writes its stale read (Value 1) back into the cache!
Now the cache holds Value 1 while the DB holds Value 2.
**Mitigations & Their Limits:**
1. **Short TTLs:** Ensures stale data self-heals after a bounded window.
2. **Versioned Cache Keys & The Deleted-Key CAS Flaw:** Entities store a monotonic version number in the DB. A naive compare-and-set (CAS) script checks `if new_version > cached_version then set end`.
   *Critical Edge Case:* If the cache key has already been evicted or deleted, `cached_version` does not exist! A naive CAS script blindly creates the key with stale Value 1.
   *Defensible Defense:* Production versioning requires **Version Watermarks (tombstones)** (e.g. keeping `user:101:version_watermark` with `SET version EX 300` on every DB write so even if the payload key is absent, writes with version < watermark are rejected), generation leases, or CDC-driven population.
3. **Database Write First:** Ensures the race window is confined to the sub-millisecond gap between database commit and cache eviction.

---

### Question 4: What is the difference between key expiration and key eviction in Redis?
**Model Answer:**
**Expiration** is time-based. A key is purged because its configured Time-To-Live (TTL) has elapsed. It is governed by passive/lazy checks on access and periodic background sampling (`serverCron`).
**Eviction** is memory-capacity-based. Keys are purged because the dataset volume has reached Redis's configured `maxmemory` ceiling, and Redis must reclaim physical RAM to accommodate new incoming write commands according to its `maxmemory-policy` (e.g. `allkeys-lru`, `volatile-lfu`).

---

### Question 5: How does Redis approximate LRU eviction, and why doesn't it use a true doubly linked list?
**Model Answer:**
A true academic LRU requires a doubly linked list where every read/write unlinks a node and moves it to the head. This requires two 64-bit pointers (16 bytes) per key, consuming massive RAM overhead across millions of keys, and incurs severe lock contention.
Instead, Redis uses an **approximated LRU algorithm**. Each Redis object stores a 24-bit timestamp representing its last access time. When memory is full, Redis randomly samples $N$ keys (default 5, configurable via `maxmemory-samples`), evaluates their 24-bit idle times, and evicts the single key with the largest idle time. At 10 samples, the approximation is statistically indistinguishable from true LRU while consuming zero pointer overhead.

---

### Question 6: Explain the difference between Cache Stampede, Cache Penetration, and Cache Avalanche.
**Model Answer:**
- **Cache Stampede (Thundering Herd):** A *single* hot key expires. Concurrent requests simultaneously miss and overwhelm the DB trying to recompute it. Mitigation: Single-flight request coalescing, probabilistic early refresh (XFetch), or mutex locking.
- **Cache Avalanche:** A *massive collection* of keys expire simultaneously (or the cache cluster crashes), shifting massive traffic to the database at once. Mitigation: TTL jitter (randomizing expiration times), cluster replication, origin rate limiting.
- **Cache Penetration:** Requests query keys that *do not exist* in either cache or database. Every request bypasses the cache and hits the DB. Mitigation: Negative caching (caching null sentinels with short TTL) and Bloom filters.

---

### Question 7: Why is `KEYS *` dangerous in Redis production, and what should you use instead?
**Model Answer:**
Redis command execution is single-threaded. `KEYS *` performs an $O(N)$ linear scan across the entire keyspace dictionary. In a production instance with millions of keys, `KEYS *` blocks the main thread for several seconds. During this freeze, all other client connections time out, health check pings fail, and Redis Sentinel or Cluster may trigger false-positive failovers.
**Replacement:** Use `SCAN`, a cursor-based, non-blocking iterator that returns small batches of keys per call (e.g. 10–50 keys) without freezing the event loop.

---

### Question 8: How do you safely release a distributed lock in Redis?
**Model Answer:**
To prevent Client A from accidentally releasing Client B's lock (which can happen if Client A's lease expires during a long GC pause and Client B acquires the lock), Client A must store a unique random UUID as the lock value (`SET lock_key token NX PX ttl`).
Upon release, Client A must execute an atomic Lua script that checks whether the stored value matches its UUID before calling `DEL`:
```lua
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
```
Because the script is atomic, Client A can never delete a lock acquired by Client B.

---

### Question 9: What is a Fencing Token, and why is a Redis distributed lock unsafe without one?
**Model Answer:**
A distributed lock cannot protect against long client process pauses (such as JVM Stop-The-World GC or OS paging). If Client 1 acquires a 30s lock, pauses for 35s in GC, its lease expires, and Client 2 acquires the lock. Both clients now believe they hold the lock!
A **Fencing Token** is a monotonically increasing integer issued upon every lock acquisition (e.g. 101, 102). When writing to the storage tier (e.g. PostgreSQL), the database validates that the incoming write presents a token strictly higher than any previously observed token. When Client 1 wakes up and submits its write with token 101, the database rejects it because Client 2 already committed with token 102.
**How Tokens are Generated & Enforced:**
1. **Redis Does Not Automatically Issue Fencing Tokens:** Standard Redis `SET lock_key uuid NX PX ttl` only provides mutual exclusion and a lease; it does *not* issue monotonic tokens. In Redis, you must explicitly generate a monotonic sequence (e.g., via an atomic Redis `INCR lock_epoch_counter` in a Lua script) or rely on consensus systems (like ZooKeeper `zxid` or etcd `mod_revision`).
2. **Storage-Tier Enforcement:** The distributed lock itself cannot prevent a stalled client from issuing network writes. Correctness is achieved **exclusively** by the protected downstream storage system (e.g., PostgreSQL `UPDATE accounts SET balance = $1, last_fencing_token = $2 WHERE id = $3 AND last_fencing_token < $2;`) rejecting obsolete tokens.

---

### Question 10: How do you implement rate limiting in Redis, and what are the critical expiry and concurrency bugs?
**Model Answer:**
Rate limiting in Redis typically utilizes two algorithms, each with distinct edge cases:
1. **Fixed-Window Counter (`INCR` + `EXPIRE` Crash Window):**
   A naive implementation issues `INCR key`. If count is 1, it issues `EXPIRE key 60`. If the application crashes or network drops between the two commands, the key is left with **no TTL**, permanently locking out the user!
   *The Fix:* Use an atomic Lua script that executes `INCR` and sets `EXPIRE` atomically on the first count (`if current == 1 then redis.call('EXPIRE', ...) end`), or Redis 7+ `INCRBY` with options.
2. **Sliding-Window Log via Sorted Sets (The Member Collision Bug):**
   Uses a Redis `ZSET` where scores are epoch timestamps. Stale entries are pruned with `ZREMRANGEBYSCORE`, current count checked via `ZCARD`, and the new request added via `ZADD`.
   *The Collision Bug:* If member strings are timestamps alone (`ZADD key now now`), two requests arriving in the exact same millisecond have identical member names. Redis updates the score of the existing member instead of adding a new one, failing to increment `ZCARD` and allowing users to exceed rate limits.
   *The Fix:* Use a composite member string combining timestamp and a unique request nonce: `now .. ':' .. requestId`.

---

### Question 11: In HTTP caching, what is the difference between `Cache-Control: no-cache` and `Cache-Control: no-store`, and what are Weak ETag semantics?
**Model Answer:**
- **`no-cache`:** Instructs the client/proxy that it **may store** the response, but it **must revalidate** with the origin server (via `If-None-Match` or `If-Modified-Since`) before serving it. If the server returns `304 Not Modified`, the cached response is served.
- **`no-store`:** Under RFC 9111 Section 5.2.2.5, `no-store` mandates that the cache **MUST NOT store any part of either the request or response in any storage tier** (neither volatile RAM nor non-volatile disk). Every request must round-trip to origin. Mandatory for sensitive personal, financial, and authentication data.
- **Weak ETag Semantics (`W/"..."`):** Under RFC 9110 Section 8.8.1, weak ETags indicate semantic equivalence rather than exact byte-for-byte octet identity (e.g. gzip vs brotli compressed payloads, or reordered JSON fields). Under RFC 9110 Section 13.1.2, weak ETags are valid for full-body `If-None-Match` cache revalidation, but are **strictly forbidden** for byte-range requests (`Range: bytes=...`) and optimistic concurrency updates (`If-Match`).

---

### Question 12: Why is `@Cacheable` subject to the self-invocation proxy bypass trap in Spring Boot, and what is the scope of `sync = true`?
**Model Answer:**
Spring's declarative caching utilizes Spring AOP dynamic proxies. When an external bean calls `@Cacheable` method `foo()`, it invokes the proxy, which executes caching interceptor logic before delegating to the target bean.
If method `bar()` inside the *same* class calls `this.foo()`, the call is an internal JVM method dispatch that completely bypasses the proxy wrapper. The caching annotations on `foo()` are ignored, and the method executes directly against the database every time.
**The Scope of `@Cacheable(sync = true)`:**
Setting `sync = true` activates **in-process (JVM-local) thread synchronization**. It ensures only one thread within *that specific JVM instance* computes a missing key while other threads wait. It does **NOT** provide distributed single-flight behavior across multiple application instances in a cluster. If 10 microservice pods experience a hot cache miss, all 10 pods will query the database simultaneously unless distributed coordination or edge coalescing is used.

---

### Question 13: What is the N+1 query problem, and why is caching an anti-pattern solution for it?
**Model Answer:**
The N+1 query problem occurs when an application executes 1 query to fetch $N$ parent records, followed by $N$ separate queries in a loop to fetch child records for each parent (e.g. fetching 100 orders, then issuing 100 queries for user details).
Caching individual child entities hides this latency during warm cache states. However, upon cache invalidation or cold start, the application fires 101 queries per page load, overwhelming the database. The correct architectural fix is optimizing the database query using `JOIN FETCH` in JPA or batch loading with `WHERE id IN (...)` before applying caching.

---

### Question 14: How does Little's Law explain cascading database connection pool exhaustion?
**Model Answer:**
Little's Law states $L = \lambda \times W$ (Concurrency = Throughput $\times$ Latency). If an API handles $\lambda = 500\text{ req/sec}$ and normal query latency is $W = 10\text{ ms} = 0.01\text{ s}$, average database connection concurrency is $500 \times 0.01 = 5$ connections.
If a cache failure shifts heavy traffic to an unindexed query, latency climbs to $W = 200\text{ ms} = 0.2\text{ s}$. Concurrency demands skyrocket to $500 \times 0.2 = \mathbf{100\text{ connections}}$. Because standard HikariCP connection pools are sized to 10–20 connections, all connections saturate immediately, incoming requests queue up, thread starvation cascades through the server, and the entire API becomes unresponsive.

---

### Question 15: What is Coordinated Omission in load testing?
**Model Answer:**
Coordinated Omission occurs when a benchmarking tool measures latency by waiting for a response before generating the next request. If the server pauses for 5 seconds (e.g. during a full GC pause), the benchmarking tool stalls and sends zero requests during that window. The benchmark records only 1 slow request, completely omitting the thousands of virtual user requests that would have arrived, queued, and suffered massive delays in a real production arrival process.

---

### Question 16: What is the XFetch algorithm, and how does it prevent cache stampedes?
**Model Answer:**
XFetch (Probabilistic Early Refresh) prevents cache stampedes without distributed locks. As a cached key approaches its expiration time, client read requests evaluate a probabilistic threshold formula: $-\beta \times \delta \times \ln(\text{Random}(0, 1)) > \text{TTL}_{\text{remaining}}$, where $\delta$ is the computation time and $\beta > 0$ is an aggressiveness factor.
Under heavy traffic, as remaining TTL drops, the probability of triggering an asynchronous background refresh climbs exponentially. One client will probabilistically refresh the cache early, repopulating it before hard expiration.
**Operational Boundary:** XFetch significantly reduces stampede probability under continuous traffic, but it **cannot guarantee that every refresh completes before expiration**. If read traffic is sparse or drops to zero during the probability window, or if computation time $\delta$ suddenly spikes or the refresh worker crashes, the key will still expire and cause a normal cache miss.

---

### Question 17: How should a backend application handle Redis outages, timeouts, and database fallback?
**Model Answer:**
By implementing **Defensive Degradation and Circuit Breaking**:
1. **Timeouts and Timer Cleanup:** Wrap all Redis interactions in strict network timeouts (e.g. 50ms). In runtimes like Node.js, ensure timer handles are cleared (`clearTimeout`) to prevent event-loop timer leaks, and remember that `Promise.race` does *not* cancel the underlying Redis socket operation.
2. **The Fallback Overload Hazard:** Naively redirecting cache misses to the primary database during a Redis outage will instantly dump tens of thousands of QPS onto PostgreSQL. Because database queries are 10x–50x slower and connection pools are limited (e.g. HikariCP with 20 connections), Little's Law dictates that connections will saturate, thread starvation will cascade, and the entire backend will collapse.
3. **Defensive Architecture:** Wrap cache access in a Circuit Breaker (e.g. Resilience4j or Opossum). When Redis fails, the circuit opens to fail-fast. Instead of unconstrained database fallback, the system must apply origin rate limiting, serve stale cached snapshots (`stale-if-error`), or degrade gracefully with partial/cached default responses.

---

### Question 18: Why is `allkeys-lru` preferred over `volatile-lru` for a dedicated cache cluster?
**Model Answer:**
`volatile-lru` only evicts keys that have an explicit TTL set. If an engineer forgets to specify a TTL on a set of keys, or if third-party libraries insert unexpired data, those keys become permanent fixtures in memory. Under memory pressure, `volatile-lru` will evict all expiring keys and then throw `OOM command not allowed` errors, even though gigabytes of unexpired keys exist. `allkeys-lru` evaluates all keys across the entire keyspace, guaranteeing that memory is always safely reclaimed based on access recency.

---

### Question 19: Explain why Bloom filters cannot have false negatives, but can have false positives, and state the required assumptions.
**Model Answer:**
A Bloom filter uses $k$ independent hash functions that map an item to $k$ bit indices in an array, setting those bits to `1`.
- When querying an item, if **any** of the $k$ bits is `0`, the item could **never** have been inserted (because inserting it would have set all $k$ bits to `1`).
- If all $k$ bits are `1`, it is possible that different previously inserted items happened to set those same bits to `1` by coincidence (a false positive).
**Strict Preconditions for the Zero-False-Negative Guarantee:**
A Bloom filter has zero false negatives **only if**:
1. **Complete Population:** All existing database records were correctly inserted into the filter.
2. **No Deletions:** Standard Bloom filters do not support deletion. If records are deleted from the database, the filter cannot remove them without risking unset bits that cause false negatives for other records.
3. **Synchronous Write-Path Updates:** Newly created records must be added to the Bloom filter before or synchronously with database commits. If an entity is queried before the filter receives the write, the filter returns `false` (does not exist) for a record that *does* exist in the database—yielding an apparent false negative! A Bloom filter is not authoritative when its membership state is stale or incomplete.

---

### Question 20: How do you achieve read-after-write consistency in a Cache-Aside architecture?
**Model Answer:**
When User A mutates their profile, they expect to see their new data immediately upon page refresh, even if asynchronous cache invalidation or replication lag has not completed.
**Technique:** When a user performs a write, the server invalidates the cache and sets a temporary session cookie or local header (e.g. `read_through_db_until = timestamp + 5000ms`). On subsequent requests from that specific user within that 5-second window, the API gateway or service bypasses the cache entirely and reads directly from the primary PostgreSQL database, ensuring 100% read-after-write consistency for the writer while serving the rest of the world from cache.

---

### Question 21: What is the Transactional Outbox pattern, and how does it relate to cache invalidation?
**Model Answer:**
The Transactional Outbox pattern solves the dual-write hazard between a database and a message broker. Instead of updating the database and attempting to publish a cache invalidation event to Kafka over the network (which can fail if the app crashes between the two), the app inserts an event record into an `outbox` table within the **same local ACID transaction** as the business mutation. A reliable CDC engine (e.g. Debezium) reads the committed database log and publishes the invalidation event to Kafka, guaranteeing at-least-once delivery of cache evictions to Redis.

---

### Question 22: What causes high memory fragmentation in Redis, and how do you resolve it?
**Model Answer:**
Memory fragmentation ratio ($\text{used\_memory\_rss} / \text{used\_memory}$) exceeds 1.5 when the operating system allocates physical memory pages (via `jemalloc`) that Redis cannot fully pack due to fluctuating key sizes, rapid allocation and deletion of keys, and memory allocation chunk alignment.
**Resolution:** Enable Redis **Active Defragmentation** (`activedefrag yes`). Redis will allocate a background CPU slice to scan memory pages, move contiguous values into packed memory pages, and release empty pages back to the operating system without restarting the daemon.

---

### Question 23: When is an SQL index better than a cache, and what dictates its latency?
**Model Answer:**
An SQL index is superior when the dataset changes frequently or when strict transactional consistency is required. A proper B-Tree index lookup eliminates cache-invalidation race conditions, network serialization overhead, and memory duplication costs.
**Workload-Dependent Latency:** While B-Tree index lookup complexity is $O(\log N)$ comparisons, physical execution latency is strictly workload-dependent:
- If active index and table pages reside in memory (PostgreSQL `shared_buffers` or InnoDB Buffer Pool) and the query is an index-only scan, execution takes **sub-millisecond** time directly in RAM.
- If pages must be read from NVMe/SSD disk (cold cache / buffer eviction), or if table heap fetches incur random I/O, or if lock contention and index bloat are present, latency climbs to **5–50ms**.
Caching should only be layered on top if the indexed query still consumes excessive CPU (e.g. complex multi-table aggregations) or if query volume exceeds database connection pool capacity.

---

### Question 24: What is the purpose of the `Vary` HTTP header in caching?
**Model Answer:**
The `Vary` header instructs HTTP caches (browsers, CDNs) that a cached response cannot be served to future requests unless the incoming request headers match the headers listed in `Vary`. For example, `Vary: Accept-Encoding, Origin` ensures that a client requesting gzip compression does not receive a cached uncompressed response, and that CORS preflight responses are partitioned by origin.

---

### Question 25: How do you design a flash sale system to prevent database collapse when 1,000,000 users attempt to purchase 100 items?
**Model Answer:**
1. **Edge Shielding:** Static sale pages and product descriptions are cached at Cloudflare CDN (`public, max-age=60`).
2. **In-Memory Pre-Allocation:** Pre-load the exact inventory count into a Redis integer key (`stock = 100`).
3. **Atomic Lua Rate Limiting & Guarded Decrement:** Users clicking "Buy" execute an atomic Redis Lua script that guards against decrements below zero:
```lua
local stock = tonumber(redis.call('get', KEYS[1]) or '0')
if stock >= 1 then
    redis.call('decr', KEYS[1])
    return 1 -- Reserved
else
    return 0 -- Sold out (does NOT decrement below zero)
end
```
4. **Asynchronous Queuing & Durability Warning:** The 100 winning users are queued via RabbitMQ/Kafka to complete database checkout. The remaining 999,900 requests fail fast at the Redis tier in ~1ms without touching PostgreSQL.
**Critical Reliability Warning:** A Redis decrement is **not** a durable reservation and does not guarantee atomicity with subsequent database transactions. If an application pod crashes after the Redis decrement but before creating the database order, that inventory slot becomes stranded/lost. Production systems must implement durable reservation leases with expiration timestamps, compensating rollbacks on DB failure, and background reconciliation workers.

---

## Section 18 — Module 7 Cheatsheet: Caching & Performance

> [!IMPORTANT]
> **Module 7 Revision Cheatsheet:** This section is an ultra-dense, self-contained reference designed for rapid interview preparation and system design defense.

---

### 18.1 Master Caching Mental Model & Formulas

- **The Cache Invariant:** A cache is derivative, volatile, and disposable. Primary persistence must always live in an authoritative database.
- **Expected Latency Formula:**
  $$E[L] = L_{\text{cache}} + (1 - H) \times (L_{\text{db}} + L_{\text{write}})$$
- **Little's Law:**
  $$L = \lambda \times W \quad (\text{Concurrency} = \text{Throughput} \times \text{Latency})$$
- **Tail Latency Amplification:** Across $M$ sub-calls with failure probability $p$:
  $$P(\text{Spike}) = 1 - (1 - p)^M$$

---

### 18.2 Master Caching Patterns Comparison

| Pattern | Read Flow | Write Flow | Data Loss Risk | Consistency | Best For |
| :--- | :--- | :--- | :---: | :--- | :--- |
| **Cache-Aside** | App reads Cache; on miss, reads DB, populates Cache. | App writes DB, then deletes (`DEL`) Cache. | **Zero** | Eventual | General REST APIs, mixed read/write workloads. |
| **Read-Through** | App reads Cache library; library loads DB on miss. | Paired with Write-Through / Write-Behind. | **Zero** | Eventual | Standard ORM / Repository abstraction layers. |
| **Write-Through** | Same as Read-Through. | App writes Cache; Cache synchronously writes DB. | **Zero** | High (No cross-system 2PC atomicity) | Read-immediately-after-write entities. |
| **Write-Behind** | Same as Read-Through. | App writes Cache; Cache flushes DB asynchronously. | **High** | Eventual | High-throughput telemetry, metrics, gaming counters. |
| **Refresh-Ahead** | App reads Cache; Cache auto-refreshes hot expiring keys. | Decoupled (Cache-Aside / Write-Through). | **Zero** | High | Known hot items where miss latency is unacceptable. |

---

### 18.3 Redis Eviction Policies & Data Structures

```
REDIS EVICTION POLICIES (maxmemory-policy)
├── noeviction        -> Throws OOM on writes when memory is full (Use for datastore/rate-limits)
├── allkeys-lru       -> Evicts least recently used keys across all keys (STANDARD WEB CACHE)
├── volatile-lru      -> Evicts least recently used keys ONLY among those with TTL
├── allkeys-lfu       -> Evicts least frequently used keys (Protects hot keys against scans)
├── volatile-lfu      -> Evicts least frequently used keys ONLY among those with TTL
└── volatile-ttl      -> Evicts keys with shortest remaining TTL
```

```
REDIS DATA STRUCTURE SELECTION
├── String     -> JSON entities, binary buffers, atomic counters (INCR), distributed locks (SET NX PX)
├── Hash       -> Objects with multiple fields (HSET/HGETALL). Avoids full object re-serialization.
├── List       -> FIFO task queues (LPUSH/BRPOP), capped audit feeds (LTRIM).
├── Set        -> Unique tags, mutual friends, set operations (SINTER/SADD/SISMEMBER).
└── Sorted Set -> Leaderboards, priority queues, sliding-window rate limiters (ZADD/ZRANGEBYSCORE).
```

---

### 18.4 Failure Modes & Defenses Cheat Matrix

| Anomaly | Root Cause | Primary Symptom | Defensive Solutions |
| :--- | :--- | :--- | :--- |
| **Cache Stampede** | Single hot key expires under high concurrency. | Database CPU spikes to 100% on a single entity lookup. | 1. Single-Flight request coalescing (in-process / distributed).<br>2. Probabilistic early refresh (XFetch algorithm - reduces risk, but sparse traffic or latency spikes can still miss).<br>3. Mutex locking on miss. |
| **Cache Avalanche** | Massive number of keys expire at the exact same second. | Broad database saturation across thousands of queries. | 1. **TTL Jitter:** Add random duration: $\text{TTL}_{\text{base}} + \text{random}(0, \text{jitter})$.<br>2. Redis Cluster high availability with read replicas. |
| **Cache Penetration** | Requests query IDs that do not exist in cache or DB. | Continuous 100% database miss rate returning 404s. | 1. **Negative Caching:** Cache null sentinel with short TTL (30s).<br>2. **Bloom Filter:** Pre-screen membership (authoritative only when fully populated and write-synchronized). |
| **Hot Key Saturation** | Single key receives extreme traffic exceeding shard CPU/NIC. | One Redis node hits 100% CPU while others sit idle. | 1. Local in-process L1 cache (Caffeine).<br>2. Split key across shards with random suffixes (`key:1..N`). |

---

### 18.5 HTTP Caching Directives (RFC 9111 & RFC 9110)

```
HTTP CACHE-CONTROL PATTERNS (RFC 9111)
├── Reusable Public Content   -> Cache-Control: public, max-age=3600, s-maxage=86400, stale-while-revalidate=60
├── Revalidate Always Content -> Cache-Control: no-cache (May store; MUST revalidate with origin before serving)
└── Sensitive / Auth Content  -> Cache-Control: no-store, private (MUST NOT store anywhere in RAM or disk)

CORE DIRECTIVES DECODED
├── max-age=N                -> Freshness lifetime for browsers in seconds.
├── s-maxage=N              -> Freshness lifetime for shared intermediary caches (CDNs) only.
├── no-cache                 -> MUST revalidate with origin before serving (DOES NOT MEAN NO STORE!).
├── no-store                 -> MUST NOT store anywhere (RAM or disk, RFC 9111 Sec 5.2.2.5; mandatory for auth/PII).
├── public                   -> Any cache (CDN, proxy, browser) may store.
├── private                  -> Browser cache only; shared CDNs must NOT store.
├── stale-while-revalidate=N -> Serve stale response immediately while refreshing in background.
├── Strong ETag ("hash")     -> Byte-for-byte binary identity; required for Range / 206 Partial Content.
└── Weak ETag (W/"hash")     -> Semantic equivalence (RFC 9110); valid for full GET revalidation, forbidden for Range.
```

---

### 18.6 Common Production Pitfalls to Avoid in Interviews

1. **Updating Cache on DB Write:** Prefer `DEL` on write to eliminate write-order race conditions. Note that key deletion alone does not eliminate the read-miss / concurrent write interleave race; version watermarks or generation leases are required for strict serialization.
2. **Synchronous Keys Command:** Never use `KEYS *` in production; always use non-blocking cursor iteration `SCAN`.
3. **Caching Before Indexing:** Always optimize SQL queries, fix N+1 queries, and add composite B-Tree indexes before implementing a cache. B-Tree lookup latency is workload-dependent (sub-millisecond in buffer cache vs tens of milliseconds on cold disk I/O).
4. **Uniform TTLs:** Never set identical static TTLs across bulk inserts; always apply pseudo-random TTL jitter.
5. **Spring Cache Scope & Proxy Trap:** Calling a `@Cacheable` method from within the same class bypasses Spring AOP proxies. Moreover, `@Cacheable(sync = true)` is strictly local to a single JVM instance, not fleet-wide across pods.
6. **Fragile Cache Dependencies & Fallback Overload:** Wrap all cache calls in strict timeouts with timer cleanup (`clearTimeout` in Node.js) and Circuit Breakers. `Promise.race` does not cancel underlying socket operations, and a cache outage must never unconstrainedly dump 100% of read traffic onto the relational database.
7. **Rate Limiter Pitfalls:** In fixed-window limiters, avoid naive `INCR` + `EXPIRE` crash windows by using atomic Lua scripts. In Redis Sorted Set sliding windows, combine `now:requestId` to guarantee uniqueness.
8. **Lock Release Stalls & Fencing Tokens:** Never release a Redis distributed lock with a bare `DEL`; use a Lua script checking the owner UUID token. Remember that Redis locks do not issue monotonic fencing tokens; tokens must be generated explicitly and enforced by the downstream storage system.
9. **Unguarded Flash-Sale Decrement:** Never decrement inventory below zero on rejection. Always guard decrements in Lua, and implement durable reservation leases and reconciliation, because an in-memory Redis decrement is not a durable transaction.
