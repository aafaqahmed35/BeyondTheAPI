# Module 06: API Engineering and Security

## 1. HTTP Semantics & Robust REST Contracts

HTTP is the foundational transport protocol of modern web APIs. Compliance with RFC 9110 guarantees predictable caching, retries, and proxy routing.

### 1.1 Method Safety & Idempotency
- **Safe Methods (GET, HEAD, OPTIONS):** Do not mutate resource state. Cachable by default.
- **Idempotent Methods (GET, HEAD, PUT, DELETE, OPTIONS):** Executing the identical request $N$ times results in the identical server resource state as executing it once:
  $$f(f(x)) = f(x)$$
- **Non-Idempotent Methods (POST, PATCH):** Executing multiple times produces side effects (e.g., duplicate charges, duplicate order rows).

### 1.2 HTTP Status Code Matrix
- **200 OK:** Standard success response with payload.
- **201 Created:** Resource created successfully. Must include `Location` header pointing to the new resource URL.
- **204 No Content:** Success with zero response body (common for `DELETE` operations).
- **301 vs. 308 (Permanent Redirects):** 301 permits clients to mutate POST to GET; 308 strictly preserves the original HTTP method.
- **400 Bad Request:** Malformed request syntax (invalid JSON, parse errors).
- **401 Unauthorized:** Missing or invalid authentication credentials (unauthenticated).
- **403 Forbidden:** Authenticated user lacks authorization permissions for this resource.
- **404 Not Found:** Resource does not exist, or hidden to prevent enumeration.
- **409 Conflict:** State conflict (e.g., unique constraint violation, optimistic lock mismatch).
- **422 Unprocessable Entity:** Syntactically valid JSON, but violates business validation rules.
- **429 Too Many Requests:** Client exceeded rate limit quotas.
- **502 Bad Gateway:** Upstream proxy/backend returned an invalid response.
- **503 Service Unavailable:** Server overloaded or undergoing maintenance.
- **504 Gateway Timeout:** Upstream dependency failed to respond within gateway timeout.

### 1.3 RFC 9457 Problem Details Standard
Production APIs must standardize error payloads using the RFC 9457 specification (`Content-Type: application/problem+json`):
```json
{
  "type": "https://api.example.com/errors/insufficient-funds",
  "title": "Insufficient Funds",
  "status": 422,
  "detail": "Account balance of $12.50 is insufficient for transaction of $50.00.",
  "instance": "/accounts/acc_123/transfers/tx_987",
  "invalidParams": [
    { "name": "amount", "reason": "Exceeds available balance" }
  ]
}
```

---

## 2. Authentication, Authorization & Identity

### 2.1 Authn vs. Authz & The BOLA / IDOR Vulnerability
- **Authentication (Authn):** Verifying the identity of the actor ("Who are you?").
- **Authorization (Authz):** Verifying permissions of the authenticated actor ("Are you allowed to execute this operation on this specific resource?").
- **Broken Object Level Authorization (BOLA / IDOR):** The #1 API vulnerability (OWASP Top 10). Occurs when an API endpoint uses client-supplied IDs without verifying ownership:
  ```java
  // FATAL VULNERABILITY: Any authenticated user can read any invoice by ID!
  @GetMapping("/invoices/{id}")
  public Invoice getInvoice(@PathVariable UUID id) {
      return invoiceRepository.findById(id).orElseThrow();
  }

  // SECURE IMPLEMENTATION: Scope query strictly to authenticated principal
  @GetMapping("/invoices/{id}")
  public Invoice getInvoice(@PathVariable UUID id, @AuthenticationPrincipal User user) {
      return invoiceRepository.findByIdAndTenantId(id, user.getTenantId())
          .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
  }
  ```

### 2.2 Password Storage Cryptography
Never store plain-text passwords or use general-purpose hash algorithms (MD5, SHA-256) which execute in nanoseconds, enabling massive GPU dictionary attacks.
- Use **memory-hard, adaptive key-derivation functions**:
  1. **Argon2id:** Winner of Password Hashing Competition. Highly resistant to GPU/ASIC cracking.
  2. **bcrypt:** Work factor $12+$ (takes $\\sim 250$ms per verification).
  3. **PBKDF2:** NIST-approved legacy standard with minimum 600,000 iterations.

