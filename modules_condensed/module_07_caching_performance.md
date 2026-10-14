# Module 07: Caching and Performance Engineering

## 1. Systems Physics of Caching & Mathematical Foundations

Caching trades memory footprint for reduced latency and offloaded database computation by exploiting the **Principle of Locality**:
- **Temporal Locality:** Data accessed recently is highly likely to be accessed again in the near future.
- **Spatial Locality:** Data stored physically near accessed data is likely to be accessed soon.

### 1.1 Cache Hit Ratio Economics
Average memory access time ($T_{\text{avg}}$) is governed by:
$$T_{\text{avg}} = H \cdot T_{\text{cache}} + (1 - H) \cdot T_{\text{db}}$$
where $H$ is the Cache Hit Ratio ($0 \le H \le 1$), $T_{\text{cache}}$ is cache read latency ($\sim 1$ms for Redis), and $T_{\text{db}}$ is database query latency ($\sim 50$ms).

*Non-Linear Performance Degradation:*
- At $H = 99\%$: $T_{\text{avg}} = (0.99 \times 1) + (0.01 \times 50) = 0.99 + 0.50 = 1.49$ ms.
- At $H = 90\%$: $T_{\text{avg}} = (0.90 \times 1) + (0.10 \times 50) = 0.90 + 5.00 = 5.90$ ms ($4\times$ latency penalty!).
A seemingly minor 9% drop in hit ratio increases backend database load and average latency by **$400\%$**.

---

## 2. Cache Topologies & Access Patterns

### 2.1 Cache Topologies: L1 vs. L2 vs. Two-Tier Near Cache
- **L1 In-Process Cache (Caffeine):** Stored directly in application JVM heap. Zero network serialization or socket RTT ($< 50$ ns). *Trade-off:* Consumes application memory; data is duplicated across nodes; risk of cache drift across instances.
- **L2 Distributed Cache (Redis):** Centralized shared cluster accessed over network ($\sim 1$ ms). Coherent single source of truth across all application servers. *Trade-off:* Network serialization and latency overhead.
- **Two-Tier Near Cache:** Reads query local L1 cache first; on L1 miss, query L2 Redis; on L2 miss, query Database. Invalidations are broadcasted across L1 caches via **Redis Pub/Sub invalidation bus**.

### 2.2 Caching Patterns
1. **Cache-Aside (Lazy Loading):** Application queries cache. On hit, return data. On miss, read database, populate cache with TTL, and return data. Primary pattern for read-heavy workloads.
2. **Write-Through:** Application writes to cache; cache synchronously writes to database before returning success. Guarantees consistency; higher write latency.
3. **Write-Behind (Write-Back):** Application writes to cache; cache asynchronously batches writes to database. Extremely high write throughput. *Risk:* Data loss if cache crashes before dirty writes are persisted.
4. **Refresh-Ahead:** Cache automatically reloads frequently accessed keys before their TTL expires based on access patterns.

### 2.3 Why Invalidate Rather Than Update
**Inviolable Production Rule:** On database writes, **invalidate (delete) the cache entry** rather than updating it:
```
Thread A: Writes Value 1 to DB
Thread B: Writes Value 2 to DB
Thread B: Updates Cache to Value 2
Thread A: Updates Cache to Value 1 (Arrives out of order due to network jitter!)
Result: Database holds Value 2; Cache holds stale Value 1 indefinitely!
```
Deleting the key guarantees the next read will fetch the latest database state cleanly.

---

## 3. Eviction Policies & Memory Management

When cache memory fills to capacity, eviction algorithms reclaim space:
- **Least Recently Used (LRU):** Evicts the key whose last access timestamp is oldest. Implemented via a Hash Map + Doubly Linked List in $O(1)$ time.
- **Least Frequently Used (LFU):** Evicts the key with the lowest access frequency counter. Uses logarithmic saturation counters (e.g., Morris counter in Redis).
- **Redis Memory Eviction Policies (`maxmemory-policy`):**
  - `volatile-lru` / `allkeys-lru`: Approximate LRU based on random key sampling.
  - `volatile-lfu` / `allkeys-lfu`: Frequency-based eviction.
  - `volatile-ttl`: Evicts shortest remaining TTL first.
  - `noeviction`: Returns errors on memory allocation commands (safe for queues/locks).

---

## 4. Redis Architecture & Persistence Internals

### 4.1 Single-Threaded Reactor Event Loop
Redis executes commands on a single thread using non-blocking I/O multiplexing (`epoll`), eliminating lock contention, mutex overhead, and thread context switches. Command execution completes in memory in nanoseconds.

