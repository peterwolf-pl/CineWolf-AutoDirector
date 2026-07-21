package pl.peterwolf.cinewolf.model;

/** Immutable path-filter settings carried with a replay generation context. */
public record PathSmoothingSettings(
        boolean enabled,
        double positionStrength,
        double rotationStrength,
        double windowSeconds,
        boolean outlierRejection,
        double outlierThresholdBlocks,
        double outlierSpeedThresholdBlocksPerSecond
) {
    public PathSmoothingSettings {
        positionStrength = clamp(positionStrength, 0.0, 1.0);
        rotationStrength = clamp(rotationStrength, 0.0, 1.0);
        windowSeconds = clamp(windowSeconds, 0.05, 2.0);
        outlierThresholdBlocks = clamp(outlierThresholdBlocks, 0.25, 64.0);
        outlierSpeedThresholdBlocksPerSecond = clamp(
                outlierSpeedThresholdBlocksPerSecond, 1.0, 512.0);
    }

    public static PathSmoothingSettings defaults() {
        return new PathSmoothingSettings(true, 0.65, 0.55, 0.30, true, 2.0, 24.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
