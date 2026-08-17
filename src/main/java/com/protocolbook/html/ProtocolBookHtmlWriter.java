package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.*;
import com.protocolbook.overrides.ProtocolOverride;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Renders the parsed protocols as a single browsable HTML page, grouped into collapsed-by-default
 * sections by reading category (Neuro/Body/MSK/Other, guessed from body part - see
 * {@link LabelConfig#category}). Protocols flagged excluded in the overrides are left out
 * entirely; protocols with manual scanning notes show them inline.
 * Shares its base look (fonts, tables, print rules) with the other generated pages via
 * {@link HtmlSupport} - style changes made there apply everywhere automatically.
 */
public class ProtocolBookHtmlWriter {
    // Fixed reading order; any custom category from category-labels.json sorts alphabetically after these.
    private static final List<String> CATEGORY_ORDER = Arrays.asList("Neuro", "Body", "MSK", "Other");

    public File write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels, File outFile) throws IOException {
        Map<String, List<Protocol>> groups = new LinkedHashMap<String, List<Protocol>>();
        for (Protocol p : protocols) {
            if (isExcluded(p, overrides)) continue;
            String category = labels.category(p.getMetadata() == null ? null : p.getMetadata().getBodyPart());
            groups.computeIfAbsent(category, k -> new ArrayList<Protocol>()).add(p);
        }
        for (List<Protocol> group : groups.values()) group.sort(Comparator.comparingDouble(this::sortKey));

        List<String> categories = new ArrayList<String>(groups.keySet());
        categories.sort(Comparator.comparingInt(this::categoryRank).thenComparing(Comparator.naturalOrder()));

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Protocol Book</title>\n");
        html.append("<style>").append(CSS).append("</style>\n</head>\n<body>\n<h1>Protocol Book</h1>\n");
        for (String category : categories) {
            List<Protocol> group = groups.get(category);
            html.append("<details class=\"group\">\n<summary>").append(HtmlSupport.esc(category))
                    .append(" (").append(group.size()).append(")</summary>\n");
            for (Protocol p : group) appendProtocol(html, p, overrides, labels);
            html.append("</details>\n");
        }
        html.append("</body>\n</html>\n");

        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    private int categoryRank(String category) {
        int idx = CATEGORY_ORDER.indexOf(category);
        return idx >= 0 ? idx : CATEGORY_ORDER.size();
    }

    private void appendProtocol(StringBuilder html, Protocol p, Map<String, ProtocolOverride> overrides, LabelConfig labels) {
        Metadata m = p.getMetadata();
        String number = m == null ? null : m.getProtocolNumber();
        html.append("<div class=\"protocol\">\n<h2>").append(HtmlSupport.esc(number)).append(" &mdash; ").append(HtmlSupport.esc(m == null ? null : m.getName())).append("</h2>\n");
        html.append("<p class=\"meta\">").append(HtmlSupport.esc(m == null ? null : m.getPatientType())).append(" &middot; ")
                .append(HtmlSupport.esc(m == null ? null : m.getBodyPart())).append("</p>\n");

        ProtocolOverride override = overrides.get(number);
        if (override != null && override.getNotes() != null && !override.getNotes().trim().isEmpty()) {
            html.append("<div class=\"notes\"><strong>Scanning notes:</strong> ").append(HtmlSupport.esc(override.getNotes())).append("</div>\n");
        }

        if (p.getDose() != null && (p.getDose().getCtdi() != null || p.getDose().getDlp() != null)) {
            html.append("<p class=\"dose\">Exam CTDIvol: ").append(HtmlSupport.esc(p.getDose().getCtdi())).append(" mGy &middot; DLP: ")
                    .append(HtmlSupport.esc(p.getDose().getDlp())).append(" mGy&middot;cm</p>\n");
        }

        for (Series s : p.getSeries()) appendSeries(html, s, labels);

        if (!p.getNotes().isEmpty()) {
            html.append("<p class=\"notes\">Notes: ").append(HtmlSupport.esc(String.join("; ", p.getNotes()))).append("</p>\n");
        }
        html.append("</div>\n");
    }

    private void appendSeries(StringBuilder html, Series s, LabelConfig labels) {
        html.append("<div class=\"series\"><h3>Series ").append(s.getNumber()).append(" &mdash; ")
                .append(HtmlSupport.esc(s.getScanType())).append(HtmlSupport.esc(s.getName() == null ? "" : ": " + s.getName())).append("</h3>\n");
        if (s.getContrast() != null && s.getContrast().isIv()) {
            html.append("<p class=\"contrast\">IV contrast: ").append(HtmlSupport.esc(s.getContrast().getIvVolume())).append(" mL");
            if (s.getContrast().getFlowRate() != null) html.append(" @ ").append(HtmlSupport.esc(s.getContrast().getFlowRate())).append(" mL/s");
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
            html.append("<tr><td>").append(HtmlSupport.esc(labels.plane(plane))).append("</td><td>").append(HtmlSupport.esc(a.getKv()))
                    .append("</td><td>").append(HtmlSupport.esc(a.getMa())).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private void appendGroup(StringBuilder html, Group g, LabelConfig labels) {
        Acquisition a = g.getAcquisition();
        boolean autoMa = a.getMaMode() != null && a.getMinMa() != null && a.getMaxMa() != null;
        html.append("<p class=\"acquisition\">").append(HtmlSupport.esc(a.getKv())).append(" kV &middot; ")
                .append(autoMa ? HtmlSupport.esc(a.getMinMa()) + "-" + HtmlSupport.esc(a.getMaxMa()) : HtmlSupport.esc(a.getMa())).append(" mA");
        if (a.getNoiseIndex() != null) html.append(" (NI ").append(HtmlSupport.esc(a.getNoiseIndex())).append(")");
        if (a.getPitch() != null) html.append(" &middot; pitch ").append(HtmlSupport.esc(a.getPitch()));
        if (a.getRotationTime() != null) html.append(" &middot; ").append(HtmlSupport.esc(a.getRotationTime())).append(" s rotation");
        if (g.getDose() != null && g.getDose().getCtdi() != null) html.append(" &middot; CTDIvol ").append(HtmlSupport.esc(g.getDose().getCtdi())).append(" mGy");
        html.append("</p>\n<table class=\"recons\">\n<tr><th>Recon</th><th>Thickness</th><th>Interval</th><th>Kernel</th></tr>\n");
        for (Reconstruction r : g.getReconstructions()) {
            html.append("<tr").append(r.isDerived() ? " class=\"reformat\"" : "").append("><td>").append(HtmlSupport.esc(r.getName()))
                    .append("</td><td>").append(HtmlSupport.esc(r.getThickness()))
                    .append("</td><td>").append(HtmlSupport.esc(r.getInterval())).append("</td><td>").append(HtmlSupport.esc(labels.kernel(r.getKernel()))).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private boolean isExcluded(Protocol p, Map<String, ProtocolOverride> overrides) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        ProtocolOverride o = overrides.get(number);
        return o != null && o.isExcluded();
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

    private static final String CSS = HtmlSupport.BASE_CSS +
            "details.group{margin-bottom:1rem;border:1px solid #ccc;border-radius:6px;padding:.5rem 1rem;}" +
            "details.group>summary{font-size:1.2rem;font-weight:bold;cursor:pointer;}" +
            ".protocol{border-top:1px solid #ddd;margin-top:1rem;padding-top:1rem;}" +
            ".meta,.dose{color:#555;font-size:.9rem;}" +
            ".notes{background:#fff8e1;border:1px solid #e0c060;border-radius:4px;padding:.5rem;margin:.5rem 0;}" +
            ".series{margin:.75rem 0 .75rem 1rem;}" +
            "table.recons tr.reformat td:first-child{padding-left:1.5rem;color:#555;}";
}
