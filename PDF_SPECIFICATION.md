# PDF Publication and Typesetting Specification

## 1. Objective and Publication Philosophy

The authoritative source of truth for all content in this project is the collection of modular Markdown files (`modules/module_XX_*.md`). 

The PDF is a **downstream publication artifact** designed to provide a unified, publication-grade reading experience suitable for printing or tablet reading.

- **Final Consolidated Output Target:** `dist/FAANG_Backend_Engineering_Handbook.pdf`
- **Module Draft Target:** `dist/drafts/module_XX_preview.pdf` (when draft rendering is invoked)

> **CRITICAL RULE:** Under no circumstances should a "final" consolidated handbook PDF be assembled while modules remain unwritten or in draft status. The final consolidated PDF will only be compiled upon the completion and explicit human approval of all 14 curriculum modules.

---

## 2. Layout, Geometry, and Typography Guidelines

When rendered via CSS Paged Media (e.g., WeasyPrint, PrinceXML) or headless browser automation (e.g., Puppeteer / Playwright printing to PDF):

### 2.1 Page Setup and Geometry
- **Page Size:** Standard US Letter (8.5 in x 11.0 in) or ISO A4 (210 mm x 297 mm), configured via `@page`.
- **Margins:**
  - Top: `25mm`
  - Bottom: `25mm`
  - Inside (Binding): `30mm` (mirror margins for two-sided book binding)
  - Outside: `20mm`
- **Page Numbering:**
  - Front matter (Cover, TOC, Preface): Roman numerals (`i`, `ii`, `iii`, etc.).
  - Body modules: Arabic numerals (`1`, `2`, `3`, etc.), resetting to 1 at Module 1, continuous across chapters.
  - Running header: Module title on even pages; current section title on odd pages.
  - Running footer: Project title left-aligned; page number right-aligned (`Page X of Y`).

### 2.2 Typography
- **Primary Body Font:** Clean modern serif or humanist sans-serif (e.g., *Charter*, *Source Serif Pro*, or *Inter*).
- **Body Font Size:** `10pt` to `10.5pt` with `1.4` to `1.5` line-height for optimal optical density and scannability.
- **Headings:** Bold geometric sans-serif (e.g., *Inter*, *Outfit*, *Roboto*).
  - Heading 1 (Module Title): `24pt`, bold, with distinct chapter ornamentation and top spacing.
  - Heading 2 (Major Sections): `16pt`, semi-bold, forced `break-before: page` for top-level sections if appropriate, or `break-after: avoid`.
  - Heading 3 (Subsections): `12.5pt`, semi-bold, `break-after: avoid`.
  - Heading 4 (Deep Dives): `11pt`, medium, italic or bold.
- **Monospace & Code Font:** *JetBrains Mono*, *Fira Code*, or *Source Code Pro*.
  - Monospace font size: `8.5pt` with `1.35` line-height.
  - Inline code: Light gray background (`#f3f4f6`), rounded border (`2px`), padded (`1px 4px`).

### 2.3 Code Blocks and Listings
- Syntax highlighting with a high-contrast, accessible light theme (e.g., GitHub Light or Solarized Light) for printable readability.
- Border: `1px solid #e5e7eb` with subtle left accent border (`3px solid #3b82f6`).
- Padding: `10pt` internal padding.
- Page break control: `break-inside: avoid` for code listings under 30 lines. For larger listings, break cleanly without clipping characters or splitting syntax tokens.
- Overflow: Must wrap lines gracefully or use clean indentation; horizontal truncation is strictly forbidden.

### 2.4 Callout Boxes and Tables
- **Callouts (Invariants, Warnings, Traps):** Distinct left border (`4px solid`), light background shading, internal padding (`8pt 12pt`), `break-inside: avoid`.
- **Tables:** Full width, bordered, zebra-striped rows, bold headers, `break-inside: avoid` for small tables, repeating table headers (`thead { display: table-header-group; }`) across page breaks.

---

## 3. Publication Pipeline Architecture

The planned compilation pipeline (`scripts/build_handbook.py`) operates as follows:

```
+------------------------------------+
|  Markdown Sources                  |
|  (modules/module_01_dsa.md, etc.)  |
+-----------------+------------------+
                  |
                  v
+------------------------------------+
|  Pre-Processor                     |
|  - Validates section integrity     |
|  - Inlines Mermaid / SVG diagrams  |
|  - Normalizes code fences          |
+-----------------+------------------+
                  |
                  v
+------------------------------------+
|  HTML5 / CSS Paged Media Generator |
|  - Professional book stylesheet    |
|  - Table of contents generator     |
|  - Syntax highlighter (Pygments)   |
+-----------------+------------------+
                  |
                  v
+------------------------------------+
|  PDF Engine                        |
|  (WeasyPrint / Headless Chromium)  |
+-----------------+------------------+
                  |
                  v
+------------------------------------+
|  Target Artifact                   |
|  dist/FAANG_Engineering_Book.pdf   |
+------------------------------------+
```

### Toolchain Dependencies and Installation Strategy
1. **Core Recommendation:** Python-based `weasyprint` or headless browser PDF rendering via standard Chromium.
2. **Current Workspace Status:** The repository currently operates in a zero-external-dependency posture. Python 3.14 and OpenJDK 21 are natively available. To prevent unvetted global package pollution or environment instability, PDF generation tools will be invoked on-demand via isolated environments or lightweight renderers once multi-module consolidation commences.