---

## 3. Session, Cookie & Token Security

### 3.1 Cookie Security Invariants
When transmitting session IDs or authentication tokens via HTTP cookies, all 4 security flags are mandatory:
1. `HttpOnly`: Completely blocks JavaScript `document.cookie` access, preventing token theft via Cross-Site Scripting (XSS).
2. `Secure`: Instructs browser to transmit cookie exclusively over encrypted HTTPS connections.
3. `SameSite=Strict` (or `Lax`): Instructs browser never to attach cookie to cross-site requests, mitigating Cross-Site Request Forgery (CSRF).
4. `__Host-` Prefix: Requires cookie to be set with `Secure`, path `/`, and prohibits sub-domain overwriting.

### 3.2 JWT Architecture & 5-Point Cryptographic Verification
A JSON Web Token consists of three base64url-encoded parts: `Header.Payload.Signature`.
```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c3JfMTIzIiwiZXhwIjoxNzg5MDAwMDAwfQ.SIGNATURE
```
**Mandatory 5-Point Verification Invariant:**
1. Verify cryptographic signature using public key / secret.
2. Verify algorithm header matches expected type (strictly reject `"alg": "none"` and enforce asymmetric RSA/ECDSA).
3. Verify expiration timestamp (`exp > Instant.now()`).
4. Verify issuer (`iss == "https://auth.example.com"`).
5. Verify audience (`aud == "api://my-backend"`).

### 3.3 OAuth 2.0 PKCE & OpenID Connect (OIDC)
- **Proof Key for Code Exchange (PKCE):** Mandatory for Single Page Apps (SPA) and mobile clients that cannot securely store a `client_secret`. Client generates a high-entropy random string `code_verifier`, hashes it with SHA-256 to create `code_challenge`, and submits it during initial authorization. During token exchange, it submits the raw `code_verifier`, proving ownership without a static secret.
- **OpenID Connect (OIDC):** An identity layer on top of OAuth 2.0. OAuth 2.0 returns an `access_token` (for API authorization); OIDC returns a signed `id_token` (JWT containing user profile claims).

---

## 4. Web Attack Surfaces & Security Defenses

### 4.1 CORS vs. CSRF vs. XSS vs. SSRF

| Vulnerability | Attack Vector | Boundary Breached | Primary Production Defense |
| :--- | :--- | :--- | :--- |
| **CORS Misconfig** | Malicious site uses browser credentials to read responses. | Cross-Origin Resource Sharing | Never return `Access-Control-Allow-Origin: *` with credentials. Explicitly whitelist trusted origins. |
| **CSRF** | Malicious site tricks authenticated browser into issuing state-mutating requests. | Ambient cookie authentication | `SameSite=Strict` cookies and Synchronizer Token Pattern (anti-CSRF header). |
| **XSS** | Attacker injects malicious JS executed in user's browser. | Contextual DOM trust | Context-aware HTML entity encoding, Content Security Policy (`CSP`), `HttpOnly` cookies. |
| **SSRF** | Attacker tricks server into requesting internal VPC resources. | Network trust perimeter | Whitelist target domains; resolve DNS and block RFC 1918 IPs (`10.0.0.0/8`, `169.254.169.254`). |

---

## 5. Rate Limiting, Idempotency & Distributed Operations

### 5.1 Distributed Token Bucket via Redis Lua Script
Rate limiting prevents denial-of-service and brute-force attacks. The **Token Bucket** algorithm allows burst capacity while maintaining an average rate:
```lua
-- Redis Lua Script: Atomic Token Bucket (Keys: [rate_key], Args: [capacity, refill_rate, now, requested])
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local data = redis.call("HMGET", key, "tokens", "last_updated")
local tokens = tonumber(data[1]) or capacity
local last_updated = tonumber(data[2]) or now

-- Calculate refilled tokens based on elapsed time
local elapsed = math.max(0, now - last_updated)
tokens = math.min(capacity, tokens + elapsed * refill_rate)

if tokens >= requested then
    tokens = tokens - requested
    redis.call("HMSET", key, "tokens", tokens, "last_updated", now)
    redis.call("EXPIRE", key, math.ceil(capacity / refill_rate))
    return 1 -- Allowed
else
    return 0 -- Rejected (HTTP 429)
end
```

