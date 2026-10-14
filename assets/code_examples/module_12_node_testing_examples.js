/**
 * Module 12 — Node.js Testing & Debugging Verification Suite.
 *
 * Implements deterministic verification of core backend testing patterns in Node.js (v24 LTS):
 * 1. Asynchronous Boundary Testing: Promise rejections, timeout handling, and async retry logic
 * 2. Idempotent Message Consumer: Deduplication & exactly-once side-effect execution under duplicate delivery
 * 3. API Contract & Validation Testing: RFC 7807 Problem Details schema, status codes, and input bounds
 * 4. Resource Leak & Event Listener Cleanup: Deterministic listener disposal preventing memory leaks
 */

import assert from 'node:assert/strict';

// =============================================================================
// 1. ASYNCHRONOUS BOUNDARY & RETRY TESTING
// =============================================================================

export async function fetchWithRetry(fetcher, maxRetries = 3, initialDelayMs = 10) {
  let attempt = 0;
  let delay = initialDelayMs;

  while (attempt < maxRetries) {
    try {
      attempt++;
      return await fetcher(attempt);
    } catch (err) {
      if (attempt >= maxRetries) {
        throw new Error(`Max retries reached (${maxRetries}). Last error: ${err.message}`);
      }
      // Exponential backoff
      await new Promise(resolve => setTimeout(resolve, delay));
      delay *= 2;
    }
  }
}

// =============================================================================
// 2. IDEMPOTENT MESSAGE CONSUMER
// =============================================================================

export class IdempotentMessageConsumer {
  constructor() {
    this.processedMessageIds = new Set();
    this.sideEffectExecutionCount = 0;
    this.resultsStore = new Map();
  }

  async processMessage(message) {
    const { messageId, payload } = message;

    // Idempotency check (Inbox pattern)
    if (this.processedMessageIds.has(messageId)) {
      return {
        status: 'DUPLICATE_ACK',
        result: this.resultsStore.get(messageId),
        executionCount: this.sideEffectExecutionCount,
      };
    }

    // Execute side-effect
    this.sideEffectExecutionCount++;
    const result = {
      orderId: payload.orderId,
      amount: payload.amount,
      processedAt: new Date().toISOString(),
    };

    this.resultsStore.set(messageId, result);
    this.processedMessageIds.add(messageId);

    return {
      status: 'PROCESSED',
      result,
      executionCount: this.sideEffectExecutionCount,
    };
  }
}

// =============================================================================
// 3. API CONTRACT & VALIDATION TESTING (RFC 7807 PROBLEM DETAILS)
// =============================================================================

export function validateCreateOrderRequest(body, authHeader) {
  // 1. Auth check
  if (!authHeader) {
    return {
      statusCode: 401,
      body: {
        type: 'https://api.example.com/errors/unauthorized',
        title: 'Unauthorized',
        status: 401,
        detail: 'Authentication token is missing.',
      },
    };
  }

  if (authHeader === 'Bearer role:read_only') {
    return {
      statusCode: 403,
      body: {
        type: 'https://api.example.com/errors/forbidden',
        title: 'Forbidden',
        status: 403,
        detail: 'Caller lacks orders:write permission.',
      },
    };
  }

  // 2. Schema validation
  const errors = [];
  if (!body.customerId || typeof body.customerId !== 'string') {
    errors.push('customerId is required and must be a string.');
  }
  if (typeof body.amount !== 'number' || body.amount <= 0) {
    errors.push('amount must be a positive number greater than 0.');
  }

  if (errors.length > 0) {
    return {
      statusCode: 422, // Unprocessable Entity
      body: {
        type: 'https://api.example.com/errors/validation-failed',
        title: 'Unprocessable Entity',
        status: 422,
        detail: 'Request body failed validation constraints.',
        invalidParams: errors,
      },
    };
  }

  return {
    statusCode: 201,
    body: {
      orderId: 'ord_node_99',
      customerId: body.customerId,
      amount: body.amount,
      status: 'CREATED',
    },
  };
}

// =============================================================================
// 4. RESOURCE LEAK & EVENT LISTENER DISPOSAL
// =============================================================================

import { EventEmitter } from 'node:events';

export class OrderEventStream {
  constructor() {
    this.emitter = new EventEmitter();
  }

  subscribe(event, handler) {
    this.emitter.on(event, handler);
    // Return an unsubscribe disposal function
    return () => {
      this.emitter.off(event, handler);
    };
  }

  publish(event, data) {
    this.emitter.emit(event, data);
  }

  listenerCount(event) {
    return this.emitter.listenerCount(event);
  }
}

// =============================================================================
// TEST SUITE RUNNER
// =============================================================================

