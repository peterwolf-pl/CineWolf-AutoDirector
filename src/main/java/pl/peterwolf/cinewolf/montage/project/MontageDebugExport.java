package pl.peterwolf.cinewolf.montage.project;

import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.MontagePlanStatistics;
import pl.peterwolf.cinewolf.montage.plan.MontageTimeMapping;
import pl.peterwolf.cinewolf.montage.plan.MontageTransition;
import pl.peterwolf.cinewolf.montage.plan.MontageTransitionType;
import pl.peterwolf.cinewolf.montage.scene.ReplayScene;
import pl.peterwolf.cinewolf.montage.scene.SceneType;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Optional, local-only diagnostic snapshot. It intentionally contains no raw replay sample arrays. */
public record MontageDebugExport(
        int schemaVersion,
        UUID projectId,
        UUID replayId,
        long exportedAtEpochMillis,
        String cineWolfVersion,
        MontageProject.AnalysisSettingsSummary analysisSettings,
        MontageProject.AnalysisStatisticsSummary analysisStatistics,
        List<MontageProject.EventSummary> events,
        List<SceneSummary> scenes,
        PlanSummary plan,
        List<MontageProject.DiagnosticSummary> warnings,
        List<String> reasons
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public MontageDebugExport {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported montage debug schema " + schemaVersion);
        }
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(replayId, "replayId");
        exportedAtEpochMillis = Math.max(0L, exportedAtEpochMillis);
        cineWolfVersion = Objects.requireNonNull(cineWolfVersion, "cineWolfVersion");
        Objects.requireNonNull(analysisSettings, "analysisSettings");
        Objects.requireNonNull(analysisStatistics, "analysisStatistics");
        events = List.copyOf(Objects.requireNonNullElse(events, List.of()));
        scenes = List.copyOf(Objects.requireNonNullElse(scenes, List.of()));
        Objects.requireNonNull(plan, "plan");
        warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
        reasons = List.copyOf(Objects.requireNonNullElse(reasons, List.of()));
    }

    public static MontageDebugExport capture(MontageProject project, ReplayAnalysisResult analysis,
                                              MontagePlan plan, Instant exportedAt) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(exportedAt, "exportedAt");
        if (!project.projectId().equals(plan.montageId())) {
            throw new IllegalArgumentException("Debug plan does not belong to the montage project");
        }
        List<SceneSummary> scenes = analysis.scenes().stream().map(SceneSummary::from)
                .sorted(Comparator.comparingLong(SceneSummary::startReplayTime)
                        .thenComparing(scene -> scene.sceneId().toString()))
                .toList();
        return new MontageDebugExport(CURRENT_SCHEMA_VERSION, project.projectId(), project.replayId(),
                exportedAt.toEpochMilli(), CineWolfAutoDirector.VERSION, project.analysisSettings(),
                project.analysisStatistics(), project.events(), scenes, PlanSummary.from(plan, project.plannedShots()),
                project.warnings(), project.reasons());
    }

    public static MontageDebugExport capture(MontageProject project, ReplayAnalysisResult analysis,
                                              MontagePlan plan) {
        return capture(project, analysis, plan, Instant.now());
    }

    public record SceneSummary(
            UUID sceneId,
            long startReplayTime,
            long endReplayTime,
            SceneType type,
            List<UUID> primaryTargetIds,
            MontageProject.PositionSummary center,
            double spatialRadius,
            double importanceScore,
            List<UUID> eventIds
    ) {
        public SceneSummary {
            Objects.requireNonNull(sceneId, "sceneId");
            if (startReplayTime < 0 || endReplayTime < startReplayTime) {
                throw new IllegalArgumentException("Invalid debug scene range");
            }
            Objects.requireNonNull(type, "type");
            primaryTargetIds = Objects.requireNonNullElse(primaryTargetIds, List.<UUID>of()).stream()
                    .filter(Objects::nonNull).distinct().sorted(Comparator.comparing(UUID::toString)).toList();
            Objects.requireNonNull(center, "center");
            spatialRadius = finiteNonNegative(spatialRadius);
            importanceScore = finiteNonNegative(importanceScore);
            eventIds = Objects.requireNonNullElse(eventIds, List.<UUID>of()).stream().filter(Objects::nonNull)
                    .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        }

        static SceneSummary from(ReplayScene scene) {
            return new SceneSummary(scene.sceneId(), scene.startReplayTime(), scene.endReplayTime(), scene.type(),
                    scene.primaryTargets().stream().map(target -> target.uuid()).toList(),
                    MontageProject.PositionSummary.from(scene.center()), scene.spatialRadius(), scene.importanceScore(),
                    scene.events().stream().map(event -> event.eventId()).toList());
        }
    }

    public record PlanSummary(
            UUID montageId,
            String presetId,
            long sourceStartReplayTime,
            long sourceEndReplayTime,
            double outputDurationSeconds,
            PlanStatisticsSummary statistics,
            List<MontageProject.PlannedShotSummary> shots,
            List<TransitionSummary> transitions,
            List<TimeMappingSummary> timeMappings
    ) {
        public PlanSummary {
            Objects.requireNonNull(montageId, "montageId");
            presetId = Objects.requireNonNull(presetId, "presetId");
            if (sourceStartReplayTime < 0 || sourceEndReplayTime <= sourceStartReplayTime) {
                throw new IllegalArgumentException("Invalid debug plan source range");
            }
            outputDurationSeconds = positive(outputDurationSeconds, 1.0);
            Objects.requireNonNull(statistics, "statistics");
            shots = List.copyOf(Objects.requireNonNullElse(shots, List.of()));
            transitions = List.copyOf(Objects.requireNonNullElse(transitions, List.of()));
            timeMappings = List.copyOf(Objects.requireNonNullElse(timeMappings, List.of()));
        }

        static PlanSummary from(MontagePlan plan, List<MontageProject.PlannedShotSummary> shots) {
            return new PlanSummary(plan.montageId(), plan.preset().id(), plan.sourceStartReplayTime(),
                    plan.sourceEndReplayTime(), plan.outputDurationSeconds(),
                    PlanStatisticsSummary.from(plan.statistics()), shots,
                    plan.transitions().stream().map(TransitionSummary::from).toList(),
                    plan.timeMappings().stream().map(TimeMappingSummary::from).toList());
        }
    }

    public record PlanStatisticsSummary(
            int plannedShotCount,
            int enabledShotCount,
            int coveredEventCount,
            int distinctShotTypeCount,
            int distinctTargetCount,
            double plannedOutputDurationSeconds,
            double shotDiversityScore
    ) {
        public PlanStatisticsSummary {
            plannedShotCount = Math.max(0, plannedShotCount);
            enabledShotCount = Math.max(0, enabledShotCount);
            coveredEventCount = Math.max(0, coveredEventCount);
            distinctShotTypeCount = Math.max(0, distinctShotTypeCount);
            distinctTargetCount = Math.max(0, distinctTargetCount);
            plannedOutputDurationSeconds = finiteNonNegative(plannedOutputDurationSeconds);
            shotDiversityScore = finiteNonNegative(shotDiversityScore);
        }

        static PlanStatisticsSummary from(MontagePlanStatistics statistics) {
            return new PlanStatisticsSummary(statistics.plannedShotCount(), statistics.enabledShotCount(),
                    statistics.coveredEventCount(), statistics.distinctShotTypeCount(),
                    statistics.distinctTargetCount(), statistics.plannedOutputDurationSeconds(),
                    statistics.shotDiversityScore());
        }
    }

    public record TransitionSummary(
            UUID fromShotId,
            UUID toShotId,
            MontageTransitionType type,
            double outputTimeSeconds,
            List<String> planningReasons
    ) {
        public TransitionSummary {
            Objects.requireNonNull(fromShotId, "fromShotId");
            Objects.requireNonNull(toShotId, "toShotId");
            Objects.requireNonNull(type, "type");
            outputTimeSeconds = finiteNonNegative(outputTimeSeconds);
            planningReasons = List.copyOf(Objects.requireNonNullElse(planningReasons, List.of()));
        }

        static TransitionSummary from(MontageTransition transition) {
            return new TransitionSummary(transition.fromShotId(), transition.toShotId(), transition.type(),
                    transition.outputTimeSeconds(), transition.planningReasons());
        }
    }

    public record TimeMappingSummary(
            double outputStartSeconds,
            double outputEndSeconds,
            long replayStartTime,
            long replayEndTime,
            double playbackSpeed
    ) {
        public TimeMappingSummary {
            outputStartSeconds = finiteNonNegative(outputStartSeconds);
            outputEndSeconds = Math.max(outputStartSeconds, finiteNonNegative(outputEndSeconds));
            replayStartTime = Math.max(0L, replayStartTime);
            replayEndTime = Math.max(replayStartTime, replayEndTime);
            playbackSpeed = positive(playbackSpeed, 1.0);
        }

        static TimeMappingSummary from(MontageTimeMapping mapping) {
            return new TimeMappingSummary(mapping.outputStartSeconds(), mapping.outputEndSeconds(),
                    mapping.replayStartTime(), mapping.replayEndTime(), mapping.playbackSpeed());
        }
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }
}
