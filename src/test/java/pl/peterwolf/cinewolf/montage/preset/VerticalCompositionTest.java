package pl.peterwolf.cinewolf.montage.preset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VerticalCompositionTest {
    @Test
    void exportResolutions() {
        assertArrayEquals(new int[]{1080, 1920},
                VerticalComposition.exportResolution(OutputAspectRatio.VERTICAL_9_16));
        assertArrayEquals(new int[]{1920, 1080},
                VerticalComposition.exportResolution(OutputAspectRatio.LANDSCAPE_16_9));
    }

    @Test
    void verticalPlanningHelpers() {
        assertTrue(VerticalComposition.distanceMultiplier(OutputAspectRatio.VERTICAL_9_16) > 1.0);
        assertEquals(1.0, VerticalComposition.distanceMultiplier(OutputAspectRatio.LANDSCAPE_16_9));
        assertEquals(FramingType.WIDE,
                VerticalComposition.verticalSafeFraming(FramingType.EXTREME_WIDE));
        assertEquals(FramingType.MEDIUM,
                VerticalComposition.verticalSafeFraming(FramingType.WIDE));
        assertTrue(VerticalComposition.fovForFraming(FramingType.WIDE, OutputAspectRatio.VERTICAL_9_16)
                < VerticalComposition.fovForFraming(FramingType.WIDE, OutputAspectRatio.LANDSCAPE_16_9));
    }
}
