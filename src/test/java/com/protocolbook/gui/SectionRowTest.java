package com.protocolbook.gui;

import com.protocolbook.labels.LabelConfig;
import com.protocolbook.model.Contrast;
import com.protocolbook.model.Group;
import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;
import com.protocolbook.model.Series;
import com.protocolbook.overrides.ProtocolOverride;
import com.protocolbook.parser.ProtocolFolderWalker;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SectionRowTest {
    private static final LabelConfig LABELS = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());

    private static Protocol protocol(String number, String name, String kv, boolean iv) {
        Protocol p = new Protocol();
        Metadata m = new Metadata();
        m.setProtocolNumber(number);
        m.setName(name);
        p.setMetadata(m);
        Series s = new Series();
        s.setNumber(2);
        s.setScanType("Helical");
        Group g = new Group();
        g.getAcquisition().setKv(kv);
        s.getGroups().add(g);
        if (iv) {
            Contrast c = new Contrast();
            c.setIv(true);
            c.setIvVolume("100");
            s.setContrast(c);
        }
        p.getSeries().add(s);
        return p;
    }

    private static SectionRow row(List<SectionRow> rows, String number) {
        for (SectionRow r : rows) if (number.equals(r.number)) return r;
        throw new AssertionError("no row " + number);
    }

    @Test void flagsContrastThatDoesNotMatchTheNameAndMissingFigures() {
        List<Protocol> protocols = Arrays.asList(
                protocol("6.1", "CT ABD PEL WITH CONTRAST", "120", false),
                protocol("6.2", "CT ABD PEL WO", "120", true),
                protocol("6.3", "CT ABD PEL W WO", "120", true),
                protocol("6.4", "CT ABD PEL W/O CONTRAST", "120", false));
        List<SectionRow> rows = SectionRow.forSection(protocols, new HashMap<>(), LABELS);

        assertEquals(Arrays.asList("Name says contrast, but no IV contrast is set"), row(rows, "6.1").warnings);
        assertTrue(row(rows, "6.2").warnings.contains("Name says without contrast, but IV contrast is set"));
        assertTrue(row(rows, "6.2").warnings.contains("No injection rate"), "volume is set but the rate isn't");
        assertFalse(row(rows, "6.3").warnings.toString().contains("Name says"), "W WO has both, nothing to flag");
        assertTrue(row(rows, "6.4").warnings.isEmpty(), "W/O with no contrast is fine: " + row(rows, "6.4").warnings);
    }

    @Test void flagsAKvTheRestOfTheSectionDoesNotUseAndTypedValuesWin() {
        List<Protocol> protocols = new ArrayList<>(Arrays.asList(
                protocol("1.1", "CT HEAD", "120", false),
                protocol("1.2", "CT HEAD 2", "120", false),
                protocol("1.3", "CT HEAD 3", "100", false)));
        List<SectionRow> rows = SectionRow.forSection(protocols, new HashMap<>(), LABELS);
        assertEquals(Arrays.asList("kV 100 (most in this section use 120)"), row(rows, "1.3").warnings);

        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride fix = new ProtocolOverride();
        Map<String, String> series2 = new HashMap<>();
        series2.put("kv", "120");
        fix.getSeries().put("2", series2);
        fix.setTitle("CT Head Routine");
        overrides.put("1.3", fix);
        rows = SectionRow.forSection(protocols, overrides, LABELS);
        assertTrue(row(rows, "1.3").warnings.isEmpty());
        assertTrue(row(rows, "1.3").kv.edited);
        assertEquals("CT Head Routine", row(rows, "1.3").name.text);
        assertTrue(row(rows, "1.3").name.edited);
    }

    @Test void summarizesARealProtocol() throws Exception {
        List<Protocol> protocols = new ProtocolFolderWalker().parse(new File("src/test/resources/sample-protocols"));
        Protocol knee = null;
        for (Protocol p : protocols) if ("9.2".equals(p.getMetadata().getProtocolNumber())) knee = p;
        Map<String, ProtocolOverride> overrides = new HashMap<>();
        ProtocolOverride o = new ProtocolOverride();
        o.setContrastRate("3");
        o.getAddedFields().add(new ProtocolOverride.AddedField(null, "Oral contrast", "None"));
        overrides.put("9.2", o);

        SectionRow r = SectionRow.forSection(Arrays.asList(knee), overrides, LABELS).get(0);
        assertEquals("100 @ 3", r.contrast.text);
        assertTrue(r.contrast.edited);
        assertEquals("70 s", r.delay.text);
        assertFalse(r.delay.edited);
        assertEquals("+1 field", r.extras.text);
    }
}
