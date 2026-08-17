package com.protocolbook.html;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolBookHtmlWriterTest {
    private static final File FIXTURE_ROOT = new File("src/test/resources/sample-protocols");

    @Test void groupsExcludesAndAppliesManualNotes(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);

        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride excludeHip = new ProtocolOverride();
        excludeHip.setExcluded(true);
        overrides.put("9.4", excludeHip); // "CT LWR EXT HIP WITH CONTRAST"

        ProtocolOverride kneeNotes = new ProtocolOverride();
        kneeNotes.setNotes("Pad under the knee for comfort.");
        overrides.put("9.2", kneeNotes); // "CT LWR EXT KNEE WITH CONTRAST"

        File out = tempDir.resolve("book.html").toFile();
        LabelConfig labels = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());
        new ProtocolBookHtmlWriter().write(protocols, overrides, labels, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        // two different protocols share this name (9.4 lower-extremities and 8.2 pelvis); only 9.4 is excluded
        assertFalse(html.contains(">9.4 &mdash; CT LWR EXT HIP WITH CONTRAST<"), "excluded protocol must not appear");
        assertTrue(html.contains(">8.2 &mdash; CT LWR EXT HIP WITH CONTRAST<"), "non-excluded protocol with the same name must still appear");
        assertTrue(html.contains("Pad under the knee for comfort."), "manual scanning note must be rendered");
        assertTrue(html.contains("AXIAL KNEE DET 2.5MM"), "recon display name should still show up");
        // body parts present: lower/upper Extremities -> MSK, neck/spine -> Neuro, pelvis -> Body
        long categoryCount = html.lines().filter(l -> l.contains("class=\"menu-category\"")).count();
        assertEquals(3, categoryCount, "should be grouped into 3 reading categories, not one section per protocol-number prefix");
        assertTrue(html.contains(">MSK ("));
        assertTrue(html.contains(">Body ("));
        assertTrue(html.contains(">Neuro ("));

        // every protocol section starts hidden; only the welcome view is visible until something is clicked
        long hiddenSections = html.lines().filter(l -> l.contains("class=\"protocol-view\" style=\"display:none;\"")).count();
        assertEquals(11, hiddenSections, "11 non-excluded protocols should each get their own hidden section (9.4 is excluded)");
        assertTrue(html.contains("id=\"welcome\" class=\"protocol-view welcome\" style=\"display:block;\""), "welcome view should be visible on load");

        // sidebar links target the matching protocol section by id
        assertTrue(html.contains("onclick=\"showProtocol('p-9-2'); return false;\""));
        assertTrue(html.contains("<section id=\"p-9-2\" class=\"protocol-view\""));
    }

    @Test void scoutsRenderAsOneTableWithPlaneLabelsAndKernelsAreMapped(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);

        Map<String, String> kernelLabels = new HashMap<>();
        kernelLabels.put("8", "STD");

        File out = tempDir.resolve("book.html").toFile();
        LabelConfig labels = new LabelConfig(kernelLabels, new HashMap<>(), new HashMap<>()); // default plane labels apply
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), labels, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("<th>Plane</th><th>kV</th><th>mA</th>"), "scout series should render as a single Plane/kV/mA table");
        assertTrue(html.contains(">AP<") || html.contains(">Lateral<"), "scout plane codes should map to AP/Lateral/PA via the default convention");
        assertFalse(html.contains("Scout planes:"), "scouts should no longer render as a plain summary line");
        assertTrue(html.contains(">STD<"), "kernel code 8 should map to STD via the supplied kernel-labels");

        // reformats inherit their parent recon's kernel (session.xml's CTDMPRData has no kernel of its own)
        int coronalRow = html.indexOf("CORONAL KNEE DET 2.5MM");
        assertTrue(coronalRow > 0);
        assertTrue(html.substring(coronalRow, coronalRow + 200).contains(">STD<"), "reformat row should show the inherited/mapped kernel");
    }

    @Test void showsMaRangeInsteadOfStaleFixedValueWhenSmartMaIsActive(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>()), null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        // The knee protocol's axial group has SmartmA active (milliAmpsMode set): milliAmps=15 is a
        // stale fallback the console keeps around, minMa=100/maxMa=635 is what's actually configured.
        assertTrue(html.contains("140 kV &middot; 100-635 mA (NI 5.0)"), "SmartmA groups should show the min-max range plus noise index, not the stale fixed mA value");
    }

    @Test void embedsLogoInMenuWelcomeAndEveryProtocolWhenProvided(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        String logoDataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(),
                new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>()), logoDataUri, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("<div class=\"menu-logo\"><img src=\"" + logoDataUri + "\""), "logo should appear at the top of the sidebar");
        assertTrue(html.contains("<img class=\"welcome-logo\" src=\"" + logoDataUri + "\""), "logo should appear on the welcome view");
        long protocolLogoCount = html.lines().filter(l -> l.contains("<img class=\"protocol-logo\"")).count();
        assertEquals(12, protocolLogoCount, "logo should appear on every one of the 12 protocol pages (none excluded in this test)");
    }

    @Test void omitsLogoElementsWhenNoneProvided(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>()), null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        // CSS rules for these classes are always present (static styling); only the rendered <img>/<div> markup should be absent
        assertFalse(html.contains("<div class=\"menu-logo\">"));
        assertFalse(html.contains("<img class=\"welcome-logo\""));
        assertFalse(html.contains("<img class=\"protocol-logo\""));
    }

    @Test void rendersPdfLibraryAsItsOwnMenuCategoryLinkingExternally(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        List<PdfLibrary.Entry> pdfLibrary = List.of(
                new PdfLibrary.Entry("Knee Replacement Planning Guide", "https://example.com/pdfs/knee-planning.pdf"),
                new PdfLibrary.Entry("Hip Replacement Planning Guide", "https://example.com/pdfs/hip-planning.pdf"));
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(),
                new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>()), null, pdfLibrary, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains(">Surgical Planning Protocols (2)<"), "PDF library should show as its own category with a count");
        assertTrue(html.contains("<a href=\"https://example.com/pdfs/knee-planning.pdf\" target=\"_blank\" rel=\"noopener noreferrer\">Knee Replacement Planning Guide</a>"));
        assertTrue(html.contains("<a href=\"https://example.com/pdfs/hip-planning.pdf\" target=\"_blank\" rel=\"noopener noreferrer\">Hip Replacement Planning Guide</a>"));
        // 4 total: Neuro/Body/MSK reading categories + this new PDF library category
        long categoryCount = html.lines().filter(l -> l.contains("class=\"menu-category\"")).count();
        assertEquals(4, categoryCount);
    }

    @Test void omitsPdfLibraryCategoryWhenEmpty(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(),
                new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>()), null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains("Surgical Planning Protocols"));
    }

    @Test void rendersProtocolImageByConventionWithClientSideFallback(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        ProtocolImages images = new ProtocolImages("https://example.com/protocol-images", "png");
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(),
                new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>()), null, null, images, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("<img class=\"protocol-image\" src=\"https://example.com/protocol-images/9.2.png\" "
                + "alt=\"9.2 reference image\" onerror=\"this.style.display='none';\">"), "image URL should be built from the protocol number");
        long imageCount = html.lines().filter(l -> l.contains("class=\"protocol-image\"")).count();
        assertEquals(12, imageCount, "every protocol gets an <img> attempt regardless of whether that file actually exists on the server");
    }

    @Test void omitsProtocolImageWhenNoBaseConfigured(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(),
                new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>()), null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains("class=\"protocol-image\""));
    }
}
