package pl.peterwolf.cinewolf.montage.project;

import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisWarning;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisRequest;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisStatistics;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.MontageWarning;
import pl.peterwolf.cinewolf.montage.plan.PlannedMontageShot;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Compact, replay-independent persistence model for an editable montage project.
 * Raw replay samples and movement-metric arrays are deliberately excluded.
 */
public record MontageProject(
        int schemaVersion,
        UUID projectId,
        UUID replayId,
        long sourceStartReplayTime,
        long sourceEndReplayTime,
        AnalysisSettingsSummary analysisSettings,
        AnalysisStatisticsSummary analysisStatistics,
        List<EventSummary> events,
        PresetSummary preset,
        List<PlannedShotSummary> plannedShots,
        List<ManualEditSummary> manualEdits,
        List<UUID> lockedShotIds,
        long generationTimestampEpochMillis,
        String cineWolfVersion,
        List<DiagnosticSummary> warnings,
        List<String> reasons
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public MontageProject {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported montage project schema " + schemaVersion);
        }
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(replayId, "replayId");
        if (sourceStartReplayTime < 0 || sourceEndReplayTime <= sourceStartReplayTime) {
            throw new IllegalArgumentException("Montage project source range must move forwards");
        }
        Objects.requireNonNull(analysisSettings, "analysisSettings");
        Objects.requireNonNull(analysisStatistics, "analysisStatistics");
        events = List.copyOf(Objects.requireNonNullElse(events, List.of()));
        Objects.requireNonNull(preset, "preset");
        plannedShots = List.copyOf(Objects.requireNonNullElse(plannedShots, List.of()));
        manualEdits = List.copyOf(Objects.requireNonNullElse(manualEdits, List.of()));
        lockedShotIds = Objects.requireNonNullElse(lockedShotIds, List.<UUID>of()).stream()
                .filter(Objects::nonNull).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        generationTimestampEpochMillis = Math.max(0L, generationTimestampEpochMillis);
        cineWolfVersion = requireText(cineWolfVersion, "cineWolfVersion");
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
        reasons = normalizedStrings(reasons);
    }

    public static MontageProject capture(UUID replayId, ReplayAnalysisResult analysis, MontagePlan plan,
                                         List<ManualEditSummary> manualEdits, Instant generatedAt) {
        Objects.requireNonNull(replayId, "replayId");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(generatedAt, "generatedAt");
        if (plan.sourceStartReplayTime() < analysis.request().startReplayTime()
                || plan.sourceEndReplayTime() > analysis.request().endReplayTime()) {
            throw new IllegalArgumentException("Montage plan lies outside the analyzed replay range");
        }

        List<EventSummary> events = analysis.rankedEvents().stream().map(EventSummary::from)
                .sorted(Comparator.comparingDouble(EventSummary::finalScore).reversed()
                        .thenComparingLong(EventSummary::peakReplayTime)
                        .thenComparing(event -> event.eventId().toString()))
                .toList();
        List<PlannedShotSummary> shots = plan.shots().stream().map(PlannedShotSummary::from)
                .sorted(Comparator.comparingInt(PlannedShotSummary::order)
                        .thenComparing(shot -> shot.shotId().toString()))
                .toList();
        List<UUID> locked = shots.stream().filter(PlannedShotSummary::locked)
                .map(PlannedShotSummary::shotId).sorted(Comparator.comparing(UUID::toString)).toList();
        List<DiagnosticSummary> diagnostics = new ArrayList<>();
        analysis.warnings().stream().map(DiagnosticSummary::from).forEach(diagnostics::add);
        plan.warnings().stream().map(DiagnosticSummary::from).forEach(diagnostics::add);
        shots.stream().flatMap(shot -> shot.warnings().stream()).forEach(diagnostics::add);
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        events.forEach(event -> reasons.addAll(event.scoringReasons()));
        shots.forEach(shot -> reasons.addAll(shot.planningReasons()));

        return new MontageProject(CURRENT_SCHEMA_VERSION, plan.montageId(), replayId,
                plan.sourceStartReplayTime(), plan.sourceEndReplayTime(),
                AnalysisSettingsSummary.from(analysis.request()), AnalysisStatisticsSummary.from(analysis.statistics()),
                events, PresetSummary.from(plan.preset()), shots, manualEdits, locked,
                generatedAt.toEpochMilli(), CineWolfAutoDirector.VERSION, diagnostics, List.copyOf(reasons));
    }

    public static MontageProject capture(UUID replayId, ReplayAnalysisResult analysis, MontagePlan plan,
                                         List<ManualEditSummary> manualEdits) {
        return capture(replayId, analysis, plan, manualEdits, Instant.now());
    }

    public record AnalysisSettingsSummary(
            long startReplayTime,
            long endReplayTime,
            List<TargetSummary> selectedTargets,
            boolean automaticTargetDetection,
            List<ReplayEventType> enabledEventTypes,
            double sensitivity,
            int coarseSamplesPerSecond,
            int detailedSamplesPerSecond
    ) {
        public AnalysisSettingsSummary {
            if (startReplayTime < 0 || endReplayTime <= startReplayTime) {
                throw new IllegalArgumentException("Analysis settings range must move forwards");
            }
            selectedTargets = Objects.requireNonNullElse(selectedTargets, List.<TargetSummary>of()).stream()
                    .filter(Objects::nonNull).sorted(Comparator.comparing(target -> target.uuid().toString())).toList();
            enabledEventTypes = Objects.requireNonNullElse(enabledEventTypes, List.<ReplayEventType>of()).stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
            sensitivity = clamp01(sensitivity);
            coarseSamplesPerSecond = Math.max(1, coarseSamplesPerSecond);
            detailedSamplesPerSecond = Math.max(coarseSamplesPerSecond, detailedSamplesPerSecond);
        }

        static AnalysisSettingsSummary from(ReplayAnalysisRequest request) {
            return new AnalysisSettingsSummary(request.startReplayTime(), request.endReplayTime(),
                    request.selectedTargets().stream().map(TargetSummary::from).toList(),
                    request.automaticTargetDetection(), List.copyOf(request.enabledEventTypes()),
                    request.sensitivity(), request.coarseSamplesPerSecond(), request.detailedSamplesPerSecond());
        }
    }

    public record AnalysisStatisticsSummary(
            long analyzedDurationTicks,
            int inputSampleCount,
            int coarseSampleCount,
            int detailedSampleCount,
            int analyzedSampleCount,
            int entityCount,
            int detectedEventCount,
            int mergedEventCount,
            int sceneCount,
            Map<ReplayEventType, Integer> eventCounts
    ) {
        public AnalysisStatisticsSummary {
            analyzedDurationTicks = Math.max(0L, analyzedDurationTicks);
            inputSampleCount = Math.max(0, inputSampleCount);
            coarseSampleCount = Math.max(0, coarseSampleCount);
            detailedSampleCount = Math.max(0, detailedSampleCount);
            analyzedSampleCount = Math.max(0, analyzedSampleCount);
            entityCount = Math.max(0, entityCount);
            detectedEventCount = Math.max(0, detectedEventCount);
            mergedEventCount = Math.max(0, mergedEventCount);
            sceneCount = Math.max(0, sceneCount);
            eventCounts = Map.copyOf(Objects.requireNonNullElse(eventCounts, Map.of()));
        }

        static AnalysisStatisticsSummary from(ReplayAnalysisStatistics statistics) {
            return new AnalysisStatisticsSummary(statistics.analyzedDurationTicks(), statistics.inputSampleCount(),
                    statistics.coarseSampleCount(), statistics.detailedSampleCount(), statistics.analyzedSampleCount(),
                    statistics.entityCount(), statistics.detectedEventCount(), statistics.mergedEventCount(),
                    statistics.sceneCount(), statistics.eventCounts());
        }
    }

    public record EventSummary(
            UUID eventId,
            ReplayEventType type,
            long startReplayTime,
            long peakReplayTime,
            long endReplayTime,
            List<UUID> targetIds,
            PositionSummary location,
            double magnitude,
            double confidence,
            EvidenceSummary evidence,
            double importanceScore,
            double cinematicScore,
            double uniquenessScore,
            double presetCompatibilityScore,
            double markerBonus,
            double targetBonus,
            double repetitionPenalty,
            double technicalRiskPenalty,
            double finalScore,
            List<String> scoringReasons
    ) {
        public EventSummary {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(type, "type");
            if (startReplayTime < 0 || peakReplayTime < startReplayTime || endReplayTime < peakReplayTime) {
                throw new IllegalArgumentException("Invalid persisted event range");
            }
            targetIds = Objects.requireNonNullElse(targetIds, List.<UUID>of()).stream().filter(Objects::nonNull)
                    .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
            Objects.requireNonNull(location, "location");
            magnitude = clamp01(magnitude);
            confidence = clamp01(confidence);
            Objects.requireNonNull(evidence, "evidence");
            importanceScore = finiteOrZero(importanceScore);
            cinematicScore = finiteOrZero(cinematicScore);
            uniquenessScore = finiteOrZero(uniquenessScore);
            presetCompatibilityScore = finiteOrZero(presetCompatibilityScore);
            markerBonus = finiteOrZero(markerBonus);
            targetBonus = finiteOrZero(targetBonus);
            repetitionPenalty = finiteOrZero(repetitionPenalty);
            technicalRiskPenalty = finiteOrZero(technicalRiskPenalty);
            finalScore = finiteOrZero(finalScore);
            scoringReasons = normalizedStrings(scoringReasons);
        }

        static EventSummary from(ScoredReplayEvent scored) {
            ReplayEvent event = scored.event();
            return new EventSummary(event.eventId(), event.type(), event.startReplayTime(), event.peakReplayTime(),
                    event.endReplayTime(), event.targets().stream().map(TargetReference::uuid).toList(),
                    PositionSummary.from(event.location()), event.magnitude(), event.confidence(),
                    EvidenceSummary.from(event.evidence()), scored.importanceScore(), scored.cinematicScore(),
                    scored.uniquenessScore(), scored.presetCompatibilityScore(), scored.markerBonus(),
                    scored.targetBonus(), scored.repetitionPenalty(), scored.technicalRiskPenalty(),
                    scored.finalScore(), scored.scoringReasons());
        }
    }

    public record EvidenceSummary(
            List<EventEvidence.DetectionSource> sources,
            List<MeasurementSummary> measurements,
            List<AttributeSummary> attributes,
            List<ReplayEventType> relatedTypes
    ) {
        public EvidenceSummary {
            sources = Objects.requireNonNullElse(sources, List.<EventEvidence.DetectionSource>of()).stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
            measurements = List.copyOf(Objects.requireNonNullElse(measurements, List.of()));
            attributes = List.copyOf(Objects.requireNonNullElse(attributes, List.of()));
            relatedTypes = Objects.requireNonNullElse(relatedTypes, List.<ReplayEventType>of()).stream()
                    .filter(Objects::nonNull).distinct().sorted().toList();
        }

        static EvidenceSummary from(EventEvidence evidence) {
            return new EvidenceSummary(List.copyOf(evidence.sources()),
                    evidence.measurements().stream().map(MeasurementSummary::from).toList(),
                    evidence.attributes().stream().map(AttributeSummary::from).toList(),
                    List.copyOf(evidence.relatedTypes()));
        }
    }

    public record MeasurementSummary(String name, double value, String unit,
                                     EventEvidence.Comparison comparison, double threshold) {
        public MeasurementSummary {
            name = requireText(name, "measurement name");
            value = finiteOrZero(value);
            unit = Objects.requireNonNullElse(unit, "");
            comparison = Objects.requireNonNullElse(comparison, EventEvidence.Comparison.NONE);
            threshold = finiteOrZero(threshold);
        }

        static MeasurementSummary from(EventEvidence.Measurement measurement) {
            return new MeasurementSummary(measurement.name(), measurement.value(), measurement.unit(),
                    measurement.comparison(), measurement.threshold());
        }
    }

    public record AttributeSummary(String name, String value) {
        public AttributeSummary {
            name = requireText(name, "attribute name");
            value = Objects.requireNonNullElse(value, "");
        }

        static AttributeSummary from(EventEvidence.Attribute attribute) {
            return new AttributeSummary(attribute.name(), attribute.value());
        }
    }

    public record PresetSummary(
            String id,
            double targetDurationSeconds,
            OutputAspectRatio aspectRatio,
            MontagePacing pacing,
            double minimumShotDuration,
            double maximumShotDuration,
            int minimumShotCount,
            int maximumShotCount
    ) {
        public PresetSummary {
            id = requireText(id, "preset id");
            targetDurationSeconds = positive(targetDurationSeconds, 1.0);
            Objects.requireNonNull(aspectRatio, "aspectRatio");
            Objects.requireNonNull(pacing, "pacing");
            minimumShotDuration = positive(minimumShotDuration, 0.5);
            maximumShotDuration = Math.max(minimumShotDuration, positive(maximumShotDuration, minimumShotDuration));
            minimumShotCount = Math.max(1, minimumShotCount);
            maximumShotCount = Math.max(minimumShotCount, maximumShotCount);
        }

        static PresetSummary from(MontagePreset preset) {
            return new PresetSummary(preset.id(), preset.targetDurationSeconds(), preset.aspectRatio(), preset.pacing(),
                    preset.minimumShotDuration(), preset.maximumShotDuration(), preset.minimumShotCount(),
                    preset.maximumShotCount());
        }
    }

    public record PlannedShotSummary(
            UUID shotId,
            int order,
            UUID sourceEventId,
            ReplayEventType sourceEventType,
            double sourceEventScore,
            TargetSummary target,
            ShotType shotType,
            FramingType framing,
            long sourceReplayStartTime,
            long sourceReplayEndTime,
            double outputStartSeconds,
            double outputDurationSeconds,
            double replaySpeed,
            ShotParametersSummary parameters,
            boolean enabled,
            boolean locked,
            List<String> planningReasons,
            List<DiagnosticSummary> warnings
    ) {
        public PlannedShotSummary {
            Objects.requireNonNull(shotId, "shotId");
            order = Math.max(0, order);
            Objects.requireNonNull(sourceEventId, "sourceEventId");
            Objects.requireNonNull(sourceEventType, "sourceEventType");
            sourceEventScore = finiteOrZero(sourceEventScore);
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(shotType, "shotType");
            Objects.requireNonNull(framing, "framing");
            if (sourceReplayStartTime < 0 || sourceReplayEndTime <= sourceReplayStartTime) {
                throw new IllegalArgumentException("Persisted shot source range must move forwards");
            }
            outputStartSeconds = finiteOrZero(outputStartSeconds);
            outputDurationSeconds = positive(outputDurationSeconds, 0.5);
            replaySpeed = positive(replaySpeed, 1.0);
            Objects.requireNonNull(parameters, "parameters");
            planningReasons = normalizedStrings(planningReasons);
            warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
        }

        static PlannedShotSummary from(PlannedMontageShot shot) {
            return new PlannedShotSummary(shot.shotId(), shot.order(), shot.sourceEvent().eventId(),
                    shot.sourceEvent().type(), shot.sourceEventScore(), TargetSummary.from(shot.target()),
                    shot.shotType(), shot.framing(), shot.sourceReplayStartTime(), shot.sourceReplayEndTime(),
                    shot.outputStartSeconds(), shot.outputDurationSeconds(), shot.replaySpeed(),
                    ShotParametersSummary.from(shot.shotRequest()), shot.enabled(), shot.locked(),
                    shot.planningReasons(), shot.warnings().stream().map(DiagnosticSummary::from).toList());
        }
    }

    public record ShotParametersSummary(
            double diameter,
            double height,
            double distance,
            double startDistance,
            double endDistance,
            double rpm,
            double durationSeconds,
            double startAngleDegrees,
            RotationDirection direction,
            double cameraSpeed,
            double fov,
            EasingType easing,
            double lookAheadSeconds
    ) {
        public ShotParametersSummary {
            diameter = finiteOrZero(diameter);
            height = finiteOrZero(height);
            distance = finiteOrZero(distance);
            startDistance = finiteOrZero(startDistance);
            endDistance = finiteOrZero(endDistance);
            rpm = finiteOrZero(rpm);
            durationSeconds = positive(durationSeconds, 0.5);
            startAngleDegrees = finiteOrZero(startAngleDegrees);
            Objects.requireNonNull(direction, "direction");
            cameraSpeed = finiteOrZero(cameraSpeed);
            fov = finiteOrZero(fov);
            Objects.requireNonNull(easing, "easing");
            lookAheadSeconds = finiteOrZero(lookAheadSeconds);
        }

        static ShotParametersSummary from(ShotRequest request) {
            return new ShotParametersSummary(request.diameter(), request.height(), request.distance(),
                    request.startDistance(), request.endDistance(), request.rpm(), request.durationSeconds(),
                    request.startAngleDegrees(), request.direction(), request.cameraSpeed(), request.fov(),
                    request.easing(), request.lookAheadSeconds());
        }
    }

    public record ManualEditSummary(
            UUID shotId,
            String field,
            String previousValue,
            String newValue,
            long editedAtEpochMillis
    ) {
        public ManualEditSummary {
            Objects.requireNonNull(shotId, "shotId");
            field = requireText(field, "edit field");
            previousValue = Objects.requireNonNullElse(previousValue, "");
            newValue = Objects.requireNonNullElse(newValue, "");
            editedAtEpochMillis = Math.max(0L, editedAtEpochMillis);
        }
    }

    public record TargetSummary(UUID uuid, String entityType, String displayName) {
        public TargetSummary {
            Objects.requireNonNull(uuid, "uuid");
            entityType = requireText(entityType, "entityType");
            displayName = Objects.requireNonNullElse(displayName, entityType);
        }

        static TargetSummary from(TargetReference target) {
            return new TargetSummary(target.uuid(), target.entityType(), target.displayName());
        }
    }

    public record PositionSummary(double x, double y, double z) {
        public PositionSummary {
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            z = finiteOrZero(z);
        }

        static PositionSummary from(Vec3d position) {
            return new PositionSummary(position.x(), position.y(), position.z());
        }
    }

    public record DiagnosticSummary(String code, String severity, List<String> arguments) {
        public DiagnosticSummary {
            code = requireText(code, "diagnostic code");
            severity = requireText(severity, "diagnostic severity");
            arguments = normalizedStrings(arguments);
        }

        static DiagnosticSummary from(AnalysisWarning warning) {
            return new DiagnosticSummary(warning.code(), warning.severity().name(), warning.arguments());
        }

        static DiagnosticSummary from(MontageWarning warning) {
            return new DiagnosticSummary(warning.code(), warning.severity().name(), warning.arguments());
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }

    private static List<String> normalizedStrings(List<String> values) {
        return Objects.requireNonNullElse(values, List.<String>of()).stream().filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static double clamp01(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
