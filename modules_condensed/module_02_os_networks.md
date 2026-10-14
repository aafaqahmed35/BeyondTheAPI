# Module 02: Operating Systems and Computer Networks

## 1. Operating System Fundamentals & Kernel Architecture

The Operating System provides hardware abstraction, resource isolation, and time-sharing execution across processes.

### 1.1 Dual-Mode Execution & System Calls
Modern CPUs enforce execution privileges via hardware rings:
- **Kernel Mode (Ring 0):** Unrestricted access to physical memory, CPU control registers (CR0, CR3), device I/O ports, and interrupt descriptors.
- **User Mode (Ring 3):** Restricted address space. Direct hardware manipulation causes a General Protection Fault (`#GP`).

Transitioning from User to Kernel mode occurs strictly through controlled hardware mechanisms:
1. **Interrupts:** Asynchronous hardware signals (e.g., NIC packet arrival, timer ticks).
2. **Exceptions / Traps:** Synchronous hardware events triggered by execution (e.g., divide-by-zero, page fault).
3. **System Calls (`syscall` / `sysenter`):** Programmatic requests for OS services (file I/O, network sockets, process spawning). The CPU saves the user Program Counter (`RIP`) and Stack Pointer (`RSP`), elevates to Ring 0, indexes the kernel's Interrupt Descriptor Table (IDT), and dispatches to the registered handler.

### 1.2 Process Control Block (PCB) vs. Thread Control Block (TCB)
- **Process:** The fundamental unit of resource allocation and isolation. Owns a distinct virtual address space, file descriptor table, signal handlers, IPC handles, and security tokens. Represented in Linux by `struct task_struct`.
- **Thread:** The fundamental unit of CPU scheduling and execution dispatch. Shares its parent process's virtual address space, open files, and signal handlers, but maintains a private **TCB**: registers, program counter, and user/kernel execution stacks.

```
+-------------------------------------------------------+
|  Process Virtual Address Space (e.g., 48-bit x86-64)  |
|  +-------------------------------------------------+  |
|  | User Stack (grows downward)                     |  |
|  |   Thread 1 Stack        Thread 2 Stack          |  |
|  |         |                     |                 |  |
|  |         v                     v                 |  |
|  | ----------------- Unmapped Hole ----------------|  |
|  |         ^                                       |  |
|  |         |                                       |  |
|  | Heap (grows upward via brk/mmap)                |  |
|  +-------------------------------------------------+  |
|  | BSS (Uninitialized static variables, zero-filled)|  |
|  +-------------------------------------------------+  |
|  | Data (Initialized global & static variables)    |  |
|  +-------------------------------------------------+  |
|  | Text (Read-only machine instructions)           |  |
|  +-------------------------------------------------+  |
+-------------------------------------------------------+
```

---

## 2. CPU Scheduling & Concurrency Synchronization

### 2.1 Context Switch Costs: Direct vs. Indirect
Switching CPU execution between threads incurs dual performance penalties:
1. **Direct Overhead (1–5 µs):** Saving register state, flushing pipeline, switching kernel stacks, and switching page table pointers in the CR3 register (for inter-process switches).
2. **Indirect Overhead (10–100 µs):**
   - **TLB Invalidation:** Switching address spaces flushes the Translation Lookaside Buffer (unless Address Space Identifiers / PCID are enabled).
   - **CPU Cache Pollution:** The incoming thread evicts the previous thread's hot L1/L2 data from cache lines, causing immediate bursts of cache misses.

### 2.2 Completely Fair Scheduler (CFS)
Linux default scheduler (CFS) models an "ideal multi-tasking CPU" using a time-ordered **Red-Black Tree** of runnable tasks:
- **Virtual Runtime (`vruntime`):** Measures execution time scaled by task priority:
  $$\text{vruntime} \mathrel{+}= \Delta \text{exec\_time} \times \left(\frac{\text{NICE\_0\_LOAD}}{\text{task\_weight}}\right)$$
- CFS picks the leftmost node (minimum `vruntime`) in $O(1)$ time, guaranteeing starvation-free proportional allocation.
- Nice values range from $-20$ (highest priority, highest weight) to $+19$ (lowest priority).

