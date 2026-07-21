package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ReplayEntitySnapshot(
        TargetReference target,
        TargetPose pose,
        double health,
        double maximumHealth,
        int hurtTime,
        boolean attacking,
        boolean swinging,
        boolean alive,
        boolean onGround,
        double groundProximity,
        Optional<UUID> vehicleUuid,
        Optional<String> vehicleType,
        boolean creativeFlying,
        boolean elytraFlying
) {
    public ReplayEntitySnapshot {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(pose, "pose");
        health = finiteOrUnknown(health);
        maximumHealth = finiteOrUnknown(maximumHealth);
        hurtTime = Math.max(0, hurtTime);
        groundProximity = Double.isFinite(groundProximity) && groundProximity >= 0.0
                ? groundProximity : Double.NaN;
        vehicleUuid = Objects.requireNonNullElse(vehicleUuid, Optional.empty());
        Optional<String> normalizedVehicleType = vehicleType == null ? Optional.empty() : vehicleType;
        vehicleType = normalizedVehicleType.map(String::trim).filter(value -> !value.isEmpty());
    }

    public static ReplayEntitySnapshot basic(TargetReference target, TargetPose pose) {
        return new ReplayEntitySnapshot(target, pose, Double.NaN, Double.NaN, 0,
                false, false, true, false, Double.NaN, Optional.empty(), Optional.empty(), false, false);
    }

    public boolean healthKnown() {
        return Double.isFinite(health) && Double.isFinite(maximumHealth) && maximumHealth > 0.0;
    }

    public boolean inVehicle() {
        return vehicleUuid.isPresent() || pose.inVehicle();
    }

    public boolean explicitFlight() {
        // Flashback reliably preserves Elytra state. Creative flight is only a hint and must be
        // corroborated by sustained airborne movement before a detector treats it as flight.
        return elytraFlying;
    }

    private static double finiteOrUnknown(double value) {
        return Double.isFinite(value) ? value : Double.NaN;
    }
}
