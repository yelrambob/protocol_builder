package com.protocolbook;

import com.protocolbook.html.PdfLibrary;
import com.protocolbook.html.PediatricWeightSheetWriter;
import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.html.ProtocolImages;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Usage: Main <input> [--json <dir>] [--html <file>] [--peds-weights <file>] [--overrides <file>]
 *             [--kernel-labels <file>] [--plane-labels <file>] [--category-labels <file>] [--detector-labels <file>]
 *             [--logo <file>] [--pdf-library <file>] [--manual-protocols <file>]
 *             [--protocol-images-base <url>] [--protocol-images-ext <ext, default png>]
 *             [--init-overrides] [--init-kernel-labels] [--init-plane-labels] [--init-category-labels] [--init-detector-labels]
 * <input> is a Protocols.xlsm workbook or a folder to walk for GE protocol exports.
 * --overrides defaults to ./protocol-overrides.json, --kernel-labels to ./kernel-labels.json,
 * --plane-labels to ./plane-labels.json, --category-labels to ./category-labels.json,
 * --detector-labels to ./detector-labels.json, --logo to ./logo.png, --pdf-library to
 * ./pdf-library.json, --manual-protocols to ./manual-protocols.json, all only if present. --logo
 * is embedded (base64) into the generated book; --pdf-library entries are linked (title+url pairs
 * you maintain by hand - see PdfLibrary) since those files live on their own separate server.
 * --manual-protocols adds protocols that don't exist as a folder on the scanner (see
 * ManualProtocols) - merged in before every output, so they flow through --json/--html/
 * --peds-weights identically to scanner-discovered ones. --protocol-images-base points at
 * wherever per-protocol reference images are hosted, named "<protocolNumber>.<ext>" - no list to
 * maintain, see ProtocolImages.
 * --peds-weights writes a printable sheet of protocols whose patientType contains "pediatric",
 * with any weight-in-kg found in the protocol name annotated with its pound equivalent.
 */
