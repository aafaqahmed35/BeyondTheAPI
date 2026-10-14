# Module 03: Java and Object-Oriented Programming

## 1. Java Execution Architecture & JVM Mechanics

Java achieves cross-platform portability by compiling source code to platform-independent bytecode (`.class`), executed by the Java Virtual Machine (JVM).

### 1.1 ClassLoader Hierarchy & Delegation Model
Class loading follows a strict **Parent-First Delegation Protocol**:
1. **Bootstrap ClassLoader:** Written in C/C++. Loads core JDK runtime classes (`java.lang.*`, `java.util.*`) from runtime modules (`lib/modules`).
2. **Platform (Extension) ClassLoader:** Loads platform-specific extensions.
3. **Application (System) ClassLoader:** Loads application classes located on the `CLASSPATH` or `--module-path`.
4. **Custom ClassLoaders:** Used by web containers (Tomcat, OSGi) to implement child-first loading for servlet isolation or runtime bytecode instrumentation.

**Verification & Linking Phases:**
- **Loading:** Reads `.class` binary byte stream into Metaspace.
- **Linking:**
  - *Verification:* Enforces bytecode constraints (no stack underflow/overflow, type safety, valid instruction parameters).
  - *Preparation:* Allocates static fields in Metaspace and initializes them to zero/null defaults.
  - *Resolution:* Transforms symbolic references in runtime constant pool into direct physical memory addresses.
- **Initialization:** Executes class initializers (`<clinit>`), evaluating static field declarations and `static {}` blocks.

### 1.2 Pass-by-Value Mechanics & Memory Layout
Java is strictly **Pass-by-Value**:
- **Primitives (`int`, `double`):** The actual bit pattern value is copied onto the callee's stack frame.
- **Object References:** The reference (a 32-bit or 64-bit heap pointer) is copied by value. The callee receives a copy of the pointer pointing to the identical heap object. Reassigning the parameter inside the callee modifies only the local pointer copy, while mutating object fields affects the shared heap state.

### 1.3 Deterministic Class Initialization Sequence
When instantiating a derived class `Child extends Parent`, the JVM executes a strict 6-step initialization order:
1. Parent static fields and `static {}` blocks (evaluated once upon class load).
2. Child static fields and `static {}` blocks.
3. Parent instance fields and instance initializer `{}` blocks (evaluated per instantiation).
4. Parent constructor body.
5. Child instance fields and instance initializer `{}` blocks.
6. Child constructor body.

---

## 2. Object-Oriented Design & Dynamic Dispatch

### 2.1 Virtual Method Tables & Dynamic Dispatch
- **Virtual Method Invocation (`invokevirtual`):** Resolved at runtime using a per-class **Virtual Method Table (vtable)** containing direct pointers to executable code. If class `Child` overrides `method()`, its vtable slot points to `Child::method`.
- **Interface Invocation (`invokeinterface`):** Resolved via an **itable**, dynamically resolving methods across unrelated class hierarchies.
- **Static & Special Invocations:** `invokestatic` (static methods) and `invokespecial` (private methods, constructors, `super`) are resolved statically at compile time with zero vtable lookup cost.

### 2.2 SOLID Principles in Production Java
- **Single Responsibility Principle (SRP):** A class must have one reason to change. Separate domain entities from persistence, transport DTOs, and serialization logic.
- **Open/Closed Principle (OCP):** Open for extension, closed for modification. Implement strategy interfaces rather than mutating switch blocks.
- **Liskov Substitution Principle (LSP):** Subtypes must be substitutable for base types without breaking client invariants. Never throw `UnsupportedOperationException` from overridden base methods.
- **Interface Segregation Principle (ISP):** Clients should not be forced to depend on methods they do not use. Split fat interfaces into cohesive role interfaces.
- **Dependency Inversion Principle (DIP):** High-level modules should depend on abstractions (interfaces), not low-level concrete classes. Enforced via Constructor Injection.

---

## 3. Object Contracts, Hashing & Modern Java Type System

### 3.1 The `equals()` and `hashCode()` Contract
Overriding `equals()` strictly requires overriding `hashCode()`:
- **Reflexive:** `x.equals(x) == true`.
- **Symmetric:** `x.equals(y) == y.equals(x)`.
- **Transitive:** `x.equals(y) && y.equals(z) ==> x.equals(z)`.
- **Consistent:** Returns identical boolean across invocations unless state mutated.
- **Null Comparison:** `x.equals(null) == false`.
- **Hash Code Invariant:** $\text{If } a.\text{equals}(b) \implies a.\text{hashCode}() == b.\text{hashCode}()$. (The converse is false: hash collisions are permitted).

