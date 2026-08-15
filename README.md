# Protocol Builder

A Java command-line tool that turns CT scanning protocols exported from a GE scanner into a single browsable "protocol book" (HTML), plus normalized JSON. It reads either of two GE export formats, fills a typed model (kV, mA, pitch, kernel, dose, contrast, reconstructions, etc.), and keeps anything it doesn't recognize instead of dropping it.

- **Recognized fields** (protocol name, body part, kV, mA, pitch, detector, dose, series, reconstructions, contrast, ...) populate a typed model.
- **Everything else** — free-text notes/comments and any workbook or XML field the parser doesn't have a slot for — is retained verbatim in `Protocol.notes` and `Protocol.advanced` rather than silently discarded.

## What it reads

The same `Main` command accepts either of two input types, auto-detected from whether the path is a file or a directory:

1. **A single `Protocols.xlsm` (or `.xlsx`/`.xls`) workbook** — parsed by `GEWorkbookParser`. Each worksheet that looks like a protocol (heuristically: sheet name and cell contents mention things like "protocol", "kV", "series", "recon", "patient position", "contrast", "CTDI", etc.) becomes one `Protocol`. Sheets named things like "cover", "instructions", "contents", "index", "lookup", "config", or "template", and hidden sheets, are skipped.
2. **A folder of GE Revolution protocol exports** — parsed by `ProtocolFolderWalker` / `UIRxProtocolParser`. The folder is searched recursively (any nesting is fine) for subfolders that each contain `protocolmetadata.json` and `UIRx.xml`; `session.xml`, if present alongside them, supplies human-readable series/recon names and any derived MPR reformats (coronal/sagittal views). If the same protocol number (`slotNumber` in `protocolmetadata.json`) appears in more than one folder — e.g. a protocol was re-saved and both the old and new export are still on disk — only the copy with the most recent `lastUpdatedDateTime` is kept; the rest are logged as `WARN:` and dropped.

You don't need to tell the tool which format you're using — point it at a file for the workbook path, or a directory for the folder-walk path.

## Requirements

- Java 8 or newer.
- The Gradle **wrapper** (`gradlew` / `gradlew.bat`) — always use it, never a system-installed `gradle` binary. The wrapper pins the exact Gradle version (8.x) this build is written for; a distro-packaged `gradle` is often years out of date and will fail with confusing errors on this `build.gradle`.

No manual dependency installation is needed — Gradle resolves Apache POI (`poi-ooxml`), `org.json`, and JUnit 5 from Maven Central on first run.

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

## Command-line reference

```
Main <input> [--json <dir>] [--html <file>] [--overrides <file>]
             [--kernel-labels <file>] [--plane-labels <file>] [--detector-labels <file>]
             [--init-overrides] [--init-kernel-labels] [--init-plane-labels] [--init-detector-labels]
```

Because this is a Gradle `application` project, every invocation goes through `./gradlew run --args="..."` — put the whole argument string in one quoted `--args` value, exactly as shown below.

