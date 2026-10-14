# Module 05: Backend Frameworks and Application Engineering

## 1. Request Lifecycle & Layered Architecture Boundaries

Production web frameworks decouple network protocols from domain rules via strict architectural layers:
```
[HTTP Request] 
      |
      v
+-------------------------------------------------------+
| Controller Tier (HTTP, Routing, Status Codes, DTOs)   |
+-------------------------------------------------------+
      | (Validated Request DTO)
      v
+-------------------------------------------------------+
| Service Tier (Business Logic, Transactions, Security) |
+-------------------------------------------------------+
      | (Domain Entities)
      v
+-------------------------------------------------------+
| Repository Tier (ORM / SQL Queries, Data Access)      |
+-------------------------------------------------------+
      |
      v
[Relational Database / Storage Engine]
```

- **Over-Posting / Mass Assignment Vulnerability:** Never bind untrusted HTTP request bodies directly to persistent ORM entities. Malicious clients can inject administrative fields (e.g., `{"role": "ADMIN", "isVerified": true}`). Always bind to strict, validated Data Transfer Objects (DTOs) with field allow-lists.

---

## 2. The Node.js Runtime Model & libuv Event Loop

Node.js executes JavaScript on a single thread via the Google V8 engine, offloading asynchronous I/O to the C-based **libuv** library.

### 2.1 The 6 Phases of the libuv Event Loop
```
   +------------------------------------+
   | 1. Timers (setTimeout, setInterval)|
   +------------------------------------+
                     |
   +------------------------------------+
   | 2. Pending Callbacks (I/O Errors)  |
   +------------------------------------+
                     |
   +------------------------------------+
   | 3. Idle, Prepare (Internal Use)    |
   +------------------------------------+
                     |
   +------------------------------------+
   | 4. Poll (Incoming Connections & I/O|
   +------------------------------------+
                     |
   +------------------------------------+
   | 5. Check (setImmediate callbacks)  |
   +------------------------------------+
                     |
   +------------------------------------+
   | 6. Close Callbacks (socket.on close|
   +------------------------------------+
```

### 2.2 Microtask Drain Invariant: `nextTick` vs. `Promise.then`
Between every transition of the event loop phases, libuv drains the **Microtask Queue** completely before proceeding:
1. `process.nextTick` Queue (highest priority).
2. `Promise.then` / resolved microtasks.

*Event Loop Starvation Hazard:* Recursive calls to `process.nextTick()` create an infinite microtask loop that starves the event loop, preventing the Poll phase from ever accepting incoming network packets. Heavy CPU work (synchronous cryptographic hashing, parsing massive 50MB JSON payloads, or catastrophic regex backtracking) blocks the single thread entirely.

### 2.3 Express.js Pipeline & The Missing Return Bug
Express pipelines requests through an array of middleware functions: `(req, res, next) => {}`.
- **The Missing Return Bug:**
  ```javascript
  // FATAL BUG: Missing return causes execution to continue after res.status()
  app.post('/api/users', (req, res, next) => {
      if (!req.body.email) {
          res.status(400).json({ error: "Email required" });
          // Missing return!
      }
      userService.create(req.body); // Executes even when email is missing!
      res.status(201).json({ success: true }); // Crashes with ERR_HTTP_HEADERS_SENT
  });
  ```

---

## 3. TypeScript Runtime Boundaries & Type Erasure

TypeScript types exist exclusively at compile-time. Once compiled to JavaScript, all interfaces, type aliases, and generics are **completely erased**.
- **The Trust Boundary Invariant:** You cannot rely on TypeScript interfaces to validate untrusted incoming HTTP payloads or external database rows.
- **Runtime Schema Validation (Zod):** Enforces parsing at network boundaries:
  ```typescript
  import { z } from 'zod';

  const CreateOrderSchema = z.object({
      customerId: z.string().uuid(),
      amountCents: z.number().int().positive(),
      items: z.array(z.string().min(1)).min(1)
  });

  type CreateOrderDTO = z.infer<typeof CreateOrderSchema>;

  app.post('/api/orders', (req, res) => {
      const result = CreateOrderSchema.safeParse(req.body);
      if (!result.success) {
          return res.status(422).json({ errors: result.error.flatten() });
      }
      const validPayload: CreateOrderDTO = result.data;
      // validPayload is provably safe at runtime
  });
  ```

---

## 4. Java Web Foundations & Spring Core Architecture

