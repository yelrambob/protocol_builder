package com.protocolbook.html;

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

    public File write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, File outFile) throws IOException {
        Map<Integer, List<Protocol>> groups = new TreeMap<Integer, List<Protocol>>();
        for (Protocol p : protocols) {
            if (isExcluded(p, overrides)) continue;
            groups.computeIfAbsent(groupKey(p), k -> new ArrayList<Protocol>()).add(p);
        }
        for (List<Protocol> group : groups.values()) group.sort(Comparator.comparingDouble(this::sortKey));

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Protocol Book</title>\n");
        html.append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<h1>Protocol Book</h1>\n");
        for (Map.Entry<Integer, List<Protocol>> group : groups.entrySet()) {
            html.append("<details class=\"group\" open>\n<summary>").append(esc(groupLabel(group.getKey(), group.getValue())))
                    .append(" (").append(group.getValue().size()).append(")</summary>\n");
            for (Protocol p : group.getValue()) appendProtocol(html, p, overrides);
            html.append("</details>\n");
        }
        html.append("</body>\n</html>\n");

        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    private void appendProtocol(StringBuilder html, Protocol p, Map<String, ProtocolOverride> overrides) {
        Metadata m = p.getMetadata();
        String number = m == null ? null : m.getProtocolNumber();
        html.append("<div class=\"protocol\">\n<h2>").append(esc(number)).append(" &mdash; ").append(esc(m == null ? null : m.getName())).append("</h2>\n");
        html.append("<p class=\"meta\">").append(esc(m == null ? null : m.getPatientType())).append(" &middot; ")
                .append(esc(m == null ? null : m.getBodyPart())).append("</p>\n");

        ProtocolOverride override = overrides.get(number);
        if (override != null && override.getNotes() != null && !override.getNotes().trim().isEmpty()) {
            html.append("<div class=\"notes\"><strong>Scanning notes:</strong> ").append(esc(override.getNotes())).append("</div>\n");
        }

        if (p.getDose() != null && (p.getDose().getCtdi() != null || p.getDose().getDlp() != null)) {
            html.append("<p class=\"dose\">Exam CTDIvol: ").append(esc(p.getDose().getCtdi())).append(" mGy &middot; DLP: ")
                    .append(esc(p.getDose().getDlp())).append(" mGy&middot;cm</p>\n");
        }

        for (Series s : p.getSeries()) appendSeries(html, s);

        if (!p.getNotes().isEmpty()) {
            html.append("<p class=\"notes\">Notes: ").append(esc(String.join("; ", p.getNotes()))).append("</p>\n");
        }
        html.append("</div>\n");
    }

    private void appendSeries(StringBuilder html, Series s) {
        html.append("<div class=\"series\"><h3>Series ").append(s.getNumber()).append(" &mdash; ")
                .append(esc(s.getScanType())).append(esc(s.getName() == null ? "" : ": " + s.getName())).append("</h3>\n");
        if (s.getContrast() != null && s.getContrast().isIv()) {
            html.append("<p class=\"contrast\">IV contrast: ").append(esc(s.getContrast().getIvVolume())).append(" mL");
            if (s.getContrast().getFlowRate() != null) html.append(" @ ").append(esc(s.getContrast().getFlowRate())).append(" mL/s");
            html.append("</p>\n");
        }
        if (isScout(s)) appendScoutSummary(html, s);
        else for (Group g : s.getGroups()) appendGroup(html, g);
        html.append("</div>\n");
    }

    private boolean isScout(Series s) {
        return s.getScanType() != null && s.getScanType().equalsIgnoreCase("Scout");
    }

    // Scouts are localizer images, not diagnostic reconstructions - one line per plane beats a full table per group.
    private void appendScoutSummary(StringBuilder html, Series s) {
        List<String> planes = new ArrayList<>();
        for (Group g : s.getGroups()) for (Reconstruction r : g.getReconstructions()) planes.add(r.getPlane() != null ? r.getPlane() + "°" : "?");
        html.append("<p class=\"acquisition\">Scout planes: ").append(esc(String.join(", ", planes))).append("</p>\n");
    }

    private void appendGroup(StringBuilder html, Group g) {
        Acquisition a = g.getAcquisition();
        html.append("<p class=\"acquisition\">").append(esc(a.getKv())).append(" kV &middot; ")
                .append(esc(a.getMa() != null ? a.getMa() : a.getMinMa() + "-" + a.getMaxMa())).append(" mA");
        if (a.getPitch() != null) html.append(" &middot; pitch ").append(esc(a.getPitch()));
        if (a.getRotationTime() != null) html.append(" &middot; ").append(esc(a.getRotationTime())).append(" s rotation");
        if (g.getDose() != null && g.getDose().getCtdi() != null) html.append(" &middot; CTDIvol ").append(esc(g.getDose().getCtdi())).append(" mGy");
        html.append("</p>\n<table class=\"recons\">\n<tr><th>Recon</th><th>Thickness</th><th>Interval</th><th>Kernel</th></tr>\n");
        for (Reconstruction r : g.getReconstructions()) {
            html.append("<tr").append(r.isDerived() ? " class=\"reformat\"" : "").append("><td>").append(esc(r.getName()))
                    .append("</td><td>").append(esc(r.getThickness()))
                    .append("</td><td>").append(esc(r.getInterval())).append("</td><td>").append(esc(r.getKernel())).append("</td></tr>\n");
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
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Protocol p : group) {
            String bodyPart = p.getMetadata() == null ? null : p.getMetadata().getBodyPart();
            if (bodyPart == null || bodyPart.trim().isEmpty()) continue;
            counts.merge(bodyPart, 1, Integer::sum);
        }
        String best = null; int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) if (e.getValue() > bestCount) { best = e.getKey(); bestCount = e.getValue(); }
        String label = best != null ? best : ("Protocol " + key);
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private String esc(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CSS =
            "body{font-family:sans-serif;max-width:900px;margin:2rem auto;padding:0 1rem;}" +
            "details.group{margin-bottom:1rem;border:1px solid #ccc;border-radius:6px;padding:.5rem 1rem;}" +
            "details.group>summary{font-size:1.2rem;font-weight:bold;cursor:pointer;}" +
            ".protocol{border-top:1px solid #ddd;margin-top:1rem;padding-top:1rem;}" +
            ".meta,.dose{color:#555;font-size:.9rem;}" +
            ".notes{background:#fff8e1;border:1px solid #e0c060;border-radius:4px;padding:.5rem;margin:.5rem 0;}" +
            ".series{margin:.75rem 0 .75rem 1rem;}" +
            "table.recons{border-collapse:collapse;margin:.25rem 0 .75rem;}" +
            "table.recons th,table.recons td{border:1px solid #ddd;padding:.25rem .5rem;font-size:.9rem;text-align:left;}" +
            "table.recons tr.reformat td:first-child{padding-left:1.5rem;color:#555;}";
}
