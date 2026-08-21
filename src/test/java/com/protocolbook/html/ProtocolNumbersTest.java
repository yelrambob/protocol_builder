package com.protocolbook.html;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolNumbersTest {
    @Test void twoSegmentNumbersAreAdult() {
        assertFalse(ProtocolNumbers.isPediatric("9.2"));
        assertFalse(ProtocolNumbers.isPediatric("9"));
        assertFalse(ProtocolNumbers.isPediatric(null));
    }

    @Test void threeOrMoreSegmentNumbersArePediatric() {
        assertTrue(ProtocolNumbers.isPediatric("9.2.1"));
        assertTrue(ProtocolNumbers.isPediatric("9.2.1.3"));
    }

    @Test void compareSortsNumericallyNotLexicographically() {
        assertTrue(ProtocolNumbers.compare("9.2", "9.10") < 0, "9.2 should sort before 9.10");
        assertTrue(ProtocolNumbers.compare("9.10", "9.2") > 0);
        assertEquals(0, ProtocolNumbers.compare("9.2", "9.2"));
    }

    @Test void compareHandlesThreeSegmentPediatricNumbers() {
        assertTrue(ProtocolNumbers.compare("9.1.2", "9.1.10") < 0, "9.1.2 should sort before 9.1.10");
        assertTrue(ProtocolNumbers.compare("9.1", "9.1.1") < 0, "the two-segment adult number should sort before its pediatric variant");
    }

    @Test void compareSortsUnparseableNumbersLast() {
        assertTrue(ProtocolNumbers.compare(null, "9.2") > 0);
        assertTrue(ProtocolNumbers.compare("9.2", null) < 0);
        assertTrue(ProtocolNumbers.compare("not-a-number", "9.2") > 0);
    }
}
