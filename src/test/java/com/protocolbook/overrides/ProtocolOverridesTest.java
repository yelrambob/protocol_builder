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

        int firstRun = ProtocolOverrides.mergeTemplate(names("9.2", "9.4"), file);
        assertEquals(2, firstRun);

        // simulate hand-editing the file directly, the way a technologist would
        writeNotes(file, "9.2", "Pad under the knee.");

        int secondRun = ProtocolOverrides.mergeTemplate(names("9.2", "9.4", "9.6"), file);
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

        ProtocolOverrides.mergeTemplate(names("1.5", "1.6"), file);
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

        com.protocolbook.model.Series series = new com.protocolbook.model.Series();
        series.setNumber(2);
        com.protocolbook.model.Group group = new com.protocolbook.model.Group();
        group.getReconstructions().addAll(Arrays.asList(cta, other));
        series.getGroups().add(group);
        java.util.List<String> problems = stroke.problems(Arrays.asList(series));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("AXAIL TYPO"));
    }

    @Test void mergeTemplateLabelsEachEntryWithItsProtocolNameAndSortsByNumber(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("protocol-overrides.json").toFile();
        java.nio.file.Files.write(file.toPath(), "{ \"9.10\": { \"notes\": \"keep me\" }, \"99.1\": { \"excluded\": true } }".getBytes());

        Map<String, String> scanner = new java.util.LinkedHashMap<>();
        scanner.put("9.10", "CT LWR EXT KNEE");
        scanner.put("10.1", "QA PHANTOM");
        scanner.put("9.2", "CT LWR EXT HIP");
        scanner.put("1.5", "CT HEAD STROKE");
        ProtocolOverrides.mergeTemplate(scanner, file);

        String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        int[] at = { text.indexOf("\"1.5\""), text.indexOf("\"9.2\""), text.indexOf("\"9.10\""), text.indexOf("\"10.1\""), text.indexOf("\"99.1\"") };
        for (int i = 1; i < at.length; i++) assertTrue(at[i - 1] >= 0 && at[i - 1] < at[i], "entries should be in protocol-number order: " + text);
        assertTrue(text.indexOf("\"protocolName\": \"CT HEAD STROKE\"") > at[0], "protocolName should be the entry's first field");
        assertTrue(text.indexOf("\"protocolName\": \"CT HEAD STROKE\"") < text.indexOf("\"title\"", at[0]));

        Map<String, ProtocolOverride> after = ProtocolOverrides.load(file);
        assertEquals("keep me", after.get("9.10").getNotes(), "existing settings survive");
        assertTrue(after.get("99.1").isExcluded(), "entries for numbers no longer on the scanner are kept");
        assertTrue(new File(file.getPath() + ".bak").isFile(), "the previous version is kept as a backup");
        assertTrue(new String(java.nio.file.Files.readAllBytes(new File(file.getPath() + ".bak").toPath())).contains("keep me"));
        assertNull(after.get("9.10").getTitle() == null || after.get("9.10").getTitle().isEmpty() ? null : "x", "protocolName must not become a title override");
    }

    @Test void reconAndSeriesOverridesLoadAndTyposAreReported(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("protocol-overrides.json").toFile();
        java.nio.file.Files.write(file.toPath(), ("{ \"9.2\": {"
                + " \"contrastDelay\": \"90\","
                + " \"series\": { \"2\": { \"kv\": 120, \"pitch\": \"0.984\", \"kvp\": \"x\" }, \"7\": { \"ma\": \"1\" } },"
                + " \"recons\": { \"axial knee det 2.5mm\": { \"kernel\": \"Bone\", \"wwwl\": \"400/40\", \"wl\": \"x\" } } } }").getBytes());
        ProtocolOverride knee = ProtocolOverrides.load(file).get("9.2");
        assertEquals("90", knee.getContrastDelay());
        assertEquals("120", knee.seriesField(2, "kv"), "numbers typed without quotes still load");
        assertNull(knee.seriesField(3, "kv"));

        com.protocolbook.model.Reconstruction axial = new com.protocolbook.model.Reconstruction();
        axial.setName("AXIAL KNEE DET 2.5MM");
        assertEquals("Bone", knee.reconField(axial, "kernel"));
        assertEquals("400/40", knee.reconField(axial, "wwwl"));
        assertNull(knee.reconField(axial, "asir"));

        com.protocolbook.model.Series series = new com.protocolbook.model.Series();
        series.setNumber(2);
        com.protocolbook.model.Group group = new com.protocolbook.model.Group();
        group.getReconstructions().add(axial);
        series.getGroups().add(group);
        String problems = String.join("\n", knee.problems(Arrays.asList(series)));
        assertTrue(problems.contains("unknown field 'kvp'"), problems);
        assertTrue(problems.contains("unknown field 'wl'"), problems);
        assertTrue(problems.contains("no series 7"), problems);
    }

    private static Map<String, String> names(String... numbers) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String n : numbers) out.put(n, "Protocol " + n);
        return out;
    }

    private static void writeNotes(File file, String protocolNumber, String notes) throws Exception {
        org.json.JSONObject json = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath())));
        json.getJSONObject(protocolNumber).put("notes", notes);
        try (java.io.FileWriter w = new java.io.FileWriter(file)) { w.write(json.toString(2)); }
    }
}
