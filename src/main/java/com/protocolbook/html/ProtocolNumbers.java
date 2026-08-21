package com.protocolbook.html;

/**
 * Shared protocol-number helpers used by both {@link ProtocolBookHtmlWriter} and
 * {@link PediatricWeightSheetWriter}.
 *
 * Pediatric protocols are numbered with an extra dot-separated segment (e.g. "9.1.2") where
 * their adult counterpart is just two (e.g. "9.1") - a scanner-console convention that's a far
 * more reliable Adult/Peds signal than the free-text patient-type field, which doesn't
 * consistently spell out "pediatric" in real exports.
 */
final class ProtocolNumbers {
    private ProtocolNumbers() {}

    static boolean isPediatric(String number) {
        if (number == null) return false;
        int dots = 0;
        for (int i = 0; i < number.length(); i++) if (number.charAt(i) == '.') dots++;
        return dots >= 2;
    }

    // Compares protocol numbers segment-by-segment as integers (e.g. "9.2" < "9.10" < "9.2.1"),
    // so this works the same for the usual two-segment adult numbers and the three-segment
    // pediatric ones without one throwing off the other's ordering. A number that can't be
    // parsed this way (missing, or non-numeric segments) sorts last.
    static int compare(String a, String b) {
        int[] sa = segments(a);
        int[] sb = segments(b);
        if (sa == null && sb == null) return 0;
        if (sa == null) return 1;
        if (sb == null) return -1;
        int len = Math.min(sa.length, sb.length);
        for (int i = 0; i < len; i++) {
            int cmp = Integer.compare(sa[i], sb[i]);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(sa.length, sb.length);
    }

    private static int[] segments(String number) {
        if (number == null || number.isEmpty()) return null;
        String[] parts = number.split("\\.");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
