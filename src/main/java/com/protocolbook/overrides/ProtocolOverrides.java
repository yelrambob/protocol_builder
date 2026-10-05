package com.protocolbook.overrides;

import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
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
 *   "9.2": { "notes": "Have the patient bend the knee slightly for..." },
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

    /**
     * Adds an empty entry for any of the given protocol numbers not already present in the file
     * (creating the file if it doesn't exist yet). Existing entries - and everything already set
     * on them - are left untouched. Returns how many new entries were added.
     */
    public static int mergeTemplate(java.util.List<String> protocolNumbers, File file) throws IOException {
        // existing entries are carried over as-is, so fields this method doesn't scaffold (e.g. reconSendDestinations) survive
        JSONObject json = file.isFile()
                ? new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)) : new JSONObject();
        int added = 0;
        for (String number : protocolNumbers) {
            if (number == null || json.has(number)) continue;
            json.put(number, new JSONObject().put("title", "").put("notes", "").put("excluded", false)
                    .put("sendDestination", "").put("contrastVolume", "").put("contrastRate", "")
                    .put("referenceSheet", "").put("scanRange", ""));
            added++;
        }
        try (FileWriter w = new FileWriter(file)) { w.write(json.toString(2)); }
        return added;
    }
}
