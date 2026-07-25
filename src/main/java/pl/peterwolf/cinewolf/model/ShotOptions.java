package pl.peterwolf.cinewolf.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Extended shot parameters for version 1.3.5 generators.
 * Existing numeric fields on {@link ShotRequest} remain the primary controls;
 * these options supply generator-specific defaults without breaking older callers.
 */
public record ShotOptions(
        double startHeight,
        double endHeight,
        double endFov,
        double minimumDistance,
        double maximumDistance,
        double velocityDistanceMultiplier,
        double sideOffset,
        double forwardOffset,
        double revolutions,
        double groundClearance,
        double angularVelocityLimitDegreesPerSecond,
        double microOrbitAmount,
        double visibilityThreshold,
        double repositionRadius,
        boolean maintainTargetSize,
        boolean allowLimitedRepositioning,
        boolean speedBasedFov,
        RevealDirection revealDirection,
        TrackingSide trackingSide,
        DetailTargetType detailTargetType,
        VehicleProfileStyle vehicleProfileStyle,
        /**
         * Optional legacy host UUID. {@link ShotType#THIRD_PERSON} no longer requires a second player;
         * the subject is filmed at eye height. Kept for config/plan compatibility.
         */
        UUID cameraHostUuid
) {
    public static final ShotOptions DEFAULTS = new ShotOptions(
            Double.NaN, Double.NaN, Double.NaN,
            2.0, 48.0, 0.35,
            0.0, 0.0, Double.NaN,
            1.0, 120.0, 0.15, 0.55, 1.5,
            false, false, true,
            RevealDirection.AUTO, TrackingSide.AUTO,
            DetailTargetType.AUTO, VehicleProfileStyle.AUTO,
            null
    );

    public ShotOptions {
        Objects.requireNonNull(revealDirection, "revealDirection");
        Objects.requireNonNull(trackingSide, "trackingSide");
        Objects.requireNonNull(detailTargetType, "detailTargetType");
        Objects.requireNonNull(vehicleProfileStyle, "vehicleProfileStyle");
        minimumDistance = finitePositiveOr(minimumDistance, 2.0);
        maximumDistance = Math.max(minimumDistance, finitePositiveOr(maximumDistance, 48.0));
        velocityDistanceMultiplier = finiteNonNegativeOr(velocityDistanceMultiplier, 0.35);
        sideOffset = finiteOr(sideOffset, 0.0);
        forwardOffset = finiteOr(forwardOffset, 0.0);
        groundClearance = finitePositiveOr(groundClearance, 1.0);
        angularVelocityLimitDegreesPerSecond = finitePositiveOr(angularVelocityLimitDegreesPerSecond, 120.0);
        microOrbitAmount = finiteNonNegativeOr(microOrbitAmount, 0.15);
        visibilityThreshold = clamp01(visibilityThreshold, 0.55);
        repositionRadius = finiteNonNegativeOr(repositionRadius, 1.5);
    }

    /** Back-compat constructor without camera host. */
    public ShotOptions(
            double startHeight, double endHeight, double endFov,
            double minimumDistance, double maximumDistance, double velocityDistanceMultiplier,
            double sideOffset, double forwardOffset, double revolutions,
            double groundClearance, double angularVelocityLimitDegreesPerSecond,
            double microOrbitAmount, double visibilityThreshold, double repositionRadius,
            boolean maintainTargetSize, boolean allowLimitedRepositioning, boolean speedBasedFov,
            RevealDirection revealDirection, TrackingSide trackingSide,
            DetailTargetType detailTargetType, VehicleProfileStyle vehicleProfileStyle
    ) {
        this(startHeight, endHeight, endFov, minimumDistance, maximumDistance, velocityDistanceMultiplier,
                sideOffset, forwardOffset, revolutions, groundClearance, angularVelocityLimitDegreesPerSecond,
                microOrbitAmount, visibilityThreshold, repositionRadius, maintainTargetSize,
                allowLimitedRepositioning, speedBasedFov, revealDirection, trackingSide, detailTargetType,
                vehicleProfileStyle, null);
    }

    public static ShotOptions defaults() {
        return DEFAULTS;
    }

    public double resolvedStartHeight(double fallback) {
        return Double.isFinite(startHeight) ? startHeight : fallback;
    }

    public double resolvedEndHeight(double fallback) {
        return Double.isFinite(endHeight) ? endHeight : fallback;
    }

    public double resolvedEndFov(double startFov) {
        return Double.isFinite(endFov) ? endFov : startFov;
    }

    public double resolvedRevolutions(double fallback) {
        return Double.isFinite(revolutions) ? revolutions : fallback;
    }

    public ShotOptions withRevealDirection(RevealDirection direction) {
        return copyWith(direction, trackingSide, detailTargetType, vehicleProfileStyle, maintainTargetSize,
                allowLimitedRepositioning, speedBasedFov, cameraHostUuid);
    }

    public Optional<UUID> cameraHost() {
        return Optional.ofNullable(cameraHostUuid);
    }

    public ShotOptions withCameraHost(UUID hostUuid) {
        return copyWith(revealDirection, trackingSide, detailTargetType, vehicleProfileStyle,
                maintainTargetSize, allowLimitedRepositioning, speedBasedFov, hostUuid);
    }

    private ShotOptions copyWith(RevealDirection reveal, TrackingSide side, DetailTargetType detail,
                                 VehicleProfileStyle style, boolean maintain, boolean reposition, boolean speedFov,
                                 UUID host) {
        return new ShotOptions(startHeight, endHeight, endFov, minimumDistance, maximumDistance,
                velocityDistanceMultiplier, sideOffset, forwardOffset, revolutions, groundClearance,
                angularVelocityLimitDegreesPerSecond, microOrbitAmount, visibilityThreshold, repositionRadius,
                maintain, reposition, speedFov, reveal, side, detail, style, host);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double finitePositiveOr(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double finiteNonNegativeOr(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }

    private static double clamp01(double value, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(0.0, Math.min(1.0, value));
    }
}
