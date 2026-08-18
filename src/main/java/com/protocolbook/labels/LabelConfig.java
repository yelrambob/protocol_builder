package com.protocolbook.labels;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns raw numeric codes from the scanner export into readable labels for the protocol book:
 * recon kernel numbers (site-specific, must come from kernel-labels.json - there's no way to
 * derive "STD"/"DTL"/"BN+" from the export alone), scout plane angles (seeded with the standard
 * GE convention, overridable per site via plane-labels.json), and the reading category for a
 * protocol number's whole-number prefix (seeded with the scanner console's own 1-9 numbering -
 * see {@link #DEFAULT_CATEGORY_LABELS} - overridable per prefix via category-labels.json).
 * Falls back to the raw code when a kernel/plane mapping is missing, so nothing is ever silently
 * hidden - but a protocol-number prefix with no category mapping at all (e.g. "10", used for
 * QA/phantom protocols) is deliberately left out of the generated book; see
 * {@link #categoryForNumber}.
 */
public class LabelConfig {
    private static final Map<String, String> DEFAULT_PLANE_LABELS = new HashMap<String, String>();
    static {
        DEFAULT_PLANE_LABELS.put("0", "AP");
        DEFAULT_PLANE_LABELS.put("90", "Lateral");
        DEFAULT_PLANE_LABELS.put("180", "PA");
        DEFAULT_PLANE_LABELS.put("270", "Lateral");
    }

    // Matches the scanner console's own protocol numbering (1.x-9.x), not a guess from body part
    // text - the machine already groups protocols this way, so the book should match it exactly.
    // 10.x (QA/phantom protocols) has no entry on purpose: see categoryForNumber().
    private static final Map<String, String> DEFAULT_CATEGORY_LABELS = new LinkedHashMap<String, String>();
    static {
        DEFAULT_CATEGORY_LABELS.put("1", "Head");
        DEFAULT_CATEGORY_LABELS.put("2", "Face");
        DEFAULT_CATEGORY_LABELS.put("3", "Neck");
        DEFAULT_CATEGORY_LABELS.put("4", "Upper Ext.");
        DEFAULT_CATEGORY_LABELS.put("5", "Chest");
        DEFAULT_CATEGORY_LABELS.put("6", "ABD/PEL");
        DEFAULT_CATEGORY_LABELS.put("7", "Spine");
        DEFAULT_CATEGORY_LABELS.put("8", "Pelvis");
        DEFAULT_CATEGORY_LABELS.put("9", "Lower Ext.");
    }

    private final Map<String, String> kernelLabels;
    private final Map<String, String> planeLabels;
    private final Map<String, String> categoryLabels;

    public LabelConfig(Map<String, String> kernelLabels, Map<String, String> planeOverrides, Map<String, String> categoryOverrides) {
        this.kernelLabels = kernelLabels != null ? kernelLabels : new HashMap<String, String>();
        this.planeLabels = new HashMap<String, String>(DEFAULT_PLANE_LABELS);
        if (planeOverrides != null)
            for (Map.Entry<String, String> e : planeOverrides.entrySet())
                if (e.getValue() != null && !e.getValue().isEmpty()) this.planeLabels.put(e.getKey(), e.getValue());
        this.categoryLabels = new HashMap<String, String>(DEFAULT_CATEGORY_LABELS);
        if (categoryOverrides != null)
            for (Map.Entry<String, String> e : categoryOverrides.entrySet())
                if (e.getValue() != null && !e.getValue().isEmpty()) this.categoryLabels.put(e.getKey(), e.getValue());
    }

    public static LabelConfig load(File kernelLabelsFile, File planeLabelsFile, File categoryLabelsFile) throws IOException {
        return new LabelConfig(CodeLabels.load(kernelLabelsFile), CodeLabels.load(planeLabelsFile), CodeLabels.load(categoryLabelsFile));
    }

    public String kernel(String code) {
        if (code == null) return null;
        String label = kernelLabels.get(code);
        return label != null && !label.isEmpty() ? label : code;
    }

    public String plane(String code) {
        if (code == null) return null;
        String label = planeLabels.get(code);
        return label != null && !label.isEmpty() ? label : code + "°";
    }

    /**
     * Reading category for a protocol number's whole-number prefix (e.g. 9 from "9.2"), matching
     * the scanner console's own 1-9 numbering. Returns null when the prefix has no mapping - by
     * default that's everything except 1-9 (in particular 10, GE's QA/phantom protocols) - which
     * signals the caller to leave those protocols out of the generated book entirely rather than
     * dumping them in a catch-all category.
     */
    public String categoryForNumber(int prefix) {
        return categoryLabels.get(String.valueOf(prefix));
    }
}
