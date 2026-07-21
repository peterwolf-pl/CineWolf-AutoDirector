package pl.peterwolf.cinewolf.model;

import java.util.Objects;

public record ShotRequest(
        TargetReference target,
        ShotType shotType,
        double diameter,
        double height,
        double distance,
        double startDistance,
        double endDistance,
        double rpm,
        double durationSeconds,
        double startAngleDegrees,
        RotationDirection direction,
        double cameraSpeed,
        double fov,
        EasingType easing,
        double lookAheadSeconds,
        long replayStartTime,
        long replayEndTime
) {
    public ShotRequest {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(shotType, "shotType");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(easing, "easing");
    }

    public double revolutions() {
        return rpm * durationSeconds / 60.0;
    }
}
