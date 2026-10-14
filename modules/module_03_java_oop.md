# Module 03 — Java and Object-Oriented Programming: Language Semantics, Type Systems, Runtime Behavior, and Design Principles

> **Curriculum Status:** Core Language & Runtime Prerequisite for Spring Boot, Database Engineering, Distributed Systems, and Technical Interview Defense.  
> **Target Audience:** B.Tech Fresher / Associate Software Engineer preparing for FAANG/Tier-1 software engineering interviews, enterprise backend development, and rigorous code reviews.  
> **Source Platform & Language Standards:** Java 21 LTS (Oracle OpenJDK / Eclipse Temurin), Java Language Specification (JLS SE 21), Java Virtual Machine Specification (JVMS SE 21).

---

## Learning Objectives

By the conclusion of this module, an engineer will be able to:
1. **Master Java's Execution Architecture:** Trace the exact path of Java code from `.java` source through `javac` bytecode compilation, ClassLoader linking, JVM bytecode verification, and JIT tiered compilation (`C1` / `C2`) to native machine code.
2. **Deconstruct Memory & Value Semantics:** Defend why Java is strictly pass-by-value in all circumstances, distinguish reference values from heap objects, reason about numeric promotion, and diagnose IEEE 754 floating-point traps.
3. **Control Object Lifecycle & Initialization:** Trace static vs. instance initialization order, constructor chaining (`super()` / `this()`), access modifier scoping, and encapsulation invariant preservation.
4. **Apply OOP Principles with Architectural Discernment:** Implement abstraction, encapsulation, inheritance, and polymorphism with backend examples while identifying when inheritance harms substitutability.
5. **Differentiate Method Resolution Mechanics:** Distinguish compile-time method overloading (`invokevirtual` static parameter binding) from runtime method overriding (vtable dynamic dispatch), static method hiding, and field hiding.
6. **Engineer Robust Contracts with Interfaces & Abstract Classes:** Balance interface default/static/private methods with abstract classes, resolve multiple interface inheritance conflicts, and leverage interfaces for Dependency Inversion and testability.
7. **Apply SOLID Principles Pragmatically:** Refactor fragile inheritance hierarchies into composable architectures, spot realistic Liskov Substitution Principle (LSP) violations, and design decoupled class boundaries.
8. **Preserve Identity, Equality, and Hashing Invariants:** Implement robust, reflexive, symmetric, transitive, and consistent `equals()` and `hashCode()` contracts, avoiding silent hash-table data corruption caused by mutable keys.
9. **Architect Immutable Domain Models:** Construct immutable classes with defensive copying on constructor ingestion and accessor reads, utilize Java 16+ `record` types, and separate `final` reference immutability from object state immutability.
10. **Harness Generics and PECS:** Navigate type erasure, design type-safe generic containers, apply Producer Extends Consumer Super (`? extends T` vs. `? super T`) wildcards, and explain why generic arrays are prohibited.
11. **Design Fault-Tolerant Error Handling:** Structure checked vs. unchecked exception hierarchies, leverage `try-with-resources` (`AutoCloseable`) with suppressed exception tracking, and preserve root-cause diagnostic contexts.
12. **Select Collections Semantically:** Evaluate `ArrayList`, `LinkedList`, `ArrayDeque`, `HashMap`, and `TreeMap` trade-offs, explain `modCount` fail-fast iterator mechanics, and contrast unmodifiable wrappers with Java 9+ immutable collections.
13. **Write Idiomatic Functional Code:** Build stream pipelines, analyze intermediate operation laziness, contrast `map()` with `flatMap()`, evaluate parallel stream thread-pool contention, and recognize when loops surpass streams.
14. **Leverage Modern Java Features:** Use Java 16+ Records, Java 17+ Sealed Classes and Interfaces, Pattern Matching for `switch`, and type-safe Enums.
15. **Reason Mechanistically About JVM Garbage Collection:** Trace root sets (GC Roots), object reachability, reference strengths, memory leak mechanics, and tiered JIT optimization.

---

## Table of Contents

