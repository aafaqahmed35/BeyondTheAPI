#!/usr/bin/env python3
"""
BeyondTheAPI Master Cheatsheets - Publication Build Engine
Extracts and compiles the 13 authoritative module cheatsheets into a standalone,
professionally typeset reference manual.

Features:
- Deterministic extraction from audited Markdown sources (modules/module_*.md)
- Complete content preservation: exact wording, formulas, code, tables, and invariants
- Executive cover page, "How to Use This Document" guide, and dynamic two-pass Master TOC
- CSS Paged Media typography with Pygments code styling and responsive table wrapping
- Automatic page-break control preventing orphaned headers and awkward splits
- PyMuPDF post-processing: running headers/footers, metadata, and full hierarchical bookmarks
- Safe isolation: output written strictly to dist/BeyondTheAPI_Master_Cheatsheets.pdf
"""

import sys
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Auto-delegate to .venv if running in an interpreter lacking required packages
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
OUTPUT_PDF = DIST_DIR / "BeyondTheAPI_Master_Cheatsheets.pdf"
MANIFEST_FILE = REPO_ROOT / "reviews" / "CHEATSHEET_EXTRACTION_MANIFEST.md"

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

CHEATSHEET_METADATA = {
    1: {"sec": 21, "title": "DSA Cheatsheet: Interview Revision Reference", "subs": 10},
    2: {"sec": 13, "title": "OS and Computer Networks Cheatsheet", "subs": 10},
    3: {"sec": 17, "title": "Java and OOP Cheatsheet: Interview Revision Reference", "subs": 5},
    4: {"sec": 16, "title": "Module 4 Cheatsheet: Database Engineering & SQL", "subs": 6},
    5: {"sec": 16, "title": "Module 5 Cheatsheet: Backend Frameworks & Application Engineering", "subs": 6},
    6: {"sec": 16, "title": "Module 6 Cheatsheet: API Engineering & Security", "subs": 9},
    7: {"sec": 18, "title": "Module 7 Cheatsheet: Caching & Performance", "subs": 6},
    8: {"sec": 22, "title": "Module 8 Cheatsheet: Asynchronous Processing & Distributed Messaging", "subs": 6},
    9: {"sec": 27, "title": "Module 9 Cheatsheet: Concurrency & Distributed Systems", "subs": 2},
    10: {"sec": 31, "title": "Module 10 Cheatsheet: System Design & Production Architecture", "subs": 4},
    11: {"sec": 28, "title": "Module 11 Cheatsheet: Observability & Production Diagnostics", "subs": 0},
    12: {"sec": 36, "title": "Module 12 Cheatsheet: Debugging & Testing", "subs": 3},
    13: {"sec": 42, "title": "Module 13 Cheatsheet: Linux, Containers & Infrastructure", "subs": 3},
}

