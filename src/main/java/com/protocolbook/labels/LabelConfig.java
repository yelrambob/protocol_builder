package com.protocolbook.labels;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns raw numeric codes from the scanner export into readable labels for the protocol book:
 * recon kernel numbers (site-specific, must come from kernel-labels.json - there's no way to
 * derive "STD"/"DTL"/"BN+" from the export alone), scout plane angles (seeded with the standard
 * GE convention, overridable per site via plane-labels.json), the reading category a body part
 * belongs to (keyword-guessed - Neuro/Body/MSK/Other - overridable per exact body part string via
 * category-labels.json), and detector row count (GE's "macroRowNumber", site-specific, from
 * detector-labels.json).
 * Falls back to the raw code/a keyword guess when a mapping is missing, so nothing is ever
 * silently hidden.
 */
public class LabelConfig {
    private static final Map<String, String> DEFAULT_PLANE_LABELS = new HashMap<String, String>();
    static {
        DEFAULT_PLANE_LABELS.put("0", "AP");
        DEFAULT_PLANE_LABELS.put("90", "Lateral");
        DEFAULT_PLANE_LABELS.put("180", "PA");
        DEFAULT_PLANE_LABELS.put("270", "Lateral");
    }

    private final Map<String, String> kernelLabels;
    private final Map<String, String> planeLabels;
    private final Map<String, String> categoryOverrides;
    private final Map<String, String> detectorLabels;

    public LabelConfig(Map<String, String> kernelLabels, Map<String, String> planeOverrides,
                        Map<String, String> categoryOverrides, Map<String, String> detectorLabels) {
        this.kernelLabels = kernelLabels != null ? kernelLabels : new HashMap<String, String>();
        this.detectorLabels = detectorLabels != null ? detectorLabels : new HashMap<String, String>();
        this.planeLabels = new HashMap<String, String>(DEFAULT_PLANE_LABELS);
        if (planeOverrides != null)
            for (Map.Entry<String, String> e : planeOverrides.entrySet())
                if (e.getValue() != null && !e.getValue().isEmpty()) this.planeLabels.put(e.getKey(), e.getValue());
        this.categoryOverrides = categoryOverrides != null ? categoryOverrides : new HashMap<String, String>();
    }

    public static LabelConfig load(File kernelLabelsFile, File planeLabelsFile, File categoryLabelsFile, File detectorLabelsFile) throws IOException {
        return new LabelConfig(CodeLabels.load(kernelLabelsFile), CodeLabels.load(planeLabelsFile),
                CodeLabels.load(categoryLabelsFile), CodeLabels.load(detectorLabelsFile));
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

    /** Broad reading category for grouping the protocol book (Neuro/Body/MSK/Other by default). */
    public String category(String bodyPart) {
        if (bodyPart == null || bodyPart.trim().isEmpty()) return "Other";
        for (Map.Entry<String, String> e : categoryOverrides.entrySet())
            if (e.getKey().equalsIgnoreCase(bodyPart) && e.getValue() != null && !e.getValue().isEmpty()) return e.getValue();
        String b = bodyPart.toLowerCase(Locale.ROOT);
        if (b.contains("head") || b.contains("orbit") || b.contains("sinus") || b.contains("brain") || b.contains("face")) return "Neuro";
        if (b.contains("spine") || b.contains("neck") || b.contains("cervical")) return "Neuro";
        if (b.contains("chest") || b.contains("abdomen") || b.contains("pelvis") || b.contains("cardiac")) return "Body";
        if (b.contains("extremit")) return "MSK";
        return "Other";
    }

    // Detector row count (GE's "macroRowNumber") - site-specific mapping to slice/collimation
    // labels like "128 slice/80mm", same reasoning as kernel(): must come from detector-labels.json.
    public String detector(String code) {
        if (code == null) return null;
        String label = detectorLabels.get(code);
        return label != null && !label.isEmpty() ? label : code;
    }
}
