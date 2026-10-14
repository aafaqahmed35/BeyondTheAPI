# Module 13: Linux, Containers, Deployment and Infrastructure

## 1. Linux Operating System & Core Systems Engineering

Production backend workloads execute almost universally on Linux kernels.

### 1.1 Filesystem Hierarchy Standard (FHS)
- `/etc`: System-wide configuration files (`/etc/hosts`, `/etc/resolv.conf`).
- `/var`: Variable data (logs in `/var/log`, spool queues).
- `/proc`: Virtual pseudo-filesystem exposing real-time kernel and process states.
  - `/proc/sys/vm/overcommit_memory`: Controls virtual memory overcommit.
  - `/proc/<PID>/fd/`: Symbolic links to all open file descriptors for process.
  - `/proc/<PID>/net/tcp`: Active TCP socket table.
- `/sys`: Hardware and device driver abstraction filesystem (cgroups v2).

### 1.2 Robust Shell Scripting Invariants
All production Bash scripts must begin with the strict safety flags:
```bash
#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

# Trap for guaranteed temporary resource cleanup on exit or error
trap 'rm -rf "${TMP_DIR:-}"' EXIT
```
- `-e`: Exit immediately if any command returns non-zero status.
- `-u`: Treat unset variables as fatal errors.
- `-o pipefail`: Return exit code of the first failing command in a pipeline (e.g., `fail | ok` fails).

### 1.3 Permissions & The Directory Execute Bit
Linux permissions use octal notation: Read (4), Write (2), Execute (1).
- **The Directory Execute Bit Invariant:** For files, execute (`+x`) permits program execution. For **directories**, execute (`+x`) permits **entering the directory and accessing its inodes**. Without `+x` on a directory, a user cannot `cd` into it, open files inside it, or read file contents, even if the files have `chmod 777` permissions!

---

## 2. Process Lifecycle, Signals & System Triage

### 2.1 Process States & Signals
- **Process States:**
  - `R` (Running / Runnable): On CPU or in run queue.
  - `S` (Interruptible Sleep): Waiting for event/I/O; wakes on signals.
  - `D` (Uninterruptible Sleep): Waiting on physical disk I/O; **cannot be killed even with `SIGKILL`**.
  - `Z` (Zombie): Terminated process whose parent has not yet called `waitpid()` to read exit code. Consumes a slot in process table.
- **Signals:**
  - `SIGTERM` (Signal 15): Polite termination request. Application should execute graceful shutdown (flush buffers, finish in-flight requests, close sockets).
  - `SIGKILL` (Signal 9): Immediate unconditional termination by kernel. Cannot be caught, blocked, or ignored. Causes immediate process destruction.
  - `SIGHUP` (Signal 1): Terminal hangup; conventionally used by daemons to reload configuration files without restart.

### 2.2 CPU, Memory & Storage Diagnostics
```bash
# CPU Load Average: Processes in R or D state. If Load > CPU Cores, system is saturated.
uptime

# Memory & Swap: Check available RAM and whether swap is active
free -m

# Storage: Inspect disk space and Inode utilization (exhausting inodes causes "No space left on device")
df -h
df -i

# Deleted-but-open files: Processes holding disk space after files unlinked
lsof +L1

# Socket Diagnostics: High-speed TCP connection breakdown
ss -s
```

---

## 3. Containerization: Linux Namespaces & cgroups v2

Containers are not virtual machines; they are standard Linux processes isolated by kernel features:
1. **Linux Namespaces (Isolation Boundaries):**
   - `pid`: Process tree isolation (container process becomes PID 1).
   - `net`: Private IP address, routing table, port space, and `veth` pair.
   - `mnt`: Private filesystem mount points.
   - `ipc`: Inter-process communication isolation (semaphores, message queues).
   - `uts`: Hostname isolation.
   - `user`: Maps container root UID 0 to unprivileged host UID.
2. **Control Groups (cgroups v2 - Resource Governance):**
   - Meters and enforces hardware limits: `cpu.max` (CFS bandwidth quota), `memory.max` (hard ceiling), `memory.oom.group`.
3. **Container Exit Codes:**
   - Exit Code `0`: Clean termination.
   - Exit Code `137` ($128 + 9$): Killed by `SIGKILL`—almost always the **kernel OOM Killer** terminating a container that exceeded its `memory.max` limit.
   - Exit Code `143` ($128 + 15$): Terminated cleanly by `SIGTERM`.

---

## 4. Production Dockerfile Best Practices

```dockerfile
# Stage 1: Build Environment
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle .
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon

# Stage 2: Minimal Runtime Environment
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Run as unprivileged non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup
COPY --from=builder /workspace/build/libs/*.jar app.jar
# Explicit memory bounds respecting cgroups
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```
- **Layer Caching:** Order directives from least frequently changed to most frequently changed (dependencies before source code).
- **Non-Root Execution:** Never run container processes as `root`. A container breakout vulnerability grants the attacker root access to the host kernel.

