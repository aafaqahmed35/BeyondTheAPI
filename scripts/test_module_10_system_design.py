#!/usr/bin/env python3
"""
Module 10 — System Design & Production Architecture Analytical Verification Suite.

Implements mathematical and deterministic simulations of system design primitives in Python 3:
1. Back-of-the-Envelope Capacity Estimation Formulas (QPS, Bandwidth, 5-Year Storage)
2. Compound Availability Multipliers (Serial Chains vs Parallel Redundancy & Downtime Budgets)
3. Consistent Hashing Virtual Node Load Variance Reduction
4. Circuit Breaker Fast-Failure State Transitions
5. Little's Law Concurrency & Worker Sizing (L = λ * W)
"""

import hashlib
import math
import sys
from collections import defaultdict
from typing import Dict, List, Tuple


def test_capacity_estimation_formulas():
    print("  [TEST 1] Back-of-the-Envelope Capacity Estimation Formulas...")
    # System: 100 Million DAU Photo/Post Platform
    dau = 100_000_000
    posts_per_user_per_day = 0.10  # 10% of users post 1 photo/day
    reads_per_user_per_day = 20.0  # 20 feed views/day
    metadata_bytes = 500           # 500 bytes per record
    seconds_per_day = 86_400

    # 1. Write QPS
    daily_writes = dau * posts_per_user_per_day  # 10,000,000 writes
    avg_write_qps = daily_writes / seconds_per_day
    peak_write_qps = avg_write_qps * 3.0  # 3x peak multiplier

    assert abs(daily_writes - 10_000_000) < 1e-6
    assert abs(avg_write_qps - 115.74) < 0.1
    assert abs(peak_write_qps - 347.22) < 0.1

    # 2. Read QPS
    daily_reads = dau * reads_per_user_per_day  # 2 Billion reads
    avg_read_qps = daily_reads / seconds_per_day
    peak_read_qps = avg_read_qps * 2.0  # 2x peak multiplier

    assert abs(daily_reads - 2_000_000_000) < 1e-6
    assert abs(avg_read_qps - 23_148.15) < 1.0
    assert abs(peak_read_qps - 46_296.30) < 2.0

    # 3. Storage Growth over 5 Years
    daily_storage_bytes = daily_writes * metadata_bytes  # 5 GB/day
    annual_storage_bytes = daily_storage_bytes * 365
    five_year_raw_bytes = annual_storage_bytes * 5
    replication_factor = 3
    five_year_total_bytes = five_year_raw_bytes * replication_factor

    daily_storage_gb = daily_storage_bytes / (10**9)
    five_year_raw_tb = five_year_raw_bytes / (10**12)
    five_year_total_tb = five_year_total_bytes / (10**12)

    assert abs(daily_storage_gb - 5.0) < 1e-6
    assert abs(five_year_raw_tb - 9.125) < 0.01
    assert abs(five_year_total_tb - 27.375) < 0.01

    # 4. Egress Bandwidth
    egress_bytes_per_sec = avg_read_qps * metadata_bytes
    egress_mbps = (egress_bytes_per_sec * 8) / (10**6)  # Megabits per second
    assert abs(egress_mbps - 92.59) < 0.5

    print(f"    -> Passed: 100M DAU modeled accurately (Write QPS: {avg_write_qps:.1f}, Read QPS: {avg_read_qps:.1f}, 5-Year Replicated Storage: {five_year_total_tb:.2f} TB).")


def test_availability_and_downtime_budgets():
    print("  [TEST 2] Compound Availability Multipliers & Annual Downtime Budgets...")
    seconds_per_year = 365.25 * 24 * 3600  # 31,557,600 seconds

    # Single service availability tiers
    availabilities = {
        "99% (Two Nines)": 0.99,
        "99.9% (Three Nines)": 0.999,
        "99.99% (Four Nines)": 0.9999,
        "99.999% (Five Nines)": 0.99999,
    }

    downtime_seconds = {
        tier: seconds_per_year * (1.0 - a) for tier, a in availabilities.items()
    }

    # Two nines allows ~3.65 days downtime/year
    assert abs(downtime_seconds["99% (Two Nines)"] / 86400 - 3.65) < 0.05
    # Three nines allows ~8.76 hours downtime/year
    assert abs(downtime_seconds["99.9% (Three Nines)"] / 3600 - 8.76) < 0.1
    # Four nines allows ~52.6 minutes downtime/year
    assert abs(downtime_seconds["99.99% (Four Nines)"] / 60 - 52.6) < 1.0
    # Five nines allows ~5.26 minutes downtime/year
    assert abs(downtime_seconds["99.999% (Five Nines)"] / 60 - 5.26) < 0.2

    # Serial Service Chain Rule: Availability degrades multiplicatively
    # Chain of 4 services, each 99.9% available
    a_single = 0.999
    a_serial_chain = a_single ** 4  # 0.999^4 = 0.996005...
    assert a_serial_chain < 0.9961
    chain_downtime_hours = (seconds_per_year * (1.0 - a_serial_chain)) / 3600
    assert chain_downtime_hours > 35.0, "Serial chaining 4 three-nines services increases annual downtime to >35 hours"

    # Parallel Redundancy Rule: Dual redundant active-active instances
    # Two independent 99% instances
    a_parallel = 1.0 - ((1.0 - 0.99) * (1.0 - 0.99))  # 1 - 0.0001 = 0.9999 (Four nines!)
    assert abs(a_parallel - 0.9999) < 1e-6

    print(f"    -> Passed: Serial chain (4 x 99.9% -> {a_serial_chain*100:.2f}%) and parallel redundancy (2 x 99% -> {a_parallel*100:.2f}%) verified.")


