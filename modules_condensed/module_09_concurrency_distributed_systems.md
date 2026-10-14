# Module 09: Concurrency and Distributed Systems

## 1. Concurrency vs. Parallelism & Hardware Foundations

- **Concurrency:** The composition of independently executing computations (structure). Dealing with multiple things at once.
- **Parallelism:** The simultaneous execution of multiple computations on multiple physical CPU cores. Doing multiple things at once.
- **Amdahl's Law:** Theoretical execution speedup is strictly bounded by the program's sequential fraction $S$:
  $$\text{Speedup} = \frac{1}{S + \frac{1 - S}{N}}$$
  If $5\%$ of a program is serial ($S = 0.05$), the absolute maximum speedup on an infinite number of CPU cores is $\frac{1}{0.05} = 20\times$.

### 1.1 CPU Cache Coherence, Cache Lines & False Sharing
Multi-core CPUs maintain cache coherency via hardware protocols (MESI: Modified, Exclusive, Shared, Invalid):
- Data moves between main RAM and CPU L1/L2/L3 caches in discrete **64-byte Cache Lines**.
- **False Sharing:** Two threads running on separate CPU cores concurrently mutate distinct, unrelated variables that happen to reside within the identical 64-byte cache line. The hardware MESI protocol continuously invalidates and reloads the cache line across cores, creating catastrophic memory bus contention.
- *Mitigation:* Pad variables to 64 bytes or use `@jdk.internal.vm.annotation.Contended`.

---

## 2. Race Conditions & The Java Memory Model (JMM)

### 2.1 The Two Fundamental Race Conditions
1. **Check-Then-Act:** A thread reads a state variable, observes condition $C$, and proceeds to act. Concurrently, another thread mutates state, invalidating $C$ before the action executes (e.g., non-atomic lazy initialization).
2. **Read-Modify-Write:** Reading a variable, modifying it, and writing back (e.g., `count++`). In bytecode, `count++` expands to: 1. `getfield`, 2. `iadd`, 3. `putfield`. Interleaved thread execution results in lost updates.

### 2.2 Formal JMM Happens-Before Specification
To allow compilers and CPUs to execute instruction reordering and store buffering optimizations, Java specifies strict **Happens-Before** rules. If action $A$ happens-before action $B$, the memory mutations of $A$ are guaranteed visible to $B$:
1. **Program Order Rule:** Each action in a single thread happens-before every subsequent action in that thread according to program order.
2. **Monitor Lock Rule:** An unlock on a monitor lock happens-before every subsequent lock acquisition on that same monitor.
3. **Volatile Variable Rule:** A write to a `volatile` field happens-before every subsequent read of that same `volatile` field.
4. **Thread Start Rule:** A call to `Thread.start()` happens-before any action in the started thread.
5. **Thread Termination Rule:** Any action in a thread happens-before any other thread successfully detects that thread has terminated (via `Thread.join()` or `Thread.isAlive()`).
6. **Transitivity Rule:** If $A$ happens-before $B$, and $B$ happens-before $C$, then $A$ happens-before $C$.

*Volatile Invariant:* Declaring a field `volatile` guarantees **visibility** and **ordering** (inserting hardware StoreLoad and LoadLoad memory fences). It **does not guarantee atomicity** for compound operations (`count++` remains thread-unsafe).

---

## 3. Synchronization Primitives & Lock Dynamics

### 3.1 `synchronized` vs. `ReentrantLock` vs. `StampedLock`
- **`synchronized`:** Java language keyword. Reentrant. JVM optimizes locks via: Biased Locking $\to$ Lightweight Locking (CAS in stack) $\to$ Heavyweight OS Mutex.
- **`ReentrantLock`:** Standard API lock. Supports timeouts (`tryLock(timeout)`), interruptible acquisition, and fair scheduling policies.
- **`StampedLock`:** Provides **Optimistic Reading**. A reader acquires a numeric stamp, reads fields without acquiring a mutual exclusion lock, and validates whether a write occurred:
  ```java
  long stamp = lock.tryOptimisticRead();
  int curX = x, curY = y;
  if (!lock.validate(stamp)) { // If write occurred, upgrade to full read lock
      stamp = lock.readLock();
      try {
          curX = x; curY = y;
      } finally {
          lock.unlockRead(stamp);
      }
  }
  ```

### 3.2 Coffman's 4 Deadlock Conditions
Deadlock occurs iff: 1. Mutual Exclusion, 2. Hold and Wait, 3. No Preemption, 4. Circular Wait.
*Universal Solution:* Enforce a **Strict Global Lock Acquisition Order** across all threads (e.g., always acquire Account A lock before Account B lock using `account.id` sorting).

---

