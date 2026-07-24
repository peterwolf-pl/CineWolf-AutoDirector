package pl.peterwolf.cinewolf.preset.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.preset.user.UserMontagePresetFile;
import pl.peterwolf.cinewolf.preset.user.UserPresetImporter;
import pl.peterwolf.cinewolf.preset.user.UserPresetManager;
import pl.peterwolf.cinewolf.preset.user.UserPresetValidator;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Local-only community preset library.
 * No network access, accounts, or remote catalogs.
 */
public final class CommunityPresetLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommunityPresetLibrary.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path root;
    private final MontagePresetRegistry builtIns;
    private final PresetLibraryIndex index = new PresetLibraryIndex();
    private final PresetBundleImporter importer = new PresetBundleImporter();
    private final PresetBundleExporter exporter = new PresetBundleExporter();
    private final PresetBundleValidator validator = new PresetBundleValidator();
    private final UserPresetImporter userImporter;
    private final Map<String, Boolean> favourites = new LinkedHashMap<>();
    private final Map<String, Integer> ratings = new LinkedHashMap<>();

    public CommunityPresetLibrary(MontagePresetRegistry builtIns) {
        this(FabricLoader.getInstance().getConfigDir().resolve("cinewolf-autodirector").resolve("library"),
                builtIns, ShotGeneratorRegistry.createDefault());
    }

    public CommunityPresetLibrary(Path root, MontagePresetRegistry builtIns, ShotGeneratorRegistry generators) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.builtIns = Objects.requireNonNull(builtIns, "builtIns");
        this.userImporter = new UserPresetImporter(new UserPresetValidator(builtIns, generators));
    }

    public Path root() {
        return root;
    }

    public PresetLibraryIndex index() {
        return index;
    }

    public List<String> reload() {
        List<String> warnings = new ArrayList<>();
        index.clear();
        loadFavourites();
        for (MontagePreset preset : builtIns.all()) {
            index.add(PresetLibraryEntry.fromBuiltIn(preset, inferCategories(preset))
                    .withFavourite(favourites.getOrDefault(preset.id(), false))
                    .withRating(ratings.getOrDefault(preset.id(), 0)));
        }
        Path bundles = root.resolve("bundles");
        if (Files.isDirectory(bundles, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.list(bundles)) {
                stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .forEach(path -> {
                            PresetBundleImporter.ImportResult result = importer.importPath(path);
                            if (!result.success()) {
                                warnings.addAll(result.errors());
                                return;
                            }
                            warnings.addAll(indexBundle(result.bundle()));
                        });
            } catch (IOException exception) {
                LOGGER.warn("Unable to list preset bundles in {}", bundles, exception);
                warnings.add("preset.library.list_failed");
            }
        }
        return warnings;
    }

    public PresetBundleImporter.ImportResult importBundle(Path path) {
        PresetBundleImporter.ImportResult result = importer.importPath(path);
        if (!result.success()) return result;
        try {
            Files.createDirectories(root.resolve("bundles"));
            Path target = root.resolve("bundles").resolve(sanitize(result.bundle().bundleId()) + ".json");
            exporter.exportTo(target, result.bundle());
            indexBundle(result.bundle());
            return result;
        } catch (IOException exception) {
            return PresetBundleImporter.ImportResult.failed(List.of("preset.library.store_failed"));
        }
    }

    public PresetBundleImporter.ImportResult importBundleJson(String json) {
        PresetBundleImporter.ImportResult result = importer.importJson(json);
        if (!result.success()) return result;
        try {
            Files.createDirectories(root.resolve("bundles"));
            Path target = root.resolve("bundles").resolve(sanitize(result.bundle().bundleId()) + ".json");
            exporter.exportTo(target, result.bundle());
            indexBundle(result.bundle());
            return result;
        } catch (IOException exception) {
            return PresetBundleImporter.ImportResult.failed(List.of("preset.library.store_failed"));
        }
    }

    public Path exportBundle(CineWolfPresetBundle bundle, Path target) throws IOException {
        return exporter.exportTo(target, bundle);
    }

    public CineWolfPresetBundle exportSelected(String bundleId, String displayName, String author,
                                               List<UserMontagePresetFile> presets) {
        return exporter.createBundle(bundleId, displayName, "", author, presets, List.of(), Set.of(),
                BundleMetadata.empty());
    }

    public synchronized void setFavourite(String id, boolean favourite) {
        favourites.put(id, favourite);
        index.find(id).ifPresent(entry -> {
            // rebuild entry
        });
        persistMeta();
        reload();
    }

    public synchronized void setRating(String id, int rating) {
        ratings.put(id, Math.max(0, Math.min(5, rating)));
        persistMeta();
        reload();
    }

    public List<PresetLibraryEntry> search(PresetLibrarySearch query) {
        return index.search(query);
    }

    private List<String> indexBundle(CineWolfPresetBundle bundle) {
        List<String> warnings = new ArrayList<>();
        for (UserMontagePresetFile file : bundle.montagePresets()) {
            UserPresetImporter.ImportResult imported = userImporter.importPreset(file);
            if (!imported.success() || imported.preset() == null) {
                warnings.addAll(imported.errors());
                continue;
            }
            MontagePreset preset = imported.preset();
            Set<PresetCategory> categories = new LinkedHashSet<>();
            for (String cat : bundle.metadata().categories()) {
                try {
                    categories.add(PresetCategory.valueOf(cat.toUpperCase(Locale.ROOT)));
                } catch (RuntimeException ignored) {
                    categories.add(PresetCategory.GENERAL);
                }
            }
            if (categories.isEmpty()) categories.add(PresetCategory.GENERAL);
            index.add(new PresetLibraryEntry(
                    preset.id(),
                    file.displayName(),
                    file.description(),
                    bundle.author(),
                    false,
                    favourites.getOrDefault(preset.id(), false),
                    ratings.getOrDefault(preset.id(), 0),
                    preset.targetDurationSeconds(),
                    preset.aspectRatio(),
                    categories,
                    new LinkedHashSet<>(bundle.metadata().tags()),
                    bundle.requiredIntegrationIds(),
                    bundle.minimumFlashbackVersion(),
                    bundle.bundleId(),
                    preset,
                    file
            ));
        }
        return warnings;
    }

    private void loadFavourites() {
        favourites.clear();
        ratings.clear();
        Path meta = root.resolve("library-meta.json");
        if (!Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            LibraryMeta loaded = GSON.fromJson(Files.readString(meta, StandardCharsets.UTF_8), LibraryMeta.class);
            if (loaded != null) {
                if (loaded.favourites != null) favourites.putAll(loaded.favourites);
                if (loaded.ratings != null) ratings.putAll(loaded.ratings);
            }
        } catch (Exception exception) {
            LOGGER.warn("Unable to load preset library metadata", exception);
        }
    }

    private void persistMeta() {
        try {
            Files.createDirectories(root);
            LibraryMeta meta = new LibraryMeta();
            meta.favourites = new LinkedHashMap<>(favourites);
            meta.ratings = new LinkedHashMap<>(ratings);
            Files.writeString(root.resolve("library-meta.json"), GSON.toJson(meta), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Unable to save preset library metadata", exception);
        }
    }

    private static Set<PresetCategory> inferCategories(MontagePreset preset) {
        Set<PresetCategory> cats = new LinkedHashSet<>();
        cats.add(PresetCategory.CINEMATIC);
        cats.add(PresetCategory.FLASHBACK);
        if (preset.aspectRatio() != null && preset.aspectRatio().name().contains("VERTICAL")) {
            cats.add(PresetCategory.VERTICAL_VIDEO);
            cats.add(PresetCategory.TIKTOK);
            cats.add(PresetCategory.YOUTUBE_SHORT);
        }
        if (preset.pacing() != null && preset.pacing().name().contains("FAST")) {
            cats.add(PresetCategory.HIGH_ENERGY);
            cats.add(PresetCategory.TRAILER);
        }
        return cats;
    }

    private static String sanitize(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private static final class LibraryMeta {
        Map<String, Boolean> favourites;
        Map<String, Integer> ratings;
    }
}
