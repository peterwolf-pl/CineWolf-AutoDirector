package pl.peterwolf.cinewolf.project.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ProjectRecoveryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectRecoveryManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path projectDirectory;
    private final ProjectAutosaveManager autosaveManager;
    private final ProjectMigrationManager migrationManager = new ProjectMigrationManager();

    public ProjectRecoveryManager(Path projectDirectory, ProjectAutosaveManager autosaveManager) {
        this.projectDirectory = Objects.requireNonNull(projectDirectory, "projectDirectory")
                .toAbsolutePath().normalize();
        this.autosaveManager = Objects.requireNonNull(autosaveManager, "autosaveManager");
    }

    public RecoveryResult recover() {
        List<String> warnings = new ArrayList<>();
        Optional<CineWolfProjectV2> autosave = autosaveManager.loadLatest();
        if (autosave.isPresent()) {
            return new RecoveryResult(true, autosave.get(), warnings, "autosave");
        }
        try {
            if (!Files.isDirectory(projectDirectory)) {
                return new RecoveryResult(false, null, List.of("project.recovery.no_directory"), "none");
            }
            try (var stream = Files.list(projectDirectory)) {
                Optional<Path> candidate = stream
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .findFirst();
                if (candidate.isEmpty()) {
                    return new RecoveryResult(false, null, List.of("project.recovery.none"), "none");
                }
                ProjectMigrationManager.MigrationResult migrated = migrationManager.migrateFile(candidate.get());
                if (!migrated.success()) {
                    return new RecoveryResult(false, null, migrated.errors(), "migration_failed");
                }
                warnings.addAll(migrated.warnings());
                return new RecoveryResult(true, migrated.project(), warnings, "migrated");
            }
        } catch (IOException exception) {
            LOGGER.warn("Project recovery failed", exception);
            return new RecoveryResult(false, null, List.of("project.recovery.io_error"), "error");
        }
    }

    public Path quarantine(Path corrupted) throws IOException {
        Path target = corrupted.resolveSibling(corrupted.getFileName() + ".broken-" + System.currentTimeMillis());
        Files.move(corrupted, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public record RecoveryResult(boolean success, CineWolfProjectV2 project, List<String> warnings, String source) {
        public RecoveryResult {
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
            source = source == null ? "none" : source;
        }
    }
}
