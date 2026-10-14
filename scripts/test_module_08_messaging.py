#!/usr/bin/env python3
"""
Module 08 — Asynchronous Processing & Distributed Messaging Verification Suite.

Implements analytical and deterministic simulations of messaging invariants in Python 3:
1. Queueing Theory & Little's Law Invariant: L = lambda * W
2. Kafka Partition Hashing, Key Skew & Hot Partition Impact on Consumer Groups
3. Consumer Lag Accumulation & Backpressure Under Load Saturation
4. Transactional Outbox Pattern with At-Least-Once Replay Safety
5. Poison Message Quarantine, DLQ Lifecycle, and Redrive Simulation
"""

import hashlib
import sys
import time
from collections import defaultdict, deque
from typing import Any, Dict, List, Optional


def test_queueing_theory_littles_law():
    print("  [TEST 1] Queueing Theory & Little's Law Invariant (L = λ * W)...")
    # Arrival rate lambda = 100 requests / sec
    # Average service time W = 0.05 seconds (50ms)
    arrival_rate = 100.0  # requests / second
    service_time = 0.05   # seconds

    expected_concurrency = arrival_rate * service_time  # 5 concurrent requests
    assert abs(expected_concurrency - 5.0) < 1e-6

    # When service time spikes to 200ms due to downstream lock contention
    degraded_service_time = 0.20  # 200ms
    degraded_concurrency = arrival_rate * degraded_service_time  # 20 concurrent requests
    assert abs(degraded_concurrency - 20.0) < 1e-6

    # Utilization rho = lambda / (c * mu), where c = worker count, mu = 1/service_time
    workers = 6
    mu = 1.0 / service_time  # 20 req/sec per worker
    capacity = workers * mu  # 120 req/sec total capacity
    utilization = arrival_rate / capacity
    assert abs(utilization - (100.0 / 120.0)) < 1e-6

    print(f"    -> Passed: Little's Law confirmed: L={expected_concurrency} -> {degraded_concurrency} under degradation.")


def test_partition_hashing_and_hot_partitions():
    print("  [TEST 2] Partition Hashing, Key Skew, and Hot Partition Bottlenecks...")
    partition_count = 4

    def compute_partition(key: str) -> int:
        # Standard Murmur2 / MD5 hash modulo simulation
        digest = hashlib.md5(key.encode("utf-8")).hexdigest()
        return int(digest, 16) % partition_count

    # Distribute 10,000 normal random keys
    partition_distribution = defaultdict(int)
    for i in range(10000):
        key = f"user_{i}"
        p = compute_partition(key)
        partition_distribution[p] += 1

    # Verify uniform distribution across partitions (within 5% variance)
    for p in range(partition_count):
        count = partition_distribution[p]
        assert 2200 <= count <= 2800, f"Partition {p} count {count} deviates significantly from uniform"

    # Now simulate a massive celebrity / enterprise hot key skew
    # E.g., Walmart or Nike sends 50,000 orders with the exact same partition key "tenant_enterprise_01"
    hot_partition = compute_partition("tenant_enterprise_01")
    skewed_distribution = defaultdict(int)
    for i in range(50000):
        skewed_distribution[hot_partition] += 1

    assert skewed_distribution[hot_partition] == 50000
    # Demonstrates that in a 4-node consumer group, 1 consumer handles 100% of this tenant's load,
    # causing that consumer to lag while the other 3 consumers sit idle!
    print(f"    -> Passed: Uniform hashing verified; hot partition skew demonstrated on partition {hot_partition}.")


