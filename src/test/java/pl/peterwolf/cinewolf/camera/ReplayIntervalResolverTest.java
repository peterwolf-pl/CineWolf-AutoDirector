package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayIntervalResolverTest {
    private final ReplayIntervalResolver resolver = new ReplayIntervalResolver();

    @Test
    void clipsDurationAtReplayEnd() {
        ReplayIntervalResolver.Resolution result = resolver.resolve(false, -1, -1, 150, 200, 10.0);
        assertEquals(150, result.startTick());
        assertEquals(200, result.endTick());
        assertEquals(2.5, result.durationSeconds(), 1.0e-9);
        assertTrue(result.clippedToReplayEnd());
    }

    @Test
    void preservesDurationThatFits() {
        ReplayIntervalResolver.Resolution result = resolver.resolve(false, -1, -1, 20, 200, 4.0);
        assertEquals(20, result.startTick());
        assertEquals(100, result.endTick());
        assertEquals(4.0, result.durationSeconds(), 1.0e-9);
        assertFalse(result.clippedToReplayEnd());
    }

    @Test
    void preservesSelectedFlashbackRange() {
        ReplayIntervalResolver.Resolution result = resolver.resolve(true, 25, 125, 80, 200, 10.0);
        assertEquals(25, result.startTick());
        assertEquals(125, result.endTick());
        assertEquals(5.0, result.durationSeconds(), 1.0e-9);
        assertTrue(result.selectedRange());
        assertFalse(result.clippedToReplayEnd());
    }

    @Test
    void clampsSelectedRangeToReplayBounds() {
        ReplayIntervalResolver.Resolution result = resolver.resolve(true, -20, 250, 80, 200, 10.0);
        assertEquals(0, result.startTick());
        assertEquals(200, result.endTick());
        assertTrue(result.clippedToReplayEnd());
    }

    @Test
    void invalidDurationProducesEmptyIntervalForExistingValidation() {
        ReplayIntervalResolver.Resolution result = resolver.resolve(false, -1, -1, 50, 200, Double.NaN);
        assertEquals(50, result.startTick());
        assertEquals(50, result.endTick());
        assertEquals(0.0, result.durationSeconds(), 1.0e-9);
    }
}
