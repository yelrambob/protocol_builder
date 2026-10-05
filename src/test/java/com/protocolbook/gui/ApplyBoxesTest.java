package com.protocolbook.gui;

import com.protocolbook.overrides.ProtocolOverride;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApplyBoxesTest {
    private static ProtocolOverride.ScanRangePicture legs() {
        ProtocolOverride.ScanRangePicture pic = new ProtocolOverride.ScanRangePicture("legs.png");
        pic.getBoxes().add(new ProtocolOverride.Box("Arterial", "#e53935", 0.1, 0.1, 0.5, 0.3));
        pic.getBoxes().add(new ProtocolOverride.Box("Venous", "#1e88e5", 0.1, 0.5, 0.5, 0.3));
        return pic;
    }

    @Test void applyingCopiesTheBoxesAndReplacesOnlyThatPicture() {
        ProtocolOverride target = new ProtocolOverride();
        target.getScanRangePictures().add(new ProtocolOverride.ScanRangePicture("chest.png"));
        ProtocolOverride.ScanRangePicture old = new ProtocolOverride.ScanRangePicture("legs.png");
        old.getBoxes().add(new ProtocolOverride.Box("Old", "#000000", 0, 0, 1, 1));
        target.getScanRangePictures().add(old);

        ProtocolOverride.ScanRangePicture source = legs();
        ApplyBoxesDialog.applyTo(target, source);

        assertEquals(2, target.getScanRangePictures().size(), "the other picture is kept, legs.png is replaced not added again");
        ProtocolOverride.ScanRangePicture copied = target.getScanRangePictures().get(1);
        assertTrue(ApplyBoxesDialog.sameBoxes(copied.getBoxes(), source.getBoxes()));
        source.getBoxes().get(0).setX(0.4);
        assertEquals(0.1, copied.getBoxes().get(0).getX(), 1e-9, "each protocol gets its own copy");
        assertFalse(ApplyBoxesDialog.sameBoxes(copied.getBoxes(), source.getBoxes()));

        ProtocolOverride fresh = new ProtocolOverride();
        ApplyBoxesDialog.applyTo(fresh, legs());
        assertEquals(1, fresh.getScanRangePictures().size());
    }
}
