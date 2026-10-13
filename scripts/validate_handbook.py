#!/usr/bin/env python3
"""
FAANG Engineering Handbook Validator
Validates structural integrity, section completeness, code fence balance,
placeholder absence, and quality ledger compliance across the handbook repository.
"""

import sys
import re
from pathlib import Path

# Required Foundation Files
REQUIRED_FOUNDATION_FILES = [
    "README.md",
    "CURRICULUM_MASTER.md",
    "RESUME_TECHNOLOGY_INVENTORY.md",
    "CONTENT_STANDARDS.md",
    "PDF_SPECIFICATION.md",
    "QUALITY_LEDGER.md",
    "REVIEW_PROTOCOL.md",
]

# Required Section Titles for Module 1
MODULE_01_REQUIRED_SECTIONS = [
    "Section 1 — How to Think About Algorithms",
    "Section 2 — Complexity Analysis",
    "Section 3 — Arrays and Strings",
    "Section 4 — Hashing and Hash-Based Collections",
    "Section 5 — Two Pointers",
    "Section 6 — Sliding Window",
    "Section 7 — Prefix Sums and Range Reasoning",
    "Section 8 — Binary Search",
    "Section 9 — Linked Lists",
    "Section 10 — Stacks and Queues",
    "Section 11 — Trees and Binary Search Trees",
    "Section 12 — Heaps and Priority Queues",
    "Section 13 — Graph Fundamentals and Traversal",
    "Section 14 — Essential Graph Algorithms",
    "Section 15 — Recursion and Backtracking",
    "Section 16 — Greedy Algorithms",
    "Section 17 — Dynamic Programming",
    "Section 18 — Sorting and Searching Trade-Offs",
    "Section 19 — Pattern Recognition and Problem-Solving Method",
    "Section 20 — Interview Practice and Self-Assessment",
    "Section 21 — DSA Cheatsheet: Interview Revision Reference",
]

# Required Section Titles for Module 2
MODULE_02_REQUIRED_SECTIONS = [
    "Section 1 — The Operating System’s Role",
    "Section 2 — Processes and Threads",
    "Section 3 — CPU Scheduling and Context Switching",
    "Section 4 — Concurrency, Synchronization, and Deadlocks",
    "Section 5 — Memory Management and Virtual Memory",
    "Section 6 — Files, I/O, and Blocking Behavior",
    "Section 7 — Networking Fundamentals",
    "Section 8 — TCP, UDP, and Connection Lifecycle",
    "Section 9 — DNS, HTTP, HTTPS, and TLS",
    "Section 10 — Latency, Timeouts, and Network Diagnostics",
    "Section 11 — End-to-End Backend Request",
    "Section 12 — Interview Questions and Self-Assessment",
    "Section 13 — OS and Computer Networks Cheatsheet",
]

# Required Section Titles for Module 3
MODULE_03_REQUIRED_SECTIONS = [
    "Section 1 — Java’s Execution Model and Architecture",
    "Section 2 — Primitive Types, References, and Value Semantics",
    "Section 3 — Classes, Objects, and Initialization Mechanics",
    "Section 4 — The Four OOP Principles in Practice",
    "Section 5 — Overloading, Overriding, and Dynamic Dispatch",
    "Section 6 — Abstract Classes and Interfaces",
    "Section 7 — Inheritance, Composition, and SOLID Design",
    "Section 8 — Object Methods, Equality, and Hashing Contracts",
    "Section 9 — Strings, Immutability, and Defensive Design",
    "Section 10 — Generics and the Type System",
    "Section 11 — Exceptions and Fault-Tolerant Error Handling",
    "Section 12 — Collections Framework and Core API Semantics",
    "Section 13 — Lambdas, Functional Interfaces, and Streams",
    "Section 14 — Enums, Records, Annotations, and Modern Java",
    "Section 15 — JVM Memory Architecture, Garbage Collection, and JIT",
    "Section 16 — High-Value Interview Reasoning and Question Bank",
    "Section 17 — Java and OOP Cheatsheet: Interview Revision Reference",
]

