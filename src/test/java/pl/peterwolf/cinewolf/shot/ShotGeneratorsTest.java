package pl.peterwolf.cinewolf.shot;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.camera.CameraMath;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import static org.junit.jupiter.api.Assertions.*;

class ShotGeneratorsTest {
    @Test
    void orbitUsesRequestedRadiusAndRpmConversion() {
        ShotRequest request = TestFixtures.request(ShotType.ORBIT, 15.0, 1.0, RotationDirection.CLOCKWISE, 0, 20);
        CameraPathPlan plan = new OrbitShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0.0)));
        assertTrue(plan.valid());
        assertEquals(6.0, plan.samples().getFirst().position().distanceTo(new Vec3d(0.0, 3.0, 0.0)), 1.0e-6);
        assertEquals(0.25, request.revolutions(), 1.0e-9);
        assertEquals(0.0, plan.samples().getLast().position().x(), 1.0e-6);
        assertEquals(-6.0, plan.samples().getLast().position().z(), 1.0e-6);
    }

    @Test
    void orbitDirectionChangesSign() {
        ShotRequest clockwise = TestFixtures.request(ShotType.ORBIT, 15.0, 1.0, RotationDirection.CLOCKWISE, 0, 20);
        ShotRequest counter = TestFixtures.request(ShotType.ORBIT, 15.0, 1.0, RotationDirection.COUNTERCLOCKWISE, 0, 20);
        var context = TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0.0));
        assertTrue(new OrbitShotGenerator().generate(clockwise, context).samples().getLast().position().z() < 0.0);
        assertTrue(new OrbitShotGenerator().generate(counter, context).samples().getLast().position().z() > 0.0);
    }

    @Test
    void orbitFollowsMovingTarget() {
        ShotRequest request = TestFixtures.request(ShotType.ORBIT, 0.0, 2.0, RotationDirection.CLOCKWISE, 0, 40);
        CameraPathPlan plan = new OrbitShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(new Vec3d(tick / 20.0, 0, 0), new Vec3d(1, 0, 0), 0)));
        assertEquals(6.0, plan.samples().getFirst().position().x(), 1.0e-6);
        assertEquals(8.0, plan.samples().getLast().position().x(), 1.0e-6);
    }

    @Test
    void orbitIncludesAcceptedAdaptiveReplayTicks() {
        ShotRequest request = TestFixtures.request(ShotType.ORBIT, 0.0, 1.0,
                RotationDirection.CLOCKWISE, 0, 20);
        ReplayContext context = new ReplayContext(
                (target, tick) -> java.util.Optional.of(TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0.0)),
                SamplingSettings.defaults(), java.util.List.of(1L));
        CameraPathPlan plan = new OrbitShotGenerator().generate(request, context);
        assertTrue(plan.samples().stream().anyMatch(sample -> sample.replayTime() == 1L));
    }

    @Test
    void followUsesBehindOffsetAndSmoothsTargetJump() {
        ShotRequest request = TestFixtures.request(ShotType.FOLLOW, 0.0, 1.0, RotationDirection.CLOCKWISE, 0, 20);
        CameraPathPlan plan = new FollowShotGenerator().generate(request, TestFixtures.context(tick -> {
            double x = tick < 10 ? 0.0 : 10.0;
            return TestFixtures.pose(new Vec3d(x, 0, 0), new Vec3d(0, 0, 1), 0);
        }));
        assertEquals(-8.0, plan.samples().getFirst().position().z(), 1.0e-6);
        var firstAfterJump = plan.samples().stream().filter(sample -> sample.replayTime() >= 10).findFirst().orElseThrow();
        assertTrue(firstAfterJump.position().x() > 0.0 && firstAfterJump.position().x() < 10.0);
    }

    @Test
    void flybyTravelsLeftToRightWithoutReversing() {
        ShotRequest request = TestFixtures.request(ShotType.FLYBY);
        CameraPathPlan plan = new FlybyShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, new Vec3d(0, 0, 1), 0)));
        assertTrue(plan.samples().getFirst().position().x() < 0.0);
        assertTrue(plan.samples().getLast().position().x() > 0.0);
        for (int i = 1; i < plan.samples().size(); i++) {
            assertTrue(plan.samples().get(i).position().x() >= plan.samples().get(i - 1).position().x() - 1.0e-9);
        }
    }

    @Test
    void dollyInAndOutUseSafeEndpoints() {
        var context = TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, new Vec3d(0, 0, 1), 0));
        CameraPathPlan in = new DollyInShotGenerator().generate(TestFixtures.request(ShotType.DOLLY_IN), context);
        CameraPathPlan out = new DollyOutShotGenerator().generate(TestFixtures.request(ShotType.DOLLY_OUT), context);
        assertEquals(16.0, in.samples().getFirst().position().distanceTo(new Vec3d(0, 3, 0)), 1.0e-6);
        assertEquals(3.0, in.samples().getLast().position().distanceTo(new Vec3d(0, 3, 0)), 1.0e-6);
        assertEquals(3.0, out.samples().getFirst().position().distanceTo(new Vec3d(0, 3, 0)), 1.0e-6);
        assertEquals(16.0, out.samples().getLast().position().distanceTo(new Vec3d(0, 3, 0)), 1.0e-6);
    }

    @Test
    void dollyUsesWindowedTravelDirectionWhenInstantaneousVelocityAlternates() {
        ShotRequest request = TestFixtures.request(ShotType.DOLLY_IN, 0.0, 1.0,
                RotationDirection.CLOCKWISE, 0, 20);
        CameraPathPlan plan = new DollyInShotGenerator().generate(request, TestFixtures.context(tick -> {
            Vec3d position = new Vec3d(0.0, 0.0, tick * 0.1);
            Vec3d alternatingVelocity = tick % 2L == 0L
                    ? new Vec3d(0.0, 0.0, 2.0) : new Vec3d(0.0, 0.0, -2.0);
            return TestFixtures.pose(position, alternatingVelocity, 0.0);
        }));

        assertTrue(plan.valid());
        for (int index = 0; index < plan.samples().size(); index++) {
            var sample = plan.samples().get(index);
            double targetZ = sample.replayTime() * 0.1;
            assertTrue(sample.position().z() < targetZ,
                    "Dolly camera crossed to the opposite side of the moving target at tick " + sample.replayTime());
            if (index > 0) {
                double jump = sample.position().distanceTo(plan.samples().get(index - 1).position());
                assertTrue(jump < 4.0,
                        "Alternating instantaneous velocity caused a catastrophic camera jump of " + jump);
            }
        }
    }

    @Test
    void dollyLimitsCameraHeadingChangeDuringAGenuineDirectionReversal() {
        ShotRequest request = TestFixtures.request(ShotType.DOLLY_IN, 0.0, 1.0,
                RotationDirection.CLOCKWISE, 0, 20);
        CameraPathPlan plan = new DollyInShotGenerator().generate(request, TestFixtures.context(tick -> {
            double z = tick <= 10 ? tick * 0.3 : 3.0 - (tick - 10) * 0.3;
            Vec3d velocity = tick <= 10 ? new Vec3d(0.0, 0.0, 6.0) : new Vec3d(0.0, 0.0, -6.0);
            return TestFixtures.pose(new Vec3d(0.0, 0.0, z), velocity, 0.0);
        }));

        for (int index = 1; index < plan.samples().size(); index++) {
            var previous = plan.samples().get(index - 1);
            var current = plan.samples().get(index);
            double currentTargetZ = current.replayTime() <= 10
                    ? current.replayTime() * 0.3 : 3.0 - (current.replayTime() - 10) * 0.3;
            double previousTargetZ = previous.replayTime() <= 10
                    ? previous.replayTime() * 0.3 : 3.0 - (previous.replayTime() - 10) * 0.3;
            double previousDirection = Math.toDegrees(Math.atan2(
                    previous.position().x(), previous.position().z() - previousTargetZ));
            double currentDirection = Math.toDegrees(Math.atan2(
                    current.position().x(), current.position().z() - currentTargetZ));
            double deltaSeconds = current.cinematicTimeSeconds() - previous.cinematicTimeSeconds();
            double angleChange = Math.abs(CameraMath.unwrapDegrees(previousDirection, currentDirection)
                    - previousDirection);
            assertTrue(angleChange <= 90.0 * deltaSeconds + 1.0e-6,
                    "Dolly heading exceeded its cinematic turn-rate limit: " + angleChange);
        }
    }

    @Test
    void dollyUsesTargetFacingInsteadOfTeleportDeltaAtADiscontinuity() {
        ShotRequest request = TestFixtures.request(ShotType.DOLLY_IN, 0.0, 1.0,
                RotationDirection.CLOCKWISE, 0, 20);
        CameraPathPlan plan = new DollyInShotGenerator().generate(request, TestFixtures.context(tick -> {
            Vec3d position = tick < 10
                    ? new Vec3d(0.0, 0.0, tick * 0.3)
                    : new Vec3d(0.0, 0.0, 100.0 + (tick - 10) * 0.3);
            TargetPose base = TestFixtures.pose(position, new Vec3d(0.0, 0.0, 6.0), tick == 10 ? 90.0 : 0.0);
            if (tick != 10) return base;
            return new TargetPose(base.position(), base.focusPosition(), base.boundingBox(), base.yaw(), base.pitch(),
                    base.velocity(), base.entityType(), base.inVehicle(), base.dimension(), true);
        }));

        var discontinuity = plan.samples().stream()
                .filter(sample -> sample.replayTime() == 10L).findFirst().orElseThrow();
        assertTrue(discontinuity.discontinuity());
        assertTrue(discontinuity.position().x() > 2.0,
                "Dolly followed the teleport delta instead of resetting to the target facing");
    }

    @Test
    void dollyRejectsAPathInsideLargeTargetBounds() {
        TargetPose largeTarget = new TargetPose(Vec3d.ZERO, new Vec3d(0, 5, 0),
                new BoundingBox(new Vec3d(-20, 0, -20), new Vec3d(20, 10, 20)),
                0, 0, new Vec3d(0, 0, 1), "minecraft:warden", false,
                "minecraft:overworld", false);
        CameraPathPlan plan = new DollyInShotGenerator().generate(TestFixtures.request(ShotType.DOLLY_IN),
                TestFixtures.context(tick -> largeTarget));
        assertFalse(plan.valid());
        assertTrue(plan.warnings().stream().anyMatch(warning -> warning.code().equals("camera_inside_target")));
    }
}