MODULE_DESCRIPTIONS = {
    1: "Asymptotic complexity master matrix, array/tree/heap traversal invariants, pattern recognition clues, and OpenJDK collection mechanics.",
    2: "Kernel vs user privilege, CPU scheduling algorithms, virtual memory translation, I/O multiplexing paradigms, socket lifecycle, and latency numbers.",
    3: "Strict pass-by-value proof, class initialization order, dynamic dispatch vs hiding, equals/hashCode contracts, PECS generics, and collections.",
    4: "Logical query processing order, relational key decision matrix, normalization rules, ACID concurrency anomalies, indexing checklist, and HikariCP sizing.",
    5: "Request lifecycle, Node.js event loop phase priorities, JPA entity state transitions, Spring @Transactional rules, and graceful shutdown standards.",
    6: "HTTP method semantics and idempotency, status code decision tree, REST URI nesting rules, JWT validation checklist, OAuth 2.0 vs OIDC, and rate limiting.",
    7: "Cache-Aside read/write flows, Little's Law, Redis eviction policies, cache stampede/avalanche/penetration defenses, and RFC 9111 HTTP cache directives.",
    8: "Kingman's queueing approximation, at-least-once vs effectively-once semantics, broker decision tree, Transactional Outbox/Inbox, and jittered retries.",
    9: "Amdahl's law, thread pool sizing formulas, volatile semantics, wait/notify while-loops, virtual threads, CAP theorem, and monotonic fencing tokens.",
    10: "System design 4-stage interview flow, back-of-the-envelope estimation formulas, database storage selection matrix, and 6 production architecture rules.",
    11: "Monitoring vs observability, telemetry triad mechanics, RED & USE heuristics, multi-window burn rate alerts, trace sampling, and incident triage cadence.",
    12: "Test doubles taxonomy (Dummy, Stub, Fake, Mock, Spy), diagnostic CLI tools (jcmd, asprof, lsof, tcpdump), and production testing rules of thumb.",
    13: "Linux diagnostic commands (ps, free, df, ss, tcpdump), Docker container operations, Kubernetes pod diagnostics, and infrastructure reliability rules.",
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

def extract_module_cheatsheet(mod_num: int) -> tuple[str, dict]:
    pattern = f"module_{mod_num:02d}_*.md"
    mod_files = list(MODULES_DIR.glob(pattern))
    if not mod_files:
        mod_files = list(MODULES_DIR.glob(f"module_{mod_num}_*.md"))
    if not mod_files:
        raise FileNotFoundError(f"Could not locate source Markdown file for Module {mod_num} in {MODULES_DIR}")

    src_path = mod_files[0]
    raw_text = src_path.read_text(encoding="utf-8")
    lines = raw_text.splitlines()

    cs_idx = None
    cs_heading = None
    for idx, line in enumerate(lines):
        if line.startswith("## ") and "cheatsheet" in line.lower():
            cs_idx = idx
            cs_heading = line.strip()
            break

    if cs_idx is None:
        raise ValueError(f"Could not identify cheatsheet heading in {src_path.name}")

    start_line = cs_idx + 1
    end_line = len(lines)
    cs_markdown = "\n".join(lines[cs_idx:])

    meta = {
        "mod_num": mod_num,
        "src_path": src_path,
        "filename": src_path.name,
        "start_line": start_line,
        "end_line": end_line,
        "line_count": end_line - start_line + 1,
        "cs_heading": cs_heading,
        "canonical_title": CANONICAL_MODULE_TITLES[mod_num],
    }

    return cs_markdown, meta

def build_cover_html() -> str:
    return """
<div class="cover-page">
  <div class="cover-top-accent"></div>
  <div class="cover-inner">
    <div class="cover-badge">OFFICIAL TECHNICAL REFERENCE COMPANION • FIRST EDITION</div>
    <h1 class="cover-title">BeyondTheAPI</h1>
    <h2 class="cover-subtitle">Master Cheatsheets</h2>
    <div class="cover-divider"></div>
    <p class="cover-descriptor">
      13-Module Software Engineering Reference • Core Mental Models, Asymptotic Complexity,
      Distributed Systems Invariants, Architectural Decision Trees, and Diagnostic Runbooks
    </p>

    <div class="cover-companion-notice">
      <div class="companion-box">
        <strong>Extracted Companion Edition:</strong> This volume is a standalone compilation of all thirteen
        authoritative revision cheatsheets from the comprehensive <em>BeyondTheAPI Software Engineering Handbook</em>.
        Every formula, table, code listing, and operational invariant has been extracted directly from the audited
        curriculum source without modification or omission.
      </div>
    </div>

    <div class="cover-metadata-grid">
      <div class="cover-meta-col">
        <strong>Curriculum Scope:</strong> Modules 1–13 (Complete Series)<br>
        <strong>Authoritative Runtimes:</strong> OpenJDK 21 LTS • Node.js v24 • PostgreSQL 16<br>
        <strong>Systems Targets:</strong> Linux / POSIX • Docker • Kubernetes
      </div>
      <div class="cover-meta-col">
        <strong>Publication Year:</strong> 2026<br>
        <strong>Format:</strong> High-Density Rapid-Revision Monograph<br>
        <strong>Content Invariant:</strong> Audited Source-Preserved Extract
      </div>
    </div>

    <div class="cover-footer-note">
      Engineered from First Principles for Rapid Technical Recall, Architectural Defense, and High-Bar Interview Preparation
    </div>
  </div>
</div>
"""

def build_how_to_use_html() -> str:
    return """
<div class="front-matter">
  <div class="fm-header">
    <div class="fm-badge">USER GUIDE & REFERENCE CONVENTIONS</div>
    <h1 class="fm-title">How to Use This Document</h1>
  </div>

  <div class="fm-section">
    <h2>1. Document Architecture & Scope</h2>
    <p>
      The <em>BeyondTheAPI Master Cheatsheets</em> provides senior backend engineers, systems designers, and high-bar
      interview candidates with an immediate, portable, high-density technical reference.
      This deliverable consolidates the thirteen concluding revision cheatsheets from each module of the
      <em>BeyondTheAPI Software Engineering Handbook</em> into a single standalone volume:
    </p>
    <ul>
      <li><strong>One-to-One Curriculum Correspondence:</strong> Each cheatsheet corresponds directly to the final revision section of its respective module (e.g., Section 21 of Module 1, Section 13 of Module 2, Section 42 of Module 13).</li>
      <li><strong>Authoritative Source Alignment:</strong> The comprehensive <em>Software Engineering Handbook</em> remains the primary source for full conceptual proofs, detailed step-by-step derivations, complete multi-file implementations, and historical context.</li>
      <li><strong>Strict Extraction Fidelity:</strong> The content in this document has been compiled directly from the audited Markdown source files without pedagogical alteration, subjective rephrasing, or content omission.</li>
    </ul>
  </div>

  <div class="fm-section">
    <h2>2. Recommended Reading & Revision Workflows</h2>
    <p>
      Depending on your operational or preparation context, use this manual according to three primary cadences:
    </p>
    <div class="workflow-grid">
      <div class="workflow-card">
        <h3>⚡ Rapid Pre-Interview Warm-Up</h3>
        <p>Review the algorithmic complexity matrices (Mod 01), concurrency happens-before invariants (Mod 09), database transaction isolation anomalies (Mod 04), and rate limiting algorithms (Mod 06) within 60 minutes of a technical session.</p>
      </div>
      <div class="workflow-card">
        <h3>🏛️ System Design Architecture Defense</h3>
        <p>Cross-reference Little's Law formulas (Mod 07 & 08), the broker architectural decision tree (Mod 08), storage selection tradeoffs (Mod 10), and multi-window burn rate thresholds (Mod 11) during design walkthroughs.</p>
      </div>
      <div class="workflow-card">
        <h3>🛠️ Production Diagnostics & Runbooks</h3>
        <p>Utilize the curated diagnostic CLI command sequences for Linux resource inspection (Mod 13), JVM thread/heap dumps (Mod 12), and network socket troubleshooting (Mod 02) during active on-call triage.</p>
      </div>
    </div>
  </div>

  <div class="fm-section">
    <h2>3. Typographic and Layout Conventions</h2>
    <p>
      All tables, formulas, and code blocks follow standardized publication typography:
    </p>
    <ul>
      <li><strong>Tables:</strong> Multi-column comparison matrices are formatted with repeating table headers (<code>thead</code>) across page splits to ensure seamless navigation across physical sheets.</li>
      <li><strong>Monospaced Listings:</strong> Shell commands, SQL statements, and Java snippets use fixed-width typography with syntax tokens highlighted for high-contrast grayscale and color reading.</li>
      <li><strong>Mathematical Equations:</strong> Complexity derivations, queuing formulas, and latency equations preserve exact mathematical notation.</li>
    </ul>
  </div>
</div>
"""

def build_master_toc_html(page_map: dict[str, int]) -> str:
    rows = []
    for mod_num in range(1, 14):
        title = CANONICAL_MODULE_TITLES[mod_num]
        desc = MODULE_DESCRIPTIONS[mod_num]
        cs_meta = CHEATSHEET_METADATA[mod_num]
        sec_num = cs_meta["sec"]
        cs_title = cs_meta["title"]

        cs_anchor = f"module-{mod_num:02d}-cheatsheet"
        page_val = page_map.get(cs_anchor, "...")

        rows.append(f"""
        <div class="toc-entry">
          <div class="toc-entry-header">
            <a href="#{cs_anchor}" class="toc-mod-title">
              <span class="toc-mod-badge">MODULE {mod_num:02d}</span> {title}
            </a>
            <span class="toc-dots"></span>
            <span class="toc-page-num">{page_val}</span>
          </div>
          <div class="toc-entry-desc">{desc}</div>
          <div class="toc-entry-cs">
            <a href="#{cs_anchor}" class="toc-cs-link">
              <span class="toc-bullet">▶</span> Section {sec_num} — {cs_title}
            </a>
          </div>
        </div>
        """)

    return f"""
<div class="master-toc">
  <div class="toc-header">
    <div class="toc-badge">TABLE OF CONTENTS</div>
    <h1 class="toc-title">Master Cheatsheet Directory</h1>
    <p class="toc-subtitle">Consolidated 13-Module Reference Index with Direct Document Navigation</p>
  </div>
  <div class="toc-grid">
    {''.join(rows)}
  </div>
</div>
"""

def build_consolidated_html(page_map: dict[str, int]) -> str:
    pygments_css = HtmlFormatter(style="default").get_style_defs(".codehilite")

    cheatsheets_html = []
    for mod_num in range(1, 14):
        cs_markdown, meta = extract_module_cheatsheet(mod_num)
        title = meta["canonical_title"]

        rendered_body = markdown.markdown(
            cs_markdown,
            extensions=["extra", "codehilite", "toc"]
        )

        module_block = f"""
        <section class="cheatsheet-module" id="module-{mod_num:02d}-cheatsheet">
          <div class="module-banner">
            <div class="module-badge">MODULE {mod_num:02d} REFERENCE</div>
            <h1 class="module-title">{title}</h1>
          </div>
          <div class="module-content">
            {rendered_body}
          </div>
        </section>
        """
        cheatsheets_html.append(module_block)

    cover_html = build_cover_html()
    how_to_use_html = build_how_to_use_html()
    toc_html = build_master_toc_html(page_map)

    stylesheet = f"""
    @page {{
      size: A4 portrait;
      margin: 18mm 15mm 20mm 15mm;
    }}

    * {{
      box-sizing: border-box;
    }}

    body {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Charter", "Source Serif Pro", serif;
      font-size: 9.2pt;
      line-height: 1.45;
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
      padding: 28mm 16mm 20mm 16mm;
      border: 2px solid #0f172a;
      background: #ffffff;
    }}
    .cover-page h2 {{
      background: transparent !important;
      border: none !important;
      padding: 0 !important;
      border-radius: 0 !important;
      margin: 0 0 20px 0 !important;
      font-size: 22pt !important;
      color: #1e3a8a !important;
      font-weight: 700 !important;
      letter-spacing: -0.3px !important;
    }}
    .cover-top-accent {{
      width: 64px;
      height: 8px;
      background: #2563eb;
      margin-bottom: 24px;
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
      margin: 0 0 6px 0;
      letter-spacing: -1px;
    }}
    .cover-subtitle {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 22pt;
      font-weight: 700;
      color: #1e3a8a;
      margin: 0 0 20px 0;
      letter-spacing: -0.3px;
    }}
    .cover-divider {{
      width: 100%;
      height: 3.5px;
      background: #2563eb;
      margin-bottom: 22px;
    }}
    .cover-descriptor {{
      font-size: 11pt;
      line-height: 1.5;
      color: #334155;
      max-width: 95%;
      margin-bottom: 28px;
    }}
    .cover-companion-notice {{
      margin-bottom: 30px;
    }}
    .companion-box {{
      background: #f8fafc;
      border-left: 4px solid #2563eb;
      border: 1px solid #e2e8f0;
      border-left-width: 4px;
      padding: 12px 16px;
      border-radius: 4px;
      font-size: 9pt;
      color: #334155;
      line-height: 1.55;
    }}
    .cover-metadata-grid {{
      display: flex;
      justify-content: space-between;
      border-top: 1px solid #cbd5e1;
      border-bottom: 1px solid #cbd5e1;
      padding: 14px 0;
      margin-bottom: 26px;
      font-size: 8.8pt;
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
      padding-top: 10px;
    }}

    /* Front Matter Styling */
    .front-matter {{
      page-break-before: always;
      page-break-after: always;
      padding: 6mm 0;
    }}
    .fm-header {{
      border-bottom: 2.5px solid #2563eb;
      padding-bottom: 8px;
      margin-bottom: 18px;
    }}
    .fm-badge {{
      display: inline-block;
      font-size: 7.8pt;
      font-weight: 700;
      letter-spacing: 1.2px;
      color: #2563eb;
      margin-bottom: 4px;
    }}
    .fm-title {{
      font-size: 22pt;
      font-weight: 800;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.5px;
    }}
    .fm-section h2 {{
      font-size: 12.5pt !important;
      font-weight: 700 !important;
      color: #1e3a8a !important;
      background: transparent !important;
      border: none !important;
      border-bottom: 1.5px solid #e2e8f0 !important;
      border-radius: 0 !important;
      padding: 0 0 4px 0 !important;
      margin-top: 16px !important;
      margin-bottom: 8px !important;
    }}
    .workflow-grid {{
      display: flex;
      justify-content: space-between;
      gap: 12px;
      margin: 12px 0;
    }}
    .workflow-card {{
      flex: 1;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 5px;
      padding: 9px 11px;
      font-size: 8pt;
      line-height: 1.42;
    }}
    .workflow-card h3 {{
      font-size: 8.8pt;
      font-weight: 700;
      color: #0f172a;
      margin: 0 0 5px 0;
      border-bottom: none;
      padding-bottom: 0;
    }}

    /* Master TOC Styling */
    .master-toc {{
      page-break-before: always;
      page-break-after: always;
      padding: 0;
    }}
    .toc-header {{
      border-bottom: 2px solid #2563eb;
      padding-bottom: 4px;
      margin-bottom: 8px;
    }}
    .toc-badge {{
      display: inline-block;
      font-size: 7.2pt;
      font-weight: 700;
      letter-spacing: 1.2px;
      color: #2563eb;
      margin-bottom: 2px;
    }}
    .toc-title {{
      font-size: 19pt;
      font-weight: 800;
      color: #0f172a;
      margin: 0 0 2px 0;
      letter-spacing: -0.5px;
    }}
    .toc-subtitle {{
      font-size: 8.5pt;
      color: #64748b;
      margin: 0;
    }}
    .toc-grid {{
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin-top: 6px;
    }}
    .toc-entry {{
      padding: 2px 5px;
      border-bottom: 1px solid #f1f5f9;
      page-break-inside: avoid;
    }}
    .toc-entry-header {{
      display: flex;
      align-items: baseline;
      width: 100%;
    }}
    .toc-mod-badge {{
      display: inline-block;
      background: #e0e7ff;
      color: #3730a3;
      font-size: 6.8pt;
      font-weight: 800;
      padding: 1px 4px;
      border-radius: 3px;
      letter-spacing: 0.8px;
      margin-right: 6px;
    }}
    .toc-mod-title {{
      font-size: 9pt;
      font-weight: 700;
      color: #0f172a;
      text-decoration: none;
      white-space: nowrap;
    }}
    .toc-dots {{
      flex: 1;
      border-bottom: 1px dotted #94a3b8;
      margin: 0 5px;
      height: 1px;
    }}
    .toc-page-num {{
      font-size: 8.8pt;
      font-weight: 700;
      color: #2563eb;
      font-variant-numeric: tabular-nums;
    }}
    .toc-entry-desc {{
      font-size: 7.2pt;
      color: #475569;
      line-height: 1.25;
      margin: 1px 0;
    }}
    .toc-entry-cs {{
      font-size: 7.2pt;
      margin-top: 0px;
    }}
    .toc-cs-link {{
      color: #059669;
      font-weight: 600;
      text-decoration: none;
    }}
    .toc-bullet {{
      font-size: 5.5pt;
      margin-right: 3px;
    }}

    /* Cheatsheet Module Styling */
    .cheatsheet-module {{
      page-break-before: always;
      margin-bottom: 24px;
    }}

    .module-banner {{
      border-bottom: 2px solid #2563eb;
      padding-bottom: 8px;
      margin-bottom: 14px;
    }}
    .module-badge {{
      display: inline-block;
      background: #2563eb;
      color: #ffffff;
      font-size: 7.8pt;
      font-weight: 800;
      padding: 2px 8px;
      border-radius: 3px;
      letter-spacing: 1px;
      margin-bottom: 4px;
    }}
    .module-title {{
      font-size: 17pt;
      font-weight: 800;
      color: #0f172a;
      margin: 4px 0 0 0;
      letter-spacing: -0.3px;
    }}

    /* Section Headings */
    h2 {{
      font-size: 13pt;
      font-weight: 800;
      color: #065f46;
      background: #ecfdf5;
      border: 1px solid #a7f3d0;
      border-left: 5px solid #059669;
      padding: 7px 12px;
      border-radius: 4px;
      margin: 16px 0 10px 0;
      page-break-after: avoid;
    }}

    h3 {{
      font-size: 10.5pt;
      font-weight: 700;
      color: #1e3a8a;
      border-bottom: 1px solid #e2e8f0;
      padding-bottom: 3px;
      margin: 16px 0 8px 0;
      page-break-after: avoid;
    }}

    h4 {{
      font-size: 9.5pt;
      font-weight: 700;
      color: #334155;
      margin: 12px 0 6px 0;
      page-break-after: avoid;
    }}

    p {{
      margin: 6px 0;
      line-height: 1.45;
    }}

    ul, ol {{
      margin: 6px 0;
      padding-left: 20px;
    }}

    li {{
      margin-bottom: 3px;
    }}

    /* Code Blocks */
    pre {{
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-left: 3.5px solid #2563eb;
      padding: 8px 12px;
      border-radius: 4px;
      font-family: ui-monospace, "SF Mono", Menlo, Monaco, Consolas, monospace;
      font-size: 7.6pt;
      line-height: 1.35;
      white-space: pre-wrap;
      word-break: break-word;
      overflow-wrap: break-word;
      margin: 10px 0;
      page-break-inside: auto;
    }}

    code {{
      font-family: ui-monospace, "SF Mono", Menlo, Monaco, Consolas, monospace;
      background: #f1f5f9;
      color: #0f172a;
      padding: 1px 4px;
      border-radius: 3px;
      font-size: 7.8pt;
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
      margin: 10px 0;
      padding: 8px 12px;
      border-radius: 0 4px 4px 0;
      page-break-inside: avoid;
    }}

    blockquote p {{
      margin: 3px 0;
    }}

    /* Tables */
    table {{
      width: 100%;
      border-collapse: collapse;
      margin: 12px 0;
      font-size: 7.8pt;
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
      padding: 4.5px 7px;
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
      margin: 18px 0;
    }}

    a {{
      color: #2563eb;
      text-decoration: none;
    }}

    {pygments_css}
    """

    full_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>BeyondTheAPI — Master Cheatsheets</title>
<style>
{stylesheet}
</style>
</head>
<body>
{cover_html}
{how_to_use_html}
{toc_html}
{''.join(cheatsheets_html)}
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
        anchor = f"module-{mod_num:02d}-cheatsheet"
        p_start = dest_map.get(anchor)
        if p_start:
            module_spans.append((mod_num, p_start, CANONICAL_MODULE_TITLES[mod_num]))

    module_spans.sort(key=lambda x: x[1])

    def get_module_title_for_page(page_no: int) -> str:
        current_title = "Handbook Cheatsheets Directory"
        for mod_num, p_start, title in module_spans:
            if page_no >= p_start:
                current_title = f"Module {mod_num:02d} — {title}"
            else:
                break
        return current_title

    # Apply headers & footers (Skip page 1 = Cover)
    for pno in range(1, total_pages):
        page = doc[pno]
        rect = page.rect
        page_no = pno + 1

        # Hairline header rule (skip pages 2 and 3 if Front Matter/TOC)
        page.draw_line(
            pymupdf.Point(42, 32),
            pymupdf.Point(rect.width - 42, 32),
            color=(0.85, 0.88, 0.91),
            width=0.5
        )
        # Running header text
        page.insert_text(
            pymupdf.Point(42, 26),
            "BeyondTheAPI • Master Cheatsheets",
            fontsize=7.2,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )
        mod_hdr = get_module_title_for_page(page_no)
        page.insert_text(
            pymupdf.Point(rect.width - 240, 26),
            mod_hdr,
            fontsize=7.2,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )

        # Hairline footer rule
        page.draw_line(
            pymupdf.Point(42, rect.height - 32),
            pymupdf.Point(rect.width - 42, rect.height - 32),
            color=(0.85, 0.88, 0.91),
            width=0.5
        )
        # Running footer text
        page.insert_text(
            pymupdf.Point(42, rect.height - 21),
            "13-Module Software Engineering Reference • Official Technical Companion",
            fontsize=7.2,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )
        pg_str = f"Page {page_no} of {total_pages}"
        page.insert_text(
            pymupdf.Point(rect.width - 95, rect.height - 21),
            pg_str,
            fontsize=7.2,
            fontname="helv",
            color=(0.4, 0.45, 0.5)
        )

    # Build hierarchical TOC bookmarks
    toc = [
        [1, "BeyondTheAPI — Master Cheatsheets", 1],
        [1, "How to Use This Document", 2],
        [1, "Master Table of Contents", 3],
    ]

    for mod_num in range(1, 14):
        title = CANONICAL_MODULE_TITLES[mod_num]
        anchor = f"module-{mod_num:02d}-cheatsheet"
        p_start = dest_map.get(anchor, 4)
        cs_meta = CHEATSHEET_METADATA[mod_num]
        sec_num = cs_meta["sec"]
        cs_title = cs_meta["title"]

        toc.append([1, f"Module {mod_num:02d}: {title}", p_start])
        toc.append([2, f"Section {sec_num} — {cs_title}", p_start])

    doc.set_toc(toc)

    # Document Metadata
    doc.set_metadata({
        "title": "BeyondTheAPI — Master Cheatsheets",
        "author": "BeyondTheAPI Engineering Team",
        "subject": "13-Module Rapid Revision Cheatsheets for Backend Engineering, Distributed Systems, Testing & Infrastructure",
        "keywords": "Cheatsheets, Computer Science, Backend Engineering, Distributed Systems, Java 21, Concurrency, Databases, Observability, Testing, Infrastructure, Kubernetes",
        "creator": "BeyondTheAPI Master Cheatsheets Publishing Engine",
        "producer": "Headless Chromium + PyMuPDF",
    })

    temp_final = pdf_path.with_suffix(".final.pdf")
    doc.save(str(temp_final))
    doc.close()

    temp_final.replace(pdf_path)

def build_master_cheatsheets_pdf() -> Path:
    DIST_DIR.mkdir(exist_ok=True)
    chrome_path = locate_chrome()
    print(f"[1/5] Using rendering engine: {chrome_path}")

    print("[2/5] Running Pass 1 (Measuring layout geometry and named destinations)...")
    pass1_pdf = DIST_DIR / "BeyondTheAPI_Cheatsheets_pass1.pdf"
    html_pass1 = build_consolidated_html({})
    t0 = time.time()
    compile_pdf_pass(html_pass1, pass1_pdf, chrome_path)
    print(f"      Pass 1 completed in {time.time() - t0:.2f}s")

    print("[3/5] Extracting rendered page numbers from Pass 1...")
    dest_map = extract_named_destinations(pass1_pdf)
    print(f"      Discovered {len(dest_map)} anchor destinations across modules.")
    for mod_num in range(1, 14):
        p_cs = dest_map.get(f"module-{mod_num:02d}-cheatsheet", "?")
        print(f"      Module {mod_num:02d} Cheatsheet: Page {p_cs}")

    print("[4/5] Running Pass 2 (Compiling cheatsheets PDF with populated Master TOC)...")
    html_pass2 = build_consolidated_html(dest_map)
    t1 = time.time()
    compile_pdf_pass(html_pass2, OUTPUT_PDF, chrome_path)
    print(f"      Pass 2 completed in {time.time() - t1:.2f}s")

    print("[5/5] Stamping running headers, footers, metadata, and PDF outline bookmarks...")
    dest_map_pass2 = extract_named_destinations(OUTPUT_PDF)
    apply_running_headers_footers_and_bookmarks(OUTPUT_PDF, dest_map_pass2)

    # Cleanup temporary artifacts
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
    print("MASTER CHEATSHEETS PDF COMPILED SUCCESSFULLY!")
    print("=" * 65)
    print(f"  Artifact Path : {OUTPUT_PDF}")
    print(f"  File Size     : {final_size:,} bytes ({final_size / 1024 / 1024:.2f} MB)")
    print(f"  Total Pages   : {total_pages}")
    print(f"  Module Count  : 13 (Modules 1–13 inclusive)")
    print("=" * 65 + "\n")

    return OUTPUT_PDF

def main() -> int:
    parser = argparse.ArgumentParser(description="BeyondTheAPI Master Cheatsheets Build Engine")
    parser.add_argument("--check-deps", action="store_true", help="Check installed rendering dependencies")
    parser.add_argument("--build-all", action="store_true", help="Compile the complete cheatsheets PDF")

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
            build_master_cheatsheets_pdf()
            return 0
        except Exception as e:
            print(f"[FATAL ERROR] Cheatsheets PDF compilation failed: {e}", file=sys.stderr)
            import traceback
            traceback.print_exc()
            return 1

    parser.print_help()
    return 0

if __name__ == "__main__":
    sys.exit(main())
