package com.protocolbook;

import com.protocolbook.html.BookTheme;
import com.protocolbook.html.ChangeReportWriter;
import com.protocolbook.html.Changelog;
import com.protocolbook.html.PdfLibrary;
import com.protocolbook.html.PediatricWeightSheetWriter;
import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.html.ProtocolBookPdfWriter;
import com.protocolbook.html.DuplicateReportWriter;
import com.protocolbook.duplicates.DuplicateFinder;
import com.protocolbook.html.ProtocolImages;
import com.protocolbook.html.ScanRangePictures;
import com.protocolbook.io.ProtocolJsonWriter;
import com.protocolbook.labels.CodeLabels;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.manual.ManualProtocols;
import com.protocolbook.model.Group;
import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.overrides.ProtocolOverrides;
import com.protocolbook.parser.GEWorkbookParser;
import com.protocolbook.parser.ProtocolFolderWalker;
import com.protocolbook.parser.ProtocolParser;
import com.protocolbook.reference.ReferenceSheets;
import com.protocolbook.reference.ScanRangeMatcher;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Usage: Main <input> [--json <dir>] [--html <file>] [--pdf <file>] [--changes-pdf <file>] [--duplicates <file>] [--book-title <text>] [--changelog <file>]
 *             [--primary-color <#hex>] [--accent-color <#hex>]
 *             [--peds-weights <file>] [--overrides <file>]
 *             [--kernel-labels <file>] [--plane-labels <file>] [--category-labels <file>]
 *             [--logo <file>] [--pdf-library <file>] [--reference-library <file>] [--manual-protocols <file>]
 *             [--protocol-images-base <url>] [--protocol-images-ext <ext, default png>]
 *             [--reference-workbook <file>]... [--reference-folder <dir>]
 *             [--init-overrides] [--init-kernel-labels] [--init-plane-labels] [--init-category-labels]
 * <input> is a Protocols.xlsm workbook or a folder to walk for GE protocol exports.
 * --overrides defaults to ./protocol-overrides.json, --kernel-labels to ./kernel-labels.json,
 * --plane-labels to ./plane-labels.json, --category-labels to ./category-labels.json, --logo to
 * ./logo.png, --pdf-library to ./pdf-library.json, --reference-library to ./reference-library.json,
 * --manual-protocols to ./manual-protocols.json, --changelog to ./changelog.json, all only if
 * present. --logo is embedded (base64) into the generated book; --book-title sets the browser tab
 * title and the welcome-page heading (defaults to "Protocol Book"); --changelog is a hand-typed
 * "what changed and why" log (see Changelog) - rendered as the book's "Recent Changes" sidebar
 * entry/table, most recent first, omitted entirely when the file is missing/empty; --pdf-library
 * and --reference-library are both title+url lists you maintain by hand (same format - see
 * PdfLibrary), rendered as two separate sidebar categories ("Surgical Planning Protocols" and
 * "Reference Documents" respectively) since those files live on their own separate server.
 * --category-labels maps a protocol number's whole-number prefix (1-9) to a
 * reading category, matching the scanner console's own numbering (1 Head, 2 Face, ... 9 Lower
 * Ext. by default - see LabelConfig) - a prefix with no mapping (e.g. 10, QA/phantom protocols)
 * is left out of the generated book entirely. --manual-protocols adds protocols that don't exist
 * as a folder on the scanner (see ManualProtocols) - merged in before every output, so they flow
 * through --json/--html/--peds-weights identically to scanner-discovered ones.
 * --protocol-images-base points at wherever per-protocol reference images are hosted, named
 * "<protocolNumber>.<ext>" - no list to maintain, see ProtocolImages.
 * --reference-workbook (repeatable) adds one of the site's own one-sheet-per-protocol reference
 * workbooks to take scan ranges from (see ReferenceSheets/ScanRangeMatcher); every workbook in
 * --reference-folder (default ./reference workbooks, only if present) is used too.
 * --pdf writes the same book as a printable PDF (cover, contents with page numbers, one protocol
 * per page). --duplicates writes a list of protocols with identical settings, or the same name
 * (see DuplicateFinder), with the protocol-overrides.json lines that would hide the extra copies.
 * --changes-pdf writes a list of every value set by hand in the overrides file next to the scanner's
 * own (see ChangeReportWriter). --primary-color/--accent-color recolor the book (see BookTheme).
 * Running with --gui (or the "gui" Gradle task) opens the step-by-step window instead (see ProtocolBuilderGui).
 * --peds-weights writes a printable sheet of protocols whose patientType contains "pediatric",
 * with any weight-in-kg found in the protocol name annotated with its pound equivalent.
 */
