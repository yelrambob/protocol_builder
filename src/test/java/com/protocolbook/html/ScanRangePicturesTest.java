package com.protocolbook.html;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.overrides.ProtocolOverrides;
import com.protocolbook.parser.ProtocolFolderWalker;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScanRangePicturesTest {
    private static final LabelConfig LABELS = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());

    private static File grey(File folder, String name) throws Exception {
        folder.mkdirs();
        BufferedImage img = new BufferedImage(400, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, 400, 600);
        g.dispose();
        File f = new File(folder, name);
        ImageIO.write(img, "png", f);
        return f;
    }

    private static Map<String, ProtocolOverride> kneeWithBoxes() {
        ProtocolOverride knee = new ProtocolOverride();
        ProtocolOverride.ScanRangePicture pic = new ProtocolOverride.ScanRangePicture("legs.png");
        pic.getBoxes().add(new ProtocolOverride.Box("Arterial", "#e53935", 0.1, 0.2, 0.5, 0.3));
        pic.getBoxes().add(new ProtocolOverride.Box("Venous", "#1e88e5", 0.2, 0.6, 0.6, 0.3));
        knee.getScanRangePictures().add(pic);
        Map<String, ProtocolOverride> overrides = new HashMap<>();
        overrides.put("9.2", knee);
        return overrides;
    }

    @Test void boxesAreDrawnIntoThePictureWhereTheyWereDrawn(@TempDir Path tempDir) throws Exception {
        File folder = tempDir.resolve(ScanRangePictures.FOLDER).toFile();
        grey(folder, "legs.png");
        String uri = ScanRangePictures.composedDataUri(folder, kneeWithBoxes().get("9.2").getScanRangePictures().get(0), 900);
        assertNotNull(uri);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1))));
        assertEquals(400, out.getWidth(), "smaller than maxWidth, so kept at its own size");
        Color edge = new Color(out.getRGB(200, 120)); // top edge of the red box: y = 0.2 * 600
        assertTrue(edge.getRed() > 180 && edge.getGreen() < 120, "red outline at the box's top edge, got " + edge);
        Color outside = new Color(out.getRGB(380, 20));
        assertTrue(Math.abs(outside.getRed() - 128) < 12 && Math.abs(outside.getBlue() - 128) < 12, "picture untouched outside the boxes");
        assertNull(ScanRangePictures.composedDataUri(folder, new ProtocolOverride.ScanRangePicture("missing.png"), 900));
    }

    @Test void picturesShowInTheHtmlAndPdfBookWithAKey(@TempDir Path tempDir) throws Exception {
        File folder = tempDir.resolve(ScanRangePictures.FOLDER).toFile();
        grey(folder, "legs.png");
        List<Protocol> protocols = new ProtocolFolderWalker().parse(new File("src/test/resources/sample-protocols"));

        File html = tempDir.resolve("book.html").toFile();
        new ProtocolBookHtmlWriter().withPictureFolder(folder).write(protocols, kneeWithBoxes(), LABELS, null, null, null, null, null, null, html);
        String text = new String(Files.readAllBytes(html.toPath()), StandardCharsets.UTF_8);
        String knee = text.substring(text.indexOf("id=\"p-9-2\""));
        knee = knee.substring(0, knee.indexOf("</section>"));
        assertTrue(knee.contains("<div class=\"scan-picture\"><img src=\"data:image/jpeg;base64,"));
        assertTrue(knee.contains("background:#e53935;\">&nbsp;</td><td>Arterial</td>"));
        assertTrue(knee.contains("<td>Venous</td>"));

        File pdf = tempDir.resolve("book.pdf").toFile();
        new ProtocolBookPdfWriter().withPictureFolder(folder).write(protocols, kneeWithBoxes(), LABELS, null, "Test", pdf);
        int images = 0;
        try (PDDocument doc = PDDocument.load(pdf)) {
            for (PDPage page : doc.getPages())
                for (org.apache.pdfbox.cos.COSName name : page.getResources().getXObjectNames())
                    if (page.getResources().getXObject(name) instanceof PDImageXObject) images++;
        }
        assertEquals(1, images, "the knee page carries the picture");
    }

    @Test void picturesSaveAndLoadAndMissingOnesAreReported(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("protocol-overrides.json").toFile();
        ProtocolOverrides.save(kneeWithBoxes(), null, file);
        ProtocolOverride back = ProtocolOverrides.load(file).get("9.2");
        assertEquals(1, back.getScanRangePictures().size());
        ProtocolOverride.Box venous = back.getScanRangePictures().get(0).getBoxes().get(1);
        assertEquals("Venous", venous.getLabel());
        assertEquals("#1e88e5", venous.getColor());
        assertEquals(0.6, venous.getY(), 1e-9);

        List<Protocol> protocols = new ProtocolFolderWalker().parse(new File("src/test/resources/sample-protocols"));
        List<String> problems = ScanRangePictures.problems(protocols, kneeWithBoxes(), tempDir.resolve("empty").toFile());
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("legs.png"));
    }

    @Test void eachNewBoxGetsTheNextUnusedColorAndImportsDoNotClobber(@TempDir Path tempDir) throws Exception {
        List<ProtocolOverride.Box> boxes = new ArrayList<>();
        assertEquals(ScanRangePictures.COLORS.get(0), ScanRangePictures.nextColor(boxes));
        boxes.add(new ProtocolOverride.Box("", ScanRangePictures.COLORS.get(0), 0, 0, 1, 1));
        assertEquals(ScanRangePictures.COLORS.get(1), ScanRangePictures.nextColor(boxes));
        boxes.get(0).setColor("#123456"); // recolored: the first palette color is free again
        assertEquals(ScanRangePictures.COLORS.get(0), ScanRangePictures.nextColor(boxes));

        File folder = tempDir.resolve("lib").toFile();
        File a = grey(tempDir.resolve("a").toFile(), "scout.png");
        assertEquals("scout.png", ScanRangePictures.importPicture(a, folder));
        assertEquals("scout.png", ScanRangePictures.importPicture(a, folder), "the same file again isn't copied twice");
        BufferedImage other = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        File b = tempDir.resolve("b").toFile();
        b.mkdirs();
        ImageIO.write(other, "png", new File(b, "scout.png"));
        assertEquals("scout (2).png", ScanRangePictures.importPicture(new File(b, "scout.png"), folder), "a different file with the same name");
        assertEquals(2, ScanRangePictures.library(folder).size());
    }
}
