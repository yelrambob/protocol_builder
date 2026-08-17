package com.protocolbook.manual;

import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManualProtocolsTest {

    @Test void loadsFieldsAndFreeTextNotes(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("manual-protocols.json").toFile();
        try (FileWriter w = new FileWriter(file)) {
            w.write("[ { \"protocolNumber\": \"9.9\", \"name\": \"CT LWR EXT CUSTOM\", \"patientType\": \"adult\", "
                    + "\"bodyPart\": \"lower Extremities\", \"notes\": \"Not yet on the scanner.\" } ]");
        }

        List<Protocol> loaded = ManualProtocols.load(file);
        assertEquals(1, loaded.size());
        Protocol p = loaded.get(0);
        assertEquals("9.9", p.getMetadata().getProtocolNumber());
        assertEquals("CT LWR EXT CUSTOM", p.getMetadata().getName());
        assertEquals("adult", p.getMetadata().getPatientType());
        assertEquals("lower Extremities", p.getMetadata().getBodyPart());
        assertEquals(1, p.getNotes().size());
        assertTrue(p.getNotes().get(0).contains("Not yet on the scanner."));
        assertTrue(p.getSeries().isEmpty(), "manual protocols have no scan data");
    }

    @Test void returnsEmptyListWhenFileMissing() throws Exception {
        assertTrue(ManualProtocols.load(new File("/no/such/file.json")).isEmpty());
    }

    @Test void mergeAddsManualProtocolsAlongsideDiscoveredOnes() {
        Protocol discovered = protocolWithNumber("9.2");
        Protocol manual = protocolWithNumber("9.9");

        List<Protocol> merged = ManualProtocols.merge(List.of(discovered), List.of(manual));

        assertEquals(2, merged.size());
    }

    @Test void manualProtocolWinsOnProtocolNumberCollision() {
        Protocol discovered = protocolWithNumber("9.2");
        discovered.getMetadata().setName("FROM SCANNER");
        Protocol manual = protocolWithNumber("9.2");
        manual.getMetadata().setName("FROM MANUAL FILE");

        List<Protocol> merged = ManualProtocols.merge(List.of(discovered), List.of(manual));

        assertEquals(1, merged.size(), "colliding protocol number should collapse to one entry");
        assertEquals("FROM MANUAL FILE", merged.get(0).getMetadata().getName(), "manual entry should win on collision");
    }

    private static Protocol protocolWithNumber(String number) {
        Protocol p = new Protocol();
        Metadata m = new Metadata();
        m.setProtocolNumber(number);
        p.setMetadata(m);
        return p;
    }
}
