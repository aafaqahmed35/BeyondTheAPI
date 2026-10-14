/**
 * Module 11 — Node.js Observability & Production Diagnostics Verification Suite.
 *
 * Implements deterministic simulations of core Node.js observability primitives:
 * 1. AsyncLocalStorage Trace Context Propagation across Asynchronous Chains
 * 2. W3C Traceparent Header Parsing, Validation, and Injection
 * 3. RED Metrics Collector (Rate, Errors, Duration Histogram)
 * 4. Structured JSON Logger with Automatic Correlation ID & PII Masking
 */

const { AsyncLocalStorage } = require('node:async_hooks');
const crypto = require('node:crypto');

// =========================================================================
// 1. ASYNCHRONOUS TRACE CONTEXT ENGINE (AsyncLocalStorage)
// =========================================================================

const traceStorage = new AsyncLocalStorage();

function runWithContext(context, fn) {
  return traceStorage.run(context, fn);
}

function getActiveContext() {
  return traceStorage.getStore() || null;
}

// =========================================================================
// 2. W3C TRACEPARENT PARSER & SERIALIZER
// =========================================================================

const TRACEPARENT_REGEX = /^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$/;

class W3CTraceParent {
  static parse(header) {
    if (!header || typeof header !== 'string') {
      throw new Error('Traceparent header must be a non-empty string');
    }
    const match = header.trim().match(TRACEPARENT_REGEX);
    if (!match) {
      throw new Error(`Invalid W3C traceparent header: ${header}`);
    }
    const [, version, traceId, parentId, flags] = match;
    if (version === 'ff') {
      throw new Error("Traceparent version 'ff' is forbidden");
    }
    if (traceId === '00000000000000000000000000000000') {
      throw new Error('TraceId cannot be all zeros');
    }
    if (parentId === '0000000000000000') {
      throw new Error('ParentId cannot be all zeros');
    }
    return {
      version,
      traceId,
      parentId,
      flags,
      isSampled: (parseInt(flags, 16) & 0x01) === 0x01,
    };
  }

  static serialize({ version = '00', traceId, parentId, flags = '01' }) {
    return `${version}-${traceId}-${parentId}-${flags}`;
  }

  static createNew(sampled = true) {
    const traceId = crypto.randomBytes(16).toString('hex');
    const parentId = crypto.randomBytes(8).toString('hex');
    const flags = sampled ? '01' : '00';
    return {
      version: '00',
      traceId,
      parentId,
      flags,
      isSampled: sampled,
    };
  }
}

// =========================================================================
// 3. RED METRICS COLLECTOR (Rate, Errors, Duration)
// =========================================================================

class RedMetricsCollector {
  constructor(bucketBoundariesMs = [10, 25, 50, 100, 250, 500]) {
    this.bucketBoundaries = [...bucketBoundariesMs].sort((a, b) => a - b);
    this.totalRequests = 0;
    this.totalErrors = 0;
    this.durationSum = 0;
    this.bucketCounts = new Array(this.bucketBoundaries.length + 1).fill(0); // +1 for +Inf
  }

  recordRequest(durationMs, isError = false) {
    this.totalRequests++;
    if (isError) this.totalErrors++;
    this.durationSum += durationMs;

    for (let i = 0; i < this.bucketBoundaries.length; i++) {
      if (durationMs <= this.bucketBoundaries[i]) {
        this.bucketCounts[i]++;
      }
    }
    this.bucketCounts[this.bucketCounts.length - 1]++; // +Inf
  }

  getErrorRate() {
    return this.totalRequests === 0 ? 0 : this.totalErrors / this.totalRequests;
  }

  getQuantile(q) {
    if (q <= 0 || q > 1) throw new Error('Quantile must be in (0, 1]');
    if (this.totalRequests === 0) return 0;

    const rank = q * this.totalRequests;
    let prevCount = 0;
    let lowerBound = 0;

    for (let i = 0; i < this.bucketBoundaries.length; i++) {
      const countAtBucket = this.bucketCounts[i];
      if (countAtBucket >= rank) {
        const countInBucket = countAtBucket - prevCount;
        if (countInBucket === 0) return this.bucketBoundaries[i];
        const fraction = (rank - prevCount) / countInBucket;
        return lowerBound + fraction * (this.bucketBoundaries[i] - lowerBound);
      }
      lowerBound = this.bucketBoundaries[i];
      prevCount = countAtBucket;
    }
    return this.bucketBoundaries[this.bucketBoundaries.length - 1];
  }
}

// =========================================================================
// 4. STRUCTURED JSON LOGGER WITH PII MASKING
// =========================================================================

const CREDIT_CARD_REGEX = /\b(?:\d{4}[-\s]?){3}\d{4}\b/g;
const BEARER_TOKEN_REGEX = /Bearer\s+[A-Za-z0-9\-._~+/]+=*/gi;

function maskSensitiveData(text) {
  if (typeof text !== 'string') return text;
  return text
    .replace(CREDIT_CARD_REGEX, '****-****-****-****')
    .replace(BEARER_TOKEN_REGEX, 'Bearer [REDACTED]');
}