### 4.2 Redis Persistence: RDB vs. AOF
1. **RDB (Redis Database Snapshots):** Point-in-time snapshot dumped to disk via `bgsave`. Uses Linux `fork()` Copy-On-Write (COW). *Risk:* Memory usage can spike up to $2\times$ during intensive writes; data between snapshots is lost on crash.
2. **AOF (Append-Only File):** Logs every write operation sequentially.
   - `appendfsync always`: Slowest, safest ($fsync$ on every write).
   - `appendfsync everysec`: Recommended production default (loses at most 1 second of writes).
   - `appendfsync no`: OS controls flushing.
   - **AOF Rewrite:** Compresses write history in background (e.g., 1000 increments on a key collapse to 1 `SET`).

---

## 5. The 4 Critical Cache Failure Modes

```
+------------------+-----------------------------------------------------------+
| Cache Stampede   | Hot key with heavy concurrent reads expires. Thousands of |
| (Thundering Herd)| threads miss simultaneously, crushing the DB with queries.|
+------------------+-----------------------------------------------------------+
| Cache Penetration| Requests for non-existent IDs bypass cache, querying DB.  |
|                  | Exploited by attackers to exhaust DB connection pools.    |
+------------------+-----------------------------------------------------------+
| Cache Avalanche  | Massive batch of keys configured with identical TTL expire|
|                  | simultaneously, causing instantaneous system-wide DB crash|
+------------------+-----------------------------------------------------------+
| Hot Key          | A single key receives millions of requests/sec, maxing out|
| Saturation       | the single-threaded CPU or network bandwidth of one node. |
+------------------+-----------------------------------------------------------+
```

### 5.1 Probabilistic Early Expiration (XFetch Algorithm)
To eliminate Cache Stampedes without blocking threads, background workers recompute the cached value before expiration:
$$\text{Recompute if: } -\beta \cdot \delta \cdot \ln(\text{rand}()) > \text{TTL}_{\text{remaining}}$$
where $\beta > 0$ is aggressiveness, $\delta$ is computation time, and $\text{rand}() \in (0, 1]$.

### 5.2 Bloom Filters for Cache Penetration
A Bloom Filter is a space-efficient probabilistic data structure that tests set membership:
- Returns: "Definitely NOT in set" or "Possibly in set". Zero False Negatives.
- Filter sits in front of the cache: if Bloom filter returns false, reject immediately with HTTP 404 without querying cache or database.

### 5.3 Jittered TTL for Cache Avalanche
Always add randomized jitter to TTLs to spread expirations evenly across time:
$$\text{TTL} = \text{BaseTTL} + \text{UniformRandom}(-\Delta, +\Delta)$$

---

## 6. Distributed Locking with Redis

### 6.1 Safe Mutual Exclusion & Release Invariant
Acquiring a distributed lock requires an atomic `SET NX PX` command with a unique token (UUID):
```bash
SET lock:order:123 "unique-uuid-token" NX PX 30000
```
- `NX`: Set only if key does not exist.
- `PX 30000`: Expire lock automatically in 30,000 ms to prevent deadlocks if holder crashes.

**The Release Invariant:** Never delete a lock key via plain `DEL`. If thread A experiences a GC pause exceeding 30s, its lock expires. Thread B acquires the lock. Thread A wakes up and calls `DEL`, accidentally deleting Thread B's lock! Release must use an **atomic Lua script**:
```lua
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end
```

### 6.2 Martin Kleppmann Redlock Critique & Fencing Tokens
Distributed locks cannot guarantee safety on storage engines that do not verify version tokens. If a thread pauses after lock verification, a network delay can cause a "zombie write".
- **Fencing Token Solution:** The lock service returns a monotonically increasing integer token ($1, 2, 3$). The storage layer rejects any write with a token lower than the latest committed token.

---

## 7. Performance Engineering Laws

- **Little's Law:** In any stable queuing system:
  $$L = \lambda \cdot W$$
  (Average Concurrent Items in System = Arrival Rate $\times$ Average Time Spent). If API receives 1,000 req/sec and average latency is 200ms ($0.2$s), average concurrent requests held in memory is exactly $1000 \times 0.2 = 200$.
- **Amdahl's Law:** Speedup limit from parallelization:
  $$S = \frac{1}{(1 - P) + \frac{P}{N}}$$
  If $10\%$ of an algorithm is strictly sequential ($1-P = 0.1$), the maximum theoretical speedup is capped at $10\times$, regardless of allocating 1,000 CPU cores.
- **Tail Latency Amplification:** If a backend fan-out service queries 100 microservices in parallel, each with $p99 = 1\%$ probability of being slow:
  $$P(\text{request is slow}) = 1 - (1 - 0.01)^{100} = 1 - (0.99)^{100} \approx 63.4\%$$
  Even though individual services have $99\%$ fast responses, **$63.4\%$ of user requests will experience tail latency!**

---

## 8. Failure Modes & Production Triage

