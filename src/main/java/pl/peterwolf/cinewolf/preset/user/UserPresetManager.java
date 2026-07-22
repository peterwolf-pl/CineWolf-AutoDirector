package pl.peterwolf.cinewolf.preset.user;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Local user-preset store with schema validation, checksums, and built-in protection. */
public final class UserPresetManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPresetManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_BYTES = 512L * 1024L;

    private final Path directory;
    private final MontagePresetRegistry builtInRegistry;
    private final UserPresetValidator validator;
    private final UserPresetImporter importer;
    private final UserPresetExporter exporter = new UserPresetExporter();
    private final Map<String, MontagePreset> userPresets = new LinkedHashMap<>();

    public UserPresetManager(MontagePresetRegistry builtInRegistry) {
        this(FabricLoader.getInstance().getConfigDir().resolve("cinewolf-autodirector").resolve("presets"),
                builtInRegistry, ShotGeneratorRegistry.createDefault());
    }

    public UserPresetManager(Path directory, MontagePresetRegistry builtInRegistry, ShotGeneratorRegistry generators) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.builtInRegistry = Objects.requireNonNull(builtInRegistry, "builtInRegistry");
        this.validator = new UserPresetValidator(builtInRegistry, generators);
        this.importer = new UserPresetImporter(validator);
    }

    public Path directory() {
        return directory;
    }

    public List<MontagePreset> loadedUserPresets() {
        return List.copyOf(userPresets.values());
    }

    public List<String> loadAll() {
        List<String> warnings = new ArrayList<>();
        userPresets.clear();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return warnings;
        try (var stream = Files.list(directory)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        LoadResult result = loadFile(path);
                        warnings.addAll(result.warnings());
                        result.preset().ifPresent(preset -> userPresets.put(preset.id(), preset));
                    });
        } catch (IOException exception) {
            LOGGER.warn("Unable to list user presets in {}", directory, exception);
            warnings.add("user_preset.list_failed");
        }
        return warnings;
    }

    public Path exportPreset(MontagePreset preset, String displayName, String description) throws IOException {
        Objects.requireNonNull(preset, "preset");
        if (builtInRegistry.get(preset.id()).isPresent() && !userPresets.containsKey(preset.id())) {
            // Exporting a built-in is allowed, but re-import under the same id is blocked.
        }
        UserMontagePresetFile file = exporter.export(preset, displayName, description);
        Files.createDirectories(directory);
        Path target = resolveSafe(sanitizeFileName(file.id()) + ".json");
        writeAtomically(target, file);
        return target;
    }

    public LoadResult importFromPath(Path source) {
        Objects.requireNonNull(source, "source");
        return loadFile(source.toAbsolutePath().normalize());
    }

    public LoadResult saveImported(UserMontagePresetFile file) throws IOException {
        UserPresetImporter.ImportResult imported = importer.importPreset(file);
        if (!imported.success()) {
            return new LoadResult(Optional.empty(), imported.errors(), Optional.empty());
        }
        if (builtInRegistry.get(imported.preset().id()).isPresent()) {
            return new LoadResult(Optional.empty(), List.of("user_preset.cannot_overwrite_builtin"), Optional.empty());
        }
        Files.createDirectories(directory);
        Path target = resolveSafe(sanitizeFileName(file.id()) + ".json");
        writeAtomically(target, file.checksum().isBlank()
                ? exporter.export(imported.preset(), file.displayName(), file.description())
                : file);
        userPresets.put(imported.preset().id(), imported.preset());
        return new LoadResult(Optional.of(imported.preset()), List.of(), Optional.of(target));
    }

    private LoadResult loadFile(Path target) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return new LoadResult(Optional.empty(), List.of("user_preset.not_found"), Optional.empty());
        }
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            return new LoadResult(Optional.empty(), List.of("user_preset.unsafe_file"), Optional.empty());
        }
        try {
            if (Files.size(target) > MAX_BYTES) {
                return quarantine(target, "user_preset.too_large");
            }
            UserMontagePresetFile file = GSON.fromJson(Files.readString(target, StandardCharsets.UTF_8),
                    UserMontagePresetFile.class);
            if (file == null) throw new IllegalArgumentException("Preset root is null");
            UserPresetImporter.ImportResult imported = importer.importPreset(file);
            if (!imported.success()) {
                return quarantine(target, imported.errors().isEmpty()
                        ? "user_preset.invalid" : imported.errors().getFirst());
            }
            if (builtInRegistry.get(imported.preset().id()).isPresent()) {
                return new LoadResult(Optional.empty(), List.of("user_preset.builtin_id_reserved"), Optional.empty());
            }
            return new LoadResult(Optional.of(imported.preset()), List.of(), Optional.of(target));
        } catch (Exception exception) {
            LOGGER.warn("Unable to load user preset {}", target, exception);
            return quarantine(target, "user_preset.malformed");
        }
    }

    private LoadResult quarantine(Path source, String warning) {
        try {
            Path broken = nextBrokenPath(source);
            try {
                Files.move(source, broken, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(source, broken);
            }
            return new LoadResult(Optional.empty(), List.of(warning), Optional.of(broken));
        } catch (IOException exception) {
            LOGGER.warn("Unable to quarantine preset {}", source, exception);
            return new LoadResult(Optional.empty(), List.of(warning, "user_preset.quarantine_failed"), Optional.empty());
        }
    }

    private Path nextBrokenPath(Path source) throws IOException {
        Path first = resolveSafe(source.getFileName() + ".broken");
        if (!Files.exists(first, LinkOption.NOFOLLOW_LINKS)) return first;
        for (int index = 1; index < 1000; index++) {
            Path candidate = resolveSafe(source.getFileName() + ".broken." + index);
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate;
        }
        throw new IOException("Too many broken preset files");
    }

    private void writeAtomically(Path target, Object value) throws IOException {
        Path temporary = Files.createTempFile(directory, ".preset-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path resolveSafe(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0
                || fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("Unsafe preset file name");
        }
        Path resolved = directory.resolve(fileName).normalize();
        if (!directory.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Preset path escapes storage directory");
        }
        return resolved;
    }

    private static String sanitizeFileName(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    public record LoadResult(Optional<MontagePreset> preset, List<String> warnings, Optional<Path> path) {
        public LoadResult {
            preset = Objects.requireNonNullElse(preset, Optional.empty());
            warnings = List.copyOf(Objects.requireNonNullElse(warnings, List.of()));
            path = Objects.requireNonNullElse(path, Optional.empty());
        }
    }
}
