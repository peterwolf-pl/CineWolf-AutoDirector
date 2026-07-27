package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.montage.plan.ReplaySourceSegment;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetType;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.ArrayList;
import java.util.List;

/** Mutable, Gson-friendly user overrides for the data-driven montage presets. */
public final class MontageConfig {
    public MontagePresetType presetType = MontagePresetType.THIRTY_SECONDS;
    public double outputDurationSeconds = 30.0;
    public OutputAspectRatio aspectRatio = OutputAspectRatio.LANDSCAPE_16_9;
    public MontagePacing pacing = MontagePacing.MODERATE;
    public boolean automaticTargetDetection = true;
    /**
     * When true, prefer {@code THIRD_PERSON}: peer-level side camera at head height.
     * No second player is required.
     */
    public boolean thirdPersonTracking = false;
    /**
     * Global eye-height offset for {@code THIRD_PERSON} (blocks relative to head/eyes).
     * {@code 0} = exact head level, negative = lower (e.g. {@code -1.25}). Applied when planning
     * and when generating third-person paths. Range roughly {@code -2.25} … {@code 2.25}.
     */
    public double thirdPersonHeight = 0.0;
    /**
     * When true, burn a slightly transparent CineWolf icon (TV-style bug) into the top-right of
     * AutoDirector montage previews/exports.
     */
    public boolean exportWatermark = true;
    /** Watermark opacity in {@code [0.15, 0.95]}. Default ~0.55. */
    public double exportWatermarkOpacity = 0.55;
    /**
     * Relative size of the top-right TV-style logo bug.
     * {@code 1.0} = default (~1/18 of frame width); range {@code [0.4, 2.5]}.
     */
    public double exportWatermarkScale = 1.0;
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
    /** @deprecated Prefer {@link #obstacleHandling}; kept for config migration. */
    @Deprecated
    public boolean collisionAvoidance = true;
    /** AVOID = move camera; CLIP = hide occluders; NONE = neither. */
    public String obstacleHandling = ObstacleHandlingMode.AVOID.name();
    /** When CLIP is active, also hide entities that sit on the camera→subject ray. */
    public boolean clipEntities = true;
    public int coarseSamplesPerSecond = 4;
    /** Samples/s inside active windows. Lower = much faster analysis on long replays. */
    public int detailedSamplesPerSecond = 10;
    /**
     * Hard cap on additional seeks performed in the detailed-sampling phase.
     * Each sample needs ~2 client ticks + seek cost, so this dominates wall-clock time.
     */
    public int maximumDetailedSamples = 360;
    /**
     * Maximum total duration of detailed windows as a fraction of the analyzed source
     * (0.05–1.0). Prevents near-continuous activity from detailing the whole replay.
     */
    public double maximumDetailedCoverageFraction = 0.35;
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
    /** Montage Engine 2.0 style profile id (see MontageStyleProfiles). */
    public String montageStyleProfileId = "clean_cinematic";
    /** Exclude weak events from default montage generation. */
    public boolean excludeWeakEvents = true;
    /** Redact player names and absolute paths in debug export. */
    public boolean redactDebugExport = true;
    /** Local community preset library enabled. */
    public boolean communityPresetLibraryEnabled = true;
    /** Debounced project autosave interval in milliseconds. */
    public int projectAutosaveDebounceMillis = 1500;
    public DetectorThresholdConfig detectorThresholds = new DetectorThresholdConfig();
    public EventScoringConfig eventScoring = new EventScoringConfig();
    public ShotDiversityConfig shotDiversity = new ShotDiversityConfig();
    public MontageShotSettings shotSettings = new MontageShotSettings();
    /**
     * Optional discontinuous source windows (e.g. 10s start + 10s middle + 10s end).
     * Empty means "use the single Flashback In/Out selection".
     */
    public List<SourceSegmentConfig> sourceSegments = new ArrayList<>();

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
        thirdPersonHeight = clamp(thirdPersonHeight, 0.0, -2.25, 2.25);
        exportWatermarkOpacity = clamp(exportWatermarkOpacity, 0.55, 0.15, 0.95);
        exportWatermarkScale = clamp(exportWatermarkScale, 1.0, 0.4, 2.5);
        coarseSamplesPerSecond = Math.max(2, Math.min(5, coarseSamplesPerSecond));
        detailedSamplesPerSecond = Math.max(4, Math.min(20, detailedSamplesPerSecond));
        maximumDetailedSamples = Math.max(0, Math.min(4_000, maximumDetailedSamples));
        maximumDetailedCoverageFraction = clamp(maximumDetailedCoverageFraction, 0.35, 0.05, 1.0);
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
        ObstacleHandlingMode mode = ObstacleHandlingMode.parse(obstacleHandling, null);
        if (mode == null) {
            mode = ObstacleHandlingMode.fromLegacy(collisionAvoidance);
        }
        obstacleHandling = mode.name();
        // Keep legacy boolean in sync for any external readers / older UI paths.
        collisionAvoidance = mode.adjustsCameraPath();
        if (montageStyleProfileId == null || montageStyleProfileId.isBlank()) {
            montageStyleProfileId = "clean_cinematic";
        }
        projectAutosaveDebounceMillis = Math.max(250, Math.min(30_000, projectAutosaveDebounceMillis));
        if (detectorThresholds == null) detectorThresholds = new DetectorThresholdConfig();
        if (eventScoring == null) eventScoring = new EventScoringConfig();
        if (shotDiversity == null) shotDiversity = new ShotDiversityConfig();
        if (shotSettings == null) shotSettings = new MontageShotSettings();
        if (sourceSegments == null) sourceSegments = new ArrayList<>();
        sourceSegments.removeIf(segment -> segment == null);
        sourceSegments.forEach(SourceSegmentConfig::normalize);
        detectorThresholds.normalize();
        eventScoring.normalize();
        shotDiversity.normalize();
        shotSettings.normalize();
    }

    public ObstacleHandlingMode obstacleHandling() {
        return ObstacleHandlingMode.parse(obstacleHandling, ObstacleHandlingMode.AVOID);
    }

    public void setObstacleHandling(ObstacleHandlingMode mode) {
        ObstacleHandlingMode resolved = mode == null ? ObstacleHandlingMode.AVOID : mode;
        obstacleHandling = resolved.name();
        collisionAvoidance = resolved.adjustsCameraPath();
    }

    /** Normalized multi-region source windows, or empty when the Flashback selection should be used. */
    public List<ReplaySourceSegment> resolvedSourceSegments() {
        normalize();
        if (sourceSegments.isEmpty()) return List.of();
        List<ReplaySourceSegment> models = new ArrayList<>(sourceSegments.size());
        for (SourceSegmentConfig segment : sourceSegments) {
            try {
                models.add(segment.toModel());
            } catch (RuntimeException ignored) {
                // Drop invalid persisted segments rather than failing config load.
            }
        }
        return ReplaySourceSegment.normalize(models);
    }

    public void setSourceSegments(List<ReplaySourceSegment> segments) {
        sourceSegments = new ArrayList<>();
        if (segments == null) return;
        for (ReplaySourceSegment segment : ReplaySourceSegment.normalize(segments)) {
            sourceSegments.add(SourceSegmentConfig.from(segment));
        }
    }

    public void clearSourceSegments() {
        sourceSegments = new ArrayList<>();
    }

    public void addSourceSegment(long startTick, long endTick, String label) {
        if (sourceSegments == null) sourceSegments = new ArrayList<>();
        SourceSegmentConfig segment = new SourceSegmentConfig(startTick, endTick, label);
        segment.normalize();
        sourceSegments.add(segment);
        normalize();
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