### 5.2 Distributed Idempotency Key Protocol
For non-idempotent operations (POST /payments), clients pass a unique UUID v4 header: `Idempotency-Key`.
```
Client                      API Gateway / Server                   Distributed Store (Redis)
  |                                   |                                        |
  | POST /charges                     |                                        |
  | Idempotency-Key: uuid-123         |                                        |
  |---------------------------------->| SET charge:uuid-123 PENDING NX EX 120  |
  |                                   |--------------------------------------->|
  |                                   |<--- OK (Lock Acquired) ----------------|
  |                                   |                                        |
  |                                   | [Execute Payment via Bank Gateway]     |
  |                                   |                                        |
  |                                   | HSET charge:uuid-123                   |
  |                                   |      status COMPLETED body {...}       |
  |                                   |--------------------------------------->|
  | HTTP 201 Created                  |                                        |
  |<----------------------------------|                                        |
  |                                   |                                        |
  | [Client Retries due to timeout]   |                                        |
  | POST /charges (uuid-123)          |                                        |
  |---------------------------------->| GET charge:uuid-123                    |
  |                                   |--------------------------------------->|
  |                                   |<--- COMPLETED, body {...} -------------|
  | HTTP 201 Created (Cached)         |                                        |
  |<----------------------------------| [Zero duplicate charges executed!]     |
```

### 5.3 Pagination: Offset vs. Keyset (Cursor)
- **Offset Pagination (`LIMIT 20 OFFSET 50000`):** Relational engine must scan and discard 50,000 rows before returning 20. Exhibits $O(N)$ query degradation and suffers from **Page Drift** (new rows inserted shift offsets, showing duplicate rows across pages).
- **Keyset / Cursor Pagination:** Uses index seek on an ordered unique column:
  ```sql
  SELECT id, title, created_at FROM articles 
  WHERE created_at < :last_seen_created_at 
  ORDER BY created_at DESC, id DESC LIMIT 20;
  ```
  Executes in constant $O(1)$ time regardless of page depth and guarantees stability during concurrent insertions.

---

## 6. Failure Modes & Security Vulnerabilities

| Vulnerability / Failure | Attack / Root Cause | Impact | Preventive Architecture |
| :--- | :--- | :--- | :--- |
| **BOLA / IDOR** | Attacker alters resource ID in URL to another tenant's ID. | Unauthorized cross-tenant data exfiltration. | Always filter database queries by the authenticated user's session tenant ID. |
| **Mass Assignment** | Attacker injects `"role": "ADMIN"` into signup JSON. | Privilege escalation. | Bind HTTP requests strictly to DTOs; never map directly to ORM entities. |
| **Double Spend / Race** | Rapid concurrent clicks submit identical payment requests. | Duplicate bank charges. | Enforce database unique constraint on `idempotency_key` and atomic Redis locks. |
| **SSRF** | Webhook endpoint fetches internal URL `http://169.254.169.254`. | AWS IAM metadata keys stolen. | Parse target URLs; resolve DNS and drop private subnets (`10.0.0.0/8`, `169.254.0.0/16`). |
| **Missing Rate Limit** | Login endpoint spammed with millions of credential guesses. | Credential stuffing attack. | Enforce Token Bucket rate limiting keyed by IP and username. |
---

## 7. High-Bar API Engineering & Security Scenarios

### Scenario 1: Defending Against BOLA (Broken Object Level Authorization)
- **Interview Question:** "A penetration test reports that a malicious tenant can view confidential invoices by modifying the invoice ID in `GET /api/v1/invoices/999`. How do you structurally fix this across the codebase?"
- **Deep Technical Answer:**
  1. BOLA occurs when an API trusts client-provided resource identifiers without validating ownership against the authenticated caller's security context.
  2. **Structural Remediation:**
     - Enforce **Tenant Scoping** at the data access tier: Every entity query must include `WHERE tenant_id = :currentUserTenantId`.
     - Implement Spring Security Method Security:
       ```java
       @PreAuthorize("@invoiceSecurity.isOwner(authentication, #invoiceId)")
       public Invoice getInvoice(UUID invoiceId) { ... }
       ```
     - Never expose raw sequential database primary keys (`id = 1, 2, 3`). Use high-entropy UUID v4 or ULID to eliminate sequential enumeration.