public class Main {
    public static void main(String[] args) {
        // Apache POI logs through Log4j, which with no backend falls back to printing ERROR-level
        // stack traces to the console - e.g. one per broken hyperlink in a reference workbook. Those
        // are recovered from and say nothing actionable, so keep them off the console.
        System.setProperty("org.apache.logging.log4j.simplelog.level", "OFF");
        System.setProperty("log4j2.statusLoggerLevel", "OFF");
        System.setProperty("org.apache.logging.log4j.simplelog.StatusLogger.level", "OFF");
        if (args.length > 0 && "--gui".equals(args[0])) {
            com.protocolbook.gui.ProtocolBuilderGui.main(new String[0]);
            return;
        }
        try {
            File input = null;
            File jsonDir = null, htmlFile = null, pdfFile = null, changesPdfFile = null, duplicatesFile = null, pedsWeightFile = null;
            BookTheme theme = BookTheme.DEFAULT;
            String bookTitle = null;
            File changelogFile = new File("changelog.json");
            File overridesFile = new File("protocol-overrides.json");
            File kernelLabelsFile = new File("kernel-labels.json");
            File planeLabelsFile = new File("plane-labels.json");
            File categoryLabelsFile = new File("category-labels.json");
            File logoFile = new File("logo.png");
            File pdfLibraryFile = new File("pdf-library.json");
            File referenceLibraryFile = new File("reference-library.json");
            File manualProtocolsFile = new File("manual-protocols.json");
            String protocolImagesBase = null, protocolImagesExt = "png";
            List<File> referenceWorkbooks = new ArrayList<File>();
            File referenceFolder = new File("reference workbooks");
            boolean initOverrides = false, initKernelLabels = false, initPlaneLabels = false, initCategoryLabels = false;
            for (int i = 0; i < args.length; i++) {
                if ("--json".equals(args[i])) jsonDir = new File(args[++i]);
                else if ("--html".equals(args[i])) htmlFile = new File(args[++i]);
                else if ("--pdf".equals(args[i])) pdfFile = new File(args[++i]);
                else if ("--changes-pdf".equals(args[i])) changesPdfFile = new File(args[++i]);
                else if ("--primary-color".equals(args[i])) theme = theme.withPrimary(args[++i]);
                else if ("--accent-color".equals(args[i])) theme = theme.withAccent(args[++i]);
                else if ("--duplicates".equals(args[i])) duplicatesFile = new File(args[++i]);
                else if ("--book-title".equals(args[i])) bookTitle = args[++i];
                else if ("--changelog".equals(args[i])) changelogFile = new File(args[++i]);
                else if ("--peds-weights".equals(args[i])) pedsWeightFile = new File(args[++i]);
                else if ("--overrides".equals(args[i])) overridesFile = new File(args[++i]);
                else if ("--kernel-labels".equals(args[i])) kernelLabelsFile = new File(args[++i]);
                else if ("--plane-labels".equals(args[i])) planeLabelsFile = new File(args[++i]);
                else if ("--category-labels".equals(args[i])) categoryLabelsFile = new File(args[++i]);
                else if ("--logo".equals(args[i])) logoFile = new File(args[++i]);
                else if ("--pdf-library".equals(args[i])) pdfLibraryFile = new File(args[++i]);
                else if ("--reference-library".equals(args[i])) referenceLibraryFile = new File(args[++i]);
                else if ("--manual-protocols".equals(args[i])) manualProtocolsFile = new File(args[++i]);
                else if ("--reference-workbook".equals(args[i])) referenceWorkbooks.add(new File(args[++i]));
                else if ("--reference-folder".equals(args[i])) referenceFolder = new File(args[++i]);
                else if ("--protocol-images-base".equals(args[i])) protocolImagesBase = args[++i];
                else if ("--protocol-images-ext".equals(args[i])) protocolImagesExt = args[++i];
                else if ("--init-overrides".equals(args[i])) initOverrides = true;
                else if ("--init-kernel-labels".equals(args[i])) initKernelLabels = true;
                else if ("--init-plane-labels".equals(args[i])) initPlaneLabels = true;
                else if ("--init-category-labels".equals(args[i])) initCategoryLabels = true;
                else if (input == null) input = new File(args[i]);
            }
            if (input == null) input = new File("Protocols.xlsm");
            if (!input.exists()) {
                throw new IllegalArgumentException("Input not found: " + input.getAbsolutePath()
                        + ". Point it at your exported protocol folder (or a Protocols.xlsm workbook), e.g. "
                        + "drag-and-drop the folder onto run-protocol-book.bat, or create a \"protocol data\" folder "
                        + "next to the .bat files and copy the exported protocol folders into it.");
            }

            List<Protocol> protocols = loadProtocols(input, manualProtocolsFile, referenceWorkbooks, referenceFolder, overridesFile, System.out);
            for (Protocol p : protocols) {
                String name = p.getMetadata() == null ? "(unnamed)" : p.getMetadata().getName();
                System.out.printf("- %s: %d series, %d reconstructions, %d notes, %d advanced fields%n",
                        name, p.getSeries().size(), reconstructionCount(p), p.getNotes().size(), p.getAdvanced().size());
            }

            if (initOverrides) {
                Map<String, String> names = new LinkedHashMap<String, String>();
                for (Protocol p : protocols) if (p.getMetadata() != null) names.put(p.getMetadata().getProtocolNumber(), p.getMetadata().getName());
                int added = ProtocolOverrides.mergeTemplate(names, overridesFile);
                System.out.println("Overrides file " + overridesFile.getAbsolutePath() + ": added " + added
                        + " new protocol(s), protocol names refreshed, sorted by number; existing settings left untouched");
            }
            if (initKernelLabels) {
                int added = CodeLabels.mergeTemplate(new ArrayList<String>(collectReconCodes(protocols, true)), kernelLabelsFile);
                System.out.println("Kernel labels file " + kernelLabelsFile.getAbsolutePath() + ": added " + added
                        + " new code(s) - fill in the \"\" values (e.g. \"STD\", \"DTL\", \"BN\", \"BN+\") from the scanner console");
                printBlankCodeSamples(kernelLabelsFile, sampleReconNamesByKernelCode(protocols),
                        "recon name(s) using it, to help identify it");
            }
            if (initPlaneLabels) {
                int added = CodeLabels.mergeTemplate(new ArrayList<String>(collectReconCodes(protocols, false)), planeLabelsFile);
                System.out.println("Plane labels file " + planeLabelsFile.getAbsolutePath() + ": added " + added
                        + " new code(s) (0/90/180/270 already default to AP/Lateral/PA/Lateral unless overridden here)");
            }
            if (initCategoryLabels) {
                TreeSet<Integer> prefixes = new TreeSet<Integer>();
                for (Protocol p : protocols) {
                    Metadata m = p.getMetadata();
                    if (m == null || m.getProtocolNumber() == null) continue;
                    try { prefixes.add(Integer.parseInt(m.getProtocolNumber().split("\\.")[0])); } catch (Exception ignored) {}
                }
                List<String> prefixStrings = new ArrayList<String>();
                for (Integer prefix : prefixes) prefixStrings.add(String.valueOf(prefix));
                int added = CodeLabels.mergeTemplate(prefixStrings, categoryLabelsFile);
                System.out.println("Category labels file " + categoryLabelsFile.getAbsolutePath() + ": added " + added
                        + " new protocol-number prefix(es) - 1-9 already default to Head/Face/Neck/Upper Ext./Chest/ABD-PEL/Spine/Pelvis/Lower Ext. "
                        + "unless overridden here; any prefix left blank/unmapped (e.g. 10) is left out of the generated book entirely");
            }
            if (pedsWeightFile != null) {
                new PediatricWeightSheetWriter().write(protocols, pedsWeightFile);
                System.out.println("Wrote pediatric weight reference to " + pedsWeightFile.getAbsolutePath());
            }
            if (jsonDir != null) {
                new ProtocolJsonWriter().writeAll(protocols, jsonDir);
                System.out.println("Wrote combined JSON to " + jsonDir.getAbsolutePath());
            }
            File pictureFolder = new File(overridesFile.getAbsoluteFile().getParentFile(), ScanRangePictures.FOLDER);
            if (htmlFile != null || pdfFile != null) {
                for (String line : ProtocolOverrides.problems(protocols, ProtocolOverrides.load(overridesFile))) System.err.println("WARN: " + line);
                for (String line : ScanRangePictures.problems(protocols, ProtocolOverrides.load(overridesFile), pictureFolder)) System.err.println("WARN: " + line);
            }
            if (htmlFile != null) {
                Map<String, ProtocolOverride> overrides = ProtocolOverrides.load(overridesFile);
                LabelConfig labels = LabelConfig.load(kernelLabelsFile, planeLabelsFile, categoryLabelsFile);
                String logoDataUri = loadLogoDataUri(logoFile);
                List<PdfLibrary.Entry> pdfLibrary = PdfLibrary.load(pdfLibraryFile);
                List<PdfLibrary.Entry> referenceLibrary = PdfLibrary.load(referenceLibraryFile);
                ProtocolImages protocolImages = protocolImagesBase == null ? null : new ProtocolImages(protocolImagesBase, protocolImagesExt);
                List<Changelog.Entry> changelog = Changelog.load(changelogFile);
                new ProtocolBookHtmlWriter().withTheme(theme).withPictureFolder(pictureFolder).write(protocols, overrides, labels, logoDataUri, pdfLibrary, referenceLibrary, protocolImages, bookTitle, changelog, htmlFile);
                System.out.println("Wrote protocol book to " + htmlFile.getAbsolutePath()
                        + (overrides.isEmpty() ? "" : " (" + overrides.size() + " override(s) applied from " + overridesFile + ")")
                        + (logoDataUri != null ? " (logo embedded from " + logoFile + ")" : "")
                        + (pdfLibrary.isEmpty() ? "" : " (" + pdfLibrary.size() + " PDF link(s) from " + pdfLibraryFile + ")")
                        + (referenceLibrary.isEmpty() ? "" : " (" + referenceLibrary.size() + " reference doc link(s) from " + referenceLibraryFile + ")")
                        + (protocolImages != null ? " (protocol images from " + protocolImagesBase + "/<number>." + protocolImagesExt + ")" : "")
                        + (changelog.isEmpty() ? "" : " (" + changelog.size() + " changelog entr" + (changelog.size() == 1 ? "y" : "ies") + " from " + changelogFile + ")"));
            }
            if (pdfFile != null) {
                Map<String, ProtocolOverride> overrides = ProtocolOverrides.load(overridesFile);
                LabelConfig labels = LabelConfig.load(kernelLabelsFile, planeLabelsFile, categoryLabelsFile);
                new ProtocolBookPdfWriter().withTheme(theme).withPictureFolder(pictureFolder).write(protocols, overrides, labels, loadLogoDataUri(logoFile), bookTitle, pdfFile);
                System.out.println("Wrote printable protocol book to " + pdfFile.getAbsolutePath());
            }
            if (changesPdfFile != null) {
                LabelConfig labels = LabelConfig.load(kernelLabelsFile, planeLabelsFile, categoryLabelsFile);
                new ChangeReportWriter().withTheme(theme).write(protocols, ProtocolOverrides.load(overridesFile), labels, bookTitle, changesPdfFile);
                System.out.println("Wrote list of manual changes to " + changesPdfFile.getAbsolutePath());
            }
            if (duplicatesFile != null) {
                DuplicateFinder.Result duplicates = DuplicateFinder.find(protocols);
                new DuplicateReportWriter().write(duplicates, ProtocolOverrides.load(overridesFile), duplicatesFile);
                System.out.println("Found " + duplicates.identical.size() + " group(s) of protocols with identical settings and "
                        + duplicates.sameNameDifferent.size() + " group(s) sharing a name - see " + duplicatesFile.getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Parses the input (a folder of GE exports or a workbook), adds manual-protocols.json, and fills in
     * scan ranges from the reference workbooks (those listed plus every one in referenceFolder). Shared
     * by the command line and the GUI; progress lines go to log.
     */
    public static List<Protocol> loadProtocols(File input, File manualProtocolsFile, List<File> referenceWorkbooks, File referenceFolder,
                                               File overridesFile, PrintStream log) throws Exception {
        ProtocolParser parser = input.isDirectory() ? new ProtocolFolderWalker() : new GEWorkbookParser();
        List<Protocol> protocols = parser.parse(input);
        log.println("Parsed " + protocols.size() + " protocol(s) from " + input.getAbsolutePath());

        List<Protocol> manualProtocols = ManualProtocols.load(manualProtocolsFile);
        if (!manualProtocols.isEmpty()) {
            protocols = ManualProtocols.merge(protocols, manualProtocols);
            log.println("Added " + manualProtocols.size() + " manual protocol(s) from " + manualProtocolsFile.getAbsolutePath());
        }
        List<File> workbooks = new ArrayList<File>(referenceWorkbooks);
        workbooks.addAll(ReferenceSheets.workbooksIn(referenceFolder));
        if (!workbooks.isEmpty()) {
            for (File f : workbooks) log.println("Reading scan ranges from " + f.getAbsolutePath());
            ScanRangeMatcher matcher = new ScanRangeMatcher(ReferenceSheets.load(workbooks));
            for (String line : matcher.apply(protocols, ProtocolOverrides.load(overridesFile))) log.println(line);
        }
        return protocols;
    }

    private static int reconstructionCount(Protocol p) {
        int count = 0;
        for (Series s : p.getSeries()) for (Group g : s.getGroups()) count += g.getReconstructions().size();
        return count;
    }

    /** Embeds an image as a base64 data URI so the generated book stays a single file. Null if the file isn't there. */
    public static String loadLogoDataUri(File logoFile) throws IOException {
        if (!logoFile.isFile()) return null;
        String name = logoFile.getName().toLowerCase(Locale.ROOT);
        String mimeType = name.endsWith(".svg") ? "image/svg+xml"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                : name.endsWith(".gif") ? "image/gif"
                : "image/png";
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(logoFile.toPath()));
        return "data:" + mimeType + ";base64," + base64;
    }

    /** Every kernel (kernels=true) or scout-plane code used by these protocols' recons, sorted. */
    public static TreeSet<String> collectReconCodes(List<Protocol> protocols, boolean kernels) {
        TreeSet<String> codes = new TreeSet<String>();
        for (Protocol p : protocols) for (Series s : p.getSeries()) for (Group g : s.getGroups())
            for (Reconstruction r : g.getReconstructions()) {
                String code = kernels ? r.getKernel() : r.getPlane();
                if (code != null && !code.isEmpty()) codes.add(code);
            }
        return codes;
    }

    private static final int MAX_SAMPLES_PER_CODE = 5;

    /** Up to five recon names per kernel code, so a code can be recognized without going to the scanner. */
    public static Map<String, List<String>> sampleReconNamesByKernelCode(List<Protocol> protocols) {
        Map<String, List<String>> samples = new LinkedHashMap<String, List<String>>();
        for (Protocol p : protocols) for (Series s : p.getSeries()) for (Group g : s.getGroups())
            for (Reconstruction r : g.getReconstructions()) {
                String code = r.getKernel();
                if (code == null || code.isEmpty() || r.getName() == null) continue;
                List<String> names = samples.computeIfAbsent(code, k -> new ArrayList<String>());
                if (!names.contains(r.getName()) && names.size() < MAX_SAMPLES_PER_CODE) names.add(r.getName());
            }
        return samples;
    }

    // The whole reason kernel codes need a hand-maintained labels file is that the raw export
    // gives no clue what a code means - so when --init-kernel-labels finds a code with no label
    // yet, print a few real recon names that used it, letting the reader recognize it (e.g.
    // "BONE+" in the name) instead of having to go stand at the scanner console to look it up.
    private static void printBlankCodeSamples(File labelsFile, Map<String, List<String>> samplesByCode, String hint) throws java.io.IOException {
        Map<String, String> labels = CodeLabels.load(labelsFile);
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) continue;
            List<String> samples = samplesByCode.get(e.getKey());
            if (samples == null || samples.isEmpty()) continue;
            System.out.println("  code \"" + e.getKey() + "\" is still blank - " + hint + ":");
            for (String sample : samples) System.out.println("    - " + sample);
        }
    }
}
