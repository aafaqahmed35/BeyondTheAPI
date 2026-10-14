# Module 12: Debugging and Testing

## 1. The Testing Pyramid & Test Double Taxonomy

Testing reduces business risk and enables safe, rapid continuous deployment.

```
                  / \
                 /   \
                / E2E \       End-to-End Tests (Slow, Fragile, High Cost)
               /-------\
              / Contract\     Contract Tests (Pact - API boundary verification)
             /-----------\
            / Integration \   Integration Tests (Testcontainers, Real DB/Kafka)
           /---------------\
          /      Unit       \ Unit Tests (Fast, In-Memory, Deterministic)
         +-------------------+
```

### 1.1 Gerard Meszaros 5 Test Double Taxonomy
1. **Dummy:** Objects passed around but never actually used (e.g., satisfying non-null method signatures).
2. **Stub:** Returns hard-coded canned answers to method calls during the test.
3. **Spy:** Wraps a real object to record invocation history (method names, argument counts).
4. **Mock:** Pre-programmed with expectations of exact method calls, arguments, and counts. Fails the test if expectations are violated.
5. **Fake:** A fully working business implementation taking shortcuts unsuitable for production (e.g., an in-memory repository using `ConcurrentHashMap`).

*The Over-Mocking Anti-Pattern:* Mocking internal implementation details (e.g., mocking private methods or DAOs inside unit tests) tightly couples tests to code structure. Any internal refactoring breaks tests even when external behavior is 100% correct. **Mock only at architectural boundaries** (external payment gateways, external SMTP services).

---

## 2. Integration Testing with Testcontainers

### 2.1 Why In-Memory Databases (H2) Lie
Testing against H2 when production uses PostgreSQL or MySQL introduces catastrophic false confidence:
- H2 lacks PostgreSQL concurrency locking semantics (`FOR UPDATE SKIP LOCKED`).
- H2 does not support native PostgreSQL data types (`JSONB`, `UUID[]`, `INET`).
- Dialect discrepancies hide syntax errors in native SQL queries until production deployment.

### 2.2 The Singleton Container Pattern
Testcontainers spins up real, ephemeral Docker containers (PostgreSQL, Kafka, Redis) for tests:
```java
public abstract class AbstractIntegrationTest {
    // Singleton pattern: Starts ONCE per test execution JVM, reused across all test classes
    static final PostgreSQLContainer<?> POSTGRES = 
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

---

## 3. Concurrency & Property-Based Testing

### 3.1 Concurrency Rendezvous Harness
To test race conditions deterministically, force concurrent threads to execute at the identical millisecond using a `CyclicBarrier`:
```java
@Test
void testConcurrentBalanceDeduction() throws Exception {
    int threadCount = 10;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            barrier.await(); // Synchronizes all threads to start at identical instant!
            if (accountService.withdraw(accountId, 100)) {
                successCount.incrementAndGet();
            }
            return null;
        });
    }
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    assertThat(successCount.get()).isEqualTo(1); // Only 1 withdrawal should succeed!
}
```

### 3.2 Property-Based Testing (Hypothesis / jqwik)
Instead of testing arbitrary hard-coded example values, property-based testing verifies universal **mathematical invariants** across thousands of automatically generated random inputs:
- Generates extreme edge cases (empty strings, `Integer.MAX_VALUE`, surrogate pairs, negative numbers).
- **Counterexample Shrinking:** When an invariant fails, the framework automatically simplifies the input to the smallest reproducible counterexample.

---

## 4. Scientific Debugging Protocol & Stack Traces

```
+-------------------------------------------------------+
| 1. Observe: Reliably reproduce defect with minimal test|
+-------------------------------------------------------+
                           |
                           v
+-------------------------------------------------------+
| 2. Hypothesize: Formulate causal theory of root cause |
+-------------------------------------------------------+
                           |
                           v
+-------------------------------------------------------+
| 3. Isolate: Test hypothesis using bisect or profiler   |
+-------------------------------------------------------+
                           |
                           v
