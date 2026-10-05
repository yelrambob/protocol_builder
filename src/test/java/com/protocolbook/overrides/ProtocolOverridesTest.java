package com.protocolbook.overrides;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolOverridesTest {

    @Test void mergeTemplateAddsOnlyMissingEntriesAndPreservesExisting(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("protocol-overrides.json").toFile();

        int firstRun = ProtocolOverrides.mergeTemplate(Arrays.asList("9.2", "9.4"), file);
        assertEquals(2, firstRun);

        // simulate hand-editing the file directly, the way a technologist would
        writeNotes(file, "9.2", "Pad under the knee.");

        int secondRun = ProtocolOverrides.mergeTemplate(Arrays.asList("9.2", "9.4", "9.6"), file);
        assertEquals(1, secondRun, "only the new protocol number 9.6 should be added");

        Map<String, ProtocolOverride> after = ProtocolOverrides.load(file);
        assertEquals(3, after.size());
        assertEquals("Pad under the knee.", after.get("9.2").getNotes(), "hand-written note must survive re-running init");
        assertNotNull(after.get("9.6"));
    }

    @Test void reconSendDestinationsLoadSurviveInitAndFlagTypos(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("protocol-overrides.json").toFile();
        java.nio.file.Files.write(file.toPath(), ("{ \"1.5\": { \"reconSendDestinations\": "
                + "{ \"AXIAL CTA HEAD\": \"AHSPACS, RAPID 1\", \"AXAIL TYPO\": \"RAPID 1\" } } }").getBytes());

        ProtocolOverrides.mergeTemplate(Arrays.asList("1.5", "1.6"), file);
        ProtocolOverride stroke = ProtocolOverrides.load(file).get("1.5");
        assertEquals("AHSPACS, RAPID 1", stroke.getReconSendDestinations().get("AXIAL CTA HEAD"), "init must not drop reconSendDestinations");

        com.protocolbook.model.Reconstruction cta = new com.protocolbook.model.Reconstruction();
        cta.setName("Axial CTA  Head");
        cta.getSendDestinations().add("AHSPACS");
        assertEquals(Arrays.asList("AHSPACS", "RAPID 1"), stroke.sendDestinationsFor(cta));

        com.protocolbook.model.Reconstruction other = new com.protocolbook.model.Reconstruction();
        other.setName("CORONAL MIP");
        other.getSendDestinations().add("AHSPACS");
        assertEquals(Arrays.asList("AHSPACS"), stroke.sendDestinationsFor(other), "unlisted recons keep the exported hosts");

        assertEquals(Arrays.asList("AXAIL TYPO"), stroke.unmatchedReconNames(Arrays.asList(cta, other)));
    }

    private static void writeNotes(File file, String protocolNumber, String notes) throws Exception {
        org.json.JSONObject json = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath())));
        json.getJSONObject(protocolNumber).put("notes", notes);
        try (java.io.FileWriter w = new java.io.FileWriter(file)) { w.write(json.toString(2)); }
    }
}
