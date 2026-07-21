package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetType;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

/** Mutable, Gson-friendly user overrides for the data-driven montage presets. */
public final class MontageConfig {
    public MontagePresetType presetType = MontagePresetType.THIRTY_SECONDS;
    public double outputDurationSeconds = 30.0;
    public OutputAspectRatio aspectRatio = OutputAspectRatio.LANDSCAPE_16_9;
    public MontagePacing pacing = MontagePacing.MODERATE;
    public boolean automaticTargetDetection = true;
    public double cameraMovementIntensity = 0.65;
    public double cutFrequency = 0.65;
    public double minimumShotDuration = 2.5;
    public double maximumShotDuration = 7.0;
    public double eventSensitivity = 0.6;
    public boolean includeReplayMarkers = true;
    public boolean includeCombat = true;
    public boolean includeBuildingEvents = true;
    public boolean includeVehicles = true;
    public boolean includeFlight = true;
    public boolean allowReplaySpeedChanges = false;
    public boolean preferChronologicalOrder = true;
    public boolean collisionAvoidance = true;
    public int coarseSamplesPerSecond = 4;
    public int detailedSamplesPerSecond = 16;
    public int maximumTrackedEntities = 16;
    public int maximumTotalSamples = 6_000;
    public int maximumDetectedEvents = 512;
    public int maximumPlannedShots = 16;
    public int maximumMontageKeyframes = 2_000;
    public double minimumReplaySpeed = 0.5;
    public double maximumReplaySpeed = 4.0;
    public double maximumReplaySpeedChange = 2.0;
    public double verticalSafeArea = 0.82;
    public boolean debugVisualization;
    public boolean debugJsonExport;
    public DetectorThresholdConfig detectorThresholds = new DetectorThresholdConfig();
    public EventScoringConfig eventScoring = new EventScoringConfig();
    public ShotDiversityConfig shotDiversity = new ShotDiversityConfig();

    public void applyPreset(MontagePreset preset) {
        outputDurationSeconds = preset.targetDurationSeconds();
        aspectRatio = preset.aspectRatio();
        pacing = preset.pacing();
        cameraMovementIntensity = preset.style().cameraMovementIntensity();
        cutFrequency = preset.style().cutFrequency();
        minimumShotDuration = preset.minimumShotDuration();
        maximumShotDuration = preset.maximumShotDuration();
        allowReplaySpeedChanges = preset.style().allowReplaySpeedChanges();
        preferChronologicalOrder = preset.style().preferChronologicalOrder();
        minimumReplaySpeed = preset.style().minimumReplaySpeed();
        maximumReplaySpeed = preset.style().maximumReplaySpeed();
        maximumReplaySpeedChange = preset.style().maximumReplaySpeedChange();
        normalize();
    }

    public void normalize() {
        if (presetType == null) presetType = MontagePresetType.THIRTY_SECONDS;
        MontagePreset preset = MontagePresetRegistry.createDefault().get(presetType)
                .orElseGet(() -> MontagePresetRegistry.createDefault().all().getFirst());
        if (aspectRatio == null) aspectRatio = preset.aspectRatio();
        if (pacing == null) pacing = preset.pacing();
        outputDurationSeconds = positiveOr(outputDurationSeconds, preset.targetDurationSeconds(), 1.0, 3_600.0);
        cameraMovementIntensity = clamp(cameraMovementIntensity, preset.style().cameraMovementIntensity(), 0.0, 1.0);
        cutFrequency = clamp(cutFrequency, preset.style().cutFrequency(), 0.0, 1.0);
        minimumShotDuration = positiveOr(minimumShotDuration, preset.minimumShotDuration(), 0.5, 120.0);
        maximumShotDuration = positiveOr(maximumShotDuration, preset.maximumShotDuration(), minimumShotDuration, 300.0);
        if (maximumShotDuration < minimumShotDuration) maximumShotDuration = minimumShotDuration;
        eventSensitivity = clamp(eventSensitivity, 0.6, 0.0, 1.0);
        coarseSamplesPerSecond = Math.max(2, Math.min(5, coarseSamplesPerSecond));
        detailedSamplesPerSecond = Math.max(10, Math.min(20, detailedSamplesPerSecond));
        maximumTrackedEntities = Math.max(1, Math.min(64, maximumTrackedEntities));
        maximumTotalSamples = Math.max(128, Math.min(50_000, maximumTotalSamples));
        maximumDetectedEvents = Math.max(32, Math.min(5_000, maximumDetectedEvents));
        maximumPlannedShots = Math.max(3, Math.min(64, maximumPlannedShots));
        maximumMontageKeyframes = Math.max(64, Math.min(20_000, maximumMontageKeyframes));
        minimumReplaySpeed = positiveOr(minimumReplaySpeed, preset.style().minimumReplaySpeed(), 0.05, 20.0);
        maximumReplaySpeed = positiveOr(maximumReplaySpeed, preset.style().maximumReplaySpeed(), minimumReplaySpeed, 40.0);
        if (maximumReplaySpeed < minimumReplaySpeed) maximumReplaySpeed = minimumReplaySpeed;
        maximumReplaySpeedChange = clamp(maximumReplaySpeedChange,
                preset.style().maximumReplaySpeedChange(), 0.0, 20.0);
        verticalSafeArea = clamp(verticalSafeArea, 0.82, 0.5, 0.98);
        if (detectorThresholds == null) detectorThresholds = new DetectorThresholdConfig();
        if (eventScoring == null) eventScoring = new EventScoringConfig();
        if (shotDiversity == null) shotDiversity = new ShotDiversityConfig();
        detectorThresholds.normalize();
        eventScoring.normalize();
        shotDiversity.normalize();
    }

    private static double positiveOr(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value) || value <= 0.0) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value)) value = fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
