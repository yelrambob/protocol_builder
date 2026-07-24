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

    private static void writeNotes(File file, String protocolNumber, String notes) throws Exception {
        org.json.JSONObject json = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath())));
        json.getJSONObject(protocolNumber).put("notes", notes);
        try (java.io.FileWriter w = new java.io.FileWriter(file)) { w.write(json.toString(2)); }
    }
}
