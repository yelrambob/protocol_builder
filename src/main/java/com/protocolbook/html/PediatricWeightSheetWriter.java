package com.protocolbook.html;

import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Printable quick-reference of pediatric protocols, sorted by protocol number, with any
 * weight-in-kg found in the protocol name annotated with its pound equivalent - pediatric
 * protocol names are conventionally weight-banded (e.g. "CT CHEST &lt;5KG", "CT ABD 10-20KG").
 * Weight parsing itself lives in {@link WeightAnnotations}, shared with {@link ProtocolBookHtmlWriter}.
 *
 * Shares its base look with {@link ProtocolBookHtmlWriter} via {@link HtmlSupport}.
 */
public class PediatricWeightSheetWriter {
    public File write(List<Protocol> protocols, File outFile) throws IOException {
        List<Protocol> peds = new ArrayList<Protocol>();
        for (Protocol p : protocols) if (isPediatric(p)) peds.add(p);
        peds.sort((a, b) -> ProtocolNumbers.compare(protocolNumber(a), protocolNumber(b)));

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Pediatric Protocols - Weight Reference</title>\n");
        html.append("<style>").append(CSS).append("</style>\n</head>\n<body>\n");
        html.append("<h1>Pediatric Protocols &ndash; Weight Reference</h1>\n");
        html.append("<p class=\"subtitle\">").append(peds.size())
                .append(" pediatric protocol(s). Pound equivalents shown in parentheses next to each weight found in the name.</p>\n");
        html.append("<table>\n<tr><th>#</th><th>Protocol</th><th>Body part</th></tr>\n");
        for (Protocol p : peds) {
            Metadata m = p.getMetadata();
            html.append("<tr><td>").append(HtmlSupport.esc(m == null ? null : m.getProtocolNumber())).append("</td><td>")
                    .append(WeightAnnotations.annotateWeights(m == null ? null : m.getName())).append("</td><td>")
                    .append(HtmlSupport.esc(m == null ? null : m.getBodyPart())).append("</td></tr>\n");
        }
        html.append("</table>\n</body>\n</html>\n");

        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    // Primarily by protocol number shape (peds numbers carry an extra dot-separated segment,
    // e.g. "9.1.2" vs adult's "9.1" - see ProtocolNumbers), falling back to the free-text patient
    // type for protocols that don't follow that convention (e.g. hand-authored manual protocols).
    private boolean isPediatric(Protocol p) {
        Metadata m = p.getMetadata();
        if (ProtocolNumbers.isPediatric(m == null ? null : m.getProtocolNumber())) return true;
        String type = m == null ? null : m.getPatientType();
        if (type == null) return false;
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("pediatric") || t.contains("peds") || t.contains("pedi") || t.contains("child");
    }

    private String protocolNumber(Protocol p) {
        return p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
    }

    private static final String CSS = HtmlSupport.BASE_CSS;
}
