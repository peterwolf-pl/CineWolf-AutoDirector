package pl.peterwolf.cinewolf.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TimelapseConfigTest {
    @Test
    void x1KeepsNormalFlashbackSampling() {
        TimelapseConfig config = new TimelapseConfig();

        assertFalse(config.enabled());
        assertEquals(60.0, config.samplingFps(60.0), 1.0e-9);
    }

    @Test
    void x10RendersOneTenthAsManyOutputSamples() {
        TimelapseConfig config = new TimelapseConfig();
        config.speedMultiplier = 10.0;

        assertTrue(config.enabled());
        assertEquals(6.0, config.samplingFps(60.0), 1.0e-9);
        assertEquals(3.0, config.samplingFps(30.0), 1.0e-9);
    }

    @Test
    void multiplierIsClampedToSafeRange() {
        TimelapseConfig config = new TimelapseConfig();

        config.speedMultiplier = 0.0;
        config.normalize();
        assertEquals(1.0, config.speedMultiplier, 1.0e-9);

        config.speedMultiplier = 10_000.0;
        config.normalize();
        assertEquals(1000.0, config.speedMultiplier, 1.0e-9);
    }
}
