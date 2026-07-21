package pl.peterwolf.cinewolf.model;

import java.util.Objects;

public record TargetPose(
        Vec3d position,
        Vec3d focusPosition,
        BoundingBox boundingBox,
        double yaw,
        double pitch,
        Vec3d velocity,
        String entityType,
        boolean inVehicle,
        String dimension,
        boolean discontinuity
) {
    public TargetPose {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(focusPosition, "focusPosition");
        Objects.requireNonNull(boundingBox, "boundingBox");
        velocity = Objects.requireNonNullElse(velocity, Vec3d.ZERO);
        entityType = Objects.requireNonNullElse(entityType, "unknown");
        dimension = Objects.requireNonNullElse(dimension, "unknown");
    }

    public boolean isFinite() {
        return position.isFinite() && focusPosition.isFinite() && boundingBox.isFinite() && velocity.isFinite()
                && Double.isFinite(yaw) && Double.isFinite(pitch);
    }
}
