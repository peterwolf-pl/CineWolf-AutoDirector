package pl.peterwolf.cinewolf.model;

public record SamplingSettings(
        int samplesPerSecond,
        int maximumSamples,
        int maximumKeyframes,
        double positionTolerance,
        double rotationToleranceDegrees,
        double fovTolerance,
        double maximumKeyframeIntervalSeconds,
        PathSmoothingSettings pathSmoothing
) {
    public SamplingSettings(int samplesPerSecond, int maximumSamples, int maximumKeyframes,
                            double positionTolerance, double rotationToleranceDegrees, double fovTolerance,
                            double maximumKeyframeIntervalSeconds) {
        this(samplesPerSecond, maximumSamples, maximumKeyframes, positionTolerance, rotationToleranceDegrees,
                fovTolerance, maximumKeyframeIntervalSeconds, PathSmoothingSettings.defaults());
    }

    public SamplingSettings {
        if (pathSmoothing == null) pathSmoothing = PathSmoothingSettings.defaults();
    }

    public static SamplingSettings defaults() {
        return new SamplingSettings(12, 4096, 512, 0.08, 0.45, 0.1, 1.0,
                PathSmoothingSettings.defaults());
    }
}
