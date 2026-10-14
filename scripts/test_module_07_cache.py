#!/usr/bin/env python3
"""
Module 07: Caching & Performance Verification Test Harness (Python 3).

Verifies mathematical models, cache algorithmic invariants, and concurrency
mechanics taught in Module 07:
 1. Expected latency equation vs cache hit rate.
 2. Bounded LRU eviction invariants.
 3. Bloom filter false-positive rate and zero-false-negative invariant.
 4. Single-flight request coalescing under concurrent worker threads.
 5. Redis ZSET sliding-window rate limiter member collision fix.
"""

import sys
import time
import math
import hashlib
import threading
from collections import OrderedDict
from concurrent.futures import ThreadPoolExecutor

def test_expected_latency_model():
    print("  [TEST 1] Expected Latency vs Cache Hit Rate Model...")
    # L_cache = 1ms, L_db = 50ms, L_cache_write = 1ms
    # E[L] = H * L_cache + (1 - H) * (L_cache + L_db + L_cache_write)
    l_cache = 1.0
    l_db = 50.0
    l_write = 1.0
    miss_penalty = l_cache + l_db + l_write # 52.0 ms

    def expected_latency(h: float) -> float:
        return (h * l_cache) + ((1.0 - h) * miss_penalty)

    e_0 = expected_latency(0.0)
    assert abs(e_0 - 52.0) < 1e-6, f"At 0% hit rate, expected 52ms, got {e_0}"

    e_90 = expected_latency(0.90)
    # 0.90 * 1 + 0.10 * 52 = 0.9 + 5.2 = 6.1 ms
    assert abs(e_90 - 6.1) < 1e-6, f"At 90% hit rate, expected 6.1ms, got {e_90}"

    e_99 = expected_latency(0.99)
    # 0.99 * 1 + 0.01 * 52 = 0.99 + 0.52 = 1.51 ms
    assert abs(e_99 - 1.51) < 1e-6, f"At 99% hit rate, expected 1.51ms, got {e_99}"

    print("    -> Passed: Analytical latency model matches theoretical derivations.")

def test_bounded_lru_invariants():
    print("  [TEST 2] Bounded LRU Cache Eviction Invariants...")
    class BoundedLRU:
        def __init__(self, capacity: int):
            self.capacity = capacity
            self.store = OrderedDict()

        def get(self, key):
            if key not in self.store:
                return None
            self.store.move_to_end(key)
            return self.store[key]

        def put(self, key, value):
            if key in self.store:
                self.store.move_to_end(key)
            self.store[key] = value
            if len(self.store) > self.capacity:
                self.store.popitem(last=False) # Evict oldest / LRU

    cache = BoundedLRU(3)
    cache.put("k1", "v1")
    cache.put("k2", "v2")
    cache.put("k3", "v3")

    # Touch k1 -> order is now k2 (oldest), k3, k1 (newest)
    assert cache.get("k1") == "v1"

    # Insert k4 -> should evict k2
    cache.put("k4", "v4")
    assert len(cache.store) == 3
    assert cache.get("k2") is None, "k2 must be evicted as LRU"
    assert cache.get("k1") == "v1", "k1 must be preserved"
    assert cache.get("k3") == "v3"
    assert cache.get("k4") == "v4"

    print("    -> Passed: Bounded LRU capacity and access-order preservation verified.")

def test_bloom_filter_invariants():
    print("  [TEST 3] Bloom Filter Invariants (Zero False Negatives)...")
    class SimpleBloomFilter:
        def __init__(self, size_bits: int, num_hashes: int):
            self.size = size_bits
            self.num_hashes = num_hashes
            self.bit_array = [False] * size_bits

        def _hashes(self, item: str):
            for i in range(self.num_hashes):
                digest = hashlib.sha256(f"{i}:{item}".encode("utf-8")).hexdigest()
                yield int(digest, 16) % self.size

        def add(self, item: str):
            for idx in self._hashes(item):
                self.bit_array[idx] = True

        def contains(self, item: str) -> bool:
            return all(self.bit_array[idx] for idx in self._hashes(item))

    bf = SimpleBloomFilter(size_bits=1000, num_hashes=5)
    inserted_items = [f"product_{i}" for i in range(100)]
    for item in inserted_items:
        bf.add(item)

    # Invariant 1: Zero False Negatives!
    for item in inserted_items:
        assert bf.contains(item) is True, f"False negative detected for {item}!"

    # Invariant 2: Non-existent items mostly return False (controlled false positive rate)
    false_positives = 0
    test_non_existent = [f"nonexistent_{i}" for i in range(1000)]
    for item in test_non_existent:
        if bf.contains(item):
            false_positives += 1

    fp_rate = false_positives / len(test_non_existent)
    # With m=1000, n=100, k=5, theoretical FP rate is ~ (1 - e^(-5*100/1000))^5 ≈ (1 - e^-0.5)^5 ≈ (0.393)^5 ≈ 0.0094 (~1%)
    assert fp_rate < 0.05, f"False positive rate too high: {fp_rate:.4f}"

    print(f"    -> Passed: Zero false negatives confirmed. Empirical FP rate: {fp_rate:.4f} (< 5%).")

