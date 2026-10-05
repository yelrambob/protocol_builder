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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AddedFieldsAndThemeTest {
    private static final LabelConfig DEFAULT_LABELS = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());
    private static final File FIXTURE_ROOT = new File("src/test/resources/sample-protocols");

    private static Map<String, ProtocolOverride> kneeWithAddedFields() {
        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride knee = new ProtocolOverride();
        knee.getAddedFields().add(new ProtocolOverride.AddedField(null, "Oral contrast", "900 mL over 1 hour"));
        knee.getAddedFields().add(new ProtocolOverride.AddedField("2", "Breath hold", "Inspiration"));
        knee.setScanRange("Distal femur to proximal tibia");
        overrides.put("9.2", knee);
        return overrides;
    }

    @Test void addedFieldsShowUnderTheExamAndUnderTheirSeries(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, kneeWithAddedFields(), DEFAULT_LABELS, null, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        String knee = html.substring(html.indexOf("id=\"p-9-2\""));
        knee = knee.substring(0, knee.indexOf("</section>"));
        int exam = knee.indexOf("<p class=\"added-field\"><strong>Oral contrast:</strong> 900 mL over 1 hour</p>");
        int series2 = knee.indexOf("<h3>Series 2");
        int breath = knee.indexOf("<p class=\"added-field\"><strong>Breath hold:</strong> Inspiration</p>");
        assertTrue(exam > 0 && exam < series2, "exam field is above the first diagnostic series");
        assertTrue(breath > series2, "series field is under series 2");
        assertTrue(knee.contains("<strong>Scan range:</strong> Distal femur to proximal tibia"),
                "a typed scan range shows even without reference workbooks");
    }

    @Test void themeColorsReplaceTheDefaultsInHtmlAndPdf(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        BookTheme theme = new BookTheme("#1e5631", "#d4a017");
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().withTheme(theme).write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(html.contains("--ahs-blue:#1e5631;"));
        assertTrue(html.contains("--ahs-orange:#d4a017;"));
        assertFalse(html.contains("#044281"), "no default color left over");

        String pdfCss = ProtocolBookPdfWriter.css(theme);
        assertTrue(pdfCss.contains("#1e5631") && pdfCss.contains("#d4a017"));
        assertFalse(pdfCss.contains("@PRIMARY@") || pdfCss.contains("@ACCENT@") || pdfCss.contains("@TINT@"));
        assertTrue(ProtocolBookPdfWriter.css(BookTheme.DEFAULT).contains("#044281"), "the default theme is the original blue");
    }

    @Test void themeWorksOutDarkAndTintShadesAndRejectsNonColors() {
        BookTheme t = new BookTheme("#044281", "#f80");
        assertEquals("#ff8800", t.getAccent(), "short hex expands");
        assertEquals("#cc6d00", t.getAccentDark());
        assertEquals("#fff3e6", t.getAccentTint());
        assertThrows(IllegalArgumentException.class, () -> new BookTheme("blue", "#fff"));
        assertEquals("Blue & Orange", BookTheme.DEFAULT.withAccent("#FF8200").getName(), "same color keeps the preset's name");
        assertEquals("Custom", BookTheme.DEFAULT.withAccent("#00ff00").getName());
    }

    @Test void changesPdfListsScannerAndTypedValuesBySection(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        Map<String, ProtocolOverride> overrides = kneeWithAddedFields();
        overrides.get("9.2").setContrastVolume("120");
        ProtocolOverride hip = new ProtocolOverride();
        hip.setExcluded(true);
        overrides.put("9.4", hip);

        File out = tempDir.resolve("changes.pdf").toFile();
        new ChangeReportWriter().write(protocols, overrides, DEFAULT_LABELS, "Test Book", out);
        try (PDDocument pdf = PDDocument.load(out)) {
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Test Book — Manual Changes"));
            assertTrue(text.contains("Adult — Lower Ext."));
            assertTrue(text.contains("9.2 — CT LWR EXT KNEE WITH CONTRAST"));
            assertTrue(text.contains("Contrast volume (mL) 100 120"), "scanner value then the typed one: " + text);
            assertTrue(text.contains("Added: Oral contrast"));
            assertTrue(text.contains("In the book Yes Left out"));
        }
    }
}
