package com.protocolbook.io;

import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.html.ProtocolNumbers;
import com.protocolbook.html.ScannerValues;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Group;
import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A spreadsheet of chosen parameters across every protocol - e.g. the ASIR level of every recon, to
 * see at a glance where it's inconsistent. One row per recon when any recon setting is chosen,
 * otherwise one per series, otherwise one per protocol. Values are what the book shows (anything typed
 * in protocol-overrides.json wins). Written as UTF-8 with a byte-order mark so Excel opens it cleanly.
 */
public final class ParameterCsvWriter {
    public enum Level { PROTOCOL, SERIES, RECON }

    /** One column that can be chosen. */
    public static final class Column {
        public final String id, label;
        public final Level level;

        Column(String id, String label, Level level) {
            this.id = id;
            this.label = label;
            this.level = level;
        }

        @Override public String toString() { return label; }
    }

    /** Every column that can be chosen, in the order they're written. */
    public static final List<Column> COLUMNS = Collections.unmodifiableList(Arrays.asList(
            new Column("scannerName", "Scanner name", Level.PROTOCOL),
            new Column("contrastVolume", "Contrast volume (mL)", Level.PROTOCOL),
            new Column("contrastRate", "Injection rate (mL/s)", Level.PROTOCOL),
            new Column("contrastDelay", "Contrast delay (s)", Level.PROTOCOL),
            new Column("examCtdi", "Exam CTDIvol (mGy)", Level.PROTOCOL),
            new Column("examDlp", "Exam DLP (mGy·cm)", Level.PROTOCOL),
            new Column("notes", "Scanning notes", Level.PROTOCOL),
            new Column("scanType", "Scan type", Level.SERIES),
            new Column("kv", "kV", Level.SERIES),
            new Column("ma", "mA / mAs", Level.SERIES),
            new Column("qualityRefMas", "Quality ref. mAs", Level.SERIES),
            new Column("doseModulation", "Dose modulation", Level.SERIES),
            new Column("noiseIndex", "Noise index", Level.SERIES),
            new Column("pitch", "Pitch", Level.SERIES),
            new Column("rotationTime", "Rotation (s)", Level.SERIES),
            new Column("collimation", "Collimation", Level.SERIES),
            new Column("ctdi", "CTDIvol (mGy)", Level.SERIES),
            new Column("thickness", "Thickness", Level.RECON),
            new Column("interval", "Interval", Level.RECON),
            new Column("kernel", "Kernel", Level.RECON),
            new Column("asir", "ASIR / iterative", Level.RECON),
            new Column("wwwl", "WW/WL", Level.RECON),
            new Column("sendTo", "Sends to", Level.RECON)));

    public static Column column(String id) {
        for (Column c : COLUMNS) if (c.id.equalsIgnoreCase(id.trim())) return c;
        throw new IllegalArgumentException("Unknown CSV column '" + id + "' - use any of " + ids());
    }

    public static List<String> ids() {
        List<String> out = new ArrayList<String>();
        for (Column c : COLUMNS) out.add(c.id);
        return out;
    }

