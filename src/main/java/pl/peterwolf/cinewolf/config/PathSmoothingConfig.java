package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.model.PathSmoothingSettings;

/** Mutable, Gson-friendly controls for the pre-collision camera-path filter. */
public final class PathSmoothingConfig {
    public boolean enabled = true;
    public double positionStrength = 0.65;
    /** Look-at / camera-target smoothing (0 = raw aim, 1 = fully filtered aim). */
    public double rotationStrength = 0.55;
    public double windowSeconds = 0.30;
    /** Independent strength for smoothing the subject/target aim point. */
    public double targetStrength = 0.70;
    /** Time window used only for target/aim filtering (seconds). */
    public double targetWindowSeconds = 0.40;
    public boolean outlierRejection = true;
    public double outlierThresholdBlocks = 2.0;
    public double outlierSpeedThresholdBlocksPerSecond = 24.0;
    /** Reject isolated target-aim spikes separately from camera-position spikes. */
    public boolean targetOutlierRejection = true;

    public void normalize() {
        positionStrength = clamp(positionStrength, 0.65, 0.0, 1.0);
        rotationStrength = clamp(rotationStrength, 0.55, 0.0, 1.0);
        windowSeconds = clamp(windowSeconds, 0.30, 0.05, 2.0);
        targetStrength = clamp(targetStrength, 0.70, 0.0, 1.0);
        targetWindowSeconds = clamp(targetWindowSeconds, 0.40, 0.05, 2.0);
        outlierThresholdBlocks = clamp(outlierThresholdBlocks, 2.0, 0.25, 64.0);
        outlierSpeedThresholdBlocksPerSecond = clamp(
                outlierSpeedThresholdBlocksPerSecond, 24.0, 1.0, 512.0);
    }

    public PathSmoothingSettings settings() {
        normalize();
        return new PathSmoothingSettings(enabled, positionStrength, rotationStrength, windowSeconds,
                targetStrength, targetWindowSeconds, outlierRejection, outlierThresholdBlocks,
                outlierSpeedThresholdBlocksPerSecond, targetOutlierRejection);
    }

    private static double clamp(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value)) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
