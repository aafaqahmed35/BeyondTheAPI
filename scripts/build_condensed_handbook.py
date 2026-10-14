#!/usr/bin/env python3
"""
BeyondTheAPI Condensed Edition - PDF Build Pipeline
Compiles the 13 condensed modules into a standalone ~100-page publication textbook.

Features:
- Two-pass build for exact Table of Contents page synchronization
- Python-Markdown with extra, codehilite, and toc extensions
- Pygments syntax-highlighted code blocks
- Headless Chromium PDF rendering via CSS Paged Media (ISO A4)
- PyMuPDF post-processing: running headers, footers with hairline rules,
  clickable internal links, hierarchical bookmarks tree, and metadata.
"""

import sys
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# Auto-delegate to .venv if needed
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
        sys.exit(1)

import subprocess
import shutil
import hashlib
import json
import re
import time

MODULES_CONDENSED_DIR = REPO_ROOT / "modules_condensed"
DIST_DIR = REPO_ROOT / "dist"
OUTPUT_PDF = DIST_DIR / "BeyondTheAPI_Condensed_Edition.pdf"

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
    1: "Asymptotic complexity, Master Theorem, array locality, sliding window, binary search, tree balancing, graphs, and dynamic programming.",
    2: "Kernel vs user space, process/thread models, CPU scheduling, futex synchronization, virtual memory, epoll, TCP/IP lifecycle, and latency numbers.",
    3: "Strict pass-by-value proof, initialization sequence, vtables, equals/hashCode contracts, generics PECS, collections, and modern GC.",
    4: "Logical query processing, join mechanics, normalization, B+ Trees, ACID, MVCC in PostgreSQL/MySQL, HikariCP sizing, and expand-contract migrations.",
    5: "Request lifecycle, Node.js libuv event loop 6 phases, TypeScript boundary validation, Spring IoC/AOP, JPA states, and transaction scopes.",
    6: "HTTP semantics, REST modeling, Problem Details, BOLA/IDOR defense, cookie flags, JWT 5-point verification, OAuth2 PKCE, and rate limiting.",
    7: "Hit ratio economics, Cache-Aside invalidation, Redis reactor, eviction algorithms, stampede defenses, XFetch, and distributed locking.",
    8: "Queueing physics, Kingman formula, delivery semantics, idempotent consumers, retries with full jitter, Kafka vs RabbitMQ, and Transactional Outbox.",
    9: "JMM happens-before rules, CAS, Goetz pool sizing, Virtual Threads, consistency models, quorum math, leases, fencing tokens, and CAP/PACELC.",
    10: "4-stage interview framework, back-of-the-envelope estimation, storage decision matrix, consistent hashing, circuit breakers, and 4 case studies.",
    11: "Telemetry triad, structured JSON logging, MDC, cardinality math, RED vs USE heuristics, percentile hierarchy, distributed tracing, and SLO burn rates.",
    12: "Testing pyramid, Meszaros test doubles, Testcontainers, CyclicBarrier concurrency tests, property testing, scientific debugging, and mutation scores.",
    13: "Linux architecture, robust Bash, permissions, process signals, cgroups/namespaces, multi-stage Dockerfiles, and Kubernetes probe architecture.",
}

def get_chromium_path() -> str:
    candidates = [
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/Applications/Chromium.app/Contents/MacOS/Chromium",
        shutil.which("google-chrome"),
        shutil.which("chromium"),
        shutil.which("chromium-browser"),
    ]
    for c in candidates:
        if c and os.path.exists(c):
            return c
    raise RuntimeError("No Chromium/Chrome executable discovered for PDF compilation.")

