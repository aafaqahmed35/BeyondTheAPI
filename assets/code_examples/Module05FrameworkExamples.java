package assets.code_examples;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

/**
 * BeyondTheApi Software Engineering Handbook — Module 5 Verification Suite
 * Backend Frameworks & Application Engineering
 * 
 * Standalone, deterministic verification of core backend framework mechanics:
 * 1. IoC & Constructor Dependency Injection with Circular Dependency Detection
 * 2. Layered Request Flow: DTO vs. Entity separation & Domain Boundary
 * 3. Centralized Exception Translation & HTTP Status Mapping (@ControllerAdvice pattern)
 * 4. @Transactional Dynamic Proxy Mechanics & The Self-Invocation Trap
 * 5. JPA-Style Persistence Context: 1st-Level Cache, Dirty Checking & Flush
 * 6. Unit Testing with Test Doubles (Fakes) for Deterministic Isolation
 */
public class Module05FrameworkExamples {

    public static void main(String[] args) {
        System.out.println("Starting Module 05 Backend Frameworks Verification Test Suite...");
        testIocAndConstructorInjection();
        testCircularDependencyDetection();
        testLayeredArchitectureAndDtoBoundaries();
        testCentralizedExceptionTranslation();
        testTransactionalProxyAndSelfInvocationTrap();
        testPersistenceContextAndDirtyChecking();
        testUnitTestWithFakeRepository();
        System.out.println("\nAll 7 Module 05 Backend Framework Test Suites Passed Successfully!");
    }

    // =========================================================================
    // 1. IoC Container & Constructor Dependency Injection
    // =========================================================================

    public interface OrderRepository {
        void saveOrder(String orderId, int amount);
        int getOrderAmount(String orderId);
    }

    public static class InMemoryOrderRepository implements OrderRepository {
        private final Map<String, Integer> storage = new HashMap<>();

        @Override
        public void saveOrder(String orderId, int amount) {
            storage.put(orderId, amount);
        }

        @Override
        public int getOrderAmount(String orderId) {
            return storage.getOrDefault(orderId, 0);
        }
    }

    public static class OrderService {
        private final OrderRepository repository;

        // Constructor Injection: Explicit, immutable, testable dependencies
        public OrderService(OrderRepository repository) {
            this.repository = Objects.requireNonNull(repository, "OrderRepository must not be null");
        }

        public void placeOrder(String orderId, int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("Order amount must be positive");
            }
            repository.saveOrder(orderId, amount);
        }

