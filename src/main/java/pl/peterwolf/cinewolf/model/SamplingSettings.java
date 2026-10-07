package pl.peterwolf.cinewolf.model;

public record SamplingSettings(
        int samplesPerSecond,
        int maximumSamples,
        int maximumKeyframes,
        double positionTolerance,
        double rotationToleranceDegrees,
        double fovTolerance,
        double maximumKeyframeIntervalSeconds,
        /**
         * Fixed camera-keyframe spacing in seconds. {@code <= 0} keeps the error-based reducer.
         * A positive value places one key per interval and does not add a key for every player step.
         */
        double cameraKeyframeIntervalSeconds,
        PathSmoothingSettings pathSmoothing
) {
    public SamplingSettings(int samplesPerSecond, int maximumSamples, int maximumKeyframes,
                            double positionTolerance, double rotationToleranceDegrees, double fovTolerance,
                            double maximumKeyframeIntervalSeconds) {
        this(samplesPerSecond, maximumSamples, maximumKeyframes, positionTolerance, rotationToleranceDegrees,
                fovTolerance, maximumKeyframeIntervalSeconds, 0.0);
    }

    public SamplingSettings(int samplesPerSecond, int maximumSamples, int maximumKeyframes,
                            double positionTolerance, double rotationToleranceDegrees, double fovTolerance,
                            double maximumKeyframeIntervalSeconds, double cameraKeyframeIntervalSeconds) {
        this(samplesPerSecond, maximumSamples, maximumKeyframes, positionTolerance, rotationToleranceDegrees,
                fovTolerance, maximumKeyframeIntervalSeconds, cameraKeyframeIntervalSeconds,
                PathSmoothingSettings.defaults());
    }

    public SamplingSettings {
        if (pathSmoothing == null) pathSmoothing = PathSmoothingSettings.defaults();
    }

    public static SamplingSettings defaults() {
        return new SamplingSettings(12, 4096, 512, 0.40, 2.0, 1.0, 8.0, 1.0,
                PathSmoothingSettings.defaults());
    }
}
