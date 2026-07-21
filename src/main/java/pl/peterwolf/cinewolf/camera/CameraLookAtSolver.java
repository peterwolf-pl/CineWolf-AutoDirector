package pl.peterwolf.cinewolf.camera;

import org.joml.Quaternionf;
import pl.peterwolf.cinewolf.model.Vec3d;

public final class CameraLookAtSolver {
    private static final double MIN_DISTANCE_SQUARED = 1.0e-10;

    public Orientation solve(Vec3d camera, Vec3d target, double previousYaw) {
        Vec3d direction = target.subtract(camera);
        if (!direction.isFinite() || direction.lengthSquared() < MIN_DISTANCE_SQUARED) {
            double yaw = Double.isFinite(previousYaw) ? previousYaw : 0.0;
            return new Orientation(yaw, 0.0, 0.0, quaternion(yaw, 0.0, 0.0), true);
        }

        Vec3d normal = direction.normalizeOr(new Vec3d(0.0, 0.0, 1.0));
        double yaw = -Math.toDegrees(Math.atan2(normal.x(), normal.z()));
        yaw = CameraMath.unwrapDegrees(previousYaw, yaw);
        double pitch = -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, normal.y()))));
        pitch = Math.max(-90.0, Math.min(90.0, pitch));
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