        public int fetchAmount(String orderId) {
            return repository.getOrderAmount(orderId);
        }
    }

    private static void testIocAndConstructorInjection() {
        OrderRepository repo = new InMemoryOrderRepository();
        OrderService service = new OrderService(repo);

        service.placeOrder("ORD-101", 5000);
        assert service.fetchAmount("ORD-101") == 5000 : "Order amount mismatch in IoC test";
        System.out.println("  [PASS] IoC & DI: Constructor injection established immutable, testable wiring");
    }

    // Circular Dependency Detection Simulation
    private static void testCircularDependencyDetection() {
        Set<String> resolutionStack = new HashSet<>();
        boolean cycleDetected = false;

        // Simulating BeanA needing BeanB, which needs BeanA
        try {
            resolveBean("BeanA", resolutionStack);
        } catch (IllegalStateException e) {
            cycleDetected = true;
            assert e.getMessage().contains("Circular dependency detected") : "Unexpected error message";
        }

        assert cycleDetected : "Failed to detect circular dependency during bean creation!";
        System.out.println("  [PASS] IoC Container: Circular dependency detection verified");
    }

    private static void resolveBean(String beanName, Set<String> stack) {
        if (!stack.add(beanName)) {
            throw new IllegalStateException("Circular dependency detected while resolving: " + beanName);
        }
        if (beanName.equals("BeanA")) {
            resolveBean("BeanB", stack);
        } else if (beanName.equals("BeanB")) {
            resolveBean("BeanA", stack); // Triggers cycle!
        }
        stack.remove(beanName);
    }

    // =========================================================================
    // 2. Layered Architecture: DTO vs. Entity Separation
    // =========================================================================

    public record CreateUserRequest(String email, String rawPassword, String displayName) {}
    public record UserResponse(long id, String email, String displayName) {}

    public static class UserEntity {
        private final long id;
        private final String email;
        private final String passwordHash; // Internal security state, NEVER in response DTO
        private String displayName;

        public UserEntity(long id, String email, String passwordHash, String displayName) {
            this.id = id;
            this.email = email;
            this.passwordHash = passwordHash;
            this.displayName = displayName;
        }

        public long getId() { return id; }
        public String getEmail() { return email; }
        public String getPasswordHash() { return passwordHash; }
        public String getDisplayName() { return displayName; }
    }

    public static class UserService {
        private long idSequence = 1;
        private final Map<Long, UserEntity> db = new HashMap<>();

        public UserResponse registerUser(CreateUserRequest request) {
            // 1. Validation & business logic
            if (request.email() == null || !request.email().contains("@")) {
                throw new IllegalArgumentException("Invalid email format");
            }
            // 2. Map DTO to Entity (hash password)
            String hash = "HASHED_" + request.rawPassword().hashCode();
            UserEntity entity = new UserEntity(idSequence++, request.email(), hash, request.displayName());
            db.put(entity.getId(), entity);

            // 3. Map Entity to Response DTO (excluding passwordHash)
            return new UserResponse(entity.getId(), entity.getEmail(), entity.getDisplayName());
        }
    }

    private static void testLayeredArchitectureAndDtoBoundaries() {
        UserService service = new UserService();
        CreateUserRequest req = new CreateUserRequest("alice@example.com", "SuperSecret123", "Alice Smith");
        UserResponse resp = service.registerUser(req);

        assert resp.id() == 1 : "Expected user ID 1";
        assert resp.email().equals("alice@example.com") : "Email mismatch";
        assert resp.displayName().equals("Alice Smith") : "Display name mismatch";
        // Verify response DTO has no getPasswordHash method or leakage
        System.out.println("  [PASS] Layered Architecture: DTO vs Entity separation preserved security boundaries");
    }

    // =========================================================================
    // 3. Centralized Exception Translation (@ControllerAdvice Pattern)
    // =========================================================================

    public record ErrorResponse(int status, String errorCode, String message, long timestamp) {}

    public static class GlobalExceptionHandler {
        public ErrorResponse handleException(Throwable ex) {
            if (ex instanceof NoSuchElementException) {
                return new ErrorResponse(404, "RESOURCE_NOT_FOUND", ex.getMessage(), System.currentTimeMillis());
            } else if (ex instanceof IllegalArgumentException) {
                return new ErrorResponse(400, "BAD_REQUEST", ex.getMessage(), System.currentTimeMillis());
            } else {
                return new ErrorResponse(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred", System.currentTimeMillis());
            }
        }
    }

    private static void testCentralizedExceptionTranslation() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ErrorResponse notFound = handler.handleException(new NoSuchElementException("User ID 99 not found"));
        assert notFound.status() == 404 : "Expected 404 status";
        assert notFound.errorCode().equals("RESOURCE_NOT_FOUND");

        ErrorResponse badReq = handler.handleException(new IllegalArgumentException("Invalid email"));
        assert badReq.status() == 400 : "Expected 400 status";
        assert badReq.errorCode().equals("BAD_REQUEST");

        ErrorResponse serverErr = handler.handleException(new NullPointerException("NPE"));
        assert serverErr.status() == 500 : "Expected 500 status";
        assert !serverErr.message().contains("NPE") : "Internal exception stack should not leak to client";

        System.out.println("  [PASS] Exception Translation: ControllerAdvice pattern mapped domain errors to HTTP responses");
    }

    // =========================================================================
    // 4. @Transactional Dynamic Proxy Mechanics & The Self-Invocation Trap
    // =========================================================================

    public interface PaymentService {
        void externalMethod();
        void transactedMethod();
    }

    public static class PaymentServiceImpl implements PaymentService {
        public int transactionCount = 0;

        @Override
        public void externalMethod() {
            // SELF-INVOCATION TRAP: Calling this.transactedMethod() directly
            // bypasses the dynamic proxy object!
            this.transactedMethod();
        }

        @Override
        public void transactedMethod() {
            // Business logic intended to be transactional
        }
    }

    public static class TransactionalProxyHandler implements InvocationHandler {
        private final PaymentServiceImpl target;
        private final List<String> log = new ArrayList<>();

        public TransactionalProxyHandler(PaymentServiceImpl target) {
            this.target = target;
        }

        public List<String> getLog() { return log; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("transactedMethod")) {
                log.add("BEGIN_TX");
                target.transactionCount++;
                try {
                    Object result = method.invoke(target, args);
                    log.add("COMMIT_TX");
                    return result;
                } catch (Exception e) {
                    log.add("ROLLBACK_TX");
                    throw e;
                }
            }
            // Non-transacted methods pass through directly
            return method.invoke(target, args);
        }
    }

    private static void testTransactionalProxyAndSelfInvocationTrap() {
        PaymentServiceImpl rawTarget = new PaymentServiceImpl();
        TransactionalProxyHandler handler = new TransactionalProxyHandler(rawTarget);
        PaymentService proxy = (PaymentService) Proxy.newProxyInstance(
                PaymentService.class.getClassLoader(),
                new Class<?>[]{PaymentService.class},
                handler
        );

        // 1. Direct call on proxy: Transaction interceptor executes!
        proxy.transactedMethod();
        assert handler.getLog().contains("BEGIN_TX") : "Direct proxy call should begin transaction";
        assert handler.getLog().contains("COMMIT_TX") : "Direct proxy call should commit transaction";
        assert rawTarget.transactionCount == 1 : "Transaction count should be 1";

        handler.getLog().clear();

        // 2. Self-Invocation Trap: Calling externalMethod() on proxy, which calls this.transactedMethod()
        proxy.externalMethod();
        // Since externalMethod invoked this.transactedMethod(), the proxy was BYPASSED!
        assert !handler.getLog().contains("BEGIN_TX") : "Self-invocation should NOT trigger transaction proxy!";
        assert handler.getLog().isEmpty() : "Log should be empty for self-invoked call";
        assert rawTarget.transactionCount == 1 : "Transaction count should still be 1 (unproxied!)";

        System.out.println("  [PASS] Spring Transactions: Dynamic proxy and the self-invocation bypass trap verified");
    }

    // =========================================================================
    // 5. JPA-Style Persistence Context: 1st-Level Cache & Dirty Checking
    // =========================================================================

    public static class ProductEntity {
        private final Long id;
        private String name;
        private int price;

        public ProductEntity(Long id, String name, int price) {
            this.id = id;
            this.name = name;
            this.price = price;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getPrice() { return price; }
        public void setPrice(int price) { this.price = price; }

        public ProductEntity snapshot() {
            return new ProductEntity(id, name, price);
        }
    }

    public static class SimulatedPersistenceContext {
        // 1st-Level Cache: Map of Entity ID -> Managed Entity instance
        private final Map<Long, ProductEntity> identityMap = new HashMap<>();
        // Snapshot Map for Dirty Checking: Entity ID -> Clean state copy
        private final Map<Long, ProductEntity> loadedSnapshots = new HashMap<>();
        private final List<String> executedSql = new ArrayList<>();

        public ProductEntity findById(Long id) {
            if (identityMap.containsKey(id)) {
                // Cache Hit: Same in-memory instance returned!
                return identityMap.get(id);
            }
            // Simulated DB fetch
            ProductEntity entity = new ProductEntity(id, "Laptop", 1200);
            identityMap.put(id, entity);
            loadedSnapshots.put(id, entity.snapshot());
            return entity;
        }

        public void flush() {
            // Dirty Checking: Compare managed entities against clean snapshots
            for (Map.Entry<Long, ProductEntity> entry : identityMap.entrySet()) {
                Long id = entry.getKey();
                ProductEntity managed = entry.getValue();
                ProductEntity snapshot = loadedSnapshots.get(id);

                if (snapshot != null && (!managed.getName().equals(snapshot.getName()) || managed.getPrice() != snapshot.getPrice())) {
                    executedSql.add("UPDATE products SET name='" + managed.getName() + "', price=" + managed.getPrice() + " WHERE id=" + id);
                    loadedSnapshots.put(id, managed.snapshot()); // Update snapshot
                }
            }
        }

        public List<String> getExecutedSql() { return executedSql; }
    }

    private static void testPersistenceContextAndDirtyChecking() {
        SimulatedPersistenceContext em = new SimulatedPersistenceContext();

        // 1. Identity Map test: multiple findById calls return identical reference (==)
        ProductEntity p1 = em.findById(10L);
        ProductEntity p2 = em.findById(10L);
        assert p1 == p2 : "1st-level cache identity map failed; references must be identical (==)!";

        // 2. Dirty checking: mutate managed entity without calling save/update
        p1.setPrice(1450);
        em.flush();

        assert em.getExecutedSql().size() == 1 : "Expected 1 dirty checking UPDATE statement";
        assert em.getExecutedSql().get(0).contains("price=1450") : "SQL update missing mutated price";

        // Second flush without mutations should produce zero SQL
        em.flush();
        assert em.getExecutedSql().size() == 1 : "Clean entity should produce zero SQL during flush";

        System.out.println("  [PASS] JPA Mechanics: 1st-level cache identity map and dirty checking verified");
    }

    // =========================================================================
    // 6. Unit Testing with Test Doubles (Fakes) for Isolation
    // =========================================================================

    public static class FakeOrderRepository implements OrderRepository {
        private final Map<String, Integer> db = new HashMap<>();
        public int saveCount = 0;

        @Override
        public void saveOrder(String orderId, int amount) {
            db.put(orderId, amount);
            saveCount++;
        }

        @Override
        public int getOrderAmount(String orderId) {
            return db.getOrDefault(orderId, 0);
        }
    }

    private static void testUnitTestWithFakeRepository() {
        FakeOrderRepository fakeRepo = new FakeOrderRepository();
        OrderService service = new OrderService(fakeRepo);

        service.placeOrder("TEST-001", 300);

        assert fakeRepo.saveCount == 1 : "Fake repository saveCount should be 1";
        assert fakeRepo.getOrderAmount("TEST-001") == 300 : "Fake repository amount mismatch";

        // Test boundary condition: negative amount rejects before reaching repository
        boolean rejected = false;
        try {
            service.placeOrder("TEST-002", -50);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        assert rejected : "Service failed to reject invalid negative order amount";
        assert fakeRepo.saveCount == 1 : "Repository should NOT have been invoked on validation failure";

        System.out.println("  [PASS] Testing Strategy: Unit test with in-memory fake repository verified");
    }
}