# Required Section Titles for Module 4
MODULE_04_REQUIRED_SECTIONS = [
    "Section 1 — Relational Database Foundations: Mathematical Model, Schemas, Keys, and Three-Valued Logic",
    "Section 2 — SQL Fundamentals: Declarative Syntax, Logical Query Processing, and Filtering",
    "Section 3 — Joins and Set Operations: Mechanics, Predicates, Semi/Anti-Joins, and Cardinality Traps",
    "Section 4 — Data Modeling and Normalization: Dependencies, Normal Forms, and Schema Evolution",
    "Section 5 — SQL Query Reasoning: Aggregations, Window Functions, CTEs, and Complex Patterns",
    "Section 6 — Indexing and Physical Storage: B+ Trees, Index Topologies, Selectivity, and Write Amplification",
    "Section 7 — Query Optimization and Execution Plans: Cost Model, Scans, Join Algorithms, and Sargability",
    "Section 8 — Transactions and the ACID Paradigm: State Boundaries, Durability, and Invariant Preservation",
    "Section 9 — Isolation Levels and Concurrency Anomalies: Dirty Reads, Phantom Reads, Lost Updates, and Write Skew",
    "Section 10 — Locking, MVCC, and Database Engine Internals: Multi-Version Snapshots, Locks, and Vacuuming",
    "Section 11 — Constraints, Invariant Enforcement, and Safe Schema Migrations: Expand-and-Contract Architecture",
    "Section 12 — PostgreSQL vs. MySQL: Engine Differences, Dialect Nuances, and Production Tradeoffs",
    "Section 13 — Database Security, Reliability, and Recovery: SQL Injection, Parameterization, WAL, and PITR",
    "Section 14 — Backend Application Access: Connection Pooling, HikariCP, ORM Tradeoffs, and N+1 Queries",
    "Section 15 — High-Value Database Interview Reasoning: Question Bank and Deep Failure Analysis",
    "Section 16 — Module 4 Cheatsheet: Database Engineering & SQL",
]

# Required Section Titles for Module 5
MODULE_05_REQUIRED_SECTIONS = [
    "Section 1 — Backend Application Architecture: The Request Lifecycle, Layering, and Abstraction Boundaries",
    "Section 2 — The Node.js Runtime Model: V8, libuv, the Event Loop, and Asynchronous I/O",
    "Section 3 — Express.js Request Pipeline: Routing, Middleware Chaining, and Error Handling",
    "Section 4 — TypeScript for Backend Engineering: Structural Typing, Trust Boundaries, and Type Erasure",
    "Section 5 — Java Web Application Foundations: Servlets, the DispatcherServlet, and Container Architecture",
    "Section 6 — Spring Core and Dependency Injection: Inversion of Control, Bean Scopes, and Lifecycle Mechanics",
    "Section 7 — Spring Boot Application Engineering: Auto-Configuration, Starters, and Externalized Config",
    "Section 8 — Spring MVC and Validation: Request Mapping, Content Negotiation, and Centralized Advice",
    "Section 9 — Persistence Integration with JPA and Hibernate: Persistence Context, Dirty Checking, and Entity Lifecycles",
    "Section 10 — Spring Declarative Transaction Management: Proxies, Self-Invocation Traps, and Rollback Rules",
    "Section 11 — Node.js Modular Application Architecture: Clean Layering, Async Resources, and Graceful Shutdown",
    "Section 12 — Testing Backend Applications: Unit vs. Integration Testing, Test Doubles, and Database Boundaries",
    "Section 13 — Production Observability, Diagnostics, and Debugging: Structured Logging, Health Checks, and Starvation",
    "Section 14 — Spring Boot vs. Node.js & Express: Comprehensive Architectural Comparison and Selection Guide",
    "Section 15 — High-Value Backend Interview Reasoning: Question Bank and Deep Failure Analysis",
    "Section 16 — Module 5 Cheatsheet: Backend Frameworks & Application Engineering",
]

PLACEHOLDER_PATTERNS = [
    re.compile(r"\bTODO\b", re.IGNORECASE),
    re.compile(r"\bTBD\b", re.IGNORECASE),
    re.compile(r"\[placeholder\]", re.IGNORECASE),
    re.compile(r"\[insert\s+[^\]]+\]", re.IGNORECASE),
    re.compile(r"\bcoming\s+soon\b", re.IGNORECASE),
    re.compile(r"\blorem\s+ipsum\b", re.IGNORECASE),
]