| Argument | Meaning |
|---|---|
| `<input>` (positional, required unless `Protocols.xlsm` exists locally) | Path to a `.xlsm`/`.xlsx`/`.xls` workbook, **or** a folder to recursively walk for GE protocol export subfolders. Defaults to `Protocols.xlsm` in the current directory if omitted. |
| `--json <dir>` | Write one normalized JSON file per protocol into `<dir>` (created if needed). See [JSON output](#json-output) below. |
| `--html <file>` | Render every parsed protocol as a single self-contained, browsable HTML file at `<file>`. See [HTML protocol book](#html-protocol-book) below. |
| `--overrides <file>` | Path to the hand-maintained overrides JSON (notes, exclusions, send destinations). Defaults to `./protocol-overrides.json`; used only if the file exists. Only takes effect together with `--html`. |
| `--kernel-labels <file>` | Path to the recon-kernel-code → label lookup. Defaults to `./kernel-labels.json`; used only if present. Only takes effect together with `--html`. |
| `--plane-labels <file>` | Path to the scout-plane-angle → label lookup. Defaults to `./plane-labels.json`; used only if present. Only takes effect together with `--html`. |
| `--detector-labels <file>` | Path to the detector-row-code → label lookup. Defaults to `./detector-labels.json`; used only if present. Only takes effect together with `--html`. |
| `--init-overrides` | Add an empty entry to the overrides file for every protocol number found that isn't already listed. Never touches existing entries. |
| `--init-kernel-labels` | Add an empty entry to the kernel-labels file for every recon kernel code found that isn't already listed. |
| `--init-plane-labels` | Add an empty entry to the plane-labels file for every scout plane code found that isn't already listed. |
| `--init-detector-labels` | Add an empty entry to the detector-labels file for every detector code found that isn't already listed. |

All four `--init-*` flags, and `--json`/`--html`, can be combined in a single run alongside one another. Every run always prints the console summary described below regardless of which other flags are passed.

Every run first prints a one-line-per-protocol console summary:

```
Parsed 42 protocol(s) from /path/to/input
- CT LWR EXT KNEE WITH CONTRAST: 6 series, 14 reconstructions, 1 notes, 3 advanced fields
- ...
```

## Label and override files

GE's raw export only ever gives you numeric/coded values for a few fields that are genuinely site-specific and can't be derived from the export itself — you have to look them up once at the scanner console (or its documentation) and record them here. These files are plain, hand-editable JSON that you keep alongside the tool (not regenerated data — re-parsing never touches values you've already filled in).

### `protocol-overrides.json` — per-protocol notes, exclusion, send destination

Keyed by protocol number (the same `slotNumber`/protocol number shown in the console summary and HTML book):

```json
{
  "9.2":  { "notes": "Have the patient bend the knee slightly for...", "excluded": false, "sendDestination": "" },
  "9.4":  { "excluded": true },
  "5.1":  { "sendDestination": "AHSPACS + 3D Lab" }
}
```

- `notes` — free-text scanning notes shown inline in the HTML protocol book.
- `excluded` — when `true`, the protocol is left out of the generated HTML book entirely (still counted in the console summary and JSON output).
- `sendDestination` — where images from this protocol are routed; not reliably derivable from the export (session.xml logs what actually ran for one historical scan, not what the protocol template always does), so it's stated here by hand.

Only used when `--html` is passed; ignored otherwise.

### `kernel-labels.json`, `plane-labels.json`, `detector-labels.json` — code → label lookups

Each is a flat `{ "code": "label" }` map:

```json
{ "8": "STD", "4": "DTL", "12": "BN+" }
```

- **`kernel-labels.json`** maps each recon kernel number to its scanner-console name (e.g. `"STD"`, `"DTL"`, `"BN"`, `"BN+"`). There is no default — every code starts blank until you fill it in.
- **`plane-labels.json`** maps scout plane angles to names. It only needs entries for angles you want to *override* — `0`/`90`/`180`/`270` already default to AP/Lateral/PA/Lateral built into the tool; leave a code out (or blank) to keep that default.
- **`detector-labels.json`** maps the detector row count (GE's `macroRowNumber`) to a description (e.g. `"64 slice"`, `"128 slice/80mm"`).
- Any code with a missing or empty label falls back to showing the raw code in the HTML book, so nothing is ever silently hidden.

All three are only used when `--html` is passed.

### Populating these files: the `--init-*` workflow

Run once against your real export data to seed each file with every code/protocol number actually in use, with blank values ready to fill in:

```bash
./gradlew run --args="'/path/to/ExportedProtocolsFolder' --init-overrides --init-kernel-labels --init-plane-labels --init-detector-labels"
```

This is safe to re-run any time (e.g. after new protocols show up on the scanner) — it only **adds** new codes/protocol numbers and never overwrites or removes anything you've already filled in. For `--init-kernel-labels` and `--init-detector-labels`, the console also prints a few real protocol/recon names that used each still-blank code, so you can recognize it without having to go stand at the scanner console:

```
Kernel labels file kernel-labels.json: added 3 new code(s) - fill in the "" values (e.g. "STD", "DTL", "BN", "BN+") from the scanner console
  code "8" is still blank - recon name(s) using it, to help identify it:
    - AXIAL BONE
    - AXIAL BONE+
```

## Output formats

### Console summary

Always printed (see [Command-line reference](#command-line-reference) above). Useful on its own to sanity-check that all expected protocols were found before generating anything else.

### JSON output

`--json <dir>` writes one JSON file per protocol into `<dir>` (created if it doesn't exist), named from the protocol number and name (e.g. `9.2_CT_LWR_EXT_KNEE_WITH_CONTRAST.json`, with characters outside `[a-zA-Z0-9._-]` replaced with `_`). Each file is a self-contained, normalized snapshot of that protocol's typed fields — metadata, patient setup, contrast, dose, series → groups → reconstructions, notes, and the catch-all `advanced` map — regardless of which input format it came from. This is a cache/snapshot of one parse, not a new source of truth; re-run the parse against fresh export data rather than hand-editing these.

### HTML protocol book

`--html <file>` renders every parsed protocol into a single self-contained, browsable HTML page (dark/light mode aware, no external assets) at `<file>`:

- Protocols are grouped into collapsible sections by the whole-number part of their protocol number (e.g. all "9.x" protocols together), each section labeled by whichever body part is most common among its protocols (a few groups — facial/orbit protocols, QA/phantom protocols — have hand-coded label overrides baked into the writer since majority-vote mislabels them). Groups made up mostly of pediatric protocols get a "Peds" prefix; the QA/phantom group always sorts last.
- Each protocol shows its number, name, patient type/body part, exam-level CTDIvol/DLP dose, any scanning notes and send-destination from `protocol-overrides.json`, and every series (scout series get a compact plane/kV/mA table; other series show kV/mA/pitch/rotation/detector/dose per acquisition group plus a reconstruction table with thickness/interval/kernel). Derived MPR reformats (coronal/sagittal views reconstructed from an axial series) are shown indented and italicized under their parent reconstruction.
- Protocols flagged `"excluded": true` in the overrides file are left out of the book entirely.

Open the resulting file directly in any browser — nothing needs to be served.

## Helper scripts

Thin wrappers around the Gradle invocations above, for people who'd rather double-click or run a short command than remember `--args` syntax. macOS/Linux (`.sh`) and Windows (`.bat`) versions are provided for each. All of them default to reading from a `protocol data` folder in this repo (intentionally **not** tracked by git — see `.gitignore` — put your real exported protocol folders there), and all accept an explicit path as the first argument instead (or via drag-and-drop onto the `.bat` file in Windows Explorer).

| Script | Equivalent to |
|---|---|
| `run-protocol-book.sh` / `.bat` `[input] [overrides-file]` | `--html book.html --overrides <overrides-file>` — the main "generate the book" command. Second argument optionally names an overrides file other than `protocol-overrides.json`. |
| `init-protocol-overrides.sh` / `.bat` `[input]` | `--init-overrides` |
| `init-kernel-labels.sh` / `.bat` `[input]` | `--init-kernel-labels` |
| `init-plane-labels.sh` / `.bat` `[input]` | `--init-plane-labels` |
| `init-detector-labels.sh` / `.bat` `[input]` | `--init-detector-labels` |

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
  overrides/                      ProtocolOverride model + load/mergeTemplate for protocol-overrides.json
  labels/                         CodeLabels (generic code->label file) + LabelConfig (kernel/plane/detector)
  io/ProtocolJsonWriter.java      --json output
  html/ProtocolBookHtmlWriter.java --html output
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

### "No protocol worksheets were detected" / "No protocol folders found"

- For a workbook: the tool only recognizes sheets whose name or cell contents mention protocol-ish terms (protocol, scan type, kV, mAs, pitch, series, recon, patient position, contrast, CTDI, DLP) and that have at least a handful of populated cells. A workbook that's all cover pages/instructions/lookup tables (or password-protected — see the "workbook is password protected" error) will report zero protocols. Save an unprotected copy if needed, and check that the actual data sheets aren't named/organized in a way that trips the "cover/instructions/contents/index/lookup/config/template" name-based skip.
- For a folder: the tool recurses looking for subfolders that contain **both** `protocolmetadata.json` and `UIRx.xml`. If your export was zipped/renamed/flattened along the way, make sure those two files still sit together in some subfolder under the path you passed.

### "Workbook not found" / unsupported input

Pass the path explicitly, e.g. `./gradlew run --args="/path/to/Protocols.xlsm"` (or `gradlew.bat run --args="C:\\path\\Protocols.xlsm"` on Windows). Only `.xlsm`, `.xlsx`, and `.xls` extensions are accepted for file input; anything else (or a missing file) is rejected before parsing starts.
