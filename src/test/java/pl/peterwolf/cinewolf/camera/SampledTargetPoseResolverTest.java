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

    @Test
    void holdsSingleSampleOutsideExactTickInsteadOfMissingTarget() {
        TargetPose only = TestFixtures.pose(new Vec3d(3, 64, -2), Vec3d.ZERO, 90);
        SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(Map.of(100L, only));

        TargetPose held = resolver.resolve(TestFixtures.TARGET, 112L).orElseThrow();
        assertEquals(3.0, held.position().x(), 1.0e-9);
        assertEquals(64.0, held.position().y(), 1.0e-9);
    }

    @Test
    void holdsEdgePoseBeforeFirstAndAfterLastSample() {
        SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(Map.of(
                20L, TestFixtures.pose(new Vec3d(0, 1, 0), Vec3d.ZERO, 0),
                40L, TestFixtures.pose(new Vec3d(10, 1, 0), Vec3d.ZERO, 0)));

        assertEquals(0.0, resolver.resolve(TestFixtures.TARGET, 5L).orElseThrow().position().x(), 1.0e-9);
        assertEquals(10.0, resolver.resolve(TestFixtures.TARGET, 55L).orElseThrow().position().x(), 1.0e-9);
    }

    @Test
    void emptyPoseMapStillReturnsEmpty() {
        SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(Map.of());
        assertTrue(resolver.resolve(TestFixtures.TARGET, 0L).isEmpty());
    }
}