def check_file_exists(repo_root: Path, rel_path: str) -> tuple[bool, str]:
    path = repo_root / rel_path
    if not path.exists():
        return False, f"File missing: {rel_path}"
    if path.stat().st_size == 0:
        return False, f"File is empty (0 bytes): {rel_path}"
    return True, f"Found {rel_path} ({path.stat().st_size} bytes)"

def check_code_fences(file_path: Path) -> tuple[bool, str]:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    lines = content.splitlines()
    in_code_block = False
    fence_len = 0
    unbalanced_line = -1

    for line_idx, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith("```"):
            m = re.match(r"^(`{3,})", stripped)
            if m:
                flen = len(m.group(1))
                if not in_code_block:
                    in_code_block = True
                    fence_len = flen
                    unbalanced_line = line_idx
                else:
                    if flen >= fence_len:
                        in_code_block = False
                        fence_len = 0
                        unbalanced_line = -1

    if in_code_block:
        return False, f"Unbalanced code block fence starting at line {unbalanced_line} in {file_path.name}"
    return True, f"Code block fences balanced in {file_path.name}"

def check_placeholders(file_path: Path) -> tuple[bool, list[str]]:
    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()
    
    findings = []
    in_code = False
    for idx, line in enumerate(lines, 1):
        if line.strip().startswith("```"):
            in_code = not in_code
            continue
        for pat in PLACEHOLDER_PATTERNS:
            match = pat.search(line)
            if match:
                snippet = line.strip()[:80]
                findings.append(f"Line {idx}: '{match.group(0)}' in: {snippet}")
    
    if findings:
        return False, findings
    return True, []

def check_sections(file_path: Path, required_sections: list[str]) -> tuple[bool, list[str]]:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    missing = []
    for sec in required_sections:
        escaped = re.escape(sec)
        pattern = re.compile(rf"^#+\s+.*{escaped}", re.MULTILINE | re.IGNORECASE)
        sec_relaxed = re.escape(sec.replace("—", "-")).replace(r"\-", r"[-—–]")
        pattern_relaxed = re.compile(rf"^#+\s+.*{sec_relaxed}", re.MULTILINE | re.IGNORECASE)
        
        if not (pattern.search(content) or pattern_relaxed.search(content)):
            missing.append(sec)
            
    if missing:
        return False, missing
    return True, []

def check_ledger_status(ledger_path: Path) -> tuple[bool, str]:
    if not ledger_path.exists():
        return False, "QUALITY_LEDGER.md missing"
    
    with open(ledger_path, "r", encoding="utf-8") as f:
        content = f.read()
        
    if "modules/module_01_dsa.md" not in content and "MOD-01-DSA" not in content:
        return False, "Module 1 is not recorded in QUALITY_LEDGER.md"
        
    if "modules/module_02_os_networks.md" not in content and "MOD-02-OS-NET" not in content:
        return False, "Module 2 is not recorded in QUALITY_LEDGER.md"

    if "modules/module_03_java_oop.md" not in content and "MOD-03-JAVA-OOP" not in content:
        return False, "Module 3 is not recorded in QUALITY_LEDGER.md"

    if "modules/module_04_database_engineering.md" not in content and "MOD-04-DB-SQL" not in content:
        return False, "Module 4 is not recorded in QUALITY_LEDGER.md"

    if "modules/module_05_backend_frameworks.md" not in content and "MOD-05-BACKEND" not in content:
        return False, "Module 5 is not recorded in QUALITY_LEDGER.md"

    lines = content.splitlines()

    # Verify Module 1 is recorded as USER APPROVED
    mod1_approved = False
    for line in lines:
        if "module_01_dsa.md" in line or "**01**" in line:
            if "USER APPROVED" in line:
                mod1_approved = True
                break
    if not mod1_approved:
        return False, "Module 1 must be marked 'USER APPROVED' reflecting explicit human authorization."

    # Verify Module 2 is recorded as USER APPROVED
    mod2_approved = False
    for line in lines:
        if "module_02_os_networks.md" in line or "**02**" in line:
            if "USER APPROVED" in line:
                mod2_approved = True
                break
    if not mod2_approved:
        return False, "Module 2 must be marked 'USER APPROVED' reflecting explicit human authorization."

    # Verify Module 3 is recorded as USER APPROVED
    mod3_approved = False
    for line in lines:
        if "module_03_java_oop.md" in line or "**03**" in line:
            if "USER APPROVED" in line:
                mod3_approved = True
                break
    if not mod3_approved:
        return False, "Module 3 must be marked 'USER APPROVED' reflecting explicit human authorization."

    # Verify Module 4 is recorded as USER APPROVED
    mod4_approved = False
    for line in lines:
        if "module_04_database_engineering.md" in line or "**04**" in line:
            if "USER APPROVED" in line:
                mod4_approved = True
                break
    if not mod4_approved:
        return False, "Module 4 must be marked 'USER APPROVED' reflecting explicit human authorization."

    # Ensure Module 5 is NOT marked USER APPROVED prematurely
    for line in lines:
        if "module_05_backend_frameworks.md" in line or "**05**" in line:
            if "USER APPROVED" in line:
                return False, "VIOLATION: Module 5 is marked 'USER APPROVED' in the ledger! Only human users may grant approval."
                
    return True, "Quality ledger correctly tracks Modules 1-4 (USER APPROVED) and Module 5 (READY FOR USER REVIEW)"

