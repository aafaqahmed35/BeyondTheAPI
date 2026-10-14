# Module 12: Debugging & Testing — Engineering Confidence, Defect Isolation, and Production Diagnostics

> **Authoritative Curriculum Standard:** FAANG Backend Engineering Handbook
> **Target Audience:** B.Tech / BE Freshers, Junior Backend Engineers (SDE-1 / SDE-2), Systems Engineering & Production Reliability Candidates
> **Target Specifications & Context:** Deterministic Unit & Integration Testing, Test Doubles (Dummy/Stub/Fake/Mock/Spy), JUnit 5 & Node.js Asynchronous Testing, Database Isolation & Testcontainers, Concurrency Harnesses, Mutation Testing, Profiling (Heap/CPU/Thread), Root-Cause Defect Isolation, and Production Incident Mitigation
> **Prerequisites:** [Module 04 — Database Engineering & SQL](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_04_database_engineering.md), [Module 05 — Backend Frameworks](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_05_backend_frameworks.md), [Module 06 — API Engineering & Security](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_06_api_engineering_security.md), [Module 07 — Caching & Performance](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_07_caching_performance.md), [Module 08 — Asynchronous Processing & Messaging](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_08_async_processing_messaging.md), [Module 09 — Concurrency & Distributed Systems](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_09_concurrency_distributed_systems.md), [Module 10 — System Design & Production Architecture](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_10_system_design_production_architecture.md), [Module 11 — Observability & Diagnostics](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_11_observability.md)
> **Forward Links:** [Module 13 — Linux, Containers, Deployment & Infrastructure](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_13_linux_containers_deployment_infrastructure.md)

---

## Table of Contents

