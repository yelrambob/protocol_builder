package com.protocolbook.gui;

import com.protocolbook.labels.CodeLabels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KernelNamesTest {
    @Test void loadingAddsNewKernelCodesAndNamingOneUpdatesTheBookLabels(@TempDir Path tempDir) throws Exception {
        File kernels = tempDir.resolve("kernel-labels.json").toFile();
        Files.write(kernels.toPath(), "{ \"8\": \"Detail\", \"999\": \"Kept even though unused\" }".getBytes(StandardCharsets.UTF_8));

        Session session = Session.load(new File("src/test/resources/sample-protocols"), tempDir.resolve("protocol-overrides.json").toFile(),
                new PrintStream(new ByteArrayOutputStream()));

        assertTrue(session.kernelSamples.containsKey("8"), "the sample protocols use kernel 8");
        Map<String, String> onDisk = CodeLabels.load(kernels);
        assertEquals("Detail", onDisk.get("8"), "existing names are never touched");
        assertEquals("Kept even though unused", onDisk.get("999"));
        assertTrue(onDisk.keySet().containsAll(session.kernelSamples.keySet()), "every code the protocols use is now in the file");
        assertFalse(session.kernelSamples.get("8").isEmpty(), "example recon names are offered for each code");

        String unnamed = null;
        for (String code : session.kernelSamples.keySet()) if (onDisk.get(code).isEmpty()) unnamed = code;
        if (unnamed != null) {
            int before = KernelPanel.unnamed(session);
            session.nameKernel(unnamed, " Bone ");
            assertEquals("Bone", CodeLabels.load(kernels).get(unnamed));
            assertEquals("Bone", session.labels.kernel(unnamed), "labels are re-read, so the next book uses the name");
            assertEquals(before - 1, KernelPanel.unnamed(session));
            assertTrue(new File(kernels.getPath() + ".bak").isFile());
        }
        session.nameKernel("8", "Standard");
        assertEquals("Standard", session.labels.kernel("8"));
    }
}
