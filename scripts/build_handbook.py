#!/usr/bin/env python3
"""
BeyondTheAPI Software Engineering Handbook - Publication Build Engine
Compiles the 13 modular Markdown sources into one consolidated, publication-grade PDF.

Features:
- Full CSS Paged Media typography matching PDF_SPECIFICATION.md
- Executive cover page, front matter, and dynamic two-pass Master Table of Contents
- Pygments syntax-highlighted code blocks with line-wrapping to prevent clipping
- Table styling with repeating thead across page splits
- Cross-module link rewriting to internal PDF anchors
- PDF bookmarks / outline generation with PyMuPDF
- Running headers (current module name) and running footers (Page X of Y)
"""

import sys
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Auto-delegate to .venv if running in an environment lacking dependencies
try:
    import markdown
    import pymupdf
    import pypdf
    from pygments.formatters import HtmlFormatter
except ImportError:
    venv_py = REPO_ROOT / ".venv" / "bin" / "python3"
    if venv_py.exists() and sys.executable != str(venv_py):
        os.execv(str(venv_py), [str(venv_py)] + sys.argv)
    else:
        print("[ERROR] Missing required dependencies (markdown, pymupdf, pypdf, pygments).", file=sys.stderr)
        print("Please activate .venv or run: .venv/bin/pip install -r requirements.txt", file=sys.stderr)
        sys.exit(1)

import argparse
import subprocess
import shutil
import hashlib
import json
import re
import time

MODULES_DIR = REPO_ROOT / "modules"
DIST_DIR = REPO_ROOT / "dist"
OUTPUT_PDF = DIST_DIR / "BeyondTheAPI_Software_Engineering_Handbook.pdf"

TOTAL_MODULES_COUNT = 13

CANONICAL_MODULE_TITLES = {
    1: "Data Structures and Algorithms",
    2: "Operating Systems and Computer Networks",
    3: "Java, OOP, and Language Fundamentals",
    4: "Database Engineering and SQL",
    5: "Backend Frameworks and Application Architecture",
    6: "API Engineering and Security",
    7: "Caching and Data Access Performance",
    8: "Asynchronous Processing and Distributed Messaging",
    9: "Concurrency and Distributed Systems",
    10: "System Design and Production Architecture",
    11: "Observability and Production Diagnostics",
    12: "Debugging and Testing",
    13: "Linux, Containers, Deployment & Infrastructure",
}

MODULE_DESCRIPTIONS = {
    1: "Asymptotic complexity, amortized analysis, core data structures, traversal invariants, dynamic programming, and Java 21 implementation patterns.",
    2: "Process vs. thread memory layouts, CPU scheduling, virtual memory, page fault mechanics, TCP handshake/teardown, sockets, and network protocols.",
    3: "Java execution lifecycle, pass-by-value proof, initialization ordering, equals/hashCode contracts, PECS generics, collections, and JVM memory areas.",
    4: "Relational modeling, B+ Tree indexing, execution plans, ACID isolation anomalies, WAL, MVCC (Postgres vs MySQL), and zero-downtime migrations.",
    5: "Spring Boot auto-configuration, IoC container, bean lifecycles, transaction proxies, Node.js event loop, and layered architecture.",
    6: "REST semantics, JWT validation, OAuth 2.0 PKCE flow, rate limiting algorithms (Token/Leaky Bucket, Redis ZSET), and OWASP security defenses.",
    7: "Cache-Aside pattern, write invalidation, single-flight request coalescing, cache penetration/stampede defenses, and Redis data structure internals.",
    8: "Delivery vs. processing guarantees, Transactional Outbox/Inbox, poison message quarantine, jittered exponential backoff, Kafka commit log, and consumer groups.",
    9: "Java Memory Model, happens-before edges, volatile, atomic CAS primitives, monotonic fencing tokens, Raft consensus, and CAP/PACELC trade-offs.",
    10: "100M DAU capacity estimation, Little's Law, consistent hashing with virtual nodes, circuit breakers, compound availability, and sharded storage.",
    11: "Metrics, structured logs, OpenTelemetry spans, W3C traceparent propagation, percentile aggregation fallacy, Google SRE multi-window burn rate alerts, and cardinality.",
    12: "Test doubles taxonomy (Dummy, Stub, Fake, Mock, Spy), deterministic concurrency harnesses with CyclicBarrier, injectable Clock, mutation testing, and soak testing.",
    13: "Linux kernel primitives, process signals (SIGTERM/SIGKILL), cgroups v2 resource bounds, production Dockerfiles, Kubernetes probes, and rollout strategies.",
}

