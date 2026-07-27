package pl.peterwolf.cinewolf.montage.timeline;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathStatistics;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.plan.MontageTimeMapping;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontageTimelinePlanBuilderTest {
    private final MontageTimelinePlanBuilder builder = new MontageTimelinePlanBuilder();

    @Test
    void placesNativeTracksOnSourceReplayTicksAndStoresElapsedOutputInTimelapseValues() {
        MontageTimelineWriteRequest request = request(10);

        MontageTimelinePlanBuilder.BuildResult result = builder.build(request);

        assertTrue(result.valid(), () -> "Unexpected errors: " + result.errors());
        MontageTimelineWritePlan plan = result.plan().orElseThrow();
        assertEquals(List.of(9_040, 9_060), plan.cameraKeyframes().stream()
                .map(MontageTimelineWritePlan.CameraPoint::timelineTick).toList());
        assertEquals(List.of(9_040, 9_060), plan.fovKeyframes().stream()
                .map(MontageTimelineWritePlan.FovPoint::timelineTick).toList());
        assertEquals(List.of(9_000, 9_060), plan.timelapseKeyframes().stream()
                .map(MontageTimelineWritePlan.TimelapsePoint::timelineTick).toList());
        assertEquals(List.of(0, 60), plan.timelapseKeyframes().stream()
                .map(MontageTimelineWritePlan.TimelapsePoint::outputElapsedTick).toList());
        assertEquals(new MontageTimelineInterval(9_000, 9_060), plan.sourceInterval());
    }

    @Test
    void calculatesTenTicksPerSecondForOneHundredSourceTicksOverTwoHundredOutputTicks() {
        MontageTimelineWritePlan.TimelapsePoint first =
                new MontageTimelineWritePlan.TimelapsePoint(100, 0);
        MontageTimelineWritePlan.TimelapsePoint second =
                new MontageTimelineWritePlan.TimelapsePoint(200, 200);

        assertEquals(10.0, first.ticksPerSecondTo(second), 1.0e-9);
    }

    @Test
    void encodesAdjacentShotsAsAOneTickHardCutWithoutOverwritingEitherCamera() {
        MontageTimelineWriteRequest request = new MontageTimelineWriteRequest(UUID.randomUUID(), 0,
                List.of(new MontageGeneratedShot(0.0, path(0.0, 5.0, 100, 200, 0.0, 10.0)),
                        new MontageGeneratedShot(5.0, path(0.0, 5.0, 200, 300, 50.0, 60.0))),
                List.of(MontageTimeMapping.between(0.0, 5.0, 100, 200),
                        MontageTimeMapping.between(5.0, 10.0, 200, 300)), 20);

        MontageTimelinePlanBuilder.BuildResult result = builder.build(request);

        assertTrue(result.valid(), () -> "Unexpected errors: " + result.errors());
        MontageTimelineWritePlan plan = result.plan().orElseThrow();
        assertEquals(List.of(100, 199, 200, 300), plan.cameraKeyframes().stream()
                .map(MontageTimelineWritePlan.CameraPoint::timelineTick).toList());
        MontageTimelineWritePlan.CameraPoint beforeCut = plan.cameraKeyframes().get(1);
        MontageTimelineWritePlan.CameraPoint afterCut = plan.cameraKeyframes().get(2);
        assertTrue(beforeCut.holdAfter());
        assertFalse(afterCut.holdAfter());
        assertEquals(10.0, beforeCut.position().x(), 1.0e-9);
        assertEquals(50.0, afterCut.position().x(), 1.0e-9);
        assertEquals(List.of(100, 199, 200, 300), plan.fovKeyframes().stream()
                .map(MontageTimelineWritePlan.FovPoint::timelineTick).toList());
        assertTrue(plan.fovKeyframes().get(1).holdAfter());
        assertFalse(plan.fovKeyframes().get(2).holdAfter());
    }

    @Test
    void bridgesASourceCutWithOneExportSecondSoFlashbackCannotSkipTheFollowingShot() {
        MontageTimelineWriteRequest request = new MontageTimelineWriteRequest(UUID.randomUUID(), 100,
                List.of(new MontageGeneratedShot(0.0, path(0.0, 1.0, 10, 30, 0.0, 4.0)),
                        new MontageGeneratedShot(1.0, path(0.0, 1.0, 50, 70, 10.0, 14.0))),
                List.of(MontageTimeMapping.between(0.0, 1.0, 10, 30),
                        MontageTimeMapping.between(1.0, 2.0, 50, 70)), 40);

        MontageTimelinePlanBuilder.BuildResult result = builder.build(request);

        assertTrue(result.valid(), () -> "Unexpected errors: " + result.errors());
        assertTrue(result.warnings().contains("montage.timeline.source_cut_bridged"));
        MontageTimelineWritePlan plan = result.plan().orElseThrow();
        List<Integer> outputs = plan.timelapseKeyframes().stream()
                .map(MontageTimelineWritePlan.TimelapsePoint::outputElapsedTick).toList();
        assertEquals(List.of(0, 20, 40, 60), outputs);
        assertEquals(List.of(10, 30, 50, 70), plan.timelapseKeyframes().stream()
                .map(MontageTimelineWritePlan.TimelapsePoint::timelineTick).toList());
    }

    @Test
    void rendersTheCompleteFinalShotForTheReportedTwentyFourFpsExport() {
        MontageTimelineWriteRequest request = new MontageTimelineWriteRequest(UUID.randomUUID(), 79,
                List.of(
                        new MontageGeneratedShot(0.0, path(0.0, 6.95, 140, 279, 0.0, 1.0)),
                        new MontageGeneratedShot(6.95, path(0.0, 2.7, 279, 333, 1.0, 2.0)),
                        new MontageGeneratedShot(9.65, path(0.0, 0.3, 333, 339, 2.0, 3.0)),
                        new MontageGeneratedShot(9.95, path(0.0, 6.7, 339, 473, 3.0, 4.0)),
                        new MontageGeneratedShot(16.65, path(0.0, 7.55, 724, 875, 4.0, 5.0))),
                List.of(
                        MontageTimeMapping.between(0.0, 6.95, 140, 279),
                        MontageTimeMapping.between(6.95, 9.65, 279, 333),
                        MontageTimeMapping.between(9.65, 9.95, 333, 339),
                        MontageTimeMapping.between(9.95, 16.65, 339, 473),
                        MontageTimeMapping.between(16.65, 24.2, 724, 875)),
                400);

        MontageTimelinePlanBuilder.BuildResult result = builder.build(request);

        assertTrue(result.valid(), () -> "Unexpected errors: " + result.errors());
        MontageTimelineWritePlan plan = result.plan().orElseThrow();
        assertEquals(List.of(0, 139, 193, 199, 333, 353, 504),
                plan.timelapseKeyframes().stream()
                        .map(MontageTimelineWritePlan.TimelapsePoint::outputElapsedTick).toList());
        ExportSimulation simulation = simulateFlashbackExport(plan.timelapseKeyframes(), 140, 875, 24, 724);
        assertEquals(605, simulation.totalFrames());
        assertEquals(181, simulation.framesAtOrAfterFinalShotStart());
    }

    @Test
    void rejectsTheCompleteCameraFovAndTimelapsePayloadAboveTheLimit() {
        MontageTimelinePlanBuilder.BuildResult result = builder.build(request(5));

        assertFalse(result.valid());
        assertTrue(result.errors().contains("montage.timeline.keyframe_limit_exceeded"));
    }

    private static MontageTimelineWriteRequest request(int limit) {
        return new MontageTimelineWriteRequest(new UUID(4L, 5L), 100,
                List.of(new MontageGeneratedShot(2.0, path(0.0, 1.0, 9_040))),
                List.of(MontageTimeMapping.between(0.0, 3.0, 9_000, 9_060)), limit);
    }

    private static CameraPathPlan path(double firstTime, double lastTime, long replayTime) {
        return path(firstTime, lastTime, replayTime, replayTime + 20, 0.0, 4.0);
    }

    private static CameraPathPlan path(double firstTime, double lastTime, long replayStart, long replayEnd,
                                       double firstX, double lastX) {
        CameraSample first = sample(firstTime, replayStart, firstX, 70.0 + firstX / 10.0);
        CameraSample last = sample(lastTime, replayEnd, lastX, 80.0 + lastX / 10.0);
        return new CameraPathPlan(TestFixtures.request(ShotType.ORBIT, 0.5, Math.max(1.0, lastTime),
                RotationDirection.CLOCKWISE, replayStart, replayEnd), List.of(first, last),
                List.of(first, last), List.of(), new PathStatistics(2, 2,
                Math.abs(lastX - firstX), Math.abs(lastX - firstX), 0.0));
    }

    private static CameraSample sample(double cinematicTime, long replayTime, double x, double fov) {
        return new CameraSample(cinematicTime, replayTime, new Vec3d(x, 4.0, 2.0), new Quaternionf(),
                20.0 + x, -5.0, 0.0, fov, new Vec3d(0.0, 1.0, 0.0), false);
    }

    /**
     * Mirrors Flashback 0.41.1 ExportJob source advancement: capture one frame, then advance the
     * source cursor by the active Timelapse tickrate divided by export FPS.
     */
    private static ExportSimulation simulateFlashbackExport(
            List<MontageTimelineWritePlan.TimelapsePoint> points,
            int exportStart, int exportEnd, int framesPerSecond, int finalShotStart) {
        double sourceTick = exportStart;
        int totalFrames = 0;
        int finalShotFrames = 0;
        while (sourceTick <= exportEnd && totalFrames < 100_000) {
            if (sourceTick >= finalShotStart) finalShotFrames++;
            MontageTimelineWritePlan.TimelapsePoint left = points.getFirst();
            MontageTimelineWritePlan.TimelapsePoint right = points.get(1);
            for (int index = 0; index < points.size() - 1; index++) {
                MontageTimelineWritePlan.TimelapsePoint candidateLeft = points.get(index);
                MontageTimelineWritePlan.TimelapsePoint candidateRight = points.get(index + 1);
                if (sourceTick < candidateRight.timelineTick() || index == points.size() - 2) {
                    left = candidateLeft;
                    right = candidateRight;
                    break;
                }
            }
            sourceTick += left.ticksPerSecondTo(right) / framesPerSecond;
            totalFrames++;
        }
        return new ExportSimulation(totalFrames, finalShotFrames);
    }

    private record ExportSimulation(int totalFrames, int framesAtOrAfterFinalShotStart) {
    }
}
