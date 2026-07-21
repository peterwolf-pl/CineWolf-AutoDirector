package pl.peterwolf.cinewolf.montage.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Local, atomic persistence for compact montage projects and optional diagnostic exports. */
public final class MontageProjectManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MontageProjectManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_PROJECT_BYTES = 8L * 1024L * 1024L;
    private static final String PROJECT_SUFFIX = ".json";
    private static final String DEBUG_SUFFIX = "-debug.json";

    private final Path projectDirectory;

    /** Uses {@code config/cinewolf-autodirector/montages}. */
    public MontageProjectManager() {
        this(FabricLoader.getInstance().getConfigDir()
                .resolve("cinewolf-autodirector")
                .resolve("montages"));
    }

    /** Explicit directory constructor for tests and alternate local storage roots. */
    public MontageProjectManager(Path projectDirectory) {
        this.projectDirectory = Objects.requireNonNull(projectDirectory, "projectDirectory")
                .toAbsolutePath().normalize();
    }

    public Path projectDirectory() {
        return projectDirectory;
    }

    public Path saveProject(MontageProject project) throws IOException {
        Objects.requireNonNull(project, "project");
        Path target = projectPath(project.projectId());
        writeAtomically(target, project);
        return target;
    }

    public Path saveDebugExport(MontageDebugExport export) throws IOException {
        Objects.requireNonNull(export, "export");
        Path target = resolveSafe(export.projectId() + DEBUG_SUFFIX);
        writeAtomically(target, export);
        return target;
    }

    public LoadResult loadProject(UUID projectId) {
        Objects.requireNonNull(projectId, "projectId");
        Path target = projectPath(projectId);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return LoadResult.empty("montage.project.not_found");
        }
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return LoadResult.empty("montage.project.unsafe_file");
        }
        try {
            if (Files.size(target) > MAX_PROJECT_BYTES) {
                return recoverMalformed(target, "montage.project.too_large", null);
            }
            MontageProject project = GSON.fromJson(Files.readString(target, StandardCharsets.UTF_8),
                    MontageProject.class);
            if (project == null) throw new IllegalArgumentException("Montage project root is null");
            if (!projectId.equals(project.projectId())) {
                throw new IllegalArgumentException("Montage project ID does not match its file name");
            }
            return new LoadResult(Optional.of(project), List.of(), Optional.empty());
        } catch (Exception exception) {
            return recoverMalformed(target, "montage.project.malformed", exception);
        }
    }

    /** String overload intentionally accepts only canonical UUIDs and never treats input as a path. */
    public LoadResult loadProject(String projectId) {
        Optional<UUID> parsed = canonicalUuid(projectId);
        return parsed.map(this::loadProject).orElseGet(() -> LoadResult.empty("montage.project.unsafe_id"));
    }

    private void writeAtomically(Path target, Object value) throws IOException {
        Files.createDirectories(projectDirectory);
        if (!Files.isDirectory(projectDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Montage project path is not a directory");
        }
        String json = GSON.toJson(value);
        Path temporary = Files.createTempFile(projectDirectory, ".cinewolf-", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            moveReplacing(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private LoadResult recoverMalformed(Path source, String warning, Exception exception) {
        if (exception != null) LOGGER.warn("Unable to load montage project {}; quarantining it", source, exception);
        Path quarantined = null;
        try {
            quarantined = nextBrokenPath(source);
            moveWithoutReplacing(source, quarantined);
        } catch (IOException quarantineError) {
            LOGGER.warn("Unable to quarantine malformed montage project {}", source, quarantineError);
            return new LoadResult(Optional.empty(), List.of(warning, "montage.project.quarantine_failed"),
                    Optional.empty());
        }
        return new LoadResult(Optional.empty(), List.of(warning), Optional.of(quarantined));
    }

    private Path nextBrokenPath(Path source) throws IOException {
        Path first = resolveSafe(source.getFileName() + ".broken");
        if (!Files.exists(first, LinkOption.NOFOLLOW_LINKS)) return first;
        for (int index = 1; index < 10_000; index++) {
            Path candidate = resolveSafe(source.getFileName() + ".broken." + index);
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate;
        }
        throw new IOException("Too many quarantined montage project files");
    }

    private Path projectPath(UUID projectId) {
        return resolveSafe(projectId.toString() + PROJECT_SUFFIX);
    }

    private Path resolveSafe(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
                || fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("Unsafe montage project file name");
        }
        Path resolved = projectDirectory.resolve(fileName).normalize();
        if (!projectDirectory.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Montage project path escapes its storage directory");
        }
        return resolved;
    }

    private static Optional<UUID> canonicalUuid(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            UUID parsed = UUID.fromString(normalized);
            return parsed.toString().equals(normalized) ? Optional.of(parsed) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void moveWithoutReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    public record LoadResult(
            Optional<MontageProject> project,
            List<String> warnings,
            Optional<Path> quarantinedFile
    ) {
        public LoadResult {
            project = Objects.requireNonNullElse(project, Optional.empty());
            warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
            quarantinedFile = Objects.requireNonNullElse(quarantinedFile, Optional.empty());
        }

        public static LoadResult empty(String warning) {
            return new LoadResult(Optional.empty(), List.of(warning), Optional.empty());
        }

        public boolean loaded() {
            return project.isPresent();
        }
    }
}
