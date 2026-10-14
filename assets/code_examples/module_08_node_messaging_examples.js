/**
 * Module 08 — Node.js Asynchronous Processing & Distributed Messaging Verification Suite.
 *
 * Implements deterministic async messaging primitives in pure Node.js (v20+ LTS):
 * 1. Reliable Queue with Visibility Timeouts, Leases, and Crash Redelivery
 * 2. Idempotent Event Consumer with Deduplication and Database Constraint Isolation
 * 3. Bounded Retries with Exponential Backoff, Jitter, and Dead-Letter Quarantine
 * 4. Transactional Outbox Relay with Duplicate Delivery Handling
 * 5. Graceful Worker Shutdown Draining In-Flight Promises within Deadlines
 */

import assert from 'node:assert/strict';

// =========================================================================
// 1. RELIABLE QUEUE WITH VISIBILITY TIMEOUT (LEASES)
// =========================================================================

export class ReliableQueue {
  constructor(visibilityTimeoutMs = 100) {
    this.visibilityTimeoutMs = visibilityTimeoutMs;
    this.available = [];
    this.activeLeases = new Map(); // token -> { message, expiresAt }
  }

  push(message) {
    this.available.push({ ...message, attemptCount: message.attemptCount || 0 });
  }

  poll() {
    const now = Date.now();
    // Redeliver expired leases
    for (const [token, leased] of this.activeLeases.entries()) {
      if (now > leased.expiresAt) {
        this.available.push({ ...leased.message, attemptCount: leased.message.attemptCount + 1 });
        this.activeLeases.delete(token);
      }
    }

    if (this.available.length === 0) {
      return null;
    }

    const message = this.available.shift();
    const token = `lease_${Math.random().toString(36).substring(2, 9)}`;
    const expiresAt = now + this.visibilityTimeoutMs;
    this.activeLeases.set(token, { message, expiresAt });

    return { message, leaseToken: token, expiresAt };
  }

  acknowledge(leaseToken) {
    const leased = this.activeLeases.get(leaseToken);
    if (!leased) {
      return false; // Stale or non-existent token
    }
    if (Date.now() > leased.expiresAt) {
      this.activeLeases.delete(leaseToken);
      return false; // Lease expired
    }
    this.activeLeases.delete(leaseToken);
    return true;
  }
}

// =========================================================================
// 2. IDEMPOTENT CONSUMER WITH DURABLE SIMULATION
// =========================================================================

export class IdempotentConsumer {
  constructor() {
    this.processedStore = new Map(); // messageId -> { status, result }
    this.balance = 0;
    this.executionCount = 0;
  }

  async processPayment(messageId, amount) {
    // Atomic reservation simulation (like INSERT ON CONFLICT DO NOTHING)
    if (this.processedStore.has(messageId)) {
      const existing = this.processedStore.get(messageId);
      return { status: 'DUPLICATE_IGNORED', result: existing.result };
    }

    // Reserve state
    this.processedStore.set(messageId, { status: 'IN_PROGRESS' });

    try {
      // Critical business effect
      this.executionCount++;
      this.balance += amount;

      const result = `Credited ${amount}`;
      this.processedStore.set(messageId, { status: 'COMPLETED', result });
      return { status: 'SUCCESS', result };
    } catch (err) {
      this.processedStore.delete(messageId);
      throw err;
    }
  }
}

// =========================================================================
// 3. RETRY HANDLER WITH BACKOFF & DEAD-LETTER QUEUE
// =========================================================================

export class RetryHandler {
  constructor(maxRetries = 3, baseDelayMs = 20, maxDelayMs = 200) {
    this.maxRetries = maxRetries;
    this.baseDelayMs = baseDelayMs;
    this.maxDelayMs = maxDelayMs;
    this.dlq = [];
  }

  calculateDelay(attempt) {
    const exponential = this.baseDelayMs * Math.pow(2, attempt);
    const ceiling = Math.min(this.maxDelayMs, exponential);
    return Math.floor(Math.random() * ceiling);
  }

