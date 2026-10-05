package com.protocolbook.reference;

import com.protocolbook.model.Metadata;
import com.protocolbook.model.Protocol;
import com.protocolbook.overrides.ProtocolOverride;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScanRangeMatcherTest {
    // Real sheet names from the site's AMG_CT_Protocols_Adult.xlsm / AMG_Protocols_PEDS.xlsx
    // (only the sheets that carry a scan range).
    private static final String[] ADULT_SHEETS = {
            "CTA DIEP Flap Protocol",
            "CTA Neck",
            "CTA Head & Neck",
            "CT Soft Tissue Neck",
            "CTA Upper Extremity",
            "CTA Endograft_Leak (Stent)",
            "CT AAA(known)",
            "CTA Bilat Run-off",
            "CTA Dissection AbdPel",
            "CTA Mes. Isch.",
            "CTA GI Bleed",
            "CT 4D Parathyroid",
            "CTA Dissection (Gated)",
            "CT TriPhase Liver&Pancreas",
            "CT Perfusion",
            "CT Head",
            "CT Facial Bones",
            "CT Orbits",
            "CT Temporal Bones",
            "CT Sinus",
            "CT Fusion SInus",
            "CT Stealth Head",
            "CTV Head",
            "CTA Head",
            "CTV Neck",
            "CT Pituitary",
            "CT C-spine",
            "CT L-spine",
            "CT T-spine",
            "CT Trauma CAP",
            "CT Onc. CAP",
            "CT Routine Chest",
            "CTA Pulmonary Embolus",
            "CTA AFIB",
            "CT Chest Venogram",
            "CT Cardio Lung",
            "Lung Cancer Screening",
            "CT Non-Contrast TAVR",
            "CTA TAVR",
            "CTA TAVR Tricuspid",
            "CT Routine Abd-Pel",
            "CT Venogram Abd-Pel",
            "CT Renal Mass",
            "CT Adrenal Mass (Washout)",
            "CT UrogramHematuria",
            "CT Cystogram",
            "CT Enterography",
            "CT Upper Ext. Shoulder_Humerus",
            "CT Upper Ext. Hand_Wrist_Elbow",
            "CT Entire Upper Ext.",
            "CT Lower Ext. Hip",
            "CT Patellar Tracking",
            "CT Lower Ext. Knee",
            "CT Lower Ext. Foot_Ankle",
            "CT Entire Lower Ext.",
            "CT Scanogram"
    };
    private static final String[] PEDS_SHEETS = {
            "PEDS CT Head",
            "PEDS CT Craniosynostosis",
            "PEDS CT Stealth Head",
            "PEDS CT Sinus",
            "PEDS CT Facial Bones",
            "PEDS CT Orbits",
            "PEDS CT C-Spine",
            "PEDS CT T-spine",
            "PEDS CT L-spine",
            "PEDS CT Soft Tissue Neck",
            "PEDS CT Temporal Bones",
            "PEDS CTA Head & Neck",
            "PEDS CTA Neck",
            "PEDS CT Routine Chest",
            "PEDS CTA Pulmonary Embolus",
            "PEDS CT Abd-Pel",
            "PEDS Upp. Ext Shoulder",
            "PEDS Upp. Ext Hnd_Wr_Elb",
            "PEDS Entire Upp. Ext.",
            "PEDS Low Ext Hip",
            "PEDS Low Ext Foot_Ankle",
            "PEDS Low Ext Knee",
            "PEDS Entire Low Ext"
    };

    private static List<ReferenceSheets.Sheet> sheets() {
        List<ReferenceSheets.Sheet> out = new ArrayList<ReferenceSheets.Sheet>();
        for (String n : ADULT_SHEETS) out.add(sheet(n, false, "range for " + n));
        for (String n : PEDS_SHEETS) out.add(sheet(n, true, "range for " + n));
        return out;
    }

    private static ReferenceSheets.Sheet sheet(String name, boolean peds, String... phaseRanges) {
        ReferenceSheets.Sheet s = new ReferenceSheets.Sheet("ref.xlsx", name, peds);
        for (String r : phaseRanges) s.ranges.add(new String[] { null, r });
        return s;
    }

    @Test void everySheetNameMatchesItselfUnambiguously() {
        ScanRangeMatcher matcher = new ScanRangeMatcher(sheets());
        for (String n : ADULT_SHEETS) assertEquals(n, name(matcher.match(n, false)), "adult sheet " + n);
        for (String n : PEDS_SHEETS) assertEquals(n, name(matcher.match(n, true)), "peds sheet " + n);
    }

    @Test void scannerStyleNamesFindTheirSheet() {
        ScanRangeMatcher matcher = new ScanRangeMatcher(sheets());
        // the scanner's own names from the sample export
        assertEquals("CT Lower Ext. Knee", name(matcher.match("CT LWR EXT KNEE WITH CONTRAST", false)));
        assertEquals("CT Upper Ext. Shoulder_Humerus", name(matcher.match("CT UP EXT SHOULDER/HUMERUS WITH CONTRAST", false)));
        assertEquals("CT Upper Ext. Hand_Wrist_Elbow", name(matcher.match("CT UP EXT HAND/WRIST/ELBOW WITH CONTRAST", false)));
        assertEquals("CT Entire Upper Ext.", name(matcher.match("CT ENTIRE UPPER EXT. WITH CONTRAST", false)));
        assertEquals("CT Lower Ext. Foot_Ankle", name(matcher.match("CT LWR EXT FOOT/ANKLE WITH CONTRAST", false)));
        assertEquals("CT C-spine", name(matcher.match("CT CERVICAL SPINE WITH CONTRAST", false)));
        assertEquals("CT L-spine", name(matcher.match("CT LUMBAR SPINE WITH CONTRAST", false)));
        // likely variants
        assertEquals("CT Routine Abd-Pel", name(matcher.match("CT ABD/PEL WITH CONTRAST", false)));
        assertEquals("CT Routine Chest", name(matcher.match("CT CHEST WITHOUT CONTRAST", false)));
        assertEquals("CT Non-Contrast TAVR", name(matcher.match("CT TAVR", false)));
        assertEquals("CTA Head", name(matcher.match("CTA HEAD", false)), "CTA must not fall back to the plain CT sheet");
        assertEquals("CT Head", name(matcher.match("CT HEAD W/O", false)));
    }

    @Test void noSheetIsBetterThanAWrongOne() {
        ScanRangeMatcher matcher = new ScanRangeMatcher(sheets());
        assertNull(matcher.match("CT BONY PELVIS WITH CONTRAST", false));
        assertNull(matcher.match("QA PHANTOM", false));
    }

    @Test void adultAndPedsNeverCrossMatch() {
        ScanRangeMatcher matcher = new ScanRangeMatcher(sheets());
        assertEquals("PEDS CT Head", name(matcher.match("CT HEAD", true)));
        assertEquals("CT Head", name(matcher.match("CT HEAD", false)));
        assertNull(matcher.match("CT CRANIOSYNOSTOSIS", false), "there's only a PEDS craniosynostosis sheet");
    }

    @Test void identicalRangesCollapseToOneLineDifferentOnesListEachPhase() {
        ReferenceSheets.Sheet same = new ReferenceSheets.Sheet("ref.xlsx", "CT Head", false);
        same.ranges.add(new String[] { "NON-CONTRAST", "Base of Skull to vertex" });
        same.ranges.add(new String[] { "POST-CONTRAST 5 min. Delay", "Base of skull to vertex" });
        assertEquals(Collections.singletonList("Base of Skull to vertex"), same.lines());

        ReferenceSheets.Sheet differ = new ReferenceSheets.Sheet("ref.xlsx", "CT Renal Mass", false);
        differ.ranges.add(new String[] { "NON-CONTRAST Abd only", "Diaphragm to Crests" });
        differ.ranges.add(new String[] { "50 Sec. ABD/PEL", "Diaphragm to Ish. Tub." });
        assertEquals(Arrays.asList("NON-CONTRAST Abd only: Diaphragm to Crests", "50 Sec. ABD/PEL: Diaphragm to Ish. Tub."), differ.lines());
    }

    @Test void overridesWinAndAreReported() {
        ScanRangeMatcher matcher = new ScanRangeMatcher(sheets());
        Protocol pelvis = protocol("8.6", "CT BONY PELVIS WITH CONTRAST");
        Protocol knee = protocol("9.2", "CT LWR EXT KNEE WITH CONTRAST");
        Protocol typed = protocol("9.4", "CT LWR EXT HIP WITH CONTRAST");
        Protocol missing = protocol("3.7", "CT CERVICAL SPINE WITH CONTRAST");
        Map<String, ProtocolOverride> overrides = new HashMap<String, ProtocolOverride>();
        overrides.put("8.6", new ProtocolOverride());
        overrides.get("8.6").setReferenceSheet("ct routine abd-pel");
        overrides.put("9.4", new ProtocolOverride());
        overrides.get("9.4").setScanRange("Iliac crest to mid femur");
        overrides.put("3.7", new ProtocolOverride());
        overrides.get("3.7").setReferenceSheet("No Such Sheet");

        List<String> report = matcher.apply(Arrays.asList(pelvis, knee, typed, missing), overrides);

        assertEquals("range for CT Routine Abd-Pel", pelvis.getPatientSetup().getScanRange(), "referenceSheet override pins the sheet, case-insensitively");
        assertEquals("range for CT Lower Ext. Knee", knee.getPatientSetup().getScanRange(), "no override: matched by name");
        assertEquals("Iliac crest to mid femur", typed.getPatientSetup().getScanRange(), "a typed scanRange wins over any sheet");
        assertNull(missing.getPatientSetup().getScanRange(), "a misspelled referenceSheet must not fall back to guessing");
        assertTrue(String.join("\n", report).contains("WARNING: 3.7 CT CERVICAL SPINE WITH CONTRAST: referenceSheet \"No Such Sheet\" not found"));
    }

    private static Protocol protocol(String number, String name) {
        Protocol p = new Protocol();
        p.setMetadata(new Metadata());
        p.getMetadata().setProtocolNumber(number);
        p.getMetadata().setName(name);
        return p;
    }

    private static String name(ReferenceSheets.Sheet s) {
        return s == null ? null : s.name;
    }
}
