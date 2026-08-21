package com.protocolbook.labels;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class LabelConfigTest {
    private final LabelConfig defaults = new LabelConfig(new HashMap<>(), new HashMap<>(), new HashMap<>());

    @Test void adultPrefixesMapToTheScannerConsolesOwnNumbering() {
        assertEquals("Head", defaults.categoryForNumber(1));
        assertEquals("Chest", defaults.categoryForNumber(5));
        assertEquals("Lower Ext.", defaults.categoryForNumber(9));
    }

    @Test void pediatricPrefixesAreTheAdultPrefixPlusTen() {
        assertEquals("Head", defaults.categoryForNumber(11));
        assertEquals("Chest", defaults.categoryForNumber(15));
        assertEquals("Lower Ext.", defaults.categoryForNumber(19));
    }

    @Test void qaPhantomPrefixesHaveNoMapping() {
        assertNull(defaults.categoryForNumber(10));
        assertNull(defaults.categoryForNumber(20));
    }

    @Test void asirCodeMapsToItsPercentage() {
        assertEquals("40%", defaults.asir("AR40"));
        assertEquals("50%", defaults.asir("AR50"));
        assertEquals("0%", defaults.asir("AR0"));
        assertEquals("40%", defaults.asir("ar40"), "the AR prefix should be matched case-insensitively");
    }

    @Test void unrecognizedAsirCodeFallsBackToTheRawCode() {
        assertEquals("STD", defaults.asir("STD"));
        assertNull(defaults.asir(null));
    }
}