| Failure Mode | Diagnostic Symptom | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **Cache Stampede** | Database CPU spikes to 100% instantly after hot key invalidation. | Thousands of threads simultaneously querying DB on cache miss. | Use Mutex locking during cache reload or XFetch probabilistic reload. |
| **Cache Penetration** | DB CPU climbs under malicious random ID query bombardment. | Non-existent keys missing cache and hitting DB directly. | Deploy Bloom Filter in front of cache; store negative entries (`NULL`) with short TTL. |
| **Redis OOM** | `OOM command not allowed when used memory > 'maxmemory'`. | Redis memory filled with unbounded keys; eviction policy set to `noeviction`. | Set `maxmemory-policy allkeys-lru`; ensure all keys have explicit TTLs. |
| **Zombie Lock Write** | Two workers simultaneously process identical payment. | Worker paused by GC; lock expired; worker resumed and executed write. | Enforce monotonic Fencing Tokens at the database storage layer. |
---

## 9. High-Bar Caching & Performance Interview Scenarios

### Scenario 1: Cache Stampede Mitigation on Expiring Hot Keys
- **Interview Question:** "A celebrity user profile cached in Redis expires. Instantly, 50,000 requests/sec miss the cache and hit the relational database simultaneously, crashing the database tier. How do you prevent this?"
- **Deep Technical Answer:**
  1. **Distributed Mutex Lock (Single Rebuilder):** When a cache miss occurs, threads attempt to acquire an atomic Redis lock (`SET lock:user:123 uuid NX EX 10`).
     - The single winning thread queries the database and repopulates the cache.
     - All other threads sleep briefly and retry the cache read, avoiding database queries entirely.
  2. **Probabilistic Early Expiration (XFetch):** Background read requests calculate:
     $$	ext{if } -eta \cdot \delta \cdot \ln(	ext{rand}()) > 	ext{TTL}_{	ext{remaining}}$$
     A single worker asynchronously recomputes and extends the cache before the key physically expires, achieving zero user-facing cache misses.

### Scenario 2: Cache Penetration Defense via Bloom Filters
- **Interview Question:** "An attacker launches a DDoS attack querying non-existent account IDs (`/accounts/random-uuid`). The queries miss the cache and overwhelm the database. How do you defend the system?"
- **Deep Technical Answer:**
  1. **Bloom Filter Ingress:** Deploy a Redis Bloom Filter initialized with all existing valid account IDs.
     - On request arrival, query `BF.EXISTS accounts_bloom :id`.
     - If the Bloom filter returns `0` (false), reject the request immediately with HTTP 404 without querying the cache or database.
  2. **Negative Caching:** For keys that pass the Bloom filter but do not exist in the database, store a negative sentinel value (`NULL`) in Redis with a short TTL (e.g., 60 seconds).

### Scenario 3: Distributed Lock Fencing Tokens
- **Interview Question:** "Why does Martin Kleppmann demonstrate that Redlock cannot guarantee mutual exclusion in distributed systems?"
- **Deep Technical Answer:**
  1. **Stop-the-World GC Pause Hazard:** Client 1 acquires a lock with a 10s lease. Client 1 enters an unexpected 15s JVM GC pause.
  2. The lock expires in Redis. Client 2 acquires the lock and successfully writes to storage.
  3. Client 1 wakes from its GC pause, assumes it still owns the lock, and writes to storage, **corrupting Client 2's data**.
  4. **The Fencing Token Solution:** The lock manager issues a monotonically increasing sequence number with each lock grant. The storage engine validates that every write has a token strictly greater than the highest committed token, rejecting stale zombie writes.
---

## 10. Redis Advanced Structures & Cluster Topology

### 10.1 Advanced Redis Data Structures
- **HyperLogLog (`PFADD`, `PFCOUNT`):** Probabilistic cardinality estimator. Measures unique items (e.g., daily unique site visitors) using strictly **12 KB of memory** with a standard error of $\le 0.81\%$, whether counting 100 items or 1,000,000,000 items.
- **Geospatial (`GEOADD`, `GEORADIUS`):** Encodes latitude/longitude coordinates into a 52-bit integer **Geohash** stored inside a standard Redis Sorted Set. Enables sub-millisecond proximity queries.
- **Bitmaps (`SETBIT`, `GETBIT`, `BITCOUNT`):** Compact bit arrays. Tracks user retention, feature flags, or daily activity for 100M users using only 12MB of RAM.

### 10.2 Redis Cluster Architecture: Hash Slots & Redirection
Redis Cluster partitions data across up to 1,000 nodes without a centralized proxy:
- **16,384 Hash Slots:** The cluster keyspace is divided into exactly 16,384 slots ($0 \text{ to } 16383$).
- **Key-to-Slot Mapping:**
  $$\text{Slot} = \text{CRC16}(\text{Key}) \pmod{16384}$$
- **Hash Tags:** Substrings enclosed in braces `{...}` force keys to hash to the identical slot (e.g., `{user:123}.profile` and `{user:123}.orders` share a slot, permitting multi-key operations and Lua scripts).
- **Client Redirection:**
  - `MOVED <slot> <ip:port>`: Permanent migration; client updates its internal slot-to-node routing cache.
  - `ASK <slot> <ip:port>`: Temporary redirection during slot migration; client issues `ASKING` before retrying command.
