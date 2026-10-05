package com.protocolbook.labels;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A flat, hand-maintained code -> label lookup (e.g. recon kernel number -> "STD"/"DTL"/"BN+",
 * scout plane angle -> "AP"/"Lateral"/"PA"). GE's raw exports only carry the numeric code, and
 * there's no reliable way to derive the label from the export alone - a person who can see the
 * scanner console (or its documentation) has to supply it once, here.
 *
 * File format: { "8": "STD", "4": "DTL" }
 */
public final class CodeLabels {
    private CodeLabels() {}

    public static Map<String, String> load(File file) throws IOException {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (file == null || !file.isFile()) return out;
        JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        for (String key : json.keySet()) out.put(key, json.optString(key, ""));
        return out;
    }

    /**
     * Writes the whole lookup, codes in numeric order (text codes after), keeping the previous file as
     * &lt;file&gt;.bak - what the GUI's "Kernel names" screen saves.
     */
    public static void save(Map<String, String> labels, File file) throws IOException {
        java.util.List<String> codes = new java.util.ArrayList<String>(labels.keySet());
        codes.sort((a, b) -> {
            boolean na = a.matches("\\d+"), nb = b.matches("\\d+");
            if (na && nb) return Long.compare(Long.parseLong(a), Long.parseLong(b));
            return na ? -1 : nb ? 1 : a.compareTo(b);
        });
        StringBuilder out = new StringBuilder("{\n");
        for (int i = 0; i < codes.size(); i++) {
            String v = labels.get(codes.get(i));
            out.append("  ").append(JSONObject.quote(codes.get(i))).append(": ").append(JSONObject.quote(v == null ? "" : v.trim()))
                    .append(i < codes.size() - 1 ? ",\n" : "\n");
        }
        out.append("}\n");
        if (file.isFile()) Files.copy(file.toPath(), new File(file.getPath() + ".bak").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.write(file.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Adds an empty entry for any code not already present, preserving existing labels. Returns how many were added. */
    public static int mergeTemplate(List<String> codes, File file) throws IOException {
        Map<String, String> existing = load(file);
        JSONObject json = new JSONObject();
        for (Map.Entry<String, String> e : existing.entrySet()) json.put(e.getKey(), e.getValue());
        int added = 0;
        for (String code : codes) {
            if (code == null || code.isEmpty() || json.has(code)) continue;
            json.put(code, "");
            added++;
        }
        try (FileWriter w = new FileWriter(file)) { w.write(json.toString(2)); }
        return added;
    }
}
