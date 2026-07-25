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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Usage: Main <input> [--json <dir>] [--html <file>] [--overrides <file>]
 *             [--kernel-labels <file>] [--plane-labels <file>] [--detector-labels <file>]
 *             [--init-overrides] [--init-kernel-labels] [--init-plane-labels] [--init-detector-labels]
 * <input> is a Protocols.xlsm workbook or a folder to walk for GE protocol exports.
 * --overrides defaults to ./protocol-overrides.json, --kernel-labels to ./kernel-labels.json,
 * --plane-labels to ./plane-labels.json, --detector-labels to ./detector-labels.json, all only if present.
 */
public class Main {
    public static void main(String[] args) {
        try {
            File input = null;
            File jsonDir = null, htmlFile = null;
            File overridesFile = new File("protocol-overrides.json");
            File kernelLabelsFile = new File("kernel-labels.json");
            File planeLabelsFile = new File("plane-labels.json");
            File detectorLabelsFile = new File("detector-labels.json");
            boolean initOverrides = false, initKernelLabels = false, initPlaneLabels = false, initDetectorLabels = false;
            for (int i = 0; i < args.length; i++) {
                if ("--json".equals(args[i])) jsonDir = new File(args[++i]);
                else if ("--html".equals(args[i])) htmlFile = new File(args[++i]);
                else if ("--overrides".equals(args[i])) overridesFile = new File(args[++i]);
                else if ("--kernel-labels".equals(args[i])) kernelLabelsFile = new File(args[++i]);
                else if ("--plane-labels".equals(args[i])) planeLabelsFile = new File(args[++i]);
                else if ("--detector-labels".equals(args[i])) detectorLabelsFile = new File(args[++i]);
                else if ("--init-overrides".equals(args[i])) initOverrides = true;
                else if ("--init-kernel-labels".equals(args[i])) initKernelLabels = true;
                else if ("--init-plane-labels".equals(args[i])) initPlaneLabels = true;
                else if ("--init-detector-labels".equals(args[i])) initDetectorLabels = true;
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
            if (initDetectorLabels) {
                int added = CodeLabels.mergeTemplate(new ArrayList<String>(collectDetectorCodes(protocols)), detectorLabelsFile);
                System.out.println("Detector labels file " + detectorLabelsFile.getAbsolutePath() + ": added " + added
                        + " new code(s) - fill in the \"\" values (e.g. \"64 slice\", \"128 slice/80mm\") from the scanner console");
                printBlankCodeSamples(detectorLabelsFile, sampleProtocolNamesByDetectorCode(protocols),
                        "protocol/series using it, to help identify it");
            }
            if (jsonDir != null) {
                new ProtocolJsonWriter().writeAll(protocols, jsonDir);
                System.out.println("Wrote combined JSON to " + jsonDir.getAbsolutePath());
            }
            if (htmlFile != null) {
                Map<String, ProtocolOverride> overrides = ProtocolOverrides.load(overridesFile);
                LabelConfig labels = LabelConfig.load(kernelLabelsFile, planeLabelsFile, detectorLabelsFile);
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
