package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathSmoothingSettings;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraPathSmootherTest {
    private static final double EPSILON = 1.0e-8;
    private final CameraPathSmoother smoother = new CameraPathSmoother();

    @Test
    void disabledSmoothingReturnsEqualSamplesWithOriginalElementIdentity() {
        List<CameraSample> samples = new ArrayList<>(List.of(
                sample(0.0, 0L, new Vec3d(0.0, 2.0, 0.0), new Vec3d(0.0, 2.0, 8.0)),
                sample(0.1, 2L, new Vec3d(1.0, 2.5, 0.0), new Vec3d(1.0, 2.0, 8.0)),
                sample(0.2, 4L, new Vec3d(2.0, 2.0, 0.0), new Vec3d(2.0, 2.0, 8.0))));

        List<CameraSample> result = smoother.smooth(samples,
                settings(false, 1.0, 1.0, 1.0, true, 0.25, 1.0));

        assertEquals(samples, result);
        for (int index = 0; index < samples.size(); index++) {
            assertSame(samples.get(index), result.get(index));
        }
    }

    @Test
    void straightConstantVelocityPathIsUnchangedAtIrregularSampleTimes() {
        double[] times = {0.0, 0.07, 0.26, 0.61, 1.0};
        List<CameraSample> samples = new ArrayList<>();
        for (double time : times) {
            Vec3d lookAt = new Vec3d(4.0 * time, 1.0 + 2.0 * time, 8.0 - time);
            samples.add(sample(time, Math.round(time * 20.0),
                    lookAt.add(new Vec3d(0.0, 2.0, -6.0)), lookAt));
        }

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 0.85, 0.85, 0.75, false, 2.0, 24.0));

        for (int index = 0; index < samples.size(); index++) {
            assertVecEquals(samples.get(index).position(), result.get(index).position());
            assertVecEquals(samples.get(index).lookAtPoint(), result.get(index).lookAtPoint());
        }
    }

    @Test
    void alternatingPositionJitterIsStronglyAttenuated() {
        List<CameraSample> samples = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            double time = index * 0.1;
            double jitter = index % 2 == 0 ? 1.0 : -1.0;
            Vec3d lookAt = new Vec3d(time * 2.0, 2.0, 8.0);
            samples.add(sample(time, index * 2L,
                    new Vec3d(lookAt.x(), 2.0 + jitter, 0.0), lookAt));
        }

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 1.0, 0.0, 0.31, false, 2.0, 24.0));

        double before = interiorAverageVerticalError(samples, 2.0);
        double after = interiorAverageVerticalError(result, 2.0);
        assertTrue(after < before * 0.45,
                () -> "Alternating camera jitter was not attenuated enough: " + before + " -> " + after);
    }

    @Test
    void isolatedOutAndBackPositionSpikeIsReplaced() {
        List<CameraSample> samples = linearSamples(5, 0.1, 1.0);
        CameraSample original = samples.get(2);
        Vec3d spike = original.position().add(new Vec3d(0.0, 5.0, 4.0));
        samples.set(2, copyWith(original, spike, original.lookAtPoint(), false, false));

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 0.0, 0.0, 0.3, true, 1.0, 10.0));

        Vec3d expected = samples.get(1).position().lerp(samples.get(3).position(), 0.5);
        assertVecEquals(expected, result.get(2).position());
        assertVecEquals(samples.get(2).lookAtPoint(), result.get(2).lookAtPoint());
    }

    @Test
    void isolatedOutAndBackLookAtSpikeIsReplaced() {
        List<CameraSample> samples = linearSamples(5, 0.1, 1.0);
        CameraSample original = samples.get(2);
        Vec3d spike = original.lookAtPoint().add(new Vec3d(0.0, 6.0, -4.0));
        samples.set(2, copyWith(original, original.position(), spike, false, false));

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 0.0, 0.0, 0.3, true, 1.0, 10.0));

        Vec3d expected = samples.get(1).lookAtPoint().lerp(samples.get(3).lookAtPoint(), 0.5);
        assertVecEquals(expected, result.get(2).lookAtPoint());
        assertVecEquals(samples.get(2).position(), result.get(2).position());
    }

    @Test
    void sustainedFastMovementIsNotRejectedAsAnOutlier() {
        List<CameraSample> samples = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            double time = index * 0.1;
            Vec3d position = new Vec3d(index * 5.0, 2.0, 0.0);
            samples.add(sample(time, index * 2L, position, position.add(new Vec3d(0.0, 0.0, 8.0))));
        }

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 0.0, 0.0, 0.4, true, 1.0, 20.0));

        for (int index = 0; index < samples.size(); index++) {
            assertVecEquals(samples.get(index).position(), result.get(index).position());
            assertVecEquals(samples.get(index).lookAtPoint(), result.get(index).lookAtPoint());
        }
    }

    @Test
    void genuineHighSpeedCornerIsNotRejectedAsAnOutlier() {
        List<Vec3d> positions = List.of(
                new Vec3d(0.0, 2.0, 0.0),
                new Vec3d(2.0, 2.0, 0.0),
                new Vec3d(4.0, 2.0, 0.0),
                new Vec3d(4.0, 2.0, 2.0),
                new Vec3d(4.0, 2.0, 4.0));
        List<CameraSample> samples = new ArrayList<>();
        for (int index = 0; index < positions.size(); index++) {
            Vec3d position = positions.get(index);
            samples.add(sample(index * 0.1, index * 2L, position,
                    position.add(new Vec3d(0.0, 0.0, 8.0))));
        }

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 0.0, 0.0, 0.4, true, 1.0, 10.0));

        assertVecEquals(positions.get(2), result.get(2).position());
        assertVecEquals(positions.get(3), result.get(3).position());
    }

    @Test
    void discontinuitySeparatesSmoothingSegments() {
        List<CameraSample> samples = List.of(
                sample(0.0, 0L, new Vec3d(0.0, 2.0, 0.0), new Vec3d(0.0, 2.0, 8.0)),
                sample(0.1, 2L, new Vec3d(1.0, 2.0, 0.0), new Vec3d(1.0, 2.0, 8.0)),
                sample(0.2, 4L, new Vec3d(2.0, 2.0, 0.0), new Vec3d(2.0, 2.0, 8.0)),
                sample(0.3, 6L, new Vec3d(100.0, 2.0, 0.0), new Vec3d(100.0, 2.0, 8.0),
                        70.0, true, false),
                sample(0.4, 8L, new Vec3d(101.0, 2.0, 0.0), new Vec3d(101.0, 2.0, 8.0)),
                sample(0.5, 10L, new Vec3d(102.0, 2.0, 0.0), new Vec3d(102.0, 2.0, 8.0)));

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 1.0, 1.0, 2.0, true, 0.25, 1.0));

        assertSame(samples.get(2), result.get(2));
        assertSame(samples.get(3), result.get(3));
        assertTrue(result.subList(0, 3).stream().allMatch(value -> value.position().x() < 10.0));
        assertTrue(result.subList(3, 6).stream().allMatch(value -> value.position().x() > 90.0));
    }

    @Test
    void collisionConstrainedSampleIsAnUntouchedAnchor() {
        List<CameraSample> samples = List.of(
                sample(0.0, 0L, new Vec3d(0.0, 2.0, 0.0), new Vec3d(0.0, 2.0, 8.0)),
                sample(0.1, 2L, new Vec3d(1.0, 2.0, 0.0), new Vec3d(1.0, 2.0, 8.0)),
                sample(0.2, 4L, new Vec3d(40.0, 7.0, 0.0), new Vec3d(40.0, 2.0, 8.0),
                        73.0, false, true),
                sample(0.3, 6L, new Vec3d(3.0, 2.0, 0.0), new Vec3d(3.0, 2.0, 8.0)),
                sample(0.4, 8L, new Vec3d(4.0, 2.0, 0.0), new Vec3d(4.0, 2.0, 8.0)));

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 1.0, 1.0, 2.0, true, 0.25, 1.0));

        assertSame(samples.get(2), result.get(2));
        assertEquals(73.0, result.get(2).fov(), 0.0);
        assertTrue(result.get(2).collisionConstrained());
        assertVecEquals(samples.get(1).position(), result.get(1).position());
        assertVecEquals(samples.get(3).position(), result.get(3).position());
    }

    @Test
    void smoothingPreservesEndpointsTimelineFovAndFlagsAndProducesFiniteOrientation() {
        List<CameraSample> samples = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            double time = index * 0.125;
            double jitter = index % 2 == 0 ? 0.35 : -0.35;
            samples.add(sample(time, 100L + index * 3L,
                    new Vec3d(index * 0.4, 3.0 + jitter, -5.0),
                    new Vec3d(index * 0.4, 2.0, 1.0), 61.0 + index, false, false));
        }

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 0.8, 0.7, 0.4, false, 2.0, 24.0));

        assertSame(samples.getFirst(), result.getFirst());
        assertSame(samples.getLast(), result.getLast());
        for (int index = 0; index < samples.size(); index++) {
            CameraSample before = samples.get(index);
            CameraSample after = result.get(index);
            assertEquals(before.cinematicTimeSeconds(), after.cinematicTimeSeconds(), 0.0);
            assertEquals(before.replayTime(), after.replayTime());
            assertEquals(before.fov(), after.fov(), 0.0);
            assertEquals(before.discontinuity(), after.discontinuity());
            assertEquals(before.collisionConstrained(), after.collisionConstrained());
            assertTrue(after.isFinite(), "Non-finite camera sample at index " + index);
            assertTrue(Float.isFinite(after.rotation().x));
            assertTrue(Float.isFinite(after.rotation().y));
            assertTrue(Float.isFinite(after.rotation().z));
            assertTrue(Float.isFinite(after.rotation().w));
            double quaternionLength = Math.sqrt(after.rotation().x * after.rotation().x
                    + after.rotation().y * after.rotation().y
                    + after.rotation().z * after.rotation().z
                    + after.rotation().w * after.rotation().w);
            assertEquals(1.0, quaternionLength, 1.0e-5,
                    "Camera quaternion was not normalized at index " + index);
        }
    }

    @Test
    void smoothingPreservesOrbitRadiusWhileFilteringDirection() {
        double radius = 10.0;
        List<CameraSample> samples = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            double time = index * 0.1;
            Vec3d focus = new Vec3d(index * 0.3, 2.0, 0.0);
            double angle = index * 0.2;
            Vec3d offset = new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            samples.add(sample(time, index * 2L, focus.add(offset), focus));
        }

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 1.0, 1.0, 0.35, false, 2.0, 24.0));

        for (int index = 0; index < result.size(); index++) {
            assertEquals(radius, result.get(index).position().distanceTo(result.get(index).lookAtPoint()), 1.0e-6,
                    "Orbit radius changed at sample " + index);
        }
    }

    @Test
    void wideWindowDoesNotAmplifyNearCancellationOnFastIrregularOrbit() {
        double centerTime = 2.0;
        double radius = 10.0;
        TreeSet<Double> relativeTimes = new TreeSet<>();
        for (int index = -11; index <= 11; index++) relativeTimes.add(index / 12.0);
        relativeTimes.addAll(List.of(-17.0 / 24.0, -13.0 / 24.0, -11.0 / 24.0, -5.0 / 24.0,
                7.0 / 24.0, 11.0 / 24.0, 13.0 / 24.0, 19.0 / 24.0));

        Vec3d focus = new Vec3d(0.0, 2.0, 0.0);
        List<CameraSample> samples = new ArrayList<>();
        int replayTick = 0;
        for (double relativeTime : relativeTimes) {
            double angle = relativeTime * Math.PI * 2.0;
            Vec3d offset = new Vec3d(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            samples.add(sample(centerTime + relativeTime, replayTick++, focus.add(offset), focus));
        }

        List<CameraSample> result = smoother.smooth(samples,
                settings(true, 1.0, 1.0, 1.0, false, 2.0, 24.0));
        int centerIndex = new ArrayList<>(relativeTimes).indexOf(0.0);
        Vec3d originalDirection = samples.get(centerIndex).position().subtract(focus).normalizeOr(Vec3d.ZERO);
        Vec3d smoothedDirection = result.get(centerIndex).position().subtract(focus).normalizeOr(Vec3d.ZERO);

        assertTrue(originalDirection.dot(smoothedDirection) > 0.999,
                "A near-zero direction resultant must not flip the camera to the opposite side of the orbit");
        assertEquals(radius, result.get(centerIndex).position().distanceTo(focus), EPSILON);
    }

    private static List<CameraSample> linearSamples(int count, double timeStep, double speed) {
        List<CameraSample> samples = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double time = index * timeStep;
            Vec3d position = new Vec3d(time * speed, 2.0, 0.0);
            samples.add(sample(time, Math.round(time * 20.0), position,
                    position.add(new Vec3d(0.0, 0.0, 8.0))));
        }
        return samples;
    }

    private static double interiorAverageVerticalError(List<CameraSample> samples, double baseline) {
        double total = 0.0;
        for (int index = 1; index < samples.size() - 1; index++) {
            total += Math.abs(samples.get(index).position().y() - baseline);
        }
        return total / Math.max(1, samples.size() - 2);
    }

    private static PathSmoothingSettings settings(boolean enabled, double positionStrength,
                                                   double rotationStrength, double windowSeconds,
                                                   boolean rejectOutliers, double outlierDistance,
                                                   double outlierSpeed) {
        return new PathSmoothingSettings(enabled, positionStrength, rotationStrength, windowSeconds,
                rejectOutliers, outlierDistance, outlierSpeed);
    }

    private static CameraSample sample(double time, long replayTick, Vec3d position, Vec3d lookAt) {
        return sample(time, replayTick, position, lookAt, 70.0, false, false);
    }

    private static CameraSample sample(double time, long replayTick, Vec3d position, Vec3d lookAt,
                                       double fov, boolean discontinuity, boolean collisionConstrained) {
        CameraLookAtSolver.Orientation orientation = new CameraLookAtSolver().solve(position, lookAt, Double.NaN);
        return new CameraSample(time, replayTick, position, orientation.quaternion(), orientation.yaw(),
                orientation.pitch(), orientation.roll(), fov, lookAt,
                discontinuity || orientation.degenerate(), collisionConstrained);
    }

    private static CameraSample copyWith(CameraSample original, Vec3d position, Vec3d lookAt,
                                         boolean discontinuity, boolean collisionConstrained) {
        return sample(original.cinematicTimeSeconds(), original.replayTime(), position, lookAt, original.fov(),
                discontinuity, collisionConstrained);
    }

    private static void assertVecEquals(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }
}
