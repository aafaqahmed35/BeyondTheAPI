# Resume Technology and Skill Inventory

## 1. Verification Statement and Status

> **Resume-specific verification is pending. The curriculum currently uses the available project and working-context information. No claim is made that the inventory is a complete extraction of the user’s resume.**

At the time of this initial repository setup, no physical or digital resume document (e.g., PDF, DOCX, TXT) was discovered in the repository root or adjacent paths. Consequently, all technologies listed below are categorized strictly according to working-context information supplied in the initial master execution prompt, foundational CS prerequisites, or candidate project profiles. 

No certifications, enterprise work history, production metrics, or performance claims have been fabricated.

---

## 2. Technology Inventory Table

| Technology / Concept | Category | Associated Project (if known) | Source of Evidence | Verification Status | Relevant Module(s) | Required Coverage | Open Questions for Candidate |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Data Structures & Algorithms** | Core Computer Science | Core Academic / Interview Prep | Master Prompt Context | Supporting prerequisite | Module 1 | Comprehensive algorithmic foundations, 20 canonical patterns, complexity proofs, Java implementations. | What specific platforms (LeetCode, Codeforces) or target question count are you aiming for? |
| **Java (JDK 17/21)** | Language & Runtime | Backend Services / Academic | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 1, Module 3, Module 5 | Language syntax, collections internals, memory model, GC basics, concurrency, Streams, OOP. | Which Java versions were used in coursework or personal projects? |
| **Operating Systems & Networking** | Systems Engineering | Core Academic Prerequisite | Master Prompt Context | Supporting prerequisite | Module 2 | Processes vs threads, virtual memory, paging, TCP/IP handshake, HTTP/HTTPS, sockets, I/O multiplexing. | Have you written low-level networking or C/POSIX systems programs? |
| **Spring Boot (3.x)** | Application Framework | Backend Microservices | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 5, Module 6, Module 7 | IoC/DI, Spring MVC, REST controllers, Spring Data JPA, security filters, exception handling, lifecycle. | Did you build a standalone microservice or monolithic Spring project? Which version? |
| **Node.js / Express.js** | Backend Runtime & Framework | REST API Service | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 5, Module 6, Module 8 | Event loop, libuv, asynchronous I/O, middleware architecture, error handling, clustering. | Was Express used in your main project or a secondary portfolio piece? |
| **TypeScript** | Language & Type System | Full-Stack / Backend Services | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 5, Module 6 | Structural typing, generics, interfaces, compilation, runtime validation (Zod). | Was TypeScript utilized across the full stack or backend only? |
| **PostgreSQL / MySQL** | Relational Databases | Relational Storage Tier | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 4, Module 10 | B-Tree indexing, execution plans, ACID properties, isolation levels (MVCC), normalization, query optimization. | What schemas and query workloads did you design? Did you manage migrations? |
| **SQL & Sequelize / JPA** | Data Access & ORMs | Relational Storage Tier | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 4, Module 5 | Complex joins, window functions, indexing strategies, N+1 query problem, connection pooling (HikariCP). | Which ORM or query builder did you configure personally? |
| **Redis** | In-Memory Data Store | Cache & Session Store | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 7, Module 10 | Data types (Strings, Hashes, Sets, ZSets), caching patterns (Cache-Aside, Write-Through), eviction policies, TTL. | Did you use Redis solely for caching, or also for rate-limiting / distributed locks? |
| **Docker & Docker Compose** | Containerization & DevOps | Local Environment / Deployment | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 13 | Multi-stage Dockerfiles, image optimization, container networking, volumes, compose orchestration. | Have you deployed containers to a cloud provider or run them locally? |
| **REST APIs & HTTP/HTTPS** | Web Architecture | API Gateway / Service Layer | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 6 | Idempotency, HTTP status codes, statelessness, payload design, versioning, CORS, JWT authentication. | How did you handle API authentication and rate limiting in your projects? |
| **Distributed Messaging & Workers** | Asynchronous Systems | Event-Driven Architecture | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 8, Module 9 | Queues vs Topics, Kafka / RabbitMQ concepts, consumer groups, backpressure, idempotency. | Did your projects incorporate a message broker or background queue (e.g. BullMQ, Celery, RabbitMQ)? |
| **Observability (OpenTelemetry)** | Operations & Monitoring | Production Diagnostics | Master Prompt Context | Supplied in working context but not yet verified against resume | Module 11 | Metrics, logs, distributed tracing, spans, trace context propagation, monitoring alerts. | Did you instrument code with OpenTelemetry SDK or use application loggers? |
| **Testing & Test Automation** | Software Quality | Unit & Integration Testing | Master Prompt Context | Supporting prerequisite | Module 12 | JUnit 5, Mockito, test pyramids, mocking DBs, edge-case coverage, integration tests. | What test coverage percentage or frameworks were implemented? |

---

## 3. Reconciliation and Verification Plan

When the candidate provides an updated resume document:
1. Cross-reference every claimed project, library, framework, database, and tool against this inventory.
2. Update the `Verification Status` to `Verified from resume` for matching items.
3. Add any newly identified resume claims (e.g., specific AWS services, GraphQL, gRPC, CI/CD pipelines) to the appropriate curriculum module.
4. Flag any technologies mentioned in this working inventory that do *not* appear on the resume as either `Supporting prerequisite` or `Optional extension`.
