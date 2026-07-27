package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.*;
import com.protocolbook.overrides.ProtocolOverride;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Renders the parsed protocols as a single browsable HTML page, grouped into collapsible
 * sections by protocol number (e.g. all "9.x" protocols together), with each section labeled
 * by the body part most common among its protocols. Protocols flagged excluded in the
 * overrides are left out entirely; protocols with manual scanning notes show them inline.
 *
 * Structure only, no visual design pass yet - plain <details>/<summary> and minimal CSS.
 */
public class ProtocolBookHtmlWriter {

    public File write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels, File outFile) throws IOException {
        Map<Integer, List<Protocol>> groups = new TreeMap<Integer, List<Protocol>>(GROUP_ORDER);
        for (Protocol p : protocols) {
            if (isExcluded(p, overrides)) continue;
            groups.computeIfAbsent(groupKey(p), k -> new ArrayList<Protocol>()).add(p);
        }
        for (List<Protocol> group : groups.values()) group.sort(Comparator.comparingDouble(this::sortKey));

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Protocol Book</title>\n");
        html.append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<h1>Protocol Book</h1>\n");
        for (Map.Entry<Integer, List<Protocol>> group : groups.entrySet()) {
            html.append("<details class=\"group\">\n<summary>").append(esc(groupLabel(group.getKey(), group.getValue())))
                    .append(" (").append(group.getValue().size()).append(")</summary>\n");
            for (Protocol p : group.getValue()) appendProtocol(html, p, overrides, labels);
            html.append("</details>\n");
        }
        html.append("</body>\n</html>\n");

        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    private void appendProtocol(StringBuilder html, Protocol p, Map<String, ProtocolOverride> overrides, LabelConfig labels) {
        Metadata m = p.getMetadata();
        String number = m == null ? null : m.getProtocolNumber();
        html.append("<details class=\"protocol\">\n<summary>").append(esc(number)).append(" &mdash; ").append(esc(m == null ? null : m.getName())).append("</summary>\n");
        html.append("<p class=\"meta\">").append(esc(m == null ? null : m.getPatientType())).append(" &middot; ")
                .append(esc(m == null ? null : m.getBodyPart())).append("</p>\n");

        ProtocolOverride override = overrides.get(number);
        if (override != null && override.getNotes() != null && !override.getNotes().trim().isEmpty()) {
            html.append("<div class=\"notes\"><strong>Scanning notes:</strong> ").append(esc(override.getNotes())).append("</div>\n");
        }
        if (override != null && override.getSendDestination() != null && !override.getSendDestination().trim().isEmpty()) {
            html.append("<p class=\"destination\">Sends to: ").append(esc(override.getSendDestination())).append("</p>\n");
        }

        if (p.getDose() != null && (p.getDose().getCtdi() != null || p.getDose().getDlp() != null)) {
            html.append("<p class=\"dose\">Exam CTDIvol: ").append(esc(p.getDose().getCtdi())).append(" mGy &middot; DLP: ")
                    .append(esc(p.getDose().getDlp())).append(" mGy&middot;cm</p>\n");
        }

        for (Series s : p.getSeries()) appendSeries(html, s, labels);

        if (!p.getNotes().isEmpty()) {
            html.append("<p class=\"notes\">Notes: ").append(esc(String.join("; ", p.getNotes()))).append("</p>\n");
        }
        html.append("</details>\n");
    }

    private void appendSeries(StringBuilder html, Series s, LabelConfig labels) {
        html.append("<div class=\"series\"><h3>Series ").append(s.getNumber()).append(" &mdash; ")
                .append(esc(s.getScanType())).append(esc(s.getName() == null ? "" : ": " + s.getName())).append("</h3>\n");
        if (s.getContrast() != null && s.getContrast().isIv()) {
            html.append("<p class=\"contrast\">IV contrast: ").append(esc(s.getContrast().getIvVolume())).append(" mL");
            if (s.getContrast().getFlowRate() != null) html.append(" @ ").append(esc(s.getContrast().getFlowRate())).append(" mL/s");
            html.append("</p>\n");
        }
        if (isScout(s)) appendScoutTable(html, s, labels);
        else for (Group g : s.getGroups()) appendGroup(html, g, labels);
        html.append("</div>\n");
    }

    private boolean isScout(Series s) {
        return s.getScanType() != null && s.getScanType().equalsIgnoreCase("Scout");
    }

    // Scouts are localizer images, not diagnostic reconstructions - one compact table beats a full acquisition block per plane.
    private void appendScoutTable(StringBuilder html, Series s, LabelConfig labels) {
        html.append("<table class=\"recons\">\n<tr><th>Plane</th><th>kV</th><th>mA</th></tr>\n");
        for (Group g : s.getGroups()) {
            Acquisition a = g.getAcquisition();
            String plane = g.getReconstructions().isEmpty() ? null : g.getReconstructions().get(0).getPlane();
            html.append("<tr><td>").append(esc(labels.plane(plane))).append("</td><td>").append(esc(a.getKv()))
                    .append("</td><td>").append(esc(a.getMa())).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private void appendGroup(StringBuilder html, Group g, LabelConfig labels) {
        Acquisition a = g.getAcquisition();
        boolean autoMa = a.getMaMode() != null && a.getMinMa() != null && a.getMaxMa() != null;
        html.append("<p class=\"acquisition\">").append(esc(a.getKv())).append(" kV &middot; ")
                .append(autoMa ? esc(a.getMinMa()) + "-" + esc(a.getMaxMa()) : esc(a.getMa())).append(" mA");
        if (a.getNoiseIndex() != null) html.append(" (NI ").append(esc(a.getNoiseIndex())).append(")");
        if (a.getPitch() != null) html.append(" &middot; pitch ").append(esc(a.getPitch()));
        if (a.getRotationTime() != null) html.append(" &middot; ").append(esc(a.getRotationTime())).append(" s rotation");
        if (a.getDetector() != null) html.append(" &middot; Detector: ").append(esc(labels.detector(a.getDetector())));
        if (g.getDose() != null && g.getDose().getCtdi() != null) html.append(" &middot; CTDIvol ").append(esc(g.getDose().getCtdi())).append(" mGy");
        html.append("</p>\n<table class=\"recons\">\n<tr><th>Recon</th><th>Thickness</th><th>Interval</th><th>Kernel</th></tr>\n");
        for (Reconstruction r : g.getReconstructions()) {
            html.append("<tr").append(r.isDerived() ? " class=\"reformat\"" : "").append("><td>").append(esc(r.getName()))
                    .append("</td><td>").append(esc(r.getThickness()))
                    .append("</td><td>").append(esc(r.getInterval())).append("</td><td>").append(esc(labels.kernel(r.getKernel()))).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private boolean isExcluded(Protocol p, Map<String, ProtocolOverride> overrides) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        ProtocolOverride o = overrides.get(number);
        return o != null && o.isExcluded();
    }

    private int groupKey(Protocol p) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        if (number == null) return Integer.MAX_VALUE;
        try { return Integer.parseInt(number.split("\\.")[0]); } catch (Exception e) { return Integer.MAX_VALUE; }
    }

    private double sortKey(Protocol p) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        if (number == null) return Double.MAX_VALUE;
        String[] parts = number.split("\\.", 2);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major + minor / 10000.0; // keeps "9.10" after "9.2" (minor compared as an integer, not a decimal)
        } catch (Exception e) { return Double.MAX_VALUE; }
    }

    private String groupLabel(int key, List<Protocol> group) {
        if (key == Integer.MAX_VALUE) return "Other";

        String label;
        if (GROUP_LABEL_OVERRIDES.containsKey(key)) {
            label = GROUP_LABEL_OVERRIDES.get(key);
        } else {
            Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
            for (Protocol p : group) {
                String bodyPart = p.getMetadata() == null ? null : p.getMetadata().getBodyPart();
                if (bodyPart == null || bodyPart.trim().isEmpty()) continue;
                counts.merge(bodyPart, 1, Integer::sum);
            }
            String best = null; int bestCount = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() > bestCount) { best = e.getKey(); bestCount = e.getValue(); }
            String raw = best != null ? best : ("Protocol " + key);
            label = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }

        if (!QA_GROUP.equals(key) && isMajorityPediatric(group)) label = "Peds " + label;
        return label;
    }

    private boolean isMajorityPediatric(List<Protocol> group) {
        int pediatric = 0, other = 0;
        for (Protocol p : group) {
            String patientType = p.getMetadata() == null ? null : p.getMetadata().getPatientType();
            if (patientType != null && patientType.trim().equalsIgnoreCase("pediatric")) pediatric++;
            else other++;
        }
        return pediatric > other;
    }

    // Body-part majority vote mislabels some groups - override those by hand:
    // 2.x and 12.x mix "head"/"orbit" metadata but are really the facial bones/sinus/orbit family,
    // and 10.x is QA/phantom protocols with no meaningful body part at all.
    private static final Integer QA_GROUP = 10;
    private static final Map<Integer, String> GROUP_LABEL_OVERRIDES = new HashMap<Integer, String>() {{
        put(2, "Face");
        put(12, "Face");
        put(QA_GROUP, "Miscellaneous QA");
    }};

    // Numbered groups sort in order, except QA/phantom protocols (10.x) which belong at the very
    // bottom of the book instead of wedged between "Lower Extremities" and the pediatric groups.
    private static final Comparator<Integer> GROUP_ORDER = Comparator.comparingInt(
            key -> QA_GROUP.equals(key) ? Integer.MAX_VALUE - 1 : key);

    private String esc(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CSS =
            ":root{" +
            "--bg:#0f1115;--card:#171a21;--border:#2a2e37;--text:#e7e9ec;--text-muted:#9aa1ac;" +
            "--accent:#5db1ff;--accent-2:#6fd7c4;--notes-bg:rgba(224,176,64,.14);--notes-border:#c9a227;" +
            "--notes-text:#e8d9ad;--row-alt:rgba(255,255,255,.03);--shadow:rgba(0,0,0,.45);}" +
            "@media (prefers-color-scheme:light){:root{" +
            "--bg:#f6f7f9;--card:#ffffff;--border:#e1e4e9;--text:#1b1e24;--text-muted:#5b6270;" +
            "--accent:#2563eb;--accent-2:#0d9488;--notes-bg:#fff8e1;--notes-border:#e0c060;" +
            "--notes-text:#6b5410;--row-alt:rgba(0,0,0,.025);--shadow:rgba(0,0,0,.08);}}" +
            "*{box-sizing:border-box;}" +
            "body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;" +
            "background:var(--bg);color:var(--text);max-width:960px;margin:2rem auto;padding:0 1.25rem 4rem;" +
            "line-height:1.45;-webkit-font-smoothing:antialiased;}" +
            "h1{font-size:1.6rem;font-weight:700;letter-spacing:-.01em;margin:0 0 1.5rem;}" +
            "details.group{margin-bottom:1rem;background:var(--card);border:1px solid var(--border);" +
            "border-radius:10px;padding:.15rem 1.25rem .9rem;box-shadow:0 1px 3px var(--shadow);}" +
            "details.group>summary{font-size:1.1rem;font-weight:600;cursor:pointer;padding:.85rem 0;" +
            "list-style:revert;color:var(--text);}" +
            "details.group>summary::marker{color:var(--accent);}" +
            "details.group>summary:hover{color:var(--accent);}" +
            "details.protocol{border-left:2px solid var(--border);margin:.75rem 0 0 .5rem;padding:0 0 .1rem .9rem;}" +
            "details.protocol:first-of-type{margin-top:.25rem;}" +
            "details.protocol[open]{padding-bottom:.75rem;}" +
            "details.protocol>summary{font-size:1.02rem;font-weight:600;cursor:pointer;padding:.5rem 0;" +
            "list-style:revert;color:var(--text);}" +
            "details.protocol>summary::marker{color:var(--accent-2);}" +
            "details.protocol>summary:hover{color:var(--accent);}" +
            ".meta,.dose,.destination{color:var(--text-muted);font-size:.85rem;margin:0 0 .35rem;}" +
            ".notes{background:var(--notes-bg);border:1px solid var(--notes-border);color:var(--notes-text);" +
            "border-radius:6px;padding:.5rem .65rem;margin:.5rem 0;font-size:.9rem;}" +
            ".series{margin:.85rem 0 .85rem 1rem;padding-left:.85rem;border-left:2px solid var(--border);}" +
            ".series h3{font-size:.92rem;font-weight:600;margin:0 0 .3rem;color:var(--text);}" +
            ".contrast{color:var(--accent-2);font-size:.85rem;margin:0 0 .3rem;}" +
            ".acquisition{font-size:.9rem;margin:0 0 .35rem;}" +
            "table.recons{width:100%;border-collapse:collapse;margin:.2rem 0 .75rem;font-size:.85rem;}" +
            "table.recons th{text-align:left;color:var(--text-muted);font-weight:600;padding:.35rem .6rem;" +
            "border-bottom:1px solid var(--border);}" +
            "table.recons td{padding:.35rem .6rem;border-bottom:1px solid var(--border);}" +
            "table.recons tr:last-child td{border-bottom:none;}" +
            "table.recons tr:nth-child(even){background:var(--row-alt);}" +
            "table.recons tr.reformat td:first-child{padding-left:1.5rem;color:var(--text-muted);font-style:italic;}";
}