  async executeWithRetry(taskFn, message) {
    let currentAttempt = message.attemptCount || 0;
    while (currentAttempt < this.maxRetries) {
      try {
        return await taskFn(message);
      } catch (err) {
        currentAttempt++;
        if (currentAttempt >= this.maxRetries) {
          // Route to DLQ
          this.dlq.push({
            message,
            reason: err.message,
            quarantinedAt: new Date().toISOString(),
            attempts: currentAttempt,
          });
          return { status: 'QUARANTINED', error: err.message };
        }
        const delay = this.calculateDelay(currentAttempt);
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }
}

// =========================================================================
// 4. TRANSACTIONAL OUTBOX SIMULATION
// =========================================================================

export class OutboxSystem {
  constructor() {
    this.orders = [];
    this.outbox = [];
    this.broker = [];
    this.idSequence = 1;
  }

  createOrderWithOutbox(orderId, payload) {
    // Single local transaction boundary
    this.orders.push(orderId);
    this.outbox.push({
      id: this.idSequence++,
      orderId,
      payload,
      published: false,
    });
  }

  relayPending(simulateNetworkCrash = false) {
    const pending = this.outbox.filter((r) => !r.published);
    for (const record of pending) {
      if (simulateNetworkCrash && record.id === 1) {
        // Message sent to broker, but relay crashes before marking published in DB!
        this.broker.push(record.orderId);
        throw new Error('Network crash prior to DB outbox ACK update');
      }
      this.broker.push(record.orderId);
      record.published = true;
    }
  }
}

// =========================================================================
// 5. GRACEFUL WORKER SHUTDOWN
// =========================================================================

export class GracefulNodeWorker {
  constructor() {
    this.isRunning = true;
    this.activeTasks = new Set();
    this.completedCount = 0;
  }

  submit(taskPromiseFn) {
    if (!this.isRunning) {
      throw new Error('Worker shutting down: new tasks rejected');
    }

    const task = taskPromiseFn()
      .then(() => {
        this.completedCount++;
      })
      .finally(() => {
        this.activeTasks.delete(task);
      });

    this.activeTasks.add(task);
    return task;
  }

  async shutdown(timeoutMs = 500) {
    this.isRunning = false;
    let timerId;
    const timeoutPromise = new Promise((_, reject) => {
      timerId = setTimeout(() => reject(new Error('Shutdown timeout exceeded')), timeoutMs);
    });

    const drainPromise = Promise.all(Array.from(this.activeTasks));
    try {
      await Promise.race([drainPromise, timeoutPromise]);
      return true;
    } finally {
      clearTimeout(timerId);
    }
  }
}

// =========================================================================
// 6. TEST HARNESS & VERIFICATION
// =========================================================================

async function runTests() {
  console.log('=================================================================');
  console.log('  MODULE 08 NODE.JS ASYNC & DISTRIBUTED MESSAGING TEST SUITE     ');
  console.log('=================================================================');

  // TEST 1
  console.log('  [TEST 1] Queue Visibility Timeout & Stale Lease Rejection...');
  const queue = new ReliableQueue(60); // 60ms lease
  queue.push({ id: 'job-1', data: 'ProcessImage' });

  const lease1 = queue.poll();
  assert(lease1 !== null);
  assert.equal(lease1.message.attemptCount, 0);

  // Stall for 90ms to trigger lease expiration
  await new Promise((r) => setTimeout(r, 90));

  const lease2 = queue.poll();
  assert(lease2 !== null, 'Message must be redelivered after visibility expiration');
  assert.equal(lease2.message.attemptCount, 1, 'Attempt count must increment on redelivery');

  // Worker 1 attempts to ACK with expired lease
  const ack1 = queue.acknowledge(lease1.leaseToken);
  assert.equal(ack1, false, 'Expired lease token must be rejected');

  // Worker 2 ACKs with active lease
  const ack2 = queue.acknowledge(lease2.leaseToken);
  assert.equal(ack2, true, 'Active lease must be acknowledged');
  console.log('    -> Passed: Visibility lease expiration and stale ACK rejection confirmed.');

  // TEST 2
  console.log('  [TEST 2] Idempotent Event Consumer under Concurrent Delivery...');
  const consumer = new IdempotentConsumer();
  const paymentMsgId = 'pay-evt-555';

  // Fire 10 concurrent processing calls with same message ID
  const promises = [];
  for (let i = 0; i < 10; i++) {
    promises.push(consumer.processPayment(paymentMsgId, 100));
  }
  const results = await Promise.all(promises);

  const successes = results.filter((r) => r.status === 'SUCCESS');
  const duplicates = results.filter((r) => r.status === 'DUPLICATE_IGNORED');

  assert.equal(successes.length, 1, 'Exactly one payment execution must succeed');
  assert.equal(duplicates.length, 9, 'All 9 duplicates must be safely ignored');
  assert.equal(consumer.executionCount, 1, 'Business logic executed once');
  assert.equal(consumer.balance, 100, 'Balance credited exactly once');
  console.log('    -> Passed: Idempotent consumer collapsed 10 concurrent events into 1 business effect.');

  // TEST 3
  console.log('  [TEST 3] Bounded Retries, Exponential Backoff, and DLQ Quarantine...');
  const retryHandler = new RetryHandler(3, 10, 50);
  const poisonMessage = { id: 'msg-bad', payload: 'INVALID_SYNTAX' };

  let attemptsExecuted = 0;
  const failingTask = async () => {
    attemptsExecuted++;
    throw new Error('Unrecoverable schema validation failure');
  };

  const outcome = await retryHandler.executeWithRetry(failingTask, poisonMessage);
  assert.equal(outcome.status, 'QUARANTINED');
  assert.equal(attemptsExecuted, 3);
  assert.equal(retryHandler.dlq.length, 1);
  assert.equal(retryHandler.dlq[0].attempts, 3);
  console.log('    -> Passed: Poison message retried 3 times and quarantined to DLQ with metadata.');

  // TEST 4
  console.log('  [TEST 4] Transactional Outbox Pattern & Duplicate Publication Handling...');
  const outboxSystem = new OutboxSystem();
  outboxSystem.createOrderWithOutbox('order-A', { item: 'book' });
  outboxSystem.createOrderWithOutbox('order-B', { item: 'pen' });

  assert.equal(outboxSystem.orders.length, 2);
  assert.equal(outboxSystem.outbox.length, 2);

  // Simulate network crash during relay
  let crashed = false;
  try {
    outboxSystem.relayPending(true);
  } catch {
    crashed = true;
  }
  assert(crashed, 'Relay should crash on simulated network drop');

  // Verify order-A was published to broker, but not marked published in outbox
  assert.equal(outboxSystem.broker.length, 1);
  assert.equal(outboxSystem.outbox[0].published, false);

  // Relay restarts and retries
  outboxSystem.relayPending(false);

  // Broker now holds order-A twice!
  const orderACount = outboxSystem.broker.filter((id) => id === 'order-A').length;
  assert.equal(orderACount, 2, 'Outbox at-least-once relay delivers order-A twice upon crash');
  assert(outboxSystem.outbox.every((r) => r.published), 'All outbox records published');
  console.log('    -> Passed: Transactional outbox eliminated dual-write; duplicate delivery demonstrated.');

  // TEST 5
  console.log('  [TEST 5] Graceful Worker Shutdown & In-Flight Promise Draining...');
  const worker = new GracefulNodeWorker();

  // Submit 3 asynchronous tasks
  for (let i = 0; i < 3; i++) {
    worker.submit(async () => {
      await new Promise((r) => setTimeout(r, 40));
    });
  }

  assert.equal(worker.activeTasks.size, 3);

  // Trigger shutdown with 300ms timeout
  const shutdownSuccess = await worker.shutdown(300);
  assert.equal(shutdownSuccess, true);
  assert.equal(worker.activeTasks.size, 0);
  assert.equal(worker.completedCount, 3);

  // Verify rejection of new tasks post-shutdown
  assert.throws(() => worker.submit(async () => {}), /shutting down/);
  console.log('    -> Passed: In-flight async tasks drained cleanly before shutdown.');

  console.log('\nAll 5 Node.js Messaging & Asynchronous Test Suites Passed Successfully!');
}

runTests().catch((err) => {
  console.error('Test failure:', err);
  process.exit(1);
});
