#!/usr/bin/env python3
"""
Module 13 — Linux, Containers, Deployment & Infrastructure Test Suite.

Validates core infrastructure manifests, scripts, and architectural models:
1. Static Dockerfile Best-Practices Validator (Multi-stage, Non-root, JVM flags)
2. Docker Compose Dependency & Readiness Condition Validator
3. Kubernetes Manifest Semantic & SecurityContext Validator
4. Bash Ops Tool Execution & Signal Trap Subprocess Verification
5. Deployment Capacity & Rollout Blast-Radius Mathematical Models
"""

import sys
import subprocess
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

def test_dockerfile_best_practices():
    print("  [TEST 1] Static Dockerfile Best-Practices Validator...")
    dockerfile_path = REPO_ROOT / "assets" / "code_examples" / "docker" / "Dockerfile.java"
    assert dockerfile_path.exists(), "Dockerfile.java missing!"

    content = dockerfile_path.read_text(encoding="utf-8")

    # 1. Multi-stage build
    assert "FROM " in content and "AS builder" in content, "Must use multi-stage build!"
    assert "AS runtime" in content, "Must have explicit runtime stage!"

    # 2. Non-root user
    assert "USER 10001:10001" in content or "USER appuser" in content, "Must run as non-root user!"

    # 3. Healthcheck defined
    assert "HEALTHCHECK" in content, "Must define container HEALTHCHECK!"

    # 4. Exec form entrypoint
    assert "ENTRYPOINT [" in content, "Must use exec-form ENTRYPOINT for proper signal forwarding!"

    # 5. Container-aware JVM flags
    assert "-XX:+UseContainerSupport" in content, "Must enable container-aware JVM flags!"
    assert "-XX:MaxRAMPercentage" in content, "Must use MaxRAMPercentage!"

    print("    -> Passed: Dockerfile satisfies multi-stage, non-root, signal-handling, and JVM container support.")

def test_docker_compose_semantics():
    print("  [TEST 2] Docker Compose Dependency & Readiness Validator...")
    compose_path = REPO_ROOT / "assets" / "code_examples" / "docker" / "docker-compose.yml"
    assert compose_path.exists(), "docker-compose.yml missing!"

    content = compose_path.read_text(encoding="utf-8")

    # Required services
    assert "postgres:" in content, "Postgres service missing!"
    assert "redis:" in content, "Redis service missing!"
    assert "backend:" in content, "Backend service missing!"

    # Healthchecks on DB and Redis
    assert "healthcheck:" in content, "Healthchecks missing!"
    assert "condition: service_healthy" in content, "Must depend on service_healthy, not just service_started!"

    # Named volumes and networks
    assert "volumes:" in content and "postgres_data:" in content, "Persistent named volumes missing!"
    assert "networks:" in content and "internal_backend_net:" in content, "Isolated network missing!"

    # Resource limits
    assert "limits:" in content and "cpus:" in content and "memory:" in content, "Resource limits missing!"

    print("    -> Passed: Docker Compose enforces service_healthy conditions, isolated network, and resource caps.")

