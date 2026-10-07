package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Intra-shot continuity on the dense planning path: caps position and look-at steps, radial zoom rate,
 * FOV rate, and re-bakes rate-limited orientation. These caps are not keyframe spacing. Flashback keys are
 * chosen later so SMOOTH playback stays on this already-limited path. Does not cross discontinuity markers;
 * hard montage cuts between shots remain untouched.
 *
 * <p>Radial limiting is essential for cinematic feel: free-space camera lag (follow/chase) otherwise
 * produces continuous zoom in/out ("breathing") even when absolute position speed looks reasonable.</p>
 */
public final class CameraPathMotionLimiter {
    private static final double DEFAULT_MAX_POSITION_SPEED = 28.0;
    private static final double DEFAULT_MAX_LOOK_AT_SPEED = 36.0;
    /** Blocks/second of camera↔subject distance change. ~2–4 keeps framing locked; intentional dolly is slower. */
    private static final double DEFAULT_MAX_RADIAL_SPEED = 3.5;
    private static final double DEFAULT_MAX_FOV_SPEED = 12.0;
    private static final double COLLISION_MAX_POSITION_SPEED = 10.0;
    private static final double COLLISION_MAX_RADIAL_SPEED = 2.0;
    private static final double MIN_DELTA = 1.0e-4;
    private static final double MIN_FOCUS_DISTANCE = 0.35;

    private final CameraLookAtSolver lookAtSolver = new CameraLookAtSolver();

    public List<CameraSample> limit(List<CameraSample> samples) {
        return limit(samples, DEFAULT_MAX_POSITION_SPEED, DEFAULT_MAX_LOOK_AT_SPEED,
                CameraLookAtSolver.DEFAULT_MAX_YAW_DEGREES_PER_SECOND,
                CameraLookAtSolver.DEFAULT_MAX_PITCH_DEGREES_PER_SECOND,
                DEFAULT_MAX_RADIAL_SPEED, DEFAULT_MAX_FOV_SPEED);
    }

    public List<CameraSample> limit(List<CameraSample> samples, double maxPositionSpeed,
                                    double maxLookAtSpeed, double maxYawRate, double maxPitchRate) {
        return limit(samples, maxPositionSpeed, maxLookAtSpeed, maxYawRate, maxPitchRate,
                DEFAULT_MAX_RADIAL_SPEED, DEFAULT_MAX_FOV_SPEED);
    }

    public List<CameraSample> limit(List<CameraSample> samples, double maxPositionSpeed,
                                    double maxLookAtSpeed, double maxYawRate, double maxPitchRate,
                                    double maxRadialSpeed, double maxFovSpeed) {
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
            boolean collision = current.collisionConstrained() || previous.collisionConstrained();
            double positionCap = collision
                    ? Math.min(maxPositionSpeed, COLLISION_MAX_POSITION_SPEED)
                    : maxPositionSpeed;
            double radialCap = collision
                    ? Math.min(maxRadialSpeed, COLLISION_MAX_RADIAL_SPEED)
                    : maxRadialSpeed;

            Vec3d lookAt = CameraSmoothing.clampStep(previous.lookAtPoint(), current.lookAtPoint(),
                    maxLookAtSpeed * delta);
            // 1) Kill radial breathing against the intended framing.
            // 2) Then enforce cartesian speed so teleports stay capped (radial reproject alone can
            //    still leave the camera far from the previous sample).
            Vec3d radialLocked = clampFocusDistance(previous.position(), previous.lookAtPoint(),
                    current.position(), lookAt, radialCap * delta);
            Vec3d position = CameraSmoothing.clampStep(previous.position(), radialLocked,
                    positionCap * delta);

            double fov = clampFov(previous.fov(), current.fov(), maxFovSpeed * delta);

            CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, lookAt,
                    previous.yaw(), previous.pitch(), delta, maxYawRate, maxPitchRate);
            CameraSample limited = new CameraSample(current.cinematicTimeSeconds(), current.replayTime(),
                    position, orientation.quaternion(), orientation.yaw(), orientation.pitch(), orientation.roll(),
                    fov, lookAt,
                    current.discontinuity() || orientation.degenerate(),
                    current.collisionConstrained()
                            || position.distanceTo(current.position()) > 1.0e-6
                            || Math.abs(fov - current.fov()) > 1.0e-4);
            result.add(limited);
            previous = limited;
        }
        return List.copyOf(result);
    }

    /**
     * Keeps camera↔lookAt distance from jumping more than {@code maxRadialStep} between samples.
     * Rebuilds the camera on the ray from the new look-at through the proposed camera position.
     */
    static Vec3d clampFocusDistance(Vec3d previousPosition, Vec3d previousLookAt,
                                    Vec3d position, Vec3d lookAt, double maxRadialStep) {
        if (position == null || lookAt == null || !position.isFinite() || !lookAt.isFinite()) {
            return position;
        }
        double previousDistance = previousPosition.distanceTo(previousLookAt);
        if (!Double.isFinite(previousDistance) || previousDistance < MIN_FOCUS_DISTANCE) {
            previousDistance = Math.max(MIN_FOCUS_DISTANCE, position.distanceTo(lookAt));
        }
        Vec3d offset = position.subtract(lookAt);
        double desiredDistance = offset.length();
        if (!Double.isFinite(desiredDistance) || desiredDistance < 1.0e-8) {
            // Degenerate: fall back to previous framing direction at clamped radius.
            Vec3d fallbackOffset = previousPosition.subtract(previousLookAt);
            if (fallbackOffset.lengthSquared() < 1.0e-8) {
                fallbackOffset = new Vec3d(0.0, 0.0, 1.0);
            }
            double radius = clampScalar(previousDistance, previousDistance, maxRadialStep);
            return lookAt.add(fallbackOffset.normalizeOr(new Vec3d(0.0, 0.0, 1.0)).multiply(radius));
        }
        double radius = clampScalar(desiredDistance, previousDistance, maxRadialStep);
        radius = Math.max(MIN_FOCUS_DISTANCE, radius);
        return lookAt.add(offset.multiply(radius / desiredDistance));
    }

    private static double clampFov(double previous, double desired, double maxStep) {
        if (!Double.isFinite(desired)) return previous;
        if (!Double.isFinite(previous)) return desired;
        if (!Double.isFinite(maxStep) || maxStep <= 0.0) return previous;
        double delta = desired - previous;
        if (Math.abs(delta) <= maxStep) return desired;
        return previous + Math.copySign(maxStep, delta);
    }

    private static double clampScalar(double desired, double previous, double maxStep) {
        if (!Double.isFinite(desired)) return previous;
        if (!Double.isFinite(maxStep) || maxStep <= 0.0) return previous;
        double delta = desired - previous;
        if (Math.abs(delta) <= maxStep) return desired;
        return previous + Math.copySign(maxStep, delta);
    }
}
