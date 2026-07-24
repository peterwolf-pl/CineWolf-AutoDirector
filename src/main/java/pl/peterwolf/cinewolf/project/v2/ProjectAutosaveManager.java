package pl.peterwolf.cinewolf.project.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Debounced local autosave with previous + recovery copies. */
public final class ProjectAutosaveManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path directory;
    private final long debounceMillis;
    private final AtomicLong lastWriteAt = new AtomicLong(0L);
    private CineWolfProjectV2 pending;

    public ProjectAutosaveManager(Path directory) {
        this(directory, 1500L);
    }

    public ProjectAutosaveManager(Path directory, long debounceMillis) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.debounceMillis = Math.max(250L, debounceMillis);
    }

    public synchronized void markDirty(CineWolfProjectV2 project) {
        this.pending = Objects.requireNonNull(project, "project");
    }

    public synchronized Optional<Path> maybeFlush() throws IOException {
        if (pending == null) return Optional.empty();
        long now = System.currentTimeMillis();
        if (now - lastWriteAt.get() < debounceMillis) return Optional.empty();
        return Optional.of(forceFlush());
    }

    public synchronized Path forceFlush() throws IOException {
        if (pending == null) throw new IllegalStateException("No pending project autosave");
        Files.createDirectories(directory);
        Path current = directory.resolve("autosave-current.json");
        Path previous = directory.resolve("autosave-previous.json");
        Path recovery = directory.resolve("autosave-recovery.json");
        if (Files.exists(current)) {
            Files.copy(current, previous, StandardCopyOption.REPLACE_EXISTING);
        }
        writeAtomic(current, pending);
        Files.copy(current, recovery, StandardCopyOption.REPLACE_EXISTING);
        lastWriteAt.set(System.currentTimeMillis());
        return current;
    }

    public Optional<CineWolfProjectV2> loadLatest() {
        Path current = directory.resolve("autosave-current.json");
        Path recovery = directory.resolve("autosave-recovery.json");
        Path previous = directory.resolve("autosave-previous.json");
        for (Path path : List.of(current, recovery, previous)) {
            Optional<CineWolfProjectV2> loaded = read(path);
            if (loaded.isPresent()) return loaded;
        }
        return Optional.empty();
    }

    private Optional<CineWolfProjectV2> read(Path path) {
        try {
            if (!Files.isRegularFile(path)) return Optional.empty();
            CineWolfProjectV2 project = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8),
                    CineWolfProjectV2.class);
            return Optional.ofNullable(project);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private void writeAtomic(Path target, CineWolfProjectV2 project) throws IOException {
        Path temporary = Files.createTempFile(directory, ".autosave-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(project), StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