### Scenario 2: Reliable Payment Processing Over Lossy Networks
- **Interview Question:** "A mobile client executes a credit card payment via `POST /payments`. The cellular connection drops before the client receives a response. How do you design the API to prevent duplicate charges upon retry?"
- **Deep Technical Answer:**
  1. Client generates a unique `Idempotency-Key: uuid-v4` header before initiating the request.
  2. **Gateway Atomic Lock:** The API gateway executes an atomic Redis lock: `SET payment:uuid-v4 PENDING NX EX 120`.
     - If lock fails and state is `PENDING`: Return HTTP 409 Conflict ("Transaction in progress, retry shortly").
     - If lock fails and state is `COMPLETED`: Return cached HTTP 201 response directly from Redis/DB without re-executing payment.
  3. **Execution:** The server invokes the payment processor, stores the transaction in the database, and updates Redis to `COMPLETED` with cached response payload. Zero duplicate charges occur.

### Scenario 3: Cookie Storage vs. `localStorage` for Authentication Tokens
- **Interview Question:** "Should single-page application JWT tokens be stored in browser `localStorage` or HTTP cookies?"
- **Deep Technical Answer:**
  1. **`localStorage` Hazard:** Accessible to any JavaScript executing in the origin. A single Cross-Site Scripting (XSS) vulnerability (e.g., via a compromised third-party npm package) allows an attacker to execute `localStorage.getItem('token')` and exfiltrate credentials.
  2. **Secure Cookie Architecture:** Store tokens in cookies configured with:
     - `HttpOnly`: Completely blocks JavaScript access, neutralizing XSS token theft.
     - `Secure`: Enforces transmission exclusively over encrypted HTTPS.
     - `SameSite=Strict`: Blocks cross-site transmission, mitigating CSRF attacks.
     - Enforce Anti-CSRF Synchronizer Tokens for state-mutating requests (`POST`, `PUT`, `DELETE`).
---

## 8. API Versioning Strategies & Webhook Architecture

### 8.1 API Versioning Trade-Off Matrix

| Strategy | Syntax Example | Caching & CDN | Tooling Support | Architectural Assessment |
| :--- | :--- | :--- | :--- | :--- |
| **URI Path** | `/api/v1/orders` | Trivial; URLs are distinct keys. | Excellent; standard in OpenAPI/Swagger. | Recommended industry default. Explicit, readable, highly cacheable. |
| **Query Parameter** | `/api/orders?v=1` | Requires CDN cache key parameter inclusion. | Moderate; easy to omit. | Acceptable for exploratory prototypes; poor for strict contract gating. |
| **Custom Header** | `X-API-Version: 1` | Requires `Vary: X-API-Version` on proxies. | Moderate; breaks browser URL copy-pasting. | Clean URLs; complex intermediate proxy caching configuration. |
| **Accept Header (Content Neg.)**| `Accept: application/vnd.app.v1+json` | Requires `Vary: Accept` header caching. | Difficult; requires complex gateway resolvers. | Strictly conforms to REST HATEOAS; steep client developer friction. |

### 8.2 Production Webhook Delivery Architecture
Webhooks allow an API to push asynchronous notifications to client servers when domain events occur (e.g., Stripe payment succeeded):
```
[Billing Service] ---> [Webhook Dispatch Worker]
                              |
                              +---> Computes HMAC-SHA256 signature
                              |     Header: `X-Signature: t=178900,v1=9a8b...`
                              |
                              v
                  [Client External Webhook URL]
                              |
                     (Client Verifies HMAC)
                              v
                  [Client Returns HTTP 200 OK]
```
- **HMAC-SHA256 Signature Verification:** Prevent tampering by computing a keyed hash of the timestamp and raw request body using a shared secret:
  $$\text{Signature} = \text{HMAC-SHA256}(\text{Secret}, \text{timestamp} + "." + \text{raw\_body})$$
- **Replay Attack Defense:** Include a timestamp `t` in the signature header. The client rejects any webhook where $|t - \text{now}| > 300\text{ seconds}$.