```java
// Production Reference Implementation using java.util.Objects
public final class AccountId {
    private final String tenantId;
    private final long sequenceNumber;

    public AccountId(String tenantId, long sequenceNumber) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountId that)) return false; // Pattern matching
        return sequenceNumber == that.sequenceNumber &&
               tenantId.equals(that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, sequenceNumber);
    }
}
```

### 3.2 Generics Type Erasure & The PECS Rule
Java generics are enforced at compile time and **erased** to their bounds (or `Object`) in bytecode to maintain binary backwards compatibility with pre-Java 5 runtimes.
- **Bridge Methods:** Synthetic methods generated by javac to preserve polymorphism during type erasure.
- **PECS Rule (Producer Extends, Consumer Super):**
  - **Producer Extends (`? extends T`):** Use when reading elements from a collection. Covariant. Elements guaranteed to be at least type `T`. Writing is prohibited (except `null`).
  - **Consumer Super (`? super T`):** Use when writing elements into a collection. Contravariant. Safe to insert instances of `T` or its subclasses. Reading returns untyped `Object`.

### 3.3 Modern Java Features (Java 17–21 LTS)
1. **Records:** Immutable transparent data carriers with compiler-generated accessors, `equals()`, `hashCode()`, and `toString()`:
   ```java
   public record UserProfile(UUID id, String email, Instant createdAt) {
       public UserProfile { // Compact constructor for validation
           Objects.requireNonNull(email, "Email must not be null");
       }
   }
   ```
2. **Sealed Classes & Interfaces:** Restricts inheritance to explicitly permitted subclasses:
   ```java
   public sealed interface DomainEvent permits OrderPlaced, OrderCancelled {}
   public record OrderPlaced(String orderId) implements DomainEvent {}
   public record OrderCancelled(String orderId, String reason) implements DomainEvent {}
   ```
3. **Pattern Matching for `switch`:** Exhaustiveness checking over sealed hierarchies without `default` fallbacks:
   ```java
   String status = switch (event) {
       case OrderPlaced p -> "Order Created: " + p.orderId();
       case OrderCancelled c -> "Cancelled: " + c.reason();
   };
   ```

---

## 4. Collections Framework Architecture

### 4.1 Collection Taxonomy & Performance
- **`List`:** Ordered sequential collections.
  - `ArrayList`: Backed by dynamic array. Fast $O(1)$ indexed reads, slow $O(n)$ middle inserts/deletes.
  - `LinkedList`: Doubly linked list. $O(1)$ node insertion given reference; atrocious cache locality and memory overhead ($24$ bytes per node).
- **`Set`:** Unordered unique elements.
  - `HashSet`: Backed by `HashMap`. $O(1)$ average add/contains.
  - `TreeSet`: Backed by Red-Black tree (`TreeMap`). $O(\log n)$ navigable sorted order.
- **`Map`:** Key-value mappings.
  - `LinkedHashMap`: Maintains doubly linked list across entries for insertion-order or access-order (LRU caches).
- **Fail-Fast vs. Fail-Safe Iterators:**
  - *Fail-Fast (`ArrayList`, `HashMap`):* Maintains internal `modCount`. If modified concurrently during iteration, throws `ConcurrentModificationException`.
  - *Fail-Safe / Weakly Consistent (`CopyOnWriteArrayList`, `ConcurrentHashMap`):* Iterates over a snapshot or concurrent segment without throwing exceptions.

---

## 5. JVM Memory Layout, Garbage Collection & JIT

### 5.1 JVM Runtime Data Areas
```
+------------------------------------------------------------------------+
|                          JVM Process Memory                            |
|  +------------------------------------------------------------------+  |
|  | Metaspace (Native RAM: Classes, Method Bytecode, Constant Pool)  |  |
|  +------------------------------------------------------------------+  |
|  | Heap Memory (Shared across all threads)                          |  |
|  |  +-------------------------------------+-----------------------+ |  |
|  |  | Young Generation (Eden, S0, S1)     | Old / Tenured Gen     | |  |
|  |  +-------------------------------------+-----------------------+ |  |
|  +------------------------------------------------------------------+  |
|  | Per-Thread Memory: Thread Stack (Frames, Locals, Operand Stack)  |  |
+------------------------------------------------------------------------+
```
- **Compressed OOPs (Ordinary Object Pointers):** On 64-bit JVMs with heaps $< 32$ GB, object references are compressed to 32-bit offsets by exploiting 8-byte object alignment, saving 30–40% memory. Setting heap $> 32$ GB disables compression, increasing memory pressure.

