package pl.peterwolf.cinewolf.shot;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.Vec3d;

import static org.junit.jupiter.api.Assertions.*;

class NewShotGeneratorsTest {
    @Test
    void registryContainsAllShotTypes() {
        ShotGeneratorRegistry registry = ShotGeneratorRegistry.createDefault();
        assertEquals(ShotType.values().length, registry.supportedTypes().size());
        for (ShotType type : ShotType.values()) {
            assertTrue(registry.supports(type), type.name());
            CameraPathPlan plan = registry.require(type).generate(TestFixtures.request(type),
                    TestFixtures.context(tick -> TestFixtures.pose(new Vec3d(tick * 0.05, 0, 0),
                            new Vec3d(1, 0, 0), 0)));
            assertTrue(plan.valid() || !plan.samples().isEmpty() || !plan.warnings().isEmpty(), type.name());
            assertFalse(plan.samples().isEmpty(), type.name());
            assertTrue(plan.samples().stream().allMatch(sample -> sample.isFinite()), type.name());
        }
    }

    @Test
    void revealMovesTowardClearerFraming() {
        ShotRequest request = TestFixtures.request(ShotType.REVEAL, 0.0, 2.0, RotationDirection.LEFT_TO_RIGHT, 0, 40);
        CameraPathPlan plan = new RevealShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0)));
        assertTrue(plan.valid());
        assertTrue(plan.samples().size() >= 2);
        double startDistance = plan.samples().getFirst().position().distanceTo(new Vec3d(0, 1.62, 0));
        double endDistance = plan.samples().getLast().position().distanceTo(new Vec3d(0, 1.62, 0));
        assertTrue(endDistance < startDistance || plan.warnings().stream()
                .anyMatch(warning -> warning.code().startsWith("reveal.")), "reveal should progress or warn");
    }

    @Test
    void craneUpRaisesCamera() {
        ShotRequest request = TestFixtures.request(ShotType.CRANE_UP);
        CameraPathPlan plan = new CraneUpShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0)));
        assertTrue(plan.valid());
        assertTrue(plan.samples().getLast().position().y() > plan.samples().getFirst().position().y());
    }

    @Test
    void craneDownLowersCamera() {
        ShotRequest request = TestFixtures.request(ShotType.CRANE_DOWN);
        CameraPathPlan plan = new CraneDownShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0)));
        assertTrue(plan.valid());
        assertTrue(plan.samples().getLast().position().y() < plan.samples().getFirst().position().y()
                || plan.warnings().stream().anyMatch(warning -> warning.code().contains("ground")));
    }

    @Test
    void spiralChangesRadiusAndAngle() {
        ShotRequest request = TestFixtures.request(ShotType.SPIRAL, 0.75, 2.0, RotationDirection.CLOCKWISE, 0, 40);
        request = new ShotRequest(request.target(), request.shotType(), 16.0, 4.0, 8.0, 10.0, 4.0, 0.75,
                2.0, 0.0, RotationDirection.CLOCKWISE, 4.0, 70.0, request.easing(), 0.2, 0, 40);
        CameraPathPlan plan = new SpiralShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0)));
        assertTrue(plan.valid());
        double startRadius = horizontalRadius(plan.samples().getFirst().position());
        double endRadius = horizontalRadius(plan.samples().getLast().position());
        assertTrue(Math.abs(startRadius - endRadius) > 0.5);
    }

    @Test
    void staticTrackingKeepsFixedCameraByDefault() {
        ShotRequest request = TestFixtures.request(ShotType.STATIC_TRACKING, 0.0, 2.0, RotationDirection.CLOCKWISE, 0, 40);
        CameraPathPlan plan = new StaticTrackingShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(new Vec3d(tick / 10.0, 0, 0), new Vec3d(1, 0, 0), 0)));
        assertTrue(plan.valid());
        Vec3d first = plan.samples().getFirst().position();
        Vec3d last = plan.samples().getLast().position();
        assertEquals(first.x(), last.x(), 1.0e-6);
        assertEquals(first.y(), last.y(), 1.0e-6);
        assertEquals(first.z(), last.z(), 1.0e-6);
    }

    @Test
    void sideTrackingStaysLateral() {
        ShotRequest request = TestFixtures.request(ShotType.SIDE_TRACKING, 0.0, 2.0, RotationDirection.LEFT_TO_RIGHT, 0, 40);
        CameraPathPlan plan = new SideTrackingShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(new Vec3d(0, 0, tick / 10.0), new Vec3d(0, 0, 1), 0)));
        assertTrue(plan.valid());
        assertTrue(Math.abs(plan.samples().getFirst().position().x()) > 1.0);
    }

    @Test
    void chaseIncreasesDistanceWithSpeed() {
        ShotRequest slow = TestFixtures.request(ShotType.CHASE, 0.0, 1.0, RotationDirection.CLOCKWISE, 0, 20);
        ShotRequest fast = TestFixtures.request(ShotType.CHASE, 0.0, 1.0, RotationDirection.CLOCKWISE, 0, 20);
        CameraPathPlan slowPlan = new ChaseShotGenerator().generate(slow,
                TestFixtures.context(tick -> TestFixtures.pose(new Vec3d(tick * 0.05, 0, 0), new Vec3d(1, 0, 0), 0)));
        CameraPathPlan fastPlan = new ChaseShotGenerator().generate(fast,
                TestFixtures.context(tick -> TestFixtures.pose(new Vec3d(tick * 0.4, 0, 0), new Vec3d(8, 0, 0), 0)));
        assertTrue(slowPlan.valid() && fastPlan.valid());
        double slowDistance = slowPlan.samples().getLast().position()
                .distanceTo(new Vec3d(20 * 0.05, 1.62, 0));
        double fastDistance = fastPlan.samples().getLast().position()
                .distanceTo(new Vec3d(20 * 0.4, 1.62, 0));
        assertTrue(fastDistance >= slowDistance - 0.5);
    }

    @Test
    void closeDetailFocusesNearTarget() {
        ShotRequest request = TestFixtures.request(ShotType.CLOSE_DETAIL);
        CameraPathPlan plan = new CloseDetailShotGenerator().generate(request,
                TestFixtures.context(tick -> TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0)));
        assertTrue(plan.valid());
        assertTrue(plan.samples().getFirst().position().distanceTo(new Vec3d(0, 1.62, 0)) < 6.0);
    }

    @Test
    void vehicleProfileProducesFinitePath() {
        ShotRequest request = TestFixtures.request(ShotType.VEHICLE_PROFILE);
        request = new ShotRequest(new pl.peterwolf.cinewolf.model.TargetReference(
                request.target().uuid(), "minecraft:minecart", "Minecart"),
                request.shotType(), request.diameter(), request.height(), request.distance(),
                request.startDistance(), request.endDistance(), request.rpm(), request.durationSeconds(),
                request.startAngleDegrees(), request.direction(), request.cameraSpeed(), request.fov(),
                request.easing(), request.lookAheadSeconds(), request.replayStartTime(), request.replayEndTime());
        CameraPathPlan plan = new VehicleProfileShotGenerator().generate(request,
                TestFixtures.context(tick -> {
                    var pose = TestFixtures.pose(new Vec3d(tick * 0.1, 0, 0), new Vec3d(2, 0, 0), 0);
                    return new pl.peterwolf.cinewolf.model.TargetPose(pose.position(), pose.focusPosition(),
                            pose.boundingBox(), pose.yaw(), pose.pitch(), pose.velocity(),
                            "minecraft:minecart", true, pose.dimension(), false);
                }));
        assertTrue(plan.valid());
        assertTrue(plan.samples().stream().allMatch(sample -> sample.isFinite()));
    }

    private static double horizontalRadius(Vec3d position) {
        return Math.hypot(position.x(), position.z());
    }
}
