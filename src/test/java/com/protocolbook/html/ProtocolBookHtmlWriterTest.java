package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Series;
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
    // Fixture protocol-number prefixes present: 3 (Neck), 4 (Upper Ext.), 7 (Spine), 8 (Pelvis), 9 (Lower Ext.)
    private static final LabelConfig DEFAULT_LABELS = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());

    @Test void groupsByProtocolNumberPrefixWithGeMatchingCategoryLabels(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);

        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride excludeHip = new ProtocolOverride();
        excludeHip.setExcluded(true);
        overrides.put("9.4", excludeHip); // "CT LWR EXT HIP WITH CONTRAST"

        ProtocolOverride kneeNotes = new ProtocolOverride();
        kneeNotes.setNotes("Pad under the knee for comfort.");
        overrides.put("9.2", kneeNotes); // "CT LWR EXT KNEE WITH CONTRAST"

        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, overrides, DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        // two different protocols share this name (9.4 lower-extremities and 8.2 pelvis); only 9.4 is excluded
        assertFalse(html.contains(">9.4 &mdash; CT LWR EXT HIP WITH CONTRAST<"), "excluded protocol must not appear");
        assertTrue(html.contains(">8.2 &mdash; CT LWR EXT HIP WITH CONTRAST<"), "non-excluded protocol with the same name must still appear");
        assertTrue(html.contains("Pad under the knee for comfort."), "manual scanning note must be rendered");
        assertTrue(html.contains("AXIAL KNEE DET 2.5MM"), "recon display name should still show up");

        // Adult and Peds are always both rendered as top-level buckets, even though this fixture is all-adult
        long bucketCount = html.lines().filter(l -> l.contains("class=\"menu-category\"")).count();
        assertEquals(2, bucketCount, "Adult and Peds buckets should always render, even with zero pediatric protocols");
        assertTrue(html.contains(">Adult ("));
        assertTrue(html.contains(">Peds (0)<"), "Peds bucket should still render with a zero count when there are no pediatric protocols");

        // fixture prefixes 3/4/7/8/9 -> 5 number-categories, labeled to match the scanner's own numbering
        long categoryCount = html.lines().filter(l -> l.contains("class=\"menu-subcat\"")).count();
        assertEquals(5, categoryCount, "should be grouped by protocol-number prefix into 5 categories");
        assertTrue(html.contains(">Neck ("), "prefix 3 should be labeled Neck");
        assertTrue(html.contains(">Upper Ext. ("), "prefix 4 should be labeled Upper Ext.");
        assertTrue(html.contains(">Spine ("), "prefix 7 should be labeled Spine");
        assertTrue(html.contains(">Pelvis ("), "prefix 8 should be labeled Pelvis");
        assertTrue(html.contains(">Lower Ext. ("), "prefix 9 should be labeled Lower Ext.");

        // no more middle Neuro/Body/MSK/Other layer or per-body-part specific subgroup layer
        assertFalse(html.contains("class=\"menu-group\""), "the old 3-level hierarchy's menu-group level should no longer be rendered");

        // every protocol section starts hidden; only the welcome view is visible until something is clicked
        long hiddenSections = html.lines().filter(l -> l.contains("class=\"protocol-view\" style=\"display:none;\"")).count();
        assertEquals(11, hiddenSections, "11 non-excluded protocols should each get their own hidden section (9.4 is excluded)");
        assertTrue(html.contains("id=\"welcome\" class=\"protocol-view welcome\" style=\"display:block;\""), "welcome view should be visible on load");

        // sidebar links target the matching protocol section by id
        assertTrue(html.contains("onclick=\"showProtocol('p-9-2'); return false;\""));
        assertTrue(html.contains("<section id=\"p-9-2\" class=\"protocol-view\""));
    }

    @Test void unmappedPrefixIsLeftOutOfTheBookEntirely(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        // re-flag one protocol as a "10.x" QA/phantom-style number, which has no default category mapping
        protocols.get(0).getMetadata().setProtocolNumber("10.1");

        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains(">10.1 &mdash;"), "a protocol number prefix with no category mapping (e.g. 10) must not appear anywhere in the book");
        long hiddenSections = html.lines().filter(l -> l.contains("class=\"protocol-view\" style=\"display:none;\"")).count();
        assertEquals(11, hiddenSections, "the unmapped-prefix protocol should be silently dropped, not dumped into a catch-all");
    }

    @Test void categoryLabelsFileOverridesTheDefaultForOneNumberPrefix(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        Map<String, String> categoryOverrides = new HashMap<>();
        categoryOverrides.put("9", "Knee/Ankle/Foot");

        File out = tempDir.resolve("book.html").toFile();
        LabelConfig labels = new LabelConfig(new HashMap<>(), new HashMap<>(), categoryOverrides);
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), labels, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains(">Knee/Ankle/Foot ("), "category-labels.json should override the default label for prefix 9");
        assertFalse(html.contains(">Lower Ext. ("), "the default label should no longer appear once overridden");
    }

    @Test void scoutsRenderAsOneTableWithPlaneLabelsAndKernelsAreMapped(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);

        Map<String, String> kernelLabels = new HashMap<>();
        kernelLabels.put("8", "STD");

        File out = tempDir.resolve("book.html").toFile();
        LabelConfig labels = new LabelConfig(kernelLabels, new HashMap<>(), new HashMap<>()); // default plane labels apply
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), labels, null, null, null, null, null, out);
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
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        // The knee protocol's axial group has SmartmA active (milliAmpsMode set): milliAmps=15 is a
        // stale fallback the console keeps around, minMa=100/maxMa=635 is what's actually configured.
        assertTrue(html.contains("140 kV &middot; 100-635 mA (NI 5.0)"), "SmartmA groups should show the min-max range plus noise index, not the stale fixed mA value");
    }

    @Test void doesNotRenderADetectorLine(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains("Detector:"), "the detector-label feature was removed; the acquisition line must not show a Detector field");
    }

    @Test void splitsPediatricProtocolsIntoTheirOwnTopLevelBucket(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        protocols.get(0).getMetadata().setPatientType("Pediatric");

        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains(">Adult (11)<"), "the other 11 protocols should stay under Adult");
        assertTrue(html.contains(">Peds (1)<"), "the one re-flagged protocol should form its own Peds bucket");
    }

    @Test void threeSegmentProtocolNumberIsPediatricEvenWithoutAPatientTypeFlag(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        // scanner convention: peds protocols are always "x.x.x" (three segments), regardless of what patientType says
        protocols.get(0).getMetadata().setProtocolNumber("9.2.1");
        protocols.get(0).getMetadata().setPatientType("adult"); // deliberately contradicts the number-based signal

        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains(">Adult (11)<"));
        assertTrue(html.contains(">Pediatric (1)<"), "a three-segment protocol number should count as pediatric even when patientType says adult");
        assertTrue(html.contains(">9.2.1 &mdash;"), "the three-segment number itself should still render correctly");
    }

    @Test void titleOverrideRenamesProtocolWithoutChangingItsNumber(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride renamed = new ProtocolOverride();
        renamed.setTitle("Knee Protocol (Renamed)");
        overrides.put("9.2", renamed);

        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, overrides, DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains(">9.2 &mdash; Knee Protocol (Renamed)<"), "sidebar link should use the title override");
        assertTrue(html.contains("<h2>9.2 &mdash; Knee Protocol (Renamed)</h2>"), "protocol page header should use the title override");
        assertFalse(html.contains("CT LWR EXT KNEE WITH CONTRAST"), "the original scanner name should no longer appear once overridden");
    }

    @Test void contrastInfoIsOmittedFromScoutSeries(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        // force a scout series to look like it carries IV contrast, matching what the scanner export can do
        for (Protocol p : protocols) for (Series s : p.getSeries())
            if (s.getScanType() != null && s.getScanType().equalsIgnoreCase("Scout") && s.getContrast() != null) {
                s.getContrast().setIv(true);
                s.getContrast().setIvVolume("100");
                s.getContrast().setFlowRate("3");
            }

        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        // the scout table (Plane/kV/mA) must never be preceded by a contrast paragraph
        int scoutTableIndex = html.indexOf("<th>Plane</th><th>kV</th><th>mA</th>");
        assertTrue(scoutTableIndex > 0, "fixtures should still include a scout series");
        int precedingSeriesStart = html.lastIndexOf("<div class=\"series\">", scoutTableIndex);
        String scoutBlock = html.substring(precedingSeriesStart, scoutTableIndex);
        assertFalse(scoutBlock.contains("class=\"contrast\""), "scout series must never show injection rate/volume");
    }

    @Test void embedsLogoInMenuWelcomeAndEveryProtocolWhenProvided(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        String logoDataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, logoDataUri, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("<div class=\"menu-logo\"><img src=\"" + logoDataUri + "\""), "logo should appear at the top of the sidebar");
        assertTrue(html.contains("<img class=\"welcome-logo\" src=\"" + logoDataUri + "\""), "logo should appear on the welcome view");
        long protocolLogoCount = html.lines().filter(l -> l.contains("<img class=\"protocol-logo\"")).count();
        assertEquals(12, protocolLogoCount, "logo should appear on every one of the 12 protocol pages (none excluded in this test)");
    }

    @Test void omitsLogoElementsWhenNoneProvided(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
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
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, pdfLibrary, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains(">Surgical Planning (2)<"), "PDF library should show as its own category with a count");
        assertTrue(html.contains("<a href=\"https://example.com/pdfs/knee-planning.pdf\" target=\"_blank\" rel=\"noopener noreferrer\">Knee Replacement Planning Guide</a>"));
        assertTrue(html.contains("<a href=\"https://example.com/pdfs/hip-planning.pdf\" target=\"_blank\" rel=\"noopener noreferrer\">Hip Replacement Planning Guide</a>"));
        // 3 top-level buckets: Adult + Peds (always rendered) + this new PDF library entry
        long bucketCount = html.lines().filter(l -> l.contains("class=\"menu-category\"")).count();
        assertEquals(3, bucketCount);
    }

    @Test void omitsPdfLibraryCategoryWhenEmpty(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains("Surgical Planning"));
    }

    @Test void rendersProtocolImageByConventionWithClientSideFallback(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        ProtocolImages images = new ProtocolImages("https://example.com/protocol-images", "png");
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, images, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("<img class=\"protocol-image\" src=\"https://example.com/protocol-images/9.2.png\" "
                + "alt=\"9.2 reference image\" onerror=\"this.style.display='none';\">"), "image URL should be built from the protocol number");
        long imageCount = html.lines().filter(l -> l.contains("class=\"protocol-image\"")).count();
        assertEquals(12, imageCount, "every protocol gets an <img> attempt regardless of whether that file actually exists on the server");
    }

    @Test void omitsProtocolImageWhenNoBaseConfigured(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains("class=\"protocol-image\""));
    }

    @Test void customBookTitleSetsPageTitleAndWelcomeHeading(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, "AHS CT Protocols", null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("<title>AHS CT Protocols</title>"), "browser tab title should use the custom book title");
        assertTrue(html.contains("<h1>AHS CT Protocols</h1>"), "welcome heading should use the custom book title");
    }

    @Test void blankBookTitleFallsBackToDefault(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("<title>Protocol Book</title>"));
        assertTrue(html.contains("<h1>Protocol Book</h1>"));
    }

    @Test void sidebarSubmenusOnlyOpenOnClickNotHover(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains(":hover>.submenu"), "hovering must not by itself reveal any drill-down level's submenu");
        assertTrue(html.contains(".menu-category.open>.submenu{display:block;}"));
        assertTrue(html.contains(".menu-subcat.open>.submenu{display:block;}"));

        // the rail's own collapsed/expanded width must also be click-driven, not hover-driven
        assertFalse(html.contains(".main-menu:hover"), "the sidebar rail must not widen on hover either - only a click should expand it");
        assertTrue(html.contains(".main-menu.expanded{width:320px;}"));
        assertTrue(html.contains("menu.classList.toggle('expanded'"), "clicking a top-level entry should drive the rail's expanded state");
    }

    @Test void changelogTableListsHandTypedEntriesMostRecentFirstAndLinksToTheProtocol(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        // written out of date order and loaded through Changelog.load(), same path Main.java uses -
        // sorting is load()'s responsibility, not something the writer re-derives from raw entries.
        File changelogFile = tempDir.resolve("changelog.json").toFile();
        try (java.io.FileWriter w = new java.io.FileWriter(changelogFile)) {
            w.write("[\n"
                    + "{\"date\":\"2026-08-10\",\"protocolNumber\":\"9.2\",\"note\":\"Increased mA range for noisy images\"},\n"
                    + "{\"date\":\"2026-08-15\",\"protocolNumber\":\"9.4\",\"note\":\"Renamed per radiology request\"},\n"
                    + "{\"date\":\"not-a-date\",\"note\":\"Site-wide contrast injector recalibrated\"}\n"
                    + "]");
        }
        List<Changelog.Entry> changelog = Changelog.load(changelogFile);

        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, changelog, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("Recent Changes (3)"), "sidebar should show a count of all 3 changelog entries");
        assertTrue(html.contains("id=\"recent-changes\""), "a dedicated recent-changes section should be rendered");
        assertTrue(html.contains("Increased mA range for noisy images"));
        assertTrue(html.contains("Aug 15, 2026"), "a yyyy-MM-dd date should be reformatted for display");

        // most recent (2026-08-15) first, then 2026-08-10, then the unparseable date last
        int row15 = html.indexOf("Renamed per radiology request");
        int row10 = html.indexOf("Increased mA range for noisy images");
        int rowUndated = html.indexOf("Site-wide contrast injector recalibrated");
        assertTrue(row15 > 0 && row10 > row15 && rowUndated > row10, "entries should sort newest first, unparseable dates last");

        // an entry naming a protocol still in the book links to it
        assertTrue(html.contains("<a href=\"#p-9-2\" onclick=\"showProtocol('p-9-2'); return false;\">9.2</a>"));
    }

    @Test void changelogOmittedWhenEmpty(@TempDir Path tempDir) throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(FIXTURE_ROOT);
        File out = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().write(protocols, new HashMap<>(), DEFAULT_LABELS, null, null, null, null, null, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertFalse(html.contains("Recent Changes"));
        assertFalse(html.contains("id=\"recent-changes\""));
    }
}
