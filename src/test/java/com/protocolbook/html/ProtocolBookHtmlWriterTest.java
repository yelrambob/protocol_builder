package com.protocolbook.html;

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
    private static final File FIXTURE_ROOT = new File("protocol data");

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
        new ProtocolBookHtmlWriter().write(protocols, overrides, out);
        String html = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);

        // two different protocols share this name (9.4 lower-extremities and 8.2 pelvis); only 9.4 is excluded
        assertFalse(html.contains(">9.4 &mdash; CT LWR EXT HIP WITH CONTRAST<"), "excluded protocol must not appear");
        assertTrue(html.contains(">8.2 &mdash; CT LWR EXT HIP WITH CONTRAST<"), "non-excluded protocol with the same name must still appear");
        assertTrue(html.contains("Pad under the knee for comfort."), "manual scanning note must be rendered");
        assertTrue(html.contains("AXIAL KNEE DET 2.5MM"), "recon display name should still show up");
        // groups: protocol numbers seen are 3.x, 4.x, 7.x, 8.x, 9.x -> 5 group headers, labeled by body part
        String lower = html.toLowerCase();
        assertTrue(lower.contains("lower extremities"));
        assertTrue(lower.contains("pelvis"));
        long groupCount = html.lines().filter(l -> l.contains("class=\"group\"")).count();
        assertEquals(5, groupCount);
    }
}
