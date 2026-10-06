package com.protocolbook.parser;

import com.protocolbook.html.ProtocolBookHtmlWriter;
import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Acquisition;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Reconstruction;
import com.protocolbook.model.Series;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SiemensWorkbookParserTest {
    // The export's raw columns, with the running-number suffix Excel's XML map gives them.
    private static final String[] COLUMNS = {"FolderName", "BodySize", "RegionName", "ProtocolName", "ScanType", "Range2", "RefKV3", "Voltage4",
            "QualityRefMAs5", "CustomMAs6", "CAREkV9", "Care11", "CareDoseType12", "CTDIw13", "RotTime18", "Delay20", "PitchFactor21", "Acq.23",
            "SeriesDescription30", "ReconSliceEffective31", "ReconIncr32", "NoOfImages33", "Kernel34", "Window35", "Transfer138", "Transfer239",
            "SliceEffective46"};

    private static File workbook(Path dir, List<Map<String, Object>> rows) throws Exception {
        File f = dir.resolve("siemens.xlsx").toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            // label copies before the raw block, as in the real export workbook - must be ignored
            header.createCell(0).setCellValue("Protocol");
            header.createCell(1).setCellValue("SeriesDescription");
            for (int i = 0; i < COLUMNS.length; i++) header.createCell(i + 2).setCellValue(COLUMNS[i]);
            int r = 1;
            for (Map<String, Object> values : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue("Protocol name");
                row.createCell(1).setCellValue("Series description");
                for (int i = 0; i < COLUMNS.length; i++) {
                    Object v = values.get(COLUMNS[i]);
                    if (v instanceof Number) row.createCell(i + 2).setCellValue(((Number) v).doubleValue());
                    else if (v != null) row.createCell(i + 2).setCellValue(v.toString());
                }
            }
            try (FileOutputStream out = new FileOutputStream(f)) { wb.write(out); }
        }
        return f;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsFor(String size, String region, String name, Object[][] perRange) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] range : perRange) {
            String scanType = (String) range[0];
            for (int i = 1; i < range.length; i += 2) {
                Map<String, Object> m = new HashMap<>();
                m.put("FolderName", "Customized scan protocols");
                m.put("BodySize", size);
                m.put("RegionName", region);
                m.put("ProtocolName", name);
                m.put("ScanType", scanType);
                Object value = range[i + 1];
                if (value instanceof Map) m.putAll((Map<String, Object>) value);
                else m.put((String) range[i], value);
                out.add(m);
            }
        }
        return out;
    }

    private static Map<String, Object> recon(String description, double thickness, Double increment, String kernel, String window, String... sendTo) {
        Map<String, Object> m = new HashMap<>();
        m.put("SeriesDescription30", description);
        m.put("ReconSliceEffective31", thickness);
        if (increment != null) m.put("ReconIncr32", increment);
        m.put("Kernel34", kernel);
        m.put("Window35", window);
        if (sendTo.length > 0) m.put("Transfer138", sendTo[0]);
        if (sendTo.length > 1) m.put("Transfer239", sendTo[1]);
        return m;
    }

    private static List<Map<String, Object>> sample() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(rowsFor("Child", "Abdomen", "NYU_CH_ABDOMEN_PELVIS_CONTRAST (Child)", new Object[][] {
                {"Topo", "Range2", "Topogram", "Voltage4", 120, "CustomMAs6", 117, "CTDIw13", 0.07, "PitchFactor21", 0,
                        "r", recon("Topogram  1.0  Tr20", 1, null, "Tr20", "Topogram Body")},
                {"Scan", "Range2", "Abdomen", "RefKV3", "120", "Voltage4", 120, "QualityRefMAs5", 140, "CustomMAs6", 60, "CAREkV9", "Semi",
                        "Care11", "On", "CareDoseType12", "CARE Dose4D", "CTDIw13", 7.53, "RotTime18", 0.5, "Delay20", 80, "PitchFactor21", 1.4,
                        "Acq.23", "128x0.6mm",
                        "r", recon("Abdomen  4.0  Br38  1", 4, 4.0, "Br38", "Abdomen", "PROD_PACS"),
                        "r", recon("Abdomen  3.0  MPR  cor", 3, 3.0, "Br38", "Abdomen", "PROD_PACS", "SYNGO_VIA"),
                        "r", recon("Abdomen  3.0  MPR  cor", 3, 3.0, "Br60", "Bone", "PROD_PACS"),
                        "SliceEffective46", 4}}));
        rows.addAll(rowsFor("Adult", "Neck", "NECK_AND_CHEST (Adult)", new Object[][] {
                {"Scan", "Range2", "Neck", "Voltage4", 100, "Care11", "Off", "PitchFactor21", 0.8, "r", recon("Neck 2.0 Br40", 2, 2.0, "Br40", "Neck")}}));
        rows.addAll(rowsFor("Adult", "UpperExtremities", "NECK_AND_CHEST (Adult)", new Object[][] {
                {"Scan", "Range2", "Neck", "Voltage4", 100, "PitchFactor21", 0.8, "r", recon("Neck 2.0 Br40", 2, 2.0, "Br40", "Neck")}}));
        rows.addAll(rowsFor("Adult", "Private", "CatphanTests_Customized (Adult)", new Object[][] {
                {"Scan", "Range2", "Phantom", "Voltage4", 120, "PitchFactor21", 0, "r", recon("Phantom 5.0 Br40", 5, 5.0, "Br40", "Abdomen")}}));
        return rows;
    }

    @Test void readsRangesReconsAndCareDoseFromTheFlattenedExport(@TempDir Path dir) throws Exception {
        File f = workbook(dir, sample());
        assertTrue(SiemensWorkbookParser.looksLikeSiemens(f));
        assertEquals(ScannerFormat.SIEMENS_SOMATOM, ScannerFormat.detect(f));

        List<Protocol> protocols = new SiemensWorkbookParser().parse(f);
        assertEquals(4, protocols.size(), "the same name under two regions is two protocols");
        Protocol abd = protocols.get(0);
        assertEquals("Abdomen/NYU_CH_ABDOMEN_PELVIS_CONTRAST (Child)", abd.getMetadata().getProtocolNumber(), "key for protocol-overrides.json");
        assertEquals("", abd.getMetadata().getDisplayNumber(), "no number shown");
        assertEquals(16, abd.getMetadata().getSection(), "child abdomen: ABD/PEL, peds side");
        assertEquals("Pediatric", abd.getMetadata().getPatientType());

        assertEquals(2, abd.getSeries().size());
        Series topo = abd.getSeries().get(0), scan = abd.getSeries().get(1);
        assertEquals("Scout", topo.getScanType());
        assertEquals("Spiral", scan.getScanType());
        Acquisition a = scan.getGroups().get(0).getAcquisition();
        assertEquals("120", a.getKv());
        assertEquals("60", a.getMa());
        assertEquals("mAs", a.getMaUnit());
        assertEquals("140", a.getQualityRefMas());
        assertEquals("CARE Dose4D, CARE kV Semi", a.getDoseModulation());
        assertEquals("1.4", a.getPitch());
        assertEquals(1.4, a.pitchRatio(), 1e-9);
        assertEquals("80", a.getScanDelay());
        assertEquals("128x0.6mm", a.getDetector());
        assertEquals("4", a.getSliceThickness(), "a range setting after its recons still belongs to the range");
        assertEquals(7.53, scan.getGroups().get(0).getDose().getCtdi(), 1e-9);
        assertEquals("120", abd.getAdvanced().get("Series 2 (Abdomen) Ref. kV"));

        List<Reconstruction> recons = scan.getGroups().get(0).getReconstructions();
        assertEquals(3, recons.size());
        assertEquals("Abdomen 4.0 Br38 1", recons.get(0).getName(), "spaces collapsed");
        assertEquals("Abdomen 3.0 MPR cor (Br38)", recons.get(1).getName(), "repeated names get their kernel, so changes can tell them apart");
        assertEquals("Abdomen 3.0 MPR cor (Br60)", recons.get(2).getName());
        assertTrue(recons.get(1).isDerived());
        assertEquals(Arrays.asList("PROD_PACS", "SYNGO_VIA"), recons.get(1).getSendDestinations());
        assertEquals("Abdomen", recons.get(0).getWindowName());
        assertEquals("SAFIRE 1", recons.get(0).getIterativeConfig(), "strength the scanner adds after the kernel");
        assertNull(recons.get(1).getIterativeConfig(), "an MPR description has no strength");

        Protocol neck = protocols.get(1);
        assertEquals("Neck/NECK_AND_CHEST (Adult)", neck.getMetadata().getProtocolNumber());
        assertEquals("Dose modulation off", neck.getSeries().get(0).getGroups().get(0).getAcquisition().getDoseModulation());
        assertEquals(4, protocols.get(2).getMetadata().getSection(), "UpperExtremities -> Upper Ext.");
        assertEquals(23, protocols.get(3).getMetadata().getSection(), "Private (phantom tests)");
    }

    @Test void safireStrengthOnlyRightAfterTheKernel() {
        assertEquals("SAFIRE 3", SiemensWorkbookParser.safire("2.0 Br38 3 AX ST", "Br38"));
        assertEquals("SAFIRE 2", SiemensWorkbookParser.safire("Abdomen  4.0  Br40  2", "Br40"));
        assertNull(SiemensWorkbookParser.safire("Head 4.0 Hr38", "Hr38"));
        assertNull(SiemensWorkbookParser.safire("1.0 Hr60 ax", "Hr60"));
        assertNull(SiemensWorkbookParser.safire("InnerEarUHR 0.4 Ur68 RT SIDE", "Ur68"));
        assertNull(SiemensWorkbookParser.safire("Head 5.0 Hc40 40X0.6SPI", "Hc40"));
        assertNull(SiemensWorkbookParser.safire("Thorax 4.0 Br38 12", "Br38"), "not a strength (1-5)");
        assertNull(SiemensWorkbookParser.safire("Topogram 1.0 Tr20", "Tr20"));
    }

    @Test void theBookShowsSiemensSettingsWithoutNumbersOrInventedContrast(@TempDir Path dir) throws Exception {
        List<Protocol> protocols = new SiemensWorkbookParser().parse(workbook(dir, sample()));
        File out = dir.resolve("book.html").toFile();
        LabelConfig labels = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), labels, null, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains(">NYU_CH_ABDOMEN_PELVIS_CONTRAST (Child)</a>"), "sidebar shows the name alone");
        assertTrue(html.contains("<h2>NYU_CH_ABDOMEN_PELVIS_CONTRAST (Child)</h2>"));
        assertTrue(html.contains(">Peds (1)<"));
        assertTrue(html.contains("120 kV &middot; 60 mAs (quality ref. 140 mAs) &middot; CARE Dose4D, CARE kV Semi &middot; pitch 1.400:1"), html);
        assertTrue(html.contains("Scan delay: 80 sec"));
        assertFalse(html.contains("Protocol without contrast"), "the export says nothing about contrast");
        assertTrue(html.contains("<td>Abdomen</td>"), "named window preset in the WW/WL column");
        assertTrue(html.contains("<td>SAFIRE 1</td>"), "SAFIRE strength in the ASIR column");
        assertTrue(html.contains("<th>Plane</th><th>kV</th><th>mAs</th>"));
        assertFalse(html.contains("CatphanTests"), "phantom tests stay out of the book");
    }
}
