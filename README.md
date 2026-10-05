# Protocol Builder

A Java command-line tool that turns CT scanning protocols exported from a GE scanner into a single browsable "protocol book" (HTML), plus normalized JSON. It reads either of two GE export formats, fills a typed model (kV, mA, pitch, kernel, dose, contrast, reconstructions, etc.), and keeps anything it doesn't recognize instead of dropping it.

- **Recognized fields** (protocol name, body part, kV, mA, pitch, dose, series, reconstructions, contrast, ...) populate a typed model.
- **Everything else** — free-text notes/comments and any workbook or XML field the parser doesn't have a slot for — is retained verbatim in `Protocol.notes` and `Protocol.advanced` rather than silently discarded.

## What it reads

The same `Main` command accepts either of two input types, auto-detected from whether the path is a file or a directory:

1. **A single `Protocols.xlsm` (or `.xlsx`/`.xls`) workbook** — parsed by `GEWorkbookParser`. Each worksheet that looks like a protocol (heuristically: sheet name and cell contents mention things like "protocol", "kV", "series", "recon", "patient position", "contrast", "CTDI", etc.) becomes one `Protocol`. Sheets named things like "cover", "instructions", "contents", "index", "lookup", "config", or "template", and hidden sheets, are skipped.
2. **A folder of GE Revolution protocol exports** — parsed by `ProtocolFolderWalker` / `UIRxProtocolParser`. The folder is searched recursively (any nesting is fine) for subfolders that each contain `protocolmetadata.json` and `UIRx.xml`; `session.xml`, if present alongside them, supplies human-readable series/recon names and any derived MPR reformats (coronal/sagittal views). If the same protocol number (`slotNumber` in `protocolmetadata.json`) appears in more than one folder — e.g. a protocol was re-saved and both the old and new export are still on disk — only the copy with the most recent `lastUpdatedDateTime` is kept; the rest are logged as `WARN:` and dropped.

You don't need to tell the tool which format you're using — point it at a file for the workbook path, or a directory for the folder-walk path.

## Requirements

- Java 17 or newer to build and run (tested on 17, 21 and 25). The compiled code itself still targets Java 8.
- The Gradle **wrapper** (`gradlew` / `gradlew.bat`) — always use it, never a system-installed `gradle` binary. The wrapper pins the exact Gradle version (9.x) this build is written for — 9.x is needed to run on Java 25, where 8.x fails with `Unsupported class file major version 69`; a distro-packaged `gradle` is often years out of date and will fail with confusing errors on this `build.gradle`.

No manual dependency installation is needed — Gradle resolves Apache POI (`poi-ooxml`), `org.json`, and JUnit 5 from Maven Central on first run.

## The Protocol Builder window (GUI)

For building or reviewing the book without editing JSON by hand, double-click **`run-gui.bat`** (Windows) or run `./run-gui.sh` / `./gradlew gui` (macOS/Linux). Keep the console window it opens running while you work; closing it closes the window too. It walks through the job one screen at a time:

1. **Choose protocols** — the exported protocols folder (or a `Protocols.xlsm` workbook) and the changes file, `protocol-overrides.json`. Label files, `logo.png`, `changelog.json`, `manual-protocols.json` and the `reference workbooks` folder are read from the changes file's folder, the way the command line reads them from the current directory.
2. **Leave out protocols** — every protocol with a *Leave out* box, a search box, and a note on duplicates (same settings or same name). *Leave out suggested duplicates* keeps the most recently updated copy of each identical group, the same suggestion as `--duplicates`.
3. **One screen per section** (Adult — Head, Adult — Chest, ...) — one line per protocol with kV, mA, pitch, contrast volume @ rate, delay, main recon, CTDIvol and notes. Values set by hand show in the accent color. *Worth a look* flags likely problems: a name that says "with contrast" with no IV contrast (or the reverse), a contrast series with no volume or rate, and a kV that differs from the one most of the section uses. Tick *Edit* on the protocols to change and press *Edit checked* (or double-click a row); *Set contrast for checked* applies one volume/rate/delay to several at once. *Section reviewed* ticks the section off in the step list.
   - The editor has four tabs. **Exam & contrast**: title, contrast volume/rate/delay, sends-to, scan range, exam dose, 3D on/off and scanning notes, with the scanner's value shown next to each box. **Series settings** and **Recons**: grids that start from what the book shows now, where any cell you change is highlighted (hover a cell to see the scanner's value). Clearing a cell, or typing the scanner's value back, removes the change. **Added fields**: information the scanner has no place for. *Add a field* asks what it applies to (Exam or one series), a title (titles used before are offered again) and the value. *Save & next* / *Save & previous* step through the checked protocols.
4. **Review & create book** — every manual change in one list (scanner value → new value), then the outputs. The **book PDF** and the **list of manual changes (PDF)** are ticked by default; the **HTML book** is optional. Choose the book title, the file name (*Save as*) and the folder. Files come out as `<name>.pdf`, `<name>.html` and `<name> - changes.pdf`.

Every edit is saved to `protocol-overrides.json` straight away (the previous version is kept as `protocol-overrides.json.bak`), so you can stop halfway through and pick up later. It's the same file the command line and the `.bat` scripts use, so either works on the same changes. Changes only affect the book; the scanner keeps its own settings. That's why the changes PDF lists the scanner's value next to each new one: it doubles as the list of what to update at the console.

