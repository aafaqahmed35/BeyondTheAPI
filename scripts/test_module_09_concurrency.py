#!/usr/bin/env python3
"""
Module 09 — Concurrency & Distributed Systems Analytical Verification Suite.

Implements mathematical and deterministic simulations of concurrency & distributed systems invariants:
1. Amdahl's Law Speedup Bounds & Parallel Scaling Limits
2. Gunther's Universal Scalability Law (USL): Contention (σ) & Coherency Delay (κ)
3. Distributed Quorum Intersection Invariant (R + W > N)
4. Monotonic Fencing Token Rejection of Zombie Writes
5. Raft Consensus Majority Quorum & Split-Brain Invariant (2f + 1)
6. Producer-Consumer Thread Coordination & Bounded Capacity Protection
7. Phi Accrual Failure Detection & Exponential Heartbeat Thresholds
"""

import math
import sys
import threading
import time
from collections import deque
from typing import Dict, List, Optional, Set, Tuple


def test_amdahls_law_limits():
    print("  [TEST 1] Amdahl's Law Speedup Bounds & Parallel Scaling Limits...")
    # S(N) = 1 / ((1 - p) + (p / N))
    # Scenario: 90% parallelizable (p = 0.90), 10% strictly serial (1 - p = 0.10)
    p = 0.90
    serial_fraction = 1.0 - p

    def speedup(n_cores: int) -> float:
        return 1.0 / (serial_fraction + (p / n_cores))

    s1 = speedup(1)
    s2 = speedup(2)
    s4 = speedup(4)
    s16 = speedup(16)
    s1000 = speedup(1000)
    s_large = speedup(100_000)
    theoretical_limit = 1.0 / serial_fraction  # 1 / 0.10 = 10.0x maximum speedup

    assert abs(s1 - 1.0) < 1e-6, "Single core speedup must be exactly 1.0x"
    assert s2 > s1, "2 cores must provide higher speedup than 1 core"
    assert s4 > s2, "4 cores must provide higher speedup than 2 cores"
    assert s16 < theoretical_limit, "16 cores cannot exceed the theoretical asymptote"
    assert abs(s1000 - theoretical_limit) < 0.10, "1000 cores approaches theoretical limit within 0.1x"
    assert abs(s_large - theoretical_limit) < 0.001, "As N -> infinity, speedup converges to 1/(1-p)"
    assert abs(theoretical_limit - 10.0) < 1e-6, "Theoretical limit for 10% serial fraction is approximately 10x"

    print(f"    -> Passed: 90% parallelizable code capped at {theoretical_limit:.1f}x speedup regardless of core count.")


def test_gunthers_usl_retrograde():
    print("  [TEST 2] Universal Scalability Law (USL): Contention (σ) & Coherency (κ)...")
    # X(N) = (gamma * N) / (1 + sigma * (N - 1) + kappa * N * (N - 1))
    gamma = 100.0  # Peak throughput per single worker (req/sec)
    sigma = 0.03   # 3% queuing contention
    kappa = 0.001  # 0.1% cross-node coherency penalty (crosstalk)

    def usl_throughput(n: int) -> float:
        denominator = 1.0 + sigma * (n - 1) + kappa * n * (n - 1)
        return (gamma * n) / denominator

    t1 = usl_throughput(1)
    assert abs(t1 - 100.0) < 1e-6

    # Verify retrograde behavior: beyond the optimal concurrency point, adding nodes DECREASES throughput
    throughputs = [usl_throughput(n) for n in range(1, 100)]
    max_throughput = max(throughputs)
    peak_concurrency = throughputs.index(max_throughput) + 1

    # Theoretical peak N_opt = sqrt((1 - sigma) / kappa)
    theoretical_peak = math.sqrt((1.0 - sigma) / kappa)
    assert abs(peak_concurrency - theoretical_peak) <= 2, "Peak concurrency must align with USL theoretical root"

    # Retrograde proof: throughput at 90 workers is strictly LESS than peak
    t_peak = usl_throughput(peak_concurrency)
    t_high = usl_throughput(90)
    assert t_high < t_peak, "Coherency crosstalk must cause retrograde throughput drop at high concurrency"

    print(f"    -> Passed: USL peak identified at N={peak_concurrency} ({t_peak:.1f} req/s); retrograde confirmed at N=90 ({t_high:.1f} req/s).")


