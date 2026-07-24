package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathSmoothingSettings;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraPathSmootherTargetSettingsTest {
    private final CameraPathSmoother smoother = new CameraPathSmoother();

    @Test
    void strongerTargetSmoothingReducesAimJitterMoreThanWeakTargetSmoothing() {
        List<CameraSample> samples = aimJitterSamples();
        PathSmoothingSettings weak = new PathSmoothingSettings(true, 0.2, 0.0, 0.25, 0.1, 0.15,
                false, 2.0, 24.0, false);
        PathSmoothingSettings strong = new PathSmoothingSettings(true, 0.2, 0.0, 0.25, 1.0, 0.6,
                false, 2.0, 24.0, false);

        double weakError = averageAimLateralError(smoother.smooth(samples, weak));
        double strongError = averageAimLateralError(smoother.smooth(samples, strong));

        assertTrue(strongError < weakError * 0.85,
                "Expected stronger target smoothing to reduce aim jitter: weak=" + weakError
                        + " strong=" + strongError);
    }

    private static List<CameraSample> aimJitterSamples() {
        List<CameraSample> samples = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            double time = index * 0.05;
            Vec3d position = new Vec3d(0.0, 2.0, -6.0);
            double jitter = (index % 2 == 0) ? 0.8 : -0.8;
            Vec3d lookAt = new Vec3d(jitter, 1.0, 0.0);
            samples.add(sample(time, Math.round(time * 20.0), position, lookAt));
        }
        return samples;
    }

    private static double averageAimLateralError(List<CameraSample> samples) {
        double total = 0.0;
        for (int index = 1; index < samples.size() - 1; index++) {
            total += Math.abs(samples.get(index).lookAtPoint().x());
        }
        return total / Math.max(1, samples.size() - 2);
    }

    private static CameraSample sample(double time, long replayTick, Vec3d position, Vec3d lookAt) {
        CameraLookAtSolver.Orientation orientation = new CameraLookAtSolver().solve(position, lookAt, Double.NaN);
        return new CameraSample(time, replayTick, position, orientation.quaternion(), orientation.yaw(),
                orientation.pitch(), orientation.roll(), 70.0, lookAt, orientation.degenerate(), false);
    }
}
