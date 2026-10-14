#!/usr/bin/env bash
# ==============================================================================
# Module 13 — Linux Operations & System Diagnostics Automation Tool
#
# Production-grade operational Bash script demonstrating:
# 1. Strict execution settings: set -euo pipefail
# 2. POSIX signal trapping & atomic tempfile cleanup (EXIT, INT, TERM)
# 3. Resource diagnostic functions (CPU, Memory, Disk, Inodes, Process, Network)
# 4. OS portability (macOS Darwin vs Linux kernel detection)
# 5. Deterministic self-test verification mode
# ==============================================================================

set -euo pipefail

# Setup temporary scratch space
TMP_DIR="$(mktemp -d -t ops_tool_scratch.XXXXXX)"

cleanup() {
    local exit_code=$?
    if [[ -d "${TMP_DIR}" ]]; then
        rm -rf "${TMP_DIR}"
    fi
    exit "${exit_code}"
}
trap cleanup EXIT INT TERM

# Detect Operating System
OS="$(uname -s)"

# ==============================================================================
# DIAGNOSTIC FUNCTIONS
# ==============================================================================

check_cpu() {
    echo "=== CPU & LOAD DIAGNOSTICS ==="
    echo "OS Kernel: ${OS} $(uname -r) ($(uname -m))"
    echo -n "Load Average: "
    uptime | awk -F'load averages?:' '{print $2}' || uptime
    echo "CPU Core Count: $(getconf _NPROCESSORS_ONLN 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 1)"
    echo "=============================="
}

check_memory() {
    echo "=== MEMORY & SWAP DIAGNOSTICS ==="
    if [[ "${OS}" == "Linux" ]]; then
        if command -v free >/dev/null 2>&1; then
            free -m
        elif [[ -f /proc/meminfo ]]; then
            grep -E "MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree" /proc/meminfo
        fi
    else
        # macOS fallback
        vm_stat | head -n 8
    fi
    echo "================================="
}

check_disk() {
    echo "=== DISK SPACE & INODE DIAGNOSTICS ==="
    echo "-- Filesystem Capacity --"
    df -h .
    echo "-- Inode Allocation --"
    df -i .
    echo "======================================"
}

inspect_process() {
    local pid="$1"
    echo "=== PROCESS DIAGNOSTICS (PID: ${pid}) ==="
    if ps -p "${pid}" >/dev/null 2>&1; then
        ps -o pid,ppid,user,%cpu,%mem,stat,comm -p "${pid}"
    else
        echo "Error: Process with PID ${pid} does not exist." >&2
        return 1
    fi
    echo "========================================"
}

check_port() {
    local port="$1"
    echo "=== LISTENING SOCKETS (PORT: ${port}) ==="
    if command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:"${port}" -sTCP:LISTEN -n -P || echo "No process currently listening on port ${port}."
    else
        echo "lsof command not available."
    fi
    echo "========================================"
}

self_test() {
    echo "=== EXECUTING OPS TOOL SELF-TEST ==="
    check_cpu > "${TMP_DIR}/cpu.log"
    check_memory > "${TMP_DIR}/mem.log"
    check_disk > "${TMP_DIR}/disk.log"
    inspect_process "$$" > "${TMP_DIR}/proc.log"

    # Verify temp files were populated
    [[ -s "${TMP_DIR}/cpu.log" ]] || { echo "Self-test failed: CPU log empty" >&2; return 1; }
    [[ -s "${TMP_DIR}/mem.log" ]] || { echo "Self-test failed: Memory log empty" >&2; return 1; }
    [[ -s "${TMP_DIR}/disk.log" ]] || { echo "Self-test failed: Disk log empty" >&2; return 1; }
    [[ -s "${TMP_DIR}/proc.log" ]] || { echo "Self-test failed: Process log empty" >&2; return 1; }

    echo "Self-test successfully verified all core diagnostic routines!"
    echo "===================================="
}

# ==============================================================================
# MAIN ENTRYPOINT & CLI PARSING
# ==============================================================================

show_usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  --check-cpu          Display CPU load average and core count
  --check-memory       Display memory and swap statistics
  --check-disk         Display disk capacity and inode consumption
  --inspect-pid <PID>  Inspect details of a running process
  --check-port <PORT>  Check listening sockets on specified TCP port
  --self-test          Execute automated self-test verification
  --help               Display this help message
EOF
}

main() {
    if [[ $# -eq 0 ]]; then
        show_usage
        exit 1
    fi

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --check-cpu)
                check_cpu
                shift
                ;;
            --check-memory)
                check_memory
                shift
                ;;
            --check-disk)
                check_disk
                shift
                ;;
            --inspect-pid)
                if [[ -z "${2:-}" ]]; then
                    echo "Error: --inspect-pid requires a PID argument." >&2
                    exit 1
                fi
                inspect_process "$2"
                shift 2
                ;;
            --check-port)
                if [[ -z "${2:-}" ]]; then
                    echo "Error: --check-port requires a PORT argument." >&2
                    exit 1
                fi
                check_port "$2"
                shift 2
                ;;
            --self-test)
                self_test
                shift
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            *)
                echo "Unknown option: $1" >&2
                show_usage
                exit 1
                ;;
        esac
    done
}

main "$@"
