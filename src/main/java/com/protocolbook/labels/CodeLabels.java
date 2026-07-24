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