### 4.1 Thread-per-Request Servlet Model & DispatcherServlet
Traditional Java web applications execute on embedded Servlet containers (Tomcat, Jetty) using a **Thread-per-Request** model:
1. Container thread pool (typically 200 worker threads) assigns one OS thread per connection.
2. The `DispatcherServlet` acts as the Front Controller:
   - Queries `HandlerMapping` to locate controller method matching URL.
   - Dispatches request via `HandlerAdapter` (executes argument resolvers, validation).
   - Marshals return objects to JSON via `HttpMessageConverter` (Jackson).

### 4.2 Inversion of Control & Spring Bean Lifecycle
Spring's IoC container manages the instantiation, configuration, and assembly of application components:
- **Constructor Injection Superiority:**
  - Guarantees immutability (`final` dependencies).
  - Prevents `NullPointerException` during standalone unit testing (impossible to instantiate without providing dependencies).
  - Detects circular dependencies at application startup rather than runtime.
- **Spring Bean Lifecycle Sequence:**
  1. Class instantiation via constructor.
  2. Dependency injection (populating bean properties).
  3. Aware interfaces invoked (`BeanNameAware`, `BeanFactoryAware`).
  4. `BeanPostProcessor.postProcessBeforeInitialization()`.
  5. `@PostConstruct` annotated methods / `InitializingBean.afterPropertiesSet()`.
  6. `BeanPostProcessor.postProcessAfterInitialization()`: **Dynamic AOP proxies are created here**.
  7. Bean in service.
  8. `@PreDestroy` / `DisposableBean.destroy()` upon shutdown.

---

## 5. Persistence with JPA and Hibernate Internals

### 5.1 The 4 Entity States in JPA
```
                  [new Entity()]
                        |
                        v
                 +---------------+
                 |   TRANSIENT   | (Not associated with Persistence Context;
                 +---------------+  no database identity)
                        |
            persist()   |   find() / merge()
                        v
                 +---------------+
+--------------> |    MANAGED    | (Attached to Persistence Context;
|                +---------------+  automatic dirty checking enabled)
|                       |
|           detach()    |   remove()
|           close()     v
|                +---------------+
|                |    REMOVED    | (Scheduled for SQL DELETE on flush)
|                +---------------+
|                       |
|                       v  persist()
+-----------------------+
```

### 5.2 Automatic Dirty Checking & The N+1 Query Problem
- **Dirty Checking:** When an entity is loaded, Hibernate stores an internal snapshot of its attribute values. During transaction commit (or before flush), Hibernate compares current entity state against the snapshot, generating minimal SQL `UPDATE` statements for altered columns.
- **The $N+1$ Query Problem:** Accessing a lazily loaded relationship (`@OneToMany`) inside a loop:
  ```java
  List<User> users = userRepository.findAll(); // 1 query: SELECT * FROM users (returns 100 rows)
  for (User user : users) {
      System.out.println(user.getOrders().size()); // Fires 100 queries: SELECT * FROM orders WHERE user_id = ?
  }
  ```
  *Solution:* Use `JOIN FETCH` in JPQL or `@EntityGraph`:
  ```sql
  SELECT u FROM User u LEFT JOIN FETCH u.orders;
  ```

---

## 6. Declarative Transactions (`@Transactional`) & Traps

Spring intercepts `@Transactional` methods using dynamic runtime proxies (CGLIB subclasses or JDK dynamic proxies).

### 6.1 The Self-Invocation Trap
```java
@Service
public class OrderService {
    public void processOrder(Order order) {
        // FATAL: Direct internal call bypasses the Spring AOP Proxy!
        // No transaction is ever started.
        this.saveWithAudit(order); 
    }

    @Transactional
    public void saveWithAudit(Order order) {
        orderRepository.save(order);
    }
}
```
*Remedy:* Move `saveWithAudit()` to a separate injected service bean, or inject the bean proxy into itself.

### 6.2 Checked Exception Rollback Trap
By default, Spring `@Transactional` rolls back strictly on unchecked exceptions (`RuntimeException` and `Error`). It **does not roll back** on checked exceptions (`IOException`, `SQLException`, custom exceptions extending `Exception`):
```java
// REQUIRED FIX: Explicitly specify rollback rules
@Transactional(rollbackFor = Exception.class)
public void updateAccount() throws AccountException { ... }
```

### 6.3 Transaction Boundary Invariant: No Remote I/O
**Inviolable Rule:** Never execute remote network calls (HTTP requests, third-party payment gateways, message bus publications) inside a `@Transactional` boundary. Network latency or downstream timeouts will hold the database connection open for seconds, completely exhausting the database connection pool.

---

## 7. Framework Comparison & Production Selection Guide