### 2.3 Synchronization Primitives: Spinlocks, Mutexes & Futexes
- **Spinlock:** Busy-waits in a tight loop executing `PAUSE` / atomic test-and-set. Zero context-switch cost; burns 100% CPU. Optimal only for short critical sections ($< 1$ context switch duration) in kernel space.
- **Mutex:** Deschedules the contending thread if unavailable, yielding the CPU to other tasks. Context switch cost incurred on contention.
- **Futex (Fast Userspace Mutex):** Linux optimization. Executes an atomic Compare-And-Swap (CAS) in userspace for the uncontented fast path (0 kernel transitions). Enters Ring 0 via `sys_futex` only when contention requires thread sleep or wake.
- **Priority Inversion & Inheritance:** A low-priority thread holding a lock preempted by a medium thread starves a high-priority thread waiting on the lock. **Priority Inheritance Protocol** solves this by temporarily promoting the lock holder to the priority of the highest waiting thread until release.
- **Coffman's 4 Deadlock Conditions:** Deadlock occurs iff all 4 hold simultaneously: 1. Mutual Exclusion, 2. Hold and Wait, 3. No Preemption, 4. Circular Wait. Eliminating any single condition prevents deadlock (e.g., strict global lock ordering eliminates circular wait).

---

## 3. Memory Management, Virtual Memory & Paging

### 3.1 x86-64 4-Level Paging & The TLB
Virtual addresses abstract physical RAM. On x86-64, a 48-bit virtual address is mapped to a 4KB physical page frame via 4 hierarchical page table levels:
$$\text{Virtual Address} \implies \text{PML4 (9b)} \to \text{PDPT (9b)} \to \text{PD (9b)} \to \text{PT (9b)} + \text{Offset (12b)}$$
- **Translation Lookaside Buffer (TLB):** Hardware associative cache on the CPU storing recent Virtual $\to$ Physical translations. A TLB hit resolves in $< 1$ ns. A TLB miss forces a 4-level memory walk (tens of nanoseconds).
- **HugePages (2MB / 1GB):** Collapses page table hierarchy to 2 or 3 levels. Reduces TLB footprint for large JVM heaps and database buffer pools by up to $512\times$, cutting TLB misses.

### 3.2 Page Faults & The OOM Killer
- **Minor (Soft) Page Fault:** Virtual page allocated via `malloc()` / `mmap()` has no physical frame mapped yet. Kernel allocates a frame from free list, updates page table, and resumes. Zero disk I/O.
- **Major (Hard) Page Fault:** Virtual page was swapped to disk. Kernel blocks thread, initiates asynchronous disk read, loads page into RAM, updates page table, and unblocks thread. Incurs massive 5–15 ms disk I/O penalty.
- **OOM Killer:** When physical memory and swap are exhausted, the kernel invokes `oom_badness()` to select and terminate a rogue process based on memory consumption and `oom_score_adj`:
  $$\text{Points} = \frac{\text{RSS} + \text{swap\_usage}}{\text{Total RAM}} \times 1000 + \text{oom\_score\_adj}$$

---

## 4. File I/O & High-Performance Event Demultiplexing

### 4.1 Virtual Filesystem (VFS) & File Descriptors
Linux abstracts all I/O as files via VFS:
- **File Descriptor (FD):** Non-negative integer index into the process's open file table. FD 0 = stdin, 1 = stdout, 2 = stderr.
- **File Table Entry:** Shared across cloned processes. Stores current file offset, access mode, and pointer to inode.
- **Inode:** Physical metadata on disk (file size, permissions, owner, block pointers). Filename is stored in directory entries (`dentry`), not the inode.

### 4.2 I/O Models: `select` vs. `poll` vs. `epoll`

| Mechanism | Kernel Scalability | Scan Cost per Event | Max FD Limit | Event Model |
| :--- | :---: | :---: | :---: | :--- |
| **`select()`** | $O(N)$ linear array | $O(N)$ userspace scan | 1024 (`FD_SETSIZE`) | Level-triggered bitmap |
| **`poll()`** | $O(N)$ pollfd list | $O(N)$ userspace scan | Unbounded (memory limit) | Level-triggered struct array |
| **`epoll()`** | $O(1)$ event-driven | $O(\text{active})$ direct return | Unbounded (RAM limit) | Edge- or Level-triggered red-black tree + ready list |

