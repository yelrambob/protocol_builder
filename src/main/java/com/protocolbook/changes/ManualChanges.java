package com.protocolbook.changes;

import com.protocolbook.html.ProtocolNumbers;
import com.protocolbook.html.ScannerValues;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything typed into protocol-overrides.json, as one row per changed value next to what the scanner
 * has - the GUI's review screen and the changes PDF. Book-only changes: the scanner itself still has
 * its own values, so this doubles as the list of what to update at the console.
 */
public final class ManualChanges {
    private ManualChanges() {}

    public static final class Change {
        /** protocolNumber is the protocol's key (its number, or for Siemens a name-based key); displayNumber is what to show ("" if none). */
        public final String protocolNumber, displayNumber, protocolName, where, setting, scannerValue, newValue;

        Change(Protocol p, String protocolName, String where, String setting, String scannerValue, String newValue) {
            this.protocolNumber = number(p);
            this.displayNumber = ProtocolNumbers.displayNumber(p);
            this.protocolName = protocolName;
            this.where = where;
            this.setting = setting;
            this.scannerValue = scannerValue;
            this.newValue = newValue;
        }
    }

    private static final Map<String, String> LABELS = new LinkedHashMap<String, String>();
    static {
        LABELS.put("kv", "kV");
        LABELS.put("ma", "mA / mAs");
        LABELS.put("noiseIndex", "Noise index");
        LABELS.put("pitch", "Pitch");
        LABELS.put("rotationTime", "Rotation (s)");
        LABELS.put("ctdi", "CTDIvol (mGy)");
        LABELS.put("name", "Name");
        LABELS.put("thickness", "Thickness");
        LABELS.put("interval", "Interval");
        LABELS.put("kernel", "Kernel");
        LABELS.put("asir", "ASIR");
        LABELS.put("wwwl", "WW/WL");
        LABELS.put("sendTo", "Sends to");
    }

    /** Readable name for a series/recon field key (see ProtocolOverride.SERIES_FIELDS / RECON_FIELDS). */
    public static String label(String field) {
        String label = LABELS.get(field);
        return label == null ? field : label;
    }

    /** Every manual change, in protocol-number order; protocols with nothing typed are left out. */
    public static List<Change> all(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels) {
        List<Protocol> sorted = new ArrayList<Protocol>(protocols);
        sorted.sort((a, b) -> ProtocolNumbers.compare(number(a), number(b)));
        List<Change> out = new ArrayList<Change>();
        for (Protocol p : sorted) {
            ProtocolOverride o = number(p) == null ? null : overrides.get(number(p));
            if (o != null) out.addAll(of(p, o, labels));
        }
        return out;
    }

    /** The manual changes on one protocol. */
    public static List<Change> of(Protocol p, ProtocolOverride o, LabelConfig labels) {
        List<Change> out = new ArrayList<Change>();
        String name = p.getMetadata() == null ? null : p.getMetadata().getName();
        if (o.isExcluded()) out.add(new Change(p, name, "Exam", "In the book", "Yes", "Left out"));
        add(out, p, "Exam", "Title", name, o.getTitle());
        add(out, p, "Exam", "Scanning notes", null, o.getNotes());
        add(out, p, "Exam", "Contrast volume (mL)", ScannerValues.contrastVolume(p), o.getContrastVolume());
        add(out, p, "Exam", "Injection rate (mL/s)", ScannerValues.contrastRate(p), o.getContrastRate());
        add(out, p, "Exam", "Contrast delay (s)", ScannerValues.contrastDelay(p), o.getContrastDelay());
        add(out, p, "Exam", "Sends to", null, o.getSendDestination());
        add(out, p, "Exam", "Scan range", null, o.getScanRange());
        add(out, p, "Exam", "Scan range sheet", null, o.getReferenceSheet());
        add(out, p, "Exam", "Exam CTDIvol (mGy)", ScannerValues.examCtdi(p), o.getExamCtdi());
        add(out, p, "Exam", "Exam DLP (mGy·cm)", ScannerValues.examDlp(p), o.getExamDlp());
        if (o.getThreeD() != null) out.add(new Change(p, name, "Exam", "3D MIP / VR", "Automatic", o.getThreeD() ? "Always" : "Never"));

        for (Map.Entry<String, Map<String, String>> e : o.getSeries().entrySet()) {
            Series s = series(p, e.getKey());
            for (String field : ProtocolOverride.SERIES_FIELDS) {
                String typed = e.getValue().get(field);
                add(out, p, "Series " + e.getKey(), label(field), s == null ? null : ScannerValues.seriesField(s, field), typed);
            }
        }
        for (Map.Entry<String, Map<String, String>> e : o.getRecons().entrySet()) {
            Reconstruction r = recon(p, e.getKey());
            for (String field : ProtocolOverride.RECON_FIELDS) {
                String typed = e.getValue().get(field);
                add(out, p, "Recon " + e.getKey(), label(field), r == null ? null : ScannerValues.reconField(r, field, labels), typed);
            }
        }
        for (Map.Entry<String, String> e : o.getReconSendDestinations().entrySet()) {
            Map<String, String> typedInRecons = o.getRecons().get(e.getKey());
            if (typedInRecons != null && notBlank(typedInRecons.get("sendTo"))) continue; // already listed above
            Reconstruction r = recon(p, e.getKey());
            add(out, p, "Recon " + e.getKey(), label("sendTo"), r == null ? null : ScannerValues.reconField(r, "sendTo", labels), e.getValue());
        }
        for (ProtocolOverride.ScanRangePicture pic : o.getScanRangePictures()) {
            List<String> boxLabels = new ArrayList<String>();
            for (ProtocolOverride.Box b : pic.getBoxes()) if (b.getLabel() != null && !b.getLabel().trim().isEmpty()) boxLabels.add(b.getLabel().trim());
            int n = pic.getBoxes().size();
            add(out, p, "Exam", "Scan range picture", null, pic.getImage() + " \u2014 " + n + " box" + (n == 1 ? "" : "es")
                    + (boxLabels.isEmpty() ? "" : ": " + String.join(", ", boxLabels)));
        }
        for (ProtocolOverride.AddedField f : o.getAddedFields()) {
            if (f.isBlank()) continue;
            add(out, p, f.isExam() ? "Exam" : "Series " + f.getSeries().trim(), "Added: " + (f.getTitle() == null ? "" : f.getTitle().trim()), null, f.getValue());
        }
        return out;
    }

    private static void add(List<Change> out, Protocol p, String where, String setting, String scanner, String typed) {
        if (!notBlank(typed)) return;
        out.add(new Change(p, p.getMetadata() == null ? null : p.getMetadata().getName(), where, setting, scanner, typed.trim()));
    }

    private static Series series(Protocol p, String number) {
        for (Series s : p.getSeries()) if (String.valueOf(s.getNumber()).equals(number.trim())) return s;
        return null;
    }

    // Matched the way the book matches them: ignoring case and repeated spaces.
    private static Reconstruction recon(Protocol p, String name) {
        for (Series s : p.getSeries()) for (Group g : s.getGroups()) for (Reconstruction r : g.getReconstructions())
            if (normalize(r.getName()).equals(normalize(name))) return r;
        return null;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private static String number(Protocol p) { return p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber(); }
}