    /**
     * Writes the chosen columns (in COLUMNS order) for every protocol in the book, plus the ones left out
     * of it when includeLeftOut is true. Returns the number of data rows written.
     */
    public static int write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels,
                            List<Column> chosen, boolean includeLeftOut, File out) throws IOException {
        List<Column> columns = new ArrayList<Column>();
        for (Column c : COLUMNS) if (chosen.contains(c)) columns.add(c);
        Level level = Level.PROTOCOL;
        for (Column c : columns) if (c.level.ordinal() > level.ordinal()) level = c.level;

        ProtocolBookHtmlWriter book = new ProtocolBookHtmlWriter();
        List<String> header = new ArrayList<String>(Arrays.asList("Section", "#", "Protocol", "In book"));
        if (level != Level.PROTOCOL) header.add("Series");
        if (level == Level.RECON) header.add("Recon");
        for (Column c : columns) header.add(c.label);

        List<List<String>> rows = new ArrayList<List<String>>();
        // book order and sections, with nothing left out yet; then drop the left-out ones unless asked for
        Map<String, Map<Integer, List<Protocol>>> tree = book.tree(protocols, Collections.<String, ProtocolOverride>emptyMap(), labels);
        for (Map.Entry<String, Map<Integer, List<Protocol>>> bucket : tree.entrySet())
            for (Map.Entry<Integer, List<Protocol>> group : bucket.getValue().entrySet())
                for (Protocol p : group.getValue()) {
                    ProtocolOverride o = overrides.get(key(p));
                    boolean in = o == null || !o.isExcluded();
                    if (!in && !includeLeftOut) continue;
                    List<String> base = new ArrayList<String>(Arrays.asList(bucket.getKey() + " - " + labels.categoryForNumber(group.getKey()),
                            ProtocolNumbers.displayNumber(p), book.displayName(p, overrides), in ? "Yes" : "Left out"));
                    if (level == Level.PROTOCOL) {
                        rows.add(row(base, columns, p, o, null, null, labels));
                        continue;
                    }
                    for (Series s : p.getSeries()) {
                        List<String> seriesBase = new ArrayList<String>(base);
                        seriesBase.add(s.getNumber() + (s.getName() == null ? "" : " - " + s.getName()));
                        if (level == Level.SERIES) {
                            rows.add(row(seriesBase, columns, p, o, s, null, labels));
                            continue;
                        }
                        for (Group g : s.getGroups())
                            for (Reconstruction r : g.getReconstructions()) {
                                List<String> reconBase = new ArrayList<String>(seriesBase);
                                reconBase.add(r.getName());
                                rows.add(row(reconBase, columns, p, o, s, r, labels));
                            }
                    }
                }

        if (out.getAbsoluteFile().getParentFile() != null) out.getAbsoluteFile().getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            w.write('﻿');
            w.write(line(header));
            for (List<String> r : rows) w.write(line(r));
        }
        return rows.size();
    }

    private static List<String> row(List<String> base, List<Column> columns, Protocol p, ProtocolOverride o, Series s, Reconstruction r, LabelConfig labels) {
        List<String> out = new ArrayList<String>(base);
        for (Column c : columns) {
            String v;
            if (c.level == Level.PROTOCOL) v = protocolValue(c.id, p, o);
            else if (c.level == Level.SERIES) v = s == null ? "" : seriesValue(c.id, s, o);
            else v = r == null ? "" : reconValue(c.id, r, o, labels);
            out.add(v == null ? "" : v);
        }
        return out;
    }

    private static String protocolValue(String id, Protocol p, ProtocolOverride o) {
        switch (id) {
            case "scannerName": return p.getMetadata() == null ? null : p.getMetadata().getName();
            case "contrastVolume": return typedOr(o == null ? null : o.getContrastVolume(), ScannerValues.contrastVolume(p));
            case "contrastRate": return typedOr(o == null ? null : o.getContrastRate(), ScannerValues.contrastRate(p));
            case "contrastDelay": return typedOr(o == null ? null : o.getContrastDelay(), ScannerValues.contrastDelay(p));
            case "examCtdi": return typedOr(o == null ? null : o.getExamCtdi(), ScannerValues.examCtdi(p));
            case "examDlp": return typedOr(o == null ? null : o.getExamDlp(), ScannerValues.examDlp(p));
            case "notes": return o == null ? null : o.getNotes();
            default: return null;
        }
    }

    private static String seriesValue(String id, Series s, ProtocolOverride o) {
        switch (id) {
            case "scanType": return s.getScanType();
            case "qualityRefMas": return s.getGroups().isEmpty() ? null : s.getGroups().get(0).getAcquisition().getQualityRefMas();
            case "doseModulation": return s.getGroups().isEmpty() ? null : s.getGroups().get(0).getAcquisition().getDoseModulation();
            case "collimation": return s.getGroups().isEmpty() ? null : s.getGroups().get(0).getAcquisition().getDetector();
            default: {
                String typed = o == null ? null : o.seriesField(s.getNumber(), id);
                return typed != null ? typed : ScannerValues.seriesField(s, id);
            }
        }
    }

    private static String reconValue(String id, Reconstruction r, ProtocolOverride o, LabelConfig labels) {
        if ("sendTo".equals(id)) return String.join(", ", o == null ? r.getSendDestinations() : o.sendDestinationsFor(r));
        String typed = o == null ? null : o.reconField(r, id);
        return typed != null ? typed : ScannerValues.reconField(r, id, labels);
    }

    private static String typedOr(String typed, String scanner) {
        return typed != null && !typed.trim().isEmpty() ? typed.trim() : scanner;
    }

    private static String key(Protocol p) {
        Metadata m = p.getMetadata();
        return m == null ? null : m.getProtocolNumber();
    }

    static String line(List<String> cells) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) b.append(',');
            String c = cells.get(i) == null ? "" : cells.get(i);
            if (c.contains(",") || c.contains("\"") || c.contains("\n") || c.contains("\r")) c = "\"" + c.replace("\"", "\"\"") + "\"";
            b.append(c);
        }
        return b.append("\r\n").toString();
    }

    /** Columns by level, for a chooser. */
    public static Map<Level, List<Column>> byLevel() {
        Map<Level, List<Column>> out = new LinkedHashMap<Level, List<Column>>();
        for (Column c : COLUMNS) out.computeIfAbsent(c.level, k -> new ArrayList<Column>()).add(c);
        return out;
    }
}
