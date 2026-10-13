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
    
    # Check for balanced triple backticks (and quadruple backticks)
    lines = content.splitlines()
    in_code_block = False
    fence_char = None
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
        # Skip checking inside code blocks for comments that might explain TODOs in problem descriptions
        for pat in PLACEHOLDER_PATTERNS:
            match = pat.search(line)
            if match:
                # Filter out intentional explanatory occurrences like discussing 'TODO' comments in code
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
        # Check if the section name appears as a heading
        escaped = re.escape(sec)
        pattern = re.compile(rf"^#+\s+.*{escaped}", re.MULTILINE | re.IGNORECASE)
        # Also check without the exact dash type (em-dash vs en-dash vs hyphen)
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
        
    # Ensure Module 1 is NOT improperly marked USER APPROVED without user consent
    # Check for table line where Module 1 has USER APPROVED
    lines = content.splitlines()
    for line in lines:
        if "module_01_dsa.md" in line or "**01**" in line:
            if "USER APPROVED" in line:
                return False, "VIOLATION: Module 1 is marked 'USER APPROVED' in the ledger! Only human users may grant approval."
                
    return True, "Quality ledger correctly tracks Module 1 without unauthorized approval"

def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    print("=================================================================")
    print("       FAANG ENGINEERING HANDBOOK - VALIDATION SUITE            ")
    print("=================================================================")
    print(f"Target Repository: {repo_root}\n")
    
    failures = []
    passes = []
    
    # 1. Foundation Files
    print("[1/6] Validating Foundation Files...")
    for rel_path in REQUIRED_FOUNDATION_FILES:
        ok, msg = check_file_exists(repo_root, rel_path)
        if ok:
            passes.append(f"FOUNDATION: {msg}")
            print(f"  [PASS] {msg}")
        else:
            failures.append(f"FOUNDATION: {msg}")
            print(f"  [FAIL] {msg}")
            
    # 2. Module 1 File Existence and Integrity
    print("\n[2/6] Validating Module 1 Source...")
    mod1_path = repo_root / "modules" / "module_01_dsa.md"
    ok, msg = check_file_exists(repo_root, "modules/module_01_dsa.md")
    if ok:
        passes.append(f"MODULE 1: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"MODULE 1: {msg}")
        print(f"  [FAIL] {msg}")
        
    # 3. Section Completeness
    print("\n[3/6] Validating Module 1 Section Completeness (20 Required Sections)...")
    if mod1_path.exists():
        ok, missing = check_sections(mod1_path, MODULE_01_REQUIRED_SECTIONS)
        if ok:
            passes.append("MODULE 1 SECTIONS: All 20 required sections present and verified.")
            print("  [PASS] All 20 required sections present.")
        else:
            failures.append(f"MODULE 1 SECTIONS: Missing sections: {missing}")
            print(f"  [FAIL] Missing {len(missing)} section(s):")
            for m in missing:
                print(f"         - {m}")
    else:
        failures.append("MODULE 1 SECTIONS: Skipped because file does not exist")
        print("  [FAIL] Cannot inspect sections: file does not exist")
        
    # 4. Code Fence Balance
    print("\n[4/6] Validating Code Fence Balance...")
    if mod1_path.exists():
        ok, msg = check_code_fences(mod1_path)
        if ok:
            passes.append(f"CODE FENCES: {msg}")
            print(f"  [PASS] {msg}")
        else:
            failures.append(f"CODE FENCES: {msg}")
            print(f"  [FAIL] {msg}")
            
    # 5. Placeholders and Prohibited Shorthand
    print("\n[5/6] Checking for Placeholders (TODO, TBD, [placeholder], etc.)...")
    if mod1_path.exists():
        ok, issues = check_placeholders(mod1_path)
        if ok:
            passes.append("PLACEHOLDERS: Zero placeholder markers found.")
            print("  [PASS] Zero placeholder markers found in source.")
        else:
            failures.append(f"PLACEHOLDERS: Found {len(issues)} placeholder occurrences.")
            print(f"  [FAIL] Found {len(issues)} placeholder occurrences:")
            for issue in issues[:5]:
                print(f"         - {issue}")
            if len(issues) > 5:
                print(f"         ... and {len(issues) - 5} more.")

    # 6. Quality Ledger and QA Report Integrity
    print("\n[6/6] Validating Quality Ledger and QA Report...")
    qa_path = repo_root / "reviews" / "module_01_qa.md"
    ok, msg = check_file_exists(repo_root, "reviews/module_01_qa.md")
    if ok:
        passes.append(f"QA REPORT: {msg}")
        print(f"  [PASS] {msg}")
    else:
        failures.append(f"QA REPORT: {msg}")
        print(f"  [FAIL] {msg}")
        
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