CHEATSHEET_SECTIONS = {
    1: (21, "DSA Cheatsheet: Interview Revision Reference"),
    2: (13, "OS and Computer Networks Cheatsheet"),
    3: (17, "Java and OOP Cheatsheet: Interview Revision Reference"),
    4: (16, "Module 4 Cheatsheet: Database Engineering & SQL"),
    5: (16, "Module 5 Cheatsheet: Backend Frameworks & Application Engineering"),
    6: (16, "Module 6 Cheatsheet: API Engineering & Security"),
    7: (18, "Module 7 Cheatsheet: Caching & Performance"),
    8: (22, "Module 8 Cheatsheet: Asynchronous Processing & Distributed Messaging"),
    9: (27, "Module 9 Cheatsheet: Concurrency & Distributed Systems"),
    10: (31, "Module 10 Cheatsheet: System Design & Production Architecture"),
    11: (28, "Module 11 Cheatsheet: Observability & Production Diagnostics"),
    12: (36, "Module 12 Cheatsheet: Debugging & Testing"),
    13: (42, "Module 13 Cheatsheet: Linux, Containers & Infrastructure"),
}

def locate_chrome() -> str:
    candidates = [
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        shutil.which("chromium"),
        shutil.which("google-chrome"),
        shutil.which("chrome"),
    ]
    for c in candidates:
        if c and os.path.exists(c):
            return c
    raise FileNotFoundError("Could not find Google Chrome or Chromium executable on the system.")

def compute_module_hashes() -> dict[str, str]:
    hashes = {}
    for mod_path in sorted(MODULES_DIR.glob("module_*.md")):
        h = hashlib.sha256(mod_path.read_bytes()).hexdigest()
        hashes[mod_path.name] = h
    return hashes

def build_cover_html() -> str:
    return """
<div class="cover-page">
  <div class="cover-top-accent"></div>
  <div class="cover-inner">
    <div class="cover-badge">OFFICIAL TECHNICAL REFERENCE • FIRST EDITION</div>
    <h1 class="cover-title">BeyondTheAPI</h1>
    <h2 class="cover-subtitle">Software Engineering Handbook</h2>
    <div class="cover-divider"></div>
    <p class="cover-descriptor">
      A Comprehensive Technical Reference for Backend Engineering, Computer Science, Distributed Systems, Observability, Testing, and Infrastructure
    </p>

    <div class="cover-metadata-grid">
      <div class="cover-meta-col">
        <strong>Curriculum Scope:</strong> Modules 1–13 (Complete Foundational Series)<br>
        <strong>Authoritative Runtimes:</strong> OpenJDK 21 LTS • Node.js v24 • PostgreSQL 16<br>
        <strong>Systems Targets:</strong> Linux / POSIX • Docker • Kubernetes
      </div>
      <div class="cover-meta-col">
        <strong>Publication Year:</strong> 2026<br>
        <strong>Format:</strong> Typeset Consolidate Monograph<br>
        <strong>Status:</strong> Source Frozen & Technically Audited
      </div>
    </div>

    <div class="cover-footer-note">
      Engineered from First Principles for Technical Depth, Correctness, and High-Bar Interview Preparation
    </div>
  </div>
</div>
"""

