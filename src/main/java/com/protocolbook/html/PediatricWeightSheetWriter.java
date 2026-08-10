package com.protocolbook.html;

import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Printable quick-reference of pediatric protocols, sorted by protocol number, with any
 * weight-in-kg found in the protocol name annotated with its pound equivalent - pediatric
 * protocol names are conventionally weight-banded (e.g. "CT CHEST &lt;5KG", "CT ABD 10-20KG").
 *
 * Best-effort regex, not an exhaustive parser of every phrasing GE sites use: handles a bare
 * "NNKG", a comparison-prefixed "&lt;NNKG"/"&gt;=NNKG", and a range "NN-NNKG"/"NN to NNKG".
 * If a real protocol name doesn't get picked up, the pattern needs extending, not the whole
 * design rethought.
 */
public class PediatricWeightSheetWriter {
    private static final double KG_TO_LB = 2.20462;
    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
            "(?<lo>\\d+(?:\\.\\d+)?)\\s*(?:-|to)\\s*(?<hi>\\d+(?:\\.\\d+)?)\\s*KGS?\\b"
            + "|(?<cmp><=|>=|<|>)?\\s*(?<single>\\d+(?:\\.\\d+)?)\\s*KGS?\\b",
            Pattern.CASE_INSENSITIVE);

    public File write(List<Protocol> protocols, File outFile) throws IOException {
        List<Protocol> peds = new ArrayList<Protocol>();
        for (Protocol p : protocols) if (isPediatric(p)) peds.add(p);
        peds.sort(Comparator.comparingDouble(this::sortKey));

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n<title>Pediatric Protocols - Weight Reference</title>\n");
        html.append("<style>").append(CSS).append("</style>\n</head>\n<body>\n");
        html.append("<h1>Pediatric Protocols &ndash; Weight Reference</h1>\n");
        html.append("<p class=\"subtitle\">").append(peds.size())
                .append(" pediatric protocol(s). Pound equivalents shown in parentheses next to each weight found in the name.</p>\n");
        html.append("<table>\n<tr><th>#</th><th>Protocol</th><th>Body part</th></tr>\n");
        for (Protocol p : peds) {
            Metadata m = p.getMetadata();
            html.append("<tr><td>").append(esc(m == null ? null : m.getProtocolNumber())).append("</td><td>")
                    .append(annotateWeights(m == null ? null : m.getName())).append("</td><td>")
                    .append(esc(m == null ? null : m.getBodyPart())).append("</td></tr>\n");
        }
        html.append("</table>\n</body>\n</html>\n");

        if (outFile.getParentFile() != null && !outFile.getParentFile().isDirectory()) outFile.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(outFile)) { w.write(html.toString()); }
        return outFile;
    }

    private boolean isPediatric(Protocol p) {
        String type = p.getMetadata() == null ? null : p.getMetadata().getPatientType();
        return type != null && type.toLowerCase(Locale.ROOT).contains("pediatric");
    }

    private double sortKey(Protocol p) {
        String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
        if (number == null) return Double.MAX_VALUE;
        String[] parts = number.split("\\.", 2);
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major + minor / 10000.0;
        } catch (Exception e) { return Double.MAX_VALUE; }
    }

    private String annotateWeights(String name) {
        if (name == null) return "";
        Matcher matcher = WEIGHT_PATTERN.matcher(name);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(esc(name.substring(last, matcher.start()))).append(esc(matcher.group()));
            if (matcher.group("lo") != null) {
                out.append(" (").append(kgToLb(matcher.group("lo"))).append('-').append(kgToLb(matcher.group("hi"))).append(" lb)");
            } else {
                out.append(" (").append(kgToLb(matcher.group("single"))).append(" lb)");
            }
            last = matcher.end();
        }
        out.append(esc(name.substring(last)));
        return out.toString();
    }

    private long kgToLb(String kg) {
        return Math.round(Double.parseDouble(kg) * KG_TO_LB);
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String CSS =
            "body{font-family:sans-serif;max-width:900px;margin:2rem auto;padding:0 1rem;}" +
            "h1{margin-bottom:.25rem;}" +
            ".subtitle{color:#555;margin-top:0;}" +
            "table{border-collapse:collapse;width:100%;}" +
            "th,td{border:1px solid #ccc;padding:.4rem .6rem;text-align:left;font-size:.95rem;}" +
            "th{background:#f0f0f0;}" +
            "@media print{body{margin:0;max-width:none;}tr{break-inside:avoid;}}";
}
