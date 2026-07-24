package com.protocolbook;

import com.protocolbook.io.ProtocolJsonWriter;
import com.protocolbook.model.Group;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Series;
import com.protocolbook.parser.GEWorkbookParser;
import com.protocolbook.parser.ProtocolFolderWalker;
import com.protocolbook.parser.ProtocolParser;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        File input = new File(args.length == 0 ? "Protocols.xlsm" : args[0]);
        try {
            ProtocolParser parser = input.isDirectory() ? new ProtocolFolderWalker() : new GEWorkbookParser();
            List<Protocol> protocols = parser.parse(input);
            System.out.println("Parsed " + protocols.size() + " protocol(s) from " + input.getAbsolutePath());
            for (Protocol p : protocols) {
                String name = p.getMetadata() == null ? "(unnamed)" : p.getMetadata().getName();
                System.out.printf("- %s: %d series, %d reconstructions, %d notes, %d advanced fields%n",
                        name, p.getSeries().size(), reconstructionCount(p), p.getNotes().size(), p.getAdvanced().size());
            }
            if (args.length > 1) {
                File outDir = new File(args[1]);
                new ProtocolJsonWriter().writeAll(protocols, outDir);
                System.out.println("Wrote combined JSON to " + outDir.getAbsolutePath());
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
