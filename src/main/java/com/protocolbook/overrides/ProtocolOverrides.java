package com.protocolbook.overrides;

import com.protocolbook.model.Protocol;
import org.json.JSONArray;
import org.json.JSONObject;

import com.protocolbook.html.ProtocolNumbers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-maintained per-protocol overrides (display title, scanning notes, exclusion from the
 * book, send destination, contrast volume/rate), keyed by protocol number (e.g. "9.2"). Kept in
 * its own file, separate from the auto-parsed/regenerable data, so re-walking the scanner exports
 * never loses them.
 *
 * File format:
 * {
 *   "9.2": { "protocolName": "CT LWR EXT KNEE WITH CONTRAST", "notes": "Have the patient bend the knee slightly for..." },
 *   "9.4": { "excluded": true },
 *   "5.1": { "sendDestination": "AHSPACS + 3D Lab" },
 *   "3.7": { "title": "CT Neck Soft Tissue (renamed)" },
 *   "5.2": { "contrastVolume": "100", "contrastRate": "3.5" },
 *   "8.6": { "referenceSheet": "CT Routine Abd-Pel" },
 *   "8.7": { "scanRange": "Iliac crests to ischial tuberosities" },
 *   "1.5": { "reconSendDestinations": { "AXIAL CTA HEAD": "AHSPACS, RAPID 1" } }
 * }
 *
 * "title" only renames how a protocol displays in the generated book - it never touches the
 * underlying scanner name, so this is the easiest way to fix a confusing/inconsistent protocol
 * name without editing the source export. To exclude a protocol from the book entirely, set
 * "excluded": true on its entry - both are the same one-line edit in this same file, and
 * --init-overrides keeps every protocol number scaffolded here with blank values so there's
 * nothing to hunt for.
 *
 * "contrastVolume"/"contrastRate" override the IV contrast volume (mL) and rate (mL/s) shown for
 * this protocol's series, in case what the scanner export carries doesn't match actual practice
 * (or a protocol has no injector data captured at all). Either can be set independently; leave
 * the other blank to keep the parsed value for it.
 *
 * "referenceSheet" names the reference-workbook sheet (see ReferenceSheets) to take this
 * protocol's scan range from, for when matching by name picks the wrong sheet or none;
 * "scanRange" types the scan range in directly and wins over any sheet.
 *
 * "protocolName" is filled in by --init-overrides from the scanner name, purely so the file can be
 * searched by name; it's never read back (use "title" to rename).
 *
 * "threeD": true adds the 3D MIP / 3D VR series to a protocol's page, false leaves them off; without
 * it they're shown for any protocol whose images auto-send to an AW Server host.
 *
 * "reconSendDestinations" replaces the auto-send hosts shown for individual recons, by recon name
 * (as shown in the book; case and repeated spaces don't matter). The typed list replaces the
 * export's for that recon, so list every host, e.g. "AHSPACS, RAPID 1".
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
            o.setTitle(entry.optString("title", null));
            o.setNotes(entry.optString("notes", null));
            o.setExcluded(entry.optBoolean("excluded", false));
            o.setSendDestination(entry.optString("sendDestination", null));
            o.setContrastVolume(entry.optString("contrastVolume", null));
            o.setContrastRate(entry.optString("contrastRate", null));
            o.setReferenceSheet(entry.optString("referenceSheet", null));
            o.setScanRange(entry.optString("scanRange", null));
            if (entry.has("threeD") && !entry.isNull("threeD") && !"".equals(entry.opt("threeD"))) o.setThreeD(entry.optBoolean("threeD"));
            o.setContrastDelay(entry.optString("contrastDelay", null));
            o.setExamCtdi(entry.optString("examCtdi", null));
            o.setExamDlp(entry.optString("examDlp", null));
            readNested(entry.optJSONObject("recons"), o.getRecons());
            readNested(entry.optJSONObject("series"), o.getSeries());
            JSONObject reconSends = entry.optJSONObject("reconSendDestinations");
            if (reconSends != null) for (String recon : reconSends.keySet()) o.getReconSendDestinations().put(recon, reconSends.optString(recon, ""));
            JSONArray added = entry.optJSONArray("addedFields");
            if (added != null) for (int i = 0; i < added.length(); i++) {
                JSONObject f = added.optJSONObject(i);
                if (f != null) o.getAddedFields().add(new ProtocolOverride.AddedField(
                        f.optString("series", null), f.optString("title", ""), f.optString("value", "")));
            }
            out.put(key, o);
        }
        return out;
    }

    // { "AXIAL KNEE DET 2.5MM": { "kernel": "Bone", "wwwl": "400/40" } } -> name -> field -> value (numbers kept as typed).
    private static void readNested(JSONObject json, Map<String, Map<String, String>> into) {
        if (json == null) return;
        for (String key : json.keySet()) {
            JSONObject fields = json.optJSONObject(key);
            if (fields == null) continue;
            Map<String, String> values = new LinkedHashMap<String, String>();
            for (String field : fields.keySet()) values.put(field, fields.isNull(field) ? "" : String.valueOf(fields.get(field)));
            into.put(key, values);
        }
    }

    /** One line per typed recon name / series number / field name that matches nothing, so a typo doesn't go unnoticed. */
    public static List<String> problems(List<Protocol> protocols, Map<String, ProtocolOverride> overrides) {
        List<String> out = new ArrayList<String>();
        for (Protocol p : protocols) {
            String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
            ProtocolOverride o = number == null ? null : overrides.get(number);
            if (o == null) continue;
            for (String problem : o.problems(p.getSeries())) out.add("protocol " + number + " " + problem);
        }
        return out;
    }

    // Fields in the order they're written, so every entry reads the same top to bottom; any other key follows these.
    private static final List<String> FIELD_ORDER = Arrays.asList("protocolName", "title", "notes", "excluded", "sendDestination",
            "contrastVolume", "contrastRate", "referenceSheet", "scanRange", "contrastDelay", "examCtdi", "examDlp", "series", "recons", "reconSendDestinations", "threeD", "addedFields");

    /**
     * Adds an empty entry for any protocol number not already present in the file (creating the file
     * if it doesn't exist yet), and sets "protocolName" on every entry to that protocol's scanner name
     * so the file can be searched by name. protocolName is only a label - it's never read back; use
     * "title" to rename a protocol in the book. Everything else on existing entries is left untouched,
     * entries for numbers no longer on the scanner are kept, and the file is written sorted by protocol
     * number. The previous file is kept as &lt;file&gt;.bak. Returns how many new entries were added.
     */
    public static int mergeTemplate(Map<String, String> protocolNames, File file) throws IOException {
        JSONObject json = file.isFile()
                ? new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
        int added = 0;
        for (Map.Entry<String, String> e : protocolNames.entrySet()) {
            String number = e.getKey();
            if (number == null) continue;
            if (!json.has(number)) {
                json.put(number, new JSONObject().put("title", "").put("notes", "").put("excluded", false)
                        .put("sendDestination", "").put("contrastVolume", "").put("contrastRate", "")
                        .put("referenceSheet", "").put("scanRange", ""));
                added++;
            }
            if (e.getValue() != null) json.getJSONObject(number).put("protocolName", e.getValue());
        }
        write(json, file);
        return added;
    }

    /**
     * Writes every entry in overrides back to the file (what the GUI edits), leaving anything else in
     * it untouched: entries for other protocol numbers and any key this tool doesn't know about. A
     * field that's blank is written as "" if the entry already had it (so a --init-overrides scaffold
     * keeps its shape) and left out otherwise. protocolNames refreshes the "protocolName" label the
     * same way --init-overrides does. The previous file is kept as &lt;file&gt;.bak.
     */
    public static void save(Map<String, ProtocolOverride> overrides, Map<String, String> protocolNames, File file) throws IOException {
        JSONObject json = file.isFile()
                ? new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
        for (Map.Entry<String, ProtocolOverride> e : overrides.entrySet()) {
            String number = e.getKey();
            if (number == null) continue;
            JSONObject entry = json.optJSONObject(number);
            if (entry == null) { entry = new JSONObject(); json.put(number, entry); }
            ProtocolOverride o = e.getValue();
            String name = protocolNames == null ? null : protocolNames.get(number);
            if (name != null) entry.put("protocolName", name);
            putText(entry, "title", o.getTitle());
            putText(entry, "notes", o.getNotes());
            if (o.isExcluded() || entry.has("excluded")) entry.put("excluded", o.isExcluded());
            putText(entry, "sendDestination", o.getSendDestination());
            putText(entry, "contrastVolume", o.getContrastVolume());
            putText(entry, "contrastRate", o.getContrastRate());
            putText(entry, "referenceSheet", o.getReferenceSheet());
            putText(entry, "scanRange", o.getScanRange());
            putText(entry, "contrastDelay", o.getContrastDelay());
            putText(entry, "examCtdi", o.getExamCtdi());
            putText(entry, "examDlp", o.getExamDlp());
            if (o.getThreeD() != null) entry.put("threeD", o.getThreeD()); else entry.remove("threeD");
            putNested(entry, "series", o.getSeries());
            putNested(entry, "recons", o.getRecons());
            JSONObject sends = new JSONObject();
            for (Map.Entry<String, String> s : o.getReconSendDestinations().entrySet())
                if (s.getValue() != null && !s.getValue().trim().isEmpty()) sends.put(s.getKey(), s.getValue().trim());
            if (sends.isEmpty()) entry.remove("reconSendDestinations"); else entry.put("reconSendDestinations", sends);
            JSONArray added = new JSONArray();
            for (ProtocolOverride.AddedField f : o.getAddedFields()) {
                if (f.isBlank()) continue;
                JSONObject field = new JSONObject();
                if (!f.isExam()) field.put("series", f.getSeries().trim());
                field.put("title", f.getTitle() == null ? "" : f.getTitle().trim());
                field.put("value", f.getValue() == null ? "" : f.getValue().trim());
                added.put(field);
            }
            if (added.isEmpty()) entry.remove("addedFields"); else entry.put("addedFields", added);
        }
        write(json, file);
    }

    private static void putText(JSONObject entry, String key, String value) {
        if (value != null && !value.trim().isEmpty()) entry.put(key, value.trim());
        else if (entry.has(key)) entry.put(key, "");
    }

    // Only the non-blank values; a series/recon with nothing typed is left out, and the key itself when nothing is.
    private static void putNested(JSONObject entry, String key, Map<String, Map<String, String>> values) {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, Map<String, String>> e : values.entrySet()) {
            JSONObject fields = new JSONObject();
            for (Map.Entry<String, String> f : e.getValue().entrySet())
                if (f.getValue() != null && !f.getValue().trim().isEmpty()) fields.put(f.getKey(), f.getValue().trim());
            if (!fields.isEmpty()) out.put(e.getKey(), fields);
        }
        if (out.isEmpty()) entry.remove(key); else entry.put(key, out);
    }

    // Keep the previous version as <file>.bak, and write to a temp file that's moved into place, so a
    // crash or full disk mid-write can never leave a half-written overrides file.
    private static void write(JSONObject json, File file) throws IOException {
        File parent = file.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory()) parent.mkdirs();
        if (file.isFile()) Files.copy(file.toPath(), new File(parent, file.getName() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
        File temp = File.createTempFile(file.getName(), ".tmp", parent);
        try {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) { w.write(sortedJson(json)); }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    // org.json doesn't keep key order, so the file is written by hand: entries by protocol number,
    // fields in FIELD_ORDER.
    static String sortedJson(JSONObject json) {
        List<String> numbers = new ArrayList<String>(json.keySet());
        numbers.sort((a, b) -> {
            int cmp = ProtocolNumbers.compare(a, b);
            return cmp != 0 ? cmp : a.compareTo(b);
        });
        StringBuilder out = new StringBuilder("{\n");
        for (int i = 0; i < numbers.size(); i++) {
            String number = numbers.get(i);
            out.append("  ").append(JSONObject.quote(number)).append(": ");
            JSONObject entry = json.optJSONObject(number);
            if (entry == null) out.append(JSONObject.valueToString(json.get(number)));
            else {
                List<String> keys = new ArrayList<String>();
                for (String k : FIELD_ORDER) if (entry.has(k)) keys.add(k);
                List<String> rest = new ArrayList<String>(entry.keySet());
                rest.removeAll(FIELD_ORDER);
                Collections.sort(rest);
                keys.addAll(rest);
                out.append("{\n");
                for (int k = 0; k < keys.size(); k++) {
                    Object value = entry.get(keys.get(k));
                    String rendered = value instanceof JSONObject ? ((JSONObject) value).toString(2).replace("\n", "\n    ")
                            : value instanceof JSONArray ? ((JSONArray) value).toString(2).replace("\n", "\n    ")
                            : JSONObject.valueToString(value);
                    out.append("    ").append(JSONObject.quote(keys.get(k))).append(": ").append(rendered)
                            .append(k < keys.size() - 1 ? ",\n" : "\n");
                }
                out.append("  }");
            }
            out.append(i < numbers.size() - 1 ? ",\n" : "\n");
        }
        return out.append("}\n").toString();
    }
}