- [Section 1 — Java’s Execution Model and Architecture](#section-1--javas-execution-model-and-architecture)
- [Section 2 — Primitive Types, References, and Value Semantics](#section-2--primitive-types-references-and-value-semantics)
- [Section 3 — Classes, Objects, and Initialization Mechanics](#section-3--classes-objects-and-initialization-mechanics)
- [Section 4 — The Four OOP Principles in Practice](#section-4--the-four-oop-principles-in-practice)
- [Section 5 — Overloading, Overriding, and Dynamic Dispatch](#section-5--overloading-overriding-and-dynamic-dispatch)
- [Section 6 — Abstract Classes and Interfaces](#section-6--abstract-classes-and-interfaces)
- [Section 7 — Inheritance, Composition, and SOLID Design](#section-7--inheritance-composition-and-solid-design)
- [Section 8 — Object Methods, Equality, and Hashing Contracts](#section-8--object-methods-equality-and-hashing-contracts)
- [Section 9 — Strings, Immutability, and Defensive Design](#section-9--strings-immutability-and-defensive-design)
- [Section 10 — Generics and the Type System](#section-10--generics-and-the-type-system)
- [Section 11 — Exceptions and Fault-Tolerant Error Handling](#section-11--exceptions-and-fault-tolerant-error-handling)
- [Section 12 — Collections Framework and Core API Semantics](#section-12--collections-framework-and-core-api-semantics)
- [Section 13 — Lambdas, Functional Interfaces, and Streams](#section-13--lambdas-functional-interfaces-and-streams)
- [Section 14 — Enums, Records, Annotations, and Modern Java](#section-14--enums-records-annotations-and-modern-java)
- [Section 15 — JVM Memory Architecture, Garbage Collection, and JIT](#section-15--jvm-memory-architecture-garbage-collection-and-jit)
- [Section 16 — High-Value Interview Reasoning and Question Bank](#section-16--high-value-interview-reasoning-and-question-bank)
- [Section 17 — Java and OOP Cheatsheet: Interview Revision Reference](#section-17--java-and-oop-cheatsheet-interview-revision-reference)

---

## Section 1 — Java’s Execution Model and Architecture

### 1.1 The Lifecycle of a Java Program

Java is neither a purely compiled language (like C or Rust) nor a purely interpreted language (like standard Python or Ruby). It uses a hybrid **compile-once, run-anywhere** architecture centered on intermediate **Bytecode**.

```
[Developer Workspace]
  OrderService.java (Human-readable Source Code)
         |
         | javac (Java Compiler - Frontend Static Analysis & Type Checking)
         v
  OrderService.class (Platform-Independent JVM Bytecode)
================================================================================
[Target Host Environment: Linux / macOS / Windows]
         |
         v
+------------------------------------------------------------------------------+
|                        Java Virtual Machine (JVM)                            |
|                                                                              |
|  1. ClassLoader Subsystem:                                                   |
|     - Loading: Reads .class binary bytes from disk/network.                  |
|     - Linking: Verification (safety checks) -> Preparation -> Resolution.   |
|     - Initialization: Executes static variable assignments & static blocks.  |
|                                                                              |
|  2. Execution Engine:                                                        |
|     - Bytecode Interpreter: Fast startup; executes bytecode instructions.    |
|     - JIT Compiler (Tiered Compilation):                                     |
|         * C1 (Client Compiler): Quickly compiles hot methods with counters.  |
|         * C2 (Server Compiler): Aggressive optimizations (inlining, escape    |
|           analysis, loop unrolling, devirtualization) -> Native Machine Code. |
|     - Garbage Collector (GC): ZGC / G1GC manages heap object lifecycles.     |
+------------------------------------------------------------------------------+
         |
         v
[Physical Hardware Execution]
  x86-64 / ARM64 CPU Instructions (Ring 3 User Mode Execution)
```

### 1.2 JDK versus JRE versus JVM

Understanding the runtime boundary is essential for diagnosing deployment environments and build pipelines:
- **Java Virtual Machine (JVM):** The abstract computing machine specification. It defines instruction set opcodes (`iload`, `invokevirtual`, `monitorenter`), register sets, stack architectures, and memory regions (Heap, Metaspace, Thread Stacks). A concrete implementation (such as HotSpot or GraalVM) runs on a specific operating system and CPU architecture.
- **Java Runtime Environment (JRE):** The execution bundle containing the JVM plus the compiled Java Class Libraries (`java.base`, `java.sql`, `java.net`). *Historical Note:* Prior to Java 9, JRE was distributed as a standalone download. Starting with Java 9 and solidified in Java 11/17/21 LTS, the standalone JRE was deprecated in favor of modular runtime images generated via `jlink`.
- **Java Development Kit (JDK):** The complete software development bundle. It includes the execution runtime (JVM + libraries) plus development and diagnostic tooling: compiler (`javac`), archive packager (`jar`), documentation generator (`javadoc`), debugger (`jdb`), and diagnostic tools (`jcmd`, `jstat`, `jmap`, `jstack`).

### 1.3 Platform Independence and Its Concrete Boundaries

Java's famous axiom **"Write Once, Run Anywhere" (WORA)** is achieved because the compiler targets the abstract JVM instruction set rather than native machine assembly. The same `OrderService.class` file compiled on an Apple Silicon macOS laptop runs without modification on an x86-64 Linux cloud server.

**Where Platform Independence Ends:**
1. **The JVM Implementation Itself:** The JVM executable (`java`) is strictly platform-dependent. An x86-64 ELF Linux binary of HotSpot cannot execute on an ARM64 macOS kernel.
2. **Native Interoperability (JNI / FFM API):** Any code utilizing the Java Native Interface (JNI) or the modern Foreign Function & Memory API (Java 22 preview, Java 21 incubator) to invoke C/C++ dynamic libraries (`.so`, `.dylib`, `.dll`) binds the application to specific OS ABIs.
3. **OS and Filesystem Semantics:** File path separators (`/` vs `\`), case-sensitivity (`ext4` on Linux is case-sensitive; `APFS` on macOS and `NTFS` on Windows default to case-insensitive), line terminators (`\n` vs `\r\n`), and file locking behaviors reflect the underlying operating system.
4. **Hardware Memory Models:** While the Java Memory Model (JMM) provides uniform multi-threaded visibility guarantees across all architectures, memory ordering instructions map differently on strongly ordered hardware (x86 TSO) vs. weakly ordered hardware (ARM64).

### 1.4 Specification Guarantees versus HotSpot Implementation Choices

A senior engineer must distinguish between what the **Java Virtual Machine Specification (JVMS)** contractually mandates versus what a particular JVM implementation (such as Oracle/OpenJdk HotSpot) chooses to do:
- **JVMS Guarantees:** 
  - Every primitive type has an exact, fixed bit-width and two's-complement range across every platform.
  - Bytecode execution must adhere strictly to operand stack type safety and verification rules.
  - Classes must be initialized lazily upon first active use.
  - The Java Memory Model must guarantee happens-before consistency.
- **Implementation Choices (Not Specification Guarantees):**
  - **Garbage Collection Algorithm:** HotSpot defaults to G1GC on modern hardware, but can use ZGC, ParallelGC, or SerialGC. The JVMS does not mandate how, when, or if garbage collection collects memory, nor does it guarantee `System.gc()` runs.
  - **JIT Compilation Strategy:** HotSpot uses Tiered Compilation (Interpreter $\to$ C1 $\to$ C2), whereas GraalVM uses an optimizing compiler written in Java, and Android's ART uses Ahead-Of-Time (AOT) combined with profile-guided interpretation.
  - **Object Memory Layout:** The JVMS does not define the internal byte alignment of objects, the presence of an object header, or reference pointer compression. HotSpot implements a 2-word header (Mark Word + Klass Word) and Compressed OOPs (`-XX:+UseCompressedOops`).

---

## Section 2 — Primitive Types, References, and Value Semantics

### 2.1 Primitive Types, Ranges, and Numeric Promotion

Java provides eight built-in **primitive types**. Unlike object references, primitive variables directly store raw binary values inside their allocated memory location (on the thread execution stack for local variables, or embedded inside the heap object's layout for fields).

```
+-----------+---------+-------------------------+--------------------------------------------------------+
| Type      | Bits    | Default Value           | Range / Precision                                      |
+-----------+---------+-------------------------+--------------------------------------------------------+
| boolean   | 1 (JVM) | false                   | true or false (Evaluated as int/byte opcodes in JVM)   |
| byte      | 8       | (byte) 0                | -128 to 127 (-2^7 to 2^7 - 1)                          |
| short     | 16      | (short) 0               | -32,768 to 32,767 (-2^15 to 2^15 - 1)                  |
| char      | 16      | '\u0000' (0)            | 0 to 65,535 (Unsigned 16-bit UTF-16 code units)        |
| int       | 32      | 0                       | -2,147,483,648 to 2,147,483,647 (-2^31 to 2^31 - 1)   |
| long      | 64      | 0L                      | -9,223,372,036,854,775,808 to 2^63 - 1                 |
| float     | 32      | 0.0f                    | IEEE 754 single-precision (~7 decimal digits)          |
| double    | 64      | 0.0d                    | IEEE 754 double-precision (~15-17 decimal digits)      |
+-----------+---------+-------------------------+--------------------------------------------------------+
```

#### Binary Numeric Promotion Rules (JLS §5.6.2)
When binary arithmetic operators (`+`, `-`, `*`, `/`, `%`) operate on numeric values:
1. If any operand is `double`, the other is promoted to `double`.
2. Otherwise, if any operand is `float`, the other is promoted to `float`.
3. Otherwise, if any operand is `long`, the other is promoted to `long`.
4. **Otherwise, both operands are promoted to `int`!**

*The Byte Promotion Trap:*
```java
byte a = 10;
byte b = 20;
// byte c = a + b; // COMPILE ERROR: a + b promotes both operands to int!
byte c = (byte) (a + b); // Explicit narrowing cast required
```

### 2.2 Floating-Point Arithmetic and Precision Limitations

Both `float` and `double` adhere to the **IEEE 754 standard** for binary floating-point arithmetic. Because numbers are represented as fractional powers of two ($\frac{1}{2}, \frac{1}{4}, \frac{1}{8}\dots$), decimal fractions such as $0.1$ ($\frac{1}{10}$) cannot be represented exactly in finite binary:

```java
double a = 0.1;
double b = 0.2;
System.out.println(a + b); // Prints: 0.30000000000000004
System.out.println(a + b == 0.3); // Prints: false!
```

> **BACKEND ENGINEERING MANDATE:** Never use `float` or `double` for monetary amounts, financial ledgers, or exact accounting calculations. Always use `java.math.BigDecimal` initialized with **String constructors** (`new BigDecimal("0.1")`), or store currency values as integer minor units (e.g., storing USD cents or INR paise as a 64-bit `long`).

### 2.3 Reference Variables versus Heap Objects

A critical mental model in Java is the absolute distinction between a **Reference Variable** and an **Object**:
- An **Object** is a concrete block of memory allocated dynamically on the JVM **Heap**. It contains instance fields, metadata headers, and runtime type information.
- A **Reference Variable** (e.g., `Order order`) is merely an address pointer (a 32-bit or 64-bit value) stored in a stack frame or another object's fields that points to the location of the object on the heap.

```
Stack Frame (Thread 1)                          JVM Heap Memory
+-----------------------+                       +---------------------------------------+
| orderRef: [0x4A0088]  | --------------------> | Object: Order (Identity: 0x4A0088)    |
+-----------------------+                       | - id: 1001L                           |
| aliasRef: [0x4A0088]  | --------------------> | - status: "PENDING"                   |
+-----------------------+                       +---------------------------------------+
| nullRef:  [null / 0x0]|                       
+-----------------------+                       +---------------------------------------+
| primitiveCount: 42    |                       | Object: Order (Identity: 0x4B0010)    |
+-----------------------+                       | - id: 1002L                           |
                                                +---------------------------------------+
```

When you write `Order aliasRef = orderRef;`, you do **not** copy the `Order` object. You copy the pointer bits (`0x4A0088`). Both variables now point to the exact same heap memory block (**Aliasing**).

### 2.4 The Universal Truth: Java is Strictly Pass-by-Value

> **CRITICAL INTERVIEW TRUTH:** Java is **100% strictly pass-by-value**. Java does **not** have pass-by-reference. Ever.

When an argument is passed into a Java method, the JVM makes an identical copy of the caller's variable bits and binds that copy to the method's parameter variable:
- When a primitive (`int x`) is passed, a copy of the numeric value is passed. Modifying the parameter inside the method changes only the local copy.
- When an object reference (`MutableBox box`) is passed, a **copy of the reference address bits** is passed!

```java
public class PassByValueProof {
    public static void attemptReassign(MutableBox b) {
        // 'b' is a local copy of the caller's reference pointer.
        // Reassigning 'b' points the local copy to a completely new object on the heap.
        b = new MutableBox(999); 
    }

    public static void mutateState(MutableBox b) {
        // Mutating the object through the copy of the pointer alters the shared heap object!
        b.value += 10;
    }

    public static void main(String[] args) {
        MutableBox original = new MutableBox(50);
        
        attemptReassign(original);
        System.out.println(original.value); // Prints: 50! Original reference was unaffected!

        mutateState(original);
        System.out.println(original.value); // Prints: 60! The shared object was mutated!
    }
}
```

*The Common Misconception:* Candidates observe `mutateState()` changing `original.value` and mistakenly say: *"See? Objects are passed by reference."* The correct explanation is: *The reference itself was passed by value (copied). Because both the caller's reference and the parameter's reference contain identical address bits, they both dereference the exact same heap object.* If Java had true pass-by-reference, `attemptReassign()` would have replaced the caller's `original` variable with the `new MutableBox(999)`.

### 2.5 Identity (`==`) versus Equality (`equals()`)

- **`==` Operator (Reference Identity):** Tests whether two variables contain the exact same raw bits. For primitives, it tests numeric equality (`5 == 5`). For references, it tests whether both variables point to the **exact same memory address on the heap** (`refA == refB` is true if and only if `refA` and `refB` address the same object identity).
- **`equals()` Method (Logical Equivalence):** A virtual instance method defined on `java.lang.Object`. Its default implementation in `Object` is simply `this == obj`. However, domain classes (`String`, `Integer`, `BigDecimal`, custom entities) override `equals()` to compare **state content** rather than memory addresses.

---

## Section 3 — Classes, Objects, and Initialization Mechanics

### 3.1 Class versus Object and Member Scopes

- **Class:** The compile-time blueprint, metadata description, and type contract defining fields, methods, constructors, and access policies. It is loaded into the JVM's Metaspace as a `java.lang.Class` instance.
- **Object:** A concrete runtime instance allocated on the Heap possessing its own private copy of all instance fields.
- **Instance Members vs. Static Members:**
  - *Instance Fields & Methods:* Associated with a specific object instance. An instance method receives an implicit `this` reference as its first hidden parameter.
  - *Static Fields & Methods:* Associated with the Class itself, shared globally across all instances. Static methods have **no `this` pointer**; they cannot access instance fields or instance methods without an explicit object reference.

### 3.2 Constructor Invocations and Chaining

A constructor initializes a newly allocated heap memory block into a valid state. 

**Constructor Rules:**
1. If a class declares zero constructors, the Java compiler automatically inserts a default no-argument constructor: `public ClassName() { super(); }`.
2. If a class declares *any* constructor (even a parameterized one), the compiler **does not** generate a default no-argument constructor.
3. Every constructor must, as its very first statement, invoke either another constructor in the same class (`this(...)`) or a constructor in its direct superclass (`super(...)`). If omitted, the compiler automatically inserts `super();`.

### 3.3 The Deterministic Initialization Order

When a subclass is instantiated for the first time, initialization proceeds through a strict, deterministic sequence:

```
Step 1: Class Loading & Static Initialization (Executed ONCE per ClassLoader)
  1.1 Superclass static variables initialized & Superclass static blocks executed (top to bottom).
  1.2 Subclass static variables initialized & Subclass static blocks executed (top to bottom).

Step 2: Instance Creation (Executed on EVERY 'new' invocation)
  2.1 Subclass constructor is called, immediately invoking super() as its first statement.
  2.2 Superclass instance variables initialized & Superclass instance initializer blocks executed.
  2.3 Superclass constructor body executes.
  2.4 Subclass instance variables initialized & Subclass instance initializer blocks executed.
  2.5 Subclass constructor body executes.
```

#### Traceable Verification Code
```java
class SuperBase {
    static { System.out.println("1. SuperBase Static"); }
    { System.out.println("3. SuperBase Instance Block"); }
    public SuperBase() { System.out.println("4. SuperBase Constructor"); }
}

class SubDerived extends SuperBase {
    static { System.out.println("2. SubDerived Static"); }
    { System.out.println("5. SubDerived Instance Block"); }
    public SubDerived() {
        super();
        System.out.println("6. SubDerived Constructor");
    }
}
// Instantiating: new SubDerived(); prints exactly: 1 -> 2 -> 3 -> 4 -> 5 -> 6
```

### 3.4 Access Modifiers and Encapsulation

Java provides four access levels to enforce modular information hiding:
- **`private`:** Accessible only within the declaring top-level class (and its nested inner classes).
- **Package-Private (Default / No Modifier):** Accessible by any class residing in the exact same package.
- **`protected`:** Accessible by any class within the same package, plus subclasses in different packages (via inheritance access).
- **`public`:** Accessible everywhere across all packages (subject to Java module system exports).

**Preserving Invariants:** Encapsulation is not merely about writing boilerplate getters and setters. True encapsulation means making fields `private` and exposing business methods that validate state transitions, ensuring that an object's internal invariant (e.g., `balance >= 0`, `startDate <= endDate`) is never violated.

---

## Section 4 — The Four OOP Principles in Practice

Object-Oriented Programming is an architectural paradigm designed to manage complexity in large systems through modularity, clear contracts, and decoupled dependencies.

```
+-----------------------------------------------------------------------------+
|                         THE FOUR OOP PILLARS                                |
+------------------+----------------------------------------------------------+
| Principle        | Core Mechanism & Problem Solved                          |
+------------------+----------------------------------------------------------+
| 1. Abstraction   | Hiding complex implementation details behind simple,     |
|                  | stable conceptual interfaces (e.g., PaymentGateway).    |
+------------------+----------------------------------------------------------+
| 2. Encapsulation | Bundling state and behavior while restricting direct     |
|                  | field access to guarantee invariant preservation.        |
+------------------+----------------------------------------------------------+
| 3. Inheritance   | Establishing an "is-a" taxonomy where subclasses inherit |
|                  | contracts and characteristics from a base class.         |
+------------------+----------------------------------------------------------+
| 4. Polymorphism  | Allowing a single interface to represent diverse runtime |
|                  | implementations through dynamic method dispatch.         |
+------------------+----------------------------------------------------------+
```

### 4.1 Abstraction
- **Problem Solved:** Prevents consumer code from coupling to volatile internal algorithms, third-party SDK quirks, or hardware details.
- **Java Mechanism:** `interface` and `abstract class`.
- **Backend Example:** A service defines `interface NotificationSender { void send(UserId id, String msg); }`. The caller knows only how to dispatch notifications. Concrete implementations (`TwilioSmsSender`, `SendGridEmailSender`, `FirebasePushSender`) can change without modifying the calling business logic.
- **Anti-Pattern / Overuse:** Creating speculative abstractions for single-use internal logic (creating `IUserService` and `UserServiceImpl` when there is only ever one implementation and no polymorphic variation).

### 4.2 Encapsulation
- **Problem Solved:** Prevents external callers from leaving an object in an invalid, corrupt, or inconsistent state.
- **Java Mechanism:** Access modifiers (`private`), immutable types, defensive copying, and validating domain methods.
- **Backend Example:** In an `Order` entity, fields `List<OrderItem> items` and `OrderStatus status` are `private`. External code cannot call `items.clear()` directly; it must call `order.cancel()`, which checks that the order is not already shipped, updates status, and emits a cancellation event.
- **Anti-Pattern / Overuse:** Generating indiscriminate public getters and setters for all private fields using IDE shortcuts or Lombok's `@Data`. An object with public setters for every field has zero encapsulation; it is an open data structure.

### 4.3 Inheritance
- **Problem Solved:** Models genuine hierarchical domain taxonomies ("is-a" relationships) and shares fundamental invariant infrastructure.
- **Java Mechanism:** `extends` keyword.
- **Backend Example:** An abstract base class `AbstractJdbcRepository<T, ID>` handles database connection pooling, transaction rollback, and statement closing, while concrete subclasses (`PostgreSqlUserRepository`) implement entity-specific SQL mapping.
- **Anti-Pattern / Overuse (The Fragile Base Class Problem):** Subclassing purely for code reuse ("has-a" masquerading as "is-a"). For example, inheriting `Properties` from `Hashtable` in early Java was a historic architectural error: `Properties` allows non-string keys via inherited `put(Object, Object)` methods, violating its own domain contract.

### 4.4 Polymorphism
- **Problem Solved:** Eliminates rigid, error-prone `switch` or `if-else if` cascades checking object types.
- **Java Mechanism:** Method overriding and dynamic dispatch (`invokevirtual` via vtables).
- **Backend Example:** A checkout system processes payment using:
  ```java
  public void processCheckout(PaymentMethod method, BigDecimal amount) {
      // Polymorphic dispatch: invokes CreditCardPayment, UpiPayment, or PayPalPayment at runtime
      method.charge(amount); 
  }
  ```
- **Distinction:** Do not confuse **Method Overloading** (compile-time ad-hoc polymorphism) with **Method Overriding** (runtime subtype polymorphism).

---

## Section 5 — Overloading, Overriding, and Dynamic Dispatch

### 5.1 Compile-Time Overloading and Static Binding

**Method Overloading** occurs when two or more methods in the same class share the exact same method name but possess **different parameter lists** (different parameter counts, types, or order). Overloading is resolved **statically at compile time** by `javac` based on the **declared compile-time types** of the arguments.

```java
public class OverloadDemo {
    public void process(Object obj) { System.out.println("Object overload"); }
    public void process(String str) { System.out.println("String overload"); }

    public static void main(String[] args) {
        OverloadDemo demo = new OverloadDemo();
        Object candidate = "Hello FAANG";
        
        // Declared type of candidate is Object!
        demo.process(candidate); // Prints: "Object overload"!
    }
}
```
Because the declared compile-time type of `candidate` is `Object`, `javac` binds the call directly to `process(Object)`. Overload resolution has zero awareness of the actual runtime object type!

### 5.2 Runtime Overriding and Dynamic Method Dispatch

**Method Overriding** occurs when a subclass provides a specific implementation of an instance method already declared in its superclass. Overriding is resolved **dynamically at runtime** by the JVM based on the **actual runtime class** of the receiver object.

#### The Virtual Method Table (vtable)
In modern JVMs (like HotSpot), each loaded class maintains a **Virtual Method Table (vtable)** containing memory function pointers to its executable methods. When `invokevirtual` executes:
1. The CPU inspects the receiver object's header to find its runtime `Klass` pointer.
2. It looks up the method offset in the class's vtable.
3. It jumps directly to the subclass's overridden implementation.

```
Declared Type: Animal a = new Dog();
a.makeSound();

Compile Time: javac verifies that Animal declares 'void makeSound()'. Emits invokevirtual.
Runtime: JVM inspects 'a' on Heap -> finds Klass = Dog -> Dog's vtable points to Dog.makeSound().
         Executes Dog's method!
```

### 5.3 Overriding Restrictions and Contracts

To ensure that a subclass remains a valid substitute for its superclass (Liskov Substitution Principle), the Java compiler strictly enforces four overriding rules:
1. **Method Signature:** Must match the superclass method name and parameter types exactly.
2. **Return Type (Covariant Returns):** Must be identical or a **subtype** of the superclass method's return type (e.g., if superclass returns `Number`, subclass may return `Integer`).
3. **Access Visibility:** Cannot become *more restrictive*. If superclass declares `public`, subclass must declare `public`. Subclass *may* broaden visibility (e.g., overriding a `protected` method as `public`).
4. **Checked Exceptions:** Subclass cannot declare *new or broader checked exceptions*. It may declare fewer checked exceptions, more specific (subclass) checked exceptions, or omit them entirely.

### 5.4 Static Method Hiding versus Instance Method Overriding

Static methods belong to the Class, not to an object instance. Therefore, static methods **cannot be overridden** polymorphically.

If a subclass declares a static method with the exact same signature as a static method in its superclass, it is called **Method Hiding**, not overriding:

```java
class Super {
    public static void print() { System.out.println("Super Static"); }
    public void instancePrint() { System.out.println("Super Instance"); }
}

class Sub extends Super {
    public static void print() { System.out.println("Sub Static"); }
    @Override
    public void instancePrint() { System.out.println("Sub Instance"); }
}

public class DispatchDemo {
    public static void main(String[] args) {
        Super poly = new Sub();
        
        poly.instancePrint(); // Prints: "Sub Instance" (Dynamic Runtime Dispatch)
        poly.print();         // Prints: "Super Static"! (Static Compile-Time Binding based on declared type 'Super')
    }
}
```

### 5.5 Field Hiding: Fields are Never Polymorphic

A ubiquitous interview trap involves accessing fields through polymorphic references:

```java
class Parent {
    public String name = "ParentField";
}
class Child extends Parent {
    public String name = "ChildField";
}

public class FieldTrap {
    public static void main(String[] args) {
        Parent ref = new Child();
        System.out.println(ref.name); // Prints: "ParentField"!
    }
}
```
> **CRITICAL RULE:** **Fields do not exhibit polymorphic behavior.** Variable access is resolved strictly at compile time based on the declared reference type (`Parent`), never by dynamic dispatch.

---

## Section 6 — Abstract Classes and Interfaces

### 6.1 Fundamental Distinctions

```
+------------------------------------+------------------------------------+------------------------------------+
| Feature                            | Abstract Class                     | Interface                          |
+------------------------------------+------------------------------------+------------------------------------+
| Inheritance Model                  | Single inheritance (extends 1)     | Multiple implementation (implements N)
| State & Instance Fields            | Can declare mutable instance fields| Only public static final constants |
| Constructor Support                | Has constructors (called via super)| No constructors                    |
| Method Types                       | Abstract, concrete, final, static  | Abstract, default, static, private |
| Access Modifiers on Methods        | public, protected, package-private | public (default/static), private   |
| Primary Architectural Intent       | Shared stateful template/skeletal  | Behavioral contract and capability |
|                                    | base implementation                | specification across hierarchies   |
+------------------------------------+------------------------------------+------------------------------------+
```

### 6.2 The Evolution of Java Interfaces

- **Java 7 and Earlier:** Interfaces were 100% abstract contracts containing only method declarations and static constants.
- **Java 8 (Default and Static Methods):** To evolve existing interfaces (such as adding `stream()` to `java.util.Collection`) without breaking thousands of legacy third-party implementations, Java 8 introduced **`default` methods** (concrete instance methods with bodies) and **`static` utility methods**.
- **Java 9 (Private Methods):** Added `private` and `private static` methods within interfaces to allow code reuse between multiple default methods without exposing helper methods publicly.

### 6.3 Resolving Interface Default Method Conflicts

When a class implements multiple interfaces declaring identical default method signatures, the compiler applies three disambiguation rules:
1. **Rule 1: Classes Beat Interfaces:** A concrete method declared in a superclass always overrides any default method provided by an implemented interface.
2. **Rule 2: Sub-Interfaces Beat Super-Interfaces:** If Interface B extends Interface A and both declare `default void act()`, Interface B's implementation wins.
3. **Rule 3: Explicit Disambiguation Required:** If two unrelated interfaces (e.g., `interface Flyer` and `interface Swimmer`) both define `default void move()`, the implementing class fails compilation unless it explicitly overrides the method and specifies which implementation to execute:
   ```java
   public class FlyingFish implements Flyer, Swimmer {
       @Override
       public void move() {
           Flyer.super.move(); // Explicitly dispatches to Flyer's default method
       }
   }
   ```

### 6.4 Strategic Selection Rules

- **Use an Interface When:**
  - You are defining a capability or role shared by completely unrelated classes (e.g., `Comparable`, `Serializable`, `AutoCloseable`).
  - You require multiple inheritance of type.
  - You are practicing **Dependency Inversion** to allow mock implementations in unit tests (e.g., injecting an interface `OrderRepository` into `OrderService`).
- **Use an Abstract Class When:**
  - You want to share state (non-static instance fields like `protected final Connection dbConn`).
  - You want to define a **Template Method Pattern**, where an algorithm's skeleton is implemented in a final method, delegating specific steps to protected abstract hooks.

---

## Section 7 — Inheritance, Composition, and SOLID Design

### 7.1 Inheritance versus Composition

> **DESIGN PRINCIPLE:** *"Favor object composition over class inheritance."* (GoF Design Patterns).

- **Inheritance ("is-a"):** Subclass is tightly coupled to the superclass's implementation details. Any change in superclass internal behavior can silently break subclass invariants (**The Fragile Base Class Problem**).
- **Composition ("has-a"):** A class holds a private reference to an instance of another class and delegates operations to it. This provides loose coupling, black-box testing, and dynamic runtime behavior changes.

#### The Classical Inheritance Flaw (Bloated Counter)
Imagine subclassing `HashSet` to count total inserted elements:
```java
public class BrokenCountSet<E> extends HashSet<E> {
    private int addCount = 0;

    @Override
    public boolean add(E e) {
        addCount++;
        return super.add(e);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return super.addAll(c); // BUG: HashSet.addAll() internally calls add() for each element!
    }
}
```
If you add 3 elements via `addAll()`, `addCount` becomes **6** instead of 3, because `HashSet.addAll()` calls `add()` internally! 

#### The Solution: Composition & Forwarding
```java
public class SafeCountSet<E> implements Set<E> {
    private final Set<E> delegate; // Composition
    private int addCount = 0;

    public SafeCountSet(Set<E> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    public boolean add(E e) {
        addCount++;
        return delegate.add(e);
    }

    public boolean addAll(Collection<? extends E> c) {
        addCount += c.size();
        return delegate.addAll(c); // Independent of internal delegation details!
    }
    // Delegate remaining Set methods directly...
}
```

### 7.2 The SOLID Principles in Backend Engineering

1. **Single Responsibility Principle (SRP):** *A class should have one, and only one, reason to change.* A class should encapsulate a single business concern. Do not place HTTP request parsing, SQL queries, and PDF invoice generation inside a single `OrderController`.
2. **Open/Closed Principle (OCP):** *Software entities should be open for extension, but closed for modification.* Add new functionality by writing new classes implementing an interface (e.g., adding `CryptoPaymentMethod`), rather than modifying existing `switch` statements across existing classes.
3. **Liskov Substitution Principle (LSP):** *Subtypes must be substitutable for their base types without altering program correctness.*
   - *Classic LSP Violation:* The Square-Rectangle problem. If `Square` extends `Rectangle`, overriding `setWidth(w)` to also mutate `height` breaks callers expecting independent dimension changes.
   - *Backend LSP Violation:* Subclassing a `Repository` and overriding `delete()` to throw `UnsupportedOperationException`. Any caller passing the base type will crash at runtime.
4. **Interface Segregation Principle (ISP):** *Clients should not be forced to depend on methods they do not use.* Split wide, monolithic interfaces into fine-grained role interfaces (e.g., splitting `CrudRepository` into `ReadOnlyRepository` and `WritableRepository`).
5. **Dependency Inversion Principle (DIP):** *High-level modules should not depend on low-level modules; both should depend on abstractions.* Business services (`PaymentService`) must depend on interfaces (`PaymentGateway`), while concrete infrastructure classes (`StripePaymentGateway`) implement those interfaces. Injected via constructor Dependency Injection.

---

## Section 8 — Object Methods, Equality, and Hashing Contracts

### 8.1 The Core Methods of `java.lang.Object`

Every class in Java implicitly inherits from `java.lang.Object`. The five most consequential methods are:
1. `public boolean equals(Object obj)`: Tests logical equivalence.
2. `public int hashCode()`: Computes an integer hash value for bucket indexing.
3. `public String toString()`: Returns string representation (default: `ClassName@hexHashCode`).
4. `protected Object clone()`: Field-by-field shallow copy (discouraged; use copy constructors instead).
5. `protected void finalize()`: **Deprecated in Java 9, marked for removal in Java 18+**. Never use; rely on `try-with-resources` and `Cleaner`.

### 8.2 The Formal `equals()` Contract (JLS & Javadoc)

An overridden `equals()` method must define an **equivalence relation**:
1. **Reflexive:** For any non-null reference `x`, `x.equals(x)` must return `true`.
2. **Symmetric:** For any non-null references `x` and `y`, `x.equals(y)` returns `true` if and only if `y.equals(x)` returns `true`.
3. **Transitive:** If `x.equals(y)` is `true` and `y.equals(z)` is `true`, then `x.equals(z)` must return `true`.
4. **Consistent:** Repeated invocations must return the same boolean, provided no fields are modified.
5. **Non-nullity:** For any non-null reference `x`, `x.equals(null)` must return `false`.

#### Idiomatic, Production-Grade `equals()` Implementation
```java
public final class UserAccount {
    private final long id;
    private final String email;

    public UserAccount(long id, String email) {
        this.id = id;
        this.email = Objects.requireNonNull(email);
    }

    @Override
    public boolean equals(Object o) {
        // Step 1: Identity check (fast-path optimization)
        if (this == o) return true;
        // Step 2: Null and exact class type check (prevents symmetry violations across subclasses)
        if (o == null || getClass() != o.getClass()) return false;
        // Step 3: Type cast
        UserAccount that = (UserAccount) o;
        // Step 4: Compare significant fields
        return this.id == that.id && this.email.equals(that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }
}
```

### 8.3 The `hashCode()` Contract and the Silent Hash Map Bug

The relationship between `equals()` and `hashCode()` is mathematically mandatory:
1. **If two objects are equal according to `equals()`, their `hashCode()` MUST produce the exact same integer value.**
2. If two objects produce different hash codes, they are guaranteed to be unequal.
3. Unequal objects *may* produce identical hash codes (**Hash Collision**).

#### The Silent Bug: Overriding `equals()` Without `hashCode()`
If you override `equals()` but fail to override `hashCode()`, the class inherits `Object.hashCode()`, which assigns hash codes based on internal memory locations.
```java
// Two instances with identical contents
UserAccount user1 = new UserAccount(101L, "admin@example.com");
UserAccount user2 = new UserAccount(101L, "admin@example.com");

Map<UserAccount, String> permissions = new HashMap<>();
permissions.put(user1, "SUPERUSER");

// user1.equals(user2) is TRUE, BUT:
// user1.hashCode() != user2.hashCode()!
System.out.println(permissions.get(user2)); // PRINTS: NULL! Record is lost in the hash map!
```
Because `user2` hashes to a completely different hash bucket, the map searches the wrong bucket array index and returns `null`, even though an equal key exists!

#### The Mutable Key Disaster
If an object's fields mutate after it is inserted as a key into a `HashMap` or `HashSet`:
1. Its hash code changes.
2. The entry remains trapped in its original bucket.
3. Subsequent `get()` or `containsKey()` calls compute the new hash code, search a *new* bucket, and fail to find the entry.
4. Calling `remove()` also fails to find the entry, causing **permanent memory leaks**.
> **RULE:** Never use mutable objects as keys in hash-based collections. Keys must be strictly immutable.

### 8.4 `Comparable` versus `Comparator`

- **`Comparable<T>` (`compareTo(T o)`):** Defines an object's **Natural Ordering**. Implemented by the domain class itself (e.g., `String`, `Integer`, `Instant`).
  - Returns negative if `this < o`, zero if `this.equals(o)`, positive if `this > o`.
  - *Contract Requirement:* Natural ordering should be consistent with `equals()`: `(x.compareTo(y) == 0) == (x.equals(y))`. Violating this causes sorted collections (`TreeSet`, `TreeMap`) to behave erratically compared to standard collections (`HashSet`).
- **`Comparator<T>` (`compare(T o1, T o2)`):** Defines an **External Custom Ordering Strategy**. Multiple comparators can be created for a single class (e.g., sorting users by name, by registration date, or by score).
  - Modern Java uses fluent combinators: `Comparator.comparing(User::getLastName).thenComparing(User::getFirstName)`.

---

## Section 9 — Strings, Immutability, and Defensive Design

### 9.1 Why `java.lang.String` is Immutable

In Java, `String` instances are completely immutable; once allocated, their character contents cannot be changed. This is a deliberate architectural decision:
1. **Security:** Strings represent sensitive parameters: database URLs, usernames, passwords, network hostnames, and filesystem paths. If Strings were mutable, an attacker could pass a valid file path to a security check and concurrently modify the path before the file is opened (Time-of-Check to Time-of-Use / TOCTOU vulnerability).
2. **Thread Safety:** Immutable objects are inherently thread-safe. Multiple threads can concurrently read, share, and cache Strings with zero synchronization.
3. **Hash Code Caching:** `String` caches its 32-bit `hash` value on first calculation. Because the character array cannot mutate, the hash code is computed at most once, making Strings exceptionally fast keys in `HashMap` and `HashSet`.
4. **String Pool (Interning):** The JVM maintains a **String Pool** in the heap. Identical string literals share the exact same heap memory instance, saving megabytes of RAM:
   ```java
   String s1 = "hello";
   String s2 = "hello";
   System.out.println(s1 == s2); // true! Both reference identical String Pool instance.

   String s3 = new String("hello");
   System.out.println(s1 == s3); // false! new explicitly allocates a distinct heap instance.
   ```

### 9.2 String Concatenation and `StringBuilder`

Because Strings are immutable, concatenating Strings in a loop with the `+` operator allocates a new `String` object on every single iteration:
```java
// PATHOLOGICAL CODE: O(n^2) Time Complexity & Massive GC Churn
String result = "";
for (int i = 0; i < 10_000; i++) {
    result += i; // Allocates 10,000 intermediate String and StringBuilder objects!
}
```
In modern Java (Java 9+ with `invokedynamic` string concat), single-statement concatenations (`"ID: " + id + ", Name: " + name`) are optimized using `StringConcatFactory`. In loops, however, always use **`StringBuilder`** (unsynchronized, single-threaded buffer) to achieve linear $O(n)$ performance with zero intermediate allocations. (Avoid legacy `StringBuffer`, which adds unnecessary synchronization overhead on every method call).

### 9.3 Designing Immutable Classes

To guarantee that a class is genuinely immutable, apply the **Five Rules of Immutability** (Effective Java):
1. **Don't provide mutator methods** (no setters).
2. **Ensure the class cannot be extended** (declare class `final`, or make constructors private with static factories).
3. **Make all fields `final`** (enforces initialization during construction).
4. **Make all fields `private`** (prevents direct client access).
5. **Ensure exclusive access to any mutable components:** If the class contains fields referencing mutable objects (such as `Date`, `int[]`, or `List`), enforce **Defensive Copying** in both constructors and accessors!

#### The Defensive Copying Imperative
```java
public final class ImmutableReport {
    private final String title;
    private final List<String> tags; // List is a mutable interface!

    public ImmutableReport(String title, List<String> tags) {
        this.title = title;
        // DEFENSIVE COPY ON INGESTION: Caller cannot modify internal state via external list
        this.tags = new ArrayList<>(Objects.requireNonNull(tags));
    }

    public List<String> getTags() {
        // DEFENSIVE COPY OR UNMODIFIABLE WRAPPER ON READ:
        return Collections.unmodifiableList(tags);
    }
}
```
*The Final Field Misconception:* Declaring a field `final` does **not** make the referenced object immutable! A `final List<String> list = new ArrayList<>();` prevents the variable `list` from pointing to a different collection, but any caller can still call `list.add("corrupt")` to mutate the list's internal state.

### 9.4 Java 16+ Records

Introduced in Java 14/15 and standardized in **Java 16 LTS**, a **Record** is a transparent, immutable data carrier:
```java
public record OrderItem(String sku, int quantity, BigDecimal unitPrice) {
    // Compact canonical constructor validation
    public OrderItem {
        Objects.requireNonNull(sku, "SKU must not be null");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
    }
}
```
The compiler automatically generates:
- `private final` fields for all components.
- A public canonical constructor.
- Public accessor methods (`sku()`, `quantity()`, `unitPrice()`).
- Implementations of `equals()`, `hashCode()`, and `toString()` based on all components.
Records cannot extend other classes (they implicitly extend `java.lang.Record`), but can implement interfaces.

---

## Section 10 — Generics and the Type System

### 10.1 Generic Type Safety and Type Erasure

Prior to Java 5, collections stored raw `Object` references, requiring error-prone manual casting that frequently failed at runtime with `ClassCastException`.

**Generics** provide compile-time type safety. The compiler validates that only objects of type `T` are added to a collection, and automatically inserts casts upon reading.

#### The Reality of Type Erasure
Java implemented generics via **Type Erasure** to preserve binary backwards-compatibility with legacy pre-Java 5 JVM bytecode.
- At compile time, `javac` checks generic constraints and type bounds.
- The compiler then **erases** all generic type parameters, replacing unbounded parameters with `Object` (or with their first bound `<T extends Number>` $\to$ `Number`) and inserting synthetic casts at call sites.
- At runtime inside the JVM, `List<String>` and `List<Integer>` are identical: both are simply raw instances of `java.util.List`!

#### Consequences of Type Erasure
1. **Cannot instantiate generic types:** `new T()` is illegal because `T` does not exist at runtime.
2. **Cannot check generic types with `instanceof`:** `if (obj instanceof List<String>)` is illegal; you can only check `if (obj instanceof List<?>)`.
3. **Cannot create generic arrays:** `new List<String>[10]` is illegal because arrays are reifiable and covariant, while generics are invariant and erased.
4. **Cannot use primitive types as type parameters:** `List<int>` is illegal (must use boxed wrapper `List<Integer>`).

### 10.2 Invariance, Covariance, and Contravariance

In Java, standard generic types are **Invariant**:
Even though `String` is a subtype of `Object`, **`List<String>` is NOT a subtype of `List<Object>`!**

```java
// Why Invariance is Mandatory:
List<String> strings = new ArrayList<>();
// List<Object> objects = strings; // If Java permitted this:
// objects.add(Integer.valueOf(42)); // We just added an Integer into a List<String>!
// String s = strings.get(0); // ClassCastException at runtime!
```

### 10.3 Wildcards and the PECS Principle

To write flexible API methods that accept collections of subtypes or supertypes, Java provides **Wildcards** (`?`):
- **Unbounded Wildcard (`List<?>`):** Represents a list of some unknown type. You can read `Object` from it, but cannot add anything except `null`.
- **Upper-Bounded Wildcard (`List<? extends T>`):** Covariant. Accepts `T` or any subclass of `T`.
- **Lower-Bounded Wildcard (`List<? super T>`):** Contravariant. Accepts `T` or any superclass of `T`.

> **THE GOLDEN RULE — PECS:** **Producer Extends, Consumer Super** (Joshua Bloch).

- If a parameterized type represents a **Producer** (you only **read** items from the collection), use `? extends T`.
- If a parameterized type represents a **Consumer** (you only **write** items into the collection), use `? super T`.

#### Concrete Worked Example: `Collections.copy`
```java
// src PRODUCES items to read; dest CONSUMES items to store
public static <T> void copy(List<? super T> dest, List<? extends T> src) {
    for (int i = 0; i < src.size(); i++) {
        dest.set(i, src.get(i)); // Reading T from src; writing T into dest!
    }
}
```
If you have a `List<Integer>` (src) and a `List<Number>` (dest), `copy(dest, src)` compiles cleanly because `Integer extends Number` (Producer) and `Number super Integer` (Consumer).

---

## Section 11 — Exceptions and Fault-Tolerant Error Handling

### 11.1 The Exception Hierarchy

```
                                  java.lang.Throwable
                                           |
                   +-----------------------+-----------------------+
                   |                                               |
            java.lang.Error                               java.lang.Exception
          (Fatal System Failures)                                  |
          - OutOfMemoryError                      +----------------+----------------+
          - StackOverflowError                    |                                 |
                                        Checked Exceptions              RuntimeException
                                        - IOException               (Unchecked Exceptions)
                                        - SQLException              - NullPointerException
                                        - ClassNotFoundException    - IllegalArgumentException
                                                                    - IndexOutOfBoundsException
```

- **`Error`:** Serious internal JVM failures from which an application typically cannot recover (e.g., `OutOfMemoryError`, `StackOverflowError`). Applications should **never** attempt to catch `Error`.
- **Checked Exceptions (`Exception` subclasses excluding `RuntimeException`):** Represent recoverable conditions that can occur in correct programs due to external environment factors (missing file, network drop, database connection timeout). The compiler **enforces** that checked exceptions must be either handled in a `try-catch` block or declared in the method signature via `throws`.
- **Unchecked Exceptions (`RuntimeException` subclasses):** Represent programming bugs, contract violations, or improper API usage (e.g., `NullPointerException`, `IllegalArgumentException`, `IllegalStateException`). The compiler does not require explicit handling.

### 11.2 Exception Handling Best Practices and Antipatterns

#### Antipattern 1: Swallowing Exceptions
```java
// DANGEROUS CODE: Silent failure
try {
    processPayment(order);
} catch (PaymentException e) {
    // Empty catch block! The payment failed, but the caller has zero knowledge!
}
```

#### Antipattern 2: Catching Generic `Exception` or `Throwable`
Catching `Exception` catches runtime errors (`NullPointerException`) that should have surfaced as bugs. Catching `Throwable` inadvertently catches fatal JVM errors (`OutOfMemoryError`), preventing normal JVM shutdown.

#### Antipattern 3: Losing the Root Cause
```java
try {
    dao.loadUser(id);
} catch (SQLException e) {
    throw new ServiceException("Failed to load user"); // BUG: Original SQLException stack trace is lost!
}
// CORRECT: Preserve cause
throw new ServiceException("Failed to load user", e);
```

### 11.3 `try-with-resources` and Suppressed Exceptions

Introduced in Java 7, the **`try-with-resources`** construct guarantees that any resource implementing `java.lang.AutoCloseable` or `java.io.Closeable` is automatically closed when the `try` block exits, even if an exception is thrown.

```java
try (FileInputStream fis = new FileInputStream("data.bin");
     BufferedInputStream bis = new BufferedInputStream(fis)) {
    bis.read();
} // fis and bis are automatically closed in reverse order of declaration!
```

#### The Power of Suppressed Exceptions
In legacy `try-finally` blocks, if both the `try` block threw an exception and the `finally` block's `close()` threw an exception, the exception in `finally` completely obliterated the original exception, hiding the root cause.

Under `try-with-resources`, if both the body and `close()` throw exceptions:
1. The exception thrown inside the `try` body is **preserved as the primary exception**.
2. The exception thrown during `close()` is captured and attached as a **Suppressed Exception**, accessible via `primaryException.getSuppressed()`.

---

## Section 12 — Collections Framework and Core API Semantics

### 12.1 The Java Collections Framework Architecture

```
                                      Iterable<E>
                                           |
                                     Collection<E>
                                           |
             +-----------------------------+-----------------------------+
             |                             |                             |
          List<E>                       Set<E>                        Queue<E>
      (Ordered, Indexed)          (Unique Elements)               (FIFO / Priority)
             |                             |                             |
      +------+------+               +------+------+                      v
      |             |               |             |                   Deque<E>
  ArrayList    LinkedList        HashSet       SortedSet<E>      (Double-Ended Queue)
                                    |             |                      |
                              LinkedHashSet    TreeSet             +-----+-----+
                                                                   |           |
                                                               ArrayDeque  LinkedList

Standalone Hierarchy:
                                       Map<K, V>
                                 (Key-Value Mappings)
                                           |
             +-----------------------------+-----------------------------+
             |                             |                             |
          HashMap                    LinkedHashMap                  SortedMap<K, V>
     (Array of Buckets)         (Hash + Doubly-Linked)                   |
                                                                      TreeMap
                                                                   (Red-Black Tree)
```

### 12.2 Collection Semantics and Selection Guide

```
+------------------+------------------+------------------+-----------------------------------------------+
| Collection       | Ordering         | Null Permitted?  | Primary Operational Characteristics           |
+------------------+------------------+------------------+-----------------------------------------------+
| ArrayList        | Insertion order  | Yes              | O(1) random access; fast traversal; slow O(n) |
|                  |                  |                  | mid-array insertions/deletions.               |
| ArrayDeque       | FIFO / LIFO      | No               | Resizable circular buffer; ideal default for  |
|                  |                  |                  | Stacks and Queues; zero node allocations.     |
| HashSet          | Unordered        | Yes (1 null)     | O(1) expected lookup/insert backed by HashMap.|
| TreeSet          | Sorted (Natural  | No               | O(log n) lookup/insert backed by Red-Black    |
|                  | or Comparator)   |                  | TreeMap; supports range queries.              |
| HashMap          | Unordered        | Yes (1 null key) | O(1) expected lookup/put; treeifies collision |
|                  |                  |                  | chains to red-black trees at threshold 8.     |
| LinkedHashMap    | Insertion order  | Yes              | O(1) lookup + predictable iteration order;    |
|                  | (or Access order)|                  | supports LRU cache eviction via removeEldest. |
| TreeMap          | Sorted by Key    | No null keys     | O(log n) operations; navigable key queries    |
|                  |                  |                  | (firstKey, higherKey, subMap).                |
+------------------+------------------+------------------+-----------------------------------------------+
```

### 12.3 Fail-Fast Iterators versus `ConcurrentModificationException`

Standard collections in `java.util` maintain an internal counter named **`modCount`** that increments on every structural modification (adding, removing, or reallocating elements).
When an `Iterator` is created, it records `expectedModCount = modCount`. On every call to `next()` or `remove()`, the iterator checks:
$$\text{if } (\text{modCount} \ne \text{expectedModCount}) \implies \text{throw new ConcurrentModificationException}()$$

#### The Second-to-Last Element Trap
```java
List<String> list = new ArrayList<>(List.of("A", "B", "C"));
for (String s : list) {
    if ("B".equals(s)) {
        list.remove(s); // Modifies underlying list directly!
    }
}
```
*Why this sometimes fails to throw CME:* In `ArrayList`, after removing `"B"` (the second-to-last element), `size` becomes 2, and the iterator's `cursor` is 2. On the next loop check, `hasNext()` evaluates `cursor != size` ($2 \ne 2 \implies \text{false}$), so the loop terminates without calling `next()`, accidentally masking the error!
*Safe Removal:* Always use explicit `Iterator.remove()` or `Collection.removeIf()`.

### 12.4 Unmodifiable Views versus Java 9+ Immutable Collections

- **`Collections.unmodifiableList(originalList)`:** Creates a **read-only wrapper view** over an existing collection. If the underlying `originalList` is modified elsewhere, the "unmodifiable" view reflects those changes immediately!
- **`List.of()`, `Set.of()`, `Map.of()` (Java 9+):** Produces **truly immutable, space-optimized** internal collections. They prohibit `null` elements, throw `UnsupportedOperationException` on any mutation, and have zero backing pointers to external mutable collections.

---

## Section 13 — Lambdas, Functional Interfaces, and Streams

### 13.1 Functional Interfaces and Lambda Expressions

A **Functional Interface** is an interface that contains **exactly one abstract method** (SAM - Single Abstract Method). It may contain any number of default or static methods. The `@FunctionalInterface` annotation informs `javac` to enforce this constraint.

#### The Standard Java Functional Interfaces (`java.util.function`)
1. `Predicate<T>`: `boolean test(T t)` (Filtering condition).
2. `Function<T, R>`: `R apply(T t)` (Transformation mapping).
3. `Consumer<T>`: `void accept(T t)` (Side-effect consumer).
4. `Supplier<T>`: `T get()` (Lazy factory provider).
5. `UnaryOperator<T>`: `T apply(T t)` (Specialized `Function<T, T>`).
6. `BiFunction<T, U, R>`: `R apply(T t, U u)` (Two-argument transformation).

### 13.2 Variable Capture and Effectively Final

A lambda expression can access variables declared outside its body (**Variable Capture**). However, Java mandates that any captured local variable must be **final or effectively final** (a variable whose value is never modified after initialization).

```java
int factor = 2; // Effectively final
List<Integer> doubled = numbers.stream().map(n -> n * factor).toList();

int counter = 0;
// numbers.forEach(n -> counter += n); // COMPILE ERROR: counter is not effectively final!
```
*Why the restriction exists:* Local variables live on the thread execution stack, while lambdas may execute asynchronously on a different thread after the enclosing method has returned and its stack frame has been destroyed. To allow capture, the JVM copies the variable's value into the lambda object on the heap. Permitting mutations would create memory synchronization anomalies between stack copies and heap copies.

### 13.3 Stream Pipelines: Laziness and Short-Circuiting

A Stream pipeline consists of three phases:
1. **Source:** `collection.stream()`, `Arrays.stream()`, or `Stream.of()`.
2. **Intermediate Operations (Lazy):** `filter()`, `map()`, `flatMap()`, `distinct()`, `sorted()`. Intermediate operations **do not execute immediately**. They merely construct an execution plan.
3. **Terminal Operations (Eager):** `collect()`, `forEach()`, `reduce()`, `count()`, `findFirst()`. A terminal operation triggers pipeline execution.

```java
// Demonstrating Laziness and Short-Circuiting:
List<String> names = List.of("Alice", "Bob", "Charlie", "David");

String match = names.stream()
    .filter(name -> {
        System.out.println("Filter: " + name);
        return name.length() > 4;
    })
    .map(name -> {
        System.out.println("Map: " + name);
        return name.toUpperCase();
    })
    .findFirst() // Terminal operation short-circuits on first match!
    .orElse("");

// OUTPUT:
// Filter: Alice
// Map: Alice
// (Charlie and David are NEVER evaluated because findFirst() satisfied the pipeline!)
```

### 13.4 `map()` versus `flatMap()`

- **`map()` (One-to-One):** Transforms each element $T$ into exactly one element $R$. If `Function` returns a Stream, `map()` produces a stream of streams: `Stream<Stream<R>>`.
- **`flatMap()` (One-to-Many + Flattening):** Transforms each element $T$ into a `Stream<R>`, and then flattens all generated streams into a single contiguous `Stream<R>`.

```java
// An Order has a List<OrderItem>
List<Order> orders = getOrders();

// map() produces Stream<List<OrderItem>>
// flatMap() flattens all nested items into Stream<OrderItem>:
List<OrderItem> allItems = orders.stream()
    .flatMap(order -> order.getItems().stream())
    .collect(Collectors.toList());
```

### 13.5 Parallel Streams: Power and Pitfalls

Calling `.parallelStream()` partitions work across the common `ForkJoinPool.commonPool()`.
- **When Parallel Streams Help:** CPU-intensive calculations over large in-memory arrays where operations are completely independent and state-free.
- **When Parallel Streams Harm:** 
  - I/O-bound tasks (database queries, network requests) block the shared worker threads, starving the entire JVM's common pool.
  - Small collections where splitting and thread coordination overhead exceeds sequential iteration cost.
  - Operations that rely on encounter order or shared mutable state.

---

## Section 14 — Enums, Records, Annotations, and Modern Java

### 14.1 Type-Safe Enums

An **`enum`** in Java is a full-featured class that extends `java.lang.Enum`. Enums provide type safety, compile-time validation, and singleton guarantees.

```java
public enum OrderStatus {
    PENDING(100) {
        @Override
        public boolean canCancel() { return true; }
    },
    SHIPPED(200) {
        @Override
        public boolean canCancel() { return false; }
    },
    DELIVERED(300) {
        @Override
        public boolean canCancel() { return false; }
    };

    private final int statusCode;

    OrderStatus(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getStatusCode() { return statusCode; }
    public abstract boolean canCancel();
}
```
- JVM guarantees that enum constants are immutable singletons; they are safe against deserialization attacks and reflection instantiation.

### 14.2 Annotations and Metadata

Annotations provide compile-time or runtime metadata.
- **Retention Policies (`@Retention`):**
  - `SOURCE`: Discarded by the compiler (e.g., `@Override`, `@SuppressWarnings`).
  - `CLASS`: Recorded in `.class` file but discarded by JVM at runtime (default).
  - `RUNTIME`: Preserved in bytecode and readable via Reflection at runtime (e.g., Spring's `@Service`, `@Autowired`, Jackson's `@JsonProperty`).
- **Target Targets (`@Target`):** Specifies where the annotation can be placed (`ElementType.TYPE`, `METHOD`, `FIELD`, `PARAMETER`).

### 14.3 Modern Java Features (Java 16, 17, and 21 LTS)

#### 1. Sealed Classes and Interfaces (Java 17 LTS)
Allows a class or interface to strictly control which subclasses are permitted to extend it, modeling exhaustive algebraic data types:
```java
public sealed interface PaymentResult permits Success, Failure, Pending {}

public record Success(String transactionId) implements PaymentResult {}
public record Failure(String errorCode) implements PaymentResult {}
public record Pending() implements PaymentResult {}
```

#### 2. Pattern Matching for `switch` (Java 21 LTS)
Combines type testing and casting in a single expressive block:
```java
public static String formatResult(PaymentResult result) {
    return switch (result) {
        case Success s -> "Paid successfully: " + s.transactionId();
        case Failure f -> "Payment failed: " + f.errorCode();
        case Pending p -> "Payment pending authorization";
        // No 'default' required because PaymentResult is sealed and exhaustive!
    };
}
```

---

## Section 15 — JVM Memory Architecture, Garbage Collection, and JIT

### 15.1 JVM Runtime Data Areas

```
+-----------------------------------------------------------------------------+
|                           JVM RUNTIME DATA AREAS                            |
+-----------------------------------------------------------------------------+
| Shared Across All Threads:                                                  |
|                                                                             |
|  +-----------------------------------------------------------------------+  |
|  | Heap Memory:                                                          |  |
|  |  * Young Generation (Eden, Survivor S0, Survivor S1)                  |  |
|  |  * Old Generation (Tenured Objects)                                   |  |
|  |  * All object instances and array allocations reside here.             |  |
|  +-----------------------------------------------------------------------+  |
|  | Metaspace (Native OS Memory, Replaced PermGen in Java 8):             |  |
|  |  * Loaded Class metadata, Method bytecodes, Constant Pool.            |  |
|  +-----------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------+
| Private to Each Thread:                                                     |
|                                                                             |
|  +--------------------+  +--------------------+  +--------------------+     |
|  | Thread 1 Stack:    |  | Thread 2 Stack:    |  | Program Counter    |     |
|  |  * Stack Frames    |  |  * Stack Frames    |  | (PC) Register      |     |
|  |  * Local Variables |  |  * Local Variables |  +--------------------+     |
|  |  * Operand Stack   |  |  * Operand Stack   |  | Native Method Stack|     |
|  +--------------------+  +--------------------+  +--------------------+     |
+-----------------------------------------------------------------------------+
```

### 15.2 Object Reachability and Garbage Collection Roots

The JVM determines an object's eligibility for garbage collection through **Reachability Analysis**, tracing outward from a set of known **GC Roots**:
- **GC Roots Include:**
  1. Local variables and parameters residing in active Thread Stack frames.
  2. Active, live Java Threads.
  3. Static fields belonging to loaded classes.
  4. JNI (Java Native Interface) global and local references.

If an object on the heap cannot be reached via any directed reference chain originating from a GC Root, it is unreachable and eligible for reclamation.

#### Reference Strengths
- **Strong Reference:** Standard reference (`Order o = new Order()`). As long as reachable from a GC Root, it is never collected.
- **Soft Reference (`SoftReference<T>`):** Cleared only when the JVM is running low on memory before throwing `OutOfMemoryError`. Useful for memory-sensitive caches.
- **Weak Reference (`WeakReference<T>`):** Cleared eagerly during the next garbage collection cycle. Used in `WeakHashMap` to associate metadata with keys without preventing key collection.
- **Phantom Reference (`PhantomReference<T>`):** Enqueued after finalization; used for pre-mortem cleanup via `Cleaner`.

### 15.3 `OutOfMemoryError` versus `StackOverflowError`

- **`StackOverflowError`:** Occurs when a thread's call-stack frame allocations exceed the configured stack limit (`-Xss`, typically $1\text{ MB}$). Almost always caused by unbounded recursion or circular method calls.
- **`OutOfMemoryError: Java heap space`:** Occurs when the JVM cannot allocate a new object on the heap, and garbage collection fails to reclaim sufficient memory to satisfy the request. Caused by memory leaks (unintentional object retention in static collections) or heap under-provisioning.
- **`OutOfMemoryError: Metaspace`:** Occurs when classloader leaks (e.g., dynamic proxy generation, repeated redeployments in application servers) exhaust native OS memory allocated for class metadata.

### 15.4 JIT Compilation and Tiered Optimization

The JVM begins execution in interpreted mode for fast startup. As methods execute frequently ("hot spots"), the **Just-In-Time (JIT) Compiler** compiles bytecode into native machine instructions:
- **Tier 1 (C1 Client Compiler):** Compiles code quickly with basic optimizations and profiling instrumentation.
- **Tier 4 (C2 Server Compiler):** Performs deep, aggressive optimizations based on runtime execution profiles:
  - **Method Inlining:** Replaces method call overhead with the actual method body.
  - **Escape Analysis:** Determines if an object's scope never escapes the allocating method. If it does not escape, the JVM can allocate the object on the stack or eliminate it entirely via **Scalar Replacement**, avoiding heap allocation.
  - **Loop Unrolling & Vectorization:** Optimizes loops to utilize CPU SIMD vector registers.

---

## Section 16 — High-Value Interview Reasoning and Question Bank

### Question 1: Pass-by-Value Mechanics
**Q:** Does Java pass objects by reference or by value? What happens when a method reassigns an object parameter?  
**Model Answer:**  
Java is 100% strictly pass-by-value for all types. When an object is passed into a method, the value being copied is the **reference address pointer**, not the object itself. Inside the method, the parameter is a local variable holding a copy of that pointer. If the method mutates the object via the copied pointer (`param.setName("New")`), the change is reflected in the shared heap object. However, if the method reassigns the parameter (`param = new User()`), it merely points its local parameter copy to a new heap location; the caller's original reference variable remains completely unchanged.  
*Common Misconception:* Stating that primitives are pass-by-value while objects are pass-by-reference.

### Question 2: Static Method Hiding versus Dynamic Dispatch
**Q:** Why does calling `Parent p = new Child(); p.staticMethod();` execute `Parent`'s static method rather than `Child`'s?  
**Model Answer:**  
Static methods belong to the Class, not an object instance, and are resolved at compile time based on the declared reference type (`Parent`). Polymorphic dynamic dispatch (`invokevirtual`) applies exclusively to instance methods resolved via the object's vtable at runtime. Subclasses hide static methods; they do not override them.

### Question 3: The `equals()` and `hashCode()` Contract
**Q:** What happens if you override `equals()` in a class but do not override `hashCode()`, and use instances as keys in a `HashMap`?  
**Model Answer:**  
Violating the contract causes equivalent objects to produce different hash codes. In a `HashMap`, bucket index is calculated from `key.hashCode()`. When inserting key $A$ and looking up equivalent key $B$, the map computes $B$'s hash code and searches the wrong bucket, returning `null` even though $A\text{.equals}(B)$ is `true`.

### Question 4: String Immutability and Security
**Q:** Why are Strings immutable in Java, and why is a `char[]` preferred over `String` for storing passwords?  
**Model Answer:**  
Strings are immutable for security (preventing TOCTOU network/filesystem parameter tampering), thread safety, String Pool caching, and hash code memoization. A `char[]` is preferred for passwords because an immutable `String` remains in heap memory until an unpredictable future GC collection, leaving plain-text credentials exposed in memory dumps. A `char[]` can be explicitly overwritten with zeros (`Arrays.fill(pwd, '0')`) immediately after use.

### Question 5: Generic Invariance and PECS
**Q:** Why doesn't `List<Object> list = new ArrayList<String>();` compile, and when should you use `? extends T` versus `? super T`?  
**Model Answer:**  
Generics are invariant to preserve type safety; if `List<Object>` accepted a `List<String>`, code could insert an `Integer` into it, causing a `ClassCastException` upon subsequent string reads. Under PECS: use `? extends T` when reading from a Producer (covariance), and `? super T` when writing into a Consumer (contravariance).

### Question 6: Checked versus Unchecked Exceptions
**Q:** When should you design a custom exception as checked versus unchecked?  
**Model Answer:**  
Use **checked exceptions** for anticipated, recoverable business or environmental conditions where the caller can realistically take corrective action (e.g., `InsufficientFundsException`). Use **unchecked exceptions** (`RuntimeException`) for programming bugs, precondition failures, or fatal infrastructural errors where recovery is impossible (e.g., `DatabaseUnavailableException`).

### Question 7: `ArrayList` versus `LinkedList`
**Q:** Why is `ArrayList` almost universally preferred over `LinkedList` in backend applications, even when inserting elements?  
**Model Answer:**  
`ArrayList` stores elements in a contiguous memory buffer, maximizing CPU cache line spatial locality. `LinkedList` wraps every element in a 24-byte `Node` object scattered across the heap, triggering severe L1/L2 cache misses and GC overhead. Furthermore, `LinkedList.add(index, item)` requires an $O(n)$ pointer traversal to find the insertion point, negating the $O(1)$ node insertion benefit.

### Question 8: Stream Laziness and Side Effects
**Q:** Why shouldn't stream pipeline operations mutate shared external state?  
**Model Answer:**  
Stream operations are lazy and may execute out-of-order or in parallel. Mutating external state introduces race conditions, non-deterministic results, and violates functional purity. Transformations should produce new immutable collections using collectors.

### Question 9: Defensive Copying and Immutability
**Q:** If a class has all `private final` fields, is it guaranteed to be immutable?  
**Model Answer:**  
No. If a `final` field references a mutable object (e.g., `Date`, `ArrayList`), external code can mutate the object's internal state through that reference. True immutability requires defensive copying during construction and returning unmodifiable views or copies from accessors.

### Question 10: `System.gc()` Guarantees
**Q:** What does calling `System.gc()` guarantee?  
**Model Answer:**  
It guarantees nothing. It is merely a non-binding hint to the JVM that the application would appreciate garbage collection. The JVM is free to ignore the call entirely. In production, `-XX:+DisableExplicitGC` is often set to prevent third-party libraries from triggering full-GC stop-the-world pauses.

---

## Section 17 — Java and OOP Cheatsheet: Interview Revision Reference

> **Quick-Revision Technical Summary:** This self-contained cheatsheet consolidates foundational Java language semantics, OOP design rules, type-system contracts, collection trade-offs, and interview failure modes taught throughout Module 3.

### 17.1 Core Mental Models & Semantics

- **Strict Pass-by-Value:** Java copies primitive values and object reference pointers. Reassigning a parameter reference does not affect the caller; mutating object fields modifies the shared heap object.
- **Identity vs. Equality:** `==` tests pointer identity (memory address); `.equals()` tests domain state content equivalence.
- **Primitives vs. References:** Primitives hold raw binary values directly on stack/heap; reference variables hold address pointers referencing objects on the heap.
- **Initialization Order:** Super static $\to$ Sub static $\to$ Super instance block $\to$ Super constructor $\to$ Sub instance block $\to$ Sub constructor.

### 17.2 OOP Principles & Method Resolution

- **Overloading (Static Binding):** Resolved at compile time by `javac` based on the declared reference type.
- **Overriding (Dynamic Dispatch):** Resolved at runtime by JVM based on the actual object type via vtables.
- **Static Method Hiding:** Static methods cannot be overridden polymorphically; resolved by class reference type.
- **Field Hiding:** Fields are never polymorphic. Access is determined strictly by declared reference type.
- **LSP Rule:** Subtypes must be completely substitutable for base types without breaking caller expectations.

### 17.3 Contracts, Immutability & Generics

- **`equals()` & `hashCode()` Invariant:** If `a.equals(b)` is true, `a.hashCode() == b.hashCode()` must be true. Never use mutable objects as hash keys.
- **Five Immutability Rules:** No setters, class final, fields private and final, exclusive access via defensive copying on ingestion and read.
- **PECS Principle:** **Producer Extends, Consumer Super**. Use `<? extends T>` when reading data; use `<? super T>` when writing data.
- **Type Erasure:** Generics exist strictly for compile-time safety. Type arguments are erased to `Object` or bounds in bytecode.

### 17.4 Collections & Streams Matrix

- **`ArrayList` vs. `LinkedList`:** Default to `ArrayList` for CPU cache spatial locality. Avoid `LinkedList` due to node allocation and pointer-chasing cache misses.
- **`ArrayDeque`:** Superior, allocation-free circular buffer default for Stacks and Queues. Prohibits `null`.
- **Fail-Fast Iterators:** Detect structural modifications via `modCount != expectedModCount` on `next()`. Safe removal requires `it.remove()` or `removeIf()`.
- **Stream Pipelines:** Intermediate operations are lazy; terminal operations trigger execution. Do not mutate shared state inside streams.

### 17.5 Top 10 Technical Interview Traps

1. **"Java passes objects by reference."** False: Java passes the reference pointer *by value*.
2. **"Fields behave polymorphically."** False: Field access is resolved statically at compile time.
3. **"`final` makes an object immutable."** False: `final` only prevents variable reassignment; internal state remains mutable.
4. **"`System.gc()` immediately frees memory."** False: It is a non-binding hint often ignored by the JVM.
5. **"`List<String>` is a subtype of `List<Object>`."** False: Generics are invariant.
6. **"Overriding `equals()` without `hashCode()` works if keys are unique."** False: Breaks `HashMap` bucket lookup completely.
7. **"Parallel streams are always faster."** False: Add thread coordination overhead and starve the common `ForkJoinPool`.
8. **"Abstract classes cannot have constructors."** False: Abstract classes have constructors invoked via `super()`.
9. **"`String s = new String(\"a\")` uses only the String Pool."** False: It explicitly allocates a redundant heap object.
10. **"Catching `Exception` is good practice."** False: It swallows runtime bugs and masks critical failures.
