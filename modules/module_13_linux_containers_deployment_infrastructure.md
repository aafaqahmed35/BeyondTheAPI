# Module 13: Linux, Containers, Deployment & Infrastructure — Production Systems Engineering, Container Orchestration, and Operational Reliability

> **Authoritative Curriculum Standard:** FAANG Backend Engineering Handbook
> **Target Audience:** B.Tech / BE Freshers, Junior Backend Engineers (SDE-1 / SDE-2), Systems Engineering & Production Reliability Candidates
> **Target Specifications & Context:** Linux Systems Programming, POSIX Process & Signal Management, Bash Automation, Namespaces & cgroups v2, Docker Multi-Stage Builds, Kubernetes Orchestration (Probes, QoS, Autoscaling), Zero-Downtime Deployment Strategies, Cloud Infrastructure, and Production Incident Triage
> **Prerequisites:** [Module 02 — OS & Computer Networks](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_02_os_networks.md), [Module 04 — Database Engineering & SQL](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_04_database_engineering.md), [Module 05 — Backend Frameworks](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_05_backend_frameworks.md), [Module 06 — API Engineering & Security](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_06_api_engineering_security.md), [Module 07 — Caching & Performance](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_07_caching_performance.md), [Module 08 — Asynchronous Processing & Messaging](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_08_async_processing_messaging.md), [Module 09 — Concurrency & Distributed Systems](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_09_concurrency_distributed_systems.md), [Module 10 — System Design & Production Architecture](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_10_system_design_production_architecture.md), [Module 11 — Observability & Diagnostics](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_11_observability.md), [Module 12 — Debugging & Testing](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_12_debugging_testing.md)

---

## Table of Contents

