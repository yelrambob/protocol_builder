package com.protocolbook.parser;

import com.protocolbook.model.Protocol;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursively finds GE protocol folders (any directory containing protocolmetadata.json
 * and UIRx.xml) under a root and parses each one. Layout below the root doesn't matter -
 * folders can be nested however they were exported/copied.
 */
public class ProtocolFolderWalker implements ProtocolParser {
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
        return result;
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
}
