package com.protocolbook.duplicates;

import com.protocolbook.model.Protocol;
import com.protocolbook.parser.ProtocolFolderWalker;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateFinderTest {
    private static List<Protocol> fixtures() throws Exception {
        return new ProtocolFolderWalker().parse(new File("src/test/resources/sample-protocols"));
    }

    private static Set<String> numbers(List<Protocol> group) {
        Set<String> out = new TreeSet<String>();
        for (Protocol p : group) out.add(p.getMetadata().getProtocolNumber());
        return out;
    }

    @Test void findsTheSameExamFiledUnderTwoNumbers() throws Exception {
        DuplicateFinder.Result r = DuplicateFinder.find(fixtures());
        // "CT ENTIRE LWR EXT WITH CONTRAST" is saved as both 8.4 (Pelvis) and 9.6 (Lower Ext.) with identical settings
        assertEquals(1, r.identical.size());
        assertEquals(new TreeSet<String>(Arrays.asList("8.4", "9.6")), numbers(r.identical.get(0)));
    }

    @Test void sameNameWithDifferentSettingsIsListedWithWhatDiffers() throws Exception {
        DuplicateFinder.Result r = DuplicateFinder.find(fixtures());
        // "CT LWR EXT HIP WITH CONTRAST" is 8.2 and 9.4, but their MAR recons use different kernels
        assertEquals(1, r.sameNameDifferent.size());
        List<Protocol> hips = r.sameNameDifferent.get(0);
        assertEquals(new TreeSet<String>(Arrays.asList("8.2", "9.4")), numbers(hips));
        List<String> diffs = DuplicateFinder.differences(hips);
        assertFalse(diffs.isEmpty());
        assertTrue(diffs.stream().anyMatch(d -> d.contains("kernel")), "the kernel difference should be named: " + diffs);
        assertTrue(diffs.stream().noneMatch(d -> d.contains(": kV")), "identical settings must not be listed as differences");
    }

    @Test void nameAndNumberDoNotCountAsSettings() throws Exception {
        List<Protocol> protocols = fixtures();
        Protocol renamed = protocols.get(0);
        String before = DuplicateFinder.fingerprint(renamed);
        renamed.getMetadata().setName("SOMETHING ELSE");
        renamed.getMetadata().setProtocolNumber("1.1");
        assertEquals(before, DuplicateFinder.fingerprint(renamed));
    }
}
