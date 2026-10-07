package pl.peterwolf.cinewolf.camera;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CameraPathSimplifierTest {
    @Test
    void fixedIntervalDoesNotKeyEveryPlayerStep() {
        List<CameraSample> samples = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            double time = i / 10.0;
            double step = (i % 2 == 0) ? 0.0 : 0.35;
            samples.add(sample(time, new Vec3d(i * 0.4, step, 0.0), i * 8.0));
        }
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(10, 200, 200, 0.05, 0.2, 0.05, 8.0, 1.0));
        assertTrue(simplified.size() <= 6, "1 s spacing on a 4 s stepped path, was " + simplified.size());
        for (int i = 1; i < simplified.size(); i++) {
            double gap = simplified.get(i).cinematicTimeSeconds() - simplified.get(i - 1).cinematicTimeSeconds();
            assertTrue(gap >= 0.9 || simplified.get(i).discontinuity(), "gap " + gap);
        }
    }

    @Test
    void fixedIntervalStillKeepsATeleport() {
        CameraSample first = sample(0.0, new Vec3d(0, 0, 0), 0);
        CameraSample beforeCut = sample(0.2, new Vec3d(1, 0, 0), 0);
        CameraSample afterCut = new CameraSample(0.25, 5L, new Vec3d(40, 0, 0), new Quaternionf(),
                90, 0, 0, 70, new Vec3d(0, 1, 0), true);
        CameraSample last = sample(2.0, new Vec3d(42, 0, 0), 90);
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(
                List.of(first, beforeCut, afterCut, last),
                new SamplingSettings(10, 100, 100, 0.4, 2.0, 1.0, 8.0, 1.0));
        assertTrue(simplified.contains(beforeCut));
        assertTrue(simplified.contains(afterCut));
    }

    @Test
    void shortMovePreservesEndpointsWhenSmoothPlaybackStaysInTolerance() {
        List<CameraSample> samples = new ArrayList<>();
        for (int i = 0; i <= 10; i++) samples.add(sample(i / 10.0, new Vec3d(i * 0.08, 0, 0), 0.0));
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(10, 100, 100, 0.5, 2.0, 1.0, 10.0));
        assertEquals(2, simplified.size());
        assertSame(samples.getFirst(), simplified.getFirst());
        assertSame(samples.getLast(), simplified.getLast());
    }

    @Test
    void maximumIntervalAddsIntermediateKeyframes() {
        SamplingSettings settings = new SamplingSettings(10, 100, 100, 1.0, 5.0, 2.0, 0.25);
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(lineSamples(), settings);
        assertTrue(simplified.size() >= 5);
        for (int i = 1; i < simplified.size(); i++) {
            assertTrue(simplified.get(i).cinematicTimeSeconds() - simplified.get(i - 1).cinematicTimeSeconds() <= 0.31);
        }
    }

    @Test
    void spatialTurnIsPreserved() {
        List<CameraSample> samples = new ArrayList<>(lineSamples());
        CameraSample original = samples.get(5);
        samples.set(5, sample(0.5, new Vec3d(5, 2, 0), original.yaw()));
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(10, 100, 100, 0.1, 1.0, 0.1, 10.0));
        assertTrue(simplified.stream().anyMatch(sample -> sample.position().y() == 2.0));
    }

    @Test
    void keepsTimeLinearSampleWhenSmoothPlaybackWouldEasePastIt() {
        List<CameraSample> samples = List.of(
                sample(0.0, new Vec3d(0, 0, 0), 0, 0, 60),
                sample(0.2, new Vec3d(2, 0, 0), 2, 2, 64),
                sample(1.0, new Vec3d(10, 0, 0), 10, 10, 80));

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 100, 100, 0.01, 0.01, 0.01, 2.0));

        assertEquals(3, simplified.size(), "two-point smooth playback is smoothstep, not the linear sample");
    }

    @Test
    void dropsTimeLinearSampleWhenTheEaseStaysInsideTolerance() {
        List<CameraSample> samples = List.of(
                sample(0.0, new Vec3d(0, 0, 0), 0, 0, 60),
                sample(0.2, new Vec3d(0.2, 0, 0), 0.2, 0.2, 60.4),
                sample(1.0, new Vec3d(1, 0, 0), 1, 1, 62));

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 100, 100, 0.5, 2.0, 1.5, 2.0));

        assertEquals(2, simplified.size());
    }

    @Test
    void preservesNonLinearFovAtIrregularSampleTime() {
        List<CameraSample> samples = List.of(
                sample(0.0, new Vec3d(0, 0, 0), 0, 0, 60),
                sample(0.2, new Vec3d(2, 0, 0), 2, 2, 72),
                sample(1.0, new Vec3d(10, 0, 0), 10, 10, 80));

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 100, 100, 0.5, 2.0, 0.5, 2.0));

        assertEquals(3, simplified.size());
    }

    @Test
    void doesNotKeepCollinearCollisionSamples() {
        CameraSample first = sample(0.0, new Vec3d(0.0, 0.0, 0.0), 0.0);
        CameraSample constrained = new CameraSample(0.5, 10L, new Vec3d(0.5, 0.0, 0.0),
                new Quaternionf(), 0.0, 0.0, 0.0, 70.0, new Vec3d(0.5, 0.0, 8.0), false, true);
        CameraSample last = sample(1.0, new Vec3d(1.0, 0.0, 0.0), 0.0);

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(List.of(first, constrained, last),
                new SamplingSettings(12, 4096, 512, 0.4, 2.0, 1.0, 8.0));

        assertEquals(List.of(first, last), simplified);
    }

    @Test
    void preservesCollisionBendWithoutTreatingItAsACut() {
        CameraSample first = sample(0.0, new Vec3d(0.0, 0.0, 0.0), 0.0);
        CameraSample constrained = new CameraSample(0.5, 10L, new Vec3d(0.5, 0.4, 0.0),
                new Quaternionf(), 0.0, 0.0, 0.0, 70.0, new Vec3d(0.5, 0.0, 8.0), false, true);
        CameraSample last = sample(1.0, new Vec3d(1.0, 0.0, 0.0), 0.0);

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(List.of(first, constrained, last),
                new SamplingSettings(12, 4096, 512, 1.0, 180.0, 10.0, 8.0));

        assertEquals(List.of(first, constrained, last), simplified);
        assertFalse(constrained.discontinuity());
    }

    @Test
    void lookAtBendDoesNotAddAKeyframeWhenWrittenAimIsUnchanged() {
        List<CameraSample> samples = List.of(
                sample(0.0, new Vec3d(0, 2, -6), 0, 0, 70, new Vec3d(0, 1, 0)),
                sample(0.5, new Vec3d(1, 2, -6), 0, 0, 70, new Vec3d(1, 4, 0)),
                sample(1.0, new Vec3d(2, 2, -6), 0, 0, 70, new Vec3d(2, 1, 0)));
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 100, 100, 0.5, 2.0, 1.0, 8.0));
        assertEquals(2, simplified.size(), "Flashback plays yaw/pitch, not the unused look-at point");
    }

    @Test
    void sustainedTurnDoesNotSampleEveryFrame() {
        List<CameraSample> samples = new ArrayList<>();
        for (int i = 0; i <= 24; i++) {
            double time = i / 12.0;
            samples.add(sample(time, new Vec3d(time * 4.0, 2.0, -8.0), time * 45.0));
        }
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 100, 100, 0.4, 2.0, 1.0, 8.0));
        assertTrue(simplified.size() < samples.size() / 2,
                "45°/s tracking must stay a curve, not a keyframe per sample: " + simplified.size());
        assertPlaybackWithinTolerance(samples, simplified, 0.4, 2.0, 1.0);
    }

    @Test
    void orbitKeepsControlPointsInsteadOfSampleRate() {
        List<CameraSample> samples = new ArrayList<>();
        double radius = 8.0;
        for (int i = 0; i <= 48; i++) {
            double time = i / 12.0;
            double angle = time * Math.PI / 2.0;
            samples.add(sample(time,
                    new Vec3d(Math.cos(angle) * radius, 3.0, Math.sin(angle) * radius),
                    Math.toDegrees(angle)));
        }
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 200, 200, 0.4, 2.0, 1.0, 8.0));
        assertTrue(simplified.size() <= 12, "quarter orbit should be a handful of control points, was "
                + simplified.size());
        assertPlaybackWithinTolerance(samples, simplified, 0.4, 2.0, 1.0);
    }

    private static void assertPlaybackWithinTolerance(List<CameraSample> dense, List<CameraSample> keys,
                                                      double positionTolerance, double rotationTolerance,
                                                      double fovTolerance) {
        assertTrue(keys.size() >= 2);
        int key = 0;
        for (CameraSample sample : dense) {
            while (key + 1 < keys.size()
                    && keys.get(key + 1).cinematicTimeSeconds() < sample.cinematicTimeSeconds() - 1.0e-9) {
                key++;
            }
            if (sample.cinematicTimeSeconds() <= keys.get(key).cinematicTimeSeconds() + 1.0e-9
                    || key + 1 >= keys.size()) {
                continue;
            }
            CameraSample start = keys.get(key);
            CameraSample end = keys.get(key + 1);
            CameraSample before = key == 0 ? start : keys.get(key - 1);
            CameraSample after = key + 2 >= keys.size() ? end : keys.get(key + 2);
            Vec3d played = SmoothKeyframeCurve.position(before.position(), start.position(), end.position(),
                    after.position(), before.cinematicTimeSeconds(), start.cinematicTimeSeconds(),
                    end.cinematicTimeSeconds(), after.cinematicTimeSeconds(), sample.cinematicTimeSeconds());
            assertTrue(played.distanceTo(sample.position()) <= positionTolerance + 0.02,
                    "position drift " + played.distanceTo(sample.position()));
            double yaw = SmoothKeyframeCurve.degrees(before.yaw(), start.yaw(), end.yaw(), after.yaw(),
                    before.cinematicTimeSeconds(), start.cinematicTimeSeconds(), end.cinematicTimeSeconds(),
                    after.cinematicTimeSeconds(), sample.cinematicTimeSeconds());
            assertTrue(CameraMath.angleDifferenceDegrees(yaw, sample.yaw()) <= rotationTolerance + 0.05);
            double fov = SmoothKeyframeCurve.value(before.fov(), start.fov(), end.fov(), after.fov(),
                    before.cinematicTimeSeconds(), start.cinematicTimeSeconds(), end.cinematicTimeSeconds(),
                    after.cinematicTimeSeconds(), sample.cinematicTimeSeconds());
            assertTrue(Math.abs(fov - sample.fov()) <= fovTolerance + 0.05);
        }
    }

    private static List<CameraSample> lineSamples() {
        List<CameraSample> samples = new ArrayList<>();
        for (int i = 0; i <= 10; i++) samples.add(sample(i / 10.0, new Vec3d(i, 0, 0), 0.0));
        return samples;
    }

    private static CameraSample sample(double time, Vec3d position, double yaw) {
        return new CameraSample(time, Math.round(time * 20), position, new Quaternionf(), yaw, 0, 0, 70,
                new Vec3d(0, 1, 0), false);
    }

    private static CameraSample sample(double time, Vec3d position, double yaw, double pitch, double fov) {
        return new CameraSample(time, Math.round(time * 20), position, new Quaternionf(), yaw, pitch, 0, fov,
                new Vec3d(0, 1, 0), false);
    }

    private static CameraSample sample(double time, Vec3d position, double yaw, double pitch, double fov,
                                       Vec3d lookAt) {
        return new CameraSample(time, Math.round(time * 20), position, new Quaternionf(), yaw, pitch, 0, fov,
                lookAt, false);
    }
}
