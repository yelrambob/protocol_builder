package com.protocolbook.html;

import com.protocolbook.model.Protocol;
import com.protocolbook.parser.ProtocolFolderWalker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PediatricWeightSheetWriterTest {

    @Test void annotatesWeightsAndExcludesAdults(@TempDir Path tempDir) throws Exception {
        writeProtocol(tempDir.resolve("p1").toFile(), "5.1", "pediatric", "CT CHEST <5KG");
        writeProtocol(tempDir.resolve("p2").toFile(), "5.2", "pediatric", "CT ABD/PEL 10-20KG");
        writeProtocol(tempDir.resolve("p3").toFile(), "5.3", "pediatric", "CT HEAD >=40 KG");
        writeProtocol(tempDir.resolve("p4").toFile(), "1.1", "adult", "CT HEAD ROUTINE");

        List<Protocol> protocols = new ProtocolFolderWalker().parse(tempDir.toFile());
        File out = tempDir.resolve("peds.html").toFile();
        new PediatricWeightSheetWriter().write(protocols, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        assertTrue(html.contains("CT CHEST &lt;5KG (11 lb)"), "single weight with comparison should be annotated");
        assertTrue(html.contains("CT ABD/PEL 10-20KG (22-44 lb)"), "range should annotate both ends");
        assertTrue(html.contains("CT HEAD &gt;=40 KG (88 lb)"), "second comparison operator variant should work");
        assertFalse(html.contains("CT HEAD ROUTINE"), "adult protocols must not appear on the pediatric sheet");
        assertTrue(html.contains("3 pediatric protocol(s)"));
    }

    private static void writeProtocol(File folder, String slotNumber, String humanoid, String name) throws Exception {
        folder.mkdirs();
        try (FileWriter w = new FileWriter(new File(folder, "protocolmetadata.json"))) {
            w.write("{ \"name\": \"" + name + "\", \"slotNumber\": \"" + slotNumber + "\", \"humanoid\": \"" + humanoid + "\", "
                    + "\"anatomyRegion\": \"chest\", \"library\": \"Site\", \"uuid\": \"u-" + slotNumber + "\" }");
        }
        String xmlEscapedName = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        try (FileWriter w = new FileWriter(new File(folder, "UIRx.xml"))) {
            w.write("<?xml version=\"1.0\"?>\n<jrx:uirx xmlns:jrx=\"http://fct.med.ge.com/jrx\"><jrx:exam><jrx:proto>"
                    + "<jrx:ulement name=\"name\" type=\"String\" value=\"" + xmlEscapedName + "\"/></jrx:proto></jrx:exam></jrx:uirx>");
        }
    }
}
