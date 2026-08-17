package com.protocolbook.manual;

import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Protocols that don't exist as a folder on the scanner (proposed/documented but not yet
 * programmed into the console, or pulled from elsewhere) - hand-maintained here since there's
 * nothing for the folder walker to discover. Shown in the book exactly like any other protocol:
 * same reading-category grouping, same protocol-overrides.json exclusion/scanning-notes (it's
 * still keyed by protocolNumber, so it applies uniformly) - just without series/dose tables,
 * since there's no scan data to show. The "notes" field here is the protocol's own description/
 * technique, separate from protocol-overrides.json's scanning notes.
 *
 * File format:
 * [
 *   { "protocolNumber": "9.9", "name": "CT LWR EXT CUSTOM PROTOCOL", "patientType": "adult",
 *     "bodyPart": "lower Extremities", "notes": "Free-text description of the technique." }
 * ]
 */
public final class ManualProtocols {
    private ManualProtocols() {}

    public static List<Protocol> load(File file) throws IOException {
        List<Protocol> out = new ArrayList<Protocol>();
        if (file == null || !file.isFile()) return out;
        JSONArray json = new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        for (int i = 0; i < json.length(); i++) {
            JSONObject entry = json.getJSONObject(i);
            Protocol p = new Protocol();
            Metadata m = new Metadata();
            m.setProtocolNumber(entry.optString("protocolNumber", null));
            m.setName(entry.optString("name", null));
            m.setPatientType(entry.optString("patientType", null));
            m.setBodyPart(entry.optString("bodyPart", null));
            p.setMetadata(m);
            String notes = entry.optString("notes", null);
            if (notes != null && !notes.isEmpty()) p.getNotes().add(notes);
            out.add(p);
        }
        return out;
    }

    /**
     * Merges manual protocols into the scanner-discovered list. If a manual entry's protocol
     * number collides with one already found on the scanner, the manual entry wins (it was
     * deliberately hand-added) and a warning is printed so the collision isn't silent.
     */
    public static List<Protocol> merge(List<Protocol> discovered, List<Protocol> manual) {
        Map<String, Protocol> byNumber = new LinkedHashMap<String, Protocol>();
        List<Protocol> unnumbered = new ArrayList<Protocol>();
        for (Protocol p : discovered) {
            String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
            if (number == null || number.isEmpty()) unnumbered.add(p); else byNumber.put(number, p);
        }
        for (Protocol p : manual) {
            String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
            if (number == null || number.isEmpty()) { unnumbered.add(p); continue; }
            if (byNumber.containsKey(number))
                System.err.println("WARN: manual protocol " + number + " overrides one already found on the scanner");
            byNumber.put(number, p);
        }
        List<Protocol> result = new ArrayList<Protocol>(byNumber.values());
        result.addAll(unnumbered);
        return result;
    }
}