- **Edge-Triggered (`EPOLLET`) Invariant:** `epoll_wait()` notifies only on state transitions (e.g., new data arrival). The application **must** use non-blocking sockets and loop `read()` until receiving `EAGAIN` or `EWOULDBLOCK`. Failing to drain the buffer results in hung sockets because no future notifications fire.

---

## 5. Computer Networks: Protocols, Lifecycle & TCP Internals

### 5.1 Protocol Layers & Encapsulation
Data traverses the stack by prepending protocol headers:
$$\text{Application Data} \to \text{TCP Header (20B)} \to \text{IP Header (20B)} \to \text{Ethernet Frame (14B + 4B FCS)}$$
- **MTU (Maximum Transmission Unit):** Standard Ethernet payload limit is 1500 bytes.
- **MSS (Maximum Segment Size):** $\text{MSS} = \text{MTU} - (\text{IP Header} + \text{TCP Header}) = 1500 - 40 = 1460$ bytes. Packets exceeding MTU are fragmented or dropped (Path MTU Discovery).

### 5.2 TCP 3-Way Handshake & 4-Way Teardown
```
    Client                                  Server
      |               SYN (seq=x)              |
      |--------------------------------------->|  Server enters SYN_RECEIVED
      |                                        |  (Listen backlog queues)
      |          SYN-ACK (seq=y, ack=x+1)      |
      |<---------------------------------------|  Client enters ESTABLISHED
      |                                        |
      |               ACK (ack=y+1)            |
      |--------------------------------------->|  Server enters ESTABLISHED
      |                                        |
      |           DATA EXCHANGE (Full Duplex)  |
      |                                        |
      |               FIN (seq=u)              |
      |--------------------------------------->|  Server sends ACK (u+1)
      |               ACK (ack=u+1)            |  Server enters CLOSE_WAIT
      |<---------------------------------------|
      |               FIN (seq=v)              |  Server ready to close
      |<---------------------------------------|
      |               ACK (ack=v+1)            |  Client enters TIME_WAIT (2MSL)
      |--------------------------------------->|  Server enters CLOSED
```

- **SYN Flood Attack & SYN Cookies:** Malicious clients send floods of SYN packets without completing the handshake, exhausting server connection backlogs. **SYN Cookies** encode connection state into the initial sequence number $y$, allowing the server to avoid allocating memory until the client's final ACK arrives.
- **`TIME_WAIT` State (2MSL = 120s):** The terminating endpoint must linger in `TIME_WAIT` for two Maximum Segment Lifetimes. Invariants:
  1. Guarantees the final ACK is resent if lost, preventing the remote server from being stuck in `LAST_ACK`.
  2. Ensures delayed duplicate packets from old connections expire in transit rather than corrupting new sockets using the same `(IP, Port)` tuple.

### 5.3 Flow Control vs. Congestion Control
- **Flow Control (End-to-End):** Prevents sender from overwhelming receiver buffer. The receiver advertises a Receive Window (`rwnd`) in every ACK header. Sender transmits at most $\min(\text{cwnd}, \text{rwnd})$.
- **Congestion Control (Network Core):** Prevents sender from overloading intermediate routers:
  - **TCP Reno:** Slow Start (exponential cwnd growth) $\to$ Congestion Avoidance (linear AIMD: Additive Increase Multiplicative Decrease) $\to$ Fast Retransmit / Fast Recovery on 3 duplicate ACKs. Drops cwnd to half on loss.
  - **TCP CUBIC:** Linux default. Uses a cubic function of time since last congestion event, accelerating probing when far from previous ceiling.
  - **TCP BBR (Bottleneck Bandwidth and RTT):** Google model-based congestion control. Ignores packet loss; actively probes Maximum Bandwidth and Minimum RTT to avoid bufferbloat at routers.
- **Nagle's Algorithm & Delayed ACK:** Nagle buffers small writes until outstanding data is ACKed. Delayed ACK waits up to 200ms before sending standalone ACK. Combined, they create a catastrophic **40–200ms latency penalty** on request-response protocols. Remedy: Disable Nagle via `socket.setTcpNoDelay(true)`.

---

## 6. DNS, HTTP Evolution & TLS 1.3

