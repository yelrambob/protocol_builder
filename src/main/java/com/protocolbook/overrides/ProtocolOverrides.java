package com.protocolbook.overrides;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hand-maintained per-protocol overrides (scanning notes, exclusion from the book), keyed by
 * protocol number (e.g. "9.2"). Kept in its own file, separate from the auto-parsed/regenerable
 * data, so re-walking the scanner exports never loses them.
 *
 * File format:
 * {
 *   "9.2": { "notes": "Have the patient bend the knee slightly for..." },
 *   "9.4": { "excluded": true }
 * }
 */
public final class ProtocolOverrides {
    private ProtocolOverrides() {}

    public static Map<String, ProtocolOverride> load(File file) throws IOException {
        Map<String, ProtocolOverride> out = new LinkedHashMap<String, ProtocolOverride>();
        if (file == null || !file.isFile()) return out;
        JSONObject json = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        for (String key : json.keySet()) {
            JSONObject entry = json.getJSONObject(key);
            ProtocolOverride o = new ProtocolOverride();
            o.setNotes(entry.optString("notes", null));
            o.setExcluded(entry.optBoolean("excluded", false));
            out.put(key, o);
        }
        return out;
    }

    /** Writes a starter overrides file (one empty entry per protocol number) for hand-editing. */
    public static void writeTemplate(java.util.List<String> protocolNumbers, File file) throws IOException {
        JSONObject json = new JSONObject();
        for (String number : protocolNumbers) if (number != null) json.put(number, new JSONObject().put("notes", "").put("excluded", false));
        try (FileWriter w = new FileWriter(file)) { w.write(json.toString(2)); }
    }
}
