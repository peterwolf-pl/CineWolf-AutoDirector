package pl.peterwolf.cinewolf.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObstacleHandlingModeTest {
    @Test
    void migratesLegacyCollisionFlag() {
        assertEquals(ObstacleHandlingMode.AVOID, ObstacleHandlingMode.fromLegacy(true));
        assertEquals(ObstacleHandlingMode.NONE, ObstacleHandlingMode.fromLegacy(false));
    }

    @Test
    void normalizeMapsLegacyBooleanWhenModeMissing() {
        MontageConfig config = new MontageConfig();
        config.obstacleHandling = null;
        config.collisionAvoidance = false;
        config.normalize();
        assertEquals(ObstacleHandlingMode.NONE, config.obstacleHandling());
        assertFalse(config.collisionAvoidance);

        config.obstacleHandling = "CLIP";
        config.normalize();
        assertEquals(ObstacleHandlingMode.CLIP, config.obstacleHandling());
        assertFalse(config.collisionAvoidance);
        assertTrue(config.obstacleHandling().clipsOccluders());
    }
}