def build_cover_html() -> str:
    return f"""
    <div class="cover-page">
      <div class="cover-badge">OFFICIAL CONDENSED TEXTBOOK EDITION</div>
      <h1 class="cover-title">BeyondTheAPI</h1>
      <h2 class="cover-subtitle">Software Engineering Handbook</h2>
      <div class="cover-edition-tag">CONDENSED EDITION · ACCELERATED CURRICULUM</div>
      <div class="cover-divider"></div>
      <p class="cover-descriptor">
        A rigorous, high-density synthesis of the complete 13-module Software Engineering Handbook.
        Designed for rapid conceptual mastery, architectural synthesis, interview revision, and
        first-principles software engineering without loss of essential technical invariants,
        failure modes, algorithms, or operational procedures.
      </p>

      <div class="companion-box">
        <strong>Curriculum Specification & Companion Status:</strong><br>
        This volume represents the authorized 100-page condensed monograph derived directly from the
        392-page comprehensive edition. All 13 canonical modules are preserved in full sequence,
        consolidating cross-chapter redundancies while retaining complete causal explanations,
        code patterns, mathematical models, and production failure modes.
      </div>

      <div class="cover-metadata-grid">
        <div class="cover-meta-col">
          <strong>Curriculum Modules:</strong> Exactly 13 Modules<br>
          <strong>Format:</strong> High-Density Textbook<br>
          <strong>Target Page Budget:</strong> ~100 Pages (90–110 Range)<br>
          <strong>Source Baseline:</strong> 17,252 Audited Source Lines
        </div>
        <div class="cover-meta-col">
          <strong>Language & Platform:</strong> Java 21, TypeScript, Linux<br>
          <strong>Publishing Engine:</strong> Headless Chromium & PyMuPDF<br>
          <strong>Page Geometry:</strong> ISO A4 Portrait<br>
          <strong>Classification:</strong> Advanced Technical Publication
        </div>
      </div>

      <div class="cover-footer-note">
        BeyondTheAPI · Distributed Systems, Backend Architecture & Production Engineering
      </div>
    </div>
    """

def build_how_to_use_html() -> str:
    return """
    <div class="fm-section" id="how-to-use">
      <div class="fm-badge">PREFACE & REVISION METHODOLOGY</div>
      <h1 class="fm-title">How to Use the Condensed Edition</h1>
      <div class="fm-divider"></div>

      <p class="fm-lead">
        The <strong>BeyondTheAPI Condensed Edition</strong> is an accelerated technical textbook designed for
        working software engineers, backend architects, and candidates preparing for rigorous technical interviews.
      </p>

      <h3>1. Architecture of This Edition</h3>
      <p>
        The comprehensive edition of the BeyondTheAPI Handbook spans 392 pages, detailing full narrative discussions,
        extended historical context, and lengthy code walkthroughs. This Condensed Edition compresses the curriculum
        into approximately 100 pages using a strict <strong>zero-information-loss standard</strong>:
      </p>
      <ul>
        <li><strong>Every Distinct Algorithm & Formula:</strong> Retained with formal asymptotic bounds and derivations.</li>
        <li><strong>Every Critical Failure Mode:</strong> Preserved in structured cause-symptom-solution tables.</li>
        <li><strong>Every Security Boundary & Invariant:</strong> Maintained with explicit conditions and caveats.</li>
        <li><strong>Consolidated Redundancy:</strong> Shared topics (such as idempotency keys, connection pool physics,
            and transaction isolation) are anchored in their primary subsystem and cross-referenced elsewhere.</li>
      </ul>

      <h3>2. Pedagogical Reading Progression</h3>
      <p>
        Each module in this edition is organized in an intuitive 10-step pedagogical sequence:
      </p>
      <ol>
        <li><strong>Core Mental Model:</strong> The first-principles physical or mathematical foundation.</li>
        <li><strong>Essential Terminology:</strong> Strict technical definitions free from marketing ambiguity.</li>
        <li><strong>System Mechanics:</strong> How the runtime, protocol, or data structure actually works under the hood.</li>
        <li><strong>Implementation Patterns:</strong> Production-grade code snippets illustrating the invariant.</li>
        <li><strong>Trade-Offs & Comparisons:</strong> Multi-column matrices contrasting competing architectural choices.</li>
        <li><strong>Failure Modes & Edge Cases:</strong> Root-cause diagnoses of catastrophic production crashes.</li>
        <li><strong>Performance & Scalability:</strong> Queueing physics, memory layouts, and algorithmic bounds.</li>
        <li><strong>Security Boundaries:</strong> Authentication, authorization, trust boundaries, and attack vectors.</li>
        <li><strong>Diagnostic Procedures:</strong> Real-world Linux, SQL, and JVM commands for live incident triage.</li>
        <li><strong>Interview Synthesis:</strong> High-bar technical scenarios with structured reasoning frameworks.</li>
      </ol>

      <div class="companion-box" style="margin-top: 24px;">
        <strong>Document Triad Role:</strong><br>
        The repository provides three complementary volumes:
        (1) <em>Comprehensive Edition (392 pages):</em> Maximum explanatory and narrative depth;
        (2) <em>Condensed Edition (~100 pages, this volume):</em> Accelerated, high-density conceptual textbook;
        (3) <em>Master Cheatsheets (32 pages):</em> Pure rapid-revision matrices and command references.
      </div>
    </div>
    """

