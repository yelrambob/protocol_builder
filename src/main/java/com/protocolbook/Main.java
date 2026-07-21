package com.protocolbook;

import com.protocolbook.model.Protocol;
import com.protocolbook.parser.GEWorkbookParser;
import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        File input = new File(args.length == 0 ? "Protocols.xlsm" : args[0]);
        try {
            List<Protocol> protocols = new GEWorkbookParser().parse(input);
            System.out.println("Parsed " + protocols.size() + " protocol(s) from " + input.getAbsolutePath());
            for (Protocol p : protocols) {
                String name = p.getMetadata() == null ? "(unnamed)" : p.getMetadata().getName();
                System.out.printf("- %s: %d series, %d reconstructions, %d notes, %d advanced fields%n", name, p.getSeries().size(), p.getReconstructions().size(), p.getNotes().size(), p.getAdvanced().size());
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }
}
