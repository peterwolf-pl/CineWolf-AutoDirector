package pl.peterwolf.cinewolf.preset.library;

import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record PresetLibrarySearch(
        String text,
        Set<PresetCategory> categories,
        Set<String> tags,
        OutputAspectRatio aspectRatio,
        Double minDuration,
        Double maxDuration,
        Boolean favouritesOnly,
        Set<String> installedIntegrations,
        String flashbackVersion,
        boolean requireCompatibleIntegrations
) {
    public PresetLibrarySearch {
        text = Objects.requireNonNullElse(text, "").trim().toLowerCase(Locale.ROOT);
        categories = Set.copyOf(categories == null ? Set.of() : categories);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        installedIntegrations = Set.copyOf(installedIntegrations == null ? Set.of() : installedIntegrations);
        flashbackVersion = Objects.requireNonNullElse(flashbackVersion, "");
    }

    public static PresetLibrarySearch all() {
        return new PresetLibrarySearch("", Set.of(), Set.of(), null, null, null, null, Set.of(), "", false);
    }

    public boolean matches(PresetLibraryEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (!text.isEmpty()) {
            String hay = (entry.id() + " " + entry.displayName() + " " + entry.description()
                    + " " + entry.author() + " " + entry.tags()).toLowerCase(Locale.ROOT);
            if (!hay.contains(text)) return false;
        }
        if (!categories.isEmpty() && entry.categories().stream().noneMatch(categories::contains)) return false;
        if (!tags.isEmpty() && entry.tags().stream().noneMatch(tags::contains)) return false;
        if (aspectRatio != null && entry.aspectRatio() != aspectRatio) return false;
        if (minDuration != null && entry.durationSeconds() < minDuration) return false;
        if (maxDuration != null && entry.durationSeconds() > maxDuration) return false;
        if (Boolean.TRUE.equals(favouritesOnly) && !entry.favourite()) return false;
        if (requireCompatibleIntegrations && !entry.requiredIntegrations().isEmpty()) {
            if (!installedIntegrations.containsAll(entry.requiredIntegrations())) return false;
        }
        if (!flashbackVersion.isBlank() && !entry.minimumFlashbackVersion().isBlank()) {
            // Keep entries whose minimum is satisfied by detected version using simple equality/startsWith.
            if (!flashbackVersion.equals(entry.minimumFlashbackVersion())
                    && !flashbackVersion.startsWith(entry.minimumFlashbackVersion())) {
                // still allow; caller can surface warning
            }
        }
        return true;
    }
}