async function runAllTests() {
  console.log('=================================================================');
  console.log(' MODULE 12: NODE.JS TESTING & DEBUGGING VERIFICATION SUITE       ');
  console.log('=================================================================\n');

  // Test 1: Asynchronous Boundary & Retry Testing
  {
    process.stdout.write('  [TEST 1] Asynchronous Boundary & Retry with Exponential Backoff... ');
    let callCount = 0;
    const flakeyService = async (attempt) => {
      callCount++;
      if (attempt < 3) {
        throw new Error(`Transient failure on attempt ${attempt}`);
      }
      return { success: true, attempts: attempt };
    };

    const result = await fetchWithRetry(flakeyService, 3, 5);
    assert.equal(result.success, true);
    assert.equal(result.attempts, 3);
    assert.equal(callCount, 3);

    // Test terminal failure
    let alwaysFailsCount = 0;
    const alwaysFails = async () => {
      alwaysFailsCount++;
      throw new Error('Fatal database outage');
    };

    await assert.rejects(
      async () => fetchWithRetry(alwaysFails, 2, 5),
      /Max retries reached \(2\)\. Last error: Fatal database outage/
    );
    assert.equal(alwaysFailsCount, 2);

    console.log('Passed: Async retry, backoff, and rejection handling verified.');
  }

  // Test 2: Idempotent Message Consumer
  {
    process.stdout.write('  [TEST 2] Idempotent Message Consumer (Duplicate Delivery Safety)... ');
    const consumer = new IdempotentMessageConsumer();
    const message = {
      messageId: 'msg_uuid_12345',
      payload: { orderId: 'ord_101', amount: 99.95 },
    };

    // Send the identical message 3 times concurrently
    const [res1, res2, res3] = await Promise.all([
      consumer.processMessage(message),
      consumer.processMessage(message),
      consumer.processMessage(message),
    ]);

    // Side effect must only execute once
    assert.equal(consumer.sideEffectExecutionCount, 1, 'Side effect executed more than once!');

    // Exactly one call should be PROCESSED, the other two DUPLICATE_ACK
    const statuses = [res1.status, res2.status, res3.status];
    const processedCount = statuses.filter(s => s === 'PROCESSED').length;
    const duplicateCount = statuses.filter(s => s === 'DUPLICATE_ACK').length;

    assert.equal(processedCount, 1, 'Exactly one message must be PROCESSED');
    assert.equal(duplicateCount, 2, 'Two duplicate messages must be acknowledged without re-execution');

    console.log('Passed: Exactly-once side effect verified across concurrent duplicate deliveries.');
  }

  // Test 3: API Contract & RFC 7807 Schema Validation
  {
    process.stdout.write('  [TEST 3] API Contract & RFC 7807 Validation Testing... ');

    // 1. Missing Auth -> 401
    const res401 = validateCreateOrderRequest({ customerId: 'c1', amount: 10 }, null);
    assert.equal(res401.statusCode, 401);
    assert.equal(res401.body.title, 'Unauthorized');

    // 2. Forbidden Role -> 403
    const res403 = validateCreateOrderRequest({ customerId: 'c1', amount: 10 }, 'Bearer role:read_only');
    assert.equal(res403.statusCode, 403);
    assert.equal(res403.body.title, 'Forbidden');

    // 3. Schema Violation (amount <= 0) -> 422
    const res422 = validateCreateOrderRequest({ customerId: 'c1', amount: -5.0 }, 'Bearer valid_token');
    assert.equal(res422.statusCode, 422);
    assert.equal(res422.body.title, 'Unprocessable Entity');
    assert.ok(res422.body.invalidParams.some(p => p.includes('amount must be a positive number')));

    // 4. Valid Request -> 201
    const res201 = validateCreateOrderRequest({ customerId: 'c1', amount: 75.0 }, 'Bearer valid_token');
    assert.equal(res201.statusCode, 201);
    assert.equal(res201.body.status, 'CREATED');
    assert.equal(res201.body.amount, 75.0);

    console.log('Passed: RFC 7807 Problem Details and HTTP status contract verified.');
  }

  // Test 4: Resource Leak & Event Listener Disposal
  {
    process.stdout.write('  [TEST 4] Resource Leak & Event Listener Cleanup... ');
    const stream = new OrderEventStream();

    assert.equal(stream.listenerCount('orderCreated'), 0);

    const handlerA = () => {};
    const handlerB = () => {};

    const unsubA = stream.subscribe('orderCreated', handlerA);
    const unsubB = stream.subscribe('orderCreated', handlerB);

    assert.equal(stream.listenerCount('orderCreated'), 2);

    // Dispose listener A
    unsubA();
    assert.equal(stream.listenerCount('orderCreated'), 1);

    // Dispose listener B
    unsubB();
    assert.equal(stream.listenerCount('orderCreated'), 0);

    console.log('Passed: Unsubscribe disposal pattern prevented listener leak.');
  }

  console.log('\n=================================================================');
  console.log(' ALL MODULE 12 NODE.JS TESTING SUITES PASSED (4/4)               ');
  console.log('=================================================================');
}

runAllTests().catch((err) => {
  console.error('\nTest Suite Failed:', err);
  process.exit(1);
});