### 6.1 DNS Resolution Chain
Hierarchical distributed lookup: Client Stub Resolver $\to$ Local Recursive Resolver $\to$ Root DNS (`.`) $\to$ TLD DNS (`.com`) $\to$ Authoritative DNS (`example.com`).
- Records: `A` (IPv4), `AAAA` (IPv6), `CNAME` (canonical alias), `MX` (mail), `TXT` (SPF/DKIM).
- Cached at OS and resolver levels bounded by `TTL` (Time To Live).

### 6.2 HTTP/1.1 vs. HTTP/2 vs. HTTP/3
- **HTTP/1.1:** Text-based. Persistent connections via `Keep-Alive`. Suffers from **Head-of-Line (HoL) Blocking** at application layer: only one outstanding request per TCP connection at a time. Browsers open 6 parallel TCP connections per host.
- **HTTP/2:** Binary framing layer. Multiplexes concurrent request/response streams over a single TCP connection. Stream prioritization, Server Push, and HPACK header compression. *Limitation:* TCP-level packet loss drops all streams simultaneously (TCP HoL blocking).
- **HTTP/3 (QUIC):** Replaces TCP with QUIC over UDP. Multiplexed streams are independent at transport layer—packet loss on Stream 1 does not stall Stream 2. Eliminates head-of-line blocking entirely. Supports **Connection Migration** using 64-bit Connection IDs (survives client IP changes from Wi-Fi to cellular).

### 6.3 TLS 1.3 Cryptographic Handshake
TLS 1.3 slashes handshake latency from 2-RTT (TLS 1.2) to **1-RTT**:
```
    Client                                  Server
      |  ClientHello                          |
      |  + Supported Ciphers                  |
      |  + Key Share (Client ECDH pubkey)     |
      |-------------------------------------->|
      |                                       |  Server calculates shared secret
      |  ServerHello                          |  Derives encryption keys
      |  + Selected Cipher                    |
      |  + Key Share (Server ECDH pubkey)     |
      |  {EncryptedExtensions}                |
      |  {Certificate}                        |
      |  {CertificateVerify (Digital Sig)}    |
      |  {Finished (HMAC)}                    |
      |<--------------------------------------|
      |                                       |
      |  {Finished (HMAC)}                    |
      |  [Application Data (Encrypted)]       |
      |<=====================================>|  1-RTT Total Handshake
```
- **Forward Secrecy:** Ephemeral Diffie-Hellman keys (`ECDHE`) ensure that compromising server private keys in the future cannot decrypt recorded past traffic.

---

## 7. Latency Numbers & Network Diagnostic Commands

### 7.1 Latency Numbers Every Systems Engineer Must Know
| Operation | Latency | Scaled Comparison (1ns = 1 sec) |
| :--- | :---: | :---: |
| **L1 CPU Cache Reference** | 0.5–1 ns | 1 second |
| **Branch Mispredict** | 3–5 ns | 3–5 seconds |
| **L2 CPU Cache Reference** | 4–7 ns | 7 seconds |
| **Mutex Lock / Unlock (Futex uncontented)** | 17–25 ns | 20 seconds |
| **Main Memory (RAM) Access** | 100 ns | 1.6 minutes |
| **SSD Random Read (NVMe)** | 10–20 µs | 3.5 hours |
| **SATA SSD Block Read** | 100–150 µs | 1.5 days |
| **Rotational HDD Seek** | 5–10 ms | 1.5 months |
| **Same-Datacenter Network Round-Trip** | 0.5 ms | 6 days |
| **Cross-Country RTT (NY to SF)** | 40–45 ms | 1.3 years |
| **Transatlantic RTT (NY to London)** | 70–80 ms | 2.5 years |

### 7.2 Diagnostic Command Reference
```bash
# Sockets & Ports: List listening TCP ports with process names
ss -tulpn

# Active connections: Find connections to port 5432
ss -tan 'sport = :5432 or dport = :5432'

# Trace system calls executed by process 1234
strace -f -e trace=network,file -p 1234

# Capture live HTTP packets on eth0 interface
tcpdump -i eth0 -nn -s0 -A 'tcp port 80 or tcp port 443'

# Detailed DNS resolution tracing
dig +trace api.example.com
```

---

## 8. Failure Modes, Diagnostic Signatures & Solutions