---

## 5. Kubernetes Architecture & Workload Controllers

```
+------------------------------------------------------------------------+
| Control Plane: kube-apiserver <--> etcd (Consensus State Store)       |
|                 |             \--> kube-scheduler                      |
|                 \----------------> kube-controller-manager             |
+------------------------------------------------------------------------+
                               | (Watches / API Calls)
                               v
+------------------------------------------------------------------------+
| Worker Nodes: kubelet <--> Container Runtime (containerd) <--> Pods   |
|               kube-proxy (iptables / IPVS service routing)             |
+------------------------------------------------------------------------+
```

### 5.1 Workload Controllers
- **Pod:** Smallest atomic deployable unit; shares network namespace and IP.
- **Deployment & ReplicaSet:** Manages declarative rolling updates and replica counts for stateless services.
- **StatefulSet:** Manages stateful applications (PostgreSQL, Kafka). Provides:
  - Stable, unique network identity (`pod-0.service.default.svc.cluster.local`).
  - Ordered, sequential deployment and rolling updates.
  - Persistent volume binding that survives pod restarts.
- **DaemonSet:** Runs exactly one copy of a pod on every eligible node (monitoring agents, log forwarders).

---

## 6. Networking, Services & Ingress

- **Cluster IP Networking:** Every Pod gets its own routable IP. Pods communicate directly across nodes without NAT.
- **Service Types:**
  - `ClusterIP`: Stable internal Virtual IP (VIP) load-balancing across matching pod endpoints.
  - `NodePort`: Exposes static port (30000–32767) on all node physical IPs.
  - `LoadBalancer`: Automatically provisions cloud provider load balancer (AWS ALB, GCP NLB).
  - `ExternalName`: Maps service to external DNS CNAME.
- **Ingress Controller:** Layer 7 HTTP/HTTPS reverse proxy (Nginx, Envoy) managing SSL termination, path routing (`/api` $\to$ service A), and host routing.

---

## 7. Requests, Limits & Health Probes

### 7.1 Resource Requests vs. Limits
- **Requests (Scheduling Guarantee):** Used by `kube-scheduler` to find a node with sufficient unallocated CPU/Memory.
- **Limits (Enforced Ceilings):**
  - **CPU Limit:** Enforced via Linux CFS bandwidth quota. Exceeding CPU limit causes **CPU Throttling**, slowing execution, but does not terminate the pod.
  - **Memory Limit:** Enforced strictly. Exceeding memory limit causes kernel OOM Killer termination (**Exit Code 137**).

### 7.2 The 3 Health Probes
1. **Startup Probe:** Protects slow-starting applications (JVM boot, cache warm-up). Disables liveness and readiness checks until it succeeds.
2. **Liveness Probe:** Detects deadlocks and unrecoverable crashes. Failure restarts the container.
   - **CRITICAL ANTI-PATTERN:** Never check external dependencies (database, Redis, downstream APIs) in a Liveness Probe! If the database has a temporary network hiccup, all pods will fail liveness simultaneously, triggering a catastrophic cluster-wide restart storm!
3. **Readiness Probe:** Detects whether the pod is ready to accept incoming traffic. Failure temporarily removes the pod IP from the Service endpoints list.

---

## 8. Deployment Strategies: Blue-Green vs. Canary

- **RollingUpdate:** Default strategy. Gradually replaces old pods with new pods respecting `maxSurge` and `maxUnavailable`.
- **Blue-Green:** Deploys identical parallel environment (Green). Tests green thoroughly. Switches ingress router to green instantly. Fast rollback; requires $2\times$ hardware infrastructure.
- **Canary:** Routes small percentage of live user traffic (e.g., $5\%$) to new release. Observes error rates and latency. Progressively expands to $100\%$ if error budget is preserved.

---

## 9. Infrastructure Golden Rules & Failure Modes

| Failure Mode | Diagnostic Symptom | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **CrashLoopBackOff (137)** | Pod repeatedly terminates immediately after starting. | Container exceeded memory limit; terminated by kernel OOM Killer. | Increase `resources.limits.memory`; check JVM `-XX:MaxRAMPercentage`. |
| **Liveness Cascade Storm** | All pods restart continuously during downstream database blip. | Liveness probe queried external database directly. | Scope liveness probe strictly to internal health; use readiness for DB. |
| **CPU Throttling** | App latency spikes despite node CPU being $< 50\%$ utilized. | Container hit CFS bandwidth quota defined by `resources.limits.cpu`. | Remove CPU limits or increase quota (CPU is compressible). |
| **Zombie Container** | `docker stop` takes exactly 10 seconds, then exits with 137. | App running as PID 1 ignored `SIGTERM`; Docker killed with `SIGKILL`. | Use `tini` / dumb-init or handle `SIGTERM` in application code. |
---

## 10. High-Bar Infrastructure & DevOps Interview Scenarios

