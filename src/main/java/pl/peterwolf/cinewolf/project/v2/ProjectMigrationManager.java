package pl.peterwolf.cinewolf.project.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import pl.peterwolf.cinewolf.montage.project.MontageProject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Migrates 1.2/1.3 montage projects to CineWolf project schema v2 with backups. */
public final class ProjectMigrationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final ReplayIdentityResolverV2 identityResolver = new ReplayIdentityResolverV2();

    public MigrationResult migrateFile(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        String json = Files.readString(source, StandardCharsets.UTF_8);
        Path backup = source.resolveSibling(source.getFileName() + ".bak-" + System.currentTimeMillis());
        Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
        MigrationResult result = migrateJson(json);
        if (!result.success()) {
            return new MigrationResult(false, null, result.errors(), result.warnings(), backup, result.report());
        }
        Path target = source.resolveSibling(stripExtension(source.getFileName().toString()) + ".v2.json");
        Files.writeString(target, GSON.toJson(result.project()), StandardCharsets.UTF_8);
        return new MigrationResult(true, result.project(), List.of(), result.warnings(), backup,
                result.report() + "; wrote " + target.getFileName());
    }

    public MigrationResult migrateJson(String json) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (json == null || json.isBlank()) {
            errors.add("project.migration.empty");
            return MigrationResult.failed(errors);
        }
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException exception) {
            errors.add("project.migration.parse_failed");
            return MigrationResult.failed(errors);
        }
        if (root == null) {
            errors.add("project.migration.null_root");
            return MigrationResult.failed(errors);
        }
        int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
        if (schema == CineWolfProjectV2.CURRENT_SCHEMA) {
            try {
                CineWolfProjectV2 project = GSON.fromJson(json, CineWolfProjectV2.class);
                return new MigrationResult(true, project, List.of(), List.of("project.migration.already_v2"),
                        null, "already v2");
            } catch (RuntimeException exception) {
                errors.add("project.migration.v2_invalid");
                return MigrationResult.failed(errors);
            }
        }
        if (schema == 1 || schema == 0) {
            try {
                MontageProject legacy = GSON.fromJson(json, MontageProject.class);
                if (legacy == null) throw new IllegalArgumentException("null project");
                ReplayIdentity identity = identityResolver.resolve(
                        "replay-" + legacy.replayId(),
                        legacy.replayId().toString(),
                        legacy.sourceEndReplayTime() - legacy.sourceStartReplayTime(),
                        Instant.ofEpochMilli(legacy.generationTimestampEpochMillis()),
                        legacy.replayId().toString()
                );
                CineWolfProjectV2 migrated = CineWolfProjectV2.fromMontageProject(
                        legacy, identity, "0.41.1", "Migrated " + legacy.projectId());
                warnings.add("project.migration.from_schema_" + schema);
                return new MigrationResult(true, migrated, List.of(), warnings, null,
                        "migrated schema " + schema + " -> 2");
            } catch (RuntimeException exception) {
                errors.add("project.migration.legacy_invalid:" + exception.getMessage());
                return MigrationResult.failed(errors);
            }
        }
        errors.add("project.migration.unsupported_schema:" + schema);
        return MigrationResult.failed(errors);
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    public record MigrationResult(
            boolean success,
            CineWolfProjectV2 project,
            List<String> errors,
            List<String> warnings,
            Path backupPath,
            String report
    ) {
        public MigrationResult {
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
            report = report == null ? "" : report;
        }

        public static MigrationResult failed(List<String> errors) {
            return new MigrationResult(false, null, errors, List.of(), null, "failed");
        }
    }
}
