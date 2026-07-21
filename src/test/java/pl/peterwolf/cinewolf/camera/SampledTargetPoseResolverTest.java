package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SampledTargetPoseResolverTest {
    @Test
    void interpolatesPositionAndEstimatesVelocity() {
        TargetPose first = TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0);
        TargetPose second = TestFixtures.pose(new Vec3d(10, 0, 0), Vec3d.ZERO, 90);
        SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(Map.of(0L, first, 20L, second));
        TargetPose middle = resolver.resolve(TestFixtures.TARGET, 10L).orElseThrow();
        assertEquals(5.0, middle.position().x(), 1.0e-9);
        assertEquals(10.0, middle.velocity().x(), 1.0e-9);
        assertEquals(45.0, middle.yaw(), 1.0e-9);
    }

    @Test
    void marksLargeTeleportAsDiscontinuity() {
        SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(Map.of(
                0L, TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0),
                20L, TestFixtures.pose(new Vec3d(100, 0, 0), Vec3d.ZERO, 0)));
        assertTrue(resolver.resolve(TestFixtures.TARGET, 10L).orElseThrow().discontinuity());
    }

    @Test
    void marksExactSamplesAdjacentToTeleport() {
        SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(Map.of(
                0L, TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0),
                1L, TestFixtures.pose(new Vec3d(40, 0, 0), Vec3d.ZERO, 0)));
        assertTrue(resolver.resolve(TestFixtures.TARGET, 0L).orElseThrow().discontinuity());
        assertTrue(resolver.resolve(TestFixtures.TARGET, 1L).orElseThrow().discontinuity());
    }
}
