package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Objects;

public record MovementMetrics(
        TargetReference target,
        long replayTime,
        Vec3d position,
        Vec3d displacement,
        Vec3d velocity,
        Vec3d smoothedVelocity,
        double speed,
        double smoothedSpeed,
        double acceleration,
        double verticalSpeed,
        double headingDegrees,
        double headingChangeDegrees,
        double angularVelocityDegreesPerSecond,
        double altitude,
        double groundProximity,
        long stationaryDurationTicks,
        DifferenceMethod differenceMethod
) {
    public MovementMetrics {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(displacement, "displacement");
        Objects.requireNonNull(velocity, "velocity");
        Objects.requireNonNull(smoothedVelocity, "smoothedVelocity");
        Objects.requireNonNull(differenceMethod, "differenceMethod");
    }
}
