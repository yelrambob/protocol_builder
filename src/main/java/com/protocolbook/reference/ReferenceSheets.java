package com.protocolbook.reference;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Reads the site's own hand-written protocol reference workbooks (e.g. AMG_CT_Protocols_Adult.xlsm,
 * AMG_Protocols_PEDS.xlsx) - one sheet per protocol, label in column A, one column per phase:
 *
 *   Phase                 | NON-CONTRAST                  | POST-CONTRAST 70 sec. Delay
 *   Scan Range/Direction  | Diaphragm to Ischial Tub.     | Diaphragm to Ischial Tub.
 *
 * Only the scan range is taken from them (it isn't in the scanner export at all). Sheets without
 * a "Scan Range..." row (table of contents, change logs, color keys) are skipped. These sheets
 * carry no protocol numbers, so {@link ScanRangeMatcher} pairs them with scanner protocols by name.
 */
public final class ReferenceSheets {
    private ReferenceSheets() {}

    /** One protocol sheet's scan ranges, in phase order. */
    public static final class Sheet {
        public final String workbook, name;
        public final boolean pediatric;
        /** Phase label (may be null when the sheet leaves it blank) -> scan range, in column order. */
        public final List<String[]> ranges = new ArrayList<String[]>();

        Sheet(String workbook, String name, boolean pediatric) {
            this.workbook = workbook; this.name = name; this.pediatric = pediatric;
        }

        /**
         * One line per phase ("NON-CONTRAST: Diaphragm to ..."), or just the range when every
         * phase uses the same one - the common case, where repeating it per phase is noise.
         */
        public List<String> lines() {
            Set<String> distinct = new LinkedHashSet<String>();
            for (String[] r : ranges) distinct.add(r[1].toLowerCase(Locale.ROOT));
            List<String> out = new ArrayList<String>();
            if (distinct.size() == 1) { out.add(ranges.get(0)[1]); return out; }
            for (String[] r : ranges) out.add(r[0] == null ? r[1] : r[0] + ": " + r[1]);
            return out;
        }
    }

    /** Every .xlsx/.xlsm/.xls in a folder (Excel's "~$" lock files skipped); empty if the folder isn't there. */
    public static List<File> workbooksIn(File folder) {
        List<File> out = new ArrayList<File>();
        File[] files = folder == null ? null : folder.listFiles();
        if (files == null) return out;
        Arrays.sort(files);
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (f.isFile() && !n.startsWith("~$") && (n.endsWith(".xlsx") || n.endsWith(".xlsm") || n.endsWith(".xls"))) out.add(f);
        }
        return out;
    }

    public static List<Sheet> load(List<File> workbooks) throws IOException {
        List<Sheet> out = new ArrayList<Sheet>();
        for (File f : workbooks) out.addAll(load(f));
        return out;
    }

    public static List<Sheet> load(File workbook) throws IOException {
        List<Sheet> out = new ArrayList<Sheet>();
        DataFormatter formatter = new DataFormatter(Locale.US);
        boolean pedsWorkbook = isPedsName(workbook.getName());
        try (InputStream in = new BufferedInputStream(new FileInputStream(workbook)); Workbook wb = WorkbookFactory.create(in)) {
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            for (org.apache.poi.ss.usermodel.Sheet s : wb) {
                if (wb.isSheetHidden(wb.getSheetIndex(s))) continue;
                Sheet sheet = new Sheet(workbook.getName(), s.getSheetName().trim(), pedsWorkbook || isPedsName(s.getSheetName()));
                Row phaseRow = null;
                for (Row row : s) {
                    String label = text(formatter, evaluator, row.getCell(0)).toLowerCase(Locale.ROOT);
                    if (label.equals("phase") || label.equals("parameters")) phaseRow = row;
                    else if (label.startsWith("scan range")) {
                        for (int c = 1; c < row.getLastCellNum(); c++) {
                            String range = clean(text(formatter, evaluator, row.getCell(c)));
                            if (range.isEmpty()) continue;
                            String phase = phaseRow == null ? "" : clean(text(formatter, evaluator, phaseRow.getCell(c)));
                            sheet.ranges.add(new String[] { phase.isEmpty() ? null : phase, range });
                        }
                        break;
                    }
                }
                if (!sheet.ranges.isEmpty()) out.add(sheet);
            }
        } catch (EncryptedDocumentException e) {
            throw new IOException("Reference workbook '" + workbook + "' is password protected. Save an unprotected copy and try again.", e);
        } catch (IOException e) {
            throw new IOException("Could not read reference workbook '" + workbook + "'. Close it in Excel and try again.", e);
        }
        return out;
    }

    private static boolean isPedsName(String name) {
        // not \b: "_" counts as a word character, and file names like AMG_Protocols_PEDS.xlsx use it as a separator
        return name.toUpperCase(Locale.ROOT).matches("(.*[^A-Z])?PEDS?([^A-Z].*)?|.*PEDIATRIC.*");
    }

    private static String text(DataFormatter formatter, FormulaEvaluator evaluator, Cell c) {
        if (c == null) return "";
        try { return formatter.formatCellValue(c, evaluator).trim(); } catch (RuntimeException e) { return formatter.formatCellValue(c).trim(); }
    }

    private static String clean(String s) {
        return s.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}
