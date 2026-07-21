package pl.peterwolf.cinewolf.montage.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.plan.MontageTransitionType;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontageProjectManagerTest {
    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REPLAY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SHOT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID TARGET_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @TempDir
    Path temporaryDirectory;

    @Test
    void projectRoundTripsThroughExplicitTemporaryDirectory() throws Exception {
        MontageProjectManager manager = new MontageProjectManager(temporaryDirectory);
        MontageProject expected = sampleProject();

        Path saved = manager.saveProject(expected);
        MontageProjectManager.LoadResult loaded = manager.loadProject(PROJECT_ID);

        assertEquals(temporaryDirectory.toAbsolutePath().normalize(), saved.getParent());
        assertTrue(loaded.loaded());
        assertEquals(expected, loaded.project().orElseThrow());
        assertTrue(loaded.warnings().isEmpty());
        try (var files = Files.list(temporaryDirectory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void malformedProjectIsQuarantinedAndReturnedAsEmpty() throws Exception {
        MontageProjectManager manager = new MontageProjectManager(temporaryDirectory);
        Files.createDirectories(temporaryDirectory);
        Path malformed = temporaryDirectory.resolve(PROJECT_ID + ".json");
        Files.writeString(malformed, "{ definitely-not-json");

        MontageProjectManager.LoadResult result = manager.loadProject(PROJECT_ID);

        assertFalse(result.loaded());
        assertTrue(result.warnings().contains("montage.project.malformed"));
        assertFalse(Files.exists(malformed));
        assertTrue(result.quarantinedFile().isPresent());
        assertTrue(Files.isRegularFile(result.quarantinedFile().orElseThrow()));
        assertTrue(result.quarantinedFile().orElseThrow().getFileName().toString().contains(".broken"));
    }

    @Test
    void persistedProjectAndDebugExportContainNoRawSampleArrays() throws Exception {
        MontageProjectManager manager = new MontageProjectManager(temporaryDirectory);
        MontageProject project = sampleProject();
        Path projectFile = manager.saveProject(project);
        MontageDebugExport debugExport = sampleDebugExport(project);
        Path debugFile = manager.saveDebugExport(debugExport);

        String projectJson = Files.readString(projectFile);
        String debugJson = Files.readString(debugFile);
        for (String json : List.of(projectJson, debugJson)) {
            assertFalse(json.contains("\"samples\""));
            assertFalse(json.contains("\"movementMetrics\""));
            assertFalse(json.contains("ReplaySample"));
        }
        assertTrue(debugJson.contains("\"events\""));
        assertTrue(debugJson.contains("\"scenes\""));
        assertTrue(debugJson.contains("\"plan\""));
        assertTrue(debugJson.contains("\"finalScore\""));
    }

    @Test
    void stringLookupCannotEscapeProjectDirectory() {
        MontageProjectManager manager = new MontageProjectManager(temporaryDirectory);
        String outsideName = "outside-" + UUID.randomUUID();

        MontageProjectManager.LoadResult traversal = manager.loadProject("../" + outsideName);
        MontageProjectManager.LoadResult absolute = manager.loadProject(temporaryDirectory.resolve("outside").toString());

        assertFalse(traversal.loaded());
        assertFalse(absolute.loaded());
        assertEquals(List.of("montage.project.unsafe_id"), traversal.warnings());
        assertEquals(List.of("montage.project.unsafe_id"), absolute.warnings());
        assertFalse(Files.exists(temporaryDirectory.getParent().resolve(outsideName + ".json")));
    }

    private static MontageProject sampleProject() {
        MontageProject.TargetSummary target = new MontageProject.TargetSummary(TARGET_ID, "minecraft:player", "Player");
        MontageProject.AnalysisSettingsSummary settings = new MontageProject.AnalysisSettingsSummary(
                100, 1_300, List.of(target), true,
                List.of(ReplayEventType.HIGH_SPEED, ReplayEventType.REPLAY_MARKER), 0.6, 4, 16);
        MontageProject.AnalysisStatisticsSummary statistics = new MontageProject.AnalysisStatisticsSummary(
                1_200, 300, 60, 140, 180, 3, 8, 5, 2,
                Map.of(ReplayEventType.HIGH_SPEED, 4, ReplayEventType.REPLAY_MARKER, 1));
        MontageProject.EvidenceSummary evidence = new MontageProject.EvidenceSummary(
                List.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT),
                List.of(new MontageProject.MeasurementSummary("smoothed_speed", 8.5, "blocks_per_second",
                        EventEvidence.Comparison.GREATER_THAN_OR_EQUAL, 5.5)),
                List.of(new MontageProject.AttributeSummary("context", "player")), List.of());
        MontageProject.EventSummary event = new MontageProject.EventSummary(EVENT_ID, ReplayEventType.HIGH_SPEED,
                300, 340, 380, List.of(TARGET_ID), new MontageProject.PositionSummary(10, 65, -4),
                0.85, 0.9, evidence, 0.8, 0.9, 0.7, 0.95,
                0.0, 0.1, 0.0, 0.02, 0.86, List.of("event.score.high_speed"));
        MontageProject.PresetSummary preset = new MontageProject.PresetSummary("30_seconds", 30,
                OutputAspectRatio.LANDSCAPE_16_9, MontagePacing.MODERATE, 2.5, 7.0, 5, 8);
        MontageProject.ShotParametersSummary parameters = new MontageProject.ShotParametersSummary(
                12, 3, 8, 16, 3, 0.5, 5, 0, RotationDirection.CLOCKWISE,
                4, 70, EasingType.SMOOTHERSTEP, 0.2);
        MontageProject.PlannedShotSummary shot = new MontageProject.PlannedShotSummary(SHOT_ID, 0, EVENT_ID,
                ReplayEventType.HIGH_SPEED, 0.86, target, ShotType.FOLLOW, FramingType.MEDIUM,
                280, 380, 0, 5, 1, parameters, true, true,
                List.of("montage.reason.follow_motion"),
                List.of(new MontageProject.DiagnosticSummary("montage.warning.example", "INFO", List.of())));
        MontageProject.ManualEditSummary edit = new MontageProject.ManualEditSummary(
                SHOT_ID, "fov", "70", "65", 1_721_587_200_000L);
        return new MontageProject(MontageProject.CURRENT_SCHEMA_VERSION, PROJECT_ID, REPLAY_ID,
                100, 1_300, settings, statistics, List.of(event), preset, List.of(shot), List.of(edit),
                List.of(SHOT_ID), 1_721_587_200_000L, "1.2.0",
                List.of(new MontageProject.DiagnosticSummary("analysis.partial", "WARNING", List.of("test"))),
                List.of("event.score.high_speed", "montage.reason.follow_motion"));
    }

    private static MontageDebugExport sampleDebugExport(MontageProject project) {
        MontageDebugExport.SceneSummary scene = new MontageDebugExport.SceneSummary(
                UUID.fromString("66666666-6666-6666-6666-666666666666"), 100, 500,
                pl.peterwolf.cinewolf.montage.scene.SceneType.MOVEMENT, List.of(TARGET_ID),
                new MontageProject.PositionSummary(10, 65, -4), 20, 0.8, List.of(EVENT_ID));
        MontageDebugExport.PlanStatisticsSummary statistics = new MontageDebugExport.PlanStatisticsSummary(
                1, 1, 1, 1, 1, 5, 1);
        MontageDebugExport.TransitionSummary transition = new MontageDebugExport.TransitionSummary(
                SHOT_ID, SHOT_ID, MontageTransitionType.HARD_CUT, 0, List.of("debug.single_shot"));
        MontageDebugExport.TimeMappingSummary mapping = new MontageDebugExport.TimeMappingSummary(0, 5, 280, 380, 1);
        MontageDebugExport.PlanSummary plan = new MontageDebugExport.PlanSummary(PROJECT_ID, "30_seconds",
                100, 1_300, 30, statistics, project.plannedShots(), List.of(transition), List.of(mapping));
        return new MontageDebugExport(MontageDebugExport.CURRENT_SCHEMA_VERSION, PROJECT_ID, REPLAY_ID,
                1_721_587_200_000L, "1.2.0", project.analysisSettings(), project.analysisStatistics(),
                project.events(), List.of(scene), plan, project.warnings(), project.reasons());
    }
}