## 4. Atomic Variables, CAS & Thread Pools

### 4.1 Compare-And-Swap (CAS) & The ABA Problem
- **CAS:** Lock-free atomic hardware instruction (`CMPXCHG`). Atomically updates a memory location iff it matches an expected old value:
  $$\text{CAS}(V, E, N): \text{if } *V == E \implies *V = N \text{ and return true; else return false}$$
- **The ABA Problem:** A thread reads value $A$. Concurrently, another thread changes $A \to B \to A$. The original thread executes CAS, succeeds, but misses intermediate mutations.
- *Solution:* Use `AtomicStampedReference`, coupling value pointers with a monotonically increasing integer version stamp.

### 4.2 Brian Goetz Thread Pool Sizing Formulas
- **CPU-Bound Tasks:** Extra threads beyond core count incur context switch overhead without increasing compute throughput:
  $$N_{\text{threads}} = N_{\text{CPU}} + 1$$
- **I/O-Bound Tasks:** Threads spend substantial time blocked waiting on network/disk I/O:
  $$N_{\text{threads}} = N_{\text{CPU}} \times U_{\text{CPU}} \times \left(1 + \frac{W}{C}\right)$$
  where $U_{\text{CPU}}$ is target CPU utilization ($0 \le U \le 1$), $W$ is wait time (blocked on I/O), and $C$ is compute time. If tasks spend $90\%$ of time waiting on database queries ($W/C = 9$), a 16-core CPU requires $16 \times 1 \times (1 + 9) = 160$ threads.

### 4.3 Virtual Threads (Project Loom) & The Pinning Hazard
Java 21 introduces user-mode **Virtual Threads** managed by the JVM runtime rather than the OS:
- Millions of virtual threads can run concurrently on a small pool of carrier OS threads (`ForkJoinPool`).
- **The Thread Pinning Hazard:** When a virtual thread executes inside a `synchronized` block or invokes a native method (JNI), it becomes **pinned** to its underlying carrier OS thread. If it initiates blocking I/O while pinned, the OS carrier thread is blocked, defeating the entire purpose of virtual threads.
- *Mitigation:* Replace all `synchronized` blocks in I/O paths with `ReentrantLock`.

---

## 5. Distributed Systems: Clocks, Order & Consistency Models

### 5.1 Physical Clocks vs. Logical Clocks
- **Physical Clock Drift:** NTP synchronization drifts by tens of milliseconds. Relying on physical `System.currentTimeMillis()` for event ordering across servers creates race conditions and lost data. Use monotonic timers (`System.nanoTime()`) strictly for elapsed duration measurements.
- **Lamport Timestamps:** Assigns monotonically increasing integer counters to events, establishing a partial causal ordering.
- **Vector Clocks:** An array of size $N$ (number of nodes). Captures causal relationships and identifies **concurrent conflicts** that require application-level reconciliation.

### 5.2 Consistency Models Hierarchy
```
Linearizability (Strongest: Real-time global order on single registers)
      |
      v
Sequential Consistency (Operations executed in program order across all nodes)
      |
      v
Causal Consistency (Causally related operations seen in identical order)
      |
      v
Eventual Consistency (Weakest: Nodes converge only after writes cease)
```
- **Linearizability:** Guarantees that if Client A finishes writing Value $X$ at time $t_1$, any client read initiated after $t_1$ must return Value $X$ or a newer write.
- **Serializability:** Multi-operation transaction isolation guarantee. Transactions execute as if in some serial sequence, but provides no real-time recency guarantees.
- **Strict Serializability:** Combines Serializability and Linearizability.

---

## 6. Quorum Consensus & The PACELC Theorem

### 6.1 Quorum Consensus Mathematics
In a distributed storage cluster with $N$ replicas:
- $W$ = Number of replicas that must acknowledge a write before success.
- $R$ = Number of replicas that must respond to a read.
- **Strict Quorum Condition:**
  $$R + W > N$$
  Guarantees that the read set and write set must overlap by at least one replica, ensuring the client reads the latest version (reconciled via version vector or timestamp).
- *Weak Quorums ($R + W \le N$):* Faster writes/reads; risk of reading stale data.

### 6.2 CAP vs. PACELC Theorems
- **CAP Theorem:** Under a Network Partition ($P$), a system must trade off between Availability ($A$) and Consistency ($C$).
- **PACELC Theorem (Extends CAP):**
  $$\text{If Partition (P)} \implies \text{Choose between Availability (A) and Consistency (C)}$$
  $$\text{Else (E)} \implies \text{Choose between Latency (L) and Consistency (C)}$$
  Examples: DynamoDB/Cassandra are **PA/EL** (favor availability and latency); PostgreSQL/Spanner are **PC/EC** (favor consistency always).

