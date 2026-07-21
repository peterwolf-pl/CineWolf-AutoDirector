package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.EasingType;

import static org.junit.jupiter.api.Assertions.*;

class EasingsTest {
    @Test
    void allEasingsPreserveEndpointsAndRemainFinite() {
        for (EasingType type : EasingType.values()) {
            assertEquals(0.0, Easings.apply(type, 0.0), 1.0e-12, type.name());
            assertEquals(1.0, Easings.apply(type, 1.0), 1.0e-12, type.name());
            assertTrue(Double.isFinite(Easings.apply(type, 0.37)), type.name());
        }
    }

    @Test
    void smootherstepIsSymmetric() {
        assertEquals(1.0 - Easings.apply(EasingType.SMOOTHERSTEP, 0.25),
                Easings.apply(EasingType.SMOOTHERSTEP, 0.75), 1.0e-12);
    }
}
