package pl.peterwolf.cinewolf.montage.preset;

import java.util.Objects;

public record MontageStyleSettings(
        double cameraMovementIntensity,
        double cutFrequency,
        FramingType targetFraming,
        double preferredReplaySpeed,
        double minimumReplaySpeed,
        double maximumReplaySpeed,
        double maximumReplaySpeedChange,
        boolean allowReplaySpeedChanges,
        boolean preferChronologicalOrder,
        boolean centerSafeFraming
) {
    public MontageStyleSettings {
        requireUnitInterval(cameraMovementIntensity, "cameraMovementIntensity");
        requireUnitInterval(cutFrequency, "cutFrequency");
        Objects.requireNonNull(targetFraming, "targetFraming");
        requirePositiveFinite(preferredReplaySpeed, "preferredReplaySpeed");
        requirePositiveFinite(minimumReplaySpeed, "minimumReplaySpeed");
        requirePositiveFinite(maximumReplaySpeed, "maximumReplaySpeed");
        if (maximumReplaySpeed < minimumReplaySpeed) {
            throw new IllegalArgumentException("maximumReplaySpeed must be at least minimumReplaySpeed");
        }
        if (preferredReplaySpeed < minimumReplaySpeed || preferredReplaySpeed > maximumReplaySpeed) {
            throw new IllegalArgumentException("preferredReplaySpeed must be inside the configured speed range");
        }
        if (!Double.isFinite(maximumReplaySpeedChange) || maximumReplaySpeedChange < 0.0) {
            throw new IllegalArgumentException("maximumReplaySpeedChange must be finite and non-negative");
        }
    }

    private static void requireUnitInterval(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between zero and one");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }
}
