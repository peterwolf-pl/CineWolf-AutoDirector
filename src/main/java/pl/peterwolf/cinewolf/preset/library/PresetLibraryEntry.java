package pl.peterwolf.cinewolf.preset.library;

import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;
import pl.peterwolf.cinewolf.preset.user.UserMontagePresetFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record PresetLibraryEntry(
        String id,
        String displayName,
        String description,
        String author,
        boolean builtIn,
        boolean favourite,
        int localRating,
        double durationSeconds,
        OutputAspectRatio aspectRatio,
        Set<PresetCategory> categories,
        Set<String> tags,
        Set<String> requiredIntegrations,
        String minimumFlashbackVersion,
        String sourceBundleId,
        MontagePreset resolvedPreset,
        UserMontagePresetFile sourceFile
) {
    public PresetLibraryEntry {
        id = Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNullElse(displayName, id);
        description = Objects.requireNonNullElse(description, "");
        author = Objects.requireNonNullElse(author, "unknown");
        localRating = Math.max(0, Math.min(5, localRating));
        durationSeconds = Math.max(0.0, durationSeconds);
        categories = Set.copyOf(categories == null ? Set.of() : categories);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        requiredIntegrations = Set.copyOf(requiredIntegrations == null ? Set.of() : requiredIntegrations);
        minimumFlashbackVersion = Objects.requireNonNullElse(minimumFlashbackVersion, "0.41.1");
        sourceBundleId = Objects.requireNonNullElse(sourceBundleId, "");
    }

    public PresetLibraryEntry withFavourite(boolean favourite) {
        return new PresetLibraryEntry(id, displayName, description, author, builtIn, favourite, localRating,
                durationSeconds, aspectRatio, categories, tags, requiredIntegrations, minimumFlashbackVersion,
                sourceBundleId, resolvedPreset, sourceFile);
    }

    public PresetLibraryEntry withRating(int rating) {
        return new PresetLibraryEntry(id, displayName, description, author, builtIn, favourite, rating,
                durationSeconds, aspectRatio, categories, tags, requiredIntegrations, minimumFlashbackVersion,
                sourceBundleId, resolvedPreset, sourceFile);
    }

    public static PresetLibraryEntry fromBuiltIn(MontagePreset preset, Set<PresetCategory> categories) {
        return new PresetLibraryEntry(
                preset.id(),
                preset.displayNameKey(),
                "built-in",
                "CineWolf",
                true,
                false,
                0,
                preset.targetDurationSeconds(),
                preset.aspectRatio(),
                categories,
                Set.of("builtin"),
                Set.of(),
                "0.41.1",
                "",
                preset,
                null
        );
    }
}
