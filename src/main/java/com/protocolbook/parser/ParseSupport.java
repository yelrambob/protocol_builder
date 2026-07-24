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

    static void putAdvanced(Protocol p, String key, String value) {
        if (value == null || value.isEmpty()) return;
        String k = key; int i = 2;
        while (p.getAdvanced().containsKey(k)) k = key + " (" + (i++) + ")";
        p.getAdvanced().put(k, value);
    }
}
