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
        long replayEndTime,
        ShotOptions options
) {
    public ShotRequest {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(shotType, "shotType");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(easing, "easing");
        options = options == null ? ShotOptions.defaults() : options;
    }

    /** Compatibility constructor used by existing call sites. */
    public ShotRequest(
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
        this(target, shotType, diameter, height, distance, startDistance, endDistance, rpm, durationSeconds,
                startAngleDegrees, direction, cameraSpeed, fov, easing, lookAheadSeconds,
                replayStartTime, replayEndTime, ShotOptions.defaults());
    }

    public double revolutions() {
        if (shotType == ShotType.SPIRAL) {
            return options.resolvedRevolutions(rpm * durationSeconds / 60.0);
        }
        return rpm * durationSeconds / 60.0;
    }

    public ShotRequest withOptions(ShotOptions next) {
        return new ShotRequest(target, shotType, diameter, height, distance, startDistance, endDistance, rpm,
                durationSeconds, startAngleDegrees, direction, cameraSpeed, fov, easing, lookAheadSeconds,
                replayStartTime, replayEndTime, next);
    }
}