def build_front_matter_html() -> str:
    return """
<div class="front-matter">
  <h1 class="fm-title">Handbook Overview & Architecture</h1>

  <div class="fm-section">
    <h2>1. Purpose and Educational Philosophy</h2>
    <p>
      The <em>BeyondTheAPI Software Engineering Handbook</em> bridges the gap between academic computer science coursework
      and the rigorous production and architectural standards demanded by tier-1 engineering organizations.
      Every topic in this curriculum is taught from <strong>first principles</strong>:
    </p>
    <ul>
      <li><strong>Mechanistic Correctness:</strong> Explaining internal memory layouts, pointer structures, CPU cache locality, assembly-level instruction sequences, and runtime contracts rather than superficial syntax.</li>
      <li><strong>Invariants and Failure Modes:</strong> Establishing strict preconditions, postconditions, loop invariants, transaction boundaries, and distributed failure domains.</li>
      <li><strong>Engineering Trade-offs:</strong> Evaluating practical trade-offs (e.g., asymptotic bounds vs. CPU cache constants, latency vs. throughput, consistency vs. availability).</li>
    </ul>
  </div>

  <div class="fm-section">
    <h2>2. The 13-Module Curriculum Progression</h2>
    <p>
      The curriculum is structured into 13 sequential modules where each module builds directly upon the mental models established in preceding modules:
    </p>
    <table class="fm-table">
      <thead>
        <tr>
          <th style="width: 12%;">Module</th>
          <th style="width: 38%;">Domain & Subject Area</th>
          <th style="width: 50%;">Core Architectural Competencies</th>
        </tr>
      </thead>
      <tbody>
        <tr><td><strong>Mod 01</strong></td><td>Data Structures & Algorithms</td><td>Complexity derivations, arrays, hashing, two pointers, sliding window, binary search, trees, graphs, backtracking, dynamic programming.</td></tr>
        <tr><td><strong>Mod 02</strong></td><td>Operating Systems & Networks</td><td>Privilege rings, process/thread models, CPU scheduling, virtual memory, page faults, TCP handshake/teardown, TIME_WAIT, TLS 1.3.</td></tr>
        <tr><td><strong>Mod 03</strong></td><td>Java & OOP Fundamentals</td><td>Strict pass-by-value proof, initialization order, dynamic dispatch vs hiding, equals/hashCode, PECS generics, collections, JVM memory.</td></tr>
        <tr><td><strong>Mod 04</strong></td><td>Database Engineering & SQL</td><td>B+ Tree indexing, clustered vs heap storage, query plans, ACID isolation anomalies, WAL, Postgres vs MySQL MVCC, zero-downtime migrations.</td></tr>
        <tr><td><strong>Mod 05</strong></td><td>Backend Frameworks</td><td>Spring Boot auto-configuration, IoC container, bean lifecycles, transaction proxies, Node.js event loop, and layered architecture.</td></tr>
        <tr><td><strong>Mod 06</strong></td><td>API Engineering & Security</td><td>REST semantics, JWT signatures, OAuth 2.0 PKCE, rate limiting algorithms (Token/Leaky Bucket, Redis ZSET), OWASP defenses.</td></tr>
        <tr><td><strong>Mod 07</strong></td><td>Caching & Performance</td><td>Cache-Aside invalidation, single-flight coalescing, stampede/penetration/avalanche defenses, Redis internal data structures.</td></tr>
        <tr><td><strong>Mod 08</strong></td><td>Async & Distributed Messaging</td><td>Delivery vs processing guarantees, Transactional Outbox, poison DLQs, jittered exponential backoff, Kafka commit logs.</td></tr>
        <tr><td><strong>Mod 09</strong></td><td>Concurrency & Distributed Systems</td><td>Java Memory Model, happens-before, volatile, atomic CAS, monotonic fencing tokens defeating zombie writes, Raft quorums.</td></tr>
        <tr><td><strong>Mod 10</strong></td><td>System Design & Architecture</td><td>100M DAU capacity planning, Little's Law, consistent hashing with virtual nodes, circuit breakers, compound availability.</td></tr>
        <tr><td><strong>Mod 11</strong></td><td>Observability & Diagnostics</td><td>Metrics, structured logs, OpenTelemetry traces, percentile aggregation fallacy, Google SRE multi-window burn rate alerts.</td></tr>
        <tr><td><strong>Mod 12</strong></td><td>Debugging & Testing</td><td>Test doubles taxonomy, deterministic concurrency harnesses with CyclicBarrier, time-freezing Clock, mutation testing, soak testing.</td></tr>
        <tr><td><strong>Mod 13</strong></td><td>Linux, Containers & Infrastructure</td><td>Linux system calls, signals (SIGTERM/SIGKILL), cgroups v2 resource bounds, production Dockerfiles, Kubernetes probes, rollouts.</td></tr>
      </tbody>
    </table>
  </div>

  <div class="fm-section">
    <h2>3. How to Use the Revision Cheatsheets</h2>
    <p>
      Every module concludes with a high-density, authoritative <strong>Revision Cheatsheet</strong> (e.g., Section 21 in Module 1, Section 13 in Module 2, Section 42 in Module 13).
      These cheatsheets distill the core mental models, mathematical formulas, critical interview traps, and operational checklists into rapid-recall format.
      Use the Master Table of Contents to navigate directly to these cheatsheets during pre-interview revision.
    </p>
  </div>
</div>
"""

