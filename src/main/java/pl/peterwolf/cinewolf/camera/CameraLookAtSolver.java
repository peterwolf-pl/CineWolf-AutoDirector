package pl.peterwolf.cinewolf.camera;

import org.joml.Quaternionf;
import pl.peterwolf.cinewolf.model.Vec3d;

public final class CameraLookAtSolver {
    private static final double MIN_DISTANCE_SQUARED = 1.0e-10;
    private static final double CLOSE_RANGE = 1.75;
    public static final double DEFAULT_MAX_YAW_DEGREES_PER_SECOND = 160.0;
    public static final double DEFAULT_MAX_PITCH_DEGREES_PER_SECOND = 110.0;

    public Orientation solve(Vec3d camera, Vec3d target, double previousYaw) {
        return solve(camera, target, previousYaw, Double.NaN, 0.0,
                DEFAULT_MAX_YAW_DEGREES_PER_SECOND, DEFAULT_MAX_PITCH_DEGREES_PER_SECOND);
    }

    public Orientation solve(Vec3d camera, Vec3d target, double previousYaw, double previousPitch,
                             double deltaSeconds) {
        return solve(camera, target, previousYaw, previousPitch, deltaSeconds,
                DEFAULT_MAX_YAW_DEGREES_PER_SECOND, DEFAULT_MAX_PITCH_DEGREES_PER_SECOND);
    }

    public Orientation solve(Vec3d camera, Vec3d target, double previousYaw, double previousPitch,
                             double deltaSeconds, double maxYawDegreesPerSecond,
                             double maxPitchDegreesPerSecond) {
        Vec3d focus = target;
        Vec3d direction = focus.subtract(camera);
        if (!direction.isFinite() || direction.lengthSquared() < MIN_DISTANCE_SQUARED) {
            double yaw = Double.isFinite(previousYaw) ? previousYaw : 0.0;
            double pitch = Double.isFinite(previousPitch) ? previousPitch : 0.0;
            return new Orientation(yaw, pitch, 0.0, quaternion(yaw, pitch, 0.0), true);
        }

        // Near-pass damping: avoid whip when the subject nearly collides with the camera.
        double distance = direction.length();
        if (distance < CLOSE_RANGE && Double.isFinite(previousYaw)) {
            Vec3d previousForward = CameraMath.horizontalDirectionFromYaw(previousYaw);
            double previousPitchRadians = Math.toRadians(Double.isFinite(previousPitch) ? previousPitch : 0.0);
            Vec3d previousAim = previousForward.multiply(Math.cos(previousPitchRadians))
                    .add(Vec3d.UP.multiply(-Math.sin(previousPitchRadians)))
                    .normalizeOr(previousForward);
            double blend = 1.0 - (distance / CLOSE_RANGE);
            Vec3d damped = direction.normalizeOr(previousAim).lerp(previousAim, blend * 0.85).normalizeOr(previousAim);
            focus = camera.add(damped.multiply(Math.max(distance, 0.75)));
            direction = focus.subtract(camera);
        }

        Vec3d normal = direction.normalizeOr(new Vec3d(0.0, 0.0, 1.0));
        double yaw = -Math.toDegrees(Math.atan2(normal.x(), normal.z()));
        yaw = CameraMath.unwrapDegrees(previousYaw, yaw);
        double pitch = -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, normal.y()))));
        pitch = Math.max(-90.0, Math.min(90.0, pitch));

        if (Double.isFinite(previousYaw) && Double.isFinite(deltaSeconds) && deltaSeconds > 1.0e-6) {
            double maxYaw = Math.max(1.0, maxYawDegreesPerSecond) * deltaSeconds;
            double maxPitch = Math.max(1.0, maxPitchDegreesPerSecond) * deltaSeconds;
            double yawDelta = yaw - previousYaw;
            if (Math.abs(yawDelta) > maxYaw) {
                yaw = previousYaw + Math.copySign(maxYaw, yawDelta);
            }
            if (Double.isFinite(previousPitch)) {
                double pitchDelta = pitch - previousPitch;
                if (Math.abs(pitchDelta) > maxPitch) {
                    pitch = previousPitch + Math.copySign(maxPitch, pitchDelta);
                }
            }
        }
        return new Orientation(yaw, pitch, 0.0, quaternion(yaw, pitch, 0.0), false);
    }

    private static Quaternionf quaternion(double yaw, double pitch, double roll) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-yaw),
                (float) Math.toRadians(pitch),
                (float) Math.toRadians(roll)
        ).normalize();
    }

    public record Orientation(double yaw, double pitch, double roll, Quaternionf quaternion, boolean degenerate) {
    }
}