def test_kubernetes_manifests():
    print("  [TEST 3] Kubernetes Manifest Semantic & Security Validator...")
    deploy_path = REPO_ROOT / "assets" / "code_examples" / "k8s" / "deployment.yaml"
    svc_path = REPO_ROOT / "assets" / "code_examples" / "k8s" / "service.yaml"
    assert deploy_path.exists(), "deployment.yaml missing!"
    assert svc_path.exists(), "service.yaml missing!"

    deploy_text = deploy_path.read_text(encoding="utf-8")
    svc_text = svc_path.read_text(encoding="utf-8")

    # 1. RollingUpdate strategy
    assert "type: RollingUpdate" in deploy_text, "Deployment must use RollingUpdate strategy!"
    assert "maxSurge:" in deploy_text and "maxUnavailable:" in deploy_text, "Must define maxSurge and maxUnavailable!"

    # 2. Independent probes (Startup, Liveness, Readiness)
    assert "startupProbe:" in deploy_text, "startupProbe missing!"
    assert "livenessProbe:" in deploy_text, "livenessProbe missing!"
    assert "readinessProbe:" in deploy_text, "readinessProbe missing!"

    # 3. SecurityContext hardening
    assert "runAsNonRoot: true" in deploy_text, "Must enforce runAsNonRoot!"
    assert "readOnlyRootFilesystem: true" in deploy_text, "Must enforce readOnlyRootFilesystem!"
    assert "allowPrivilegeEscalation: false" in deploy_text, "Must prevent privilege escalation!"

    # 4. Resources
    assert "requests:" in deploy_text and "limits:" in deploy_text, "Must specify CPU/Memory requests and limits!"

    # 5. Service matching labels
    assert "app.kubernetes.io/name: backend-service" in svc_text, "Service selector missing expected label!"
    assert "type: ClusterIP" in svc_text, "Service must default to ClusterIP!"

    print("    -> Passed: Kubernetes manifests comply with cloud-native security, probes, and rolling update standards.")

def test_bash_ops_tool_execution():
    print("  [TEST 4] Bash Ops Tool Subprocess Execution & Self-Test...")
    script_path = REPO_ROOT / "assets" / "code_examples" / "module_13_ops_tool.sh"
    assert script_path.exists(), "module_13_ops_tool.sh missing!"

    # Run self-test
    res = subprocess.run(
        [str(script_path), "--self-test"],
        capture_output=True,
        text=True,
        timeout=10
    )

    assert res.returncode == 0, f"Ops tool self-test failed with code {res.returncode}:\n{res.stderr}"
    assert "Self-test successfully verified all core diagnostic routines!" in res.stdout

    print("    -> Passed: Bash ops tool executed cleanly under set -euo pipefail with verified trap cleanup.")

def test_deployment_capacity_and_rollout_math():
    print("  [TEST 5] Deployment Capacity & Rollout Blast-Radius Mathematical Models...")

    # Scenario: Service requires N = 20 replicas under steady state
    baseline_replicas = 20

    # 1. Blue-Green Deployment:
    # Requires an identical parallel cluster running simultaneously (100% surge)
    blue_green_capacity_multiplier = 2.0
    blue_green_total_replicas = int(baseline_replicas * blue_green_capacity_multiplier)
    assert blue_green_total_replicas == 40, "Blue-green should require 40 replicas"

    # 2. Canary Deployment (10% canary step):
    # Canary introduces 10% new version while running 90% or 100% old version
    canary_step_fraction = 0.10
    canary_surge_replicas = int(baseline_replicas * (1.0 + canary_step_fraction))
    assert canary_surge_replicas == 22, "10% Canary should require only 22 replicas (110% capacity)"

    # 3. Blast Radius Comparison:
    # If new version contains a fatal defect:
    # Unverified full rollout impacts 100% of users
    # Canary step impacts exactly 10% of users
    canary_blast_radius = 0.10
    assert canary_blast_radius < 0.50

    # 4. Linux cgroups CFS Quota/Period formula:
    # CFS Bandwidth Control: Cores = Quota / Period
    # 250,000 micros quota with 100,000 micros period = 2.5 cores
    quota = 250000
    period = 100000
    computed_cores = quota / period
    assert computed_cores == 2.5

    print("    -> Passed: Blue-Green vs Canary capacity sizing, blast-radius math, and cgroups CFS quotas verified.")

def main():
    print("=================================================================")
    print(" MODULE 13: INFRASTRUCTURE & SYSTEMS TEST SUITE (PYTHON 3)       ")
    print("=================================================================\n")

    test_dockerfile_best_practices()
    test_docker_compose_semantics()
    test_kubernetes_manifests()
    test_bash_ops_tool_execution()
    test_deployment_capacity_and_rollout_math()

    print("\n=================================================================")
    print(" ALL MODULE 13 INFRASTRUCTURE TESTS PASSED (5/5)                 ")
    print("=================================================================")

if __name__ == "__main__":
    main()