def build_master_toc_html(page_map: dict[str, int]) -> str:
    rows = []
    for mod_num in range(1, 14):
        title = CANONICAL_MODULE_TITLES[mod_num]
        desc = MODULE_DESCRIPTIONS[mod_num]
        cs_sec, cs_title = CHEATSHEET_SECTIONS[mod_num]

        mod_page = page_map.get(f"module-{mod_num:02d}", "...")
        cs_page = page_map.get(f"module-{mod_num:02d}-cheatsheet", "...")

        rows.append(f"""
        <div class="toc-entry">
          <div class="toc-entry-header">
            <a href="#module-{mod_num:02d}" class="toc-mod-title">
              <span class="toc-mod-badge">MODULE {mod_num:02d}</span> {title}
            </a>
            <span class="toc-dots"></span>
            <span class="toc-page-num">{mod_page}</span>
          </div>
          <div class="toc-entry-desc">{desc}</div>
          <div class="toc-entry-cs">
            <a href="#module-{mod_num:02d}-cheatsheet" class="toc-cs-link">
              <span class="toc-star">★</span> Section {cs_sec} — {cs_title}
            </a>
            <span class="toc-dots"></span>
            <span class="toc-cs-page">{cs_page}</span>
          </div>
        </div>
        """)

    return f"""
<div class="master-toc">
  <h1 class="toc-title">Master Table of Contents</h1>
  <p class="toc-subtitle">Consolidated 13-Module Curriculum Overview with Direct Section Navigation</p>
  <div class="toc-grid">
    {''.join(rows)}
  </div>
</div>
"""

def preprocess_module_markdown(mod_num: int, md_text: str) -> str:
    # 1. Normalize cross-module file links into internal anchor links (#module-XX)
    def link_replacer(match):
        full_match = match.group(0)
        target_mod = match.group(1)
        mod_int = int(target_mod)
        return f"#module-{mod_int:02d}"

    cleaned = re.sub(
        r'file:///[^\s\)\"\>]+/modules/module_(\d+)[^\s\)\"\>]*',
        link_replacer,
        md_text
    )

    # 2. Add an anchor right before the final cheatsheet section
    cs_sec, cs_name = CHEATSHEET_SECTIONS[mod_num]
    cs_pattern = rf"(##\s+Section\s+{cs_sec}\s+[-—–]\s+[^\n]+)"
    cs_anchor = f'\n<div id="module-{mod_num:02d}-cheatsheet" class="cheatsheet-anchor"></div>\n\\1'
    cleaned = re.sub(cs_pattern, cs_anchor, cleaned, count=1)

    return cleaned

