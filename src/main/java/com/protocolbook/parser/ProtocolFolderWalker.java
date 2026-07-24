package com.protocolbook.parser;

import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;

import java.io.File;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursively finds GE protocol folders (any directory containing protocolmetadata.json
 * and UIRx.xml) under a root and parses each one. Layout below the root doesn't matter -
 * folders can be nested however they were exported/copied.
 *
 * If more than one folder maps to the same protocol number (e.g. a protocol was re-saved
 * and both the old and new export are still on disk), only the one with the most recent
 * protocolmetadata.json "lastUpdatedDateTime" is kept.
 */
public class ProtocolFolderWalker implements ProtocolParser {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx");
    private final UIRxProtocolParser protocolParser = new UIRxProtocolParser();

    public List<Protocol> parse(File root) throws Exception {
        if (root == null || !root.isDirectory()) throw new IllegalArgumentException("Not a directory: " + root);
        List<Protocol> result = new ArrayList<Protocol>();
        for (File folder : findProtocolFolders(root)) {
            try { result.add(protocolParser.parse(folder)); }
            catch (Exception e) { System.err.println("WARN: skipped '" + folder + "': " + e.getMessage()); }
        }
        if (result.isEmpty())
            throw new IllegalArgumentException("No protocol folders found under " + root.getAbsolutePath()
                    + ". Expected subfolders containing protocolmetadata.json and UIRx.xml.");
        return dedupeByProtocolNumber(result);
    }

    private List<File> findProtocolFolders(File dir) {
        List<File> out = new ArrayList<File>();
        collect(dir, out);
        return out;
    }

    private void collect(File dir, List<File> out) {
        if (new File(dir, "protocolmetadata.json").isFile() && new File(dir, "UIRx.xml").isFile()) { out.add(dir); return; }
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) if (c.isDirectory()) collect(c, out);
    }

    private List<Protocol> dedupeByProtocolNumber(List<Protocol> protocols) {
        Map<String, Protocol> byNumber = new LinkedHashMap<String, Protocol>();
        List<Protocol> unnumbered = new ArrayList<Protocol>();
        for (Protocol p : protocols) {
            String number = p.getMetadata() == null ? null : p.getMetadata().getProtocolNumber();
            if (number == null) { unnumbered.add(p); continue; }
            Protocol existing = byNumber.get(number);
            if (existing == null) { byNumber.put(number, p); continue; }
            Protocol keep = mostRecentlyUpdated(existing, p);
            Protocol drop = keep == existing ? p : existing;
            System.err.println("WARN: duplicate protocol number " + number + " - keeping the version last updated "
                    + describe(keep) + ", dropping the one last updated " + describe(drop));
            byNumber.put(number, keep);
        }
        List<Protocol> result = new ArrayList<Protocol>(byNumber.values());
        result.addAll(unnumbered);
        return result;
    }

    private Protocol mostRecentlyUpdated(Protocol a, Protocol b) {
        OffsetDateTime ta = parseTimestamp(a.getMetadata().getLastUpdated());
        OffsetDateTime tb = parseTimestamp(b.getMetadata().getLastUpdated());
        if (ta == null) return tb == null ? a : b; // can't tell; keep the first one found
        if (tb == null) return a;
        return ta.isAfter(tb) ? a : b;
    }

    private String describe(Protocol p) {
        Metadata m = p.getMetadata();
        return m.getLastUpdated() != null ? m.getLastUpdated() : "(unknown time)";
    }

    private static OffsetDateTime parseTimestamp(String value) {
        if (value == null || value.isEmpty()) return null;
        try { return OffsetDateTime.parse(value, TIMESTAMP_FORMAT); }
        catch (Exception e) { return null; }
    }
}