### 5.2 Garbage Collectors: G1 GC vs. ZGC
- **Weak Generational Hypothesis:** The empirical reality that the vast majority of allocated objects die shortly after allocation.
- **G1 GC (Garbage-First):** Divides heap into 2048 equal-sized regions (1MB to 32MB). Dynamically assigns regions to Eden, Survivor, or Old. Prioritizes regions with the most garbage to meet a user-defined pause target (`-XX:MaxGCPauseMillis=200`). Performs concurrent marking and parallel evacuation compaction.
- **ZGC (Z Garbage Collector):** Ultra-low latency generational GC. Performs nearly all phases concurrently (marking, relocation, reference processing). Uses **Colored Pointers** (reference bits storing GC metadata) and **Load Barriers** to intercept thread reads and fix pointers on the fly. Guarantees sub-millisecond maximum pauses on multi-terabyte heaps.

### 5.3 Just-In-Time (JIT) Tiered Compilation
The JVM executes bytecode via interpretation before compiling hot methods to native machine instructions:
1. **Tier 0:** Bytecode Interpreter. Collects invocation and backedge loop branch profiling counters.
2. **Tier 1–3 (C1 Compiler):** Fast compilation with client optimizations.
3. **Tier 4 (C2 / Graal):** Highly aggressive compiler. Performs method inlining, loop unrolling, and **Escape Analysis** (if an object does not escape the allocating method, heap allocation is replaced with stack scalar replacement).

---

## 6. Failure Modes, Memory Leaks & Production Anti-Patterns

| Failure Pattern | Symptom / Error | Root Cause | Preventive Invariant |
| :--- | :--- | :--- | :--- |
| **ThreadLocal Leak** | `OutOfMemoryError: Metaspace` or Heap leak across redeployments. | `ThreadLocal` variables set by worker threads in application pools never cleared via `remove()`. | Always wrap in `try { ... } finally { threadLocal.remove(); }`. |
| **Static Collection Bloat** | Gradual heap exhaustion, frequent full GCs. | Adding objects to `static Map` or `List` without eviction or bounded size. | Use bounded caches with LRU eviction (Caffeine) or WeakReferences. |
| **Missing Resource Cleanup** | `SocketException: Too many open files` | Failure to close database connections or files during exceptional paths. | Use `try-with-resources` (`AutoCloseable`) exclusively. |
| **Broken Equals/HashCode** | Missing entries in `HashMap` despite present key. | Key fields mutated after insertion, changing the object's `hashCode()`. | Use strictly immutable types (`record`, `final`) as map keys. |
| **StackOverflowError** | Immediate JVM thread crash. | Unbounded recursion or circular object graph serialization without cycles check. | Convert recursion to iterative stack-based loops or bound depth. |
---

## 7. High-Bar Java Interview Scenarios & Language Synthesis

### Scenario 1: String Immutability and Security Invariants
- **Interview Question:** "Why is `String` strictly immutable and `final` in Java, and what catastrophic security vulnerabilities would arise if it were mutable?"
- **Deep Technical Answer:**
  1. **Security & ClassLoading:** Network socket addresses, file system paths, and database URLs are passed as Strings. If Strings were mutable, an attacker could pass a validated safe path `"/app/safe"`, pass security checks, and concurrently mutate the string to `"/etc/shadow"` (**TOCTOU race**).
  2. **Thread Safety:** Immutability guarantees strings can be freely shared across concurrent threads with zero synchronization overhead.
  3. **String Constant Pool:** Multiple variables point to the identical heap literal. Mutability would allow one component to mutate a string literal, silently corrupting unrelated application modules.
  4. **HashCode Caching:** `String` caches its `hash` computation upon first access. Immutability guarantees the cached hash code remains permanently consistent in `HashMap` buckets.