def build_consolidated_html(page_map: dict[str, int]) -> str:
    pygments_css = HtmlFormatter(style="default").get_style_defs(".codehilite")

    modules_html = []
    for mod_num in range(1, 14):
        pattern = f"module_{mod_num:02d}_*.md" if mod_num >= 10 else f"module_{mod_num:02d}_*.md"
        mod_files = list(MODULES_DIR.glob(pattern))
        if not mod_files:
            # fallback to 1-digit prefix if any
            mod_files = list(MODULES_DIR.glob(f"module_{mod_num}_*.md"))
        if not mod_files:
            raise FileNotFoundError(f"Could not locate source file for Module {mod_num}")

        src_path = mod_files[0]
        raw_text = src_path.read_text(encoding="utf-8")
        processed_text = preprocess_module_markdown(mod_num, raw_text)

        body = markdown.markdown(
            processed_text,
            extensions=["extra", "codehilite", "toc"]
        )

        module_block = f"""
        <section class="handbook-module" id="module-{mod_num:02d}">
          <div class="module-header-anchor"></div>
          {body}
        </section>
        """
        modules_html.append(module_block)

    cover_html = build_cover_html()
    front_matter_html = build_front_matter_html()
    toc_html = build_master_toc_html(page_map)

    stylesheet = f"""
    @page {{
      size: A4 portrait;
      margin: 20mm 16mm 20mm 16mm;
    }}

    * {{
      box-sizing: border-box;
    }}

    body {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Charter", "Source Serif Pro", serif;
      font-size: 9.5pt;
      line-height: 1.48;
      color: #111827;
      background: #ffffff;
      margin: 0;
      padding: 0;
    }}

    /* Cover Page Styling */
    .cover-page {{
      page-break-before: avoid;
      page-break-after: always;
      height: 100vh;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 30mm 15mm 20mm 15mm;
      border: 2px solid #0f172a;
      background: #ffffff;
    }}
    .cover-top-accent {{
      width: 60px;
      height: 8px;
      background: #2563eb;
      margin-bottom: 25px;
    }}
    .cover-badge {{
      display: inline-block;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 8.5pt;
      font-weight: 700;
      letter-spacing: 1.5px;
      color: #2563eb;
      margin-bottom: 12px;
    }}
    .cover-title {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 38pt;
      font-weight: 900;
      color: #0f172a;
      line-height: 1.05;
      margin: 0 0 8px 0;
      letter-spacing: -1px;
      border: none !important;
      padding: 0 !important;
    }}
    .cover-subtitle {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 19pt;
      font-weight: 600;
      color: #334155;
      margin: 0 0 24px 0;
      letter-spacing: -0.3px;
    }}
    .cover-divider {{
      width: 100%;
      height: 3px;
      background: #2563eb;
      margin-bottom: 24px;
    }}
    .cover-descriptor {{
      font-size: 11.5pt;
      line-height: 1.5;
      color: #475569;
      max-width: 90%;
      margin-bottom: 40px;
    }}
    .cover-metadata-grid {{
      display: flex;
      justify-content: space-between;
      border-top: 1px solid #cbd5e1;
      border-bottom: 1px solid #cbd5e1;
      padding: 16px 0;
      margin-bottom: 30px;
      font-size: 9pt;
      color: #334155;
      line-height: 1.6;
    }}
    .cover-meta-col {{
      width: 48%;
    }}
    .cover-footer-note {{
      font-size: 8.5pt;
      color: #64748b;
      font-style: italic;
      border-top: 1px dashed #cbd5e1;
      padding-top: 12px;
    }}

    /* Front Matter Styling */
    .front-matter {{
      page-break-before: always;
      page-break-after: always;
      padding: 10mm 0;
    }}
    .fm-title {{
      font-size: 22pt;
      font-weight: 800;
      color: #0f172a;
      border-bottom: 2px solid #2563eb;
      padding-bottom: 10px;
      margin-top: 0;
      margin-bottom: 20px;
    }}
    .fm-section h2 {{
      font-size: 13.5pt;
      font-weight: 700;
      color: #1e3a8a;
      border-bottom: 1px solid #e2e8f0;
      padding-bottom: 4px;
      margin-top: 24px;
      margin-bottom: 10px;
    }}
    .fm-table {{
      width: 100%;
      border-collapse: collapse;
      margin: 12px 0 20px 0;
      font-size: 8pt;
    }}
    .fm-table th, .fm-table td {{
      border: 1px solid #cbd5e1;
      padding: 6px 9px;
      vertical-align: top;
    }}
    .fm-table th {{
      background: #f1f5f9;
      font-weight: 700;
      color: #0f172a;
    }}

    /* Master TOC Styling */
    .master-toc {{
      page-break-before: always;
      page-break-after: always;
      padding: 10mm 0;
    }}
    .toc-title {{
      font-size: 22pt;
      font-weight: 800;
      color: #0f172a;
      border-bottom: 2px solid #2563eb;
      padding-bottom: 8px;
      margin-top: 0;
      margin-bottom: 6px;
    }}
    .toc-subtitle {{
      font-size: 10pt;
      color: #64748b;
      margin-top: 0;
      margin-bottom: 22px;
    }}
    .toc-entry {{
      margin-bottom: 14px;
      break-inside: avoid;
    }}
    .toc-entry-header {{
      display: flex;
      align-items: baseline;
      font-size: 10pt;
      font-weight: 700;
    }}
    .toc-mod-title {{
      color: #0f172a;
      text-decoration: none;
      white-space: nowrap;
    }}
    .toc-mod-badge {{
      display: inline-block;
      background: #1e3a8a;
      color: #ffffff;
      padding: 1px 5px;
      border-radius: 3px;
      font-size: 7.5pt;
      font-weight: 800;
      letter-spacing: 0.5px;
      margin-right: 6px;
    }}
    .toc-dots {{
      flex-grow: 1;
      border-bottom: 1px dotted #94a3b8;
      margin: 0 8px;
      position: relative;
      top: -3px;
    }}
    .toc-page-num {{
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 9.5pt;
      font-weight: 700;
      color: #1e3a8a;
      min-width: 30px;
      text-align: right;
    }}
    .toc-entry-desc {{
      font-size: 8pt;
      color: #475569;
      line-height: 1.35;
      margin-top: 2px;
      margin-left: 20px;
    }}
    .toc-entry-cs {{
      display: flex;
      align-items: baseline;
      font-size: 8pt;
      font-weight: 600;
      margin-top: 3px;
      margin-left: 20px;
    }}
    .toc-cs-link {{
      color: #047857;
      text-decoration: none;
      white-space: nowrap;
    }}
    .toc-star {{
      color: #d97706;
      font-size: 9pt;
      margin-right: 3px;
    }}
    .toc-cs-page {{
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 8pt;
      color: #047857;
      font-weight: 600;
    }}

    /* Module Presentation Styling */
    .handbook-module {{
      page-break-before: always;
      padding-top: 10mm;
    }}

    h1, h2, h3, h4, h5, h6 {{
      color: #0f172a;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      page-break-after: avoid;
      break-after: avoid;
    }}

    h1 {{
      font-size: 20pt;
      font-weight: 800;
      color: #1e3a8a;
      border-bottom: 2.5px solid #2563eb;
      padding-bottom: 8px;
      margin-top: 0;
      margin-bottom: 16px;
      letter-spacing: -0.3px;
    }}

    h2 {{
      font-size: 13.5pt;
      font-weight: 700;
      color: #0f172a;
      border-bottom: 1px solid #e2e8f0;
      padding-bottom: 4px;
      margin-top: 26px;
      margin-bottom: 12px;
    }}

    h3 {{
      font-size: 11pt;
      font-weight: 600;
      color: #1e293b;
      margin-top: 18px;
      margin-bottom: 8px;
    }}

    h4 {{
      font-size: 9.5pt;
      font-weight: 600;
      color: #334155;
      margin-top: 14px;
      margin-bottom: 6px;
    }}

    p {{
      margin: 8px 0;
      text-align: justify;
    }}

    ul, ol {{
      margin: 8px 0 12px 0;
      padding-left: 24px;
    }}

    li {{
      margin-bottom: 4px;
    }}

    /* Code Blocks */
    pre {{
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-left: 3.5px solid #2563eb;
      padding: 9px 12px;
      border-radius: 4px;
      font-family: ui-monospace, "SF Mono", Menlo, Monaco, Consolas, monospace;
      font-size: 7.8pt;
      line-height: 1.36;
      white-space: pre-wrap;
      word-break: break-word;
      overflow-wrap: break-word;
      margin: 12px 0;
      page-break-inside: auto;
    }}

    code {{
      font-family: ui-monospace, "SF Mono", Menlo, Monaco, Consolas, monospace;
      background: #f1f5f9;
      color: #0f172a;
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 8pt;
    }}

    pre code {{
      background: transparent;
      padding: 0;
      border-radius: 0;
      color: inherit;
    }}

    /* Blockquotes & Callouts */
    blockquote {{
      border-left: 4px solid #2563eb;
      background: #f0fdf4;
      color: #166534;
      margin: 12px 0;
      padding: 8px 14px;
      border-radius: 0 4px 4px 0;
      page-break-inside: avoid;
    }}

    blockquote p {{
      margin: 4px 0;
    }}

    /* Tables */
    table {{
      width: 100%;
      border-collapse: collapse;
      margin: 14px 0;
      font-size: 8pt;
      page-break-inside: auto;
    }}

    thead {{
      display: table-header-group;
    }}

    tr {{
      page-break-inside: avoid;
    }}

    th, td {{
      border: 1px solid #cbd5e1;
      padding: 5px 8px;
      text-align: left;
      vertical-align: top;
      word-break: break-word;
    }}

    th {{
      background-color: #f1f5f9;
      font-weight: 700;
      color: #0f172a;
    }}

    tr:nth-child(even) {{
      background-color: #f8fafc;
    }}

    hr {{
      border: 0;
      height: 1px;
      background: #e2e8f0;
      margin: 22px 0;
    }}

    /* Cheatsheet Section Styling */
    .cheatsheet-anchor + h2 {{
      background: #ecfdf5;
      border: 1.5px solid #10b981;
      border-left: 6px solid #059669;
      padding: 10px 14px;
      border-radius: 4px;
      color: #065f46;
      margin-top: 32px;
      font-size: 14pt;
    }}

    a {{
      color: #2563eb;
      text-decoration: none;
    }}

    a:hover {{
      text-decoration: underline;
    }}

    {pygments_css}
    """

    full_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>BeyondTheAPI — Software Engineering Handbook</title>