def test_single_flight_coalescing():
    print("  [TEST 4] Single-Flight Request Coalescing under Concurrent Threads...")
    lock = threading.Lock()
    in_flight = {}
    db_execution_count = 0

    def expensive_db_query(key: str) -> str:
        nonlocal db_execution_count
        with lock:
            db_execution_count += 1
        time.sleep(0.04) # Simulate slow SQL join
        return f"RESULT_FOR_{key}"

    def single_flight_execute(key: str) -> str:
        with lock:
            if key in in_flight:
                event, result_box, my_id = in_flight[key]
                need_execute = False
            else:
                event = threading.Event()
                result_box = [None, None] # [result, exception]
                my_id = object()
                in_flight[key] = (event, result_box, my_id)
                need_execute = True

        if need_execute:
            try:
                res = expensive_db_query(key)
                result_box[0] = res
            except Exception as e:
                result_box[1] = e
                raise
            finally:
                with lock:
                    # Clean up ONLY if this exact registration is still present
                    current = in_flight.get(key)
                    if current and current[2] is my_id:
                        in_flight.pop(key, None)
                event.set()
            return res
        else:
            event.wait()
            if result_box[1] is not None:
                raise result_box[1]
            return result_box[0]

    concurrency = 20
    results = []
    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(single_flight_execute, "hot_item_key") for _ in range(concurrency)]
        for f in futures:
            results.append(f.result())

    assert len(results) == concurrency
    for r in results:
        assert r == "RESULT_FOR_hot_item_key"

    assert db_execution_count == 1, f"Expected 1 DB execution, observed {db_execution_count}"
    assert len(in_flight) == 0, "In-flight registry must be empty after completion"

    # Verify exception propagation and cleanup
    def failing_loader(key: str) -> str:
        raise RuntimeError("Database connection reset")

    def failing_single_flight(key: str) -> str:
        with lock:
            if key in in_flight:
                event, result_box, my_id = in_flight[key]
                need_execute = False
            else:
                event = threading.Event()
                result_box = [None, None]
                my_id = object()
                in_flight[key] = (event, result_box, my_id)
                need_execute = True
        if need_execute:
            try:
                res = failing_loader(key)
                result_box[0] = res
                return res
            except Exception as e:
                result_box[1] = e
                raise
            finally:
                with lock:
                    current = in_flight.get(key)
                    if current and current[2] is my_id:
                        in_flight.pop(key, None)
                event.set()
        else:
            event.wait()
            if result_box[1] is not None:
                raise result_box[1]
            return result_box[0]

    caught = False
    try:
        failing_single_flight("failing_query")
    except RuntimeError as re:
        caught = True
        assert "Database connection reset" in str(re)
    assert caught, "Exception must be raised"
    assert len(in_flight) == 0, "In-flight map must clean up after exception"

    print(f"    -> Passed: {concurrency} threads coalesced into 1 DB query; error cleanup verified.")

def test_redis_zset_rate_limiter_collision():
    print("  [TEST 5] Redis ZSET Sliding-Window Rate Limiter Member Collision Fix...")
    class SimulatedZSet:
        def __init__(self):
            self.members = {} # member -> score

        def zadd(self, member, score):
            # Dict behavior simulates Redis ZADD: if member exists, updates score!
            self.members[member] = score

        def zcard(self):
            return len(self.members)

    # Flawed implementation: uses timestamp alone as member
    flawed_zset = SimulatedZSet()
    fixed_zset = SimulatedZSet()

    same_millisecond = 1700000000100

    # 5 requests arrive at the EXACT same millisecond
    for req_idx in range(5):
        # Flawed: member is just timestamp string
        flawed_zset.zadd(str(same_millisecond), same_millisecond)
        # Fixed: member is composite string "timestamp:requestId"
        fixed_zset.zadd(f"{same_millisecond}:req_uuid_{req_idx}", same_millisecond)

    # Flawed ZSET has cardinality 1 because identical members overwrite scores!
    assert flawed_zset.zcard() == 1, "Demonstrating the flaw: ZCARD is only 1!"
    # Fixed ZSET has cardinality 5, correctly recording all requests!
    assert fixed_zset.zcard() == 5, "Fixed ZSET preserves all 5 distinct requests!"

    print("    -> Passed: Member collision bug verified and composite nonce fix confirmed.")

def main():
    print("=================================================================")
    print("  MODULE 07 CACHING & PERFORMANCE TEST HARNESS (PYTHON 3)        ")
    print("=================================================================")

    test_expected_latency_model();
    test_bounded_lru_invariants();
    test_bloom_filter_invariants();
    test_single_flight_coalescing();
    test_redis_zset_rate_limiter_collision();

    print("\nAll 5 Python Caching & Performance Test Suites Passed Successfully!")
    return 0

if __name__ == "__main__":
    sys.exit(main())