### Scenario 1: Diagnosing Pod Evictions and OOMKilled Exit Code 137
- **Interview Question:** "A Kubernetes pod repeatedly terminates with status `OOMKilled` and Exit Code 137. How do you triage and permanently resolve it?"
- **Deep Technical Answer:**
  1. **Exit Code Anatomy:** Exit code $137 = 128 + 9 (	ext{SIGKILL})$. The Linux kernel OOM Killer terminated the process because the container memory usage exceeded its cgroups `resources.limits.memory` limit.
  2. **Triage:**
     ```bash
     kubectl describe pod <pod-name> # Confirms "OOMKilled: true, Exit Code: 137"
     ```
  3. **JVM Root Cause:** In Java containers, the JVM heap is only a fraction of total memory. Metaspace, thread stacks ($1	ext{MB} 	imes 	ext{threads}$), direct byte buffers (Netty), and JIT code caches consume substantial off-heap RAM.
  4. **Resolution:**
     - Configure JVM container awareness: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`.
     - Increase Kubernetes container memory limit to provide headroom for off-heap allocations.

### Scenario 2: Uninterruptible Sleep (`D` State) and High Load Average
- **Interview Question:** "A Linux server reports a CPU Load Average of 48 on an 8-core CPU, but `top` reports CPU usage is only $12\%$. What is happening and how do you diagnose it?"
- **Deep Technical Answer:**
  1. **Load Average Definition:** Linux Load Average counts the number of processes in `R` (Running) state **plus processes in `D` (Uninterruptible Sleep) state**.
  2. Processes in `D` state are blocked waiting on hardware I/O (typically disk I/O, NFS mounts, or kernel mutexes). They consume zero CPU cycles, yet increase load average.
  3. **Diagnostic:**
     - Check `iowait` percentage (`wa`) in `top`. High `wa` confirms disk bottleneck.
     - Run `vmstat 1` and inspect the `b` (blocked) column.
     - Run `ps aux | awk '$8 ~ /D/ {print $0}'` to identify the exact PIDs stuck in uninterruptible sleep.
     - Run `iostat -xz 1` to locate saturated block devices.

### Scenario 3: The Danger of Database Checks in Kubernetes Liveness Probes
- **Interview Question:** "Why is configuring a Kubernetes Liveness Probe to execute `SELECT 1` against the primary database considered a catastrophic operational anti-pattern?"
- **Deep Technical Answer:**
  1. **Failure Cascade Storm:** If the database experiences a temporary network blip or high load, the database query times out across all application pods simultaneously.
  2. **Cluster Collapse:** Kubernetes interprets the probe timeout as a fatal application crash and issues `SIGKILL` to **every pod across the cluster concurrently**.
  3. When all pods reboot simultaneously, they slam the recovering database with thousands of initial connection requests, driving it back into failure (**Self-Inflicted Thundering Herd**).
  4. **Correct Architecture:**
     - **Liveness Probe:** Check **only internal process health** (e.g., deadlock detection, thread liveness). Never check external dependencies!
     - **Readiness Probe:** Checks database and external dependencies. If DB fails, readiness drops traffic routing without restarting pods.
---

## 8. Deep-Dive Kubernetes StatefulSets & Kernel Socket Tuning

### 8.1 Kubernetes StatefulSet Architecture
StatefulSets manage stateful clustered applications (PostgreSQL, Kafka, Cassandra):
```
Headless Service: `clusterIP: None`
      |
      +---> pod-0.kafka-headless.default.svc.cluster.local (IP: 10.244.1.5)
      |
      +---> pod-1.kafka-headless.default.svc.cluster.local (IP: 10.244.2.8)
      |
      +---> pod-2.kafka-headless.default.svc.cluster.local (IP: 10.244.3.12)
```
- **Ordered Lifecycle Invariant:** Pods deploy sequentially from index $0$ to $N-1$. `pod-1` starts only after `pod-0` enters Running and Ready. Termination executes in reverse order ($N-1$ down to $0$).
- **VolumeClaimTemplates:** Each replica dynamically provisions its own independent PersistentVolumeClaim (`data-pod-0`, `data-pod-1`). Deleting or scaling down a StatefulSet **never deletes PersistentVolumeClaims**, preventing accidental data destruction.

### 8.2 Production Linux Kernel Network Tuning (`/etc/sysctl.conf`)
High-throughput backend servers handling 100,000+ concurrent connections require tuning Linux kernel network buffers:
```ini
# Maximum socket listen backlog queue size (prevents SYN drops under bursts)
net.core.somaxconn = 65535

# Maximum number of unacknowledged packets queued on input interface
net.core.netdev_max_backlog = 100000

# TCP Read and Write buffer limits (min, default, max in bytes)
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216

# Enable TCP BBR Congestion Control (replaces loss-based CUBIC)
net.core.default_qdisc = fq
net.ipv4.tcp_congestion_control = bbr

# Enable fast recycling of TIME_WAIT sockets for outgoing connections
net.ipv4.tcp_tw_reuse = 1
```