<style>
{stylesheet}
</style>
</head>
<body>
{cover_html}
{front_matter_html}
{toc_html}
{''.join(modules_html)}
</body>
</html>
"""
    return full_html

def compile_pdf_pass(html_content: str, output_path: Path, chrome_path: str):
    temp_html = output_path.with_suffix(".html")
    temp_html.write_text(html_content, encoding="utf-8")

    cmd = [
        chrome_path,
        "--headless",
        "--disable-gpu",
        "--run-all-compositor-stages-before-draw",
        f"--print-to-pdf={output_path}",
        "--no-pdf-header-footer",
        str(temp_html)
    ]

    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Chrome PDF compilation failed with exit code {res.returncode}:\n{res.stderr}")

def extract_named_destinations(pdf_path: Path) -> dict[str, int]:
    reader = pypdf.PdfReader(str(pdf_path))
    dest_map = {}
    for name, dest in reader.named_destinations.items():
        clean_name = name.lstrip("/")
        try:
            page_num = reader.get_destination_page_number(dest) + 1
            dest_map[clean_name] = page_num
        except Exception:
            pass
    return dest_map

def apply_running_headers_footers_and_bookmarks(pdf_path: Path, dest_map: dict[str, int]):
    doc = pymupdf.open(str(pdf_path))
    total_pages = doc.page_count

    # Calculate module page spans
    module_spans = []
    for mod_num in range(1, 14):
        p_start = dest_map.get(f"module-{mod_num:02d}")
        if p_start:
            module_spans.append((mod_num, p_start, CANONICAL_MODULE_TITLES[mod_num]))

    module_spans.sort(key=lambda x: x[1])

    def get_module_title_for_page(page_no: int) -> str:
        current_title = "Handbook Overview & Contents"
        for mod_num, p_start, title in module_spans:
            if page_no >= p_start:
                current_title = f"Module {mod_num:02d} — {title}"
            else:
                break
        return current_title

    # Running Headers & Footers (skip page 1 = Cover)
    for pno in range(1, total_pages):
        page = doc[pno]
        rect = page.rect
        page_no = pno + 1

        # Hairline header rule
        page.draw_line(
            pymupdf.Point(45, 34),
            pymupdf.Point(rect.width - 45, 34),
            color=(0.85, 0.88, 0.91),
            width=0.5
        )
        # Running header text
        page.insert_text(
            pymupdf.Point(45, 28),
            "BeyondTheAPI",
            fontsize=7.5,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )
        mod_hdr = get_module_title_for_page(page_no)
        page.insert_text(
            pymupdf.Point(rect.width - 250, 28),
            mod_hdr,
            fontsize=7.5,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )

        # Hairline footer rule
        page.draw_line(
            pymupdf.Point(45, rect.height - 34),
            pymupdf.Point(rect.width - 45, rect.height - 34),
            color=(0.85, 0.88, 0.91),
            width=0.5
        )
        # Running footer text
        page.insert_text(
            pymupdf.Point(45, rect.height - 23),
            "Software Engineering Handbook • First Edition (2026)",
            fontsize=7.5,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )
        pg_str = f"Page {page_no} of {total_pages}"
        page.insert_text(
            pymupdf.Point(rect.width - 100, rect.height - 23),
            pg_str,
            fontsize=7.5,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )

    # Build complete hierarchical TOC outline
    toc = []
    toc.append([1, "BeyondTheAPI — Software Engineering Handbook", 1])
    toc.append([1, "Handbook Overview & Architecture", 2])
    toc.append([1, "Master Table of Contents", 3])

    for mod_num in range(1, 14):
        title = CANONICAL_MODULE_TITLES[mod_num]
        p_start = dest_map.get(f"module-{mod_num:02d}", 1)
        cs_sec, cs_title = CHEATSHEET_SECTIONS[mod_num]
        cs_page = dest_map.get(f"module-{mod_num:02d}-cheatsheet", p_start)

        toc.append([1, f"Module {mod_num:02d}: {title}", p_start])
        toc.append([2, f"★ Section {cs_sec}: {cs_title}", cs_page])

    doc.set_toc(toc)

    # Document Metadata
    doc.set_metadata({
        "title": "BeyondTheAPI — Software Engineering Handbook",
        "author": "BeyondTheAPI Engineering Team",
        "subject": "Comprehensive Computer Science, Backend Architecture & Systems Engineering Handbook",
        "keywords": "Computer Science, Backend Engineering, Distributed Systems, Java 21, Concurrency, Databases, Observability, Testing, Infrastructure, Kubernetes",
        "creator": "BeyondTheAPI Technical Publishing Engine",
        "producer": "Headless Chromium + PyMuPDF",
    })

    temp_final = pdf_path.with_suffix(".final.pdf")
    doc.save(str(temp_final))
    doc.close()

    temp_final.replace(pdf_path)

def build_consolidated_handbook() -> Path:
    DIST_DIR.mkdir(exist_ok=True)
    chrome_path = locate_chrome()
    print(f"[1/5] Using rendering engine: {chrome_path}")

    print("[2/5] Running Pass 1 (Measuring layout geometry and named destinations)...")
    pass1_pdf = DIST_DIR / "BeyondTheAPI_pass1.pdf"
    html_pass1 = build_consolidated_html({})
    t0 = time.time()
    compile_pdf_pass(html_pass1, pass1_pdf, chrome_path)
    print(f"      Pass 1 completed in {time.time() - t0:.2f}s")

    print("[3/5] Extracting exact rendered page numbers from Pass 1...")
    dest_map = extract_named_destinations(pass1_pdf)
    print(f"      Discovered {len(dest_map)} anchor destinations across modules.")
    for mod_num in range(1, 14):
        p_mod = dest_map.get(f"module-{mod_num:02d}", "?")
        p_cs = dest_map.get(f"module-{mod_num:02d}-cheatsheet", "?")
        print(f"      Module {mod_num:02d} Start: Page {p_mod:<4} | Cheatsheet: Page {p_cs}")

    print("[4/5] Running Pass 2 (Compiling consolidated PDF with populated Master TOC)...")
    html_pass2 = build_consolidated_html(dest_map)
    t1 = time.time()
    compile_pdf_pass(html_pass2, OUTPUT_PDF, chrome_path)
    print(f"      Pass 2 completed in {time.time() - t1:.2f}s")

    print("[5/5] Stamping running headers, footers, metadata, and PDF outline bookmarks...")
    # Re-extract destinations from pass 2 to guarantee exact page alignment
    dest_map_pass2 = extract_named_destinations(OUTPUT_PDF)
    apply_running_headers_footers_and_bookmarks(OUTPUT_PDF, dest_map_pass2)

    # Cleanup pass 1 temporary artifacts
    if pass1_pdf.exists():
        pass1_pdf.unlink()
    pass1_html = pass1_pdf.with_suffix(".html")
    if pass1_html.exists():
        pass1_html.unlink()
    pass2_html = OUTPUT_PDF.with_suffix(".html")
    if pass2_html.exists():
        pass2_html.unlink()

    final_size = OUTPUT_PDF.stat().st_size
    doc = pymupdf.open(str(OUTPUT_PDF))
    total_pages = doc.page_count
    doc.close()

    print("\n" + "=" * 65)
    print("CONSOLIDATED HANDBOOK PDF COMPILED SUCCESSFULLY!")
    print("=" * 65)
    print(f"  Artifact Path : {OUTPUT_PDF}")
    print(f"  File Size     : {final_size:,} bytes ({final_size / 1024 / 1024:.2f} MB)")
    print(f"  Total Pages   : {total_pages}")
    print(f"  Module Count  : 13 (Modules 1–13 inclusive)")
    print("=" * 65 + "\n")

    return OUTPUT_PDF

def main() -> int:
    parser = argparse.ArgumentParser(description="BeyondTheAPI Engineering Handbook Build Engine")
    parser.add_argument("--check-deps", action="store_true", help="Check installed rendering dependencies")
    parser.add_argument("--build-all", action="store_true", help="Compile the complete consolidated handbook PDF")

    args = parser.parse_args()

    if args.check_deps:
        print("Checking dependencies...")
        try:
            ch = locate_chrome()
            print(f"  [PASS] Chromium / Chrome: {ch}")
        except Exception as e:
            print(f"  [FAIL] Chrome: {e}")
        print("  [PASS] Python Markdown, Pygments, PyMuPDF, PyPDF available.")
        return 0

    if args.build_all:
        try:
            build_consolidated_handbook()
            return 0
        except Exception as e:
            print(f"[FATAL ERROR] PDF compilation failed: {e}", file=sys.stderr)
            import traceback
            traceback.print_exc()
            return 1

    parser.print_help()
    return 0

if __name__ == "__main__":
    sys.exit(main())
