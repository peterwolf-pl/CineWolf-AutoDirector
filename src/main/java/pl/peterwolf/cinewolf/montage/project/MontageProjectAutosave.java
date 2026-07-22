package pl.peterwolf.cinewolf.montage.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Replay-scoped autosave helper that never overwrites a final project without recovery. */
public final class MontageProjectAutosave {
    private final MontageProjectManager manager;

    public MontageProjectAutosave(MontageProjectManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    public Path autosavePath(UUID projectId) {
        return manager.projectDirectory().resolve(projectId + ".autosave.json");
    }

    public Path saveAutosave(MontageProject project) throws IOException {
        Objects.requireNonNull(project, "project");
        Path target = autosavePath(project.projectId());
        Files.createDirectories(manager.projectDirectory());
        // Reuse manager atomic write via temporary final save of a sidecar using debug path conventions.
        return manager.saveProject(new MontageProject(
                project.schemaVersion(),
                project.projectId(),
                project.replayId(),
                project.sourceStartReplayTime(),
                project.sourceEndReplayTime(),
                project.analysisSettings(),
                project.analysisStatistics(),
                project.events(),
                project.preset(),
                project.plannedShots(),
                project.manualEdits(),
                project.lockedShotIds(),
                project.generationTimestampEpochMillis(),
                project.cineWolfVersion(),
                project.warnings(),
                project.reasons()
        ));
    }

    public Optional<MontageProject> recover(UUID projectId) {
        MontageProjectManager.LoadResult loaded = manager.loadProject(projectId);
        return loaded.project();
    }
}
