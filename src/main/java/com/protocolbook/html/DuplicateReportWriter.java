package com.protocolbook.html;

import com.protocolbook.duplicates.DuplicateFinder;
import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Printable list of effectively-duplicate protocols (see {@link DuplicateFinder}), with the
 * protocol-overrides.json lines that would hide the extra copies from the book - the person
 * decides which copy to keep, this only proposes keeping the most recently updated one (the copy
 * someone has been maintaining), or the lowest-numbered when there are no dates.
 */
public class DuplicateReportWriter {
    public File write(DuplicateFinder.Result result, Map<String, ProtocolOverride> overrides, File outFile) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Duplicate Protocols</title>\n");
        html.append("<style>").append(HtmlSupport.BASE_CSS).append("pre{background:#f6f6f6;padding:.6rem;border:1px solid #ddd;}h2{margin-top:2rem;}</style>\n</head>\n<body>\n");
        html.append("<h1>Duplicate Protocols</h1>\n");
        html.append("<p class=\"subtitle\">To hide a copy from the protocol book, add its line to protocol-overrides.json. ")
                .append("Nothing here changes the scanner.</p>\n");

        html.append("<h2>Identical settings (").append(result.identical.size()).append(" group(s))</h2>\n");
        html.append("<p>Same series, kV/mA, pitch, contrast and reconstructions &mdash; only the name, number or filing differs.</p>\n");
        List<String> suggestions = new ArrayList<String>();
        for (List<Protocol> group : result.identical) {
            appendTable(html, group, overrides);
            Protocol keep = newest(group);
            for (Protocol p : sorted(group)) {
                String number = number(p);
                if (p != keep && number != null && !isExcluded(p, overrides)) suggestions.add("  \"" + number + "\": { \"excluded\": true },");
            }
        }
        if (!suggestions.isEmpty()) {
            html.append("<p>Suggested protocol-overrides.json lines (keeps the most recently updated copy of each group &mdash; change which one if another copy is filed in the right place):</p>\n<pre>");
            for (String line : suggestions) html.append(HtmlSupport.esc(line)).append("\n");
            html.append("</pre>\n");
        }

        html.append("<h2>Same name, different settings (").append(result.sameNameDifferent.size()).append(" group(s))</h2>\n");
        html.append("<p>Worth a look: one may be an older copy of the other, or they may be deliberately different.</p>\n");
        for (List<Protocol> group : result.sameNameDifferent) {
            List<Protocol> ordered = sorted(group);
            appendTable(html, ordered, overrides);
            html.append("<p>Differences (in the order listed above):</p>\n<ul>\n");
            for (String d : DuplicateFinder.differences(ordered)) html.append("<li>").append(HtmlSupport.esc(d)).append("</li>\n");
            html.append("</ul>\n");
        }

        html.append("</body>\n</html>\n");
        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    private void appendTable(StringBuilder html, List<Protocol> group, Map<String, ProtocolOverride> overrides) {
        html.append("<table>\n<tr><th>#</th><th>Protocol</th><th>Patient / body part</th><th>Last updated</th><th>In book?</th></tr>\n");
        for (Protocol p : sorted(group)) {
            Metadata m = p.getMetadata();
            html.append("<tr><td>").append(HtmlSupport.esc(number(p))).append("</td><td>").append(HtmlSupport.esc(m == null ? null : m.getName()))
                    .append("</td><td>").append(HtmlSupport.esc(m == null ? null : m.getPatientType())).append(" / ")
                    .append(HtmlSupport.esc(m == null ? null : m.getBodyPart())).append("</td><td>").append(HtmlSupport.esc(m == null ? null : m.getLastUpdated()))
                    .append("</td><td>").append(isExcluded(p, overrides) ? "excluded" : "yes").append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    // lastUpdated is an ISO-8601 timestamp from protocolmetadata.json, so text order is date order.
    private static Protocol newest(List<Protocol> group) {
        Protocol best = null;
        for (Protocol p : sorted(group)) {
            String updated = p.getMetadata() == null ? null : p.getMetadata().getLastUpdated();
            String bestUpdated = best == null || best.getMetadata() == null ? null : best.getMetadata().getLastUpdated();
            if (best == null || (updated != null && (bestUpdated == null || updated.compareTo(bestUpdated) > 0))) best = p;
        }
        return best;
    }

    private static List<Protocol> sorted(List<Protocol> group) {
        List<Protocol> out = new ArrayList<Protocol>(group);
        out.sort((a, b) -> ProtocolNumbers.compare(number(a), number(b)));
        return out;
    }

    private static String number(Protocol p) {
        return p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
    }

    private static boolean isExcluded(Protocol p, Map<String, ProtocolOverride> overrides) {
        ProtocolOverride o = overrides.get(number(p));
        return o != null && o.isExcluded();
    }
}