The **Colors** menu sets the book's two colors: a few ready-made pairs, or *Choose main color* / *Choose accent color* for any color. The window's header bar shows the current pair. The choice, along with the folders, title, file name and output boxes, is remembered for next time.

## Quick start

macOS/Linux:

```bash
./gradlew test
./gradlew run --args="/path/to/Protocols.xlsm"
./gradlew run --args="/path/to/ExportedProtocolsFolder"
```

Windows:

```powershell
gradlew.bat test
gradlew.bat run --args="C:\path\to\Protocols.xlsm"
gradlew.bat run --args="C:\path\to\ExportedProtocolsFolder"
```

With `Protocols.xlsm` in this directory, running `./gradlew run` (or `gradlew.bat run`) with no arguments is enough — it defaults to `./Protocols.xlsm`. Parse/validation failures are printed as `ERROR:` messages and the process exits with code `2`.

### Copy-paste commands

Run from inside the `protocol_builder` directory. Substitute your actual workbook path — match the filename's case exactly (Linux filesystems are case-sensitive: `protocols.xlsm` and `Protocols.xlsm` are different files).

Linux/macOS:

```bash
cd ~/protocol_builder
./gradlew test
./gradlew run --args="./protocols.xlsm"
./gradlew run --args="$HOME/protocol_builder/protocols.xlsm"
./gradlew run --args="./protocols.xlsm --html book.html"
```

> `--args` takes one quoted string, don't pass it twice — combine flags into the one string instead, e.g. `--args="./protocols.xlsm --html book.html"`.

Windows (PowerShell or Command Prompt):

```powershell
cd C:\path\to\protocol_builder
gradlew.bat test
gradlew.bat run --args="protocols.xlsm"
gradlew.bat run --args="C:\path\to\protocol_builder\protocols.xlsm"
gradlew.bat run --args="protocols.xlsm --html book.html"
```

> `--args` takes one quoted string on Windows too — combine flags into it the same way, e.g. `--args="protocols.xlsm --html book.html"`.

> Note: `~` is **not** expanded by Gradle's `--args` — it's passed through as a literal character, not resolved to your home directory the way it would be at an interactive shell prompt. Use `$HOME` (Linux/macOS) or a full `C:\...` path (Windows) instead, or just a relative path when already in the right directory.

## Command-line reference

```
Main <input> [--json <dir>] [--html <file>] [--peds-weights <file>] [--overrides <file>]
             [--kernel-labels <file>] [--plane-labels <file>] [--category-labels <file>]
             [--logo <file>] [--pdf-library <file>] [--reference-library <file>] [--manual-protocols <file>]
             [--protocol-images-base <url>] [--protocol-images-ext <ext, default png>]
             [--reference-workbook <file>]... [--reference-folder <dir>]
             [--init-overrides] [--init-kernel-labels] [--init-plane-labels] [--init-category-labels]
```

Because this is a Gradle `application` project, every invocation goes through `./gradlew run --args="..."` — put the whole argument string in one quoted `--args` value, exactly as shown below.