+-------------------------------------------------------+
| 4. Verify: Implement fix; prove test passes; check fix|
+-------------------------------------------------------+
```

### 4.1 Exception Chain Analysis
In Java exception stack traces:
- Always read from the bottom: the **"Caused by:"** line identifies the true physical root cause (e.g., `SocketTimeoutException`).
- **Suppressed Exceptions:** Exceptions thrown inside `try-with-resources` auto-closing blocks while an active exception is propagating are appended as `Suppressed:`, preventing masked root causes.

---

## 5. Flaky Tests & Mutation Testing

### 5.1 Flaky Test Mathematics
If a test suite contains $N = 100$ integration tests, each with a seemingly negligible $p = 0.5\%$ probability of flaking due to timing or race conditions:
$$P(\text{Entire Suite Passes}) = (1 - p)^N = (1 - 0.005)^{100} = (0.995)^{100} \approx 60.5\%$$
**$39.5\%$ of CI builds will randomly fail!** Zero tolerance for flaky tests: quarantine immediately.

### 5.2 Mutation Testing (Pitest)
Code coverage (line or branch) only proves code was *executed*, not that assertions actually verified correctness.
- **Mutation Testing:** Injects synthetic bugs into compiled bytecode (reversing conditional `if (x < y)` $\to$ `if (x >= y)`, mutating return values to `null`).
- Runs test suite:
  - If a test fails: Mutant is **Killed** (test suite verified behavior).
  - If tests pass: Mutant **Survived** (useless tests with missing assertions!).
  $$\text{Mutation Score} = \frac{\text{Killed Mutants}}{\text{Total Mutants}} \times 100\%$$

---

## 6. Failure Modes & Testing Triage

| Failure Mode | Root Cause | Impact | Preventive Architecture |
| :--- | :--- | :--- | :--- |
| **H2 Dialect Drift** | H2 in-memory DB masks invalid SQL or missing locks. | Code passes CI; crashes instantly on production PostgreSQL. | Replace H2 with real database integration tests via Testcontainers. |
| **Flaky Time Tests** | Tests use `Instant.now()` asserting fixed timestamps. | Tests fail intermittently around midnight or daylight savings. | Inject deterministic mock `Clock` (`Clock.fixed()`). |
| **Shared State Bleed** | Test 1 mutates static variable or DB row; Test 2 assumes empty state. | Tests pass in isolation; fail when run in parallel or suite order. | Enforce test isolation via `@DirtiesContext` or unique test schemas. |
| **False-Confidence Mocks**| Entire service mocked with Stubs returning static DTOs. | 100% test coverage; complete integration failure in production. | Limit mocking to external trust boundaries; test real persistence. |
---

## 7. High-Bar Testing & Debugging Interview Scenarios

### Scenario 1: Diagnosing Complex Production Memory Leaks
- **Interview Question:** "A Java microservice memory graph exhibits a classic 'sawtooth' pattern with an upward trend, eventually terminating with `java.lang.OutOfMemoryError: Java heap space`. Walk through your investigation."
- **Deep Technical Answer:**
  1. **Acquire Artifacts:** Capture an automated heap dump upon crash via JVM flag:
     ```bash
     -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/dumps/
     ```
  2. **Dominator Tree Analysis (Eclipse MAT):** Open heap dump in Eclipse Memory Analyzer. Inspect the **Dominator Tree** to identify the single object retaining the largest percentage of heap memory.
  3. **Inspect Path to GC Roots:** Exclude soft, weak, and phantom references to find the strong reference chain keeping the leaky objects alive.
  4. **Common Root Causes:** Unbounded static maps, `ThreadLocal` variables in pooled threads, unclosed database result sets, or registered event listeners never deregistered.

### Scenario 2: Eliminating Non-Deterministic Flaky Tests in CI
- **Interview Question:** "Your team's CI pipeline fails $25\%$ of the time on main branch builds with no code changes. How do you systematically eliminate flaky tests?"
- **Deep Technical Answer:**
  1. **Quarantine:** Immediately tag and move failing tests to a quarantined suite to restore confidence in the main deployment pipeline.
  2. **Root Cause Categorization:**
     - **Time Dependencies:** Tests calling `Instant.now()` or using `Thread.sleep()`. Fix: Inject mock `Clock` and use Awaitility condition polling.
     - **Concurrency Races:** Multi-threaded tests executing without deterministic synchronization barriers. Fix: Use `CyclicBarrier` and `CountDownLatch`.
     - **State Pollution:** Tests depending on shared database state or static singletons. Fix: Isolate database schemas per worker and reset state in `@AfterEach`.
  3. **Defensive Verification:** Run the refactored test 1,000 times in parallel in CI using `git bisect run` or test repeat runners before un-quarantining.

### Scenario 3: Why Mutation Testing Outperforms Code Coverage
- **Interview Question:** "A pull request boasts $100\%$ line coverage, but introduces a critical production bug. Explain how this happens and how mutation testing detects it."
- **Deep Technical Answer:**
  1. **Code Coverage Illusion:** Line coverage measures code *execution*, not code *verification*. A test can execute 100% of method lines without asserting any return values or state changes (e.g., executing a method with zero `assert` statements).
  2. **Mutation Testing (Pitest):** Injects deliberate mutations into compiled bytecode:
     - Changes boundary condition `if (balance < amount)` to `if (balance <= amount)`.
     - Modifies arithmetic `total + tax` to `total - tax`.
     - Replaces return values with `null`.
  3. If all tests pass against the mutated bytecode, the mutant **Survived**, exposing missing assertions. If tests fail, the mutant was **Killed**, proving assertions verify behavior.
---

## 8. Consumer-Driven Contract Testing & Chaos Engineering

### 8.1 Consumer-Driven Contracts (Pact)
End-to-end integration environments across 50 microservices are notoriously fragile, expensive, and slow. **Consumer-Driven Contract Testing** decouples service verification:
1. **Consumer Test:** The consumer service defines expectations of provider behavior in a mock test, generating a contract JSON file (**Pact file**).
2. **Pact Broker:** The contract is published to a central Pact Broker.
3. **Provider Verification:** The provider service pulls the contract in CI, replays the recorded requests against its real controllers, and verifies responses match expectations without deploying the consumer.
4. If a provider introduces a breaking change (e.g., renaming a JSON field), provider CI fails immediately before merging to main.

### 8.2 Chaos Engineering Principles (Chaos Mesh / Gremlin)
Chaos Engineering tests distributed system resilience by proactively injecting synthetic faults into staging and production:
- **Hypothesis Formulation:** Define steady-state metric (e.g., "99.9% of checkout requests succeed with $< 200$ms latency").
- **Blast Radius Containment:** Inject faults into a single Availability Zone or $5\%$ of canary traffic.
- **Fault Injection Types:**
  - *Network Latency / Packet Loss:* Simulates cross-region network degradation (verifies circuit breaker timeouts).
  - *Pod Kill:* Randomly terminates Kubernetes pods (verifies graceful zero-downtime rolling updates and liveness probes).
  - *Clock Skew:* Injects NTP drift (verifies token expiration and lease safety).
