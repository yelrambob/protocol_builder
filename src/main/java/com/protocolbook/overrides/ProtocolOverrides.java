package com.protocolbook.overrides;

import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import org.json.JSONObject;

import com.protocolbook.html.ProtocolNumbers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
            JSONObject reconSends = entry.optJSONObject("reconSendDestinations");
            if (reconSends != null) for (String recon : reconSends.keySet()) o.getReconSendDestinations().put(recon, reconSends.optString(recon, ""));
            out.put(key, o);
        }
        return out;
    }

    /** One line per reconSendDestinations recon name that matches no recon in its protocol, so a typo doesn't go unnoticed. */
    public static List<String> unmatchedReconNames(List<Protocol> protocols, Map<String, ProtocolOverride> overrides) {
        List<String> out = new ArrayList<String>();
        for (Protocol p : protocols) {
            String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
            ProtocolOverride o = number == null ? null : overrides.get(number);
            if (o == null || o.getReconSendDestinations().isEmpty()) continue;
            List<Reconstruction> recons = new ArrayList<Reconstruction>();
            for (Series s : p.getSeries()) for (Group g : s.getGroups()) recons.addAll(g.getReconstructions());
            for (String name : o.unmatchedReconNames(recons))
                out.add("protocol " + number + " reconSendDestinations: no recon named '" + name + "' - check the spelling against the book");
        }
        return out;
    }

    // Fields in the order they're written, so every entry reads the same top to bottom; any other key follows these.
    private static final List<String> FIELD_ORDER = Arrays.asList("protocolName", "title", "notes", "excluded", "sendDestination",
            "contrastVolume", "contrastRate", "referenceSheet", "scanRange", "reconSendDestinations", "threeD");

    /**
     * Adds an empty entry for any protocol number not already present in the file (creating the file
     * if it doesn't exist yet), and sets "protocolName" on every entry to that protocol's scanner name
     * so the file can be searched by name. protocolName is only a label - it's never read back; use
     * "title" to rename a protocol in the book. Everything else on existing entries is left untouched,
     * entries for numbers no longer on the scanner are kept, and the file is written sorted by protocol
     * number. Returns how many new entries were added.
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
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) { w.write(sortedJson(json)); }
        return added;
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
