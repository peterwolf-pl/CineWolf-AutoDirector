package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.model.PathSmoothingSettings;

/** Mutable, Gson-friendly controls for the pre-collision camera-path filter. */
public final class PathSmoothingConfig {
    public boolean enabled = true;
    public double positionStrength = 0.65;
    public double rotationStrength = 0.55;
    public double windowSeconds = 0.30;
    public boolean outlierRejection = true;
    public double outlierThresholdBlocks = 2.0;
    public double outlierSpeedThresholdBlocksPerSecond = 24.0;

    public void normalize() {
        positionStrength = clamp(positionStrength, 0.65, 0.0, 1.0);
        rotationStrength = clamp(rotationStrength, 0.55, 0.0, 1.0);
        windowSeconds = clamp(windowSeconds, 0.30, 0.05, 2.0);
        outlierThresholdBlocks = clamp(outlierThresholdBlocks, 2.0, 0.25, 64.0);
        outlierSpeedThresholdBlocksPerSecond = clamp(
                outlierSpeedThresholdBlocksPerSecond, 24.0, 1.0, 512.0);
    }

    public PathSmoothingSettings settings() {
        normalize();
        return new PathSmoothingSettings(enabled, positionStrength, rotationStrength, windowSeconds,
                outlierRejection, outlierThresholdBlocks, outlierSpeedThresholdBlocksPerSecond);
    }

    private static double clamp(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value)) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