| Failure Mode | Diagnostic Symptom | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **Port Exhaustion** | `bind: address already in use` | Outgoing connections closed by client linger in `TIME_WAIT`, exhausting ephemeral port range (32768–60999). | Enable `net.ipv4.tcp_tw_reuse = 1` or use connection pooling. |
| **SYN Backlog Overflow** | `TCP: request_sock_TCP: Possible SYN flooding on port 80. Dropping request.` | Rate of incoming SYNs exceeds `net.core.somaxconn` and `listen(fd, backlog)`. | Increase `somaxconn` and backlog; ensure `net.ipv4.tcp_syncookies = 1`. |
| **OOM Killing** | Process terminated with Exit Code 137; `dmesg` reports `Out of memory: Killed process`. | Process memory exceeded available RAM + swap, scoring highest in `oom_badness()`. | Adjust memory limits, tune `vm.overcommit_memory = 2`, fix leaks. |
| **High Load Avg, Low CPU** | Load average $> 20$, but `top` reports CPU usage $< 15\%$. | Threads blocked in Uninterruptible Sleep (`D` state) waiting on disk I/O or NFS storage. | Inspect disk I/O with `iostat -xz 1` and check `wa` (iowait) percentage in `top`. |
| **40ms Latency Spikes** | Request latencies systematically cluster around 40ms or 200ms. | Interaction between Nagle's algorithm (`TCP_NODELAY` off) and TCP Delayed ACK. | Disable Nagle on client & server sockets via `TCP_NODELAY`. |
---

## 9. High-Bar Systems Interview Scenarios

### Scenario 1: Hardware-Level Cache Invalidation Across CPU Cores
- **Interview Question:** "Explain what happens at the hardware and kernel level when two threads on separate CPU cores concurrently update two fields in the same Java object."
- **Deep Technical Answer:**
  1. The fields reside in adjacent memory offsets within the identical **64-byte Cache Line**.
  2. When Core 1 writes to Field A, its L1 cache controller issues an `Invalidate` signal across the CPU interconnect bus via the **MESI Protocol**.
  3. Core 2's cache line holding Field B transitions from Shared (`S`) to Invalid (`I`).
  4. When Core 2 attempts to write to Field B, it suffers a cache miss, stalls execution, and forces a cache-line reload from L3/RAM.
  5. This ping-ponging ("False Sharing") destroys multi-core parallelism, causing latency spikes without high CPU utilization. Fix via `@Contended` padding.

### Scenario 2: Sockets Stuck in `CLOSE_WAIT` State
- **Interview Question:** "A production backend monitoring dashboard alerts that 10,000 TCP sockets are stuck in `CLOSE_WAIT`. What is the root cause and how do you resolve it?"
- **Deep Technical Answer:**
  1. **TCP State Machine Reality:** `CLOSE_WAIT` occurs on the **receiver of the FIN**. The remote peer has initiated connection closure; the local OS kernel acknowledged with ACK.
  2. The socket remains in `CLOSE_WAIT` until the **local application explicitly calls `close()`** on the socket file descriptor.
  3. **Root Cause:** A programming bug in the local application's error handling. Sockets caught in exception paths or blocked in worker threads are never closed.
  4. **Diagnostic:** Inspect with `ss -tan state close-wait -p` to identify the responsible PID. The fix requires patching application code with guaranteed `try-with-resources` socket closure.

### Scenario 3: The 40ms Latency Spike on Small Request Bursts
- **Interview Question:** "A high-performance API client experiences inexplicable 40ms to 200ms latency spikes when sending small JSON requests. What causes this?"
- **Deep Technical Answer:**
  1. **Nagle's Algorithm:** Buffers small outgoing packets until previous packets are ACKed to avoid transmitting 40-byte headers for 1-byte payloads.
  2. **TCP Delayed ACK:** The receiving kernel delays sending standalone ACKs for up to 40–200ms, hoping to piggyback the ACK on outgoing response data.
  3. **The Deadlock Collision:** The client waits for an ACK before sending remaining data (Nagle), while the server waits for more data before sending an ACK (Delayed ACK). Both sides idle until the Delayed ACK timer expires.
  4. **Fix:** Disable Nagle's algorithm on all application sockets: `socket.setTcpNoDelay(true)`.
