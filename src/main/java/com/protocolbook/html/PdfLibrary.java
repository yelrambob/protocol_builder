package com.protocolbook.html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-maintained list of externally hosted PDFs (e.g. surgical planning protocols living on
 * your own server), rendered as their own sidebar category in the protocol book, separate from
 * the CT protocols. Nothing here is auto-discovered - this tool has no way to know what's on
 * your server, so the list is entirely yours to maintain.
 *
 * File format (order in the file is the order they're listed in):
 * [
 *   { "title": "Some Surgical Planning Protocol", "url": "https://example.com/pdfs/one.pdf" },
 *   { "title": "Another One", "url": "https://example.com/pdfs/two.pdf" }
 * ]
 */
public final class PdfLibrary {
    private PdfLibrary() {}

    public static final class Entry {
        public final String title;
        public final String url;
        public Entry(String title, String url) { this.title = title; this.url = url; }
    }

    public static List<Entry> load(File file) throws IOException {
        List<Entry> out = new ArrayList<Entry>();
        if (file == null || !file.isFile()) return out;
        JSONArray json = new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        for (int i = 0; i < json.length(); i++) {
            JSONObject entry = json.getJSONObject(i);
            String title = entry.optString("title", null);
            String url = entry.optString("url", null);
            if (title != null && !title.isEmpty() && url != null && !url.isEmpty()) out.add(new Entry(title, url));
        }
        return out;
    }
}
