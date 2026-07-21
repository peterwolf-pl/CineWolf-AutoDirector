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
    void straightPathPreservesFirstAndLastOnlyWhenIntervalAllows() {
        List<CameraSample> samples = lineSamples();
        SamplingSettings settings = new SamplingSettings(10, 100, 100, 0.01, 1.0, 0.1, 10.0);
        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples, settings);
        assertEquals(2, simplified.size());
        assertSame(samples.getFirst(), simplified.getFirst());
        assertSame(samples.getLast(), simplified.getLast());
    }

    @Test
    void maximumIntervalAddsIntermediateKeyframes() {
        SamplingSettings settings = new SamplingSettings(10, 100, 100, 0.01, 1.0, 0.1, 0.25);
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
    void usesActualSampleTimeForPitchAndFovInterpolation() {
        List<CameraSample> samples = List.of(
                sample(0.0, new Vec3d(0, 0, 0), 0, 0, 60),
                sample(0.2, new Vec3d(2, 0, 0), 2, 2, 64),
                sample(1.0, new Vec3d(10, 0, 0), 10, 10, 80));

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 100, 100, 0.01, 0.01, 0.01, 2.0));

        assertEquals(2, simplified.size(), "a time-linear sample is removable even when it is not the midpoint");
    }

    @Test
    void preservesNonLinearFovAtIrregularSampleTime() {
        List<CameraSample> samples = List.of(
                sample(0.0, new Vec3d(0, 0, 0), 0, 0, 60),
                sample(0.2, new Vec3d(2, 0, 0), 2, 2, 72),
                sample(1.0, new Vec3d(10, 0, 0), 10, 10, 80));

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(samples,
                new SamplingSettings(12, 100, 100, 0.01, 0.01, 0.01, 2.0));

        assertEquals(3, simplified.size());
    }

    @Test
    void preservesCollisionConstrainedSamplesWithoutTreatingThemAsCuts() {
        CameraSample first = sample(0.0, new Vec3d(0.0, 0.0, 0.0), 0.0);
        CameraSample constrained = new CameraSample(0.5, 10L, new Vec3d(0.5, 0.0, 0.0),
                new Quaternionf(), 0.0, 0.0, 0.0, 70.0, new Vec3d(0.5, 0.0, 1.0), false, true);
        CameraSample last = sample(1.0, new Vec3d(1.0, 0.0, 0.0), 0.0);

        List<CameraSample> simplified = new CameraPathSimplifier().simplify(List.of(first, constrained, last),
                new SamplingSettings(12, 4096, 512, 1.0, 180.0, 10.0, 2.0));

        assertEquals(List.of(first, constrained, last), simplified);
        assertFalse(constrained.discontinuity());
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
}
