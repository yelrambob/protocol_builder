package com.protocolbook.gui;

import com.protocolbook.html.ScannerValues;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One protocol boiled down to a line of a section screen: the handful of settings worth a glance,
 * each as the book will show it (typed values win), plus anything that looks off. Kept apart from
 * the Swing code so it can be tested.
 */
final class SectionRow {
    /** One value and whether it was set by hand. */
    static final class Cell {
        final String text;
        final boolean edited;

        Cell(String text, boolean edited) {
            this.text = text == null ? "" : text;
            this.edited = edited;
        }

        @Override public String toString() { return text; }
    }

    final Protocol protocol;
    final String number;
    final Cell name, kv, ma, pitch, contrast, delay, recon, ctdi, extras;
    final List<String> warnings = new ArrayList<String>();

    private SectionRow(Protocol p, ProtocolOverride o, String scannerName, LabelConfig labels) {
        protocol = p;
        number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        String title = o == null ? null : blankToNull(o.getTitle());
        name = new Cell(title != null ? title : scannerName, title != null);
        kv = seriesCell(p, o, "kv");
        ma = seriesCell(p, o, "ma");
        pitch = seriesCell(p, o, "pitch");

        Cell volume = typedOr(o == null ? null : o.getContrastVolume(), ScannerValues.contrastVolume(p));
        Cell rate = typedOr(o == null ? null : o.getContrastRate(), ScannerValues.contrastRate(p));
        Cell delaySeconds = typedOr(o == null ? null : o.getContrastDelay(), ScannerValues.contrastDelay(p));
        boolean iv = ScannerValues.firstContrastSeries(p) != null;
        if (iv || volume.edited || rate.edited) {
            contrast = new Cell((volume.text.isEmpty() ? "?" : volume.text) + " @ " + (rate.text.isEmpty() ? "?" : rate.text),
                    volume.edited || rate.edited);
            delay = new Cell(delaySeconds.text.isEmpty() ? "" : delaySeconds.text + " s", delaySeconds.edited);
        } else {
            contrast = new Cell("None", false);
            delay = new Cell("", false);
        }

        Reconstruction first = firstRecon(p);
        if (first == null) recon = new Cell("", false);
        else {
            String thickness = reconValue(first, o, "thickness", labels), kernel = reconValue(first, o, "kernel", labels);
            boolean edited = o != null && (o.reconField(first, "thickness") != null || o.reconField(first, "kernel") != null);
            recon = new Cell((thickness == null ? "" : thickness + " mm ") + (kernel == null ? "" : kernel), edited);
        }
        Cell examCtdi = typedOr(o == null ? null : o.getExamCtdi(), ScannerValues.examCtdi(p));
        ctdi = examCtdi;

        List<String> bits = new ArrayList<String>();
        if (o != null && blankToNull(o.getNotes()) != null) bits.add("Notes");
        int added = o == null ? 0 : o.getAddedFields().size();
        if (added > 0) bits.add("+" + added + " field" + (added == 1 ? "" : "s"));
        extras = new Cell(String.join(", ", bits), !bits.isEmpty());

        checkContrast(p, iv, volume, rate);
    }

    /** Rows for one section's protocols, with each kV compared against what most of the section uses. */
    static List<SectionRow> forSection(List<Protocol> protocols, Session session) {
        return forSection(protocols, session.overrides, session.labels);
    }

    static List<SectionRow> forSection(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels) {
        List<SectionRow> rows = new ArrayList<SectionRow>();
        for (Protocol p : protocols) {
            String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
            rows.add(new SectionRow(p, number == null ? null : overrides.get(number), scannerName(p), labels));
        }
        String usual = mostCommon(rows);
        if (usual != null)
            for (SectionRow r : rows)
                if (!r.kv.text.isEmpty() && !r.kv.text.equals(usual)) r.warnings.add("kV " + r.kv.text + " (most in this section use " + usual + ")");
        return rows;
    }

    // A kV most of the section agrees on (at least 3 protocols, more than half) - otherwise nothing to compare against.
    private static String mostCommon(List<SectionRow> rows) {
        Map<String, Integer> counts = new HashMap<String, Integer>();
        int total = 0;
        for (SectionRow r : rows) {
            if (r.kv.text.isEmpty()) continue;
            counts.merge(r.kv.text, 1, Integer::sum);
            total++;
        }
        if (total < 3) return null;
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() * 2 > total) return e.getKey();
        return null;
    }

    private static final Pattern SAYS_WITH = Pattern.compile("(?i)(\\bW/(?!O)|\\bWITH\\b|\\bW\\b(?![/ ]*O\\b)|\\bCONTRAST\\b|\\bCTA\\b|\\bANGIO)");
    private static final Pattern SAYS_WITHOUT = Pattern.compile("(?i)(\\bW/?O\\b|\\bWITHOUT\\b|\\bNON[- ]?CON|\\bNO CONTRAST\\b)");

    private void checkContrast(Protocol p, boolean iv, Cell volume, Cell rate) {
        String scannerName = scannerName(p);
        String n = scannerName == null ? "" : scannerName;
        boolean saysWith = SAYS_WITH.matcher(n).find(), saysWithout = SAYS_WITHOUT.matcher(n).find();
        if (saysWith && !saysWithout && !iv) warnings.add("Name says contrast, but no IV contrast is set");
        if (saysWithout && !saysWith && iv) warnings.add("Name says without contrast, but IV contrast is set");
        if (iv && volume.text.isEmpty()) warnings.add("No contrast volume");
        if (iv && rate.text.isEmpty()) warnings.add("No injection rate");
    }

    // Distinct values across the diagnostic (non-scout) series, typed ones winning: "120" or "120 / 100".
    private static Cell seriesCell(Protocol p, ProtocolOverride o, String field) {
        Set<String> values = new LinkedHashSet<String>();
        boolean edited = false;
        for (Series s : p.getSeries()) {
            if (ScannerValues.isScout(s) || s.getGroups().isEmpty()) continue;
            String typed = o == null ? null : o.seriesField(s.getNumber(), field);
            if (typed != null) edited = true;
            String v = typed != null ? typed : ScannerValues.seriesField(s, field);
            if (v != null && !v.trim().isEmpty()) values.add(v.trim());
        }
        return new Cell(String.join(" / ", values), edited);
    }

    private static Reconstruction firstRecon(Protocol p) {
        for (Series s : p.getSeries()) {
            if (ScannerValues.isScout(s)) continue;
            for (Group g : s.getGroups()) if (!g.getReconstructions().isEmpty()) return g.getReconstructions().get(0);
        }
        return null;
    }

    private static String reconValue(Reconstruction r, ProtocolOverride o, String field, LabelConfig labels) {
        String typed = o == null ? null : o.reconField(r, field);
        return typed != null ? typed : ScannerValues.reconField(r, field, labels);
    }

    private static Cell typedOr(String typed, String scanner) {
        String t = blankToNull(typed);
        return t != null ? new Cell(t, true) : new Cell(scanner, false);
    }

    private static String scannerName(Protocol p) {
        return p.getMetadata() == null ? null : p.getMetadata().getName();
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    @Override public String toString() {
        return String.format(Locale.ROOT, "%s %s kV=%s warnings=%s", number, name, kv, warnings);
    }
}
