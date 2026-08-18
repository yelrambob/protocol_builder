package com.protocolbook.html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-typed log of what changed on a protocol and why. Not derived from the scanner export -
 * protocolmetadata.json's lastUpdatedDateTime only says a file changed, not what changed or why,
 * so it's no substitute for someone writing that down. Rendered as a "Recent Changes" table in
 * the protocol book, most recent entry first; each row links to that protocol's page when its
 * protocolNumber still matches one in the book.
 *
 * File format (order in the file doesn't matter - entries are sorted by date when loaded):
 * [
 *   { "date": "2026-08-15", "protocolNumber": "9.2", "note": "Increased mA range for noisy images" },
 *   { "date": "2026-08-10", "protocolNumber": "5.1", "note": "Renamed to CTA TAVR" }
 * ]
 * "date" should be yyyy-MM-dd; an entry whose date doesn't parse that way still shows, just
 * sorted after every entry that does. "protocolNumber" is optional - omit it for a note that
 * isn't about one specific protocol (e.g. a site-wide contrast injector recalibration).
 */
public final class Changelog {
    private Changelog() {}

    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    public static final class Entry {
        public final String date;
        public final String protocolNumber;
        public final String note;
        private final LocalDate parsedDate;

        public Entry(String date, String protocolNumber, String note) {
            this.date = date;
            this.protocolNumber = protocolNumber;
            this.note = note;
            this.parsedDate = parse(date);
        }

        /** Formatted for display (e.g. "Aug 15, 2026"); falls back to the raw typed string if it isn't yyyy-MM-dd. */
        public String displayDate() {
            return parsedDate != null ? DISPLAY_FORMAT.format(parsedDate) : (date == null ? "" : date);
        }

        private static LocalDate parse(String value) {
            if (value == null || value.isEmpty()) return null;
            try { return LocalDate.parse(value, INPUT_FORMAT); } catch (Exception e) { return null; }
        }
    }

    public static List<Entry> load(File file) throws IOException {
        List<Entry> out = new ArrayList<Entry>();
        if (file == null || !file.isFile()) return out;
        JSONArray json = new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        for (int i = 0; i < json.length(); i++) {
            JSONObject entry = json.getJSONObject(i);
            String note = entry.optString("note", null);
            if (note == null || note.isEmpty()) continue;
            out.add(new Entry(entry.optString("date", null), entry.optString("protocolNumber", null), note));
        }
        out.sort((a, b) -> {
            if (a.parsedDate == null && b.parsedDate == null) return 0;
            if (a.parsedDate == null) return 1;
            if (b.parsedDate == null) return -1;
            return b.parsedDate.compareTo(a.parsedDate); // newest first
        });
        return out;
    }
}
