package com.protocolbook.parser;

import com.protocolbook.model.Protocol;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GEWorkbookParserTest {
    @TempDir Path temp;

    @Test void parsesProtocolAndPreservesUnknownFields() throws Exception {
        File file = temp.resolve("Protocols.xlsm").toFile();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet cover = wb.createSheet("Instructions"); row(cover,0,"Read me");
            Sheet s = wb.createSheet("Chest CTA");
            row(s,0,"Protocol Name","Chest CTA"); row(s,1,"Patient Position","Supine");
            row(s,2,"kVp",120); row(s,3,"CTDIvol","8.5 mGy"); row(s,4,"Technologist Note","Check IV access");
            row(s,5,"GE Proprietary Toggle","On");
            row(s,7,"Series Number","Series Description","Recon Kernel","Thickness","Plane");
            row(s,8,1,"Arterial","STANDARD","1.25","Axial");
            try (OutputStream out = new FileOutputStream(file)) { wb.write(out); }
        }
        List<Protocol> result = new GEWorkbookParser().parse(file);
        assertEquals(1, result.size()); Protocol p = result.get(0);
        assertEquals("Chest CTA", p.getMetadata().getName()); assertEquals("120", p.getAcquisition().getKv());
        assertEquals(8.5, p.getDose().getCtdi()); assertEquals(1, p.getSeries().size()); assertEquals(1, p.getReconstructions().size());
        assertEquals(1, p.getSeries().get(0).getGroups().size());
        assertEquals("STANDARD", p.getSeries().get(0).getGroups().get(0).getReconstructions().get(0).getKernel());
        assertTrue(p.getNotes().get(0).contains("Check IV access"));
        assertTrue(p.getAdvanced().values().contains("On"));
    }

    @Test void reportsMissingWorkbookActionably() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new GEWorkbookParser().parse(temp.resolve("missing.xlsm").toFile()));
        assertTrue(e.getMessage().contains("Workbook not found")); assertTrue(e.getMessage().contains("gradlew run"));
    }

    private static void row(Sheet s,int n,Object... values){Row r=s.createRow(n);for(int i=0;i<values.length;i++){Cell c=r.createCell(i);Object v=values[i];if(v instanceof Number)c.setCellValue(((Number)v).doubleValue());else c.setCellValue(String.valueOf(v));}}
}
