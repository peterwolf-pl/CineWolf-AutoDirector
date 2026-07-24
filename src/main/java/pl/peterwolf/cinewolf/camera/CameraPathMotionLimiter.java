package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Intra-shot continuity: caps position and look-at steps and re-bakes rate-limited orientation.
 * Does not cross discontinuity markers; hard montage cuts between shots remain untouched.
 */
public final class CameraPathMotionLimiter {
    private static final double DEFAULT_MAX_POSITION_SPEED = 28.0;
    private static final double DEFAULT_MAX_LOOK_AT_SPEED = 36.0;
    private static final double COLLISION_MAX_POSITION_SPEED = 10.0;
    private static final double MIN_DELTA = 1.0e-4;

    private final CameraLookAtSolver lookAtSolver = new CameraLookAtSolver();

    public List<CameraSample> limit(List<CameraSample> samples) {
        return limit(samples, DEFAULT_MAX_POSITION_SPEED, DEFAULT_MAX_LOOK_AT_SPEED,
                CameraLookAtSolver.DEFAULT_MAX_YAW_DEGREES_PER_SECOND,
                CameraLookAtSolver.DEFAULT_MAX_PITCH_DEGREES_PER_SECOND);
    }

    public List<CameraSample> limit(List<CameraSample> samples, double maxPositionSpeed,
                                    double maxLookAtSpeed, double maxYawRate, double maxPitchRate) {
        Objects.requireNonNull(samples, "samples");
        if (samples.size() <= 1) return List.copyOf(samples);

        List<CameraSample> result = new ArrayList<>(samples.size());
        CameraSample previous = samples.getFirst();
        result.add(previous);
        for (int index = 1; index < samples.size(); index++) {
            CameraSample current = samples.get(index);
            if (current.discontinuity() || previous.discontinuity()) {
                result.add(current);
                previous = current;
                continue;
            }
            double delta = Math.max(MIN_DELTA,
                    current.cinematicTimeSeconds() - previous.cinematicTimeSeconds());
            double positionCap = (current.collisionConstrained() || previous.collisionConstrained())
                    ? Math.min(maxPositionSpeed, COLLISION_MAX_POSITION_SPEED)
                    : maxPositionSpeed;
            Vec3d position = CameraSmoothing.clampStep(previous.position(), current.position(),
                    positionCap * delta);
            Vec3d lookAt = CameraSmoothing.clampStep(previous.lookAtPoint(), current.lookAtPoint(),
                    maxLookAtSpeed * delta);
            CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, lookAt,
                    previous.yaw(), previous.pitch(), delta, maxYawRate, maxPitchRate);
            CameraSample limited = new CameraSample(current.cinematicTimeSeconds(), current.replayTime(),
                    position, orientation.quaternion(), orientation.yaw(), orientation.pitch(), orientation.roll(),
                    current.fov(), lookAt,
                    current.discontinuity() || orientation.degenerate(),
                    current.collisionConstrained() || position.distanceTo(current.position()) > 1.0e-6);
            result.add(limited);
            previous = limited;
        }
        return List.copyOf(result);
    }
}
