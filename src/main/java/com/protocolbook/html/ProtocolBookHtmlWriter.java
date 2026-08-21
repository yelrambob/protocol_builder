package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.*;
import com.protocolbook.overrides.ProtocolOverride;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Renders the parsed protocols as a single self-contained HTML app: a click-only sidebar (nothing
 * opens or expands on hover - that was cumbersome to navigate; clicking a top-level entry both
 * opens it and widens the sidebar) with Adult and Peds always shown as their own top-level entries
 * (spelled out, not abbreviated to an icon letter - even with zero protocols in one of them, so
 * the menu structure is never a surprise) - split primarily by protocol number shape (an extra
 * dot-separated segment marks a pediatric protocol, e.g. "9.1.2" vs adult's "9.1"; see
 * {@link ProtocolNumbers}), falling back to {@link Metadata#getPatientType} - then drilling down
 * into a reading category keyed by the protocol number's
 * whole-number prefix (e.g. all "9.x" protocols together) and labeled to match the scanner
 * console's own numbering (1 Head, 2 Face, 3 Neck, ... 9 Lower Ext. for adult, the same nine
 * again at 11-19 for pediatric - see {@link LabelConfig#categoryForNumber}) -> individual pages.
 * A prefix with no category mapping (by default anything outside 1-9/11-19, in particular
 * 10.x/20.x QA/phantom protocols) is left out of the book entirely, not dumped in a catch-all.
 * Pediatric protocols sharing the same first two number segments (e.g. "15.7.1", "15.7.2",
 * "15.7.3" - the third segment is a per-weight-band variant, e.g. "&lt;5KG"/"10-20KG"/"ROUTINE")
 * are one "family" and render as a single page: the technique parameters that actually vary by
 * weight (kV, mA, noise index, CTDIvol, DLP) are laid out in one comparison table instead of
 * repeating the identical series/recon structure once per weight band - see {@link PageEntry}
 * and {@link #appendWeightVariantTable}. A main panel shows exactly one page at a time, toggled
 * by a small inline script (no external JS/CSS/fonts - everything is embedded so the file works
 * offline). Protocols flagged excluded in the overrides are left out entirely; a page with a
 * "title" override displays under that name instead of its scanner name (or its weight-stripped
 * family name), and manual scanning notes/send destination show inline.
 * An optional {@link PdfLibrary} of externally hosted PDFs (not tied to any CT protocol) gets
 * its own top-level sidebar entry, linking out with target="_blank" since those files live
 * on a different server than wherever this book itself ends up hosted.
 * An optional {@link ProtocolImages} shows a per-page reference image, looked up by
 * convention (page number -> filename) rather than a maintained list; the <img> hides
 * itself client-side if that particular page doesn't have one on the server.
 * An optional {@link Changelog} of hand-typed "what changed and why" entries gets its own
 * top-level "Recent Changes" sidebar entry, most recent first, each row linking to that
 * protocol's page when its number (or the family number it was merged into) still matches one
 * in the book.
 */
public class ProtocolBookHtmlWriter {
    private static final List<String> BUCKET_ORDER = Arrays.asList("Adult", "Peds");

    /**
     * One sidebar link / main-panel section. Usually wraps a single protocol; for a pediatric
     * weight-band family it wraps every variant sharing the family's first two number segments,
     * sorted lightest-to-heaviest (see {@link ProtocolNumbers}), with {@link #number} being the
     * shared family key (e.g. "15.7") rather than any one variant's full number.
     */
    private static final class PageEntry {
        final String number;
        final List<Protocol> variants;
        PageEntry(String number, List<Protocol> variants) { this.number = number; this.variants = variants; }
        Protocol primary() { return variants.get(0); }
        boolean isFamily() { return variants.size() > 1; }
    }

    public File write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels,
                       String logoDataUri, List<PdfLibrary.Entry> pdfLibrary, ProtocolImages protocolImages,
                       String bookTitle, List<Changelog.Entry> changelog, File outFile) throws IOException {
        String title = bookTitle == null || bookTitle.trim().isEmpty() ? "Protocol Book" : bookTitle;
        // bucket (Adult/Peds) -> protocol-number whole-number prefix -> protocols.
        // A prefix with no category label (see LabelConfig.categoryForNumber) is skipped entirely -
        // that's how 10.x/20.x (QA/phantom) protocols stay off the generated book without needing
        // to be excluded one at a time in protocol-overrides.json.
        Map<String, Map<Integer, List<Protocol>>> byProtocol = new LinkedHashMap<String, Map<Integer, List<Protocol>>>();
        // Adult and Peds always get a top-level entry, even with zero protocols in one of them -
        // the sidebar's shape shouldn't depend on what happens to be in this particular export.
        for (String bucket : BUCKET_ORDER) byProtocol.put(bucket, new LinkedHashMap<Integer, List<Protocol>>());
        for (Protocol p : protocols) {
            if (isExcluded(p, overrides)) continue;
            int prefix = groupKey(p);
            if (labels.categoryForNumber(prefix) == null) continue;
            String bucket = patientBucket(p);
            byProtocol.computeIfAbsent(bucket, k -> new LinkedHashMap<Integer, List<Protocol>>())
                    .computeIfAbsent(prefix, k -> new ArrayList<Protocol>())
                    .add(p);
        }

        // Group each reading category's protocols into pages: weight-band variants of the same
        // family collapse into one PageEntry, everything else is a family of one (which is why
        // this applies uniformly to Adult's plain two-segment numbers with no special-casing).
        Map<String, Map<Integer, List<PageEntry>>> tree = new LinkedHashMap<String, Map<Integer, List<PageEntry>>>();
        for (Map.Entry<String, Map<Integer, List<Protocol>>> bucketEntry : byProtocol.entrySet()) {
            Map<Integer, List<PageEntry>> byGroup = new LinkedHashMap<Integer, List<PageEntry>>();
            for (Map.Entry<Integer, List<Protocol>> groupEntry : bucketEntry.getValue().entrySet())
                byGroup.put(groupEntry.getKey(), buildPageEntries(groupEntry.getValue()));
            tree.put(bucketEntry.getKey(), byGroup);
        }

        List<String> buckets = new ArrayList<String>(tree.keySet());
        buckets.sort(Comparator.comparingInt(this::bucketRank).thenComparing(Comparator.naturalOrder()));

        Map<PageEntry, String> ids = new IdentityHashMap<PageEntry, String>();
        // Every protocol number that resolves to a page - each variant's own number plus, for a
        // merged family, its shared family key - so a changelog entry naming either still links.
        Map<String, String> pageIdByNumber = new HashMap<String, String>();
        Set<String> usedIds = new HashSet<String>();
        int index = 0;
        for (String bucket : buckets)
            for (Integer prefix : sortedGroups(tree.get(bucket)))
                for (PageEntry pe : tree.get(bucket).get(prefix)) {
                    // Adult and Peds occupy disjoint number-prefix ranges by convention (1-9 vs
                    // 11-19), so an id collision across buckets is not expected in practice - but
                    // a hand-authored manual protocol can defy that (e.g. patientType flagged
                    // "pediatric" on an otherwise adult-shaped number), so guard against it rather
                    // than silently emitting two elements with the same id.
                    String id = pageEntryId(pe, index++);
                    if (usedIds.contains(id)) id = id + "-" + bucket.toLowerCase(Locale.ROOT);
                    usedIds.add(id);
                    ids.put(pe, id);
                    pageIdByNumber.put(pe.number, id);
                    for (Protocol v : pe.variants) {
                        String number = protocolNumber(v);
                        if (number != null) pageIdByNumber.put(number, id);
                    }
                }

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>").append(HtmlSupport.esc(title)).append("</title>\n");
        html.append("<style>").append(CSS).append("</style>\n</head>\n<body>\n");

        html.append("<nav class=\"main-menu\">\n");
        if (logoDataUri != null) html.append("<div class=\"menu-logo\"><img src=\"").append(logoDataUri).append("\" alt=\"Atlantic Health System\"></div>\n");
        html.append("<ul>\n");
        for (String bucket : buckets) {
            Map<Integer, List<PageEntry>> byGroup = tree.get(bucket);
            html.append("<li class=\"menu-category\">\n<a href=\"#\" class=\"cat-link\" onclick=\"return toggleMenu(this);\">")
                    .append("<span class=\"nav-text\">").append(HtmlSupport.esc(bucket)).append(" (").append(count(byGroup)).append(")</span></a>\n");
            html.append("<ul class=\"submenu\">\n");
            for (Integer prefix : sortedGroups(byGroup)) {
                List<PageEntry> groupEntries = byGroup.get(prefix);
                html.append("<li class=\"menu-subcat\">\n<a href=\"#\" class=\"subcat-link\" onclick=\"return toggleMenu(this);\">")
                        .append(HtmlSupport.esc(labels.categoryForNumber(prefix))).append(" (").append(groupEntries.size()).append(")</a>\n");
                html.append("<ul class=\"submenu\">\n");
                for (PageEntry pe : groupEntries) {
                    String id = ids.get(pe);
                    html.append("<li><a href=\"#").append(id).append("\" data-target=\"").append(id)
                            .append("\" onclick=\"showProtocol('").append(id).append("'); return false;\">")
                            .append(HtmlSupport.esc(pe.number)).append(" &mdash; ")
                            .append(HtmlSupport.esc(pageTitle(pe, overrides))).append("</a></li>\n");
                }
                html.append("</ul>\n</li>\n");
            }
            html.append("</ul>\n</li>\n");
        }
        if (changelog != null && !changelog.isEmpty()) {
            html.append("<li class=\"menu-category\">\n<a href=\"#\" class=\"cat-link\" onclick=\"showProtocol('recent-changes'); return false;\">")
                    .append("<span class=\"nav-text\">Recent Changes (").append(changelog.size()).append(")</span></a>\n</li>\n");
        }
        if (pdfLibrary != null && !pdfLibrary.isEmpty()) {
            html.append("<li class=\"menu-category\">\n<a href=\"#\" class=\"cat-link\" onclick=\"return toggleMenu(this);\">")
                    .append("<span class=\"nav-text\">Surgical Planning (").append(pdfLibrary.size()).append(")</span></a>\n");
            html.append("<ul class=\"submenu\">\n");
            for (PdfLibrary.Entry entry : pdfLibrary) {
                html.append("<li><a href=\"").append(HtmlSupport.esc(entry.url)).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                        .append(HtmlSupport.esc(entry.title)).append("</a></li>\n");
            }
            html.append("</ul>\n</li>\n");
        }
        html.append("</ul>\n</nav>\n");

        html.append("<main class=\"main-content\">\n");
        html.append("<div id=\"welcome\" class=\"protocol-view welcome\" style=\"display:block;\">\n");
        if (logoDataUri != null) html.append("<img class=\"welcome-logo\" src=\"").append(logoDataUri).append("\" alt=\"Atlantic Health System\">\n");
        html.append("<h1>").append(HtmlSupport.esc(title)).append("</h1>\n<p>Select a protocol from the menu to view it.</p>\n</div>\n");
        if (changelog != null && !changelog.isEmpty()) appendRecentChanges(html, changelog, pageIdByNumber);
        for (String bucket : buckets)
            for (Integer prefix : sortedGroups(tree.get(bucket)))
                for (PageEntry pe : tree.get(bucket).get(prefix)) {
                    html.append("<section id=\"").append(ids.get(pe)).append("\" class=\"protocol-view\" style=\"display:none;\">\n");
                    appendPageEntry(html, pe, overrides, labels, logoDataUri, protocolImages);
                    html.append("</section>\n");
                }
        html.append("</main>\n");

        html.append("<script>").append(JS).append("</script>\n");
        html.append("</body>\n</html>\n");

        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    // Groups a reading category's protocols into pages by family key (first two number segments -
    // see familyKey): a weight-band family of 2+ variants becomes one merged PageEntry, and a
    // protocol whose family key is its own full number (the usual two-segment adult case, or any
    // unmerged single pediatric variant) becomes a family of one, unchanged from before.
    private List<PageEntry> buildPageEntries(List<Protocol> group) {
        Map<String, List<Protocol>> byFamily = new LinkedHashMap<String, List<Protocol>>();
        int index = 0;
        for (Protocol p : group) byFamily.computeIfAbsent(familyKey(p, index++), k -> new ArrayList<Protocol>()).add(p);
        List<PageEntry> entries = new ArrayList<PageEntry>();
        for (Map.Entry<String, List<Protocol>> e : byFamily.entrySet()) {
            List<Protocol> variants = e.getValue();
            variants.sort((a, b) -> ProtocolNumbers.compare(protocolNumber(a), protocolNumber(b)));
            entries.add(new PageEntry(e.getKey(), variants));
        }
        entries.sort((a, b) -> ProtocolNumbers.compare(a.number, b.number));
        return entries;
    }

    // First two dot-separated segments of the protocol number (e.g. "15.7" from "15.7.1") - the
    // scanner's own weight-band family; everything after that is just a per-weight-band variant.
    // A protocol with no number never merges with another unnumbered one - each gets a unique key.
    private String familyKey(Protocol p, int fallbackIndex) {
        String number = protocolNumber(p);
        if (number == null) return "unnamed-" + fallbackIndex;
        String[] parts = number.split("\\.");
        return parts.length <= 2 ? number : parts[0] + "." + parts[1];
    }

    private List<Integer> sortedGroups(Map<Integer, List<PageEntry>> byGroup) {
        List<Integer> groups = new ArrayList<Integer>(byGroup.keySet());
        groups.sort(Comparator.naturalOrder());
        return groups;
    }

    private int count(Map<Integer, List<PageEntry>> byGroup) {
        int total = 0;
        for (List<PageEntry> list : byGroup.values()) total += list.size();
        return total;
    }

    // Primarily by protocol number shape (peds numbers carry an extra dot-separated segment,
    // e.g. "9.1.2" vs adult's "9.1" - see ProtocolNumbers), falling back to the free-text patient
    // type for protocols that don't follow that convention (e.g. hand-authored manual protocols).
    private String patientBucket(Protocol p) {
        Metadata m = p.getMetadata();
        if (ProtocolNumbers.isPediatric(m == null ? null : m.getProtocolNumber())) return "Peds";
        String type = m == null ? null : m.getPatientType();
        if (type == null) return "Adult";
        String t = type.toLowerCase(Locale.ROOT);
        boolean pediatric = t.contains("pediatric") || t.contains("peds") || t.contains("pedi") || t.contains("child");
        return pediatric ? "Peds" : "Adult";
    }

    private int bucketRank(String bucket) {
        int idx = BUCKET_ORDER.indexOf(bucket);
        return idx >= 0 ? idx : BUCKET_ORDER.size();
    }

    // Whole-number prefix of the protocol number (e.g. 9 from "9.2") - GE's own grouping convention.
    private int groupKey(Protocol p) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        if (number == null) return Integer.MIN_VALUE;
        try { return Integer.parseInt(number.split("\\.")[0]); } catch (Exception e) { return Integer.MIN_VALUE; }
    }

    private String pageEntryId(PageEntry pe, int index) {
        String base = pe.number != null && !pe.number.isEmpty() ? pe.number : ("unnamed-" + index);
        return "p-" + base.replaceAll("[^a-zA-Z0-9]+", "-");
    }

    // A "title" override renames how a page displays in the book without touching its underlying
    // scanner name (which still flows through --json/console output unchanged). For a merged
    // weight-band family, "number" is the shared family key (e.g. "15.7"), so one override
    // renames the whole family at once rather than needing an entry per weight band.
    private String displayName(String number, String rawName, Map<String, ProtocolOverride> overrides) {
        ProtocolOverride override = overrides.get(number);
        if (override != null && override.getTitle() != null && !override.getTitle().trim().isEmpty()) return override.getTitle();
        return rawName;
    }

    private String pageTitle(PageEntry pe, Map<String, ProtocolOverride> overrides) {
        Metadata m = pe.primary().getMetadata();
        String rawName = m == null ? null : m.getName();
        if (pe.isFamily()) {
            String stripped = WeightAnnotations.stripLabel(rawName);
            if (stripped != null && !stripped.isEmpty()) rawName = stripped;
        }
        return displayName(pe.number, rawName, overrides);
    }

    private void appendPageEntry(StringBuilder html, PageEntry pe, Map<String, ProtocolOverride> overrides, LabelConfig labels,
                                  String logoDataUri, ProtocolImages protocolImages) {
        Protocol primary = pe.primary();
        Metadata m = primary.getMetadata();
        String name = pageTitle(pe, overrides);
        html.append("<div class=\"protocol-header\">\n");
        if (logoDataUri != null) html.append("<img class=\"protocol-logo\" src=\"").append(logoDataUri).append("\" alt=\"Atlantic Health System\">\n");
        html.append("<h2>").append(HtmlSupport.esc(pe.number)).append(" &mdash; ").append(HtmlSupport.esc(name)).append("</h2>\n");
        html.append("</div>\n");

        String imageUrl = protocolImages == null ? null : protocolImages.urlFor(pe.number);
        if (imageUrl != null) {
            html.append("<img class=\"protocol-image\" src=\"").append(HtmlSupport.esc(imageUrl)).append("\" alt=\"")
                    .append(HtmlSupport.esc(pe.number)).append(" reference image\" onerror=\"this.style.display='none';\">\n");
        }

        html.append("<p class=\"meta\">").append(HtmlSupport.esc(m == null ? null : m.getPatientType())).append(" &middot; ")
                .append(HtmlSupport.esc(m == null ? null : m.getBodyPart())).append("</p>\n");

        ProtocolOverride override = overrides.get(pe.number);
        if (override != null && override.getNotes() != null && !override.getNotes().trim().isEmpty()) {
            html.append("<div class=\"notes\"><strong>Scanning notes:</strong> ").append(HtmlSupport.esc(override.getNotes())).append("</div>\n");
        }
        if (override != null && override.getSendDestination() != null && !override.getSendDestination().trim().isEmpty()) {
            html.append("<p class=\"destination\">Sends to: ").append(HtmlSupport.esc(override.getSendDestination())).append("</p>\n");
        }

        if (pe.isFamily()) {
            appendWeightVariantTable(html, pe.variants);
        } else if (primary.getDose() != null && (primary.getDose().getCtdi() != null || primary.getDose().getDlp() != null)) {
            html.append("<p class=\"dose\">Exam CTDIvol: ").append(HtmlSupport.esc(primary.getDose().getCtdi())).append(" mGy &middot; DLP: ")
                    .append(HtmlSupport.esc(primary.getDose().getDlp())).append(" mGy&middot;cm</p>\n");
        }

        for (Series s : primary.getSeries()) appendSeries(html, s, labels, pe.isFamily());

        if (!primary.getNotes().isEmpty()) {
            html.append("<p class=\"notes\">Notes: ").append(HtmlSupport.esc(String.join("; ", primary.getNotes()))).append("</p>\n");
        }
    }

    // A merged weight-band family's per-variant technique parameters (everything else - series
    // names, reconstruction thickness/interval/kernel - is assumed identical across the family and
    // rendered once from the first variant; see appendPageEntry/appendSeries). Only the first
    // non-scout series' first acquisition group is compared - real weight-banded protocols are a
    // single diagnostic phase, so this covers the case the merge exists for without trying to
    // diff an arbitrary number of series/groups against each other.
    private void appendWeightVariantTable(StringBuilder html, List<Protocol> variants) {
        html.append("<table class=\"weight-variants\">\n<tr><th>Weight</th><th>kV</th><th>mA</th><th>NI</th><th>CTDIvol</th><th>DLP</th></tr>\n");
        for (Protocol v : variants) {
            Group g = firstDiagnosticGroup(v);
            Acquisition a = g == null ? null : g.getAcquisition();
            boolean autoMa = a != null && a.getMaMode() != null && a.getMinMa() != null && a.getMaxMa() != null;
            Dose dose = v.getDose();
            html.append("<tr><td>").append(HtmlSupport.esc(weightLabel(v))).append("</td><td>")
                    .append(a == null ? "" : HtmlSupport.esc(a.getKv())).append("</td><td>")
                    .append(a == null ? "" : (autoMa ? HtmlSupport.esc(a.getMinMa()) + "-" + HtmlSupport.esc(a.getMaxMa()) : HtmlSupport.esc(a.getMa())))
                    .append("</td><td>").append(a == null ? "" : HtmlSupport.esc(a.getNoiseIndex())).append("</td><td>")
                    .append(HtmlSupport.esc(dose == null ? null : dose.getCtdi())).append("</td><td>")
                    .append(HtmlSupport.esc(dose == null ? null : dose.getDlp())).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private Group firstDiagnosticGroup(Protocol p) {
        for (Series s : p.getSeries()) {
            if (isScout(s)) continue;
            if (!s.getGroups().isEmpty()) return s.getGroups().get(0);
        }
        return null;
    }

    // The row label for one weight-band variant: the weight phrase from its own name if present
    // (e.g. "<5KG"), else its protocol number's trailing segment(s) beyond the shared family key
    // (e.g. "1" from "15.7.1"), else the whole number as a last resort.
    private String weightLabel(Protocol v) {
        Metadata m = v.getMetadata();
        String label = WeightAnnotations.extractLabel(m == null ? null : m.getName());
        if (label != null) return label;
        String number = m == null ? null : m.getProtocolNumber();
        if (number == null) return null;
        int firstDot = number.indexOf('.');
        int secondDot = firstDot < 0 ? -1 : number.indexOf('.', firstDot + 1);
        return secondDot >= 0 && secondDot + 1 < number.length() ? number.substring(secondDot + 1) : number;
    }

    private void appendSeries(StringBuilder html, Series s, LabelConfig labels, boolean familyMerged) {
        boolean scout = isScout(s);
        html.append("<div class=\"series\"><h3>Series ").append(s.getNumber()).append(" &mdash; ")
                .append(HtmlSupport.esc(s.getScanType())).append(HtmlSupport.esc(s.getName() == null ? "" : ": " + s.getName())).append("</h3>\n");
        // Injection rate/volume describes the contrast bolus for the diagnostic series, not the
        // scout/localizer - scouts never carry contrast timing of their own, so skip it there.
        if (!scout && s.getContrast() != null && s.getContrast().isIv()) {
            html.append("<p class=\"contrast\">IV contrast: ").append(HtmlSupport.esc(s.getContrast().getIvVolume())).append(" mL");
            if (s.getContrast().getFlowRate() != null) html.append(" @ ").append(HtmlSupport.esc(s.getContrast().getFlowRate())).append(" mL/s");
            html.append("</p>\n");
        }
        if (scout) appendScoutTable(html, s, labels);
        else for (Group g : s.getGroups()) { if (familyMerged) appendGroupRecons(html, g, labels); else appendGroup(html, g, labels); }
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
        html.append("</p>\n");
        appendReconTable(html, g, labels);
    }

    // For a merged weight-band family, kV/mA/NI/CTDIvol vary per variant and are already shown in
    // appendWeightVariantTable - this renders only what's assumed constant across the whole
    // family: pitch/rotation time (if set) and the reconstruction table.
    private void appendGroupRecons(StringBuilder html, Group g, LabelConfig labels) {
        Acquisition a = g.getAcquisition();
        StringBuilder line = new StringBuilder();
        if (a.getPitch() != null) line.append("pitch ").append(HtmlSupport.esc(a.getPitch()));
        if (a.getRotationTime() != null) {
            if (line.length() > 0) line.append(" &middot; ");
            line.append(HtmlSupport.esc(a.getRotationTime())).append(" s rotation");
        }
        if (line.length() > 0) html.append("<p class=\"acquisition\">").append(line).append("</p>\n");
        appendReconTable(html, g, labels);
    }

    private void appendReconTable(StringBuilder html, Group g, LabelConfig labels) {
        html.append("<table class=\"recons\">\n<tr><th>Recon</th><th>Thickness</th><th>Interval</th><th>Kernel</th><th>ASIR</th></tr>\n");
        for (Reconstruction r : g.getReconstructions()) {
            html.append("<tr").append(r.isDerived() ? " class=\"reformat\"" : "").append("><td>").append(HtmlSupport.esc(r.getName()))
                    .append("</td><td>").append(HtmlSupport.esc(r.getThickness()))
                    .append("</td><td>").append(HtmlSupport.esc(r.getInterval())).append("</td><td>").append(HtmlSupport.esc(labels.kernel(r.getKernel())))
                    .append("</td><td>").append(HtmlSupport.esc(labels.asir(r.getIterativeConfig()))).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private boolean isExcluded(Protocol p, Map<String, ProtocolOverride> overrides) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        ProtocolOverride o = overrides.get(number);
        return o != null && o.isExcluded();
    }

    private String protocolNumber(Protocol p) {
        return p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
    }

    // Hand-typed log of what changed and why (see Changelog) - not derived from the scanner
    // export, since protocolmetadata.json's lastUpdatedDateTime says a file changed but not what
    // changed or why. Rows are already sorted newest-first by Changelog.load().
    private void appendRecentChanges(StringBuilder html, List<Changelog.Entry> changelog, Map<String, String> pageIdByNumber) {
        html.append("<section id=\"recent-changes\" class=\"protocol-view\" style=\"display:none;\">\n");
        html.append("<h2>Recent Changes</h2>\n<p class=\"meta\">Hand-maintained log of what changed and why - see changelog.json.</p>\n");
        html.append("<table>\n<tr><th>Date</th><th>Protocol</th><th>Note</th></tr>\n");
        for (Changelog.Entry entry : changelog) {
            String id = entry.protocolNumber == null ? null : pageIdByNumber.get(entry.protocolNumber);
            html.append("<tr><td>").append(HtmlSupport.esc(entry.displayDate())).append("</td><td>");
            if (id != null) {
                html.append("<a href=\"#").append(id).append("\" onclick=\"showProtocol('").append(id).append("'); return false;\">")
                        .append(HtmlSupport.esc(entry.protocolNumber)).append("</a>");
            } else {
                html.append(HtmlSupport.esc(entry.protocolNumber));
            }
            html.append("</td><td>").append(HtmlSupport.esc(entry.note)).append("</td></tr>\n");
        }
        html.append("</table>\n</section>\n");
    }

    // Best-effort Atlantic Health System palette (menu orange, main panel blue) - not sourced from an
    // official brand guide, so swap these hex values if AHS's real brand colors differ.
    private static final String CSS =
            ":root{--ahs-blue:#044281;--ahs-blue-accent:#044281;--ahs-orange:#ff8200;--ahs-orange-dark:#cc6900;}" +
            "*{box-sizing:border-box;}" +
            "html,body{margin:0;padding:0;min-height:100%;}" +
            "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:var(--ahs-blue);}" +

            // Sidebar - top-level entries (Adult/Peds/Recent Changes/Surgical Planning) are always
            // spelled out in full, never shrunk to a single icon letter; only the rail's width and
            // every drill-down level react to clicks (see toggleMenu in JS), never hover.
            // menu-category (Adult/Peds/Recent Changes/PDF library) -> menu-subcat (1-9 reading category, e.g. "Chest").
            ".main-menu{position:fixed;top:0;left:0;bottom:0;width:220px;background:var(--ahs-orange);overflow-x:hidden;overflow-y:auto;" +
            "transition:width .15s ease;z-index:1000;box-shadow:2px 0 8px rgba(0,0,0,.3);}" +
            ".main-menu.expanded{width:320px;}" +
            ".menu-logo{padding:14px 0;text-align:center;border-bottom:1px solid rgba(255,255,255,.3);}" +
            ".menu-logo img{max-width:160px;max-height:60px;}" +
            ".main-menu.expanded .menu-logo img{max-width:220px;}" +
            ".main-menu ul{list-style:none;margin:0;padding:6px 0;}" +
            ".menu-category{border-top:1px solid rgba(255,255,255,.25);}" +
            ".menu-category:first-child{border-top:none;}" +
            ".cat-link{display:flex;align-items:center;padding:12px 16px;color:#fff;text-decoration:none;white-space:nowrap;font-weight:600;font-size:15px;cursor:pointer;}" +
            ".cat-link:hover,.menu-category.open>.cat-link{background:var(--ahs-orange-dark);}" +
            ".nav-text{display:inline-block;}" +
            ".submenu{display:none;background:rgba(0,0,0,.15);}" +
            ".menu-category.open>.submenu{display:block;}" +

            ".menu-subcat{border-top:1px solid rgba(255,255,255,.15);}" +
            ".subcat-link{display:block;padding:10px 14px 10px 28px;color:#fff;text-decoration:none;font-weight:600;font-size:14px;" +
            "cursor:pointer;white-space:normal;background:rgba(0,0,0,.08);}" +
            ".subcat-link:hover,.menu-subcat.open>.subcat-link{background:var(--ahs-orange-dark);}" +
            ".menu-subcat.open>.submenu{display:block;}" +

            ".submenu a{display:block;padding:8px 14px 8px 28px;color:#fff;text-decoration:none;font-size:13px;line-height:1.35;" +
            "white-space:normal;border-top:1px solid rgba(255,255,255,.12);}" +
            ".submenu a:hover,.submenu a.active{background:var(--ahs-blue);}" +

            // Main content - blue canvas, protocol shown as a white reading card so dense tables stay legible.
            ".main-content{margin-left:220px;min-height:100vh;padding:2.5rem;}" +
            ".protocol-view.welcome{color:#fff;text-align:center;padding-top:14vh;}" +
            ".protocol-view.welcome h1{font-size:2.2rem;margin-bottom:.5rem;}" +
            ".welcome-logo{max-width:280px;max-height:120px;margin-bottom:1.5rem;}" +
            "section.protocol-view{background:#fff;border-radius:10px;box-shadow:0 2px 14px rgba(0,0,0,.25);padding:2rem 2.5rem;max-width:1100px;margin:0 auto;}" +
            ".protocol-header{display:flex;align-items:center;gap:1rem;border-bottom:3px solid var(--ahs-orange);padding-bottom:.4rem;margin-bottom:1rem;}" +
            ".protocol-logo{max-height:48px;max-width:160px;flex-shrink:0;}" +
            ".protocol-image{display:block;max-width:100%;max-height:400px;margin:0 auto 1rem;border-radius:6px;}" +
            "section.protocol-view h2{color:var(--ahs-blue);margin:0;}" +
            "section.protocol-view h3{color:var(--ahs-blue);margin:1.25rem 0 .25rem;}" +
            ".meta,.dose,.destination{color:#555;font-size:.9rem;}" +
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
            // Generic drill-down toggle shared by both sidebar levels (menu-category/menu-subcat):
            // opens the clicked node and closes its siblings at the same level, leaving ancestor/descendant levels alone.
            // Nothing here reacts to hover - the sidebar is entirely click-driven, including its own
            // collapsed/expanded width, which follows whether a top-level item is open, not the mouse.
            "function toggleMenu(el){" +
            "var li=el.parentElement;var siblingsUl=li.parentElement;var wasOpen=li.classList.contains('open');" +
            "Array.prototype.forEach.call(siblingsUl.children,function(sib){sib.classList.remove('open');});" +
            "if(!wasOpen)li.classList.add('open');" +
            "if(li.classList.contains('menu-category')){" +
            "var menu=li.closest('.main-menu');if(menu)menu.classList.toggle('expanded',!wasOpen);}" +
            "return false;}";
}
