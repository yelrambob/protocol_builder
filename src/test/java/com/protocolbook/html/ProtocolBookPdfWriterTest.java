package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.parser.ProtocolFolderWalker;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolBookPdfWriterTest {
    private static final LabelConfig DEFAULT_LABELS = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());

    @Test void coverContentsThenOneProtocolPerPageSkippingExcluded(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(new File("src/test/resources/sample-protocols"));
        Map<String, ProtocolOverride> overrides = new HashMap<>();
        overrides.put("9.4", new ProtocolOverride());
        overrides.get("9.4").setExcluded(true);
        overrides.put("9.2", new ProtocolOverride());
        overrides.get("9.2").setNotes("Pad under the knee for comfort.");

        File out = tempDir.resolve("book.pdf").toFile();
        new ProtocolBookPdfWriter().write(protocols, overrides, DEFAULT_LABELS, null, "Test Book", out);

        try (PDDocument pdf = PDDocument.load(out)) {
            assertEquals(2 + 11, pdf.getNumberOfPages(), "cover + contents + one page for each of the 11 non-excluded protocols");
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Test Book"));
            assertTrue(text.contains("Contents"));
            assertTrue(text.contains("9.2 — CT LWR EXT KNEE WITH CONTRAST"));
            assertFalse(text.contains("9.4 — CT LWR EXT HIP"), "excluded protocol must not appear");
            assertTrue(text.contains("Pad under the knee for comfort."), "scanning notes carry over from the HTML layout");
            assertTrue(text.contains("pitch 0.516:1"), "same acquisition line as the HTML book");
        }
    }
}