| Criterion | Spring Boot (Java 21) | Node.js / Express (TypeScript) |
| :--- | :--- | :--- |
| **Concurrency Model** | Multi-threaded (Thread-per-request / Virtual Threads) | Single-threaded event loop + libuv thread pool |
| **Type Safety** | Strict nominal typing enforced at compile and runtime | Structural compile-time typing (erased at runtime) |
| **CPU-Bound Tasks** | Excellent; utilizes all cores via ForkJoin / threads | Terrible; blocks event loop unless offloaded to Worker threads |
| **I/O-Bound Tasks** | Highly scalable with Java 21 Virtual Threads | Excellent native asynchronous non-blocking I/O |
| **Memory Footprint** | Moderate to High (150MB–500MB baseline) | Very Low (40MB–100MB baseline) |
| **Ecosystem Maturity** | Enterprise gold standard: Spring Security, JPA, Batch | Vast npm ecosystem; requires disciplined architectural gating |

---

## 8. Failure Modes & Application Triage

| Failure Pattern | Symptom / Error | Root Cause | Solution |
| :--- | :--- | :--- | :--- |
| **Event Loop Blocking** | Node.js API latency spikes to seconds; health checks fail. | Synchronous CPU computation (heavy JSON parsing, regex) on event loop. | Offload CPU work to `worker_threads` or streaming parsers. |
| **Connection Exhaustion** | `HikariPool: Connection is not available, request timed out`. | Remote HTTP or SMTP calls executed inside `@Transactional` holding DB connections. | Move remote calls outside transaction; use Transactional Outbox pattern. |
| **Headers Already Sent** | `Error [ERR_HTTP_HEADERS_SENT]: Cannot set headers after they are sent`. | Missing `return` statement after `res.json()` in conditional Express middleware. | Add explicit `return res.status(...).json(...)`. |
| **LazyInitializationException** | `failed to lazily initialize a collection of role: could not initialize proxy - no Session`. | Accessing lazy-loaded entity collection after Hibernate session/transaction closed. | Fetch required relations eagerly via `JOIN FETCH` or DTO projections. |
---

## 9. High-Bar Backend Framework Interview Scenarios

### Scenario 1: The Self-Invocation `@Transactional` Proxy Bypass
- **Interview Question:** "A developer marks `methodB()` with `@Transactional(propagation = Propagation.REQUIRES_NEW)`. When called from `methodA()` within the same Spring service bean, no new transaction is created. Why?"
- **Deep Technical Answer:**
  1. Spring implements declarative transactions using **Dynamic Proxies** (CGLIB subclasses).
  2. External callers invoke the proxy, which executes interceptor transaction logic before delegating to the target bean.
  3. When `methodA()` executes `this.methodB()`, the call is dispatched against the raw underlying Java object instance (`this`), completely **bypassing the Spring AOP Proxy**.
  4. **Solution:** Move `methodB()` to an independent service bean, or inject the bean proxy into itself via `@Autowired private OrderService self;`.

### Scenario 2: Event Loop Latency Spikes in Node.js Microservices
- **Interview Question:** "A Node.js microservice handling 5,000 WebSocket connections experiences massive event loop lag (> 2,000ms) during user authentication. How do you triage and resolve it?"
- **Deep Technical Answer:**
  1. **Root Cause:** Authentication executes synchronous password hashing (`bcrypt.hashSync()`) or parsing huge JSON configuration objects directly on the single-threaded V8 event loop.
  2. This starves the libuv Poll phase, preventing new incoming TCP packets or WebSocket frames from being processed.
  3. **Triage:** Measure event loop delay using `perf_hooks.monitorEventLoopDelay()`.
  4. **Resolution:** Offload CPU computation to worker threads (`worker_threads`) or use asynchronous native cryptographic methods (`crypto.scrypt()` with libuv thread pool offload).

### Scenario 3: Hibernate `LazyInitializationException` and OSIV Anti-Pattern
- **Interview Question:** "Why does Hibernate throw `LazyInitializationException` and why is enabling `spring.jpa.open-in-view=true` considered a dangerous production anti-pattern?"
- **Deep Technical Answer:**
  1. **Root Cause:** An entity's lazy collection is accessed after the persistence context (Hibernate Session) has closed (e.g., in a presentation controller or JSON serializer).
  2. **The OSIV Anti-Pattern:** Open Session In View keeps the database connection and Hibernate session open until the HTTP response body is fully serialized to the network socket.
  3. **Production Disaster:** If a client has a slow network connection, the application holds the underlying database connection open for seconds while writing HTTP bytes. A small traffic burst completely exhausts the HikariCP connection pool, crashing the entire service.
  4. **Fix:** Disable OSIV (`open-in-view: false`). Fetch required data in the Service tier using JPQL `JOIN FETCH` or DTO projections.
