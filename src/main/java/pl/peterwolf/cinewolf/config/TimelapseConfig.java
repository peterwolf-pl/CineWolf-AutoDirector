package pl.peterwolf.cinewolf.config;

public final class TimelapseConfig {
    public static final double MIN_SPEED_MULTIPLIER = 1.0;
    public static final double MAX_SPEED_MULTIPLIER = 1000.0;

    /**
     * Replay-time acceleration applied only to Flashback export sampling.
     * x1 preserves Flashback's normal export behaviour.
     */
    public double speedMultiplier = 1.0;

    public void normalize() {
        if (!Double.isFinite(speedMultiplier)) {
            speedMultiplier = MIN_SPEED_MULTIPLIER;
        }
        speedMultiplier = Math.max(MIN_SPEED_MULTIPLIER,
                Math.min(MAX_SPEED_MULTIPLIER, speedMultiplier));
    }

    public boolean enabled() {
        normalize();
        return speedMultiplier > MIN_SPEED_MULTIPLIER + 1.0e-6;
    }

    /**
     * Keep Flashback's encoder FPS unchanged and reduce only the source
     * sampling rate. The replay therefore advances faster between output
     * frames and unnecessary intermediate frames are never rendered.
     */
    public double samplingFps(double outputFps) {
        normalize();
        if (!Double.isFinite(outputFps) || outputFps <= 0.0 || !enabled()) {
            return outputFps;
        }
        return outputFps / speedMultiplier;
    }
}