| Argument | Meaning |
|---|---|
| `<input>` (positional, required unless `Protocols.xlsm` exists locally) | Path to a `.xlsm`/`.xlsx`/`.xls` workbook, **or** a folder to recursively walk for GE protocol export subfolders. Defaults to `Protocols.xlsm` in the current directory if omitted. |
| `--json <dir>` | Write one normalized JSON file per protocol into `<dir>` (created if needed). See [JSON output](#json-output) below. |
| `--html <file>` | Render every parsed protocol as a single self-contained, browsable HTML file at `<file>`. See [HTML protocol book](#html-protocol-book) below. |
| `--pdf <file>` | The same book as a printable PDF: cover, contents with page numbers, then one protocol per page with the same content and order as the HTML book (minus `--protocol-images-base` images). Close the PDF in your viewer before re-running, or Windows won't let it be overwritten. |
| `--changes-pdf <file>` | Write a PDF listing every value set by hand in the overrides file, by section and protocol, next to the scanner's own value. Values set this way only change the book, so this is also the list of what to update at the scanner. |
| `--primary-color <#hex>` / `--accent-color <#hex>` | Recolor the book (HTML, PDF and changes PDF). Main color: page background, headings, table headers (default `#044281`). Accent: sidebar, header underline, notes box (default `#ff8200`). |
| `--gui` | Open the Protocol Builder window instead (see [The Protocol Builder window](#the-protocol-builder-window-gui)); must be the first argument. |
| `--duplicates <file>` | Write a list of protocols that are effectively duplicates — identical settings under two numbers, or the same name with different settings (with what differs) — plus the `protocol-overrides.json` lines that would hide the extra copies. See [Duplicate protocols](#duplicate-protocols). |
| `--book-title <text>` | Sets the browser tab title and the welcome-page heading in the HTML book. Defaults to "Protocol Book". Only takes effect together with `--html`. |
| `--changelog <file>` | Path to a hand-typed "what changed and why" log, rendered as the book's "Recent Changes" sidebar entry/table (see below). Defaults to `./changelog.json`; used only if present. Only takes effect together with `--html`. |
| `--peds-weights <file>` | Write a printable sheet of protocols whose patient type is pediatric, with any weight-in-kg found in the protocol name annotated with its pound equivalent. |
| `--overrides <file>` | Path to the hand-maintained overrides JSON (title, notes, exclusions, send destinations, contrast volume/rate). Defaults to `./protocol-overrides.json`; used only if the file exists. Only takes effect together with `--html`. |
| `--kernel-labels <file>` | Path to the recon-kernel-code → label lookup. Defaults to `./kernel-labels.json`; used only if present. Only takes effect together with `--html`. |
| `--plane-labels <file>` | Path to the scout-plane-angle → label lookup. Defaults to `./plane-labels.json`; used only if present. Only takes effect together with `--html`. |
| `--category-labels <file>` | Path to the protocol-number-prefix (1-9 adult, 11-19 pediatric) → reading-category override lookup. Defaults to `./category-labels.json`; used only if present. Only takes effect together with `--html`. |
| `--logo <file>` | Image (PNG/JPG/GIF/SVG) embedded as a base64 data URI at the top of the sidebar, the welcome view, and every protocol page. Defaults to `./logo.png`; used only if present. Only takes effect together with `--html`. |
| `--pdf-library <file>` | Path to the hand-maintained list of externally hosted PDFs (title+url pairs) shown as their own "Surgical Planning" sidebar category. Defaults to `./pdf-library.json`; used only if present. Only takes effect together with `--html`. |
| `--reference-library <file>` | Same format as `--pdf-library` (title+url pairs), rendered as a separate "Reference Documents" sidebar category — for links that aren't surgical planning material (e.g. oral contrast administration guides). Defaults to `./reference-library.json`; used only if present. Only takes effect together with `--html`. |
| `--manual-protocols <file>` | Path to hand-authored protocols that don't exist as a folder on the scanner. Defaults to `./manual-protocols.json`; used only if present. Merged in before every output (`--json`/`--html`/`--peds-weights`), not just `--html`. |
| `--protocol-images-base <url>` | Base URL where per-protocol reference images are hosted, named `<protocolNumber>.<ext>` (e.g. `9.2.png`) — no list to maintain, each protocol page just attempts to load its own image and hides it client-side if that one 404s. Only takes effect together with `--html`. |
| `--protocol-images-ext <ext>` | File extension used with `--protocol-images-base`. Defaults to `png`. |
| `--reference-workbook <file>` | One of your own one-sheet-per-protocol reference workbooks to take scan ranges from (see [Scan ranges](#scan-ranges-from-your-reference-workbooks)). Repeat for more than one. |
| `--reference-folder <dir>` | Every `.xlsx`/`.xlsm`/`.xls` in this folder is used as a reference workbook too. Defaults to `./reference workbooks`; used only if present. |
| `--init-overrides` | Add an empty entry to the overrides file for every protocol number found that isn't already listed, label every entry with its scanner protocol name (`protocolName`), and rewrite the file sorted by protocol number. Settings you've already typed (notes, `excluded`, titles, send destinations, ...) are never changed, and entries for protocols no longer on the scanner are kept. The previous file is saved as `protocol-overrides.json.bak` first. |
| `--init-kernel-labels` | Add an empty entry to the kernel-labels file for every recon kernel code found that isn't already listed. |
| `--init-plane-labels` | Add an empty entry to the plane-labels file for every scout plane code found that isn't already listed. |
| `--init-category-labels` | Add an entry to the category-labels file for every distinct protocol-number prefix found that isn't already listed. |

All four `--init-*` flags, and `--json`/`--html`/`--peds-weights`, can be combined in a single run alongside one another. Every run always prints the console summary described below regardless of which other flags are passed.

Every run first prints a one-line-per-protocol console summary:

```
Parsed 42 protocol(s) from /path/to/input
- CT LWR EXT KNEE WITH CONTRAST: 6 series, 14 reconstructions, 1 notes, 3 advanced fields
- ...
```

## Label and override files

GE's raw export only ever gives you numeric/coded values for a few fields that are genuinely site-specific and can't be derived from the export itself — you have to look them up once at the scanner console (or its documentation) and record them here. These files are plain, hand-editable JSON that you keep alongside the tool (not regenerated data — re-parsing never touches values you've already filled in).

### `protocol-overrides.json` — per-protocol title, notes, exclusion, send destination, contrast

Keyed by protocol number (the same `slotNumber`/protocol number shown in the console summary and HTML book) — this is also the easiest way to **rename a protocol's displayed title, exclude it, or correct its displayed contrast volume/rate**, without touching the scanner export:

```json
{
  "9.2":  { "protocolName": "CT LWR EXT KNEE WITH CONTRAST", "notes": "Have the patient bend the knee slightly for...", "excluded": false, "sendDestination": "" },
  "9.4":  { "excluded": true },
  "5.1":  { "sendDestination": "AHSPACS + 3D Lab" },
  "3.7":  { "title": "CT Neck Soft Tissue (renamed)" },
  "5.2":  { "contrastVolume": "100", "contrastRate": "3.5" },
  "8.6":  { "referenceSheet": "CT Routine Abd-Pel" },
  "8.7":  { "scanRange": "Iliac crests to ischial tuberosities" },
  "1.5":  { "reconSendDestinations": { "AXIAL CTA HEAD": "AHSPACS, RAPID 1", "CTP MAPS": "RAPID 1" } }
}
```

- `title` — overrides how the protocol displays in the HTML book (sidebar link and page header) without changing its underlying scanner name, which still flows through unchanged everywhere else (console summary, `--json`). Leave blank/omit to keep the scanner name.
- `notes` — free-text scanning/study notes, shown in a highlighted "Scanning notes" box near the top of the protocol's page in the HTML book and PDF.
- `excluded` — when `true`, the protocol is left out of the generated HTML book entirely (still counted in the console summary and JSON output).
- `sendDestination` — where images from this protocol are routed, typed by hand. Optional: without it, the book shows an "Auto-sends to:" line built from the auto-send hosts in the export (see below). Set it when you want to word the destination yourself or list a destination the scanner doesn't auto-send to.

**Auto-send hosts** are read straight from the export: in `session.xml`, each recon and reformat has an `AutoJobTask` naming one `CTJobHost` per destination (e.g. `AHSPACS`, `RAPID 1`). They show per recon in the book's "Auto-send" column and as `sendDestinations` in the JSON. A blank cell means that recon isn't auto-sent (e.g. "by request only" MAR recons). Dose-report hosts (`DOSESC#...`/`DOSESR#...`) are left out.
- **Any parameter the book shows can be overridden.** What you type replaces the scanner's value in the HTML book and PDF; it's shown bold with a dotted underline (hover in the HTML book to see the scanner's own value), and the page gets a one-line note saying values were set by hand. Values are text, shown exactly as typed.
  - `series` — acquisition settings, by series number as shown in the book (`"1"` is usually the scouts, `"2"` the first scan): `kv`, `ma` (e.g. `"100-635"`), `noiseIndex`, `pitch` (`"0.984"` shows as `0.984:1`), `rotationTime`, `ctdi`. On a scout series only `kv` and `ma` are shown.
  - `recons` — per recon, by recon name as shown in the book (case and extra spaces don't matter): `name` (renames the row), `thickness`, `interval`, `kernel` (shown exactly as typed, e.g. `"Bone"`), `asir` (e.g. `"40%"`), `wwwl` (e.g. `"400/40"`), `sendTo` (every host, comma-separated - same as `reconSendDestinations`).
  - `contrastDelay` (seconds, e.g. `"90"`), `examCtdi`, `examDlp` — alongside the existing `contrastVolume` / `contrastRate`.
  - A recon name or series number that matches nothing, or a field name that isn't one of the above (e.g. `kvp` instead of `kv`), prints a `WARN:` line when the book is built, so a typo never silently does nothing.

  ```json
  "9.2": {
    "protocolName": "CT LWR EXT KNEE WITH CONTRAST",
    "contrastDelay": "90",
    "series": { "2": { "kv": "120", "pitch": "0.984" } },
    "recons": {
      "AXIAL KNEE DET 2.5MM": { "kernel": "Bone", "wwwl": "400/40" },
      "CORONAL KNEE DET 2.5MM": { "wwwl": "2000/500", "sendTo": "AHSPACS" }
    }
  }
  ```
- `addedFields` — lines the scanner has no slot for, shown as "**Title:** value": a field without `series` shows under the protocol's header (after the scanning notes), a field with `"series": "2"` shows under that series. The GUI's *Added fields* tab writes these; a series number that isn't in the protocol prints a `WARN:` line.

  ```json
  "9.2": {
    "addedFields": [
      { "title": "Oral contrast", "value": "900 mL over 1 hour" },
      { "series": "2", "title": "Breath hold", "value": "Inspiration" }
    ]
  }
  ```
- `threeD` — controls the **3D MIP** and **3D VR** series added at the bottom of a protocol's page, each listing a 10° rotation and a separate 10° tumble. They appear automatically for any protocol that sends to AW Server: any auto-send host (including ones typed in `reconSendDestinations`) or `sendDestination` text with "AW" at the start of a word, e.g. `AW`, `AWSERVER`, `AW_SERVER1`, `AW Server`. Set `"threeD": true` to add them to a protocol that doesn't match, or `"threeD": false` to leave them off one that does.
- `protocolName` — filled in by `--init-overrides` with the protocol's name on the scanner, so you can find a protocol in this file by searching for its name (Ctrl+F) instead of knowing its number. It's only a label: it's refreshed on every `--init-overrides` run and never read back, so editing it does nothing. Use `title` to rename a protocol in the book.
- `reconSendDestinations` — corrects the auto-send hosts for individual recons when the export lists fewer than the scanner really sends to (e.g. a stroke CTA that also goes to `RAPID 1`). Key each recon by its name as shown in the book (case and extra spaces don't matter) and list **every** host, comma-separated: the typed list replaces what the export says for that recon. Recons you don't name keep the exported hosts, and the "Auto-sends to:" header line includes the typed hosts. A name that matches no recon in that protocol prints a `WARN:` line when the book is built, so typos don't slip by. `--init-overrides` never removes these entries.
- `contrastVolume` / `contrastRate` — override the IV contrast volume (mL) and rate (mL/s) shown for this protocol's series, in case what the export carries doesn't match actual practice. Either can be set independently; leave the other blank to keep the parsed value for it.
- `referenceSheet` — the reference-workbook sheet to take this protocol's scan range from, when matching by name picks the wrong one or none (exact sheet name, case-insensitive).
- `scanRange` — type the scan range in directly; wins over any reference workbook.

`--init-overrides` scaffolds every protocol number here with all eight fields blank and its `protocolName`, in protocol-number order, so renaming, excluding, or correcting a protocol's contrast values is a matter of finding its number in this one file and editing a value — no new tooling needed. Only used when `--html` is passed; ignored otherwise.

### `kernel-labels.json`, `plane-labels.json`, `category-labels.json` — code → label lookups

Each is a flat `{ "code": "label" }` map:

```json
{ "8": "STD", "4": "DTL", "12": "BN+" }
```

- **`kernel-labels.json`** maps each recon kernel number to its scanner-console name. The repo ships with this site's codes: `4` Head, `8` Detail, `128` Lung, `16384` Bone+. GE stores the kernel as a bit flag (one power of two per kernel), which is why the numbers jump. Run `init-kernel-labels` to add a blank entry for any new code your exports use.
- **`plane-labels.json`** maps scout plane angles to names. It only needs entries for angles you want to *override* — `0`/`90`/`180`/`270` already default to AP/Lateral/PA/Lateral built into the tool; leave a code out (or blank) to keep that default.
- **`category-labels.json`** maps a protocol number's whole-number prefix (as a string, e.g. `"9"`) to the reading category it sorts under in the sidebar. It only needs entries for prefixes you want to *override* — the tool already defaults to the scanner console's own numbering: `1` Head, `2` Face, `3` Neck, `4` Upper Ext., `5` Chest, `6` ABD/PEL, `7` Spine, `8` Pelvis, `9` Lower Ext., and the same nine body parts again at `11`-`19` for their pediatric counterparts (pediatric prefix = adult prefix + 10, e.g. `15` is pediatric Chest). **A prefix with no mapping at all (nothing in this file and no built-in default — in particular `10`/`20`, GE's QA/phantom protocols) is left out of the generated book entirely, not dumped into a catch-all.** If you need a prefix beyond 1-9/11-19 included, add it here with the label you want.
- Any kernel/plane code with a missing or empty label falls back to showing the raw code in the HTML book, so nothing there is ever silently hidden — category prefixes are the one exception, by design (see above).

All three are only used when `--html` is passed.

### `logo.png`, `pdf-library.json`, `reference-library.json`, `manual-protocols.json`, `changelog.json`, protocol images — optional extras for the HTML book

- **`logo.png`** (or `.jpg`/`.gif`/`.svg`, path set via `--logo`) is embedded as a base64 data URI so the generated book stays a single offline-capable file — no logo file means no logo markup is rendered at all.
- **`pdf-library.json`** and **`reference-library.json`** are both hand-maintained lists of externally hosted PDFs (or images — any URL works, it's just a link), since there's no naming convention that could locate arbitrary files on your own server. Same format for both:
  ```json
  [
    { "title": "Knee Replacement Planning Guide", "url": "https://your-server.example.com/pdfs/knee-planning.pdf" }
  ]
  ```
  `pdf-library.json` renders as its own "Surgical Planning" sidebar category; `reference-library.json` renders as a separate "Reference Documents" category for anything that doesn't belong under surgical planning (e.g. pediatric/adult oral contrast administration guides). Each opens its links in a new tab.
- **`manual-protocols.json`** adds protocols that don't exist as a folder on the scanner (e.g. something still being planned). Merged in before every output, so a manual entry shows up in `--json`/`--html`/`--peds-weights` exactly like a scanner-discovered one:
  ```json
  [
    { "protocolNumber": "9.9", "name": "CT LWR EXT CUSTOM", "patientType": "adult", "bodyPart": "lower Extremities", "notes": "Not yet on the scanner." }
  ]
  ```
  If a manual protocol's number collides with a scanner-discovered one, the manual entry wins.
- **`changelog.json`** is a hand-typed log of what changed on a protocol and why — the scanner export can tell you *that* `lastUpdatedDateTime` changed, never *what* or *why*, so this has to be written by a person:
  ```json
  [
    { "date": "2026-08-15", "protocolNumber": "9.2", "note": "Increased mA range for noisy images" },
    { "date": "2026-08-10", "protocolNumber": "5.1", "note": "Renamed to CTA TAVR" },
    { "date": "2026-07-01", "note": "Site-wide contrast injector recalibrated" }
  ]
  ```
  Rendered as the "Recent Changes" sidebar entry/table, sorted most-recent-first (`date` should be `yyyy-MM-dd`; an entry with an unparseable or missing date still shows, just sorted last). `protocolNumber` is optional — omit it for a note that isn't about one specific protocol; when present and still in the book, that row links straight to the protocol's page.
- **Protocol reference images** aren't listed anywhere — point `--protocol-images-base` at wherever you host them (e.g. your own EC2 server) and name each file after its protocol number (`9.2.png`, `8.8.png`, ...). Every protocol page attempts to load its own image and hides it client-side (no broken-image icon) if that particular protocol doesn't have one.

### Populating the label/override files: the `--init-*` workflow

Run once against your real export data to seed each file with every code/protocol number actually in use, with blank values ready to fill in:

```bash
./gradlew run --args="'/path/to/ExportedProtocolsFolder' --init-overrides --init-kernel-labels --init-plane-labels --init-category-labels"
```

This is safe to re-run any time (e.g. after new protocols show up on the scanner) — it only **adds** new codes/protocol numbers and never overwrites or removes anything you've already filled in. For `--init-kernel-labels`, the console also prints a few real recon names that used each still-blank code, so you can recognize it without having to go stand at the scanner console:

```
Kernel labels file kernel-labels.json: added 3 new code(s) - fill in the "" values (e.g. "STD", "DTL", "BN", "BN+") from the scanner console
  code "8" is still blank - recon name(s) using it, to help identify it:
    - AXIAL BONE
    - AXIAL BONE+
```

## Scan ranges from your reference workbooks

The scan range (e.g. "Diaphragm to Ischial Tuberosities") isn't in the scanner export at all, so the book takes it from the site's own reference workbooks: one sheet per protocol, labels in column A, and a `Scan Range/Direction` row with one column per phase under a `Phase` row (the layout of `AMG_CT_Protocols_Adult.xlsm` / `AMG_Protocols_PEDS.xlsx`). Put the workbooks in a `reference workbooks` folder next to the `.bat` files (not tracked by git, like `protocol data`) and every run picks them up; no extra arguments are needed.

Those sheets have no protocol numbers, so each scanner protocol is matched to a sheet by name: abbreviations are expanded (`LWR EXT` = `Lower Ext.`, `CERVICAL SPINE` = `C-spine`, `ABD/PEL` = `Abd-Pel`), contrast wording and "Routine"/"PEDS" are ignored, and the closest sheet wins only if it's a clear winner. Adult protocols only match sheets in the adult workbook, and peds only the PEDS one (a workbook or sheet with "PEDS" in its name). A sheet whose phases all share one range shows it once; otherwise each phase is listed.

Every run prints which sheet each protocol got and which got none:

```
Scan ranges from 79 reference sheet(s): 11 protocol(s) matched, 1 without one
  9.2 CT LWR EXT KNEE WITH CONTRAST  <-  AMG_CT_Protocols_Adult.xlsm > CT Lower Ext. Knee
  ...
No scan range found for (set "referenceSheet" or "scanRange" in protocol-overrides.json):
  8.6 CT BONY PELVIS WITH CONTRAST
```

Fix a wrong or missing match with `referenceSheet` (name the sheet) or `scanRange` (type it) in `protocol-overrides.json`.

## Duplicate protocols

`find-duplicates.bat` writes `duplicates.html`, comparing every protocol's actual settings (series, kV/mA, pitch, rotation, scan delay, contrast, and every reconstruction's name/kernel/thickness/ASIR/window), ignoring its name, number and body part:

- **Identical settings** — the same exam saved twice, typically under two category numbers (e.g. 8.4 and 9.6 "CT ENTIRE LWR EXT"). The report suggests the `"excluded": true` lines to paste into `protocol-overrides.json`, keeping the most recently updated copy; change which one if another copy is filed in the right place.
- **Same name, different settings** — listed with exactly what differs (e.g. `Series 2, group 1: recon 5 kernel: 4 / 8`), since one may be an older copy of the other.

Excluding only hides a protocol from the book and PDF; nothing on the scanner changes. Delete it on the scanner itself if it should go for good.

## Output formats

### Console summary

Always printed (see [Command-line reference](#command-line-reference) above). Useful on its own to sanity-check that all expected protocols were found before generating anything else.

### JSON output

`--json <dir>` writes one JSON file per protocol into `<dir>` (created if it doesn't exist), named from the protocol number and name (e.g. `9.2_CT_LWR_EXT_KNEE_WITH_CONTRAST.json`, with characters outside `[a-zA-Z0-9._-]` replaced with `_`). Each file is a self-contained, normalized snapshot of that protocol's typed fields — metadata, patient setup, contrast, dose, series → groups → reconstructions, notes, and the catch-all `advanced` map — regardless of which input format it came from. This is a cache/snapshot of one parse, not a new source of truth; re-run the parse against fresh export data rather than hand-editing these.

### HTML protocol book

`--html <file>` renders every parsed protocol into a single self-contained, browsable HTML app (no external CSS/JS/font/CDN dependencies — it works fully offline) at `<file>`, styled in Atlantic Health System's colors (orange sidebar, blue main content — a best-effort approximation, not sourced from an official brand guide):

- A **sidebar**, always showing its top-level entries spelled out in full (never shrunk to a single icon letter). Nothing in it reacts to hover — clicking a top-level entry both opens it and widens the rail so submenu labels are readable. It drills down two levels, each starting collapsed until you click it open:
  1. **Adult** / **Peds** — split from each protocol's patient type. Both always appear, even if one of them has zero protocols in this export.
  2. A **reading category** keyed by the protocol number's whole-number prefix (e.g. all "9.x" protocols together) and labeled to match the scanner console's own numbering, not a guess — `1` Head, `2` Face, `3` Neck, `4` Upper Ext., `5` Chest, `6` ABD/PEL, `7` Spine, `8` Pelvis, `9` Lower Ext. by default, with the same nine repeated at `11`-`19` for pediatric (overridable via `category-labels.json`). **A prefix with no category mapping (by default anything outside 1-9/11-19, in particular `10.x`/`20.x` QA/phantom protocols) is left out of the book entirely** — no manual exclusion needed.

  An optional **Recent Changes** entry (top-level, alongside Adult/Peds) links directly to a table built from `changelog.json` — a hand-typed log of what changed and why, most-recent-first, each row linking to that protocol's page when its number still matches one in the book. Not derived from the scanner export; nothing here is automatic. Omitted entirely when the file is missing or empty.

  Two optional entries also sit alongside Adult/Peds, both hand-maintained title+url lists (see above): **Surgical Planning** from `pdf-library.json`, and **Reference Documents** from `reference-library.json` (for links that don't belong under surgical planning, e.g. contrast administration guides).
- The **main panel** shows exactly one protocol at a time as a white reading card on the blue background, selected via the sidebar (a small inline script toggles visibility — no page reload). A welcome view with the logo and the book's title (`--book-title`, defaults to "Protocol Book") is shown until something is picked; the same title also sets the browser tab title.
- Each protocol shows its number, name (or its `title` override, if set — see below), patient type/body part, an optional reference image (from `--protocol-images-base`), exam-level CTDIvol/DLP (min-max mA range), any scanning notes and send-destination from `protocol-overrides.json`, and every series. Scout series get a compact plane/kV/mA table and never show contrast/injection info (scouts are localizers, not diagnostic acquisitions). Other series show the IV contrast volume/rate under the series name (overridable per protocol via `contrastVolume`/`contrastRate` in `protocol-overrides.json`), then "Protocol with contrast · 70 sec contrast delay" (or "Protocol without contrast"; the delay is GE's `groupDelay`), then kV/mA (or the min-max range when SmartmA/auto-mA is active) with noise index — shown only when mA is actually automatic, since a fixed-mA group's noise index field can be a stale leftover value — pitch, rotation time and CTDIvol per acquisition group. Pitch is shown as the real ratio (e.g. `0.992:1`): GE exports it as table travel in detector rows, so it's that value over `macroRowNumber` (127/128 = 0.992, 88/64 = 1.375). CTDIvol (and the exam totals) is shown as a min-max range across the SmartmA range (e.g. `9.67-61.38 mGy`): the console calculates it at the group's `milliAmps` value and dose scales linearly with mA, so it's the exported figure × minMa / milliAmps to × maxMa / milliAmps; fixed-mA groups show the exported figure as a single value. That's followed by a reconstruction table with thickness/interval/kernel/ASIR. ASIR/ASIR-V level is read straight from the scanner's own "AR"+percentage convention (e.g. "AR40" → "40%") — no labels file needed, unlike kernel codes, since it's a fixed GE convention rather than a site-specific code. Derived MPR reformats (coronal/sagittal views reconstructed from an axial series) are shown indented and italicized under their parent reconstruction, inheriting its kernel. Each pediatric weight-band variant of a protocol (GE numbers these with a shared "major.minor" prefix plus a per-weight third segment, e.g. "15.7.1"/"15.7.2"/"15.7.3") still gets its own page and its own sidebar entry. Adult protocols are listed in protocol-number order; Peds protocols are listed alphabetically by name within each category.
- Protocols flagged `"excluded": true` in the overrides file are left out of the book entirely — the same one-line edit as setting a `"title"` override, both in `protocol-overrides.json`; see [Label and override files](#label-and-override-files) above.
- Printing (browser print / print-to-PDF) hides the sidebar and expands the protocol card to the full page width.

Open the resulting file directly in any browser — nothing needs to be served (unless you want to distribute it from a shared location, e.g. an internal web server).

## Helper scripts

Thin wrappers around the Gradle invocations above, for people who'd rather double-click or run a short command than remember `--args` syntax. macOS/Linux (`.sh`) and Windows (`.bat`) versions are provided for each. All of them default to reading from a `protocol data` folder in this repo (intentionally **not** tracked by git — see `.gitignore` — put your real exported protocol folders there), and all accept an explicit path as the first argument instead (or via drag-and-drop onto the `.bat` file in Windows Explorer).

| Script | Equivalent to |
|---|---|
| `run-gui.sh` / `.bat` | `./gradlew gui` — opens the [Protocol Builder window](#the-protocol-builder-window-gui). |
| `run-protocol-book.sh` / `.bat` `[input] [overrides-file]` | `--html book.html --pdf book.pdf --overrides <overrides-file>` — the main "generate the book" command. Second argument optionally names an overrides file other than `protocol-overrides.json`. |
| `init-protocol-overrides.sh` / `.bat` `[input]` | `--init-overrides` |
| `init-kernel-labels.sh` / `.bat` `[input]` | `--init-kernel-labels` |
| `init-plane-labels.sh` / `.bat` `[input]` | `--init-plane-labels` |
| `init-category-labels.sh` / `.bat` `[input]` | `--init-category-labels` |
| `pediatric-weight-sheet.sh` / `.bat` `[input]` | `--peds-weights peds-weights.html` |
| `find-duplicates.sh` / `.bat` `[input]` | `--duplicates duplicates.html` |

Example:

```bash
./run-protocol-book.sh ~/ProtocolData
# then open book.html in a browser
```

```powershell
run-protocol-book.bat "C:\path\to\ProtocolData"
```

## Project layout

```
src/main/java/com/protocolbook/
  Main.java                       CLI entry point: argument parsing, wiring parsers/writers together
  model/                          Typed protocol data model (Protocol, Metadata, PatientSetup, Contrast,
                                   Acquisition, Dose, Series, Group, Reconstruction, Timing)
  parser/
    ProtocolParser.java           Common interface implemented by both parsers below
    GEWorkbookParser.java         Parses a Protocols.xlsm/.xlsx/.xls workbook
    ProtocolFolderWalker.java     Recursively finds GE export folders and de-dupes by protocol number
    UIRxProtocolParser.java       Parses one export folder's protocolmetadata.json/UIRx.xml/session.xml
    ParseSupport.java             Shared tolerant numeric parsing and "advanced" field bookkeeping
  overrides/                      ProtocolOverride model + load/save/mergeTemplate for protocol-overrides.json
  changes/ManualChanges.java      Every hand-set value next to the scanner's (GUI review screen, --changes-pdf)
  gui/                            The Protocol Builder window (Swing): ProtocolBuilderGui (frame, steps, Colors menu),
                                   SetupPanel, ExcludePanel, SectionPanel + SectionRow (at-a-glance line and checks),
                                   ProtocolEditorDialog, FinishPanel, Session (loaded data), Prefs (remembered choices)
  labels/                         CodeLabels (generic code->label file) + LabelConfig (kernel/plane/category)
  manual/ManualProtocols.java     Loads/merges hand-authored protocols not present on the scanner
  io/ProtocolJsonWriter.java      --json output
  html/
    ProtocolBookHtmlWriter.java   --html output (AHS-themed click-only-sidebar single-page app)
    HtmlSupport.java              Shared HTML escaping/CSS used by both HTML writers
    ProtocolBookPdfWriter.java    --pdf output (same content as the HTML book, rendered with openhtmltopdf)
    ChangeReportWriter.java       --changes-pdf output
    ScannerValues.java            The scanner's own value for each overridable setting, as the book shows it
    BookTheme.java                The book's two colors (--primary-color / --accent-color, the GUI's Colors menu)
    PdfLibrary.java                --pdf-library and --reference-library loading (same format, two separate lists)
    Changelog.java                 --changelog loading (hand-typed "Recent Changes" log)
    ProtocolImages.java           --protocol-images-base URL convention
    PediatricWeightSheetWriter.java --peds-weights output
src/test/java/com/protocolbook/   JUnit 5 tests
src/test/resources/sample-protocols/  Checked-in sample GE export fixtures used by the tests
```

The loose `.java` files at the repository root (`Main.java`, `Protocol.java`, `Contrast.java`, etc.) predate this package layout, are **not** part of the Gradle source set (`src/main/java`), and are not compiled or run by anything in this repo — ignore them; the files under `src/main/java/com/protocolbook/` are the ones actually in use.

## Testing

```bash
./gradlew test
```

Runs the full JUnit 5 suite, including parser tests against the checked-in sample export fixtures in `src/test/resources/sample-protocols/`. CI (`.github/workflows/gradle.yml`) runs `./gradlew build` (compile + test) on every push and pull request to `main` against JDK 17, and separately submits a Gradle dependency graph for Dependabot alerts.

## VS Code

Install the **Extension Pack for Java** and **Gradle for Java** extensions, open this `protocol_builder` folder, then run the Gradle `application > run` task from the Gradle side panel (or use `./gradlew run` in the integrated terminal, as above, to pass a workbook/folder path via `--args`). A `.vscode/launch.json` run configuration is included for launching/debugging `Main` directly from the editor.

## Troubleshooting

### `gradlew` fails with a certificate/PKIX error

The wrapper downloads its pinned Gradle distribution over HTTPS on first run. If that fails with `unable to find valid certification path to requested target`, it's a JVM trust-store problem, not a bad download URL. On Ubuntu (including EC2 images), this is usually the `ca-certificates-java` package's `cacerts` file being out of sync with the OS-level CA bundle that tools like `curl` already trust. Fix it with:

```bash
sudo dpkg-reconfigure ca-certificates-java
```

then re-run `./gradlew`.

On **Windows** (typically a hospital/corporate network that inspects HTTPS traffic), Java doesn't trust the network's inspection certificate even though Windows and your browser do. The helper `.bat` scripts already handle this by telling Java to use the Windows certificate store (`-Djavax.net.ssl.trustStoreType=Windows-ROOT`). To make plain `gradlew.bat` (tests, VS Code, etc.) work too, add it once to your personal Gradle settings (local to your PC, not committed), then stop any old daemons:

```bat
(echo systemProp.javax.net.ssl.trustStoreType=Windows-ROOT& echo org.gradle.jvmargs=-Xmx512m -Djavax.net.ssl.trustStoreType=Windows-ROOT)>> "%USERPROFILE%\.gradle\gradle.properties"
gradlew.bat --stop
```

If it still fails, ask IT for the network's root certificate and import it into the JDK's `cacerts` with `keytool -importcert`.

### "Input not found"

The helper scripts default to a `protocol data` folder next to them, which git doesn't track, so it won't exist on a fresh checkout. Create it and copy your exported protocol folders in, or drag-and-drop your export folder onto the `.bat` file.

### "No protocol worksheets were detected" / "No protocol folders found"

- For a workbook: the tool only recognizes sheets whose name or cell contents mention protocol-ish terms (protocol, scan type, kV, mAs, pitch, series, recon, patient position, contrast, CTDI, DLP) and that have at least a handful of populated cells. A workbook that's all cover pages/instructions/lookup tables (or password-protected — see the "workbook is password protected" error) will report zero protocols. Save an unprotected copy if needed, and check that the actual data sheets aren't named/organized in a way that trips the "cover/instructions/contents/index/lookup/config/template" name-based skip.
- For a folder: the tool recurses looking for subfolders that contain **both** `protocolmetadata.json` and `UIRx.xml`. If your export was zipped/renamed/flattened along the way, make sure those two files still sit together in some subfolder under the path you passed.

### "Workbook not found" / unsupported input

Pass the path explicitly, e.g. `./gradlew run --args="/path/to/Protocols.xlsm"` (or `gradlew.bat run --args="C:\\path\\Protocols.xlsm"` on Windows). Only `.xlsm`, `.xlsx`, and `.xls` extensions are accepted for file input; anything else (or a missing file) is rejected before parsing starts.
