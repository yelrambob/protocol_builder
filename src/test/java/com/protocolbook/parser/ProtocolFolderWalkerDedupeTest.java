package com.protocolbook.parser;

import com.protocolbook.model.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolFolderWalkerDedupeTest {

    @Test void keepsMostRecentlyUpdatedWhenProtocolNumbersCollide(@TempDir Path tempDir) throws Exception {
        writeMinimalProtocol(tempDir.resolve("old-export").toFile(), "7.2", "OLD NAME", "2026-01-01T00:00:00.000-0400");
        writeMinimalProtocol(tempDir.resolve("new-export").toFile(), "7.2", "NEW NAME", "2026-06-01T00:00:00.000-0400");

        List<Protocol> protocols = new ProtocolFolderWalker().parse(tempDir.toFile());

        assertEquals(1, protocols.size(), "duplicate protocol numbers should collapse to one");
        assertEquals("NEW NAME", protocols.get(0).getMetadata().getName());
    }

    private static void writeMinimalProtocol(File folder, String slotNumber, String name, String lastUpdated) throws Exception {
        folder.mkdirs();
        try (FileWriter w = new FileWriter(new File(folder, "protocolmetadata.json"))) {
            w.write("{ \"name\": \"" + name + "\", \"slotNumber\": \"" + slotNumber + "\", \"humanoid\": \"adult\", "
                    + "\"anatomyRegion\": \"spine\", \"library\": \"Site\", \"uuid\": \"u\", \"lastUpdatedDateTime\": \"" + lastUpdated + "\" }");
        }
        try (FileWriter w = new FileWriter(new File(folder, "UIRx.xml"))) {
            w.write("<?xml version=\"1.0\"?>\n<jrx:uirx xmlns:jrx=\"http://fct.med.ge.com/jrx\"><jrx:exam><jrx:proto>"
                    + "<jrx:ulement name=\"name\" type=\"String\" value=\"" + name + "\"/></jrx:proto></jrx:exam></jrx:uirx>");
        }
    }
}