class StructuredLogger {
  static log(level, message, metadata = {}) {
    const context = getActiveContext() || {};
    const sanitizedMeta = {};
    for (const [k, v] of Object.entries(metadata)) {
      sanitizedMeta[k] = maskSensitiveData(v);
    }

    const payload = {
      timestamp: new Date().toISOString(),
      level,
      message: maskSensitiveData(message),
      trace_id: context.traceId || null,
      span_id: context.spanId || null,
      ...sanitizedMeta,
    };

    return JSON.stringify(payload);
  }
}

// =========================================================================
// TEST HARNESS AND ASSERTIONS
// =========================================================================

async function runTests() {
  console.log('=================================================================');
  console.log(' MODULE 11: NODE.JS OBSERVABILITY & TRACE VERIFICATION          ');
  console.log('=================================================================\n');

  // Test 1: AsyncLocalStorage Trace Context
  process.stdout.write('  [TEST 1] AsyncLocalStorage Trace Propagation across Async Tasks... ');
  const ctx = { traceId: '4bf92f3577b34da6a3ce929d0e0e4736', spanId: '00f067aa0ba902b7' };

  await runWithContext(ctx, async () => {
    // Nested asynchronous promise chain
    await new Promise((resolve) => setTimeout(resolve, 10));
    const active = getActiveContext();
    if (!active || active.traceId !== ctx.traceId) {
      throw new Error(`Context lost across timer boundary! Expected ${ctx.traceId}, got ${active?.traceId}`);
    }

    // Sub-task execution
    await (async () => {
      await Promise.resolve();
      const subActive = getActiveContext();
      if (subActive?.spanId !== ctx.spanId) {
        throw new Error('Context lost across promise microtask!');
      }
    })();
  });

  // Verify context is cleared outside runner
  if (getActiveContext() !== null) {
    throw new Error('Context leaked outside AsyncLocalStorage.run() scope!');
  }
  console.log('Passed: Trace context safely preserved across async boundaries.');

  // Test 2: W3C Traceparent Header Parsing & Validation
  process.stdout.write('  [TEST 2] W3C Traceparent Header Parsing, Validation & Injection... ');
  const header = '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01';
  const parsed = W3CTraceParent.parse(header);
  if (parsed.traceId !== '4bf92f3577b34da6a3ce929d0e0e4736' || parsed.parentId !== '00f067aa0ba902b7' || !parsed.isSampled) {
    throw new Error('W3C traceparent parsing failed');
  }
  const serialized = W3CTraceParent.serialize(parsed);
  if (serialized !== header) {
    throw new Error(`Serialization mismatch: ${serialized} vs ${header}`);
  }

  // Reject all-zero traceId
  let rejected = false;
  try {
    W3CTraceParent.parse('00-00000000000000000000000000000000-00f067aa0ba902b7-01');
  } catch {
    rejected = true;
  }
  if (!rejected) throw new Error('Failed to reject all-zero traceId');
  console.log('Passed: W3C traceparent format compliance and serialization confirmed.');

  // Test 3: RED Metrics Collector & Histogram Quantiles
  process.stdout.write('  [TEST 3] RED Metrics (Rate, Errors, Duration Histogram)... ');
  const red = new RedMetricsCollector([10, 50, 100, 250]);
  for (let i = 0; i < 90; i++) red.recordRequest(15, false); // 90 requests at 15ms (success)
  for (let i = 0; i < 10; i++) red.recordRequest(200, true); // 10 requests at 200ms (500 error)

  if (red.getErrorRate() !== 0.10) {
    throw new Error(`Expected error rate 10%, got ${red.getErrorRate() * 100}%`);
  }
  const p50 = red.getQuantile(0.50);
  const p99 = red.getQuantile(0.99);
  if (p50 > 50) throw new Error(`Expected p50 <= 50ms, got ${p50}`);
  if (p99 <= 100) throw new Error(`Expected p99 > 100ms, got ${p99}`);
  console.log('Passed: RED error rate (10%) and p50/p99 latency quantiles calculated accurately.');

  // Test 4: Structured JSON Logging & PII Masking
  process.stdout.write('  [TEST 4] Structured JSON Logging & PII Masking... ');
  await runWithContext({ traceId: 'trace-abc-123', spanId: 'span-xyz-789' }, async () => {
    const rawMsg = 'Charged card 4111-2222-3333-4444 using Bearer secret_token_value_here';
    const jsonStr = StructuredLogger.log('INFO', rawMsg, { route: '/checkout' });
    const logObj = JSON.parse(jsonStr);

    if (logObj.trace_id !== 'trace-abc-123') throw new Error('trace_id missing from structured log');
    if (logObj.message.includes('4111-2222-3333-4444')) throw new Error('Credit card not masked in message');
    if (!logObj.message.includes('****-****-****-****')) throw new Error('Expected masked credit card placeholder');
    if (logObj.message.includes('secret_token_value_here')) throw new Error('Bearer token leaked in log');
    if (!logObj.message.includes('Bearer [REDACTED]')) throw new Error('Bearer token [REDACTED] placeholder missing');
  });
  console.log('Passed: Structured JSON logger attached active traceId and sanitized PII tokens.');

  console.log('\n=================================================================');
  console.log(' ALL MODULE 11 NODE.JS OBSERVABILITY TESTS PASSED PERFECTLY (4/4)');
  console.log('=================================================================');
}

runTests().catch((err) => {
  console.error('\n[FATAL ERROR]', err);
  process.exit(1);
});
