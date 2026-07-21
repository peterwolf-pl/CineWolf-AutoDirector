package pl.peterwolf.cinewolf.model;

import org.joml.Quaternionf;

import java.util.Objects;

public record CameraSample(
        double cinematicTimeSeconds,
        long replayTime,
        Vec3d position,
        Quaternionf rotation,
        double yaw,
        double pitch,
        double roll,
        double fov,
        Vec3d lookAtPoint,
        boolean discontinuity,
        boolean collisionConstrained
) {
    public CameraSample(double cinematicTimeSeconds, long replayTime, Vec3d position, Quaternionf rotation,
                        double yaw, double pitch, double roll, double fov, Vec3d lookAtPoint,
                        boolean discontinuity) {
        this(cinematicTimeSeconds, replayTime, position, rotation, yaw, pitch, roll, fov, lookAtPoint,
                discontinuity, false);
    }

    public CameraSample {
        Objects.requireNonNull(position, "position");
        rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
        Objects.requireNonNull(lookAtPoint, "lookAtPoint");
    }

    public boolean isFinite() {
        return Double.isFinite(cinematicTimeSeconds) && position.isFinite()
                && Float.isFinite(rotation.x) && Float.isFinite(rotation.y)
                && Float.isFinite(rotation.z) && Float.isFinite(rotation.w)
                && Double.isFinite(yaw) && Double.isFinite(pitch)
                && Double.isFinite(roll) && Double.isFinite(fov)
                && lookAtPoint.isFinite();
    }
}
