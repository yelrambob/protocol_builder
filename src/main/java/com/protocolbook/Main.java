package com.protocolbook;

import com.protocolbook.html.PediatricWeightSheetWriter;
import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.io.ProtocolJsonWriter;
import com.protocolbook.labels.CodeLabels;
import com.protocolbook.labels.LabelConfig;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Usage: Main <input> [--json <dir>] [--html <file>] [--peds-weights <file>] [--overrides <file>]
 *             [--kernel-labels <file>] [--plane-labels <file>] [--category-labels <file>] [--logo <file>]
 *             [--init-overrides] [--init-kernel-labels] [--init-plane-labels] [--init-category-labels]
 * <input> is a Protocols.xlsm workbook or a folder to walk for GE protocol exports.
 * --overrides defaults to ./protocol-overrides.json, --kernel-labels to ./kernel-labels.json,
 * --plane-labels to ./plane-labels.json, --category-labels to ./category-labels.json, --logo to
 * ./logo.png, all only if present. --logo is embedded (base64) into the generated book, not linked.
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
            File logoFile = new File("logo.png");
            boolean initOverrides = false, initKernelLabels = false, initPlaneLabels = false, initCategoryLabels = false;
            for (int i = 0; i < args.length; i++) {
                if ("--json".equals(args[i])) jsonDir = new File(args[++i]);
                else if ("--html".equals(args[i])) htmlFile = new File(args[++i]);
                else if ("--peds-weights".equals(args[i])) pedsWeightFile = new File(args[++i]);
                else if ("--overrides".equals(args[i])) overridesFile = new File(args[++i]);
                else if ("--kernel-labels".equals(args[i])) kernelLabelsFile = new File(args[++i]);
                else if ("--plane-labels".equals(args[i])) planeLabelsFile = new File(args[++i]);
                else if ("--category-labels".equals(args[i])) categoryLabelsFile = new File(args[++i]);
                else if ("--logo".equals(args[i])) logoFile = new File(args[++i]);
                else if ("--init-overrides".equals(args[i])) initOverrides = true;
                else if ("--init-kernel-labels".equals(args[i])) initKernelLabels = true;
                else if ("--init-plane-labels".equals(args[i])) initPlaneLabels = true;
                else if ("--init-category-labels".equals(args[i])) initCategoryLabels = true;
                else if (input == null) input = new File(args[i]);
            }
            if (input == null) input = new File("Protocols.xlsm");

            ProtocolParser parser = input.isDirectory() ? new ProtocolFolderWalker() : new GEWorkbookParser();
            List<Protocol> protocols = parser.parse(input);
            System.out.println("Parsed " + protocols.size() + " protocol(s) from " + input.getAbsolutePath());
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
                int added = CodeLabels.mergeTemplate(new ArrayList<String>(collectCodes(protocols, true)), kernelLabelsFile);
                System.out.println("Kernel labels file " + kernelLabelsFile.getAbsolutePath() + ": added " + added
                        + " new code(s) - fill in the \"\" values (e.g. \"STD\", \"DTL\", \"BN\", \"BN+\") from the scanner console");
            }
            if (initPlaneLabels) {
                int added = CodeLabels.mergeTemplate(new ArrayList<String>(collectCodes(protocols, false)), planeLabelsFile);
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
                LabelConfig labels = LabelConfig.load(kernelLabelsFile, planeLabelsFile, categoryLabelsFile);
                String logoDataUri = loadLogoDataUri(logoFile);
                new ProtocolBookHtmlWriter().write(protocols, overrides, labels, logoDataUri, htmlFile);
                System.out.println("Wrote protocol book to " + htmlFile.getAbsolutePath()
                        + (overrides.isEmpty() ? "" : " (" + overrides.size() + " override(s) applied from " + overridesFile + ")")
                        + (logoDataUri != null ? " (logo embedded from " + logoFile + ")" : ""));
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

    private static TreeSet<String> collectCodes(List<Protocol> protocols, boolean kernels) {
        TreeSet<String> codes = new TreeSet<String>();
        for (Protocol p : protocols) for (Series s : p.getSeries()) for (Group g : s.getGroups())
            for (Reconstruction r : g.getReconstructions()) {
                String code = kernels ? r.getKernel() : r.getPlane();
                if (code != null && !code.isEmpty()) codes.add(code);
            }
        return codes;
    }
}