def test_quorum_intersection_invariants():
    print("  [TEST 3] Distributed Quorum Intersection Invariant (R + W > N)...")
    N = 5  # Total cluster replicas

    # Case A: Strict Strong Consistency Quorum: W = 3, R = 3 => W + R = 6 > 5
    W_strong = 3
    R_strong = 3
    assert W_strong + R_strong > N, "Quorum intersection condition must be satisfied"

    # Simulate replica subsets
    all_replicas = {1, 2, 3, 4, 5}
    import itertools
    for write_set in itertools.combinations(all_replicas, W_strong):
        for read_set in itertools.combinations(all_replicas, R_strong):
            intersection = set(write_set).intersection(set(read_set))
            assert len(intersection) >= 1, "Any write quorum and read quorum MUST overlap by at least 1 replica"

    # Case B: Weak Quorum / Eventual Consistency: W = 2, R = 2 => W + R = 4 <= 5
    W_weak = 2
    R_weak = 2
    assert W_weak + R_weak <= N
    # Show that disjoint sets are mathematically possible
    write_set_disjoint = {1, 2}
    read_set_disjoint = {3, 4}
    disjoint_intersection = write_set_disjoint.intersection(read_set_disjoint)
    assert len(disjoint_intersection) == 0, "Weak quorums permit stale reads due to empty intersection"

    print("    -> Passed: Strong quorum (3+3>5) mathematically guarantees non-empty overlap across all combinations.")


def test_fencing_token_zombie_defeat():
    print("  [TEST 4] Monotonic Fencing Tokens Defeating Zombie Writes...")
    class StorageEngine:
        def __init__(self):
            self.highest_token = 0
            self.data = {}

        def write(self, key: str, value: str, token: int) -> bool:
            if token <= self.highest_token:
                raise ValueError(f"StaleTokenRejected: Token {token} <= highest observed {self.highest_token}")
            self.highest_token = token
            self.data[key] = value
            return True

    storage = StorageEngine()

    # Step 1: Active primary writes with token 100
    storage.write("leader", "node-1-data", 100)
    assert storage.data["leader"] == "node-1-data"
    assert storage.highest_token == 100

    # Step 2: Node 1 enters long GC pause. Lock service leases to Node 2 with token 101.
    storage.write("leader", "node-2-data", 101)
    assert storage.data["leader"] == "node-2-data"
    assert storage.highest_token == 101

    # Step 3: Node 1 wakes up unaware of timeout and attempts to commit with old token 100
    zombie_rejected = False
    try:
        storage.write("leader", "zombie-node-1-overwrite", 100)
    except ValueError as e:
        if "StaleTokenRejected" in str(e):
            zombie_rejected = True

    assert zombie_rejected, "Storage engine MUST reject zombie write from Node 1"
    assert storage.data["leader"] == "node-2-data", "Storage state must remain preserved as Node 2's commit"

    print("    -> Passed: Monotonic token verification successfully neutralized zombie write attempt.")


