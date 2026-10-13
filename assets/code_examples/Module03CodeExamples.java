package assets.code_examples;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 03 Code Examples & Language Semantics Test Harness.
 *
 * Demonstrates idiomatic, production-grade Java 21 implementations of core
 * language semantics, type-system rules, object-oriented mechanics, and runtime behaviors:
 *
 * 1. Pass-by-Value & Reference Aliasing Mechanics.
 * 2. Static vs. Instance Initialization Order & Constructor Chaining.
 * 3. Dynamic Dispatch vs. Static Method Hiding vs. Field Hiding.
 * 4. equals() and hashCode() Contracts in Hash-Based Collections.
 * 5. Defensive Copying & Immutability Invariant Preservation.
 * 6. Generics Invariance, Wildcards, and the PECS Principle.
 * 7. Exception Handling, Try-With-Resources, and Suppressed Exceptions.
 * 8. Collection Semantics & Fail-Fast Iterator Behavior.
 * 9. Stream Laziness and map() vs. flatMap().
 *
 * All examples include explicit assertions verifying runtime guarantees.
 */
public class Module03CodeExamples {

    // =========================================================================
    // 1. PASS-BY-VALUE & REFERENCE ALIASING
    // =========================================================================

    public static class MutableBox {
        public int value;

        public MutableBox(int value) {
            this.value = value;
        }
    }

    public static class ParameterPassingDemo {

        /**
         * Reassigning the parameter variable 'box' only reassigns the local reference copy.
         * The caller's reference variable remains completely unchanged.
         */
        public static void attemptReassign(MutableBox box) {
            box = new MutableBox(999);
        }

        /**
         * Mutating the object through the reference copy alters the state of the shared heap object.
         */
        public static void mutateState(MutableBox box) {
            box.value += 10;
        }

        /**
         * Primitives are passed strictly by copy of value.
         */
        public static void modifyPrimitive(int x) {
            x = x + 100;
        }
    }

    // =========================================================================
    // 2. INITIALIZATION ORDER & CONSTRUCTOR CHAINING
    // =========================================================================

    public static class InitializationTracker {
        public static final List<String> executionLog = new ArrayList<>();
    }

    public static class SuperClass {
        static {
            InitializationTracker.executionLog.add("SuperClass: Static Initializer");
        }

        {
            InitializationTracker.executionLog.add("SuperClass: Instance Initializer");
        }

        public SuperClass() {
            InitializationTracker.executionLog.add("SuperClass: Constructor");
        }
    }

    public static class SubClass extends SuperClass {
        static {
            InitializationTracker.executionLog.add("SubClass: Static Initializer");
        }

        {
            InitializationTracker.executionLog.add("SubClass: Instance Initializer");
        }

        public SubClass() {
            super(); // Explicit or implicit constructor chaining
            InitializationTracker.executionLog.add("SubClass: Constructor");
        }
    }

    // =========================================================================
    // 3. DYNAMIC DISPATCH, STATIC HIDING, AND FIELD HIDING
    // =========================================================================

    public static class Parent {
        public String name = "ParentField";

        public String identifyInstance() {
            return "ParentInstance";
        }

        public static String identifyStatic() {
            return "ParentStatic";
        }
    }

    public static class Child extends Parent {
        // Field hiding: hides Parent.name, resolved at compile-time by reference type!
        public String name = "ChildField";

        // Instance method overriding: polymorphic dynamic dispatch at runtime
        @Override
        public String identifyInstance() {
            return "ChildInstance";
        }

        // Static method hiding: NOT polymorphic; resolved at compile-time by reference type!
        public static String identifyStatic() {
            return "ChildStatic";
        }
    }

    // =========================================================================
    // 4. EQUALS AND HASHCODE CONTRACTS
    // =========================================================================

    public static class Employee {
        private final long id;
        private final String department;

        public Employee(long id, String department) {
            this.id = id;
            this.department = Objects.requireNonNull(department);
        }

        public long getId() {
            return id;
        }