def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    print("=================================================================")
    print("       FAANG ENGINEERING HANDBOOK - VALIDATION SUITE            ")
    print("=================================================================")
    print(f"Target Repository: {repo_root}\n")
    
    failures = []
    passes = []
    
    # 1. Foundation Files
    print("[1/10] Validating Foundation Files...")
    for rel_path in REQUIRED_FOUNDATION_FILES:
        ok, msg = check_file_exists(repo_root, rel_path)
        if ok:
            passes.append(f"FOUNDATION: {msg}")
            print(f"  [PASS] {msg}")
        else:
            failures.append(f"FOUNDATION: {msg}")
            print(f"  [FAIL] {msg}")
            
    # 2. Module 1 File Existence and Integrity (Approved)
    print("\n[2/10] Validating Module 1 Source (Approved)...")
    mod1_path = repo_root / "modules" / "module_01_dsa.md"
    ok, msg = check_file_exists(repo_root, "modules/module_01_dsa.md")
    if ok:
        passes.append(f"MODULE 1: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"MODULE 1: {msg}")
        print(f"  [FAIL] {msg}")
        
    if mod1_path.exists():
        ok, missing = check_sections(mod1_path, MODULE_01_REQUIRED_SECTIONS)
        if ok:
            passes.append("MODULE 1 SECTIONS: All 21 sections present.")
            print("  [PASS] All 21 required sections present.")
        else:
            failures.append(f"MODULE 1 SECTIONS: Missing: {missing}")
            print(f"  [FAIL] Missing {len(missing)} section(s)")

        with open(mod1_path, "r", encoding="utf-8") as f:
            mod1_text = f.read()
        major1 = re.findall(r"^##\s+Section\s+(\d+)\s*[-—–]\s*(.+)$", mod1_text, re.MULTILINE)
        if major1 and major1[-1][0] == "21":
            passes.append("MODULE 1 FINAL SECTION: Section 21 verified final.")
            print(f"  [PASS] Section 21 verified as final section.")
        else:
            failures.append("MODULE 1 FINAL SECTION: Section 21 is not final.")
            print("  [FAIL] Section 21 is not final.")

    # 3. Module 2 File Existence and Integrity (Approved)
    print("\n[3/10] Validating Module 2 Source (Approved)...")
    mod2_path = repo_root / "modules" / "module_02_os_networks.md"
    ok, msg = check_file_exists(repo_root, "modules/module_02_os_networks.md")
    if ok:
        passes.append(f"MODULE 2: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"MODULE 2: {msg}")
        print(f"  [FAIL] {msg}")

    if mod2_path.exists():
        ok, missing = check_sections(mod2_path, MODULE_02_REQUIRED_SECTIONS)
        if ok:
            passes.append("MODULE 2 SECTIONS: All 13 sections present.")
            print("  [PASS] All 13 required sections present.")
        else:
            failures.append(f"MODULE 2 SECTIONS: Missing sections: {missing}")
            print(f"  [FAIL] Missing {len(missing)} section(s):")
            for m in missing:
                print(f"         - {m}")

        with open(mod2_path, "r", encoding="utf-8") as f:
            mod2_text = f.read()
        major2 = re.findall(r"^##\s+Section\s+(\d+)\s*[-—–]\s*(.+)$", mod2_text, re.MULTILINE)
        if major2 and major2[-1][0] == "13":
            passes.append("MODULE 2 FINAL SECTION: Section 13 verified final.")
            print(f"  [PASS] Section 13 verified as final major section: '{major2[-1][1].strip()}'")
        else:
            failures.append("MODULE 2 FINAL SECTION: Section 13 is not final.")
            print("  [FAIL] Section 13 is not final.")

    # 4. Module 3 File Existence and Integrity (Approved)
    print("\n[4/10] Validating Module 3 Source (Approved)...")
    mod3_path = repo_root / "modules" / "module_03_java_oop.md"
    ok, msg = check_file_exists(repo_root, "modules/module_03_java_oop.md")
    if ok:
        passes.append(f"MODULE 3: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"MODULE 3: {msg}")
        print(f"  [FAIL] {msg}")

    if mod3_path.exists():
        ok, missing = check_sections(mod3_path, MODULE_03_REQUIRED_SECTIONS)
        if ok:
            passes.append("MODULE 3 SECTIONS: All 17 sections present.")
            print("  [PASS] All 17 required sections present.")
        else:
            failures.append(f"MODULE 3 SECTIONS: Missing sections: {missing}")
            print(f"  [FAIL] Missing {len(missing)} section(s):")
            for m in missing:
                print(f"         - {m}")

        with open(mod3_path, "r", encoding="utf-8") as f:
            mod3_text = f.read()
        major3 = re.findall(r"^##\s+Section\s+(\d+)\s*[-—–]\s*(.+)$", mod3_text, re.MULTILINE)
        if major3 and major3[-1][0] == "17":
            passes.append("MODULE 3 FINAL SECTION: Section 17 verified final.")
            print(f"  [PASS] Section 17 verified as final major section: '{major3[-1][1].strip()}'")
        else:
            failures.append("MODULE 3 FINAL SECTION: Section 17 is not final.")
            print("  [FAIL] Section 17 is not final.")

    # 5. Module 4 File Existence, Section Completeness & Cheatsheet Final Section Check (Approved)
    print("\n[5/10] Validating Module 4 Source (Approved - 16 Sections)...")
    mod4_path = repo_root / "modules" / "module_04_database_engineering.md"
    ok, msg = check_file_exists(repo_root, "modules/module_04_database_engineering.md")
    if ok:
        passes.append(f"MODULE 4: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"MODULE 4: {msg}")
        print(f"  [FAIL] {msg}")

    if mod4_path.exists():
        ok, missing = check_sections(mod4_path, MODULE_04_REQUIRED_SECTIONS)
        if ok:
            passes.append("MODULE 4 SECTIONS: All 16 sections present.")
            print("  [PASS] All 16 required sections present.")
        else:
            failures.append(f"MODULE 4 SECTIONS: Missing sections: {missing}")
            print(f"  [FAIL] Missing {len(missing)} section(s):")
            for m in missing:
                print(f"         - {m}")

        with open(mod4_path, "r", encoding="utf-8") as f:
            mod4_text = f.read()
        major4 = re.findall(r"^##\s+Section\s+(\d+)\s*[-—–]\s*(.+)$", mod4_text, re.MULTILINE)
        if major4:
            last4_num, last4_title = major4[-1]
            if last4_num == "16":
                passes.append("MODULE 4 FINAL SECTION: Section 16 is verified as the final major section.")
                print(f"  [PASS] Section 16 is verified as the final major section: '{last4_title.strip()}'")
            else:
                failures.append(f"MODULE 4 FINAL SECTION: Expected Section 16 to be final, found Section {last4_num}.")
                print(f"  [FAIL] Expected Section 16 to be final, found Section {last4_num}.")
        else:
            failures.append("MODULE 4 FINAL SECTION: No major sections found in Module 4.")
            print("  [FAIL] No major sections found in Module 4.")

    # 6. Module 5 File Existence, Section Completeness & Cheatsheet Final Section Check (Ready for Review)
    print("\n[6/10] Validating Module 5 Source (Ready for Review - 16 Sections including Cheatsheet)...")
    mod5_path = repo_root / "modules" / "module_05_backend_frameworks.md"
    ok, msg = check_file_exists(repo_root, "modules/module_05_backend_frameworks.md")
    if ok:
        passes.append(f"MODULE 5: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"MODULE 5: {msg}")
        print(f"  [FAIL] {msg}")

    if mod5_path.exists():
        ok, missing = check_sections(mod5_path, MODULE_05_REQUIRED_SECTIONS)
        if ok:
            passes.append("MODULE 5 SECTIONS: All 16 sections present.")
            print("  [PASS] All 16 required sections present.")
        else:
            failures.append(f"MODULE 5 SECTIONS: Missing sections: {missing}")
            print(f"  [FAIL] Missing {len(missing)} section(s):")
            for m in missing:
                print(f"         - {m}")

        with open(mod5_path, "r", encoding="utf-8") as f:
            mod5_text = f.read()
        major5 = re.findall(r"^##\s+Section\s+(\d+)\s*[-—–]\s*(.+)$", mod5_text, re.MULTILINE)
        if major5:
            last5_num, last5_title = major5[-1]
            if last5_num == "16":
                passes.append("MODULE 5 FINAL SECTION: Section 16 is verified as the final major section.")
                print(f"  [PASS] Section 16 is verified as the final major section: '{last5_title.strip()}'")
            else:
                failures.append(f"MODULE 5 FINAL SECTION: Expected Section 16 to be final, found Section {last5_num}.")
                print(f"  [FAIL] Expected Section 16 to be final, found Section {last5_num}.")
        else:
            failures.append("MODULE 5 FINAL SECTION: No major sections found in Module 5.")
            print("  [FAIL] No major sections found in Module 5.")

    # 7. Code Fence Balance
    print("\n[7/10] Validating Code Fence Balance across Modules...")
    for mod_path in [mod1_path, mod2_path, mod3_path, mod4_path, mod5_path]:
        if mod_path.exists():
            ok, msg = check_code_fences(mod_path)
            if ok:
                passes.append(f"CODE FENCES ({mod_path.name}): {msg}")
                print(f"  [PASS] {msg}")
            else:
                failures.append(f"CODE FENCES ({mod_path.name}): {msg}")
                print(f"  [FAIL] {msg}")

    # 8. Placeholders in Modules
    print("\n[8/10] Checking for Placeholders (TODO, TBD, [placeholder], etc.)...")
    for mod_path in [mod1_path, mod2_path, mod3_path, mod4_path, mod5_path]:
        if mod_path.exists():
            ok, issues = check_placeholders(mod_path)
            if ok:
                passes.append(f"PLACEHOLDERS ({mod_path.name}): Zero placeholder markers found.")
                print(f"  [PASS] Zero placeholder markers found in {mod_path.name}.")
            else:
                failures.append(f"PLACEHOLDERS ({mod_path.name}): Found {len(issues)} placeholder occurrences.")
                print(f"  [FAIL] Found {len(issues)} placeholder occurrences in {mod_path.name}:")
                for issue in issues[:5]:
                    print(f"         - {issue}")

    # 9. QA Reports
    print("\n[9/10] Validating QA Reports...")
    for qa_name in ["module_01_qa.md", "module_02_qa.md", "module_03_qa.md", "module_04_qa.md", "module_05_qa.md"]:
        ok, msg = check_file_exists(repo_root, f"reviews/{qa_name}")
        if ok:
            passes.append(f"QA REPORT ({qa_name}): {msg}")
            print(f"  [PASS] {msg}")
        else:
            failures.append(f"QA REPORT ({qa_name}): {msg}")
            print(f"  [FAIL] {msg}")

    # 10. Quality Ledger Integrity
    print("\n[10/10] Validating Quality Ledger Integrity...")
    ledger_path = repo_root / "QUALITY_LEDGER.md"
    ok, msg = check_ledger_status(ledger_path)
    if ok:
        passes.append(f"LEDGER: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"LEDGER: {msg}")
        print(f"  [FAIL] {msg}")

    # Summary Report
    print("\n=================================================================")
    print("                    VALIDATION SUMMARY                          ")
    print("=================================================================")
    print(f"Total Passed Checks: {len(passes)}")
    print(f"Total Failed Checks: {len(failures)}")
    
    if failures:
        print("\nFailures:")
        for f in failures:
            print(f"  [!] {f}")
        print("\nResult: VALIDATION FAILED")
        return 1
    else:
        print("\nResult: ALL VALIDATION CHECKS PASSED PERFECTLY")
        return 0

if __name__ == "__main__":
    sys.exit(main())
