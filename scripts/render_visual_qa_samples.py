#!/usr/bin/env python3
"""
render_visual_qa_samples.py
Renders high-resolution PNG snapshots of representative pages from
dist/BeyondTheAPI_Condensed_Edition.pdf for Visual QA inspection.
"""

from pathlib import Path
import pymupdf

REPO_ROOT = Path(__file__).resolve().parent.parent
PDF_PATH = REPO_ROOT / "dist" / "BeyondTheAPI_Condensed_Edition.pdf"
OUT_DIR = REPO_ROOT / "dist" / "qa_inspections" / "condensed"
OUT_DIR.mkdir(parents=True, exist_ok=True)

SAMPLE_PAGES = [
    1,   # Cover
    2,   # Preface / How to Use
    4,   # Master Table of Contents
    5,   # Module 01 start (DSA)
    13,  # Module 02 start (OS & Networks)
    28,  # Module 04 start (Database Engineering)
    35,  # Module 05 start (Backend Frameworks)
    61,  # Module 09 start (Concurrency & Distributed Systems)
    69,  # Module 10 start (System Design)
    72,  # Module 10 Case Studies (Flux / ArgusIQ diagrams)
    88,  # Module 13 start (Linux & Infrastructure)
    94,  # Final Page
]

def render_samples():
    doc = pymupdf.open(str(PDF_PATH))
    total_pages = doc.page_count
    print(f"Opened PDF with {total_pages} pages.")

    rendered = []
    for p_num in SAMPLE_PAGES:
        if 1 <= p_num <= total_pages:
            page = doc[p_num - 1]
            pix = page.get_pixmap(dpi=150)
            out_file = OUT_DIR / f"page_{p_num:02d}.png"
            pix.save(str(out_file))
            rendered.append(out_file)
            print(f"Rendered Page {p_num:02d} -> {out_file} ({pix.width}x{pix.height})")

    doc.close()
    print(f"\n[SUCCESS] Rendered {len(rendered)} inspection images in {OUT_DIR}")

if __name__ == "__main__":
    render_samples()
