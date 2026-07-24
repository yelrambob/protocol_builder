package com.protocolbook.parser;

import com.protocolbook.io.ProtocolJsonWriter;
import com.protocolbook.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test against the real, sanitized GE Revolution exports checked in under
 * "protocol data" - one folder per protocol, each holding protocolmetadata.json + UIRx.xml
 * (+ session.xml, whose CTReconTask DESCRIPTION/SELECTED_GROUP_PATHS supply the display names).
 */
class ProtocolFolderWalkerTest {
    private static final File FIXTURE_ROOT = new File("protocol data");

    @Test void walksAllRealProtocolFolders() throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        assertEquals(12, protocols.size(), "expected one Protocol per real folder, ignoring test.txt and *.lastRetain.xml");

        Protocol knee = find(protocols, "9.2");
        assertEquals("CT LWR EXT KNEE WITH CONTRAST", knee.getMetadata().getName());
        assertEquals("adult", knee.getMetadata().getPatientType());
        assertEquals("lower Extremities", knee.getMetadata().getBodyPart());
        assertEquals("Site", knee.getMetadata().getLibrary());
        assertEquals(1.51, knee.getDose().getCtdi());
        assertEquals(36.6, knee.getDose().getDlp());
        assertEquals(2, knee.getSeries().size());

        Series scout = knee.getSeries().get(0);
        assertEquals(2, scout.getGroups().size());
        assertEquals("Scout", scout.getScanType());
        assertEquals(1, scout.getGroups().get(0).getReconstructions().size());

        Series axial = knee.getSeries().get(1);
        assertEquals("Axial", axial.getScanType());
        assertEquals(1, axial.getGroups().size());
        Group group = axial.getGroups().get(0);
        assertEquals(4, group.getReconstructions().size());
        assertEquals(1.45, group.getDose().getCtdi());
        assertEquals(34.84, group.getDose().getDlp());
        assertEquals(140, Integer.parseInt(group.getAcquisition().getKv()));

        Reconstruction primaryRecon = group.getReconstructions().get(0);
        assertEquals("AXIAL KNEE DET 2.5MM", primaryRecon.getName(), "display name should come from session.xml, joined via SELECTED_GROUP_PATHS");
        assertEquals("2.5", primaryRecon.getThickness());

        assertEquals("100", axial.getContrast().getIvVolume());
        assertTrue(axial.getContrast().isIv());
    }

    @Test void writesOneCombinedJsonFilePerProtocol(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File outDir = tempDir.toFile();
        List<File> written = new ProtocolJsonWriter().writeAll(protocols, outDir);
        assertEquals(protocols.size(), written.size());
        for (File f : written) assertTrue(f.isFile() && f.length() > 0);
    }

    @Test void ignoresFoldersWithoutProtocolFiles(@TempDir Path tempDir) {
        File junkDir = tempDir.resolve("not-a-protocol").toFile();
        junkDir.mkdirs();
        assertThrows(IllegalArgumentException.class, () -> new ProtocolFolderWalker().parse(tempDir.toFile()));
    }

    private static Protocol find(List<Protocol> protocols, String protocolNumber) {
        for (Protocol p : protocols) if (protocolNumber.equals(p.getMetadata().getProtocolNumber())) return p;
        throw new AssertionError("No protocol with number " + protocolNumber);
    }
}