1. [Section 1 — Why Testing Exists: Risk Reduction, Feedback Speed, and Confidence](#section-1--why-testing-exists-risk-reduction-feedback-speed-and-confidence)
2. [Section 2 — Test Levels and the Testing Pyramid: Unit, Integration, Component, Contract, End-to-End](#section-2--test-levels-and-the-testing-pyramid-unit-integration-component-contract-end-to-end)
3. [Section 3 — Test Strategy Based on Risk, Boundaries, and Failure Impact](#section-3--test-strategy-based-on-risk-boundaries-and-failure-impact)
4. [Section 4 — Testability, Dependency Boundaries, and Separation of Concerns](#section-4--testability-dependency-boundaries-and-separation-of-concerns)
5. [Section 5 — Unit-Testing Principles and Deterministic Test Design](#section-5--unit-testing-principles-and-deterministic-test-design)
6. [Section 6 — Java Testing with JUnit 5 and Established Repository Conventions](#section-6--java-testing-with-junit-5-and-established-repository-conventions)
7. [Section 7 — Assertions, Test Fixtures, Setup/Teardown, Parameterized Tests, and Test Naming](#section-7--assertions-test-fixtures-setupteardown-parameterized-tests-and-test-naming)
8. [Section 8 — Mocking, Stubbing, Fakes, Spies, and When Not to Mock](#section-8--mocking-stubbing-fakes-spies-and-when-not-to-mock)
9. [Section 9 — Testing Spring Boot Services, Controllers, Repositories, and Application Boundaries](#section-9--testing-spring-boot-services-controllers-repositories-and-application-boundaries)
10. [Section 10 — Testing Node.js Services and Asynchronous Code](#section-10--testing-nodejs-services-and-asynchronous-code)
11. [Section 11 — Database Integration Tests, Transaction Behavior, Isolation, and Test Data](#section-11--database-integration-tests-transaction-behavior-isolation-and-test-data)
12. [Section 12 — Testing Redis, Caching, and Invalidation Behavior](#section-12--testing-redis-caching-and-invalidation-behavior)
13. [Section 13 — Testing APIs: Status Codes, Validation, Authorization, Error Contracts, and Compatibility](#section-13--testing-apis-status-codes-validation-authorization-error-contracts-and-compatibility)
14. [Section 14 — Contract Testing and Compatibility Across Service Boundaries](#section-14--contract-testing-and-compatibility-across-service-boundaries)
15. [Section 15 — Testing Asynchronous Consumers, Retries, Idempotency, and Duplicate Delivery](#section-15--testing-asynchronous-consumers-retries-idempotency-and-duplicate-delivery)
16. [Section 16 — Concurrency Tests, Race Conditions, Deadlocks, and Nondeterminism](#section-16--concurrency-tests-race-conditions-deadlocks-and-nondeterminism)
17. [Section 17 — Property-Based Testing, Invariants, Boundary Testing, and Fuzzing Concepts](#section-17--property-based-testing-invariants-boundary-testing-and-fuzzing-concepts)
18. [Section 18 — Regression Testing and Reproducing Reported Defects](#section-18--regression-testing-and-reproducing-reported-defects)
19. [Section 19 — Debugging Fundamentals: Observe, Hypothesize, Isolate, Verify](#section-19--debugging-fundamentals-observe-hypothesize-isolate-verify)
20. [Section 20 — A Repeatable Debugging Workflow for Unfamiliar Codebases](#section-20--a-repeatable-debugging-workflow-for-unfamiliar-codebases)
21. [Section 21 — Reading Stack Traces, Exception Chains, Logs, Metrics, and Traces](#section-21--reading-stack-traces-exception-chains-logs-metrics-and-traces)
22. [Section 22 — Breakpoints, Stepping, Watches, Conditional Breakpoints, and Debugger Pitfalls](#section-22--breakpoints-stepping-watches-conditional-breakpoints-and-debugger-pitfalls)
23. [Section 23 — Heap, CPU, Thread, and Allocation Profiling Fundamentals](#section-23--heap-cpu-thread-and-allocation-profiling-fundamentals)
24. [Section 24 — Memory Leaks, Resource Leaks, Connection-Pool Exhaustion, and Thread Starvation](#section-24--memory-leaks-resource-leaks-connection-pool-exhaustion-and-thread-starvation)
25. [Section 25 — Performance Testing: Load, Stress, Spike, Soak, and Capacity Testing](#section-25--performance-testing-load-stress-spike-soak-and-capacity-testing)
26. [Section 26 — Benchmarking Pitfalls, Warm-Up, Variance, and Statistically Responsible Comparisons](#section-26--benchmarking-pitfalls-warm-up-variance-and-statistically-responsible-comparisons)
27. [Section 27 — Flaky Tests, Time-Dependent Behavior, Randomness, Shared State, and External Dependencies](#section-27--flaky-tests-time-dependent-behavior-randomness-shared-state-and-external-dependencies)
28. [Section 28 — Test Isolation, Parallel Execution, Test Containers, and Disposable Environments](#section-28--test-isolation-parallel-execution-test-containers-and-disposable-environments)
29. [Section 29 — CI Test Stages, Quality Gates, Test Selection, and Failure Triage](#section-29--ci-test-stages-quality-gates-test-selection-and-failure-triage)
30. [Section 30 — Coverage Metrics: What Line/Branch Coverage Reveals and What It Cannot Prove](#section-30--coverage-metrics-what-linebranch-coverage-reveals-and-what-it-cannot-prove)
31. [Section 31 — Mutation Testing and the Limits of Coverage-Driven Optimization](#section-31--mutation-testing-and-the-limits-of-coverage-driven-optimization)
32. [Section 32 — Production Debugging, Safe Mitigation, Rollback, and Incident Follow-Up](#section-32--production-debugging-safe-mitigation-rollback-and-incident-follow-up)
33. [Section 33 — Testing Security Boundaries and Failure Paths](#section-33--testing-security-boundaries-and-failure-paths)
34. [Section 34 — Common Testing and Debugging Interview Questions](#section-34--common-testing-and-debugging-interview-questions)
35. [Section 35 — Scenario-Based Debugging Exercises with Worked Reasoning](#section-35--scenario-based-debugging-exercises-with-worked-reasoning)
36. [Section 36 — Module 12 Cheatsheet: Debugging & Testing](#section-36-module-12-cheatsheet-debugging--testing)

---

## Section 1 — Why Testing Exists: Risk Reduction, Feedback Speed, and Confidence

### Intuition & Definition
Testing is not an academic chore or a regulatory checkbox; it is an economic mechanism for **risk reduction** and **feedback acceleration**. In software engineering, the cost of discovering and rectifying a defect scales exponentially across the delivery lifecycle:

$$\text{Cost of Defect}(\text{Local Dev}) \ll \text{Cost}(\text{CI Build}) \ll \text{Cost}(\text{Staging}) \ll \text{Cost}(\text{Production Outage})$$

A bug caught at a developer's workstation within 200 milliseconds of compiling costs pennies in developer attention. The same bug discovered as data corruption in production across 10,000 distributed database rows costs thousands of dollars in emergency engineering hours, lost customer trust, regulatory fines, and permanent reputational damage.

### The Fundamental Axiom of Testing
> **Dijkstra's Axiom:** *"Program testing can be used to show the presence of bugs, but never to show their absence!"*

A passing test suite does **not** prove that software is correct. It merely proves that under the specific inputs, mock configurations, and execution paths exercised by the tests, none of the codified assertions failed. High engineering confidence requires recognizing what tests can and cannot guarantee.

### Operational Trade-Offs
| Quality Attribute | Low Automated Testing | High Automated Testing |
| :--- | :--- | :--- |
| **Delivery Velocity** | Rapid initial hacking, grinding halt at 6 months | Steady, sustainable throughput over years |
| **Refactoring Safety** | Fear of touching legacy code ("if it works, don't touch it") | Fearless modernization; regressions caught in seconds |
| **Defect Detection** | End-users act as unpaid QA engineers | Regressions trapped in local builds & CI pipelines |
| **Onboarding Time** | Months spent reading unwritten tribal rules | New hires deploy code on Day 1 backed by green suites |

---

## Section 2 — Test Levels and the Testing Pyramid: Unit, Integration, Component, Contract, End-to-End

### The Testing Pyramid
The Testing Pyramid (popularized by Mike Cohn and Martin Fowler) establishes an architectural balance between **execution speed**, **isolation**, **debugging clarity**, and **system fidelity**.

```
                / \
               /   \      End-to-End (E2E)
              /     \     - High fidelity, slowest, highest cost, most brittle
             /-------\
            /         \   Contract & Component Tests
           /           \  - Inter-service schema & subsystem boundaries
          /-------------\
         /               \ Integration Tests
        /                 \ - Real DB, real cache, real broker containers
       /-------------------\
      /                     \ Unit Tests
     /                       \ - In-memory, nanosecond speed, deterministic, isolated
    /-------------------------\
```

### Detailed Layer Characteristics

1. **Unit Tests**:
   - **Scope:** Single class, method, or pure domain function.
   - **Dependencies:** All external I/O (disk, network, database, third-party APIs) is replaced with memory-bound doubles or pure function inputs.
   - **Execution Time:** $< 10\text{ ms}$ per test.
   - **Role:** Exercises algorithmic edge cases, permutations, validation branches, and domain business rules.

2. **Integration Tests**:
   - **Scope:** Interaction between domain code and external infrastructure (PostgreSQL repositories, Redis caches, Kafka producers/consumers).
   - **Dependencies:** Real infrastructure run locally via disposable containers (e.g., Testcontainers).
   - **Execution Time:** $100\text{ ms} - 2\text{ s}$ per test.
   - **Role:** Validates SQL dialect compatibility, transaction rollbacks, index triggers, serialization formats, and connection pool timeouts.

3. **Contract Tests**:
   - **Scope:** Boundary agreements between service consumers and providers.
   - **Dependencies:** Consumer-driven contract files (Pact / Spring Cloud Contract); no live downstream network calls required.
   - **Role:** Guarantees backward and forward API schema compatibility without spinning up an entire microservice fleet.

4. **Component Tests**:
   - **Scope:** An entire standalone service running in-memory with external network collaborators mocked at the HTTP/gRPC boundary (e.g., WireMock).
   - **Role:** Verifies dependency injection wiring, HTTP routing, filter chains, security interceptors, and application startup health.

5. **End-to-End (E2E) Tests**:
   - **Scope:** User journeys spanning the entire distributed topology (Ingress $\to$ Gateway $\to$ Service A $\to$ Kafka $\to$ Service B $\to$ DB).
   - **Role:** Final smoke tests verifying deployment wiring; intentionally kept minimal in quantity due to flakiness and high execution cost.

---

## Section 3 — Test Strategy Based on Risk, Boundaries, and Failure Impact

### Risk-Based Test Prioritization
Engineering teams that attempt to write 100% End-to-End tests suffer crippling CI queue times and chronic flakiness. Conversely, teams with only unit tests face catastrophic production failures when real databases or third-party gateways reject payloads.

A robust engineering test strategy evaluates two dimensions: **Probability of Failure** and **Impact of Failure**.

$$\text{Test Investment} \propto \text{Complexity} \times \text{Blast Radius}$$

```
    High Impact | [High Integration + Invariant Tests]  | [Full Contract + E2E Tests]
                | Core Ledger / Payments Engine        | User Checkout / Authentication
    ------------+---------------------------------------+--------------------------------
    Low Impact  | [Pure Unit Tests]                     | [Basic Smoke Tests]
                | Slug Generation / String Formatting   | Admin Analytics Export
                +---------------------------------------+--------------------------------
                                 Low Probability                     High Probability
```

### Boundary Identification
Every software service possesses three primary boundaries:
1. **Inbound Transport Boundary:** HTTP controllers, gRPC stubs, message consumers. Tested for validation, authorization, and deserialization.
2. **Core Domain Boundary:** Pure entities, aggregate roots, calculators, finite state machines. Tested exhaustively with fast, deterministic unit tests.
3. **Outbound Infrastructure Boundary:** SQL queries, Redis cache writes, third-party payment clients. Tested with integration tests using disposable, real instances.

---

## Section 4 — Testability, Dependency Boundaries, and Separation of Concerns

### Architecture Dictates Testability
Code that is difficult to test is almost always poorly designed. High coupling, global mutable state, hidden dependencies instantiated via `new` inside methods, and mixed concerns turn test suites into brittle nightmares.

```java
// ANTI-PATTERN: Untestable tightly coupled service
public class UntestableOrderService {
    public void checkout(String orderId) {
        // Tightly coupled static call + hidden network call + hidden database call
        PaymentClient client = new PaymentClient("https://prod.gateway.com");
        boolean ok = client.chargeCreditCard(DatabasePool.getConnection(), orderId);
        if (ok) {
            SystemEmailSender.sendReceipt(orderId); // Global static side-effect
        }
    }
}
```

```java
// PRODUCTION-GRADE: Testable via Dependency Inversion & Ports-and-Adapters
public class TestableOrderService {
    private final PaymentPort paymentPort;
    private final NotificationPort notificationPort;
    private final OrderRepository orderRepository;

    public TestableOrderService(PaymentPort paymentPort,
                                NotificationPort notificationPort,
                                OrderRepository orderRepository) {
        this.paymentPort = Objects.requireNonNull(paymentPort);
        this.notificationPort = Objects.requireNonNull(notificationPort);
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    public boolean checkout(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.isPayable()) {
            return false;
        }

        boolean charged = paymentPort.charge(order.getCustomerId(), order.getTotalAmount());
        if (charged) {
            order.markPaid();
            orderRepository.save(order);
            notificationPort.sendReceipt(order.getCustomerEmail(), order.getId());
            return true;
        }
        return false;
    }
}
```

### Seams and Hexagonal Architecture
By introducing interfaces (Ports) and injecting dependencies (Adapters), we create **seams** where test harnesses can substitute real infrastructure with fast, deterministic test doubles without modifying production logic.

---

## Section 5 — Unit-Testing Principles and Deterministic Test Design

### The F.I.R.S.T. Principles of Unit Testing
- **Fast:** Tests must execute in milliseconds. A test suite taking 15 minutes will be skipped by developers before committing.
- **Isolated / Independent:** Tests must not depend on the execution order of other tests. Test B must pass whether run first, last, or in parallel with Test A.
- **Repeatable:** Running a test 1,000 times in any environment (local macOS, Linux Docker CI, Windows) must yield identical results. No reliance on live network, unseeded random numbers, or system clocks.
- **Self-Validating:** Tests must report a binary pass/fail status. No manual inspection of console logs or debugging output is permitted.
- **Thorough / Timely:** Tests must cover edge cases, null boundaries, empty collections, and failure conditions, written alongside or prior to implementation.

### State vs. Interaction Testing
- **State-Based Testing:** Exercises an operation and asserts the resulting state of the object or data store (e.g., `assertEquals(OrderState.PAID, order.getState())`). Strongly preferred because it decouples tests from internal implementation details.
- **Interaction-Based Testing:** Asserts that specific methods were invoked on collaborators with specific parameters (e.g., `verify(paymentGateway, times(1)).charge(any())`). Use sparingly: over-reliance creates brittle tests that break during internal refactoring even when external behavior is preserved.

---

## Section 6 — Java Testing with JUnit 5 and Established Repository Conventions

JUnit 5 (Jupiter) is the modern standard for Java testing, separating the API (`junit-jupiter-api`) from the execution engine (`junit-jupiter-engine`).

### Core JUnit 5 Annotations
```java
package assets.code_examples;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Wallet Domain Model & Debit Calculations")
class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet("usr_100", 500.0);
    }

    @AfterEach
    void tearDown() {
        wallet = null; // Facilitate GC in large test suites
    }

    @Test
    @DisplayName("Should successfully debit balance when funds are sufficient")
    void shouldDebitWhenSufficientFunds() {
        boolean success = wallet.debit(200.0);
        assertTrue(success, "Debit operation should return true");
        assertEquals(300.0, wallet.getBalance(), 0.001, "Remaining balance must be 300.0");
    }

    @Test
    @DisplayName("Should reject debit and throw when amount is negative")
    void shouldThrowWhenDebitAmountIsNegative() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> wallet.debit(-50.0),
                "Negative debit must raise IllegalArgumentException"
        );
        assertTrue(ex.getMessage().contains("must be positive"));
    }

    @Test
    @DisplayName("Should enforce execution timeout on intensive calculation")
    void shouldCompleteWithinTimeout() {
        assertTimeoutPreemptively(Duration.ofMillis(100), () -> {
            wallet.calculateAccruedInterest(30);
        });
    }
}
```

---

## Section 7 — Assertions, Test Fixtures, Setup/Teardown, Parameterized Tests, and Test Naming

### Parameterized Tests: Eliminating Test Duplication
When verifying multiple boundary inputs against a single specification, parameterized tests prevent copy-paste bloat while ensuring each permutation runs as an independent test case.

```java
@ParameterizedTest(name = "Tier {0} with credit score {1} should receive interest rate {2}%")
@CsvSource({
    "STANDARD, 600, 12.5",
    "STANDARD, 720,  9.8",
    "GOLD,     750,  7.2",
    "PLATINUM, 800,  4.5"
})
void shouldCalculateCorrectInterestRates(String tier, int creditScore, double expectedRate) {
    RateEngine engine = new RateEngine();
    double rate = engine.computeRate(CustomerTier.valueOf(tier), creditScore);
    assertEquals(expectedRate, rate, 0.01);
}
```

### Expressive Test Naming Conventions
A test name must explain **what** is being tested, **under what condition**, and **what the expected outcome is**.
- *Weak:* `testOrder()`
- *Better:* `testOrderPayment()`
- *Production Standard:* `givenActiveAccount_whenSufficientBalance_thenDebitSucceedsAndEmitsEvent()`

---

## Section 8 — Mocking, Stubbing, Fakes, Spies, and When Not to Mock

### The Gerard Meszaros Test Doubles Taxonomy
Many engineers use the word "mock" to describe every substitute object. In modern software engineering, test doubles fall into five distinct categories with precise behavioral definitions:

```
                      +-------------------+
                      |    Test Double    |
                      +-------------------+
                                |
     +------------+-------------+-------------+------------+
     |            |             |             |            |
+----+----+  +----+----+   +----+----+   +----+----+  +----+----+
|  Dummy  |  |  Stub   |   |  Fake   |   |  Mock   |  |   Spy   |
+---------+  +---------+   +---------+   +---------+  +---------+
Passed but   Canned        Working       Pre-set      Wraps real
never called responses     in-memory     expectations object; logs
             no state      shortcut      assertions   calls
```

### Definitions & Comparison Matrix

| Test Double | Purpose | Has Business Logic? | Verifies Interactions? | Example Use Case |
| :--- | :--- | :---: | :---: | :--- |
| **Dummy** | Fills parameter lists to satisfy compiler | No | No | Logger or Audit context passed to constructor |
| **Stub** | Returns fixed, canned data for queries | No | No | Currency converter returning fixed `1.10` exchange rate |
| **Fake** | Fully functional in-memory implementation | Yes (lightweight) | No | `InMemoryUserRepository` using `ConcurrentHashMap` |
| **Mock** | Programmed with expectations; verifies calls | No | Yes | `EmailClient` verifying `send()` called once with recipient |
| **Spy** | Intercepts real object; records invocations | Yes (delegates) | Yes | Verifying audit logger received exact sanitization payload |

### The Over-Mocking Anti-Pattern
Over-mocking occurs when a test suite mocks every collaborator, including internal domain classes, database queries, and private helpers.
- **The Symptom:** Tests pass 100%, but the application fails immediately upon deployment because real SQL queries have syntax errors, or real JSON serializers fail on circular dependencies.
- **The Rule:** Mock only at **architectural boundaries** (external third-party APIs, email delivery services, message queues). Use **Fakes** for storage layers and test domain models as real instances.

---

## Section 9 — Testing Spring Boot Services, Controllers, Repositories, and Application Boundaries

### Spring Test Slices vs. Full Context
Spring Boot provides test slices that load only the beans required for a specific layer, drastically reducing test execution time compared to loading the entire application context (`@SpringBootTest`).

```
                +---------------------------------------------+
                |        @SpringBootTest (Full Context)       |
                | - Loads all beans, filters, security, JPA   |
                | - Slowest startup; highest fidelity         |
                +---------------------------------------------+
                                       |
          +----------------------------+----------------------------+
          |                                                         |
+------------------------------+                          +------------------------------+
|   @WebMvcTest (Web Slice)    |                          |   @DataJpaTest (Data Slice)  |
| - Loads Controllers, Filters |                          | - Loads Entities, Repos      |
| - Mocks Services (@MockBean) |                          | - In-memory DB / Testcontainers|
| - Uses MockMvc (No HTTP port)|                          | - Auto-rollback transactions |
+------------------------------+                          +------------------------------+
```

### Testing the Web Slice with MockMvc
```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void shouldReturnCreatedWhenPayloadIsValid() throws Exception {
        OrderResponse mockResponse = new OrderResponse("ord_123", "CREATED", 150.0);
        Mockito.when(orderService.createOrder(any())).thenReturn(mockResponse);

        String jsonPayload = """
            {
                "customerId": "cust_99",
                "amount": 150.0
            }
            """;

        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("ord_123"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }
}
```

---

## Section 10 — Testing Node.js Services and Asynchronous Code

Node.js asynchronous testing requires strict handling of Promise resolutions, rejection catching, and event emitter cleanup.

### Trapping Unhandled Rejections & Async Retries
```javascript
import assert from 'node:assert/strict';
import test from 'node:test';

test('Async retry mechanism should back off and succeed on third attempt', async () => {
    let attempts = 0;
    const flakyApi = async () => {
        attempts++;
        if (attempts < 3) throw new Error('Transient 503 Service Unavailable');
        return { data: 'PAYLOAD_OK' };
    };

    const result = await fetchWithRetry(flakyApi, 3, 10);
    assert.equal(result.data, 'PAYLOAD_OK');
    assert.equal(attempts, 3);
});

test('Should reject with explicit error when retries are exhausted', async () => {
    const brokenApi = async () => { throw new Error('Downstream Timeout'); };
    await assert.rejects(
        async () => fetchWithRetry(brokenApi, 2, 5),
        /Max retries reached \(2\)/
    );
});
```

---

## Section 11 — Database Integration Tests, Transaction Behavior, Isolation, and Test Data

### Why In-Memory Databases (H2) Lie
For years, tutorials recommended testing Spring Boot repositories against in-memory H2 databases. In production, this causes catastrophic defects:
1. **Dialect Discrepancies:** PostgreSQL features like `JSONB`, `ON CONFLICT DO UPDATE`, Window Functions, and `ILIKE` behave differently or fail on H2.
2. **Locking & Concurrency:** H2 does not replicate PostgreSQL Multi-Version Concurrency Control (MVCC) or row-level `SELECT ... FOR UPDATE` semantics.
3. **Trigger & Index Enforcement:** Partial indexes, foreign-key cascade timing, and check constraints frequently pass on H2 but crash on PostgreSQL.

**Production Standard:** Use **Testcontainers** with the exact production database image (e.g., `postgres:16-alpine`).

### Transactional Rollbacks in Tests: The False-Positive Trap
Spring's `@DataJpaTest` annotates test methods with `@Transactional`, automatically rolling back the database transaction at the end of each test. While convenient for data cleanup, it introduces subtle failure modes:
- **Hibernate Lazy Loading False Positives:** The persistence context remains open for the entire test method. Calling `order.getItems().size()` outside a service method succeeds in the test, but throws `LazyInitializationException` in production where the controller runs outside the service transaction.
- **Unflushed Constraints:** Database foreign key and unique constraints are only validated when the transaction is flushed. If code does not call `entityManager.flush()`, constraint violations will be completely missed by the test.

---

## Section 12 — Testing Redis, Caching, and Invalidation Behavior

Testing cache-aside and invalidation patterns requires verifying real expiration, negative caching, and atomic updates.

```
       [Test Request]
              |
              v
       +--------------+  Cache Miss
       | Cache-Aside  | ------------> [Fetch from Real DB]
       | Engine       |                      |
       +--------------+ <--------------------+
              | Writes Key + TTL
              v
       [Redis Container]
              |
              v
       [Assert Key Exists & TTL in Bounds]
              |
       [Trigger Entity Update]
              |
              v
       [Assert Key Evicted / Invalidated in Redis]
```

### The Danger of Mocking Redis
Mocking Redis with a `Map<String, Object>` masks critical failure modes:
1. **Serialization Bugs:** Missing default constructors, non-serializable fields, or date format mismatches in Jackson/Kryo fail in real Redis but pass in mocks.
2. **Connection Leakage:** Unreleased Jedis or Lettuce connections under high concurrency are never detected without a real Redis instance.
3. **Distributed Locks:** Redis Redlock and Lua scripts rely on atomic server execution that in-memory maps cannot replicate.

---

## Section 13 — Testing APIs: Status Codes, Validation, Authorization, Error Contracts, and Compatibility

API testing must verify RFC compliance, authorization boundaries, and structured error payloads (RFC 7807 Problem Details).

### HTTP Status Code Contract Matrix
| Scenario | Correct Status Code | Incorrect Common Mistake | Architectural Rationale |
| :--- | :---: | :---: | :--- |
| Missing Bearer Token | `401 Unauthorized` | `403 Forbidden` | Caller identity is unknown; authentication required. |
| Authenticated User lacks Role | `403 Forbidden` | `401 Unauthorized` | Identity is verified, but permissions are insufficient. |
| Syntax Error (Malformed JSON) | `400 Bad Request` | `500 Server Error` | Client transmitted unparseable syntax. |
| Business Constraint Violation | `422 Unprocessable` | `400 Bad Request` | Syntax valid, but domain rule violated (e.g. negative price). |
| Resource Missing | `404 Not Found` | `200 OK` (empty list) | Targeted resource identifier does not exist. |
| Optimistic Locking Conflict | `409 Conflict` | `500 Server Error` | Client update collided with concurrent modification. |

---

## Section 14 — Contract Testing and Compatibility Across Service Boundaries

### Consumer-Driven Contract Testing (CDCT)
In distributed microservices, end-to-end integration environments are notoriously unstable and expensive. Consumer-Driven Contract Testing decouples teams by verifying agreements at compile time.

```
+--------------------+                     +--------------------+
| Order Service      |                     | Payment Service    |
| (Consumer)         |                     | (Provider)         |
+--------------------+                     +--------------------+
         |                                           ^
         | 1. Generates Contract                     | 3. Replays Contract
         |    (Pact JSON: Expected                   |    against real Provider
         |    Request & Response)                    |    Controller in CI
         v                                           |
+----------------------------------------------------+--+
|                    Pact Broker                        |
| 2. Stores Versioned Contract Agreement Specification  |
+-------------------------------------------------------+
```

If the Payment Service changes `customerId` to `clientId`, the Provider build immediately fails in CI *before* deploying to production, without needing both services running in a shared staging environment.

---

## Section 15 — Testing Asynchronous Consumers, Retries, Idempotency, and Duplicate Delivery

As detailed in [Module 08 — Asynchronous Processing & Messaging](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_08_async_processing_messaging.md), message brokers provide **at-least-once** delivery. Tests must prove that receiving identical messages multiple times produces strictly idempotent outcomes.

### The Concurrent Duplicate Delivery Test Pattern
```java
@Test
void shouldProcessDuplicateMessagesIdempotently() throws Exception {
    IdempotentConsumer consumer = new IdempotentConsumer(ledgerRepository);
    PaymentEvent event = new PaymentEvent("evt_uuid_999", "acc_101", 50.0);

    // Concurrently dispatch duplicate events across multiple threads
    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch latch = new CountDownLatch(threads);

    for (int i = 0; i < threads; i++) {
        executor.submit(() -> {
            try {
                consumer.handle(event);
            } finally {
                latch.countDown();
            }
        });
    }

    assertTrue(latch.await(5, TimeUnit.SECONDS));

    // Assert side effect occurred EXACTLY ONCE
    assertEquals(1, ledgerRepository.findTransactionsForEvent("evt_uuid_999").size());
    assertEquals(50.0, ledgerRepository.getBalance("acc_101"));
}
```

---

## Section 16 — Concurrency Tests, Race Conditions, Deadlocks, and Nondeterminism

Testing multi-threaded code requires deterministic synchronization primitives. **Never use `Thread.sleep()`** to wait for concurrent tasks: sleeps waste CI time when too long, and cause intermittent test failures when system load slows thread scheduling.

### Synchronizing Race Conditions with `CyclicBarrier`
To test whether a class is susceptible to race conditions, all threads must hit the critical section simultaneously. A `CyclicBarrier` acts as a starting gate, holding all threads until every worker is ready, then releasing them in the exact same clock tick.

```java
CyclicBarrier startingGate = new CyclicBarrier(threadCount);
for (int i = 0; i < threadCount; i++) {
    executor.submit(() -> {
        startingGate.await(); // All threads release simultaneously
        idempotencyCache.computeIfAbsent(key, expensiveCalculation);
    });
}
```

### Deadlock Detection Harness
To detect deadlocks in test suites, use Java's `ThreadMXBean`:
```java
ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
long[] deadlockedThreads = threadBean.findDeadlockedThreads();
if (deadlockedThreads != null && deadlockedThreads.length > 0) {
    throw new AssertionError("Deadlock detected across threads: " + Arrays.toString(deadlockedThreads));
}
```

---

## Section 17 — Property-Based Testing, Invariants, Boundary Testing, and Fuzzing Concepts

### Beyond Example-Based Testing
Traditional example-based tests check specific inputs: `assertEquals(4, add(2, 2))`. Property-based testing (originating in QuickCheck and implemented in `jqwik` for Java) generates hundreds of randomized inputs to verify **system invariants**.

### Core Invariant Properties
1. **Round-Trip Serialization:** $\text{deserialize}(\text{serialize}(x)) \equiv x$
2. **Idempotency:** $f(f(x)) \equiv f(x)$
3. **Symmetry / Inversion:** $\text{decrypt}(\text{encrypt}(m, k), k) \equiv m$
4. **Conservation:** Sorting an array must preserve array length and element frequencies.

### Counterexample Shrinking
When a property-based test fails on a complex randomized input (e.g., an array of 5,000 numbers), the test framework automatically executes a **shrinking algorithm**. It strips elements, divides integers, and reduces the failing case to the minimal counterexample (e.g., `[-1]`), revealing the exact edge case immediately.

---

## Section 18 — Regression Testing and Reproducing Reported Defects

### The Scientific Bug-Fixing Protocol
When a production bug is reported, jumping straight into code modifications is a major anti-pattern. Follow this 4-step protocol:

1. **Synthesize Minimal Reproduction:** Isolate the inputs, state, and environmental triggers that caused the production defect.
2. **Write a Failing Regression Test:** Codify the reproduction in an automated unit or integration test. Verify that the test **fails with the exact reported symptoms** (`RED`).
3. **Implement the Minimal Root-Cause Fix:** Modify the production code until the regression test passes (`GREEN`).
4. **Verify No Collateral Damage:** Run the full module test suite to guarantee that existing invariants remain unviolated.

---

## Section 19 — Debugging Fundamentals: Observe, Hypothesize, Isolate, Verify

Debugging is an application of the scientific method to computer systems:

```
    +-------------------------------------------------------+
    | 1. OBSERVE                                            |
    | Gather objective telemetry, stack traces, and inputs. |
    +-------------------------------------------------------+
                               |
                               v
    +-------------------------------------------------------+
    | 2. HYPOTHESIZE                                        |
    | Formulate testable causal explanations.               |
    +-------------------------------------------------------+
                               |
                               v
    +-------------------------------------------------------+
    | 3. ISOLATE                                            |
    | Narrow scope via binary search and variable removal.  |
    +-------------------------------------------------------+
                               |
                               v
    +-------------------------------------------------------+
    | 4. VERIFY                                             |
    | Validate hypothesis with reproducer and fix.          |
    +-------------------------------------------------------+
```

### Avoiding "Shotgun Debugging"
Shotgun debugging is the practice of randomly altering code, tweaking annotations, or adding speculative null-checks until an error message changes. This introduces technical debt, masks root causes, and frequently spawns secondary regressions.

---

## Section 20 — A Repeatable Debugging Workflow for Unfamiliar Codebases

When dropped into a massive, undocumented production service:
1. **Trace from the Inbound Boundary:** Locate the entry point (e.g., REST controller, Kafka listener, or RPC handler).
2. **Follow the Data Flow:** Trace the Request DTO through domain services to the persistence repository.
3. **Inspect Active Telemetry:** Correlate the failing request via `trace_id` in logs and distributed traces (see [Module 11 — Observability & Diagnostics](file:///Users/mohammedaafaqahmed/Desktop/BeyondTheApi/modules/module_11_observability.md)).
4. **Identify Mutating State Transitions:** Pinpoint where object state transitions from valid to invalid.

---

## Section 21 — Reading Stack Traces, Exception Chains, Logs, Metrics, and Traces

### Anatomy of an Exception Chain
In Java, exceptions are frequently wrapped across architectural layers. The outermost exception is almost never the root cause:

```
org.springframework.dao.DataIntegrityViolationException: could not execute statement; ...
    at org.hibernate.exception.internal.SQLStateConverter.convert(...)
Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement
    at org.hibernate.dialect.PostgreSQLDialect$2.convert(...)
Caused by: org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "uq_users_email"
  Detail: Key (email)=(alice@example.com) already exists.  <-- TRUE ROOT CAUSE
```

### Correlating Telemetry Across Pillars
When diagnosing an incident:
1. Find the latency spike or error rate jump on **Metrics** dashboards.
2. Click the exemplar or find the highest latency trace in **Distributed Tracing**.
3. Copy the `trace_id` and query **Structured Logs** to inspect the exact exception stack trace and parameter payload.

---

## Section 22 — Breakpoints, Stepping, Watches, Conditional Breakpoints, and Debugger Pitfalls

### Advanced Debugger Techniques
- **Conditional Breakpoints:** Break execution only when a specific expression evaluates to true (e.g., `order.getAmount() > 1000000 && user.isTrial()`). Saves hours of stepping through thousands of normal loop iterations.
- **Watch Expressions:** Dynamically monitor computed values or complex object graphs across stepping frames.
- **Exception Breakpoints:** Configure the IDE debugger to halt immediately when a specific exception class (e.g., `NullPointerException`) is instantiated, regardless of where in the call stack it occurs.

### Pitfalls of Debugging Concurrent Code
Stepping through multi-threaded applications in an interactive debugger alters thread scheduling timing (the **Heisenbug effect**). Threads halted at breakpoints hold locks, trigger health check timeouts, and cause connection pool starvation that does not occur in normal production execution. For concurrency issues, use deterministic concurrency harnesses and high-frequency logging over interactive debuggers.

---

## Section 23 — Heap, CPU, Thread, and Allocation Profiling Fundamentals

### Profiling vs. Monitoring
- **Monitoring:** Answers *what* is happening at a macro level (e.g., CPU is at 98%, Heap usage is 3.8 GB).
- **Profiling:** Answers *where in the code* the resources are being consumed (e.g., `Regex.matcher()` inside `Sanitizer.java:42` accounts for 74% of CPU clock cycles).

### Profiling Tools & Mechanics
1. **CPU Profiling (Async-Profiler / JProfiler):**
   - *Sampling vs Instrumentation:* Instrumentation adds bytecode hooks to every method entry/exit, creating massive performance distortion. Sampling inspects call stacks at regular intervals (e.g., every 10ms) using OS signals (e.g., `perf_events`) without safe-point bias.
   - *Flame Graphs:* Horizontal axis represents total execution time fraction; vertical axis represents call stack depth. Wide plateaus indicate CPU bottlenecks.
2. **Memory Profiling & Heap Dumps (`jcmd`, `jmap`, Memory Analyzer Tool - MAT):**
   - *Shallow vs. Retained Heap:* Shallow heap is the memory consumed by the object itself (primitive fields and references). Retained heap is the memory that would be freed if this object were garbage collected (including all transitively referenced child objects).
   - *Dominator Trees:* Identifies the single root object holding millions of unreachable or leaked sub-objects.

---

## Section 24 — Memory Leaks, Resource Leaks, Connection-Pool Exhaustion, and Thread Starvation

### Common Backend Leak Patterns

1. **ThreadLocal Memory Leaks in Thread Pools:**
   In web containers (Tomcat, Netty), worker threads are reused across thousands of HTTP requests. If a request handler stores user context in a `ThreadLocal` and fails to call `threadLocal.remove()` in a `finally` block, the data remains permanently attached to the worker thread, leaking memory and leaking security context into future requests!

2. **Unclosed JDBC Connections & Streams:**
   Failing to use `try-with-resources` on database connections, HTTP response streams, or file handles leaks OS file descriptors. Eventually, the operating system rejects new connections with `IOException: Too many open files`.

3. **Connection-Pool Exhaustion (Little's Law):**
   As demonstrated mathematically in our verification suite, the number of active database connections required is governed by Little's Law:
   $$L = \lambda \times W$$
   If average query latency $W$ jumps from $15\text{ ms}$ to $150\text{ ms}$ due to a missing table index, the required connections jump ten-fold from $4.5$ to $45$. If the pool is capped at $20$, incoming threads block, queue up, and crash with `ConnectionPoolTimeoutException`.

---

## Section 25 — Performance Testing: Load, Stress, Spike, Soak, and Capacity Testing

### The 4 Major Performance Testing Archetypes

```
Traffic
  ^
  |        Load Test                  Stress Test                 Spike Test                 Soak Test
  |     +--------------+           +--------------+--+         +---+                      +-----------------------+
  |     | Normal Peak  |           | Push to Break   |         |   | Sudden Surge         | Steady Sub-Peak Load  |
  |     | 100% Target  |           | 250% Failure    |         |   | (Flash Sale)         | 24-48 Hours           |
  |-----+--------------+-----   ---+--------------+--+--   ----+   +------------+--   ----+-----------------------+--
  +------------------------->   -------------------------> -------------------------> ------------------------------>
            Time                          Time                        Time                        Time
```

1. **Load Testing:** Evaluates system behavior under expected normal and peak traffic to verify SLO latency and throughput compliance.
2. **Stress Testing:** Pushes throughput past the breaking point to determine the saturation threshold and observe graceful degradation (e.g., circuit breaker tripping vs. complete cluster crash).
3. **Spike Testing:** Delivers instant $10\times$ traffic surges to test autoscaling responsiveness and queue buffering.
4. **Soak / Endurance Testing:** Runs steady sub-peak traffic over 24–72 hours to detect slow memory leaks, disk buffer exhaustion, and thread pool creep.

---

## Section 26 — Benchmarking Pitfalls, Warm-Up, Variance, and Statistically Responsible Comparisons

### The Microbenchmarking Hazards
Writing a naive microbenchmark using `System.currentTimeMillis()` produces useless, misleading numbers due to JVM optimizations:
1. **JIT Warm-Up & Tiered Compilation:** The JVM interprets bytecode initially (C1 compiler) before optimizing frequently executed paths into native machine code (C2 compiler). Benchmarking un-warmed code measures bytecode interpreter speed, not production throughput.
2. **Dead-Code Elimination:** If the benchmark calculates a result that is never returned or printed, the C2 optimizer eliminates the computation entirely: your loop takes 0 nanoseconds!
3. **Loop Unrolling & Constant Folding:** The compiler replaces mathematical expressions with static constants at compile time.

**Solution:** Always use **JMH (Java Microbenchmark Harness)**, which utilizes Blackholes (`bh.consume(result)`) to prevent dead-code elimination and manages warm-up iterations automatically.

---

## Section 27 — Flaky Tests, Time-Dependent Behavior, Randomness, Shared State, and External Dependencies

### The Flaky Test Compound Probability Catastrophe
A test is flaky if it can pass or fail for the same version of code without any modifications. While developers often dismiss a 0.5% flake rate as "just a blip," the mathematics of compounding probability ruin engineering velocity:

$$P(\text{Suite Failure}) = 1 - (1 - p)^N$$

If a test suite contains $N = 200$ tests, each with an individual flake rate of $p = 0.5\%$:
$$P(\text{Suite Failure}) = 1 - (0.995)^{200} = 1 - 0.3669 = 63.3\%$$

More than **6 out of every 10 CI runs will fail** purely due to flakiness! Engineers stop trusting CI, ignore real test failures, and waste hundreds of hours re-triggering builds.

### Common Causes & Fixes
- **System Clocks:** Calling `Instant.now()` breaks across midnight or leap seconds. **Fix:** Inject `java.time.Clock` and use `Clock.fixed()`.
- **Unordered Collections:** Iterating over `HashSet` produces non-deterministic order across different JVM runs. **Fix:** Assert element containment (`containsExactlyInAnyOrder`) rather than strict list equality.
- **Asynchronous Races:** Relying on `Thread.sleep(500)`. **Fix:** Use polling wait libraries like `Awaitility.await().untilAsserted(...)`.

---

## Section 28 — Test Isolation, Parallel Execution, Test Containers, and Disposable Environments

### Disposable Containers with Testcontainers
Modern integration tests spin up disposable Docker containers per test run, ensuring complete isolation:

```java
@Testcontainers
class OrderRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldPersistOrderSuccessfully() {
        // Runs against a real, isolated PostgreSQL 16 container!
    }
}
```

### Singleton Container Pattern
Spinning up a new Docker container for every test class adds minutes to the test suite. The **Singleton Container Pattern** starts the container once for the entire JVM test process and reuses it across test classes, cleaning database tables between runs using fast truncated SQL scripts.

---

## Section 29 — CI Test Stages, Quality Gates, Test Selection, and Failure Triage

A modern continuous integration pipeline implements progressive quality gates:

```
[Commit Push]
     |
     v
[Fast Quality Gate (< 2 mins)]
- Static Analysis (Checkstyle, SpotBugs)
- Pure Unit Tests (Fast in-memory execution)
     |
     v (Passed)
[Integration Gate (< 8 mins)]
- Testcontainers (PostgreSQL, Redis, Kafka)
- Contract Verification (Pact)
     |
     v (Passed)
[Deploy to Ephemeral Preview Environment]
- Smoke Tests (< 3 mins)
     |
     v (Passed)
[Merge Allowed]
```

### Test Impact Analysis (TIA)
In large monorepos, running 50,000 tests on every commit is impractical. Test Impact Analysis inspects the git diff and dependency graph to execute only the tests that cover the modified classes and their direct consumers.

---

## Section 30 — Coverage Metrics: What Line/Branch Coverage Reveals and What It Cannot Prove

### Line Coverage vs. Branch Coverage vs. Condition Coverage
- **Line Coverage:** Measures whether a line of code was touched during execution.
- **Branch Coverage:** Measures whether all decision branches (both `true` and `false` paths of an `if` statement) were evaluated.
- **Condition Coverage:** Measures whether every individual boolean sub-expression in a compound condition was evaluated independently.

```java
// Line coverage touches this in 1 test case (a=true, b=true, c=true)
// But branch and condition coverage requires testing all 8 permutations!
if ((a || b) && c) {
    executeCriticalTransfer();
}
```

### The 100% Coverage Illusion
A suite can achieve 100% line coverage while asserting **nothing of value**:
```java
// Achieves 100% line coverage on calculator, but verifies ZERO invariants!
@Test
void testCalculator() {
    calculator.calculateComplexTaxes(100000, 4);
    // No assertions! If tax logic produces negative numbers or crashes, test still passes!
}
```

---

## Section 31 — Mutation Testing and the Limits of Coverage-Driven Optimization

### How Mutation Testing Works
Mutation testing (via tools like **Pitest** in Java) injects intentional bugs (mutants) into the compiled bytecode and runs the test suite against each mutant:

```
                  +--------------------------------+
                  |  Original Compiled Bytecode    |
                  +--------------------------------+
                                  |
               +------------------+------------------+
               | Mutator: Invert Conditionals        |
               v                                     v
       +------------------+                  +------------------+
       | Mutant 1: `>`    |                  | Mutant 2: `*`    |
       | changed to `>=`  |                  | changed to `/`   |
       +------------------+                  +------------------+
               |                                     |
         [Run Tests]                           [Run Tests]
               |                                     |
               v                                     v
       [Tests Fail: RED]                     [Tests Pass: GREEN]
       MUTANT KILLED (Good!)                 MUTANT SURVIVED (Defect in test assertions!)
```

### The Mutation Score
$$\text{Mutation Score} = \frac{\text{Mutants Killed}}{\text{Total Mutants Generated}} \times 100\%$$

As verified in our test suite, a weak test suite can have 100% line coverage but a **25% mutation score**. Mutation testing evaluates the **assertion quality** of the test suite rather than merely measuring lines touched.

---

## Section 32 — Production Debugging, Safe Mitigation, Rollback, and Incident Follow-Up

When a high-severity incident strikes production:

### Mitigation Precedes Root-Cause Analysis
1. **Mitigate User Impact First:** Do not spend 45 minutes analyzing stack traces while users experience a 90% error rate.
   - If a new deployment occurred within the last hour: **Roll back immediately**.
   - If traffic overwhelmed a downstream service: **Enable circuit breakers or rate limiting**.
   - If a bad feature flag was enabled: **Disable the feature flag**.
2. **Preserve Diagnostic Artifacts Before Restarting:**
   - Take a thread dump: `jcmd <pid> Thread.print > /tmp/threaddump.txt`
   - Capture a heap dump if memory is leaking: `jcmd <pid> GC.heap_dump /tmp/heap.hprof`
   - Archive current log buffers.
3. **Conduct Blameless Postmortems:**
   - Focus on systemic vulnerabilities, missing circuit breakers, and alert gaps rather than blaming human operator error.

---

## Section 33 — Testing Security Boundaries and Failure Paths

Security testing cannot be deferred to annual penetration audits; automated test suites must verify authorization boundaries on every build.

### Automated Security Assertions
1. **Broken Object Level Authorization (BOLA / IDOR):**
   Test that User A cannot read or mutate User B's resources by substituting UUIDs in API paths:
   ```java
   @Test
   void shouldRejectAccessToOtherUsersInvoice() throws Exception {
       mockMvc.perform(get("/api/v1/invoices/inv_user_b")
               .header("Authorization", "Bearer token_for_user_a"))
               .andExpect(status().isForbidden());
   }
   ```
2. **SQL Injection Resistance:**
   Assert that malicious strings (e.g., `' OR '1'='1`) are treated as literal values and not executed.
3. **Payload Size Limits & ReDoS:**
   Verify that oversized payloads ($> 10\text{ MB}$) are rejected with `413 Payload Too Large` and regular expressions do not hang the CPU.

---

## Section 34 — Common Testing and Debugging Interview Questions

### Q1: "Why would you choose a Fake over a Mock when testing a repository?"
**Answer:**
A Mock verifies specific method interactions and parameter values, coupling the test directly to the implementation details of the caller. If the caller refactors from `findById()` to `findAllById()`, the mock test breaks even if the behavior is identical. A Fake provides a working in-memory implementation (e.g., using a `ConcurrentHashMap`). It allows state-based assertions, supports realistic query flows, and allows fearless refactoring without brittle test updates.

### Q2: "We have a test suite with 95% line coverage, yet critical bugs frequently slip into production. Why?"
**Answer:**
Line coverage measures only which lines were touched by an execution frame; it does not measure whether assertions verified the correct state or output. Tests with weak assertions (or no assertions) achieve 100% line coverage. Furthermore, line coverage cannot identify missing logic (unhandled edge cases, omitted database constraints, concurrent race conditions, or serialization errors). Mutation testing and property-based boundary testing are necessary to evaluate true test efficacy.

### Q3: "How do you systematically debug an intermittent CPU spike in production that occurs twice a week?"
**Answer:**
1. Check metrics to determine if CPU correlates with traffic spikes, garbage collection pauses, or specific background worker crons.
2. Inspect distributed traces during the spike to find the slowest operations.
3. Attach a continuous low-overhead sampling profiler (e.g., `async-profiler` or eBPF continuous profiling) to capture flame graphs during the event.
4. If thread contention is suspected, capture consecutive thread dumps 5 seconds apart to identify threads blocked in `WAITING` or `BLOCKED` states on synchronized locks.

---

## Section 35 — Scenario-Based Debugging Exercises with Worked Reasoning

### Scenario 1: Connection Pool Exhaustion under Moderate Load
**Symptoms:** Under normal 200 RPS traffic, the service runs smoothly. At 300 RPS, requests suddenly hang for 30 seconds, and the log displays `HikariCP: ConnectionTimeoutException: Connection is not available, request timed out after 30000ms`.
**Investigation:**
1. Check database server metrics: CPU is only 15%, memory is healthy, and the DB has 100 free connections. The bottleneck is the client-side connection pool.
2. Inspect active threads via thread dump: 30 threads are blocked waiting for a connection from the pool.
3. Trace the service code: An endpoint retrieves an unpaginated database stream and iterates over it while making a slow, synchronous HTTP call inside the loop:
   ```java
   // DEFECT: Connection held across slow external network call!
   @Transactional
   public void processOrders() {
       orderRepo.streamAll().forEach(order -> {
           externalPaymentClient.verify(order); // Takes 500ms over network!
       });
   }
   ```
**Resolution:** Remove the `@Transactional` wrapper from the network loop. Fetch data in paginated batches, close the database connection immediately, and then execute the external network calls concurrently.

---

### Scenario 2: Double-Spending Race Condition in Wallet Balance Transfer
**Symptoms:** An account with a balance of $100 transfers $100 to two different recipients simultaneously. Both transfers succeed, leaving the account at -$100.
**Investigation:**
1. Inspect code:
   ```java
   // DEFECT: Read-modify-write race condition
   Wallet w = walletRepo.findById(id);
   if (w.getBalance() >= amount) {
       w.setBalance(w.getBalance() - amount);
       walletRepo.save(w);
   }
   ```
2. Two concurrent threads read the balance as $100 simultaneously. Both pass the check and both deduct $100.
**Resolution:** Use pessimistic locking (`SELECT ... FOR UPDATE`) or atomic database operations:
```sql
UPDATE wallets SET balance = balance - :amount WHERE id = :id AND balance >= :amount;
```
Verify the update count: if 0, throw `InsufficientFundsException`. Codify with a deterministic `CyclicBarrier` concurrency test.

---

### Scenario 3: Intermittent Test Failure in CI Due to UTC vs. Local Timezone
**Symptoms:** An integration test checking order filtering by date passes locally on developer laptops in California (PST) but fails on the CI server in Ireland (UTC) when run after 4:00 PM local time.
**Investigation:**
The test code uses `LocalDate.now()` to query orders created today:
```java
LocalDate today = LocalDate.now(); // Uses system default timezone!
```
At 5:00 PM PST, the UTC date is already tomorrow! The test queries for orders created on tomorrow's date, finds zero results, and fails.
**Resolution:** Always use explicit UTC clocks: `LocalDate.now(ZoneOffset.UTC)` or inject a fixed test `Clock`.

---

## Section 36 — Module 12 Cheatsheet: Debugging & Testing

### Test Doubles Quick Reference
| Double | Gerard Meszaros Definition | When to Use |
| :--- | :--- | :--- |
| **Dummy** | Passed to fill method signatures; never called | Mandatory constructor parameters irrelevant to scenario |
| **Stub** | Returns static, canned responses; no assertion tracking | Providing fixed inputs for queries or calculators |
| **Fake** | Working in-memory implementation with shortcuts | In-memory repositories (`ConcurrentHashMap`), local caches |
| **Mock** | Configured with expectations; verifies invocations | Verifying outbound commands (emails sent, payment charged) |
| **Spy** | Wraps real instance; intercepts and records calls | Auditing real behavior while delegating execution |

### Diagnostic CLI Commands
```bash
# Capture thread dump from Java process
jcmd <PID> Thread.print > threaddump.txt

# Capture heap dump without stopping application
jcmd <PID> GC.heap_dump /tmp/heapdump.hprof

# Profile CPU using async-profiler (50Hz sampling for 30s)
./asprof -d 30 -f /tmp/flamegraph.html <PID>

# Inspect open file descriptors / sockets of a process
lsof -p <PID> | wc -l

# Network packet inspection on local port 8080
tcpdump -i lo0 -nn -s0 -A 'tcp port 8080'
```

### Key Engineering Rules of Thumb
1. **Never use `Thread.sleep()` in tests.** Use `CyclicBarrier`, `CountDownLatch`, or polling waiters (`Awaitility`).
2. **High line coverage is not high confidence.** Evaluate mutation scores to measure assertion quality.
3. **Never test against H2 if production runs PostgreSQL.** Use Testcontainers.
4. **Mitigate production incidents before analyzing root causes.** Roll back first, diagnose second.
5. **A passing test suite does not prove correctness.** It only proves absence of codified failure.
