package com.protocolbook;

import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.io.ProtocolJsonWriter;
import com.protocolbook.labels.CodeLabels;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.overrides.ProtocolOverrides;
import com.protocolbook.parser.GEWorkbookParser;
import com.protocolbook.parser.ProtocolFolderWalker;
import com.protocolbook.parser.ProtocolParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Usage: Main <input> [--json <dir>] [--html <file>] [--overrides <file>]
 *             [--kernel-labels <file>] [--plane-labels <file>]
 *             [--init-overrides] [--init-kernel-labels] [--init-plane-labels]
 * <input> is a Protocols.xlsm workbook or a folder to walk for GE protocol exports.
 * --overrides defaults to ./protocol-overrides.json, --kernel-labels to ./kernel-labels.json,
 * --plane-labels to ./plane-labels.json, all only if present.
 */
public class Main {
    public static void main(String[] args) {
        try {
            File input = null;
            File jsonDir = null, htmlFile = null;
            File overridesFile = new File("protocol-overrides.json");
            File kernelLabelsFile = new File("kernel-labels.json");
            File planeLabelsFile = new File("plane-labels.json");
            boolean initOverrides = false, initKernelLabels = false, initPlaneLabels = false;
            for (int i = 0; i < args.length; i++) {
                if ("--json".equals(args[i])) jsonDir = new File(args[++i]);
                else if ("--html".equals(args[i])) htmlFile = new File(args[++i]);
                else if ("--overrides".equals(args[i])) overridesFile = new File(args[++i]);
                else if ("--kernel-labels".equals(args[i])) kernelLabelsFile = new File(args[++i]);
                else if ("--plane-labels".equals(args[i])) planeLabelsFile = new File(args[++i]);
                else if ("--init-overrides".equals(args[i])) initOverrides = true;
                else if ("--init-kernel-labels".equals(args[i])) initKernelLabels = true;
                else if ("--init-plane-labels".equals(args[i])) initPlaneLabels = true;
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
            if (jsonDir != null) {
                new ProtocolJsonWriter().writeAll(protocols, jsonDir);
                System.out.println("Wrote combined JSON to " + jsonDir.getAbsolutePath());
            }
            if (htmlFile != null) {
                Map<String, ProtocolOverride> overrides = ProtocolOverrides.load(overridesFile);
                LabelConfig labels = LabelConfig.load(kernelLabelsFile, planeLabelsFile);
                new ProtocolBookHtmlWriter().write(protocols, overrides, labels, htmlFile);
                System.out.println("Wrote protocol book to " + htmlFile.getAbsolutePath()
                        + (overrides.isEmpty() ? "" : " (" + overrides.size() + " override(s) applied from " + overridesFile + ")"));
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