def test_consistent_hashing_variance_reduction():
    print("  [TEST 3] Consistent Hashing Virtual Node Load Variance Reduction...")
    # Demonstrate that increasing virtual nodes per physical node reduces standard deviation of key distribution
    nodes = ["shard-A", "shard-B", "shard-C", "shard-D"]
    num_keys = 20_000

    def hash_fn(val: str) -> int:
        return int(hashlib.md5(val.encode()).hexdigest(), 16)

    def simulate_ring(v_nodes: int) -> float:
        # Build ring
        ring = []
        for n in nodes:
            for vn in range(v_nodes):
                ring.append((hash_fn(f"{n}#vn_{vn}"), n))
        ring.sort(key=lambda x: x[0])
        ring_hashes = [r[0] for r in ring]

        import bisect
        counts = defaultdict(int)
        for k in range(num_keys):
            kh = hash_fn(f"key_val_{k}")
            idx = bisect.bisect_right(ring_hashes, kh)
            if idx == len(ring):
                idx = 0
            counts[ring[idx][1]] += 1

        expected_per_node = num_keys / len(nodes)
        variance = sum((c - expected_per_node) ** 2 for c in counts.values()) / len(nodes)
        return math.sqrt(variance)

    std_dev_low_vnodes = simulate_ring(v_nodes=5)
    std_dev_high_vnodes = simulate_ring(v_nodes=150)

    # Increasing virtual nodes from 5 to 150 must substantially reduce standard deviation
    assert std_dev_high_vnodes < std_dev_low_vnodes, "Higher virtual node count must improve key balance"
    ratio = std_dev_low_vnodes / std_dev_high_vnodes
    assert ratio > 2.0, f"Virtual nodes should improve uniformity by at least 2x (actual ratio: {ratio:.1f}x)"

    print(f"    -> Passed: Virtual nodes reduced load std-dev from {std_dev_low_vnodes:.1f} to {std_dev_high_vnodes:.1f} ({ratio:.1f}x smoother).")


def test_circuit_breaker_thresholds():
    print("  [TEST 4] Circuit Breaker Failure Threshold & Fast-Fail Simulation...")
    class MockCircuitBreaker:
        def __init__(self, failure_threshold: int):
            self.threshold = failure_threshold
            self.failures = 0
            self.state = "CLOSED"

        def record(self, success: bool):
            if success:
                if self.state == "HALF_OPEN":
                    self.state = "CLOSED"
                    self.failures = 0
            else:
                self.failures += 1
                if self.failures >= self.threshold:
                    self.state = "OPEN"

        def is_call_allowed(self) -> bool:
            return self.state != "OPEN"

    cb = MockCircuitBreaker(failure_threshold=3)
    assert cb.is_call_allowed()

    cb.record(success=False)
    cb.record(success=False)
    assert cb.state == "CLOSED"
    assert cb.is_call_allowed()

    cb.record(success=False)
    assert cb.state == "OPEN"
    assert not cb.is_call_allowed(), "Calls must be fast-failed when breaker is OPEN"

    print("    -> Passed: Circuit breaker tripped to OPEN on 3rd failure; fast-fail verified.")


def test_littles_law_system_sizing():
    print("  [TEST 5] Little's Law Concurrency & Worker Sizing (L = λ * W)...")
    # Arrival rate lambda = 5,000 req/sec
    arrival_rate = 5000.0  # req/sec

    # Baseline service time W = 20ms (0.02s)
    service_time_fast = 0.020
    concurrent_requests_fast = arrival_rate * service_time_fast
    assert abs(concurrent_requests_fast - 100.0) < 1e-6, "5000 req/s @ 20ms requires 100 concurrent workers"

    # Degraded service time (e.g. database lock contention causes 200ms response time)
    service_time_slow = 0.200
    concurrent_requests_slow = arrival_rate * service_time_slow
    assert abs(concurrent_requests_slow - 1000.0) < 1e-6, "5000 req/s @ 200ms requires 1000 concurrent workers"

    # If worker pool is capped at 200 threads, system queues 800 requests or rejects
    max_workers = 200
    capacity_req_per_sec = max_workers / service_time_slow  # 200 / 0.2 = 1,000 req/sec
    assert capacity_req_per_sec == 1000.0, "Capacity collapses to 1,000 req/s when service time degrades to 200ms"

    print(f"    -> Passed: Little's Law proves that a 10x latency spike (20ms -> 200ms) expands concurrency 10x (100 -> 1000 workers).")


def main() -> int:
    print("=================================================================")
    print(" MODULE 10: SYSTEM DESIGN & ARCHITECTURE PYTHON TEST HARNESS     ")
    print("=================================================================\n")

    test_capacity_estimation_formulas()
    test_availability_and_downtime_budgets()
    test_consistent_hashing_variance_reduction()
    test_circuit_breaker_thresholds()
    test_littles_law_system_sizing()

    print("\n=================================================================")
    print(" ALL MODULE 10 ANALYTICAL SYSTEM DESIGN TESTS PASSED (5/5)       ")
    print("=================================================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())