---

## 7. Distributed Locks, Leases & Fencing Tokens

### 7.1 The Distributed Locking Hazard & Fencing Tokens
Timeouts and heartbeats cannot guarantee distributed mutual exclusion because network delays or Stop-the-World GC pauses can freeze a process after lock verification:

```
Client 1                   Lock Service (Zookeeper/etcd)                 Storage Tier
   |                                     |                                     |
   | Acquire Lock                        |                                     |
   |------------------------------------>|                                     |
   | Lock Granted (Lease = 10s)          |                                     |
   |<------------------------------------|                                     |
   |                                     |                                     |
   | [Client 1 enters 15s GC Pause!]     |                                     |
   | (Lock expires in Lock Service)      |                                     |
   |                                     |                                     |
   |                   Client 2          |                                     |
   |                      | Acquire Lock |                                     |
   |                      |------------->|                                     |
   |                      | Lock Granted |                                     |
   |                      |<-------------|                                     |
   |                      | Write Data (Token = 34)                            |
   |                      |--------------------------------------------------->|  (Saved!)
   |                                                                           |
   | [Client 1 wakes up from GC!]                                              |
   | Writes Data (Zombie Write with expired lock!)                             |
   |-------------------------------------------------------------------------->|  (DATA CORRUPTED!)
```

### 7.2 The Fencing Token Proof
To guarantee absolute safety, the distributed lock service must issue a **monotonically increasing Fencing Token** with every lock acquisition ($1, 2, 3...$). The storage tier validates the token and strictly rejects any write bearing a token lower than the highest token observed:
$$\text{If } \text{token}_{\text{write}} < \text{token}_{\text{max}} \implies \text{REJECT WRITE}$$

---

## 8. Consensus Algorithms (Raft) & 2PC

### 8.1 The Raft Consensus Algorithm
Raft decomposes consensus into three independent sub-problems:
1. **Leader Election:** If a follower receives no heartbeats within a randomized election timeout (150–300 ms), it transitions to Candidate, increments term, and requests votes. A candidate wins if it receives votes from a majority of nodes ($\lfloor N/2 \rfloor + 1$). Randomized timeouts prevent split-vote ties.
2. **Log Replication:** Leader accepts client writes, appends to its log, and sends `AppendEntries` RPCs to followers.
3. **Commit Rule:** Once an entry is replicated on a majority of nodes, the leader commits it and applies it to its state machine.

### 8.2 Two-Phase Commit (2PC) vs. Distributed Sagas
- **Two-Phase Commit (2PC):** Phase 1 (Prepare): Coordinator asks participants to vote and promise to commit. Phase 2 (Commit): Coordinator instructs commit iff all voted yes.
  - *Catastrophic Limitation:* 2PC is a **blocking protocol**. If the coordinator crashes after participants vote YES, participants hold locks indefinitely, freezing databases.
- **Sagas:** Sequence local ACID transactions. Scale infinitely better than 2PC across microservice boundaries.

---

## 9. Failure Modes & Incident Recovery Matrix

| Failure Mode | Root Cause | Impact | Preventive Architecture |
| :--- | :--- | :--- | :--- |
| **Virtual Thread Pinning** | `synchronized` blocks inside virtual thread I/O routines. | Carrier OS threads blocked, freezing thread pool. | Replace `synchronized` with `ReentrantLock`. |
| **Split-Brain** | Network partition isolates cluster into two partitions; both elect leaders. | Divergent data written to both sides; irreversible corruption. | Require strict majority quorum ($\lfloor N/2 \rfloor + 1$) for leader election. |
| **Zombie Write** | Process delayed by GC pause writes data after distributed lock expired. | Data overwritten out of order. | Enforce monotonic Fencing Tokens validated at storage engine. |
| **Deadlock** | Inconsistent lock acquisition ordering across concurrent services. | Threads blocked indefinitely in `WAITING` state. | Enforce strict deterministic global lock ordering; use `tryLock` timeouts. |
---

## 10. High-Bar Concurrency & Distributed Systems Scenarios

### Scenario 1: Why Volatile Cannot Implement an Atomic Counter
- **Interview Question:** "Why is declaring `private volatile int counter = 0` insufficient for implementing a thread-safe increment operation (`counter++`) in Java?"
- **Deep Technical Answer:**
  1. `volatile` enforces memory visibility (CPU cache flush/invalidation) and instruction ordering (memory barriers).
  2. `counter++` is **not an atomic operation**. It compiles into three distinct bytecode instructions:
     - `GETFIELD`: Reads current value from heap into thread operand stack.
     - `IADD`: Increments value by 1 on stack.
     - `PUTFIELD`: Writes modified value back to heap.
  3. **Lost Updates:** Two threads concurrently execute `GETFIELD`, both reading value `5`. Both increment to `6`, and both write `6` back to the heap. Two increments executed, but counter increased by only 1.
  4. **Solution:** Use hardware-supported atomic primitives (`AtomicInteger.incrementAndGet()` using CAS) or `synchronized`.

