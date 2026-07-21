package pl.peterwolf.cinewolf.camera;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathStatistics;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraPathFinalizerTest {
    private final CameraPathFinalizer finalizer = new CameraPathFinalizer();

    @Test
    void removesStaleKeyframeLimitWhenCurrentSimplificationFits() {
        List<CameraSample> samples = List.of(sample(0.0, 0L, 0.0, false),
                sample(0.5, 10L, 0.5, false), sample(1.0, 20L, 1.0, false));
        CameraPathPlan raw = plan(samples, List.of(new PathWarning(PathWarning.Severity.ERROR, "keyframe_limit",
                "Limit from an earlier pre-smoothing pass", 0.0)));

        CameraPathPlan result = finalizer.finalizePath(raw,
                new SamplingSettings(12, 100, 10, 0.01, 1.0, 0.1, 2.0));

        assertTrue(result.valid());
        assertFalse(result.warnings().stream().anyMatch(warning -> warning.code().equals("keyframe_limit")));
    }

    @Test
    void recalculatesKeyframeLimitFromCurrentCollisionAnchors() {
        List<CameraSample> samples = List.of(sample(0.0, 0L, 0.0, false),
                sample(0.5, 10L, 0.5, true), sample(1.0, 20L, 1.0, false));

        CameraPathPlan result = finalizer.finalizePath(plan(samples, List.of()),
                new SamplingSettings(12, 100, 2, 1.0, 180.0, 10.0, 2.0));

        assertFalse(result.valid());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.code().equals("keyframe_limit")));
    }

    private static CameraPathPlan plan(List<CameraSample> samples, List<PathWarning> warnings) {
        return new CameraPathPlan(TestFixtures.request(ShotType.FOLLOW), samples, samples, warnings,
                new PathStatistics(samples.size(), samples.size(), 1.0, 1.0, 0.0));
    }

    private static CameraSample sample(double time, long tick, double x, boolean collisionConstrained) {
        return new CameraSample(time, tick, new Vec3d(x, 2.0, 0.0), new Quaternionf(), 0.0, 0.0, 0.0,
                70.0, new Vec3d(x, 2.0, 8.0), false, collisionConstrained);
    }
}