def test_consumer_lag_and_backpressure():
    print("  [TEST 3] Consumer Lag Accumulation & Backpressure Under Saturation...")
    # Producer generates 200 msgs/sec
    # Consumer can only process 100 msgs/sec
    arrival_rate = 200
    consume_rate = 100
    seconds = 5

    lag = 0
    lag_timeline = []
    for s in range(1, seconds + 1):
        produced = arrival_rate
        consumed = consume_rate
        lag += (produced - consumed)
        lag_timeline.append(lag)

    assert lag_timeline == [100, 200, 300, 400, 500], f"Lag accumulation unexpected: {lag_timeline}"
    # Unbounded queueing without backpressure leads to memory saturation / SLA breach
    print("    -> Passed: Consumer lag accumulation mathematically modeled under arrival overload.")


def test_transactional_outbox_and_deduplication():
    print("  [TEST 4] Transactional Outbox Pattern & Consumer Deduplication...")
    outbox_table = []
    broker_topic = []
    consumer_processed_set = set()
    consumer_execution_count = 0

    # Step 1: Database writes business entity + outbox event in same ACID tx
    outbox_table.append({"event_id": "evt_101", "type": "OrderPlaced", "order_id": "ord_999"})

    # Step 2: Relay sends event to broker, but crashes before deleting outbox row
    broker_topic.append(outbox_table[0])
    # Relay crashes, restarts, and re-polls outbox -> sends DUPLICATE to broker!
    broker_topic.append(outbox_table[0])

    assert len(broker_topic) == 2, "Broker received duplicate due to relay failure window"

    # Step 3: Idempotent Consumer processes messages from broker
    for msg in broker_topic:
        msg_id = msg["event_id"]
        if msg_id in consumer_processed_set:
            continue  # Idempotently ignored
        consumer_processed_set.add(msg_id)
        consumer_execution_count += 1

    assert consumer_execution_count == 1, "Consumer must execute business logic exactly once!"
    print("    -> Passed: Outbox relay duplicate delivery safely neutralized by idempotent consumer.")


def test_dlq_quarantine_and_redrive():
    print("  [TEST 5] Poison Message Quarantine, DLQ Lifecycle, and Redrive...")
    main_queue = deque()
    dlq = []
    max_retries = 3

    main_queue.append({"id": "msg_ok", "payload": "valid_json", "attempts": 0})
    main_queue.append({"id": "msg_poison", "payload": "corrupt_data", "attempts": 0})

    processed = []

    while main_queue:
        msg = main_queue.popleft()
        msg["attempts"] += 1

        if msg["payload"] == "corrupt_data":
            if msg["attempts"] >= max_retries:
                # Terminal quarantine
                dlq.append({
                    "message": msg,
                    "reason": "Unrecoverable deserialization exception",
                    "quarantined_at": time.time()
                })
            else:
                main_queue.append(msg)  # Retry
        else:
            processed.append(msg["id"])

    assert processed == ["msg_ok"]
    assert len(dlq) == 1
    assert dlq[0]["message"]["id"] == "msg_poison"
    assert dlq[0]["message"]["attempts"] == 3

    # Redrive operation: patch poison message and re-enqueue
    quarantined = dlq.pop(0)
    repaired_msg = quarantined["message"]
    repaired_msg["payload"] = "valid_json_repaired"
    repaired_msg["attempts"] = 0
    main_queue.append(repaired_msg)

    # Process redrive
    redriven = main_queue.popleft()
    processed.append(redriven["id"])

    assert processed == ["msg_ok", "msg_poison"]
    assert len(dlq) == 0
    print("    -> Passed: Poison message isolated after 3 retries, repaired, and successfully redriven.")


def main():
    print("=================================================================")
    print("  MODULE 08 ASYNC & DISTRIBUTED MESSAGING TEST SUITE (PYTHON 3)  ")
    print("=================================================================")

    test_queueing_theory_littles_law()
    test_partition_hashing_and_hot_partitions()
    test_consumer_lag_and_backpressure()
    test_transactional_outbox_and_deduplication()
    test_dlq_quarantine_and_redrive()

    print("\nAll 5 Python Messaging & Asynchronous Test Suites Passed Successfully!")


if __name__ == "__main__":
    main()
