package pl.peterwolf.cinewolf.project.v2;

import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.montage.project.MontageProject;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CineWolfProjectV2(
        int schemaVersion,
        String cineWolfVersion,
        String flashbackVersion,
        UUID projectId,
        String projectName,
        ReplayIdentity replayIdentity,
        Instant createdAt,
        Instant modifiedAt,
        MontageProject.AnalysisSettingsSummary analysisSettings,
        MontageProject.AnalysisStatisticsSummary analysis,
        MontageProject.PresetSummary montagePreset,
        List<MontageProject.PlannedShotSummary> plannedShots,
        List<MontageProject.EventSummary> events,
        List<MontageProject.ManualEditSummary> shotEdits,
        Set<String> requiredIntegrations,
        List<UUID> lockedShotIds,
        ProjectTimelineState timelineState,
        ProjectUiState uiState,
        long sourceStartReplayTime,
        long sourceEndReplayTime,
        List<MontageProject.DiagnosticSummary> warnings,
        List<String> reasons
) {
    public static final int CURRENT_SCHEMA = 2;

    public CineWolfProjectV2 {
        if (schemaVersion <= 0) schemaVersion = CURRENT_SCHEMA;
        cineWolfVersion = Objects.requireNonNullElse(cineWolfVersion, CineWolfAutoDirector.VERSION);
        flashbackVersion = Objects.requireNonNullElse(flashbackVersion, "0.41.1");
        Objects.requireNonNull(projectId, "projectId");
        projectName = Objects.requireNonNullElse(projectName, "Untitled Project");
        Objects.requireNonNull(replayIdentity, "replayIdentity");
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        modifiedAt = modifiedAt == null ? createdAt : modifiedAt;
        plannedShots = List.copyOf(plannedShots == null ? List.of() : plannedShots);
        events = List.copyOf(events == null ? List.of() : events);
        shotEdits = List.copyOf(shotEdits == null ? List.of() : shotEdits);
        requiredIntegrations = Set.copyOf(requiredIntegrations == null ? Set.of() : requiredIntegrations);
        lockedShotIds = List.copyOf(lockedShotIds == null ? List.of() : lockedShotIds);
        timelineState = timelineState == null ? ProjectTimelineState.empty() : timelineState;
        uiState = uiState == null ? ProjectUiState.defaults() : uiState;
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
    }

    public static CineWolfProjectV2 fromMontageProject(MontageProject project, ReplayIdentity identity,
                                                       String flashbackVersion, String projectName) {
        Objects.requireNonNull(project, "project");
        return new CineWolfProjectV2(
                CURRENT_SCHEMA,
                project.cineWolfVersion(),
                flashbackVersion,
                project.projectId(),
                projectName,
                identity,
                Instant.ofEpochMilli(project.generationTimestampEpochMillis()),
                Instant.now(),
                project.analysisSettings(),
                project.analysisStatistics(),
                project.preset(),
                project.plannedShots(),
                project.events(),
                project.manualEdits(),
                Set.of(),
                project.lockedShotIds(),
                new ProjectTimelineState(project.sourceStartReplayTime(), project.sourceEndReplayTime(),
                        project.sourceStartReplayTime(), true),
                ProjectUiState.defaults(),
                project.sourceStartReplayTime(),
                project.sourceEndReplayTime(),
                project.warnings(),
                project.reasons()
        );
    }
}