### Scenario 2: `ConcurrentHashMap` Evolution (Java 7 vs. Java 8+)
- **Interview Question:** "Explain the architectural evolution of `ConcurrentHashMap` between Java 7 and Java 8."
- **Deep Technical Answer:**
  1. **Java 7 (Segment Locking):** Divided the map into a fixed array of 16 `Segment` objects, each extending `ReentrantLock`. Concurrency was strictly limited to 16 concurrent writers. Reading required volatile reads.
  2. **Java 8+ (Lock-Free CAS + Synchronized Buckets):**
     - Eliminates Segments entirely. Employs a flat array of hash bucket nodes (`Node<K, V>`).
     - **CAS Insertion:** If a bucket is empty, a new node is inserted using a single lock-free Compare-And-Swap (`Unsafe.compareAndSwapObject`).
     - **Granular Bucket Locks:** If a bucket is occupied, synchronization locks **only the head node** of that specific bucket (`synchronized(f)`). Concurrency scales linearly with table size.
     - Automatically treeifies buckets exceeding 8 nodes into Red-Black trees.

### Scenario 3: JIT Escape Analysis & Scalar Replacement
- **Interview Question:** "How does the JVM JIT compiler eliminate heap allocations, and what are the prerequisites?"
- **Deep Technical Answer:**
  1. During C2 compilation, the JIT executes **Escape Analysis** to determine if an allocated object escapes the allocating method's scope (e.g., returned, stored in field, or passed to non-inlined method).
  2. If the object is proven **NoEscape**, the JIT applies **Scalar Replacement**: it breaks the object into its individual primitive fields and maps them directly to CPU hardware registers or stack locations.
  3. **Zero Heap Allocation:** No memory is allocated on the heap; zero garbage collection overhead is incurred.
  4. *Prerequisite:* Aggressive **Method Inlining** must occur first so the compiler can analyze the entire execution graph.
---

## 8. Deep-Dive Bytecode Bridge Methods & Collections Master Matrix

### 8.1 Generics Type Erasure & Synthetic Bridge Methods
When a class implements a parameterized interface with a specific type argument, type erasure replaces the generic type parameter with `Object` in bytecode. To preserve polymorphism and dynamic dispatch, `javac` automatically generates a **synthetic bridge method**:
```java
// Source Code
public class NodeId implements Comparable<NodeId> {
    @Override
    public int compareTo(NodeId other) { return ... }
}

// Bytecode generated by javac (Decompiled):
public class NodeId implements Comparable {
    public int compareTo(NodeId other) { return ... }
    
    // SYNTHETIC BRIDGE METHOD generated to satisfy Comparable.compareTo(Object)
    public synthetic bridge int compareTo(Object other) {
        return this.compareTo((NodeId) other); // Casts and delegates
    }
}
```

### 8.2 Java Collections Framework Master Complexity Matrix

| Collection Class | Internal Data Structure | `get()` | `add()` | `remove()` | `contains()` | Thread-Safe? |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **`ArrayList`** | Contiguous dynamic array | $O(1)$ | $O(1)^*$ | $O(n)$ | $O(n)$ | No (Fail-Fast) |
| **`LinkedList`** | Doubly linked node list | $O(n)$ | $O(1)$ | $O(1)$ | $O(n)$ | No (Fail-Fast) |
| **`ArrayDeque`** | Circular array buffer | $O(1)$ | $O(1)^*$ | $O(1)$ | $O(n)$ | No (Fail-Fast) |
| **`PriorityQueue`** | Array-backed binary heap | $O(1)$ | $O(\log n)$ | $O(\log n)$ | $O(n)$ | No (Fail-Fast) |
| **`HashSet`** | Backed by `HashMap` | N/A | $O(1)$ | $O(1)$ | $O(1)$ | No (Fail-Fast) |
| **`TreeSet`** | Backed by Red-Black `TreeMap` | N/A | $O(\log n)$ | $O(\log n)$ | $O(\log n)$ | No (Fail-Fast) |
| **`HashMap`** | Hash bucket array + RB Trees | $O(1)$ | $O(1)$ | $O(1)$ | $O(1)$ | No (Fail-Fast) |
| **`LinkedHashMap`** | Hash buckets + Doubly linked list| $O(1)$ | $O(1)$ | $O(1)$ | $O(1)$ | No (Insertion/Access order)|
| **`ConcurrentHashMap`**| CAS + Head-node synchronized | $O(1)$ | $O(1)$ | $O(1)$ | $O(1)$ | Yes (Thread-Safe Lock-Free)|
| **`CopyOnWriteArrayList`**| Copy array on mutation | $O(1)$ | $O(n)$ | $O(n)$ | $O(n)$ | Yes (Fast reads, slow writes)|