### Scenario 2: Strict Quorum Math in Distributed Key-Value Stores
- **Interview Question:** "In a distributed storage cluster with replication factor $N = 5$, what read ($R$) and write ($W$) quorums guarantee strong linearizable consistency, and what happens during a network partition?"
- **Deep Technical Answer:**
  1. **Strict Quorum Condition:** Strong consistency requires:
     $$R + W > N$$
     Configuring $W = 3$ and $R = 3 \implies 3 + 3 = 6 > 5$. Any read quorum of 3 nodes is mathematically guaranteed to intersect with at least one node containing the latest write.
  2. **Network Partition (PACELC):** If a network split isolates 2 nodes from 3 nodes:
     - The majority partition (3 nodes) satisfies $W \ge 3$ and continues accepting reads and writes.
     - The minority partition (2 nodes) cannot reach quorum ($2 < 3$) and rejects writes, preserving consistency and preventing split-brain corruption.

### Scenario 3: Diagnosing Virtual Thread Carrier Pinning
- **Interview Question:** "After migrating a Spring Boot application to Java 21 Virtual Threads, throughput plummets under load. What diagnostic steps identify carrier thread pinning?"
- **Deep Technical Answer:**
  1. **Root Cause:** A virtual thread executed a blocking operation inside a `synchronized` block or native JNI call, **pinning** the virtual thread to its underlying OS carrier thread (`ForkJoinPool` worker).
  2. When all carrier threads become pinned and blocked, the entire JVM runtime freezes, unable to schedule remaining virtual threads.
  3. **Diagnostic:** Launch JVM with the system property:
     ```bash
     -Djdk.tracePinnedThreads=full
     ```
     Prints stack traces whenever a virtual thread pins its carrier thread during a blocking call.
  4. **Remedy:** Refactor legacy code replacing `synchronized` with `ReentrantLock`.
---

## 8. Deep-Dive Consensus Mechanics & Synchronization Patterns

### 8.1 The Raft Consensus State Machine
Raft decomposes multi-node distributed consensus into formal state transitions:
```
           +-----------------------------------------------+
           |                                               | (Times out,
           v                                               |  starts election)
     +------------+      Times out, starts election   +---------------+
     |  FOLLOWER  | --------------------------------> |   CANDIDATE   |
     +------------+                                   +---------------+
           ^                                               |
           | Steps down (discovers higher term)            | Wins election
           |                                               v
           +----------------------------------------- +---------------+
                                                      |    LEADER     |
                                                      +---------------+
```
- **Raft Safety Invariants:**
  1. **Election Safety:** At most one leader can be elected per term ($Term$). A candidate must receive votes from a strict majority ($\lfloor N/2 \rfloor + 1$) of nodes.
  2. **Leader Append-Only:** A leader never overwrites or truncates its own log entries; it only appends new entries.
  3. **Log Matching Property:** If two logs contain an entry with the same index and term, then the logs are identical in all entries up through the given index.
  4. **Leader Completeness:** If a log entry is committed in a given term, that entry will be present in the logs of the leaders for all higher-numbered terms. Candidates with less up-to-date logs are denied votes.

### 8.2 Brian Goetz Bounded Buffer Implementation
A canonical high-bar interview implementation of a thread-safe, bounded FIFO buffer using explicit `ReentrantLock` and two `Condition` variables:

```java
public class BoundedBuffer<T> {
    private final Object[] items;
    private int tail, head, count;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public BoundedBuffer(int capacity) {
        items = new Object[capacity];
    }

    public void put(T x) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length) { // MANDATORY: while loop prevents spurious wakeups!
                notFull.await();
            }
            items[tail] = x;
            if (++tail == items.length) tail = 0;
            count++;
            notEmpty.signal(); // Signal waiting consumers
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) { // While loop invariant
                notEmpty.await();
            }
            T x = (T) items[head];
            items[head] = null; // Prevent memory leak
            if (++head == items.length) head = 0;
            count--;
            notFull.signal(); // Signal waiting producers
            return x;
        } finally {
            lock.unlock();
        }
    }
}
```
*The While-Loop Wait Invariant:* Threads must **always evaluate condition predicates in a `while` loop**, never an `if` block. This defends against **Spurious Wakeups** (operating system waking threads without a signal) and thread state races where another thread consumes the item before the awakened thread acquires the lock.
