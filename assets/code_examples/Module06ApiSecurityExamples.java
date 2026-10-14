package assets.code_examples;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Module 06 - API Engineering & Security Verification Test Suite (Java 21 LTS).
 *
 * Demonstrates and verifies core API engineering and security mechanics:
 * 1. Request DTO Validation & RFC 7807/9457 Problem Details contract.
 * 2. Broken Object Level Authorization (BOLA/IDOR) prevention at the domain service layer.
 * 3. Authentication (401) vs Authorization (403) boundary decisions.
 * 4. Cryptographic Token Signature (HMAC-SHA256) and tamper detection.
 * 5. Idempotency Key state machine, atomic registration, and duplicate replay handling.
 * 6. Token Bucket Rate Limiter invariants and burst capacity enforcement.
 * 7. Keyset (Cursor) vs Offset pagination stability under concurrent writes.
 */
public class Module06ApiSecurityExamples {

    // =========================================================================
    // 1. DTO Validation & RFC 7807 / 9457 Problem Details Contract
    // =========================================================================

    public record CreateOrderRequest(String customerId, List<OrderItem> items, String currency) {
        public record OrderItem(String productId, int quantity, long unitPriceCents) {}

        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            if (customerId == null || customerId.isBlank()) {
                errors.add("customerId: must not be blank");
            }
            if (items == null || items.isEmpty()) {
                errors.add("items: must contain at least one order item");
            } else {
                for (int i = 0; i < items.size(); i++) {
                    OrderItem item = items.get(i);
                    if (item.productId == null || item.productId.isBlank()) {
                        errors.add("items[" + i + "].productId: must not be blank");
                    }
                    if (item.quantity <= 0) {
                        errors.add("items[" + i + "].quantity: must be greater than zero");
                    }
                    if (item.unitPriceCents < 0) {
                        errors.add("items[" + i + "].unitPriceCents: must not be negative");
                    }
                }
            }
            if (currency == null || !currency.matches("^[A-Z]{3}$")) {
                errors.add("currency: must be a valid 3-letter ISO code (e.g., USD, EUR)");
            }
            return errors;
        }
    }

    public record ProblemDetail(
            URI type,
            String title,
            int status,
            String detail,
            URI instance,
            Instant timestamp,
            Map<String, Object> extensions
    ) {
        public static ProblemDetail forValidationFailure(List<String> violations, String instancePath) {
            Map<String, Object> ext = new HashMap<>();
            ext.put("invalid_parameters", violations);
            ext.put("error_code", "VALIDATION_FAILED");
            return new ProblemDetail(
                    URI.create("https://api.example.com/errors/validation-failed"),
                    "Request Validation Failed",
                    400,
                    "The payload failed one or more schema validation constraints.",
                    URI.create(instancePath),
                    Instant.now(),
                    ext
            );
        }
    }

    public static void testRequestValidationAndProblemDetails() {
        System.out.println("  [TEST 1] Request Validation & RFC 7807 Problem Details Contract...");

        // Malformed request
        CreateOrderRequest invalidReq = new CreateOrderRequest(
                "",
                List.of(new CreateOrderRequest.OrderItem("", -5, -100)),
                "usd_invalid"
        );
        List<String> violations = invalidReq.validate();
        assert !violations.isEmpty() : "Validation should fail for invalid order request";
        assert violations.size() == 5 : "Expected 5 validation violations, found: " + violations.size();

        ProblemDetail problem = ProblemDetail.forValidationFailure(violations, "/api/v1/orders");
        assert problem.status() == 400 : "Expected HTTP 400 for validation failure";
        assert problem.title().equals("Request Validation Failed") : "Title mismatch";
        assert problem.extensions().containsKey("invalid_parameters") : "Must include invalid parameters";

        // Valid request
        CreateOrderRequest validReq = new CreateOrderRequest(
                "cust_12345",
                List.of(new CreateOrderRequest.OrderItem("prod_999", 2, 4999)),
                "USD"
        );
        assert validReq.validate().isEmpty() : "Valid request should produce zero violations";
        System.out.println("    -> Passed: RFC 7807 contract and field-level validation invariants verified.");
    }

    // =========================================================================
    // 2. Broken Object Level Authorization (BOLA/IDOR) Prevention
    // =========================================================================

    public record UserPrincipal(String userId, String tenantId, Set<String> roles) {}

    public record OrderRecord(String orderId, String ownerUserId, String tenantId, long totalAmountCents) {}

    public static class OrderSecurityService {
        private final Map<String, OrderRecord> orderStore = new ConcurrentHashMap<>();

        public void seedOrder(OrderRecord order) {
            orderStore.put(order.orderId(), order);
        }

        /**
         * Secure resource fetch: checks BOTH tenancy and resource ownership.
         * Throws SecurityException if user is authenticated but unauthorized for this object.
         */
        public OrderRecord getOrderSecure(String orderId, UserPrincipal principal) {
            OrderRecord order = orderStore.get(orderId);
            if (order == null) {
                return null; // 404 Not Found
            }

            // Tenant isolation boundary
            if (!order.tenantId().equals(principal.tenantId())) {
                // Return 404 to avoid leaking existence of cross-tenant resources
                return null;
            }

            // Object-level authorization check: user must own order OR hold tenant admin role
            boolean isOwner = order.ownerUserId().equals(principal.userId());
            boolean isAdmin = principal.roles().contains("TENANT_ADMIN");

            if (!isOwner && !isAdmin) {
                // Object Level Authorization failure (BOLA / IDOR prevented)
                throw new SecurityException("User " + principal.userId() + " is not authorized to access order " + orderId);
            }

            return order;
        }
    }

    public static void testObjectLevelAuthorizationBOLA() {
        System.out.println("  [TEST 2] Broken Object Level Authorization (BOLA/IDOR) Defense...");
        OrderSecurityService service = new OrderSecurityService();
        service.seedOrder(new OrderRecord("ord_alice_1", "user_alice", "tenant_alpha", 15000));
        service.seedOrder(new OrderRecord("ord_bob_1", "user_bob", "tenant_alpha", 25000));
        service.seedOrder(new OrderRecord("ord_charlie_1", "user_charlie", "tenant_beta", 35000));

        UserPrincipal alice = new UserPrincipal("user_alice", "tenant_alpha", Set.of("USER"));
        UserPrincipal bob = new UserPrincipal("user_bob", "tenant_alpha", Set.of("USER"));
        UserPrincipal adminAlpha = new UserPrincipal("user_admin", "tenant_alpha", Set.of("TENANT_ADMIN"));

        // Alice accessing her own order: Allowed
        OrderRecord aliceOrder = service.getOrderSecure("ord_alice_1", alice);
        assert aliceOrder != null && aliceOrder.orderId().equals("ord_alice_1") : "Alice should access her order";

        // Alice attempting to access Bob's order: BOLA attack blocked
        boolean bolaBlocked = false;
        try {
            service.getOrderSecure("ord_bob_1", alice);
        } catch (SecurityException e) {
            bolaBlocked = true;
        }
        assert bolaBlocked : "Alice must not be permitted to access Bob's order (BOLA check failed to trigger)";

        // Tenant Admin accessing Alice's order: Allowed by role policy
        OrderRecord adminAccess = service.getOrderSecure("ord_alice_1", adminAlpha);
        assert adminAccess != null : "Tenant Admin in same tenant should be authorized";

        // Alice attempting cross-tenant access to Charlie's order: Tenant isolation produces null (404)
        OrderRecord crossTenant = service.getOrderSecure("ord_charlie_1", alice);
        assert crossTenant == null : "Cross-tenant access must return null without leaking existence";

        System.out.println("    -> Passed: Object-level ownership and multi-tenant isolation enforced.");
    }

    // =========================================================================
    // 3. Authentication (401) vs Authorization (403) Boundary
    // =========================================================================

    public enum SecurityDecision {
        ALLOW(200),
        UNAUTHENTICATED(401),
        UNAUTHORIZED(403);

        final int httpStatus;
        SecurityDecision(int httpStatus) { this.httpStatus = httpStatus; }
    }

    public static class SecurityFilterSimulator {
        public SecurityDecision evaluateRequest(String authHeader, Set<String> userRoles, String requiredRole) {
            // Step 1: Authentication Boundary ("Who are you?")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return SecurityDecision.UNAUTHENTICATED; // 401: missing/invalid credentials
            }
            String token = authHeader.substring(7).trim();
            if (token.isBlank() || token.equals("expired_or_invalid")) {
                return SecurityDecision.UNAUTHENTICATED; // 401: credentials invalid/expired
            }

            // Step 2: Authorization Boundary ("What may you do?")
            if (requiredRole != null && !userRoles.contains(requiredRole)) {
                return SecurityDecision.UNAUTHORIZED; // 403: identity confirmed, but insufficient permissions
            }

            return SecurityDecision.ALLOW; // 200: authenticated and authorized
        }
    }

    public static void testAuthNVsAuthZDecision() {
        System.out.println("  [TEST 3] Authentication (401) vs Authorization (403) Boundary...");
        SecurityFilterSimulator sim = new SecurityFilterSimulator();

        // Anonymous request
        SecurityDecision d1 = sim.evaluateRequest(null, Set.of(), "ADMIN");
        assert d1 == SecurityDecision.UNAUTHENTICATED && d1.httpStatus == 401 : "Anonymous must yield 401";

        // Invalid token
        SecurityDecision d2 = sim.evaluateRequest("Bearer expired_or_invalid", Set.of(), "ADMIN");
        assert d2 == SecurityDecision.UNAUTHENTICATED && d2.httpStatus == 401 : "Invalid credentials must yield 401";

        // Authenticated user lacking ADMIN role
        SecurityDecision d3 = sim.evaluateRequest("Bearer valid_token_alice", Set.of("USER"), "ADMIN");
        assert d3 == SecurityDecision.UNAUTHORIZED && d3.httpStatus == 403 : "Missing required role must yield 403";

        // Authenticated user with ADMIN role
        SecurityDecision d4 = sim.evaluateRequest("Bearer valid_token_admin", Set.of("ADMIN", "USER"), "ADMIN");
        assert d4 == SecurityDecision.ALLOW && d4.httpStatus == 200 : "Valid credentials and role must yield 200";

        System.out.println("    -> Passed: 401 Unauthorized vs 403 Forbidden strictly distinguished.");
    }

    // =========================================================================
    // 4. Cryptographic Token Signature (HMAC-SHA256) & Tamper Detection
    // =========================================================================

    public static class JwtHmacVerifier {
        private final byte[] secretKey;

        public JwtHmacVerifier(String secret) {
            this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        }

        public String sign(String headerJson, String payloadJson) {
            String encodedHeader = base64UrlEncode(headerJson);
            String encodedPayload = base64UrlEncode(payloadJson);
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = hmacSha256(signingInput);
            return signingInput + "." + signature;
        }

        public boolean verify(String jwt) {
            String[] parts = jwt.split("\\.");
            if (parts.length != 3) return false;
            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = hmacSha256(signingInput);
            // Constant-time comparison to prevent timing attacks
            return MessageDigestEq(parts[2], expectedSignature);
        }

        private String hmacSha256(String data) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
                byte[] sigBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }
        }

        private static String base64UrlEncode(String src) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(src.getBytes(StandardCharsets.UTF_8));
        }

        private static boolean MessageDigestEq(String a, String b) {
            byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
            byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
            if (aBytes.length != bBytes.length) return false;
            int result = 0;
            for (int i = 0; i < aBytes.length; i++) {
                result |= aBytes[i] ^ bBytes[i];
            }
            return result == 0;
        }
    }

    public static void testCryptographicTokenSignature() {
        System.out.println("  [TEST 4] Cryptographic Token Signature & Tamper Detection...");
        JwtHmacVerifier verifier = new JwtHmacVerifier("super_secret_signing_key_at_least_256_bits_long!");

        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"user_123\",\"role\":\"USER\",\"exp\":1999999999}";
        String validJwt = verifier.sign(header, payload);

        assert verifier.verify(validJwt) : "Legitimate signed token must verify successfully";

        // Tamper test: Attacker alters payload to gain ADMIN role without valid signing key
        String tamperedPayload = "{\"sub\":\"user_123\",\"role\":\"ADMIN\",\"exp\":1999999999}";
        String[] parts = validJwt.split("\\.");
        String tamperedJwt = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assert !verifier.verify(tamperedJwt) : "Tampered token signature verification must fail!";
        System.out.println("    -> Passed: Cryptographic HMAC signature correctly rejects tampered claims.");
    }

    // =========================================================================
    // 5. Idempotency Key State Machine & Duplicate Replay Handling
    // =========================================================================

    public enum IdempotencyStatus { IN_FLIGHT, COMPLETED, RECONCILIATION_REQUIRED }

    public record IdempotencyRecord(
            String idempotencyKey,
            String requestHash,
            IdempotencyStatus status,
            int statusCode,
            String responseBody,
            Instant createdAt
    ) {}

    public static class IdempotencyManager {
        private final ConcurrentMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();

        public record ExecutionResult(int httpStatus, String responseBody, boolean replayed) {}

        /**
         * Atomic execution with idempotency key.
         */
        public ExecutionResult execute(String key, String requestPayload, Callable<ExecutionResult> businessOperation) {
            return execute(key, requestPayload, false, businessOperation);
        }

        public ExecutionResult execute(String key, String requestPayload, boolean hasExternalSideEffects, Callable<ExecutionResult> businessOperation) {
            String payloadHash = Integer.toHexString(requestPayload.hashCode());

            // Step 1: Atomic reservation of key
            IdempotencyRecord initialRecord = new IdempotencyRecord(
                    key, payloadHash, IdempotencyStatus.IN_FLIGHT, 0, null, Instant.now()
            );

            IdempotencyRecord existing = store.putIfAbsent(key, initialRecord);

            if (existing != null) {
                // Key was already seen
                if (!existing.requestHash().equals(payloadHash)) {
                    // Same key used with different payload: 422 Unprocessable Content or 400 Bad Request
                    return new ExecutionResult(422, "{\"error\":\"IDEMPOTENCY_KEY_PAYLOAD_MISMATCH\"}", false);
                }

                if (existing.status() == IdempotencyStatus.IN_FLIGHT) {
                    // Concurrent duplicate request still processing: 409 Conflict with Retry-After advice
                    return new ExecutionResult(409, "{\"error\":\"OPERATION_ALREADY_IN_FLIGHT\"}", false);
                }

                if (existing.status() == IdempotencyStatus.RECONCILIATION_REQUIRED) {
                    // Ambiguous failure after external side-effect: do not delete or blindly re-execute!
                    return new ExecutionResult(409, "{\"error\":\"TRANSACTION_PENDING_RECONCILIATION\"}", false);
                }

                if (existing.status() == IdempotencyStatus.COMPLETED) {
                    // Safe replay: return previously cached response
                    return new ExecutionResult(existing.statusCode(), existing.responseBody(), true);
                }
            }

            // Step 2: Execute operation if we won the reservation
            try {
                ExecutionResult result = businessOperation.call();
                IdempotencyRecord completedRecord = new IdempotencyRecord(
                        key, payloadHash, IdempotencyStatus.COMPLETED, result.httpStatus(), result.responseBody(), Instant.now()
                );
                store.put(key, completedRecord);
                return result;
            } catch (Exception e) {
                if (hasExternalSideEffects) {
                    // Ambiguous external side effect occurred: mark as RECONCILIATION_REQUIRED.
                    // DO NOT delete the in-flight key, as an immediate retry would double-charge or duplicate external mutations!
                    store.put(key, new IdempotencyRecord(
                            key, payloadHash, IdempotencyStatus.RECONCILIATION_REQUIRED, 500, "{\"error\":\"DOWNSTREAM_UNCERTAINTY\"}", Instant.now()
                    ));
                } else {
                    // On local database abort before external RPCs, removing reservation allows safe client retry
                    store.remove(key);
                }
                throw new RuntimeException("Business operation failed", e);
            }
        }
    }

    public static void testIdempotencyKeyStateMachine() {
        System.out.println("  [TEST 5] Idempotency Key State Machine & Duplicate Replays...");
        IdempotencyManager manager = new IdempotencyManager();
        AtomicInteger paymentExecutionCount = new AtomicInteger(0);

        Callable<IdempotencyManager.ExecutionResult> paymentOp = () -> {
            int exec = paymentExecutionCount.incrementAndGet();
            return new IdempotencyManager.ExecutionResult(201, "{\"payment_id\":\"pay_" + exec + "\",\"status\":\"CHARGED\"}", false);
        };

        String key = "idem_key_uuid_789";
        String payload = "{\"amount\":5000,\"currency\":\"USD\"}";

        // First call: Executes business logic
        IdempotencyManager.ExecutionResult res1 = manager.execute(key, payload, paymentOp);
        assert res1.httpStatus() == 201 : "First request should return 201 Created";
        assert !res1.replayed() : "First request is not replayed";
        assert paymentExecutionCount.get() == 1 : "Payment executed exactly once";

        // Second call (Network retry): Returns cached response without re-executing payment!
        IdempotencyManager.ExecutionResult res2 = manager.execute(key, payload, paymentOp);
        assert res2.httpStatus() == 201 : "Replayed request returns original status";
        assert res2.replayed() : "Must indicate response was replayed from idempotency store";
        assert res2.responseBody().equals(res1.responseBody()) : "Replayed response body must match original";
        assert paymentExecutionCount.get() == 1 : "Business logic was NOT executed a second time!";

        // Third call with SAME key but DIFFERENT payload: Mismatch error
        IdempotencyManager.ExecutionResult res3 = manager.execute(key, "{\"amount\":9999,\"currency\":\"USD\"}", paymentOp);
        assert res3.httpStatus() == 422 : "Payload mismatch for existing key must yield 422";

        // Fourth call: Ambiguous external side effect failure must transition to RECONCILIATION_REQUIRED
        String externalKey = "idem_key_uuid_ext_456";
        AtomicInteger externalChargeCount = new AtomicInteger(0);
        Callable<IdempotencyManager.ExecutionResult> failingExternalOp = () -> {
            externalChargeCount.incrementAndGet();
            throw new RuntimeException("Downstream payment gateway socket read timeout after dispatch!");
        };

        boolean threw = false;
        try {
            manager.execute(externalKey, payload, true, failingExternalOp);
        } catch (RuntimeException e) {
            threw = true;
        }
        assert threw : "External operation failure should propagate";
        assert externalChargeCount.get() == 1 : "Gateway was called once";

        // Subsequent client retry must NOT delete key or re-execute external gateway!
        IdempotencyManager.ExecutionResult resRetry = manager.execute(externalKey, payload, true, failingExternalOp);
        assert resRetry.httpStatus() == 409 : "Ambiguous failure requires manual reconciliation before retry";
        assert resRetry.responseBody().contains("TRANSACTION_PENDING_RECONCILIATION");
        assert externalChargeCount.get() == 1 : "Downstream external mutation must NEVER be re-invoked automatically!";

        System.out.println("    -> Passed: Idempotency state machine guarantees single execution, safe replays, and durable reconciliation locks.");
    }

    // =========================================================================
    // 6. Token Bucket Rate Limiter
    // =========================================================================

    public static class TokenBucketRateLimiter {
        private final long capacity;
        private final double refillRatePerSecond;
        private double availableTokens;
        private long lastRefillTimestampNanos;

        public TokenBucketRateLimiter(long capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.availableTokens = capacity;
            this.lastRefillTimestampNanos = System.nanoTime();
        }

        public synchronized boolean tryAcquire(long tokens) {
            refill();
            if (availableTokens >= tokens) {
                availableTokens -= tokens;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillTimestampNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                availableTokens = Math.min(capacity, availableTokens + (elapsedSeconds * refillRatePerSecond));
                lastRefillTimestampNanos = now;
            }
        }
    }

    public static void testTokenBucketRateLimiter() {
        System.out.println("  [TEST 6] Token Bucket Rate Limiter Invariants & Burst Capacity...");
        // Capacity: 5 tokens, Refill rate: 2 tokens per second
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 2.0);

        // Immediate burst of 5 tokens should succeed
        for (int i = 0; i < 5; i++) {
            assert limiter.tryAcquire(1) : "Burst token " + (i + 1) + " should be acquired";
        }

        // 6th token should be rejected (burst capacity exhausted)
        assert !limiter.tryAcquire(1) : "Token should be rejected when bucket is empty";

        System.out.println("    -> Passed: Token bucket burst ceiling and depletion invariants verified.");
    }

    // =========================================================================
    // 7. Keyset (Cursor) vs Offset Pagination Stability
    // =========================================================================

    public record AuditEvent(long id, String eventName, long createdAtMillis) {}

    public static void testKeysetVsOffsetPaginationStability() {
        System.out.println("  [TEST 7] Keyset (Cursor) vs Offset Pagination Stability under Concurrent Writes...");

        // Existing dataset: IDs 1..6 ordered by ID ascending
        List<AuditEvent> database = new ArrayList<>(List.of(
                new AuditEvent(1, "LOGIN", 1000),
                new AuditEvent(2, "UPDATE_PROFILE", 2000),
                new AuditEvent(3, "CHECKOUT", 3000),
                new AuditEvent(4, "LOGOUT", 4000),
                new AuditEvent(5, "LOGIN", 5000),
                new AuditEvent(6, "DELETE_ACCOUNT", 6000)
        ));

        int pageSize = 2;

        // Client reads Page 1 using Offset: items [1, 2]
        List<AuditEvent> page1Offset = database.subList(0, 2);
        assert page1Offset.get(0).id() == 1 && page1Offset.get(1).id() == 2;

        // Concurrent mutation: Row 1 is deleted or a new row is inserted at index 0
        database.add(0, new AuditEvent(0, "SYSTEM_INIT", 500));

        // Client reads Page 2 using naive Offset (offset=2, limit=2):
        // In the modified list [0, 1, 2, 3, 4, 5, 6], offset 2 returns [2, 3]!
        // Item 2 was DUPLICATED because of offset drift!
        List<AuditEvent> page2OffsetShifted = database.subList(2, 4);
        assert page2OffsetShifted.get(0).id() == 2 : "Offset drift caused item 2 to appear in both Page 1 and Page 2!";

        // Keyset Pagination: Client requests WHERE id > 2 ORDER BY id ASC LIMIT 2
        long lastSeenId = 2; // Opaque cursor extracted from last item of Page 1
        List<AuditEvent> page2Keyset = database.stream()
                .filter(e -> e.id() > lastSeenId)
                .sorted(Comparator.comparingLong(AuditEvent::id))
                .limit(pageSize)
                .toList();

        // Keyset returns [3, 4] with zero duplicates regardless of prepended items!
        assert page2Keyset.size() == 2;
        assert page2Keyset.get(0).id() == 3 : "Keyset pagination is immune to offset drift!";
        assert page2Keyset.get(1).id() == 4;

        System.out.println("    -> Passed: Keyset pagination guarantees deterministic ordering without duplicate shifts.");
    }

    // =========================================================================
    // Master Runner
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  MODULE 06 API ENGINEERING & SECURITY TEST SUITE (JAVA 21 LTS) ");
        System.out.println("=================================================================");

        testRequestValidationAndProblemDetails();
        testObjectLevelAuthorizationBOLA();
        testAuthNVsAuthZDecision();
        testCryptographicTokenSignature();
        testIdempotencyKeyStateMachine();
        testTokenBucketRateLimiter();
        testKeysetVsOffsetPaginationStability();

        System.out.println("\nAll 7 Java API Engineering & Security Test Suites Passed Successfully!");
    }
}
