# Curriculum Master Specification

## 1. Executive Summary and Architecture

The *FAANG Engineering Handbook* curriculum is designed to guide a graduating Computer Science engineer (B.Tech fresher) from foundational computer science theory to production-grade distributed backend engineering and confident resume defense in FAANG and tier-1 tech interviews.

The curriculum is structured into **14 sequential modules**, where each module builds directly upon the mental models, invariants, and implementation patterns established in preceding modules.

---

## 2. Curriculum Governance Principles

The development, authoring, and review of this curriculum are strictly governed by ten non-negotiable principles:

1. **Mandatory Resume Coverage:** Every significant technology, library, pattern, or project claimed on the candidate's resume must receive rigorous conceptual and implementation coverage.
2. **Defensible Educational Purpose:** Every topic included must have a clear educational objective or direct relevance to tier-1 technical interview evaluations.
3. **Strict Prerequisite Sequencing:** Foundational concepts (algorithms, OS/networks, language runtimes) must precede complex applied topics (distributed caching, microservices, async message buses) that depend on them.
4. **Deep-Dive Once, Reinforce in Context:** Core concepts are taught once in exhaustive depth and subsequently reinforced in realistic production scenarios.
5. **No Silent Omissions:** No approved or required topic within a module's scope may be skipped or replaced with an external link.
6. **Depth Over Page Count:** Page count and word volume are never metrics of success. Mathematical and mechanistic correctness, pedagogical clarity, and code defensibility are paramount.
7. **Qualified Technical Claims:** All runtime characteristics, asymptotic bounds, and system behaviors must be technically accurate, version-specified, and properly qualified.
8. **Clear Boundary Between Theory and Project Implementation:** The curriculum must explicitly distinguish general conceptual knowledge from verified implementations present in the candidate's portfolio.
9. **Mandatory User Approval Gates:** The user must explicitly approve each module's source and QA audit before the workflow may advance to the subsequent module.
10. **Serial Execution Integrity:** Modules are developed strictly one at a time. Batch generation across multiple modules in a single session is forbidden.

---

## 3. Approved Module Sequence and Technology Mapping

The table below specifies the 14 modules, their target domains, the technologies covered, and their verification status (classified as: *Verified from resume*, *Verified from project documentation*, *Supplied in working context but not yet verified against resume*, *Supporting prerequisite*, or *Optional extension*).

