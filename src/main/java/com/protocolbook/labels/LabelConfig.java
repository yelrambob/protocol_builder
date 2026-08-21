package com.protocolbook.labels;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw numeric codes from the scanner export into readable labels for the protocol book:
 * recon kernel numbers (site-specific, must come from kernel-labels.json - there's no way to
 * derive "STD"/"DTL"/"BN+" from the export alone), scout plane angles (seeded with the standard
 * GE convention, overridable per site via plane-labels.json), ASIR/ASIR-V iterative reconstruction
 * level (GE's own "AR" + percentage convention, e.g. "AR40" -&gt; "40%" - see {@link #asir}), and
 * the reading category for a
 * protocol number's whole-number prefix (seeded with the scanner console's own 1-9 numbering,
 * plus its pediatric counterpart 11-19 (peds prefix = adult prefix + 10, e.g. "15" for pediatric
 * Chest since "5" is adult Chest) - see {@link #DEFAULT_CATEGORY_LABELS} - overridable per prefix
 * via category-labels.json). Falls back to the raw code when a kernel/plane mapping is missing,
 * so nothing is ever silently hidden - but a protocol-number prefix with no category mapping at
 * all (e.g. "10"/"20", used for QA/phantom protocols) is deliberately left out of the generated
 * book; see {@link #categoryForNumber}.
 */
public class LabelConfig {
    // GE's own recon-level ASIR/ASIR-V convention: "AR" followed by the blend percentage, e.g.
    // "AR40" is 40% ASIR - unlike kernel codes, this doesn't need a site-maintained labels file.
    private static final Pattern ASIR_CODE = Pattern.compile("(?i)^AR(\\d+)$");

    private static final Map<String, String> DEFAULT_PLANE_LABELS = new HashMap<String, String>();
    static {
        DEFAULT_PLANE_LABELS.put("0", "AP");
        DEFAULT_PLANE_LABELS.put("90", "Lateral");
        DEFAULT_PLANE_LABELS.put("180", "PA");
        DEFAULT_PLANE_LABELS.put("270", "Lateral");
    }

    // Matches the scanner console's own protocol numbering (1.x-9.x for adult, 11.x-19.x for its
    // pediatric counterpart - same body part, prefix offset by +10), not a guess from body part
    // text - the machine already groups protocols this way, so the book should match it exactly.
    // 10.x/20.x (QA/phantom protocols) have no entry on purpose: see categoryForNumber().
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
        DEFAULT_CATEGORY_LABELS.put("11", "Head");
        DEFAULT_CATEGORY_LABELS.put("12", "Face");
        DEFAULT_CATEGORY_LABELS.put("13", "Neck");
        DEFAULT_CATEGORY_LABELS.put("14", "Upper Ext.");
        DEFAULT_CATEGORY_LABELS.put("15", "Chest");
        DEFAULT_CATEGORY_LABELS.put("16", "ABD/PEL");
        DEFAULT_CATEGORY_LABELS.put("17", "Spine");
        DEFAULT_CATEGORY_LABELS.put("18", "Pelvis");
        DEFAULT_CATEGORY_LABELS.put("19", "Lower Ext.");
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
     * ASIR/ASIR-V iterative reconstruction level for a recon's raw iterativeConfig code, e.g.
     * "AR40" -&gt; "40%" - most protocols land on 40%, but this reads whatever the scanner actually
     * set rather than assuming it. Falls back to the raw code unchanged when it doesn't match
     * GE's "AR"+percentage convention, so nothing is ever silently hidden.
     */
    public String asir(String code) {
        if (code == null) return null;
        Matcher m = ASIR_CODE.matcher(code.trim());
        return m.matches() ? m.group(1) + "%" : code;
    }

    /**
     * Reading category for a protocol number's whole-number prefix (e.g. 9 from "9.2"), matching
     * the scanner console's own 1-9 (adult) / 11-19 (pediatric) numbering. Returns null when the
     * prefix has no mapping - by default that's everything except 1-9/11-19 (in particular 10 and
     * 20, GE's QA/phantom protocols) - which signals the caller to leave those protocols out of
     * the generated book entirely rather than dumping them in a catch-all category.
     */
    public String categoryForNumber(int prefix) {
        return categoryLabels.get(String.valueOf(prefix));
    }
}