        public String getDepartment() {
            return department;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Employee employee = (Employee) o;
            return id == employee.id && department.equals(employee.department);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, department);
        }
    }

    /**
     * Faulty class illustrating the bug where equals is overridden without hashCode.
     */
    public static class FaultyKey {
        private final int id;

        public FaultyKey(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FaultyKey faultyKey = (FaultyKey) o;
            return id == faultyKey.id;
        }
        // hashCode() intentionally omitted; uses System.identityHashCode()
    }

    // =========================================================================
    // 5. DEFENSIVE COPYING & IMMUTABILITY
    // =========================================================================

    public static final class ImmutableDepartment {
        private final String name;
        private final List<String> employeeRoles;

        public ImmutableDepartment(String name, List<String> roles) {
            this.name = Objects.requireNonNull(name);
            // Defensive copy on ingestion prevents caller from modifying internal state
            this.employeeRoles = new ArrayList<>(Objects.requireNonNull(roles));
        }

        public String getName() {
            return name;
        }

        public List<String> getEmployeeRoles() {
            // Defensive copy or unmodifiable wrapper on accessor prevents external mutation
            return Collections.unmodifiableList(employeeRoles);
        }
    }

    // =========================================================================
    // 6. GENERICS, WILDCARDS, AND PECS (Producer Extends, Consumer Super)
    // =========================================================================

    public static class NumberUtilities {

        /**
         * Producer Extends: src PRODUCES numbers to read, so we use <? extends Number>.
         * Consumer Super: dest CONSUMES numbers to store, so we use <? super Number>.
         */
        public static <T> void copyElements(List<? super T> dest, List<? extends T> src) {
            for (T item : src) {
                dest.add(item);
            }
        }

        /**
         * Sums any list of numbers (Producer Extends).
         */
        public static double sumOfList(List<? extends Number> numbers) {
            double total = 0.0;
            for (Number num : numbers) {
                total += num.doubleValue();
            }
            return total;
        }
    }

    // =========================================================================
    // 7. EXCEPTIONS, TRY-WITH-RESOURCES, AND SUPPRESSED EXCEPTIONS
    // =========================================================================

    public static class FaultyResource implements Closeable {
        private final String name;
        private final boolean throwOnClose;

        public FaultyResource(String name, boolean throwOnClose) {
            this.name = name;
            this.throwOnClose = throwOnClose;
        }

        public void doWork(boolean shouldFail) throws IOException {
            if (shouldFail) {
                throw new IOException("Primary failure in " + name);
            }
        }

        @Override
        public void close() throws IOException {
            if (throwOnClose) {
                throw new IOException("Closing failure in " + name);
            }
        }
    }

    // =========================================================================
    // 8. TEST HARNESS & REGRESSION VERIFICATION
    // =========================================================================

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Module 03 Java & OOP Verification Test Suite...");

        testPassByValueAndAliasing();
        testInitializationOrder();
        testPolymorphicDispatchVsHiding();
        testEqualsAndHashCodeContract();
        testDefensiveCopyingImmutability();
        testGenericsAndPECS();
        testTryWithResourcesAndSuppressedExceptions();
        testCollectionFailFastSemantics();
        testStreamLazinessAndFlatMap();

        System.out.println("\nAll 9 Module 03 Language & OOP Test Suites Passed Successfully!");
    }

    private static void testPassByValueAndAliasing() {
        MutableBox box = new MutableBox(50);
        int primitiveVal = 100;

        // 1. Primitive pass-by-value
        ParameterPassingDemo.modifyPrimitive(primitiveVal);
        assert primitiveVal == 100 : "Primitive must remain unchanged after method return";

        // 2. Object reference reassignment
        ParameterPassingDemo.attemptReassign(box);
        assert box.value == 50 : "Caller reference must not point to newly allocated object";

        // 3. Object state mutation via reference copy
        ParameterPassingDemo.mutateState(box);
        assert box.value == 60 : "Object state must reflect mutation through reference copy";

        System.out.println("  [PASS] Language Semantics: Pass-by-value and reference aliasing verified");
    }

    private static void testInitializationOrder() {
        InitializationTracker.executionLog.clear();
        new SubClass();

        List<String> expectedOrder = List.of(
            "SuperClass: Static Initializer",
            "SubClass: Static Initializer",
            "SuperClass: Instance Initializer",
            "SuperClass: Constructor",
            "SubClass: Instance Initializer",
            "SubClass: Constructor"
        );

        assert InitializationTracker.executionLog.equals(expectedOrder) :
            "Initialization order violated! Actual: " + InitializationTracker.executionLog;

        System.out.println("  [PASS] Object Lifecycle: Static and instance initialization order verified");
    }

    private static void testPolymorphicDispatchVsHiding() {
        Parent polymorphicRef = new Child();

        // Instance method: polymorphic dynamic dispatch (Child's method executes)
        assert "ChildInstance".equals(polymorphicRef.identifyInstance()) :
            "Dynamic dispatch must execute overridden method of runtime object";

        // Static method: compile-time static binding (Parent's method executes)
        // Suppress compiler warning via explicit class invocation to verify semantics
        assert "ParentStatic".equals(Parent.identifyStatic()) :
            "Static method must be resolved based on declaring class, not runtime instance";

        // Field hiding: resolved at compile-time by declared reference type!
        assert "ParentField".equals(polymorphicRef.name) :
            "Field access must resolve by compile-time reference type, not runtime instance type";

        Child directRef = new Child();
        assert "ChildField".equals(directRef.name) : "Direct child field access must resolve child field";

        System.out.println("  [PASS] OOP Polymorphism: Dynamic dispatch vs static hiding vs field hiding verified");
    }

    private static void testEqualsAndHashCodeContract() {
        Employee emp1 = new Employee(101, "Engineering");
        Employee emp2 = new Employee(101, "Engineering");
        Employee emp3 = new Employee(102, "Operations");

        // Contract consistency
        assert emp1.equals(emp2) : "Identical fields must equal";
        assert !emp1.equals(emp3) : "Distinct IDs must not equal";
        assert emp1.hashCode() == emp2.hashCode() : "Equal objects must have identical hash codes";

        Map<Employee, String> directory = new HashMap<>();
        directory.put(emp1, "Lead Architect");
        assert "Lead Architect".equals(directory.get(emp2)) : "HashMap lookup must succeed with equivalent key";

        // FaultyKey contract violation demonstration
        FaultyKey key1 = new FaultyKey(500);
        FaultyKey key2 = new FaultyKey(500);
        assert key1.equals(key2) : "Keys evaluate as equal";

        Map<FaultyKey, String> faultyMap = new HashMap<>();
        faultyMap.put(key1, "Val");
        // Because hashCode() was not overridden, key1 and key2 land in different hash buckets almost certainly
        if (key1.hashCode() != key2.hashCode()) {
            assert !faultyMap.containsKey(key2) :
                "Demonstration: missing hashCode causes containsKey to fail on equivalent keys";
        }

        System.out.println("  [PASS] Object Contracts: equals() and hashCode() hashing contracts verified");
    }

    private static void testDefensiveCopyingImmutability() {
        List<String> mutableList = new ArrayList<>(List.of("Dev", "QA"));
        ImmutableDepartment dept = new ImmutableDepartment("Core Platform", mutableList);

        // Attempt 1: Mutating external input list after construction
        mutableList.add("Security");
        assert dept.getEmployeeRoles().size() == 2 : "Defensive copy must isolate internal list from caller";

        // Attempt 2: Mutating returned list
        try {
            dept.getEmployeeRoles().add("Hacker");
            assert false : "Accessor must return an unmodifiable view";
        } catch (UnsupportedOperationException expected) {
            // Success
        }

        System.out.println("  [PASS] Defensive Design: Constructor and accessor defensive copying preserved invariants");
    }

    private static void testGenericsAndPECS() {
        List<Integer> integers = List.of(10, 20, 30);
        List<Number> numberContainer = new ArrayList<>();

        // Copy integers (Producer Extends) into numbers (Consumer Super)
        NumberUtilities.copyElements(numberContainer, integers);
        assert numberContainer.size() == 3 : "Elements must copy successfully under PECS";

        double total = NumberUtilities.sumOfList(numberContainer);
        assert Math.abs(total - 60.0) < 0.001 : "Sum of list must equal 60.0";

        System.out.println("  [PASS] Generics & Types: Invariance, wildcards, and PECS principle verified");
    }

    private static void testTryWithResourcesAndSuppressedExceptions() {
        try (FaultyResource r1 = new FaultyResource("Resource1", true)) {
            r1.doWork(true);
            assert false : "Primary exception must be thrown";
        } catch (IOException ex) {
            // Primary exception thrown during body execution
            assert "Primary failure in Resource1".equals(ex.getMessage()) :
                "Primary exception must be preserved";

            // Exception during close() is captured as a suppressed exception
            Throwable[] suppressed = ex.getSuppressed();
            assert suppressed.length == 1 : "Suppressed exception must be attached";
            assert "Closing failure in Resource1".equals(suppressed[0].getMessage()) :
                "Suppressed exception message must match close() exception";
        }

        System.out.println("  [PASS] Error Handling: Try-with-resources and suppressed exceptions verified");
    }

    private static void testCollectionFailFastSemantics() {
        List<String> names = new ArrayList<>(List.of("Alpha", "Beta", "Gamma"));

        // Fail-Fast: structural modification during iteration throws ConcurrentModificationException.
        // Note: Removing "Alpha" leaves cursor=1 != size=2, ensuring next() is called and detects modCount mismatch.
        // (Removing the second-to-last element is a famous quirk where cursor==size causes hasNext() to terminate before next() runs!)
        try {
            for (String name : names) {
                if ("Alpha".equals(name)) {
                    names.remove(name); // Modifying underlying collection directly
                }
            }
            assert false : "Direct collection modification during foreach must throw CME";
        } catch (ConcurrentModificationException expected) {
            // Expected fail-fast behavior
        }

        // Safe removal via Iterator.remove()
        List<String> safeList = new ArrayList<>(List.of("Alpha", "Beta", "Gamma"));
        Iterator<String> it = safeList.iterator();
        while (it.hasNext()) {
            if ("Beta".equals(it.next())) {
                it.remove();
            }
        }
        assert safeList.size() == 2 && !safeList.contains("Beta") : "Iterator.remove() must safely remove elements";

        System.out.println("  [PASS] Collections Semantics: Fail-fast iterator and safe removal verified");
    }

    private static void testStreamLazinessAndFlatMap() {
        List<String> evaluations = new ArrayList<>();

        // Laziness: Intermediate operations execute only when terminal operation is triggered
        List<String> result = List.of("apple", "banana", "cherry", "date")
            .stream()
            .filter(s -> {
                evaluations.add("filter:" + s);
                return s.length() > 4;
            })
            .map(s -> {
                evaluations.add("map:" + s);
                return s.toUpperCase();
            })
            .limit(1) // Short-circuit terminal pipeline
            .collect(Collectors.toList());

        assert result.equals(List.of("APPLE")) : "Result must match first element passing filter";
        // Verify short-circuiting: "cherry" and "date" must never be evaluated!
        assert !evaluations.contains("filter:cherry") : "Stream must short-circuit after limit(1) satisfies terminal";

        // flatMap: Flattens 1-to-many stream mappings
        List<List<Integer>> nested = List.of(List.of(1, 2), List.of(3, 4));
        List<Integer> flattened = nested.stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        assert flattened.equals(List.of(1, 2, 3, 4)) : "flatMap must flatten nested streams into single stream";

        System.out.println("  [PASS] Streams & FP: Stream laziness, short-circuiting, and flatMap verified");
    }
}