| Mod # | Module Title | Primary Subjects & Competencies | Technologies Covered | Verification Classification | Prerequisite Modules |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **01** | **Data Structures and Algorithms** | Asymptotic analysis, memory layout, arrays/strings, hashing, two pointers, sliding window, prefix sums, binary search, linked lists, stacks/queues, trees/BSTs, heaps, graph traversals, topological sort, Dijkstra, recursion/backtracking, greedy, dynamic programming, sorting trade-offs, pattern recognition. | Java 21 LTS | Supporting prerequisite (Java supplied in context) | *None (Foundational)* |
| **02** | **Operating Systems and Computer Networks** | Process vs Thread memory layout, context switching, scheduling, virtual memory, paging, page faults, CPU cache lines, socket programming, TCP 3-way handshake, flow control, TCP congestion control, TIME_WAIT, DNS resolution, HTTP 1.1 vs 2 vs 3, TLS 1.3 handshake, epoll / I/O multiplexing. | Linux kernel primitives, POSIX sockets, TCP/IP, Wireshark, HTTP/1.1/2/3, TLS 1.3 | Supporting prerequisite | Module 1 |
| **03** | **Java, OOP, and Language Fundamentals** | JVM memory architecture (Heap, Metaspace, Stack), Garbage Collection (G1GC, ZGC generational), JIT compilation, Java Memory Model (JMM), `volatile`, `synchronized`, Java Collections Framework internals (`HashMap`, `ConcurrentHashMap`, `ArrayList`), Generics type erasure, Streams API mechanics, Clean OOP vs Composition. | Java 21, JVM bytecode, JMM, JCF, Java Concurrency Utilities (`java.util.concurrent`) | Supplied in working context but not yet verified against resume | Module 1, Module 2 |
| **04** | **Database Engineering and SQL** | Relational data modeling, Normalization (1NF to BCNF), B-Tree vs B+Tree indexing mechanics, Clustered vs Secondary indexes, Query execution plans (`EXPLAIN ANALYZE`), ACID properties, Transaction Isolation Levels, MVCC (Multi-Version Concurrency Control), Deadlocks, Write-Ahead Logging (WAL), Complex SQL (Window functions, CTEs). | PostgreSQL 16, MySQL 8.0, Standard SQL | Supplied in working context but not yet verified against resume | Module 1, Module 2 |
| **05** | **Backend Frameworks and Application Architecture** | Inversion of Control (IoC), Dependency Injection (DI), Spring Boot auto-configuration, Bean lifecycle, Spring MVC request pipeline, Node.js event loop & libuv thread pool, Express middleware chaining, Clean Architecture, Layered architecture (Controller-Service-Repository), Domain-Driven Design (DDD) basics. | Spring Boot 3.x, Node.js 20+, Express.js, TypeScript | Supplied in working context but not yet verified against resume | Module 3, Module 4 |
| **06** | **API Engineering and Security** | RESTful resource design, HTTP semantics, Idempotency keys, Pagination (Offset vs Keyset cursor), Input validation & sanitization, Authentication vs Authorization, JWT architecture & signature verification, OAuth2 authorization code flow, RBAC, CORS mechanics, Rate limiting algorithms (Token Bucket, Leaky Bucket). | REST, JSON, JWT, OAuth2, Spring Security / Passport.js, Zod | Supplied in working context but not yet verified against resume | Module 5 |
| **07** | **Caching and Data Access Performance** | Caching topologies (In-memory, Local vs Distributed), Redis internal data structures (SkipList, SDS, Dict, ZipList), Cache patterns (Cache-Aside, Read-Through, Write-Through, Write-Behind), Cache Stampede, Cache Penetration, Cache Avalanche solutions, Database connection pooling internals (HikariCP). | Redis 7+, HikariCP, Spring Cache, Jedis / Lettuce | Supplied in working context but not yet verified against resume | Module 4, Module 5 |
| **08** | **Asynchronous Processing and Distributed Messaging** | Synchronous vs Asynchronous trade-offs, Message Broker topologies (Queue vs Pub/Sub), Point-to-Point vs Log-based architectures, RabbitMQ AMQP exchange routing, Apache Kafka partitioned commit log mechanics, Consumer Groups, Offsets, Backpressure, At-least-once vs Exactly-once semantics, Dead Letter Queues (DLQ). | RabbitMQ, Apache Kafka, BullMQ | Supplied in working context but not yet verified against resume | Module 2, Module 5 |
| **09** | **Concurrency and Distributed Systems** | Multithreading race conditions, Locks vs Lock-free atomic primitives, CAS (Compare-And-Swap), Thread pools and sizing formulas, Distributed systems fundamentals, CAP theorem, PACELC theorem, Fallacies of distributed computing, Distributed consensus intuition (Raft, Paxos), Distributed locking with Redis (Redlock). | Java Concurrency (`CompletableFuture`, `ReentrantLock`), Redlock, Raft consensus | Supporting prerequisite | Module 3, Module 8 |
| **10** | **System Design and Production Architecture** | High-level system design methodology, Back-of-the-envelope capacity estimations, Load balancing algorithms (L4 vs L7), Database horizontal scaling (Read Replicas, Sharding strategies, Consistent Hashing), Microservices vs Monolith trade-offs, Real-world designs (URL Shortener, Rate Limiter, News Feed, Payment Processing). | Nginx / Envoy, HAProxy, Sharding algorithms, Consistent Hashing | Supporting prerequisite | Modules 4-9 |
| **11** | **Observability and Production Diagnostics** | The Three Pillars of Observability (Metrics, Logs, Traces), Distributed tracing mechanics (W3C Trace Context propagation, Spans, Tracestate), Log aggregation & structured logging (JSON, MDC), Metric aggregation (Counters, Gauges, Histograms, Percentiles p50/p95/p99), OpenTelemetry instrumentation, Health checks. | OpenTelemetry SDK, Prometheus, Grafana, Micrometer, Logback / SLF4J | Supplied in working context but not yet verified against resume | Module 5, Module 6 |
| **12** | **Debugging and Testing** | The Testing Pyramid (Unit, Integration, Contract, E2E), Test-Driven Development (TDD) principles, Mocking frameworks and boundaries, In-memory vs Containerized test fixtures (Testcontainers), Thread dumps analysis, Heap dumps & memory leak diagnosis with Eclipse Memory Analyzer (MAT), Profiling CPU hotspots with async-profiler. | JUnit 5, Mockito, Testcontainers, JConsole, VisualVM, async-profiler | Supporting prerequisite | Module 3, Module 5 |
| **13** | **Containers, Deployment, and Infrastructure** | Linux cgroups and namespaces, Container vs VM architecture, Multi-stage Dockerfile optimization, Layer caching, Non-root security best practices, Docker Compose local service orchestration, Basic CI/CD pipeline stages (Lint, Test, Build, Scan, Deploy), Cloud deployment basics (12-Factor App methodology). | Docker, Docker Compose, Linux namespaces/cgroups, GitHub Actions | Supplied in working context but not yet verified against resume | Module 2, Module 5 |
| **14** | **Project Architecture and Resume Defense** | Deep technical defense of candidate's capstone backend projects, Architectural justification for database, framework, and caching choices, Production failure modes and live debugging war stories, Handling architectural pushback, Responding to "Why didn't you use X instead?" questions. | Candidate's verified portfolio technologies | To be verified upon resume inspection | Modules 1-13 |

---

## 4. Module Dependency Graph

```
                                  [01: DSA]
                                      |
                           [02: OS & Computer Networks]
                                      |
                      [03: Java & Runtime Fundamentals]
                                      |
                         [04: Database Engineering]
                                      |
                     [05: Backend Frameworks & Arch]
                                      |
           +--------------------------+--------------------------+
           |                          |                          |
   [06: API & Security]     [07: Caching & Redis]   [08: Async & Messaging]
           |                          |                          |
           +--------------------------+--------------------------+
                                      |
                     [09: Concurrency & Distributed]
                                      |
                        [10: Modern System Design]
                                      |
           +--------------------------+--------------------------+
           |                          |                          |
   [11: Observability]      [12: Testing & Debugging]  [13: Docker & Infra]
           |                          |                          |
           +--------------------------+--------------------------+
                                      |
                    [14: Project Architecture & Defense]
```

Each module is unlocked only when all its upstream dependencies are authored, self-checked, audited via QA, and approved by the user.
