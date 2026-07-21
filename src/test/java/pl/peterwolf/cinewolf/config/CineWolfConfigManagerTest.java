package pl.peterwolf.cinewolf.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CineWolfConfigManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesOldVersionAndNormalizesLimits() throws Exception {
        Path path = temporaryDirectory.resolve("cinewolf-autodirector.json");
        Files.writeString(path, "{\"version\":0,\"samplesPerSecond\":2,\"maximumKeyframes\":1}");
        CineWolfConfig config = new CineWolfConfigManager(LoggerFactory.getLogger("test"), path).load();
        assertEquals(CineWolfConfig.CURRENT_VERSION, config.version);
        assertEquals(8, config.samplesPerSecond);
        assertEquals(16, config.maximumKeyframes);
        assertNotNull(config.montage);
        assertNotNull(config.montage.detectorThresholds);
        assertNotNull(config.montage.eventScoring);
        assertNotNull(config.montage.shotDiversity);
        assertNotNull(config.pathSmoothing);
        assertTrue(config.pathSmoothing.enabled);
        assertEquals(0.65, config.pathSmoothing.positionStrength, 1.0e-9);
        assertEquals(0.55, config.pathSmoothing.rotationStrength, 1.0e-9);
        assertEquals(30.0, config.montage.outputDurationSeconds, 1.0e-9);
    }

    @Test
    void normalizesMontageOverridesWithoutResettingLegacyShotSettings() throws Exception {
        Path path = temporaryDirectory.resolve("cinewolf-autodirector.json");
        Files.writeString(path, """
                {"version":1,"diameter":24.0,"montage":{"eventSensitivity":5.0,
                "coarseSamplesPerSecond":99,"minimumShotDuration":9.0,"maximumShotDuration":2.0}}
                """);

        CineWolfConfig config = new CineWolfConfigManager(LoggerFactory.getLogger("test"), path).load();

        assertEquals(24.0, config.diameter, 1.0e-9);
        assertEquals(1.0, config.montage.eventSensitivity, 1.0e-9);
        assertEquals(5, config.montage.coarseSamplesPerSecond);
        assertEquals(9.0, config.montage.minimumShotDuration, 1.0e-9);
        assertEquals(9.0, config.montage.maximumShotDuration, 1.0e-9);
    }

    @Test
    void malformedConfigurationIsBackedUpAndRecovered() throws Exception {
        Path path = temporaryDirectory.resolve("cinewolf-autodirector.json");
        Files.writeString(path, "{ definitely not json");
        CineWolfConfig config = new CineWolfConfigManager(LoggerFactory.getLogger("test"), path).load();
        assertNotNull(config);
        assertEquals(ShotDefaults.ORBIT_DIAMETER, config.diameter, 1.0e-9);
        assertTrue(Files.exists(path));
        assertTrue(Files.exists(path.resolveSibling("cinewolf-autodirector.json.malformed")));
    }

    @Test
    void normalizesVersionThreeAnalysisProfiles() throws Exception {
        Path path = temporaryDirectory.resolve("cinewolf-autodirector.json");
        Files.writeString(path, """
                {"version":2,"montage":{"detectorThresholds":{"turnDegrees":-2},
                "eventScoring":{"importanceWeight":-1,"baseImportance":{"DEATH":7}},
                "shotDiversity":{"repeatedShotPenalty":-4}}}
                """);

        CineWolfConfig config = new CineWolfConfigManager(LoggerFactory.getLogger("test"), path).load();

        assertEquals(45.0, config.montage.detectorThresholds.turnDegrees, 1.0e-9);
        assertEquals(0.35, config.montage.eventScoring.importanceWeight, 1.0e-9);
        assertEquals(1.0, config.montage.eventScoring.baseImportance.get(
                pl.peterwolf.cinewolf.montage.event.ReplayEventType.DEATH), 1.0e-9);
        assertEquals(0.22, config.montage.shotDiversity.repeatedShotPenalty, 1.0e-9);
    }

    @Test
    void preservesAndNormalizesPathSmoothingOverrides() throws Exception {
        Path path = temporaryDirectory.resolve("cinewolf-autodirector.json");
        Files.writeString(path, """
                {"version":3,"diameter":24.0,"pathSmoothing":{"enabled":false,
                "positionStrength":4.0,"rotationStrength":-2.0,"windowSeconds":8.0,
                "outlierRejection":false,"outlierThresholdBlocks":0.01,
                "outlierSpeedThresholdBlocksPerSecond":900.0}}
                """);

        CineWolfConfig config = new CineWolfConfigManager(LoggerFactory.getLogger("test"), path).load();

        assertEquals(4, config.version);
        assertEquals(24.0, config.diameter, 1.0e-9);
        assertFalse(config.pathSmoothing.enabled);
        assertFalse(config.pathSmoothing.outlierRejection);
        assertEquals(1.0, config.pathSmoothing.positionStrength, 1.0e-9);
        assertEquals(0.0, config.pathSmoothing.rotationStrength, 1.0e-9);
        assertEquals(2.0, config.pathSmoothing.windowSeconds, 1.0e-9);
        assertEquals(0.25, config.pathSmoothing.outlierThresholdBlocks, 1.0e-9);
        assertEquals(512.0, config.pathSmoothing.outlierSpeedThresholdBlocksPerSecond, 1.0e-9);

        String saved = Files.readString(path);
        assertTrue(saved.contains("\"pathSmoothing\""));
        assertTrue(saved.contains("\"positionStrength\": 1.0"));
    }

    @Test
    void restoresMissingPathSmoothingObjectWithoutResettingOtherSettings() throws Exception {
        Path path = temporaryDirectory.resolve("cinewolf-autodirector.json");
        Files.writeString(path, "{\"version\":3,\"diameter\":18.0,\"pathSmoothing\":null}");

        CineWolfConfig config = new CineWolfConfigManager(LoggerFactory.getLogger("test"), path).load();

        assertEquals(18.0, config.diameter, 1.0e-9);
        assertNotNull(config.pathSmoothing);
        assertEquals(0.30, config.pathSmoothing.windowSeconds, 1.0e-9);
        assertTrue(config.samplingSettings().pathSmoothing().enabled());
    }

    private static final class ShotDefaults {
        private static final double ORBIT_DIAMETER = 12.0;
    }
}
