package com.protocolbook.io;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.parser.ProtocolFolderWalker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ParameterCsvWriterTest {
    private static final LabelConfig LABELS = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());

    private static List<String> lines(File f) throws Exception {
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("﻿"), "byte-order mark so Excel reads it as UTF-8");
        return Arrays.asList(text.substring(1).split("\r\n"));
    }

    @Test void oneRowPerReconWithAsirAndHandSetValuesWinning(@TempDir Path dir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(new File("src/test/resources/sample-protocols"));
        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride knee = new ProtocolOverride();
        Map<String, String> axial = new HashMap<>();
        axial.put("asir", "40%");
        knee.getRecons().put("AXIAL KNEE DET 2.5MM", axial);
        overrides.put("9.2", knee);
        ProtocolOverride hip = new ProtocolOverride();
        hip.setExcluded(true);
        overrides.put("9.4", hip);

        File out = dir.resolve("p.csv").toFile();
        List<ParameterCsvWriter.Column> cols = Arrays.asList(ParameterCsvWriter.column("asir"), ParameterCsvWriter.column("kernel"));
        int rows = ParameterCsvWriter.write(protocols, overrides, LABELS, cols, false, out);
        List<String> lines = lines(out);
        assertEquals("Section,#,Protocol,In book,Series,Recon,Kernel,ASIR / iterative", lines.get(0), "chosen columns in their fixed order");
        assertEquals(rows + 1, lines.size());
        assertTrue(lines.contains("Adult - Lower Ext.,9.2,CT LWR EXT KNEE WITH CONTRAST,Yes,2 - AXIAL KNEE DET 2.5MM,AXIAL KNEE DET 2.5MM,8,40%"),
                "typed ASIR replaces the scanner's 50%: " + lines);
        assertTrue(lines.contains("Adult - Lower Ext.,9.2,CT LWR EXT KNEE WITH CONTRAST,Yes,2 - AXIAL KNEE DET 2.5MM,AXIAL KNEE DET 0.625 mm,8,50%"));
        for (String l : lines) assertFalse(l.contains(",9.4,"), "left-out protocol not included unless asked");

        ParameterCsvWriter.write(protocols, overrides, LABELS, cols, true, out);
        boolean leftOut = false;
        for (String l : lines(out)) leftOut |= l.contains(",9.4,") && l.contains(",Left out,");
        assertTrue(leftOut);
    }

    @Test void seriesAndProtocolLevelsAndQuoting(@TempDir Path dir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(new File("src/test/resources/sample-protocols"));
        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride knee = new ProtocolOverride();
        knee.setNotes("Pad the knee, \"feet first\"");
        overrides.put("9.2", knee);

        File out = dir.resolve("s.csv").toFile();
        ParameterCsvWriter.write(protocols, overrides, LABELS, Arrays.asList(ParameterCsvWriter.column("kv"), ParameterCsvWriter.column("notes")), false, out);
        List<String> lines = lines(out);
        assertEquals("Section,#,Protocol,In book,Series,Scanning notes,kV", lines.get(0));
        assertTrue(lines.contains("Adult - Lower Ext.,9.2,CT LWR EXT KNEE WITH CONTRAST,Yes,2 - AXIAL KNEE DET 2.5MM,\"Pad the knee, \"\"feet first\"\"\",140"), lines.toString());

        ParameterCsvWriter.write(protocols, overrides, LABELS, Arrays.asList(ParameterCsvWriter.column("contrastVolume")), false, out);
        assertEquals("Section,#,Protocol,In book,Contrast volume (mL)", lines(out).get(0));
        assertTrue(lines(out).contains("Adult - Lower Ext.,9.2,CT LWR EXT KNEE WITH CONTRAST,Yes,100"));
        assertThrows(IllegalArgumentException.class, () -> ParameterCsvWriter.column("kvp"));
    }
}
