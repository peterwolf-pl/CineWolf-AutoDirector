package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TargetPoseSanitizerTest {
    @Test
    void removesIsolatedOutAndBackSpike() {
        Map<Long, TargetPose> poses = new LinkedHashMap<>();
        poses.put(0L, TestFixtures.pose(new Vec3d(0, 0, 0), new Vec3d(1, 0, 0), 0));
        poses.put(5L, TestFixtures.pose(new Vec3d(20, 0, 0), new Vec3d(1, 0, 0), 0)); // spike
        poses.put(10L, TestFixtures.pose(new Vec3d(2, 0, 0), new Vec3d(1, 0, 0), 0));

        Map<Long, TargetPose> sanitized = TargetPoseSanitizer.sanitize(poses);
        assertEquals(1.0, sanitized.get(5L).position().x(), 1.0e-6);
    }

    @Test
    void keepsSustainedHighSpeedMotion() {
        Map<Long, TargetPose> poses = new LinkedHashMap<>();
        for (long tick = 0; tick <= 40; tick += 5) {
            double x = tick * 0.5; // 10 blocks/s
            poses.put(tick, TestFixtures.pose(new Vec3d(x, 0, 0), new Vec3d(10, 0, 0), 0));
        }
        Map<Long, TargetPose> sanitized = TargetPoseSanitizer.sanitize(poses);
        for (long tick = 0; tick <= 40; tick += 5) {
            assertEquals(tick * 0.5, sanitized.get(tick).position().x(), 1.0e-6);
        }
    }

    @Test
    void resolverClampsVelocityFromSpikyNeighbors() {
        Map<Long, TargetPose> poses = Map.of(
                0L, TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0),
                1L, TestFixtures.pose(new Vec3d(100, 0, 0), Vec3d.ZERO, 0),
                2L, TestFixtures.pose(new Vec3d(1, 0, 0), Vec3d.ZERO, 0));
        SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(poses);
        TargetPose middle = resolver.resolve(TestFixtures.TARGET, 1L).orElseThrow();
        // Spike at tick 1 should be rewritten toward the chord (~0.5).
        assertTrue(middle.position().x() < 10.0, "spike was not sanitized: " + middle.position().x());
        assertTrue(middle.velocity().length() <= 64.0 + 1.0e-6);
    }
}
