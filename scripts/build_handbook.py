#!/usr/bin/env python3
"""
FAANG Engineering Handbook - Publication Build Engine
Handles compilation of Markdown sources into HTML/PDF publication artifacts.
Enforces that final consolidated book compilation is gated on 100% curriculum completion.
"""

import sys
import argparse
import subprocess
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
MODULES_DIR = REPO_ROOT / "modules"
DIST_DIR = REPO_ROOT / "dist"
DRAFTS_DIR = DIST_DIR / "drafts"

TOTAL_MODULES_COUNT = 14

def check_render_tools() -> dict[str, bool]:
    """Check availability of external PDF rendering tools without auto-installing."""
    return {
        "weasyprint": shutil.which("weasyprint") is not None,
        "pandoc": shutil.which("pandoc") is not None,
        "chromium": (shutil.which("chromium") is not None or 
                     shutil.which("google-chrome") is not None or
                     Path("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome").exists()),
        "wkhtmltopdf": shutil.which("wkhtmltopdf") is not None,
    }

def build_draft_html(module_name: str) -> Path:
    """Build a standalone styled HTML preview for draft inspection."""
    DIST_DIR.mkdir(exist_ok=True)
    DRAFTS_DIR.mkdir(exist_ok=True)
    
    src_file = MODULES_DIR / f"{module_name}.md"
    if not src_file.exists():
        raise FileNotFoundError(f"Source module file not found: {src_file}")
        
    with open(src_file, "r", encoding="utf-8") as f:
        md_text = f.read()

    # Minimal clean reader CSS matching PDF_SPECIFICATION.md
    html_output = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Draft Preview - {module_name}</title>
<style>
  body {{
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
    line-height: 1.6;
    color: #1f2937;
    max-width: 900px;
    margin: 40px auto;
    padding: 0 24px;
    background-color: #fafafa;
  }}
  article {{
    background: #ffffff;
    padding: 48px;
    border-radius: 8px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    border: 1px solid #e5e7eb;
  }}
  h1, h2, h3, h4 {{ color: #111827; font-weight: 700; }}
  h1 {{ border-bottom: 2px solid #e5e7eb; padding-bottom: 12px; font-size: 2rem; }}
  h2 {{ border-bottom: 1px solid #f3f4f6; padding-bottom: 8px; margin-top: 36px; font-size: 1.5rem; }}
  h3 {{ margin-top: 28px; font-size: 1.25rem; }}
  pre {{
    background: #1e293b;
    color: #f8fafc;
    padding: 16px;
    border-radius: 6px;
    overflow-x: auto;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 0.9rem;
    line-height: 1.45;
  }}
  code {{
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    background: #f1f5f9;
    color: #0f172a;
    padding: 2px 5px;
    border-radius: 4px;
    font-size: 0.88em;
  }}
  pre code {{ background: transparent; color: inherit; padding: 0; }}
  blockquote {{
    border-left: 4px solid #3b82f6;
    margin: 16px 0;
    padding: 8px 16px;
    background: #eff6ff;
    color: #1e3a8a;
  }}
  table {{
    width: 100%;
    border-collapse: collapse;
    margin: 20px 0;
  }}
  th, td {{
    border: 1px solid #e5e7eb;
    padding: 10px 14px;
    text-align: left;
  }}
  th {{ background: #f9fafb; font-weight: 600; }}
  tr:nth-child(even) {{ background: #f9fafb; }}
  .notice {{
    background: #fffbeb;
    border: 1px solid #fde68a;
    color: #92400e;
    padding: 12px 16px;
    border-radius: 6px;
    margin-bottom: 24px;
    font-size: 0.95rem;
  }}
</style>
</head>
<body>
<div class="notice">
  <strong>Draft Preview Notice:</strong> This is a rendered preview of <code>{src_file.name}</code>. 
  The authoritative source of truth is the Markdown source.
</div>
<article>
<pre style="background: transparent; color: inherit; padding: 0; white-space: pre-wrap; font-family: inherit;">
{md_text}
</pre>
</article>
</body>
</html>
"""
    out_file = DRAFTS_DIR / f"{module_name}_preview.html"
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(html_output)
        
    return out_file

def main() -> int:
    parser = argparse.ArgumentParser(description="FAANG Engineering Handbook Build System")
    parser.add_argument("--check-deps", action="store_true", help="Check installed PDF compilation tools")
    parser.add_argument("--draft", type=str, help="Render a draft preview of a specific module (e.g., module_01_dsa)")
    parser.add_argument("--build-all", action="store_true", help="Attempt consolidated handbook build (Requires all 14 modules)")
    
    args = parser.parse_args()
    
    print("=================================================================")
    print("          FAANG ENGINEERING HANDBOOK - BUILD ENGINE              ")
    print("=================================================================")
    
    tools = check_render_tools()
    print("Installed Tooling Status:")
    for tool, installed in tools.items():
        status = "INSTALLED" if installed else "NOT FOUND"
        print(f"  - {tool:15}: {status}")
    print()

    if args.check_deps:
        return 0

    if args.draft:
        mod_name = args.draft.replace(".md", "")
        print(f"Building draft preview for: {mod_name}...")
        try:
            out_file = build_draft_html(mod_name)
            print(f"[SUCCESS] Draft preview generated at:\n  {out_file}")
            return 0
        except Exception as e:
            print(f"[ERROR] Failed to build draft preview: {e}")
            return 1

    if args.build_all:
        print("Checking prerequisites for consolidated handbook build...")
        existing_modules = list(MODULES_DIR.glob("module_*.md"))
        print(f"Discovered completed modules: {len(existing_modules)} / {TOTAL_MODULES_COUNT}")
        
        if len(existing_modules) < TOTAL_MODULES_COUNT:
            print("\n[BLOCKED] CONSOLIDATED BUILD HALTED:")
            print("  According to the Curriculum Governance and PDF Specification, a final")
            print("  consolidated handbook PDF cannot be published while modules remain incomplete.")
            print(f"  Currently available: {len(existing_modules)} of {TOTAL_MODULES_COUNT} modules.")
            print("  To preview individual completed modules, run:")
            print("    python3 scripts/build_handbook.py --draft module_01_dsa\n")
            return 1
            
        print("[INFO] All 14 modules detected. Proceeding to compilation...")
        # Full compilation logic to be activated upon completion of all modules
        return 0

    parser.print_help()
    return 0

if __name__ == "__main__":
    sys.exit(main())
