package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.*;
import com.protocolbook.overrides.ProtocolOverride;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Renders the parsed protocols as a single self-contained HTML app: a hover-expandable sidebar
 * grouped by reading category (Neuro/Body/MSK/Other, guessed from body part - see
 * {@link LabelConfig#category}), and a main panel that shows exactly one protocol at a time,
 * toggled by a small inline script (no external JS/CSS/fonts - everything is embedded so the
 * file works offline). Protocols flagged excluded in the overrides are left out entirely;
 * protocols with manual scanning notes show them inline.
 */
public class ProtocolBookHtmlWriter {
    // Fixed reading order; any custom category from category-labels.json sorts alphabetically after these.
    private static final List<String> CATEGORY_ORDER = Arrays.asList("Neuro", "Body", "MSK", "Other");

    public File write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels, String logoDataUri, File outFile) throws IOException {
        Map<String, List<Protocol>> groups = new LinkedHashMap<String, List<Protocol>>();
        for (Protocol p : protocols) {
            if (isExcluded(p, overrides)) continue;
            String category = labels.category(p.getMetadata() == null ? null : p.getMetadata().getBodyPart());
            groups.computeIfAbsent(category, k -> new ArrayList<Protocol>()).add(p);
        }
        for (List<Protocol> group : groups.values()) group.sort(Comparator.comparingDouble(this::sortKey));

        List<String> categories = new ArrayList<String>(groups.keySet());
        categories.sort(Comparator.comparingInt(this::categoryRank).thenComparing(Comparator.naturalOrder()));

        Map<Protocol, String> ids = new IdentityHashMap<Protocol, String>();
        int index = 0;
        for (String category : categories) for (Protocol p : groups.get(category)) ids.put(p, protocolId(p, index++));

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Protocol Book</title>\n");
        html.append("<style>").append(CSS).append("</style>\n</head>\n<body>\n");

        html.append("<nav class=\"main-menu\">\n");
        if (logoDataUri != null) html.append("<div class=\"menu-logo\"><img src=\"").append(logoDataUri).append("\" alt=\"Atlantic Health System\"></div>\n");
        html.append("<ul>\n");
        for (String category : categories) {
            List<Protocol> group = groups.get(category);
            html.append("<li class=\"menu-category\">\n<a href=\"#\" class=\"cat-link\" onclick=\"return toggleCategory(this);\">")
                    .append("<span class=\"nav-icon\">").append(categoryInitial(category)).append("</span>")
                    .append("<span class=\"nav-text\">").append(HtmlSupport.esc(category)).append(" (").append(group.size()).append(")</span></a>\n");
            html.append("<ul class=\"submenu\">\n");
            for (Protocol p : group) {
                String id = ids.get(p);
                Metadata m = p.getMetadata();
                html.append("<li><a href=\"#").append(id).append("\" data-target=\"").append(id)
                        .append("\" onclick=\"showProtocol('").append(id).append("'); return false;\">")
                        .append(HtmlSupport.esc(m == null ? null : m.getProtocolNumber())).append(" &mdash; ")
                        .append(HtmlSupport.esc(m == null ? null : m.getName())).append("</a></li>\n");
            }
            html.append("</ul>\n</li>\n");
        }
        html.append("</ul>\n</nav>\n");

        html.append("<main class=\"main-content\">\n");
        html.append("<div id=\"welcome\" class=\"protocol-view welcome\" style=\"display:block;\">\n");
        if (logoDataUri != null) html.append("<img class=\"welcome-logo\" src=\"").append(logoDataUri).append("\" alt=\"Atlantic Health System\">\n");
        html.append("<h1>Protocol Book</h1>\n<p>Select a protocol from the menu to view it.</p>\n</div>\n");
        for (String category : categories) {
            for (Protocol p : groups.get(category)) {
                html.append("<section id=\"").append(ids.get(p)).append("\" class=\"protocol-view\" style=\"display:none;\">\n");
                appendProtocol(html, p, overrides, labels, logoDataUri);
                html.append("</section>\n");
            }
        }
        html.append("</main>\n");

        html.append("<script>").append(JS).append("</script>\n");
        html.append("</body>\n</html>\n");

        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    private String protocolId(Protocol p, int index) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        String base = number != null && !number.isEmpty() ? number : ("unnamed-" + index);
        return "p-" + base.replaceAll("[^a-zA-Z0-9]+", "-");
    }

    private String categoryInitial(String category) {
        return category == null || category.isEmpty() ? "?" : category.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private int categoryRank(String category) {
        int idx = CATEGORY_ORDER.indexOf(category);
        return idx >= 0 ? idx : CATEGORY_ORDER.size();
    }

    private void appendProtocol(StringBuilder html, Protocol p, Map<String, ProtocolOverride> overrides, LabelConfig labels, String logoDataUri) {
        Metadata m = p.getMetadata();
        String number = m == null ? null : m.getProtocolNumber();
        html.append("<div class=\"protocol-header\">\n");
        if (logoDataUri != null) html.append("<img class=\"protocol-logo\" src=\"").append(logoDataUri).append("\" alt=\"Atlantic Health System\">\n");
        html.append("<h2>").append(HtmlSupport.esc(number)).append(" &mdash; ").append(HtmlSupport.esc(m == null ? null : m.getName())).append("</h2>\n");
        html.append("</div>\n");
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

    // Best-effort Atlantic Health System palette (menu orange, main panel blue) - not sourced from an
    // official brand guide, so swap these hex values if AHS's real brand colors differ.
    private static final String CSS =
            ":root{--ahs-blue:#003057;--ahs-blue-accent:#0072ce;--ahs-orange:#ff8200;--ahs-orange-dark:#cc6900;}" +
            "*{box-sizing:border-box;}" +
            "html,body{margin:0;padding:0;min-height:100%;}" +
            "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:var(--ahs-blue);}" +

            // Sidebar - collapsed to an icon rail, hover (or click, for touchscreens) expands it.
            ".main-menu{position:fixed;top:0;left:0;bottom:0;width:56px;background:var(--ahs-orange);overflow-x:hidden;overflow-y:auto;" +
            "transition:width .15s ease;z-index:1000;box-shadow:2px 0 8px rgba(0,0,0,.3);}" +
            ".main-menu:hover,.main-menu.expanded{width:300px;}" +
            ".menu-logo{padding:14px 0;text-align:center;border-bottom:1px solid rgba(255,255,255,.3);}" +
            ".menu-logo img{max-width:44px;max-height:44px;}" +
            ".main-menu:hover .menu-logo img,.main-menu.expanded .menu-logo img{max-width:220px;}" +
            ".main-menu ul{list-style:none;margin:0;padding:6px 0;}" +
            ".menu-category{border-top:1px solid rgba(255,255,255,.25);}" +
            ".menu-category:first-child{border-top:none;}" +
            ".cat-link{display:flex;align-items:center;padding:12px 0;color:#fff;text-decoration:none;white-space:nowrap;font-weight:600;font-size:15px;cursor:pointer;}" +
            ".cat-link:hover,.menu-category.open>.cat-link{background:var(--ahs-orange-dark);}" +
            ".nav-icon{display:flex;align-items:center;justify-content:center;width:56px;height:28px;flex-shrink:0;}" +
            ".nav-icon span{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;" +
            "background:rgba(255,255,255,.25);font-size:14px;font-weight:700;}" +
            ".nav-text{display:inline-block;}" +
            ".submenu{display:none;background:rgba(0,0,0,.15);}" +
            ".menu-category:hover .submenu,.menu-category.open .submenu{display:block;}" +
            ".submenu a{display:block;padding:8px 14px 8px 56px;color:#fff;text-decoration:none;font-size:13px;line-height:1.35;" +
            "white-space:normal;border-top:1px solid rgba(255,255,255,.12);}" +
            ".submenu a:hover,.submenu a.active{background:var(--ahs-blue);}" +

            // Main content - blue canvas, protocol shown as a white reading card so dense tables stay legible.
            ".main-content{margin-left:56px;min-height:100vh;padding:2.5rem;}" +
            ".protocol-view.welcome{color:#fff;text-align:center;padding-top:14vh;}" +
            ".protocol-view.welcome h1{font-size:2.2rem;margin-bottom:.5rem;}" +
            ".welcome-logo{max-width:280px;max-height:120px;margin-bottom:1.5rem;}" +
            "section.protocol-view{background:#fff;border-radius:10px;box-shadow:0 2px 14px rgba(0,0,0,.25);padding:2rem 2.5rem;max-width:1100px;margin:0 auto;}" +
            ".protocol-header{display:flex;align-items:center;gap:1rem;border-bottom:3px solid var(--ahs-orange);padding-bottom:.4rem;margin-bottom:1rem;}" +
            ".protocol-logo{max-height:48px;max-width:160px;flex-shrink:0;}" +
            "section.protocol-view h2{color:var(--ahs-blue);margin:0;}" +
            "section.protocol-view h3{color:var(--ahs-blue);margin:1.25rem 0 .25rem;}" +
            ".meta,.dose{color:#555;font-size:.9rem;}" +
            ".notes{background:#fff4e5;border:1px solid var(--ahs-orange);border-radius:6px;padding:.6rem .9rem;margin:.6rem 0;}" +
            ".series{margin:1rem 0 1rem 1rem;padding-left:1rem;border-left:3px solid #dbe7f3;}" +
            "table{border-collapse:collapse;width:100%;margin:.4rem 0 1rem;}" +
            "th,td{border:1px solid #dde3ea;padding:.4rem .6rem;font-size:.9rem;text-align:left;}" +
            "th{background:var(--ahs-blue);color:#fff;}" +
            "table.recons tr.reformat td:first-child{padding-left:1.5rem;color:#555;}" +

            "@media print{.main-menu{display:none;}body{background:#fff;}.main-content{margin-left:0;padding:0;}" +
            "section.protocol-view{box-shadow:none;border-radius:0;max-width:none;}}";

    private static final String JS =
            "function showProtocol(id){" +
            "document.querySelectorAll('.protocol-view').forEach(function(el){el.style.display='none';});" +
            "var t=document.getElementById(id);if(t)t.style.display='block';" +
            "document.querySelectorAll('.submenu a').forEach(function(a){a.classList.remove('active');});" +
            "var link=document.querySelector('.submenu a[data-target=\"'+id+'\"]');if(link)link.classList.add('active');" +
            "window.scrollTo(0,0);}" +
            "function toggleCategory(el){" +
            "var li=el.parentElement;var wasOpen=li.classList.contains('open');" +
            "document.querySelectorAll('.menu-category.open').forEach(function(l){l.classList.remove('open');});" +
            "if(!wasOpen)li.classList.add('open');return false;}";
}
