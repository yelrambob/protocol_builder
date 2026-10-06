package com.protocolbook.parser;

import java.io.File;
import java.util.Locale;

/**
 * Which scanner (and export) the protocols come from - picked on the GUI's first screen, or --format on
 * the command line (which otherwise works it out from the input). Formats not supported yet are listed
 * so they can be picked and explain what's needed, rather than silently reading the wrong thing.
 */
public enum ScannerFormat {
    GE_REVOLUTION("GE Revolution — exported protocol folder", "ge-folder", true, true,
            "The folder the protocols were exported to (each protocol is a subfolder with protocolmetadata.json and UIRx.xml)."),
    GE_WORKBOOK("GE — Protocols.xlsm workbook", "ge-workbook", true, false,
            "A Protocols.xlsm/.xlsx workbook with one sheet per protocol."),
    SIEMENS_SOMATOM("Siemens SOMATOM — protocol export workbook (.xlsm/.xlsx)", "siemens", true, false,
            "The SOMATOM protocol export opened in Excel: one row per value, with FolderName, BodySize, RegionName, ProtocolName, Range, Kernel... columns."),
    SIEMENS_NEWER("Siemens — newer scanner (not supported yet)", "siemens-new", false, false,
            "Not supported yet - send a sample export from the newer scanner and it can be added."),
    GE_OLDER("GE — older protocol export (not supported yet)", "ge-old", false, false,
            "Not supported yet - send a sample export from the older GE scanner and it can be added.");

    public final String label, id, help;
    public final boolean supported, folder;

    ScannerFormat(String label, String id, boolean supported, boolean folder, String help) {
        this.label = label;
        this.id = id;
        this.supported = supported;
        this.folder = folder;
        this.help = help;
    }

    /** The reader for this format; throws for one that isn't supported yet. */
    public ProtocolParser parser() {
        switch (this) {
            case GE_REVOLUTION: return new ProtocolFolderWalker();
            case GE_WORKBOOK: return new GEWorkbookParser();
            case SIEMENS_SOMATOM: return new SiemensWorkbookParser();
            default: throw new IllegalArgumentException(label + ": " + help);
        }
    }

    /** A message if this input can't be read as this format (folder vs file, workbook type), else null. */
    public String problemWith(File input) {
        if (!supported) return help;
        if (input == null || !input.exists()) return "Can't find " + (input == null ? "the input" : input.getAbsolutePath());
        if (folder && !input.isDirectory()) return label + " needs the export folder, not a file.";
        if (!folder && input.isDirectory()) return label + " needs the workbook file, not a folder.";
        if (this == SIEMENS_SOMATOM && !SiemensWorkbookParser.looksLikeSiemens(input))
            return input.getName() + " doesn't look like a Siemens protocol export (no ProtocolName / RegionName columns found).";
        return null;
    }

    /** What the input most likely is: a folder is a GE Revolution export; a workbook is Siemens if it has the Siemens columns, else GE. */
    public static ScannerFormat detect(File input) {
        if (input.isDirectory()) return GE_REVOLUTION;
        return SiemensWorkbookParser.looksLikeSiemens(input) ? SIEMENS_SOMATOM : GE_WORKBOOK;
    }

    public static ScannerFormat byId(String id) {
        for (ScannerFormat f : values()) if (f.id.equalsIgnoreCase(id.trim()) || f.name().equalsIgnoreCase(id.trim())) return f;
        StringBuilder ids = new StringBuilder();
        for (ScannerFormat f : values()) ids.append(ids.length() == 0 ? "" : ", ").append(f.id);
        throw new IllegalArgumentException("Unknown --format '" + id + "' - use one of " + ids.toString().toLowerCase(Locale.ROOT));
    }

    @Override public String toString() { return label; }
}
