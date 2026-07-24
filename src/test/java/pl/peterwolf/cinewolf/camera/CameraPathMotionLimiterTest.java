package pl.peterwolf.cinewolf.camera;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CameraPathMotionLimiterTest {
    private final CameraPathMotionLimiter limiter = new CameraPathMotionLimiter();

    @Test
    void clampsOneSamplePositionTeleport() {
        CameraSample a = sample(0.0, new Vec3d(0, 2, 0), 0);
        CameraSample b = sample(0.1, new Vec3d(40, 2, 0), 0); // 400 blocks/s
        List<CameraSample> limited = limiter.limit(List.of(a, b), 20.0, 30.0, 160.0, 110.0);
        assertEquals(2, limited.size());
        assertEquals(a.position(), limited.getFirst().position());
        assertTrue(limited.getLast().position().distanceTo(a.position()) <= 20.0 * 0.1 + 1.0e-6);
        assertTrue(limited.getLast().position().x() < 40.0);
    }

    @Test
    void rateLimitsYawAcrossClosePass() {
        CameraSample a = sample(0.0, new Vec3d(0, 2, -4), 0, new Vec3d(0, 2, 0));
        // Subject jumps behind camera; unlimited look-at would flip ~180 degrees.
        CameraSample b = sample(0.05, new Vec3d(0, 2, -4), 0, new Vec3d(0, 2, -8));
        List<CameraSample> limited = limiter.limit(List.of(a, b), 30.0, 40.0, 90.0, 60.0);
        double yawDelta = CameraMath.angleDifferenceDegrees(limited.getFirst().yaw(), limited.getLast().yaw());
        assertTrue(yawDelta <= 90.0 * 0.05 + 1.0e-3, "yawDelta=" + yawDelta);
    }

    @Test
    void doesNotCrossDiscontinuity() {
        CameraSample a = sample(0.0, new Vec3d(0, 0, 0), 0);
        CameraSample cut = new CameraSample(0.1, 2L, new Vec3d(100, 0, 0), new Quaternionf(), 90, 0, 0, 70,
                new Vec3d(100, 0, 1), true, false);
        List<CameraSample> limited = limiter.limit(List.of(a, cut), 5.0, 5.0, 30.0, 30.0);
        assertEquals(100.0, limited.getLast().position().x(), 1.0e-9);
    }

    private static CameraSample sample(double time, Vec3d position, double yaw) {
        return sample(time, position, yaw, position.add(new Vec3d(0, 0, 1)));
    }

    private static CameraSample sample(double time, Vec3d position, double yaw, Vec3d lookAt) {
        return new CameraSample(time, Math.round(time * 20), position, new Quaternionf(), yaw, 0, 0, 70,
                lookAt, false);
    }
}
