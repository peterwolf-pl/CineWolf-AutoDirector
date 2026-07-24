package pl.peterwolf.cinewolf.model;

/** Immutable path-filter settings carried with a replay generation context. */
public record PathSmoothingSettings(
        boolean enabled,
        double positionStrength,
        double rotationStrength,
        double windowSeconds,
        double targetStrength,
        double targetWindowSeconds,
        boolean outlierRejection,
        double outlierThresholdBlocks,
        double outlierSpeedThresholdBlocksPerSecond,
        boolean targetOutlierRejection
) {
    public PathSmoothingSettings {
        positionStrength = clamp(positionStrength, 0.0, 1.0);
        rotationStrength = clamp(rotationStrength, 0.0, 1.0);
        windowSeconds = clamp(windowSeconds, 0.05, 2.0);
        targetStrength = clamp(targetStrength, 0.0, 1.0);
        targetWindowSeconds = clamp(targetWindowSeconds, 0.05, 2.0);
        outlierThresholdBlocks = clamp(outlierThresholdBlocks, 0.25, 64.0);
        outlierSpeedThresholdBlocksPerSecond = clamp(
                outlierSpeedThresholdBlocksPerSecond, 1.0, 512.0);
    }

    /** Backward-compatible constructor used by older tests. */
    public PathSmoothingSettings(boolean enabled, double positionStrength, double rotationStrength,
                                 double windowSeconds, boolean outlierRejection,
                                 double outlierThresholdBlocks, double outlierSpeedThresholdBlocksPerSecond) {
        this(enabled, positionStrength, rotationStrength, windowSeconds, rotationStrength, windowSeconds,
                outlierRejection, outlierThresholdBlocks, outlierSpeedThresholdBlocksPerSecond, outlierRejection);
    }

    public static PathSmoothingSettings defaults() {
        return new PathSmoothingSettings(true, 0.65, 0.55, 0.30, 0.70, 0.40, true, 2.0, 24.0, true);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
