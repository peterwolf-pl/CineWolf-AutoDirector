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
    void rejectsAZeroDurationSourceCutBetweenAdjacentMappings() {
        MontageTimelineWriteRequest request = new MontageTimelineWriteRequest(UUID.randomUUID(), 100,
                List.of(new MontageGeneratedShot(0.0, path(0.0, 2.0, 10))),
                List.of(MontageTimeMapping.between(0.0, 1.0, 10, 30),
                        MontageTimeMapping.between(1.0, 2.0, 50, 70)), 20);

        MontageTimelinePlanBuilder.BuildResult result = builder.build(request);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("montage.timeline.source_cut_not_supported"));
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
}
