package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotType;

public final class CineWolfConfig {
    public static final int CURRENT_VERSION = 4;

    public int version = CURRENT_VERSION;
    public boolean previewVisible = true;
    public ShotType shotType = ShotType.ORBIT;
    public double diameter = 12.0;
    public double height = 3.0;
    public double distance = 8.0;
    public double startDistance = 16.0;
    public double endDistance = 3.0;
    public double rpm = 0.5;
    public double durationSeconds = 10.0;
    public double startAngleDegrees = 0.0;
    public RotationDirection direction = RotationDirection.CLOCKWISE;
    public double cameraSpeed = 4.0;
    public double fov = 70.0;
    public EasingType easing = EasingType.SMOOTHERSTEP;
    public double lookAheadSeconds = 0.2;
    public int samplesPerSecond = 12;
    public int maximumSamples = 4096;
    public int maximumKeyframes = 512;
    public double positionTolerance = 0.08;
    public double rotationToleranceDegrees = 0.45;
    public double fovTolerance = 0.1;
    public double maximumKeyframeIntervalSeconds = 1.0;
    public PathSmoothingConfig pathSmoothing = new PathSmoothingConfig();
    public MontageConfig montage = new MontageConfig();

    public void normalize() {
        version = CURRENT_VERSION;
        if (shotType == null) shotType = ShotType.ORBIT;
        if (direction == null) direction = RotationDirection.CLOCKWISE;
        if (easing == null) easing = EasingType.SMOOTHERSTEP;
        samplesPerSecond = Math.max(8, Math.min(20, samplesPerSecond));
        maximumSamples = Math.max(64, Math.min(20_000, maximumSamples));
        maximumKeyframes = Math.max(16, Math.min(2_000, maximumKeyframes));
        positionTolerance = positiveOr(positionTolerance, 0.08);
        rotationToleranceDegrees = positiveOr(rotationToleranceDegrees, 0.45);
        fovTolerance = positiveOr(fovTolerance, 0.1);
        maximumKeyframeIntervalSeconds = positiveOr(maximumKeyframeIntervalSeconds, 1.0);
        if (pathSmoothing == null) pathSmoothing = new PathSmoothingConfig();
        pathSmoothing.normalize();
        if (montage == null) montage = new MontageConfig();
        montage.normalize();
    }

    public SamplingSettings samplingSettings() {
        normalize();
        return new SamplingSettings(samplesPerSecond, maximumSamples, maximumKeyframes, positionTolerance,
                rotationToleranceDegrees, fovTolerance, maximumKeyframeIntervalSeconds, pathSmoothing.settings());
    }

    public void resetFor(ShotType type) {
        shotType = type;
        diameter = 12.0;
        height = 3.0;
        distance = 8.0;
        startDistance = 16.0;
        endDistance = 3.0;
        rpm = 0.5;
        durationSeconds = 10.0;
        startAngleDegrees = 0.0;
        direction = switch (type) {
            case FLYBY, SIDE_TRACKING, REVEAL, VEHICLE_PROFILE -> RotationDirection.LEFT_TO_RIGHT;
            default -> RotationDirection.CLOCKWISE;
        };
        cameraSpeed = type == ShotType.CHASE ? 6.0 : type == ShotType.STATIC_TRACKING ? 2.0 : 4.0;
        fov = type == ShotType.CLOSE_DETAIL ? 45.0 : 70.0;
        easing = EasingType.SMOOTHERSTEP;
        lookAheadSeconds = type == ShotType.CHASE ? 0.35 : 0.2;
        if (type == ShotType.SPIRAL) {
            diameter = 16.0;
            startDistance = 10.0;
            endDistance = 4.0;
            rpm = 0.75;
            height = 4.0;
        } else if (type == ShotType.CRANE_UP || type == ShotType.CRANE_DOWN) {
            distance = 10.0;
            height = type == ShotType.CRANE_UP ? 8.0 : 2.5;
            startDistance = type == ShotType.CRANE_UP ? 2.0 : 10.0;
            endDistance = type == ShotType.CRANE_UP ? 10.0 : 2.0;
        } else if (type == ShotType.CLOSE_DETAIL) {
            distance = 1.8;
            height = 1.2;
        } else if (type == ShotType.CHASE) {
            distance = 10.0;
            height = 2.5;
        } else if (type == ShotType.VEHICLE_PROFILE) {
            distance = 12.0;
            height = 2.0;
        } else if (type == ShotType.REVEAL) {
            startDistance = 14.0;
            endDistance = 6.0;
            distance = 10.0;
            height = 3.0;
        }
    }

    private static double positiveOr(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
