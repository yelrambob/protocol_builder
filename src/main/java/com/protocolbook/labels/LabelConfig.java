package com.protocolbook.labels;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Turns raw numeric codes from the scanner export into readable labels for the protocol book:
 * recon kernel numbers (site-specific, must come from kernel-labels.json - there's no way to
 * derive "STD"/"DTL"/"BN+" from the export alone) and scout plane angles (seeded with the
 * standard GE convention, overridable per site via plane-labels.json).
 * Falls back to the raw code when a mapping is missing, so nothing is ever silently hidden.
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

    public LabelConfig(Map<String, String> kernelLabels, Map<String, String> planeOverrides) {
        this.kernelLabels = kernelLabels != null ? kernelLabels : new HashMap<String, String>();
        this.planeLabels = new HashMap<String, String>(DEFAULT_PLANE_LABELS);
        if (planeOverrides != null)
            for (Map.Entry<String, String> e : planeOverrides.entrySet())
                if (e.getValue() != null && !e.getValue().isEmpty()) this.planeLabels.put(e.getKey(), e.getValue());
    }

    public static LabelConfig load(File kernelLabelsFile, File planeLabelsFile) throws IOException {
        return new LabelConfig(CodeLabels.load(kernelLabelsFile), CodeLabels.load(planeLabelsFile));
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
}