def test_raft_majority_quorum_split_brain():
    print("  [TEST 5] Raft Consensus Majority Quorum & Split-Brain Invariant...")
    # In a cluster of N nodes, majority quorum size Q = floor(N / 2) + 1
    # Cluster fault tolerance f = floor((N - 1) / 2)

    cluster_sizes = [(3, 2, 1), (5, 3, 2), (7, 4, 3)]
    for n_nodes, required_quorum, max_failures in cluster_sizes:
        calculated_quorum = (n_nodes // 2) + 1
        calculated_f = (n_nodes - 1) // 2
        assert calculated_quorum == required_quorum
        assert calculated_f == max_failures

    # Network Partition Simulation on 5-Node Cluster: [Node1, Node2] vs [Node3, Node4, Node5]
    partition_minority = {1, 2}
    partition_majority = {3, 4, 5}

    quorum_needed = (5 // 2) + 1  # 3 nodes

    # Minority partition attempts leader election or log commitment
    minority_can_form_quorum = len(partition_minority) >= quorum_needed
    assert not minority_can_form_quorum, "Minority partition (2 nodes) CANNOT form a quorum of 3"

    # Majority partition attempts leader election or log commitment
    majority_can_form_quorum = len(partition_majority) >= quorum_needed
    assert majority_can_form_quorum, "Majority partition (3 nodes) CAN form quorum and make forward progress"

    # Mathematical impossibility of two simultaneous majorities
    # If partition A has size >= 3 and partition B has size >= 3, size A + size B >= 6 > 5 (Pigeonhole principle)
    assert (len(partition_minority) + len(partition_majority)) == 5
    print("    -> Passed: Pigeonhole principle prevents dual majorities; minority partition blocked from split-brain commit.")


def test_bounded_buffer_thread_coordination():
    print("  [TEST 6] Multi-threaded Bounded Buffer Producer-Consumer Invariant...")
    class ThreadSafeBoundedBuffer:
        def __init__(self, capacity: int):
            self.capacity = capacity
            self.queue = deque()
            self.lock = threading.Lock()
            self.not_empty = threading.Condition(self.lock)
            self.not_full = threading.Condition(self.lock)

        def put(self, item: int):
            with self.not_full:
                while len(self.queue) >= self.capacity:
                    self.not_full.wait()
                self.queue.append(item)
                self.not_empty.notify()

        def take(self) -> int:
            with self.not_empty:
                while len(self.queue) == 0:
                    self.not_empty.wait()
                item = self.queue.popleft()
                self.not_full.notify()
                return item

    buffer = ThreadSafeBoundedBuffer(capacity=5)
    TOTAL_ITEMS = 100
    consumed_items = []

    def producer():
        for i in range(TOTAL_ITEMS):
            buffer.put(i)

    def consumer():
        for _ in range(TOTAL_ITEMS):
            val = buffer.take()
            consumed_items.append(val)

    t_prod = threading.Thread(target=producer)
    t_cons = threading.Thread(target=consumer)

    t_prod.start()
    t_cons.start()

    t_prod.join(timeout=3.0)
    t_cons.join(timeout=3.0)

    assert not t_prod.is_alive(), "Producer should finish cleanly"
    assert not t_cons.is_alive(), "Consumer should finish cleanly"
    assert len(consumed_items) == TOTAL_ITEMS, "All items must be received without loss"
    assert consumed_items == list(range(TOTAL_ITEMS)), "FIFO item ordering must be strictly preserved"

    print(f"    -> Passed: BoundedBuffer successfully coordinated {TOTAL_ITEMS} items with zero loss and preserved FIFO ordering.")


def test_phi_accrual_failure_detector():
    print("  [TEST 7] Phi Accrual Failure Detector Invariant...")
    # Phi = -log10(P_later(t - t_last))
    # Under Gaussian assumption with mean heartbeat mu and standard deviation sigma:
    # As elapsed time t grows relative to mu, Phi increases monotonically.

    mean_interval = 1.0   # 1.0 second expected heartbeat
    std_dev = 0.1         # 100ms standard deviation

    def phi_score(elapsed: float) -> float:
        # Standard normal CDF approximation
        z = (elapsed - mean_interval) / std_dev
        if z < -5.0:
            p_later = 1.0
        elif z > 5.0:
            # Extreme tail approximation
            p_later = math.exp(-0.5 * z * z) / (z * math.sqrt(2 * math.pi))
        else:
            # Erf approximation for normal CDF
            p_later = 0.5 * math.erfc(z / math.sqrt(2))
        p_later = max(p_later, 1e-15)
        return -math.log10(p_later)

    phi_on_time = phi_score(1.0)  # Heartbeat on time
    phi_slight_delay = phi_score(1.2)  # 2 std deviations late
    phi_severe_delay = phi_score(1.6)  # 6 std deviations late

    assert phi_on_time < 1.0, "On-time heartbeat must yield very low suspicion score"
    assert phi_slight_delay > phi_on_time, "Suspicion must increase monotonically with delay"
    assert phi_severe_delay > 8.0, "Severe delay must trigger high phi suspicion score (> 8 threshold)"

    print(f"    -> Passed: Phi accrual suspicion scaled monotonically (on-time: {phi_on_time:.2f}, delayed: {phi_severe_delay:.2f}).")


def main() -> int:
    print("=================================================================")
    print(" MODULE 09: CONCURRENCY & DISTRIBUTED SYSTEMS PYTHON HARNESS    ")
    print("=================================================================\n")

    test_amdahls_law_limits()
    test_gunthers_usl_retrograde()
    test_quorum_intersection_invariants()
    test_fencing_token_zombie_defeat()
    test_raft_majority_quorum_split_brain()
    test_bounded_buffer_thread_coordination()
    test_phi_accrual_failure_detector()

    print("\n=================================================================")
    print(" ALL MODULE 09 PYTHON CONCURRENCY INVARIANTS PASSED (7/7)       ")
    print("=================================================================")
    return 0


if __name__ == "__main__":
    sys.exit(main())