### PART I — LINUX & SHELL ENGINEERING
1. [Section 1 — Linux's Role in Backend Engineering](#section-1--linuxs-role-in-backend-engineering)
2. [Section 2 — Filesystem Hierarchy and Navigation](#section-2--filesystem-hierarchy-and-navigation)
3. [Section 3 — Essential Command-Line Engineering](#section-3--essential-command-line-engineering)
4. [Section 4 — Bash and Shell Scripting](#section-4--bash-and-shell-scripting)
5. [Section 5 — Users, Groups, Permissions, and Security](#section-5--users-groups-permissions-and-security)
6. [Section 6 — Processes, Threads, Signals, and Job Control](#section-6--processes-threads-signals-and-job-control)
7. [Section 7 — CPU and Scheduling Diagnosis](#section-7--cpu-and-scheduling-diagnosis)
8. [Section 8 — Memory, Swap, and OOM Diagnosis](#section-8--memory-swap-and-oom-diagnosis)
9. [Section 9 — Disk, Filesystems, and Storage](#section-9--disk-filesystems-and-storage)
10. [Section 10 — Linux Networking Fundamentals](#section-10--linux-networking-fundamentals)
11. [Section 11 — Logs, systemd, and Service Management](#section-11--logs-systemd-and-service-management)
12. [Section 12 — Advanced Linux Troubleshooting Tools](#section-12--advanced-linux-troubleshooting-tools)
13. [Section 13 — SSH and Remote Administration](#section-13--ssh-and-remote-administration)
14. [Section 14 — Linux Production Incident Lab](#section-14--linux-production-incident-lab)

### PART II — DOCKER & CONTAINERIZATION
15. [Section 15 — Virtual Machines Versus Containers](#section-15--virtual-machines-versus-containers)
16. [Section 16 — Docker Architecture and Core Concepts](#section-16--docker-architecture-and-core-concepts)
17. [Section 17 — Writing Production-Appropriate Dockerfiles](#section-17--writing-production-appropriate-dockerfiles)
18. [Section 18 — Container Filesystems, Volumes, and Persistence](#section-18--container-filesystems-volumes-and-persistence)
19. [Section 19 — Docker Networking](#section-19--docker-networking)
20. [Section 20 — Docker Compose and Multi-Service Applications](#section-20--docker-compose-and-multi-service-applications)
21. [Section 21 — Container Resources and Lifecycle](#section-21--container-resources-and-lifecycle)
22. [Section 22 — Container Security and Operations](#section-22--container-security-and-operations)

### PART III — KUBERNETES
23. [Section 23 — Kubernetes Architecture](#section-23--kubernetes-architecture)
24. [Section 24 — Workloads and Deployment Objects](#section-24--workloads-and-deployment-objects)
25. [Section 25 — Services and Networking](#section-25--services-and-networking)
26. [Section 26 — Configuration and Secrets](#section-26--configuration-and-secrets)
27. [Section 27 — Requests, Limits, and Scheduling](#section-27--requests-limits-and-scheduling)
28. [Section 28 — Probes, Rollouts, and Availability](#section-28--probes-rollouts-and-availability)
29. [Section 29 — Kubernetes Troubleshooting Lab](#section-29--kubernetes-troubleshooting-lab)

### PART IV — DEPLOYMENT & INFRASTRUCTURE
30. [Section 30 — Environments and Configuration](#section-30--environments-and-configuration)
31. [Section 31 — Reverse Proxies, TLS, and Traffic Routing](#section-31--reverse-proxies-tls-and-traffic-routing)
32. [Section 32 — CI/CD and Automated Delivery](#section-32--cicd-and-automated-delivery)
33. [Section 33 — Deployment Strategies and Database Changes](#section-33--deployment-strategies-and-database-changes)
34. [Section 34 — Cloud and Infrastructure Fundamentals](#section-34--cloud-and-infrastructure-fundamentals)
35. [Section 35 — Reliability, Recovery, and Production Operations](#section-35--reliability-recovery-and-production-operations)

### PART V — INTEGRATED HANDS-ON LABS
36. [Section 36 — Lab 1: Diagnose a Linux Service](#section-36--lab-1-diagnose-a-linux-service)
37. [Section 37 — Lab 2: Containerize a Backend Application](#section-37--lab-2-containerize-a-backend-application)
38. [Section 38 — Lab 3: Run a Multi-Service Stack](#section-38--lab-3-run-a-multi-service-stack)
39. [Section 39 — Lab 4: Kubernetes Deployment Walkthrough](#section-39--lab-4-kubernetes-deployment-walkthrough)
40. [Section 40 — Lab 5: Production Deployment Incident](#section-40--lab-5-production-deployment-incident)
41. [Section 41 — Interview Questions and Scenario-Based Exercises](#section-41--interview-questions-and-scenario-based-exercises)
42. [Section 42 — Module 13 Cheatsheet: Linux, Containers & Infrastructure](#section-42--module-13-cheatsheet-linux-containers--infrastructure)

---

## PART I — LINUX & SHELL ENGINEERING

---

## Section 1 — Linux's Role in Backend Engineering

### First Principles: Kernel, User Space, and System Calls
The Linux operating system is the universal substrate of modern backend engineering. Every cloud instance, container, database node, and load balancer runs on Linux. To reason about production failures, an engineer must understand the boundary separating **User Space** from **Kernel Space**:

```
+-----------------------------------------------------------------+
|                          USER SPACE                             |
|  Java JVM (Spring Boot)  |  Node.js (V8)  |  PostgreSQL Server  |
|  Standard C Library (glibc / musl) | System Utilities (ps, curl)|
+-----------------------------------------------------------------+
                                |
           System Call Interface (syscalls: read, write,
                       clone, epoll_wait, mmap)
                                v
+-----------------------------------------------------------------+
|                         KERNEL SPACE                            |
|  Process Scheduler (CFS)  |  Virtual Memory & Page Cache        |
|  VFS & Filesystem Drivers |  TCP/IP Network Stack               |
|  cgroups v2 & Namespaces  |  Device Drivers & Hardware I/O      |
+-----------------------------------------------------------------+
```

1. **User Space:** Applications execute in ring 3 CPU protection mode. Application code cannot directly access physical hardware, network interfaces, or raw disk sectors.
2. **System Calls (Syscalls):** When a backend service reads a socket (`read()`), allocates memory (`mmap()`, `brk()`), or spawns a thread (`clone()`), it triggers a software interrupt, transitioning the CPU to ring 0 kernel mode.
3. **Hardware & Substrate Portability:**
   - *Local Machine:* Engineers frequently develop on macOS (Darwin kernel with BSD userland) on Apple Silicon (ARM64).
   - *Production Environment:* Services run on Linux (e.g., Ubuntu, Debian, RHEL, Alpine) on x86_64 or ARM64. **Never assume that behavior on macOS matches Linux production.** Linux syscalls (`epoll`, `cgroups`, `/proc`) do not exist natively on macOS.

---

## Section 2 — Filesystem Hierarchy and Navigation

### The Linux Filesystem Hierarchy Standard (FHS)
Unlike Windows drive letters (`C:\`, `D:\`), Linux unifies all physical disks, virtual filesystems, and device drivers into a single root tree (`/`):

| Directory | Purpose | Production Engineering Relevance |
| :--- | :--- | :--- |
| `/` | Root of the entire filesystem | Root partition saturation crashes the OS. |
| `/etc` | System-wide static configuration | Nginx conf, systemd services, hostnames, resolv.conf. |
| `/var` | Variable data (logs, spool, caches) | Target of log rotation; primary suspect during disk-full outages (`/var/log`). |
| `/tmp` | Ephemeral temporary scratch files | Often mounted as `tmpfs` (RAM). Cleaned on reboot. |
| `/proc` | Virtual pseudo-filesystem exposing kernel state | Direct inspection of processes (`/proc/<pid>/`) and memory (`/proc/meminfo`). |
| `/sys` | Virtual pseudo-filesystem exposing device/cgroup state | Inspection of cgroups v2 resource limits (`/sys/fs/cgroup/`). |
| `/dev` | Device nodes (block, character, pseudo) | `/dev/null` (data sink), `/dev/urandom` (entropy), `/dev/shm` (shared memory). |
| `/usr` | User binaries, read-only system libraries | Standard location for application runtimes and shared objects (`/usr/bin`). |
| `/opt` | Optional third-party enterprise software packages | Frequently holds custom vendor agents (Datadog, Dynatrace). |

### File Descriptors, Links, and Mounts
- **File Descriptors (FDs):** In Linux, *"everything is a file"*. Sockets, pipes, directories, and disk files are accessed via non-negative integer handles. An application leaking open sockets will exhaust process FD limits (`ulimit -n`).
- **Hard Links vs. Symbolic Links:**
  - *Hard Link:* A directory entry pointing directly to an existing inode. Cannot span filesystems; deleting the original file preserves data until all hard links are unlinked.
  - *Symbolic Link (Symlink):* A special file containing a path string pointing to another target. Breaks if target is moved or deleted.

---

## Section 3 — Essential Command-Line Engineering

### The Unix Philosophy: Composition via Pipes
The Unix philosophy dictates: *Write programs that do one thing well. Write programs to work together. Write programs to handle text streams.*

```
             stdin              stdout             stdout
[Source File] ----> [grep filter] ----> [sort keys] ----> [uniq -c] ----> [Final Report]
                           |                  |
                        stderr             stderr
                           |                  |
                           v                  v
                   [/dev/null / log]  [/dev/null / log]
```

### High-Velocity Command Matrix
- `cat`, `less`, `head`, `tail`: Inspecting streams. `tail -f -n 100 /var/log/nginx/access.log` monitors live ingress.
- `grep`, `sed`, `awk`:
  - `grep -E 'HTTP/1.1" 50[0-9]' access.log`: Filter for all 5xx server errors.
  - `awk '{print $1}' access.log | sort | uniq -c | sort -nr | head -n 10`: Top 10 client IP addresses by request volume.
  - `sed -i 's/localhost:5432/postgres-cluster:5432/g' config.env`: In-place stream replacement.
- `xargs`: Translates stdin into command arguments: `find /tmp -name "*.tmp" -mtime +7 | xargs rm -f`.
- **Exit Codes and Operators:**
  - `0`: Success. Non-zero (`1–255`): Failure.
  - `cmdA && cmdB`: Run `cmdB` only if `cmdA` succeeds ($0$).
  - `cmdA || cmdB`: Run `cmdB` only if `cmdA` fails ($\neq 0$).

---

## Section 4 — Bash and Shell Scripting

### Writing Robust, Production-Grade Shell Scripts
Naive shell scripts fail silently, proceed after errors, and corrupt data. A production script must enforce defensive execution:

```bash
#!/usr/bin/env bash
# ==============================================================================
# Production Automation Template
# ==============================================================================
set -euo pipefail

# set -e: Exit immediately if any command returns a non-zero status.
# set -u: Treat unset variables as an error and exit immediately.
# set -o pipefail: Pipeline exit code is that of the last command to fail (not succeed).

readonly LOG_DIR="/var/log/app"
readonly BACKUP_ARCHIVE="/var/backups/app_$(date +%Y%m%d).tar.gz"

# POSIX Trap for deterministic cleanup
cleanup() {
    local exit_code=$?
    echo "[INFO] Cleaning up temporary working files..."
    rm -rf "${TMP_WORKSPACE:-}"
    exit "${exit_code}"
}
trap cleanup EXIT INT TERM

# Create secure temporary directory
TMP_WORKSPACE="$(mktemp -d -t app_ops.XXXXXX)"

main() {
    echo "[INFO] Commencing backup process in ${TMP_WORKSPACE}..."
    if [[ ! -d "${LOG_DIR}" ]]; then
        echo "[ERROR] Log directory ${LOG_DIR} does not exist!" >&2
        exit 1
    fi

    tar -czf "${BACKUP_ARCHIVE}" -C "${LOG_DIR}" .
    echo "[SUCCESS] Backup written to ${BACKUP_ARCHIVE}."
}

main "$@"
```

---

## Section 5 — Users, Groups, Permissions, and Security

### Permission Bits: Representation and Interpretation
Linux security relies on **Discretionary Access Control (DAC)** based on User, Group, and Others:

```
 -  r w x  r - x  r - -    (Numeric: 754)
 |  \___/  \___/  \___/
 |    |      |      |
 |    |      |      +-> Others: Read-only (4)
 |    |      +--------> Group: Read & Execute (5)
 |    +---------------> Owner: Read, Write & Execute (7)
 +--------------------> File Type (- = Regular file, d = Directory, l = Symlink)
```

### Directory Execute Permission Gotcha
For a regular file, `x` means executable as a binary or script. For a **directory**, `x` means **traversable**: permission to enter the directory (`cd`) and access files inside. A directory with `r--` permission allows listing filenames (`ls`), but reading file metadata (`stat`) or contents will fail with `Permission Denied`!

### The Danger of `chmod -R 777`
Running `chmod -R 777` on a directory grants world-writable permissions to every local user and compromised process. In production, SSH rejects private keys with permissive permissions (`chmod 600 id_rsa` is required). World-writable scripts allow unprivileged processes to inject code into root cronjobs.

---

## Section 6 — Processes, Threads, Signals, and Job Control

### Process Creation and States
1. **Process Spawning:** In Linux, all processes descend from PID 1 (`systemd`). Processes are created via `fork()` (or `clone()`) followed by `execve()`.
2. **Process States:**
   - `R` (Running / Runnable): Actively executing on CPU or queued in scheduler.
   - `S` (Interruptible Sleep): Waiting for an event (socket I/O, timer). Wakes on signals.
   - `D` (Uninterruptible Sleep): Waiting on synchronous disk or network NFS I/O. **Cannot be killed by SIGKILL!**
   - `Z` (Zombie): Terminated process whose exit code has not yet been read by its parent via `wait()`. Consumes a PID slot, but zero RAM or CPU.

### SIGTERM (15) vs. SIGKILL (9)
| Signal | Name | Catchable? | Purpose |
| :--- | :--- | :---: | :--- |
| **SIGTERM** | Terminate (15) | **Yes** | Polite request to shut down. Application drains in-flight requests, commits transactions, and closes DB connections. |
| **SIGKILL** | Kill (9) | **No** | Immediate kernel termination. Memory is freed, but file writes are truncated and in-flight transactions are aborted abruptly. |

---

## Section 7 — CPU and Scheduling Diagnosis

### Load Average vs. CPU Utilization
- **CPU Utilization:** Percentage of clock ticks cores spent executing non-idle code (`top`, `mpstat`).
- **Load Average:** The average number of tasks in **runnable state (`R`)** PLUS tasks in **uninterruptible sleep state (`D`)** over 1, 5, and 15 minutes (`uptime`).
- **Crucial Distinction:** A 4-core machine can have 100% CPU utilization with a load average of 4.0 (healthy saturation). The same 4-core machine can have a load average of **80.0** with **0% CPU utilization** if 80 threads are blocked in `D` state waiting on a hung NFS mount!

---

## Section 8 — Memory, Swap, and OOM Diagnosis

### Memory Hierarchy: Virtual Memory, RSS, and Page Cache
```
+-------------------------------------------------------------+
|               Virtual Memory Space (VSS)                    |
|  [Mapped Libs]  [Uncommitted Mappings]  [Committed Heap]    |
+-------------------------------------------------------------+
                               |
                        Page Table Translation
                               v
+-------------------------------------------------------------+
|                Physical RAM Allocation                      |
|  Resident Set Size (RSS)  |  Page Cache (Disk buffer) | Free|
+-------------------------------------------------------------+
```

1. **Virtual Set Size (VSS):** Total virtual address space mapped by process. Irrelevant for memory limits.
2. **Resident Set Size (RSS):** Physical RAM currently occupied by process pages.
3. **Page Cache:** Free RAM utilized by Linux to cache disk files. Automatically reclaimed by the kernel when applications request memory. High page cache is normal and desirable!
4. **The Linux OOM Killer:** When physical RAM and swap are exhausted, the kernel invokes `oom_killer`. It calculates an `oom_score` (based on RAM percentage and `/proc/<pid>/oom_score_adj`) and forcibly sends `SIGKILL` to the offending process.

---

## Section 9 — Disk, Filesystems, and Storage

### Disk Capacity vs. Inode Exhaustion
A disk can report `No space left on device` even when `df -h` shows 50% free capacity!
- **Space Exhaustion (`df -h`):** All data blocks are occupied by file contents.
- **Inode Exhaustion (`df -i`):** The fixed table of file metadata records (inodes) is 100% full. This occurs when applications create millions of tiny files (e.g., uncleaned session cache files).

### The Deleted-but-Open File Phenomenon
When a runaway log file fills `/var/log`, running `rm app.log` deletes the directory entry, but if the backend process still holds the file descriptor open, **the disk blocks are NOT freed!**
- *Diagnostic:* `lsof | grep deleted` identifies open file handles to unlinked files.
- *Fix:* Truncate the file directly without unlinking: `> /proc/<pid>/fd/<fd_num>`.

---

## Section 10 — Linux Networking Fundamentals

### TCP States and Sockets
- `LISTEN`: Socket waiting for inbound connection requests.
- `ESTABLISHED`: Active bidirectional TCP connection.
- `TIME_WAIT`: Socket closed by local endpoint, waiting $2 \times \text{MSL}$ (Maximum Segment Lifetime, typically 60s) to absorb delayed duplicate packets. High `TIME_WAIT` exhausts ephemeral ports.
- `CLOSE_WAIT`: Remote side closed connection; local application has not yet called `close()`. Accumulation indicates an application socket leak!

### Localhost (`127.0.0.1`) vs. All Interfaces (`0.0.0.0`)
- Binding to `127.0.0.1`: Service accepts packets *only* from the local loopback interface. In Docker containers, binding to `127.0.0.1` means **external requests from the Docker host or bridge network will be rejected!**
- Binding to `0.0.0.0`: Service listens on all network interfaces. Mandatory for containerized backends.

---

## Section 11 — Logs, systemd, and Service Management

### Modern Service Management with systemd
Systemd manages service dependencies, environment isolation, and crash restarts:

```ini
[Unit]
Description=Backend Microservice API
After=network.target postgresql.service

[Service]
Type=simple
User=appuser
Group=appgroup
WorkingDirectory=/opt/app
EnvironmentFile=/etc/default/backend-api
ExecStart=/usr/bin/java -XX:+UseContainerSupport -jar /opt/app/service.jar
Restart=on-failure
RestartSec=5s
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

- Inspect logs: `journalctl -u backend-api.service -f --since "1 hour ago"`.

---

## Section 12 — Advanced Linux Troubleshooting Tools

- `lsof -p <pid>`: Lists all open file descriptors, network connections, and pipes for a process.
- `strace -p <pid> -e trace=network,file -tt -T`: Traces system calls with microsecond timestamps and call durations. Identifies if a process is hanging on a blocked `connect()` or `read()`.
- `ulimit -a` / `prlimit --pid <pid>`: Displays system resource limits (open files, max user processes, stack size).

---

## Section 13 — SSH and Remote Administration

### Key-Based Authentication and File Permissions
SSH enforces strict DAC permissions on the client and server:
- `~/.ssh/`: Must be `chmod 700`.
- `~/.ssh/authorized_keys`: Must be `chmod 600`.
- `~/.ssh/id_rsa` (Private Key): Must be `chmod 600`. SSH clients reject connection attempts if private keys are accessible by other users.

---

## Section 14 — Linux Production Incident Lab

### 10 Critical Production Scenarios

1. **High CPU:** `top` $\to$ identify runaway PID $\to$ `top -H -p <pid>` identifies thread ID $\to$ convert thread ID to hex $\to$ match `nid` in Java thread dump.
2. **OOM Termination:** `dmesg -T | grep -i oom` $\to$ confirm kernel OOM killer invoked $\to$ adjust container memory limits or tune JVM heap headroom.
3. **Inode Exhaustion:** `df -i` reveals 100% inodes $\to$ `find /var/spool -type d -size +100k` locates directory with millions of orphan session files.
4. **Port Already in Use:** `lsof -iTCP:<port> -sTCP:LISTEN` identifies conflicting process.
5. **DNS Failure:** `curl` fails with `Could not resolve host` $\to$ check `/etc/resolv.conf` and test with `dig @1.1.1.1 domain.com`.
6. **Permission Denied on Directory:** Parent directory missing `+x` execute permission preventing traversal.
7. **Excessive Open FDs:** `lsof -p <pid> | wc -l` exceeds `ulimit -n` $\to$ unclosed HTTP client connection leak.
8. **Service Restart Loop:** `journalctl -u service -n 50 --no-pager` reveals missing mandatory environment variable.
9. **Uninterruptible Sleep (`D` State):** Process stuck in kernel disk I/O; `cat /proc/<pid>/stack` shows wait on NFS RPC.
10. **Deleted-but-Open Disk Leak:** `df -h` shows 100% full, but `du -sh /*` shows 20GB used $\to$ `lsof | grep deleted` reveals unlinked 80GB log held open by crashed worker.

---

## PART II — DOCKER & CONTAINERIZATION

---

## Section 15 — Virtual Machines Versus Containers

### Architectural Foundations
Containers are **not** lightweight Virtual Machines. They are isolated Linux processes running directly on the host kernel:

```
+---------------------------------------+  +---------------------------------------+
|          VIRTUAL MACHINES             |  |              CONTAINERS               |
| +-----------------------------------+ |  | +-----------------+ +---------------+ |
| | App 1 | App 2                     | |  | | App 1           | | App 2         | |
| | Guest OS Kernel (Linux / Windows) | |  | | Bins & Libs     | | Bins & Libs   | |
| +-----------------------------------+ |  | +-----------------+ +---------------+ |
| | Hypervisor (KVM / VMware / Xen)   | |  | | Linux Namespaces & cgroups v2       | |
| +-----------------------------------+ |  | +-----------------------------------+ |
| | Host OS & Hardware                | |  | | Shared Host Linux Kernel & Hardware | |
| +-----------------------------------+ |  | +-----------------------------------+ |
+---------------------------------------+  +---------------------------------------+
```

### The 2 Primitives of Container Isolation
1. **Linux Namespaces (What a process can SEE):**
   - `pid`: Process tree isolation (Container process thinks it is PID 1).
   - `net`: Network device and routing table isolation.
   - `mnt`: Filesystem mount point isolation.
   - `ipc`: Inter-process communication isolation.
   - `uts`: Hostname isolation.
   - `user`: User and group ID mapping isolation.
2. **Control Groups / cgroups v2 (What a process can USE):**
   - Resource meters and limiters: memory limits (`memory.max`), CPU quota (`cpu.max`), I/O bandwidth (`io.weight`).

---

## Section 16 — Docker Architecture and Core Concepts

### Docker Components & Layered Storage
Docker uses an immutable layer model backed by an **OverlayFS** union filesystem:
- **Image Layers:** Read-only snapshots generated by each Dockerfile command (`RUN`, `COPY`).
- **Container Layer:** Ephemeral read-write layer added on top of image layers when a container runs. Deleting the container permanently deletes the read-write layer!

---

## Section 17 — Writing Production-Appropriate Dockerfiles

### Best Practices Checklist
1. **Multi-Stage Builds:** Compile in a heavy build stage; copy artifacts to a minimal JRE/Alpine runtime stage.
2. **Order Matters:** Place infrequently changed commands (`COPY pom.xml`, `RUN ./mvnw dependency:go-offline`) before frequently changed source code (`COPY src src/`).
3. **Run as Non-Root:** Create an explicit unprivileged user (`USER 10001:10001`). Never run production backends as `root`!
4. **Exec-Form ENTRYPOINT:** Always use JSON array format: `ENTRYPOINT ["java", "-jar", "app.jar"]`. The shell format (`ENTRYPOINT java -jar app.jar`) spawns `/bin/sh -c` as PID 1, which swallows `SIGTERM` signals and prevents graceful shutdown!

---

## Section 18 — Container Filesystems, Volumes, and Persistence

- **Bind Mounts:** Maps a specific host directory into the container (`-v /var/data:/data`). High performance, but creates host filesystem coupling.
- **Named Volumes:** Managed by Docker daemon (`-v db_data:/var/lib/postgresql/data`). Decoupled from host paths, portable, and safe for production databases.

---

## Section 19 — Docker Networking

- **Bridge Network:** Default internal virtual bridge. Containers on the same user-defined bridge resolve each other via **automatic DNS** using container names!
- **Host Network (`--net=host`):** Bypasses network namespace isolation. Container shares host IP and port space directly (lower latency, but port collisions possible).

---

## Section 20 — Docker Compose and Multi-Service Applications

### Startup Order vs. Application Readiness
Specifying `depends_on: [postgres]` in Docker Compose ensures the PostgreSQL container starts before the backend, **but it does NOT mean the database is ready to accept connections!**
- **Production Solution:** Use health check conditions:
  ```yaml
  depends_on:
    postgres:
      condition: service_healthy
  ```

---

## Section 21 — Container Resources and Lifecycle

### Container Exit Codes
- `0`: Clean exit.
- `137` ($128 + 9$): Terminated by `SIGKILL`. Almost always indicates **OOMKilled** by cgroups memory limit!
- `143` ($128 + 15$): Terminated by `SIGTERM` (normal shutdown).

---

## Section 22 — Container Security and Operations

- Drop all Linux capabilities: `--cap-drop=ALL`.
- Read-only root filesystem: `--read-only`, mounting writable `/tmp` via `tmpfs`.
- Regular image scanning: Trivy / Grype scanning in CI for CVE vulnerabilities.

---

## PART III — KUBERNETES

---

## Section 23 — Kubernetes Architecture

### Control Plane vs. Worker Nodes
```
+-------------------------------------------------------+
|                    CONTROL PLANE                      |
|  kube-apiserver   <--->  etcd (Distributed Key-Value) |
|  kube-scheduler   <--->  kube-controller-manager      |
+-------------------------------------------------------+
                           |
        Reconciliation Loop: Desired State vs Actual
                           |
                           v
+-------------------------------------------------------+
|                    WORKER NODES                       |
|  kubelet (Node Agent)     |  kube-proxy (iptables/IPVS)|
|  containerd (CRI Runtime) |  Pod 1, Pod 2, Pod 3       |
+-------------------------------------------------------+
```

---

## Section 24 — Workloads and Deployment Objects

- **Pod:** Smallest deployable unit in Kubernetes (one or more containers sharing network namespace, loopback, and volumes).
- **Deployment:** Manages declarative rolling updates and replica scaling for stateless workloads.
- **StatefulSet:** Manages stateful workloads requiring stable, unique network identifiers (e.g., `kafka-0`, `kafka-1`) and dedicated persistent volumes.

---

## Section 25 — Services and Networking

### Service Types
1. **ClusterIP (Default):** Exposes service on an internal IP reachable only inside the cluster.
2. **NodePort:** Exposes service on a static port (`30000–32767`) on each node's IP.
3. **LoadBalancer:** Provisions a cloud load balancer (AWS NLB/ALB, GCP LB) that routes to NodePorts.
4. **Ingress:** L7 reverse proxy (Nginx, Traefik) routing HTTP paths and domains to ClusterIP services.

---

## Section 26 — Configuration and Secrets

- **ConfigMaps:** Plaintext configuration keys or configuration files.
- **Secrets:** Base64 encoded data. **Warning:** Kubernetes Secrets are NOT encrypted by default; they must be paired with etcd KMS encryption at rest and Vault / AWS Secrets Manager integration.

---

## Section 27 — Requests, Limits, and Scheduling

### Requests vs. Limits
- **Requests:** Guaranteed minimum resources used by `kube-scheduler` to place Pods on nodes. If a node lacks unreserved CPU/memory requests, the Pod remains `Pending`.
- **Limits:** Hard upper ceiling enforced by Linux cgroups.
  - Exceeding **CPU Limit:** CPU throttled via CFS quota. Application slows down, but does not crash.
  - Exceeding **Memory Limit:** Container immediately terminated with **OOMKilled** (`exit code 137`).

---

## Section 28 — Probes, Rollouts, and Availability

### The 3 Kubernetes Probes
1. **Startup Probe:** Mutes liveness and readiness checks during slow initialization (e.g., initial JVM classloading).
2. **Liveness Probe:** Detects unrecoverable deadlocks. If it fails, the kubelet restarts the container. **Rule:** NEVER check downstream databases in liveness probes! An outage in PostgreSQL will cause every backend pod to restart simultaneously, creating a cluster-wide thundering herd!
3. **Readiness Probe:** Detects whether Pod can accept traffic. If it fails, Kubernetes pulls the Pod's IP from the Service endpoint slice.

---

## Section 29 — Kubernetes Troubleshooting Lab

### Triage Decision Tree
- `ImagePullBackOff`: Wrong image tag, missing registry credentials (`imagePullSecrets`), or network block.
- `CrashLoopBackOff`: Container starts and exits immediately. Run `kubectl logs <pod> --previous`.
- `Pending`: No node has sufficient CPU/memory requests, or node affinity constraints unmet.
- `OOMKilled`: Memory limit exceeded. Increase `resources.limits.memory` or tune `-XX:MaxRAMPercentage`.

---

## PART IV — DEPLOYMENT & INFRASTRUCTURE

---

## Section 30 — Environments and Configuration

- **The Twelve-Factor App:** Strict separation of config from code. Configuration injected via environment variables.
- **Configuration Drift:** Divergence between staging and production environments. Mitigated by Infrastructure as Code (Terraform) and GitOps (ArgoCD).

---

## Section 31 — Reverse Proxies, TLS, and Traffic Routing

- **TLS Termination:** Decrypting HTTPS at the ingress/load balancer and forwarding plaintext HTTP internally within a secure private VPC.
- **Header Forwarding:** Forwarding `X-Forwarded-For` and `X-Forwarded-Proto` so backend rate limiters and audit logs capture true client IPs.

---

## Section 32 — CI/CD and Automated Delivery

```
[Git Commit] -> [Lint & SAST] -> [Unit Tests] -> [Docker Build] -> [Trivy CVE Scan] -> [Push Image Digest] -> [Deploy Staging] -> [Canary Prod]
```

Immutable image tags using git commit SHA or sha256 digests (`image@sha256:...`) prevent accidental image overwrites.

---

## Section 33 — Deployment Strategies and Database Changes

### Blue-Green vs. Canary Rollouts
- **Blue-Green:** Run two identical production clusters. Switch router traffic 100% instantly. Requires 200% capacity ($2\times$ cost).
- **Canary:** Route 5–10% of user traffic to the new version. Monitor error rates in Grafana for 30 minutes before advancing to 100%.

### Expand-and-Contract Database Migrations
Never execute breaking schema changes (e.g., renaming a column) in a single deployment:
1. **Expand:** Add new nullable column `email_address`. Deploy backend code that dual-writes to both `email` and `email_address`.
2. **Backfill:** Migrate legacy records asynchronously.
3. **Contract:** Deploy backend code reading only `email_address`. Drop the old column.

---

## Section 34 — Cloud and Infrastructure Fundamentals

- **Availability Zones (AZs):** Physically isolated datacenters within a metropolitan region with independent power, cooling, and networking.
- **Shared Responsibility Model:** Cloud provider manages physical hardware, hypervisor, and facilities; customer manages OS patches, IAM, firewall rules, and data encryption.

---

## Section 35 — Reliability, Recovery, and Production Operations

- **RPO (Recovery Point Objective):** Maximum acceptable data loss duration (e.g., 15 minutes of WAL logs).
- **RTO (Recovery Time Objective):** Maximum acceptable downtime duration to restore service (e.g., 1 hour).
- **Chaos Engineering:** Intentionally injecting node terminations and network latency (Chaos Mesh, Gremlin) to prove automated recovery.

---

## PART V — INTEGRATED HANDS-ON LABS

---

## Section 36 — Lab 1: Diagnose a Linux Service

**Problem:** A backend service is unresponsive; requests hang indefinitely.
1. `uptime`: Load average is 18.0 on a 2-core VM.
2. `top`: CPU is 98% idle, but `vmstat 1 5` shows 16 tasks in `b` (blocked) state and high `wa` (iowait).
3. `lsof -p <pid> | grep -E "REG|DIR"` reveals process is attempting to write to an NFS mount that has hung.
4. *Mitigation:* Unmount hung NFS filesystem (`umount -f -l`) and restart service with local disk buffer.

---

## Section 37 — Lab 2: Containerize a Backend Application

Review our production multi-stage Dockerfile in `assets/code_examples/docker/Dockerfile.java`:
- Multi-stage build isolating build tools.
- Non-root user `10001:10001`.
- Built-in container-aware JVM flags (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`).
- Healthcheck testing readiness endpoint.

---

## Section 38 — Lab 3: Run a Multi-Service Stack

Review our Docker Compose stack in `assets/code_examples/docker/docker-compose.yml`:
- Coordinates Backend, PostgreSQL 16, and Redis 7.
- Enforces `condition: service_healthy` so backend does not crash on uninitialized databases.
- Isolated bridge network and persistent data volumes.

---

## Section 39 — Lab 4: Kubernetes Deployment Walkthrough

Review our Kubernetes manifests in `assets/code_examples/k8s/`:
- `deployment.yaml`: Rolling update with 0 unavailable, startup/liveness/readiness probes, security context (non-root, read-only root FS).
- `service.yaml`: ClusterIP service mapping port 80 to 8080.

---

## Section 40 — Lab 5: Production Deployment Incident

**Symptoms:** Deployment completed, all pods running, but 100% of external traffic returns HTTP 503.
1. `kubectl get pods -n production`: 3/3 pods show `Running`.
2. `kubectl describe service backend-service`: `Endpoints: <none>`!
3. **Root Cause:** The Deployment template labels (`app.kubernetes.io/name: backend-service`) did not match the Service selector (`app: backend-service`).
4. *Fix:* Align selector labels; Kubernetes immediately populates endpoint slice and traffic restores.

---

## Section 41 — Interview Questions and Scenario-Based Exercises

### Q1: "A container is killed with exit code 137. How do you diagnose whether it was OOMKilled or manually killed?"
**Answer:**
Exit code 137 indicates termination by `SIGKILL` ($128 + 9$). Inspect `kubectl describe pod <pod>` or `docker inspect <id>`. If `OOMKilled: true` appears, Linux cgroups triggered memory termination. On the Linux host, verify via `dmesg -T | grep -i oom`. If no OOM message exists, a human operator or orchestrator termination script sent `kill -9`.

### Q2: "Why should a Kubernetes Liveness probe never check an external database connection?"
**Answer:**
Liveness probes determine if the local container process is healthy or deadlocked. If an external database experiences a temporary network partition, all backend pods would fail their liveness checks simultaneously. The kubelet would restart every container at once, causing cascading restart loops, thundering herds, and prolonged cluster downtime. External dependencies must only be checked by **Readiness probes**.

---

## Section 42 — Module 13 Cheatsheet: Linux, Containers & Infrastructure

### Diagnostic Linux Commands
```bash
# Process and Thread Diagnosis
ps aux --sort=-%cpu | head -n 10            # Top 10 CPU-consuming processes
ps aux --sort=-%mem | head -n 10            # Top 10 Memory-consuming processes
ps -o pid,ppid,user,%cpu,%mem,stat,comm -p <PID> # Detailed process attributes
top -H -p <PID>                             # Inspect individual threads of a process

# Memory and Storage
free -m                                     # RAM and Swap breakdown in MB
df -h                                       # Filesystem disk capacity
df -i                                       # Filesystem inode utilization
lsof | grep deleted                         # Find deleted files still held open

# Network and Sockets
ss -tulpn                                   # All listening TCP/UDP sockets with PIDs
lsof -iTCP:<PORT> -sTCP:LISTEN              # Find process listening on specific port
tcpdump -i any -nn -s0 -A 'tcp port 8080'   # Live packet capture on port 8080
```

### Docker & Kubernetes Quick Reference
```bash
# Docker Operations
docker ps --filter "status=exited"          # List crashed containers
docker stats                                # Live CPU/Memory stream per container
docker logs --tail 100 -f <CONTAINER_ID>    # Follow tail of container logs

# Kubernetes Diagnostics
kubectl get pods -o wide --show-labels      # Pods with IP, Node, and Labels
kubectl describe pod <POD_NAME>             # Events, probes, and restart reasons
kubectl logs <POD_NAME> --previous          # Logs from crashed previous container
kubectl get events --sort-by='.metadata.creationTimestamp' # Cluster-wide events
```

### Golden Rules of Infrastructure Reliability
1. **Never run containers as root.** Always specify an unprivileged UID/GID.
2. **Never check external dependencies in liveness probes.** Use readiness probes for dependencies.
3. **Never deploy breaking database schema changes in a single step.** Use expand-and-contract.
4. **Treat infrastructure as cattle, not pets.** Everything must be codifiable in GitOps.
5. **Always test backups.** An unverified backup is not a backup.
