package com.protocolbook.html;

import com.protocolbook.changes.ManualChanges;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Printable PDF of every value set by hand (see {@link ManualChanges}): per section, per protocol, what
 * the scanner has next to what the book now shows. Those changes only live in the book, so this is
 * also the list of what to update at the scanner console for the two to match.
 */
public class ChangeReportWriter {
    private BookTheme theme = BookTheme.DEFAULT;

    public ChangeReportWriter withTheme(BookTheme theme) {
        this.theme = theme == null ? BookTheme.DEFAULT : theme;
        return this;
    }

    public File write(List<Protocol> protocols, Map<String, ProtocolOverride> overrides, LabelConfig labels,
                      String bookTitle, File outFile) throws IOException {
        String title = (bookTitle == null || bookTitle.trim().isEmpty() ? "Protocol Book" : bookTitle.trim()) + " — Manual Changes";
        List<ManualChanges.Change> changes = ManualChanges.all(protocols, overrides, labels);

        Map<String, Protocol> byNumber = new LinkedHashMap<String, Protocol>();
        for (Protocol p : protocols) if (p.getMetadata() != null) byNumber.put(p.getMetadata().getProtocolNumber(), p);
        // section ("Adult - Head") -> protocol number -> its changes, in protocol-number order
        Map<String, Map<String, List<ManualChanges.Change>>> sections = new LinkedHashMap<String, Map<String, List<ManualChanges.Change>>>();
        for (String bucket : new String[] {"Adult", "Peds"})
            for (ManualChanges.Change c : changes) {
                Protocol p = byNumber.get(c.protocolNumber);
                if (!bucket.equals(ProtocolNumbers.isPediatricProtocol(p) ? "Peds" : "Adult")) continue;
                sections.computeIfAbsent(bucket + " — " + category(c.protocolNumber, labels), k -> new LinkedHashMap<String, List<ManualChanges.Change>>())
                        .computeIfAbsent(c.protocolNumber, k -> new ArrayList<ManualChanges.Change>()).add(c);
            }
        int protocolCount = 0;
        for (Map<String, List<ManualChanges.Change>> s : sections.values()) protocolCount += s.size();

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>").append(HtmlSupport.esc(title)).append("</title>\n");
        html.append("<style>").append(css()).append("</style>\n</head>\n<body>\n");
        html.append("<h1>").append(HtmlSupport.esc(title)).append("</h1>\n");
        html.append("<p class=\"meta\">Generated ").append(new SimpleDateFormat("MMMM d, yyyy").format(new Date())).append(" &middot; ")
                .append(changes.size()).append(" change(s) on ").append(protocolCount).append(" protocol(s)</p>\n");
        html.append("<p class=\"intro\">These values were set by hand for the protocol book and differ from what is saved on the scanner. "
                + "The scanner still has its own values &mdash; update the protocol at the console if the scanner should match.</p>\n");
        if (changes.isEmpty()) html.append("<p>No manual changes.</p>\n");

        for (Map.Entry<String, Map<String, List<ManualChanges.Change>>> section : sections.entrySet()) {
            html.append("<h2>").append(HtmlSupport.esc(section.getKey())).append("</h2>\n");
            for (List<ManualChanges.Change> rows : section.getValue().values()) {
                ManualChanges.Change first = rows.get(0);
                html.append("<div class=\"protocol\">\n<h3>").append(HtmlSupport.esc(first.protocolNumber)).append(" &mdash; ")
                        .append(HtmlSupport.esc(first.protocolName)).append("</h3>\n");
                html.append("<table>\n<tr><th class=\"where\">Where</th><th class=\"setting\">Setting</th><th class=\"old\">Scanner</th><th>Changed to</th></tr>\n");
                for (ManualChanges.Change c : rows) {
                    html.append("<tr><td>").append(HtmlSupport.esc(c.where)).append("</td><td>").append(HtmlSupport.esc(c.setting))
                            .append("</td><td class=\"old\">").append(c.scannerValue == null || c.scannerValue.trim().isEmpty() ? "&mdash;" : multiline(c.scannerValue))
                            .append("</td><td class=\"new\">").append(multiline(c.newValue)).append("</td></tr>\n");
                }
                html.append("</table>\n</div>\n");
            }
        }
        html.append("</body>\n</html>\n");
        ProtocolBookPdfWriter.render(html.toString(), outFile);
        return outFile;
    }

    private static String multiline(String s) {
        return HtmlSupport.esc(s).replace("\n", "<br/>");
    }

    private static String category(String number, LabelConfig labels) {
        try {
            String label = labels.categoryForNumber(Integer.parseInt(number.split("\\.")[0]));
            if (label != null) return label;
        } catch (Exception ignored) {}
        return "Other";
    }

    private String css() {
        return "@page{size:letter;margin:.6in .55in .7in;"
                + "@bottom-center{content:'Page ' counter(page) ' of ' counter(pages);font-family:sans-serif;font-size:8pt;color:#777;}}"
                + "body{font-family:sans-serif;font-size:9.5pt;color:#222;}"
                + "h1{color:" + theme.getPrimary() + ";font-size:18pt;margin:0 0 4px;border-bottom:3px solid " + theme.getAccent() + ";padding-bottom:4px;}"
                + "h2{color:" + theme.getPrimary() + ";font-size:13pt;margin:16px 0 4px;}"
                + "h3{font-size:10pt;margin:8px 0 3px;}"
                + ".meta{color:#555;margin:2px 0;}"
                + ".intro{background:" + theme.getAccentTint() + ";border:1px solid " + theme.getAccent() + ";padding:5px 8px;margin:8px 0;}"
                + ".protocol{page-break-inside:avoid;}"
                + "table{border-collapse:collapse;width:100%;margin:2px 0 8px;table-layout:fixed;}"
                + "th,td{border:1px solid #ccd5df;padding:2px 5px;text-align:left;font-size:8.5pt;vertical-align:top;}"
                + "th{background:" + theme.getPrimary() + ";color:#fff;}"
                + "th.where{width:26%;}th.setting{width:22%;}th.old{width:20%;}td{word-wrap:break-word;}"
                + "td.old{color:#666;}td.new{font-weight:bold;}";
    }
}