def build_master_toc_html(page_map: dict[str, int]) -> str:
    rows = []
    for mod_num in range(1, 14):
        title = CANONICAL_MODULE_TITLES[mod_num]
        desc = MODULE_DESCRIPTIONS[mod_num]
        target_page = page_map.get(f"module_{mod_num:02d}", mod_num * 8)

        row_html = f"""
        <div class="toc-card">
          <div class="toc-header-line">
            <span class="toc-mod-tag">MODULE {mod_num:02d}</span>
            <span class="toc-mod-title">{title}</span>
            <span class="toc-dots"></span>
            <a href="#module-{mod_num:02d}" class="toc-page-btn" data-target-page="{target_page}">Page {target_page}</a>
          </div>
          <div class="toc-desc">{desc}</div>
        </div>
        """
        rows.append(row_html)

    return f"""
    <div class="fm-section" id="master-toc">
      <div class="fm-badge">CURRICULUM ARCHITECTURE</div>
      <h1 class="fm-title">Master Table of Contents</h1>
      <div class="fm-divider"></div>
      <div class="toc-container">
        {''.join(rows)}
      </div>
    </div>
    """

def build_consolidated_html(page_map: dict[str, int]) -> str:
    pygments_css = HtmlFormatter(style="default").get_style_defs(".codehilite")

    modules_html = []
    for mod_num in range(1, 14):
        # Read condensed markdown file
        filename = f"module_{mod_num:02d}" + (
            "_dsa.md" if mod_num == 1 else
            "_os_networks.md" if mod_num == 2 else
            "_java_oop.md" if mod_num == 3 else
            "_database_engineering.md" if mod_num == 4 else
            "_backend_frameworks.md" if mod_num == 5 else
            "_api_engineering_security.md" if mod_num == 6 else
            "_caching_performance.md" if mod_num == 7 else
            "_async_processing_messaging.md" if mod_num == 8 else
            "_concurrency_distributed_systems.md" if mod_num == 9 else
            "_system_design_production_architecture.md" if mod_num == 10 else
            "_observability.md" if mod_num == 11 else
            "_debugging_testing.md" if mod_num == 12 else
            "_linux_containers_deployment_infrastructure.md"
        )
        mod_path = MODULES_CONDENSED_DIR / filename
        md_text = mod_path.read_text(encoding="utf-8")
        title = CANONICAL_MODULE_TITLES[mod_num]

        rendered_body = markdown.markdown(
            md_text,
            extensions=["extra", "codehilite", "toc"]
        )

        block = f"""
        <section class="condensed-module" id="module-{mod_num:02d}">
          <div class="module-banner">
            <div class="module-badge">MODULE {mod_num:02d} · CONDENSED TEXTBOOK</div>
            <h1 class="module-title">{title}</h1>
          </div>
          <div class="module-content">
            {rendered_body}
          </div>
        </section>
        """
        modules_html.append(block)

    cover_html = build_cover_html()
    how_to_use_html = build_how_to_use_html()
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
      font-size: 10pt;
      line-height: 1.55;
      color: #111827;
      background: #ffffff;
      margin: 0;
      padding: 0;
    }}

    /* Cover Page */
    .cover-page {{
      page-break-before: avoid;
      page-break-after: always;
      display: flex;
      flex-direction: column;
      justify-content: center;
      min-height: 100vh;
      padding: 30px 10px 20px 10px;
    }}
    .cover-badge {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 10.5pt;
      font-weight: 700;
      letter-spacing: 1.5px;
      color: #1d4ed8;
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
      margin: 0 0 10px 0;
      letter-spacing: -0.3px;
    }}
    .cover-edition-tag {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 11pt;
      font-weight: 600;
      letter-spacing: 1px;
      color: #059669;
      margin-bottom: 20px;
    }}
    .cover-divider {{
      width: 100%;
      height: 3.5px;
      background: #1d4ed8;
      margin-bottom: 22px;
    }}
    .cover-descriptor {{
      font-size: 11pt;
      line-height: 1.55;
      color: #334155;
      margin-bottom: 24px;
    }}
    .companion-box {{
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-left: 4px solid #1d4ed8;
      padding: 12px 16px;
      border-radius: 4px;
      font-size: 9pt;
      color: #334155;
      line-height: 1.55;
      margin-bottom: 24px;
    }}
    .cover-metadata-grid {{
      display: flex;
      justify-content: space-between;
      border-top: 1px solid #cbd5e1;
      border-bottom: 1px solid #cbd5e1;
      padding: 14px 0;
      margin-bottom: 24px;
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

    /* Front Matter */
    .fm-section {{
      page-break-before: always;
      page-break-after: always;
      padding-top: 10px;
    }}
    .fm-badge {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 9pt;
      font-weight: 700;
      letter-spacing: 1.2px;
      color: #1d4ed8;
      margin-bottom: 6px;
    }}
    .fm-title {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 24pt;
      font-weight: 800;
      color: #0f172a;
      margin: 0 0 10px 0;
      letter-spacing: -0.5px;
    }}
    .fm-divider {{
      width: 100%;
      height: 2px;
      background: #1d4ed8;
      margin-bottom: 18px;
    }}
    .fm-lead {{
      font-size: 10.5pt;
      line-height: 1.55;
      color: #1e293b;
      font-weight: 500;
      margin-bottom: 16px;
    }}

    /* Table of Contents Grid */
    .toc-container {{
      display: flex;
      flex-direction: column;
      gap: 7px;
    }}
    .toc-card {{
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-left: 3.5px solid #1d4ed8;
      border-radius: 4px;
      padding: 6px 12px;
    }}
    .toc-header-line {{
      display: flex;
      align-items: baseline;
      width: 100%;
    }}
    .toc-mod-tag {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 7.8pt;
      font-weight: 800;
      letter-spacing: 0.5px;
      color: #1d4ed8;
      margin-right: 8px;
      flex-shrink: 0;
    }}
    .toc-mod-title {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 9pt;
      font-weight: 700;
      color: #0f172a;
      flex-shrink: 0;
    }}
    .toc-dots {{
      flex-grow: 1;
      border-bottom: 1px dotted #94a3b8;
      margin: 0 8px;
      position: relative;
      top: -3px;
    }}
    .toc-page-btn {{
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
      font-size: 8.5pt;
      font-weight: 700;
      color: #0f172a;
      text-decoration: none;
      background: #e2e8f0;
      padding: 1px 7px;
      border-radius: 3px;
      border: 1px solid #cbd5e1;
      flex-shrink: 0;
    }}
    .toc-desc {{
      font-size: 7.8pt;
      line-height: 1.35;
      color: #475569;
      margin-top: 2px;
    }}

    /* Modules */
    .condensed-module {{
      page-break-before: always;
      padding-top: 6px;
    }}
    .module-banner {{
      margin-bottom: 16px;
      border-bottom: 2px solid #1d4ed8;
      padding-bottom: 10px;
    }}
    .module-badge {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 8.5pt;
      font-weight: 800;
      letter-spacing: 1.2px;
      color: #1d4ed8;
      margin-bottom: 4px;
    }}
    .module-title {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 20pt;
      font-weight: 800;
      color: #0f172a;
      margin: 0;
      letter-spacing: -0.4px;
    }}

    /* Typography & Hierarchy */
    h1 {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 16pt;
      font-weight: 700;
      color: #0f172a;
      margin: 20px 0 10px 0;
      border-bottom: 1px solid #cbd5e1;
      padding-bottom: 4px;
      page-break-after: avoid;
    }}
    .condensed-module h1:first-child {{
      display: none; /* Hide top-level title in markdown since banner contains it */
    }}
    h2 {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 12.5pt;
      font-weight: 700;
      color: #1e3a8a;
      margin: 18px 0 8px 0;
      page-break-after: avoid;
    }}
    h3 {{
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      font-size: 10.5pt;
      font-weight: 700;
      color: #0f172a;
      margin: 14px 0 6px 0;
      page-break-after: avoid;
    }}
    h4 {{
      font-size: 9.5pt;
      font-weight: 700;
      color: #334155;
      margin: 12px 0 4px 0;
      page-break-after: avoid;
    }}
    p {{
      margin: 0 0 10px 0;
      text-align: justify;
    }}
    ul, ol {{
      margin: 0 0 10px 0;
      padding-left: 20px;
    }}
    li {{
      margin-bottom: 4px;
    }}

    /* Code Blocks */
    pre, .codehilite pre {{
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
      font-size: 8.2pt;
      line-height: 1.45;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-left: 3.5px solid #1d4ed8;
      border-radius: 4px;
      padding: 8px 12px;
      margin: 10px 0;
      overflow-x: auto;
      white-space: pre-wrap;
      word-wrap: break-word;
      page-break-inside: avoid;
    }}
    code {{
      font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
      font-size: 8.2pt;
      background: #f1f5f9;
      color: #0f172a;
      padding: 1px 4px;
      border-radius: 3px;
      border: 1px solid #e2e8f0;
    }}
    pre code {{
      background: transparent;
      padding: 0;
      border: none;
    }}

    /* Tables */
    table {{
      width: 100%;
      border-collapse: collapse;
      font-size: 8pt;
      line-height: 1.4;
      margin: 12px 0;
      page-break-inside: avoid;
    }}
    th, td {{
      border: 1px solid #cbd5e1;
      padding: 5px 8px;
      text-align: left;
      vertical-align: top;
    }}
    th {{
      background: #1e293b;
      color: #ffffff;
      font-weight: 700;
    }}
    tr:nth-child(even) {{
      background: #f8fafc;
    }}

    /* Blockquotes */
    blockquote {{
      margin: 10px 0;
      padding: 8px 14px;
      background: #f1f5f9;
      border-left: 3.5px solid #059669;
      border-radius: 3px;
      font-style: italic;
      color: #334155;
      page-break-inside: avoid;
    }}

    /* Pygments */
    {pygments_css}
    """

    full_html = f"""<!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <title>BeyondTheAPI — Software Engineering Handbook (Condensed Edition)</title>
      <style>{stylesheet}</style>
    </head>
    <body>
      {cover_html}
      {how_to_use_html}
      {toc_html}
      {''.join(modules_html)}
    </body>
    </html>
    """
    return full_html

def compile_html_to_pdf(html_path: Path, output_pdf_path: Path):
    chrome_bin = get_chromium_path()
    cmd = [
        chrome_bin,
        "--headless",
        "--disable-gpu",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        f"--print-to-pdf={output_pdf_path}",
        "--no-pdf-header-footer",
        str(html_path)
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise RuntimeError(f"Chromium PDF generation failed: {res.stderr}")

def extract_module_page_starts(pdf_path: Path) -> dict[str, int]:
    doc = pymupdf.open(str(pdf_path))
    page_map = {}
    for mod_num in range(1, 14):
        pat = re.compile(rf"MODULE\s+0?{mod_num}\s+·\s+CONDENSED\s+TEXTBOOK", re.IGNORECASE)
        for pno in range(doc.page_count):
            text = doc[pno].get_text()
            if pat.search(text):
                page_map[f"module_{mod_num:02d}"] = pno + 1
                break
    doc.close()
    return page_map

def post_process_pdf(pdf_path: Path, page_map: dict[str, int]):
    doc = pymupdf.open(str(pdf_path))
    total_pages = doc.page_count
    print(f"Applying running headers/footers, bookmarks, and links to {total_pages} pages...")

    # Build TOC bookmarks
    outline = [
        [1, "BeyondTheAPI — Condensed Edition", 1],
        [1, "How to Use This Document", 2],
        [1, "Master Table of Contents", 3],
    ]

    for mod_num in range(1, 14):
        title = CANONICAL_MODULE_TITLES[mod_num]
        start_page = page_map.get(f"module_{mod_num:02d}", mod_num * 8)
        outline.append([1, f"Module {mod_num:02d}: {title}", start_page])

        # Scan for H2 headings inside this module's page range
        m_next = page_map.get(f"module_{mod_num+1:02d}", total_pages + 1)
        for pno in range(start_page - 1, m_next - 1):
            if pno >= total_pages:
                break
            page_text = doc[pno].get_text()
            for line in page_text.splitlines():
                m = re.match(r"^(\d+\.\d+)\s+([A-Za-z0-9 ,:&/\-()]+)$", line.strip())
                if m:
                    h2_title = f"{m.group(1)} {m.group(2)}"
                    if len(h2_title) < 50:
                        outline.append([2, h2_title, pno + 1])

    doc.set_toc(outline)

    # Hairline running header & footer
    for pno in range(1, total_pages):  # Skip cover (Page 1)
        page = doc[pno]
        rect = page.rect
        current_page_num = pno + 1

        # Determine current module
        active_mod_title = "Condensed Edition"
        for mod_num in range(13, 0, -1):
            if current_page_num >= page_map.get(f"module_{mod_num:02d}", 999):
                active_mod_title = f"Module {mod_num:02d} · {CANONICAL_MODULE_TITLES[mod_num]}"
                break

        # Running Header
        header_y = 32
        page.draw_line(
            pymupdf.Point(42, header_y + 4),
            pymupdf.Point(rect.width - 42, header_y + 4),
            color=(0.8, 0.84, 0.88), width=0.5
        )
        page.insert_text(
            pymupdf.Point(42, header_y),
            "BeyondTheAPI · Condensed Edition",
            fontsize=7.5, fontname="helv", color=(0.4, 0.45, 0.5)
        )
        page.insert_text(
            pymupdf.Point(rect.width - 42 - (len(active_mod_title) * 4.2), header_y),
            active_mod_title,
            fontsize=7.5, fontname="helv", color=(0.2, 0.3, 0.5)
        )

        # Running Footer
        footer_y = rect.height - 32
        page.draw_line(
            pymupdf.Point(42, footer_y - 8),
            pymupdf.Point(rect.width - 42, footer_y - 8),
            color=(0.8, 0.84, 0.88), width=0.5
        )
        page.insert_text(
            pymupdf.Point(42, footer_y),
            "13-Module Software Engineering Handbook · Condensed Edition",
            fontsize=7.5, fontname="helv", color=(0.4, 0.45, 0.5)
        )
        page_str = f"Page {current_page_num} of {total_pages}"
        page.insert_text(
            pymupdf.Point(rect.width - 42 - (len(page_str) * 4.5), footer_y),
            page_str,
            fontsize=7.5, fontname="helv", color=(0.1, 0.15, 0.25)
        )

    # Clickable internal links on Master TOC (Page 3)
    toc_page = doc[2]  # Page 3 is index 2
    for mod_num in range(1, 14):
        target_p = page_map.get(f"module_{mod_num:02d}", 1)
        # Search for "Page X" button text
        needle = f"Page {target_p}"
        rects = toc_page.search_for(needle)
        for r in rects:
            # Add link annotation
            toc_page.insert_link({
                "kind": pymupdf.LINK_GOTO,
                "from": r,
                "page": target_p - 1,
            })

    # PDF Metadata
    doc.set_metadata({
        "title": "BeyondTheAPI: Software Engineering Handbook (Condensed Edition)",
        "author": "BeyondTheAPI Engineering Team",
        "subject": "13-Module Condensed Software Engineering Curriculum & Interview Synthesis",
        "keywords": "Software Engineering, System Design, Concurrency, Algorithms, Architecture, Performance, Databases",
        "creator": "BeyondTheAPI Publishing Pipeline",
        "producer": "PyMuPDF & Headless Chromium",
    })

    tmp_out = pdf_path.with_name("tmp_annotated.pdf")
    doc.save(str(tmp_out), incremental=False, encryption=pymupdf.PDF_ENCRYPT_KEEP)
    doc.close()
    tmp_out.replace(pdf_path)

def build_condensed_edition():
    print("=" * 70)
    print("   BEYONDTHEAPI CONDENSED EDITION - COMPILATION PIPELINE")
    print("=" * 70)

    DIST_DIR.mkdir(parents=True, exist_ok=True)
    tmp_html = DIST_DIR / "condensed_tmp.html"
    tmp_pdf = DIST_DIR / "condensed_pass1.pdf"

    # Pass 1: Initial render with provisional page numbers
    print("[PASS 1] Generating preliminary HTML layout...")
    provisional_map = {f"module_{m:02d}": 4 + (m - 1) * 7 for m in range(1, 14)}
    pass1_html = build_consolidated_html(provisional_map)
    tmp_html.write_text(pass1_html, encoding="utf-8")

    print("[PASS 1] Compiling initial PDF via Chromium...")
    compile_html_to_pdf(tmp_html, tmp_pdf)

    # Extract actual module start pages
    print("[PASS 1] Extracting exact module start pages...")
    actual_page_map = extract_module_page_starts(tmp_pdf)
    for m in range(1, 14):
        print(f"  Module {m:02d} starts on: Page {actual_page_map.get(f'module_{m:02d}', 'UNKNOWN')}")

    # Pass 2: Re-generate HTML with exact page numbers
    print("\n[PASS 2] Generating final HTML layout with exact page numbers...")
    pass2_html = build_consolidated_html(actual_page_map)
    tmp_html.write_text(pass2_html, encoding="utf-8")

    print("[PASS 2] Compiling final PDF via Chromium...")
    compile_html_to_pdf(tmp_html, OUTPUT_PDF)

    # Post-process with PyMuPDF
    print("[POST-PROCESS] Applying running headers, footers, bookmarks, and links...")
    post_process_pdf(OUTPUT_PDF, actual_page_map)

    # Clean up temp files
    if tmp_html.exists():
        tmp_html.unlink()
    if tmp_pdf.exists():
        tmp_pdf.unlink()

    doc = pymupdf.open(str(OUTPUT_PDF))
    final_pages = doc.page_count
    doc.close()
    file_size = OUTPUT_PDF.stat().st_size

    print("\n" + "=" * 70)
    print(f"[SUCCESS] Generated: {OUTPUT_PDF}")
    print(f"Total Pages: {final_pages} (Target: 90–110 pages)")
    print(f"File Size: {file_size:,} bytes ({file_size / 1024 / 1024:.2f} MB)")
    print("=" * 70)

if __name__ == "__main__":
    build_condensed_edition()
