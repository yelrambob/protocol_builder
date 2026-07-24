package com.protocolbook.parser;

import com.protocolbook.model.Protocol;

/** Tolerant numeric parsing and de-duplicated "advanced" field storage shared by all parsers. */
final class ParseSupport {
    private ParseSupport() {}

    static Double decimal(String s, Protocol p, String field) {
        if (s == null || s.isEmpty()) return null;
        try { return Double.valueOf(s.replaceAll("[^0-9.+-]", "")); }
        catch (Exception e) { p.getNotes().add("Could not parse numeric " + field + ": " + s); return null; }
    }

    static Integer integer(String s, Protocol p, String field) {
        if (s == null || s.isEmpty()) return null;
        try { return (int) Double.parseDouble(s.replaceAll("[^0-9.+-]", "")); }
        catch (Exception e) { p.getNotes().add("Could not parse " + field + ": " + s); return null; }
    }

    /**
     * Snaps a raw numeric string to the nearest multiple of step and formats it cleanly.
     * GE Revolution detector rows are physically 0.625mm, so slice thickness/interval values
     * are always meant to be a multiple of that - but come out of session.xml's derived
     * reformat data with floating-point noise (e.g. "2.4999996965620004" instead of "2.5").
     */
    static String roundToStep(String s, double step) {
        if (s == null || s.isEmpty()) return s;
        try {
            double value = Double.parseDouble(s.replaceAll("[^0-9.+-]", ""));
            double rounded = Math.round(value / step) * step;
            return java.math.BigDecimal.valueOf(rounded).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        } catch (Exception e) { return s; }
    }

    static void putAdvanced(Protocol p, String key, String value) {
        if (value == null || value.isEmpty()) return;
        String k = key; int i = 2;
        while (p.getAdvanced().containsKey(k)) k = key + " (" + (i++) + ")";
        p.getAdvanced().put(k, value);
    }
}