public class Main {
    public static void main(String[] args) {
        try {
            File input = null;
            File jsonDir = null, htmlFile = null, pedsWeightFile = null;
            File overridesFile = new File("protocol-overrides.json");
            File kernelLabelsFile = new File("kernel-labels.json");
            File planeLabelsFile = new File("plane-labels.json");
            File categoryLabelsFile = new File("category-labels.json");
            File detectorLabelsFile = new File("detector-labels.json");
            File logoFile = new File("logo.png");
            File pdfLibraryFile = new File("pdf-library.json");
            File manualProtocolsFile = new File("manual-protocols.json");
            String protocolImagesBase = null, protocolImagesExt = "png";
            boolean initOverrides = false, initKernelLabels = false, initPlaneLabels = false,
                    initCategoryLabels = false, initDetectorLabels = false;
            for (int i = 0; i < args.length; i++) {
                if ("--json".equals(args[i])) jsonDir = new File(args[++i]);
                else if ("--html".equals(args[i])) htmlFile = new File(args[++i]);
                else if ("--peds-weights".equals(args[i])) pedsWeightFile = new File(args[++i]);
                else if ("--overrides".equals(args[i])) overridesFile = new File(args[++i]);
                else if ("--kernel-labels".equals(args[i])) kernelLabelsFile = new File(args[++i]);
                else if ("--plane-labels".equals(args[i])) planeLabelsFile = new File(args[++i]);
                else if ("--category-labels".equals(args[i])) categoryLabelsFile = new File(args[++i]);
                else if ("--detector-labels".equals(args[i])) detectorLabelsFile = new File(args[++i]);
                else if ("--logo".equals(args[i])) logoFile = new File(args[++i]);
                else if ("--pdf-library".equals(args[i])) pdfLibraryFile = new File(args[++i]);
                else if ("--manual-protocols".equals(args[i])) manualProtocolsFile = new File(args[++i]);
                else if ("--protocol-images-base".equals(args[i])) protocolImagesBase = args[++i];
                else if ("--protocol-images-ext".equals(args[i])) protocolImagesExt = args[++i];
                else if ("--init-overrides".equals(args[i])) initOverrides = true;
                else if ("--init-kernel-labels".equals(args[i])) initKernelLabels = true;
                else if ("--init-plane-labels".equals(args[i])) initPlaneLabels = true;
                else if ("--init-category-labels".equals(args[i])) initCategoryLabels = true;
                else if ("--init-detector-labels".equals(args[i])) initDetectorLabels = true;
                else if (input == null) input = new File(args[i]);
            }
            if (input == null) input = new File("Protocols.xlsm");

            ProtocolParser parser = input.isDirectory() ? new ProtocolFolderWalker() : new GEWorkbookParser();
            List<Protocol> protocols = parser.parse(input);
            System.out.println("Parsed " + protocols.size() + " protocol(s) from " + input.getAbsolutePath());

            List<Protocol> manualProtocols = ManualProtocols.load(manualProtocolsFile);
            if (!manualProtocols.isEmpty()) {
                protocols = ManualProtocols.merge(protocols, manualProtocols);
                System.out.println("Added " + manualProtocols.size() + " manual protocol(s) from " + manualProtocolsFile.getAbsolutePath());
            }
            for (Protocol p : protocols) {
                String name = p.getMetadata() == null ? "(unnamed)" : p.getMetadata().getName();
                System.out.printf("- %s: %d series, %d reconstructions, %d notes, %d advanced fields%n",
                        name, p.getSeries().size(), reconstructionCount(p), p.getNotes().size(), p.getAdvanced().size());
            }

            if (initOverrides) {
                List<String> numbers = new ArrayList<String>();
                for (Protocol p : protocols) if (p.getMetadata() != null) numbers.add(p.getMetadata().getProtocolNumber());
                int added = ProtocolOverrides.mergeTemplate(numbers, overridesFile);
                System.out.println("Overrides file " + overridesFile.getAbsolutePath() + ": added " + added + " new protocol(s), existing entries left untouched");
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
                TreeSet<String> bodyParts = new TreeSet<String>();
                for (Protocol p : protocols) {
                    Metadata m = p.getMetadata();
                    if (m != null && m.getBodyPart() != null && !m.getBodyPart().isEmpty()) bodyParts.add(m.getBodyPart());
                }
                int added = CodeLabels.mergeTemplate(new ArrayList<String>(bodyParts), categoryLabelsFile);
                System.out.println("Category labels file " + categoryLabelsFile.getAbsolutePath() + ": added " + added
                        + " new body part(s) - leave blank to keep the guessed category (Neuro/Body/MSK/Other), fill in only to override it");
            }
            if (initDetectorLabels) {
                int added = CodeLabels.mergeTemplate(new ArrayList<String>(collectDetectorCodes(protocols)), detectorLabelsFile);
                System.out.println("Detector labels file " + detectorLabelsFile.getAbsolutePath() + ": added " + added
                        + " new code(s) - fill in the \"\" values (e.g. \"64 slice\", \"128 slice/80mm\") from the scanner console");
                printBlankCodeSamples(detectorLabelsFile, sampleProtocolNamesByDetectorCode(protocols),
                        "protocol/series using it, to help identify it");
            }
            if (pedsWeightFile != null) {
                new PediatricWeightSheetWriter().write(protocols, pedsWeightFile);
                System.out.println("Wrote pediatric weight reference to " + pedsWeightFile.getAbsolutePath());
            }
            if (jsonDir != null) {
                new ProtocolJsonWriter().writeAll(protocols, jsonDir);
                System.out.println("Wrote combined JSON to " + jsonDir.getAbsolutePath());
            }
            if (htmlFile != null) {
                Map<String, ProtocolOverride> overrides = ProtocolOverrides.load(overridesFile);
                LabelConfig labels = LabelConfig.load(kernelLabelsFile, planeLabelsFile, categoryLabelsFile, detectorLabelsFile);
                String logoDataUri = loadLogoDataUri(logoFile);
                List<PdfLibrary.Entry> pdfLibrary = PdfLibrary.load(pdfLibraryFile);
                ProtocolImages protocolImages = protocolImagesBase == null ? null : new ProtocolImages(protocolImagesBase, protocolImagesExt);
                new ProtocolBookHtmlWriter().write(protocols, overrides, labels, logoDataUri, pdfLibrary, protocolImages, htmlFile);
                System.out.println("Wrote protocol book to " + htmlFile.getAbsolutePath()
                        + (overrides.isEmpty() ? "" : " (" + overrides.size() + " override(s) applied from " + overridesFile + ")")
                        + (logoDataUri != null ? " (logo embedded from " + logoFile + ")" : "")
                        + (pdfLibrary.isEmpty() ? "" : " (" + pdfLibrary.size() + " PDF link(s) from " + pdfLibraryFile + ")")
                        + (protocolImages != null ? " (protocol images from " + protocolImagesBase + "/<number>." + protocolImagesExt + ")" : ""));
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }

    private static int reconstructionCount(Protocol p) {
        int count = 0;
        for (Series s : p.getSeries()) for (Group g : s.getGroups()) count += g.getReconstructions().size();
        return count;
    }

    /** Embeds an image as a base64 data URI so the generated book stays a single file. Null if the file isn't there. */
    private static String loadLogoDataUri(File logoFile) throws IOException {
        if (!logoFile.isFile()) return null;
        String name = logoFile.getName().toLowerCase(Locale.ROOT);
        String mimeType = name.endsWith(".svg") ? "image/svg+xml"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                : name.endsWith(".gif") ? "image/gif"
                : "image/png";
        String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(logoFile.toPath()));
        return "data:" + mimeType + ";base64," + base64;
    }

    private static TreeSet<String> collectReconCodes(List<Protocol> protocols, boolean kernels) {
        TreeSet<String> codes = new TreeSet<String>();
        for (Protocol p : protocols) for (Series s : p.getSeries()) for (Group g : s.getGroups())
            for (Reconstruction r : g.getReconstructions()) {
                String code = kernels ? r.getKernel() : r.getPlane();
                if (code != null && !code.isEmpty()) codes.add(code);
            }
        return codes;
    }

    private static TreeSet<String> collectDetectorCodes(List<Protocol> protocols) {
        TreeSet<String> codes = new TreeSet<String>();
        for (Protocol p : protocols) for (Series s : p.getSeries()) for (Group g : s.getGroups()) {
            String code = g.getAcquisition().getDetector();
            if (code != null && !code.isEmpty()) codes.add(code);
        }
        return codes;
    }

    private static final int MAX_SAMPLES_PER_CODE = 5;

    private static Map<String, List<String>> sampleReconNamesByKernelCode(List<Protocol> protocols) {
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

    private static Map<String, List<String>> sampleProtocolNamesByDetectorCode(List<Protocol> protocols) {
        Map<String, List<String>> samples = new LinkedHashMap<String, List<String>>();
        for (Protocol p : protocols) for (Series s : p.getSeries()) for (Group g : s.getGroups()) {
            String code = g.getAcquisition().getDetector();
            String protocolName = p.getMetadata() == null ? null : p.getMetadata().getName();
            if (code == null || code.isEmpty() || protocolName == null) continue;
            String entry = protocolName + " (series " + s.getNumber() + ")";
            List<String> names = samples.computeIfAbsent(code, k -> new ArrayList<String>());
            if (!names.contains(entry) && names.size() < MAX_SAMPLES_PER_CODE) names.add(entry);
        }
        return samples;
    }

    // The whole reason kernel/detector codes need a hand-maintained labels file is that the raw
    // export gives no clue what a code means - so when --init-*-labels finds a code with no label
    // yet, print a few real names that used it, letting the reader recognize it (e.g. "BONE+" in
    // the name) instead of having to go stand at the scanner console to look it up.
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
