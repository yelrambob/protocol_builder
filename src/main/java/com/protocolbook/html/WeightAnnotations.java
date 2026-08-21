package com.protocolbook.html;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Weight-in-kg parsing shared by {@link PediatricWeightSheetWriter} (which annotates every match
 * in a protocol name with its pound equivalent) and {@link ProtocolBookHtmlWriter} (which uses a
 * bare extracted label, e.g. "&lt;5KG", to tell weight-band family variants apart when they're
 * merged onto one page, and strips it out to get the family's shared name).
 *
 * Best-effort regex, not an exhaustive parser of every phrasing GE sites use: handles a bare
 * "NNKG", a comparison-prefixed "&lt;NNKG"/"&gt;=NNKG", and a range "NN-NNKG"/"NN to NNKG". If a
 * real protocol name doesn't get picked up, the pattern needs extending, not the whole design
 * rethought.
 */
final class WeightAnnotations {
    private static final double KG_TO_LB = 2.20462;
    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
            "(?<lo>\\d+(?:\\.\\d+)?)\\s*(?:-|to)\\s*(?<hi>\\d+(?:\\.\\d+)?)\\s*KGS?\\b"
            + "|(?<cmp><=|>=|<|>)?\\s*(?<single>\\d+(?:\\.\\d+)?)\\s*KGS?\\b",
            Pattern.CASE_INSENSITIVE);

    private WeightAnnotations() {}

    /** The first weight phrase found in a protocol name (e.g. "&lt;5KG", "10-20KG"), or null if none. */
    static String extractLabel(String name) {
        if (name == null) return null;
        Matcher m = WEIGHT_PATTERN.matcher(name);
        return m.find() ? m.group().trim() : null;
    }

    /** A protocol name with its weight phrase (if any) removed, for the shared name of a merged
     * weight-band family (e.g. "CT CHEST &lt;5KG" -&gt; "CT CHEST"). Untouched if no weight phrase is found. */
    static String stripLabel(String name) {
        if (name == null) return null;
        String stripped = WEIGHT_PATTERN.matcher(name).replaceAll("").trim();
        stripped = stripped.replaceAll("\\s{2,}", " ").replaceAll("[-,]+$", "").trim();
        return stripped;
    }

    /** Re-renders every weight phrase in a raw (not yet HTML-escaped) name with its pound
     * equivalent appended in parentheses, e.g. "CT CHEST &lt;5KG" -&gt; "CT CHEST &lt;5KG (11 lb)"
     * - the whole result is HTML-escaped. */
    static String annotateWeights(String name) {
        if (name == null) return "";
        Matcher matcher = WEIGHT_PATTERN.matcher(name);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            out.append(HtmlSupport.esc(name.substring(last, matcher.start()))).append(HtmlSupport.esc(matcher.group()));
            if (matcher.group("lo") != null) {
                out.append(" (").append(kgToLb(matcher.group("lo"))).append('-').append(kgToLb(matcher.group("hi"))).append(" lb)");
            } else {
                out.append(" (").append(kgToLb(matcher.group("single"))).append(" lb)");
            }
            last = matcher.end();
        }
        out.append(HtmlSupport.esc(name.substring(last)));
        return out.toString();
    }

    private static long kgToLb(String kg) {
        return Math.round(Double.parseDouble(kg) * KG_TO_LB);
    }
}
