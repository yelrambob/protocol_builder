package com.protocolbook;

import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.io.ProtocolJsonWriter;
import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.overrides.ProtocolOverrides;
import com.protocolbook.parser.GEWorkbookParser;
import com.protocolbook.parser.ProtocolFolderWalker;
import com.protocolbook.parser.ProtocolParser;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Usage: Main <input> [--json <dir>] [--html <file>] [--overrides <file>]
 * <input> is a Protocols.xlsm workbook or a folder to walk for GE protocol exports.
 * --overrides defaults to ./protocol-overrides.json if present.
 */
public class Main {
    public static void main(String[] args) {
        try {
            File input = null;
            File jsonDir = null, htmlFile = null, overridesFile = new File("protocol-overrides.json");
            boolean initOverrides = false;
            for (int i = 0; i < args.length; i++) {
                if ("--json".equals(args[i])) jsonDir = new File(args[++i]);
                else if ("--html".equals(args[i])) htmlFile = new File(args[++i]);
                else if ("--overrides".equals(args[i])) overridesFile = new File(args[++i]);
                else if ("--init-overrides".equals(args[i])) initOverrides = true;
                else if (input == null) input = new File(args[i]);
            }
            if (input == null) input = new File("Protocols.xlsm");

            ProtocolParser parser = input.isDirectory() ? new ProtocolFolderWalker() : new GEWorkbookParser();
            List<Protocol> protocols = parser.parse(input);
            System.out.println("Parsed " + protocols.size() + " protocol(s) from " + input.getAbsolutePath());
            for (Protocol p : protocols) {
                String name = p.getMetadata() == null ? "(unnamed)" : p.getMetadata().getName();
                System.out.printf("- %s: %d series, %d reconstructions, %d notes, %d advanced fields%n",
                        name, p.getSeries().size(), reconstructionCount(p), p.getNotes().size(), p.getAdvanced().size());
            }

            if (initOverrides) {
                if (overridesFile.isFile()) throw new IllegalStateException(overridesFile + " already exists; delete or rename it first, or pass --overrides <other file>");
                List<String> numbers = new java.util.ArrayList<String>();
                for (Protocol p : protocols) if (p.getMetadata() != null) numbers.add(p.getMetadata().getProtocolNumber());
                ProtocolOverrides.writeTemplate(numbers, overridesFile);
                System.out.println("Wrote starter overrides file to " + overridesFile.getAbsolutePath() + " (" + numbers.size() + " protocol(s))");
            }
            if (jsonDir != null) {
                new ProtocolJsonWriter().writeAll(protocols, jsonDir);
                System.out.println("Wrote combined JSON to " + jsonDir.getAbsolutePath());
            }
            if (htmlFile != null) {
                Map<String, ProtocolOverride> overrides = ProtocolOverrides.load(overridesFile);
                new ProtocolBookHtmlWriter().write(protocols, overrides, htmlFile);
                System.out.println("Wrote protocol book to " + htmlFile.getAbsolutePath()
                        + (overrides.isEmpty() ? "" : " (" + overrides.size() + " override(s) applied from " + overridesFile + ")"));
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }

    private static int reconstructionCount(Protocol p) {
        int count = 0;
        for (Series s : p.getSeries()) for (Group g : s.getGroups()) count += g.getReconstructions().size();
        return count;
    }
}
