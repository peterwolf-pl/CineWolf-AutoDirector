package pl.peterwolf.cinewolf.preset.library;

import pl.peterwolf.cinewolf.preset.user.UserMontagePresetFile;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CineWolfPresetBundle(
        int schemaVersion,
        String bundleId,
        String displayName,
        String description,
        String author,
        String license,
        String minimumCineWolfVersion,
        String minimumFlashbackVersion,
        Set<String> requiredIntegrationIds,
        List<UserMontagePresetFile> montagePresets,
        List<UserShotPresetFile> shotPresets,
        BundleMetadata metadata,
        String checksum
) {
    public static final int CURRENT_SCHEMA = 1;

    public CineWolfPresetBundle {
        if (schemaVersion <= 0) schemaVersion = CURRENT_SCHEMA;
        bundleId = Objects.requireNonNullElse(bundleId, "bundle");
        displayName = Objects.requireNonNullElse(displayName, bundleId);
        description = Objects.requireNonNullElse(description, "");
        author = Objects.requireNonNullElse(author, "unknown");
        license = Objects.requireNonNullElse(license, "AllRightsReserved");
        minimumCineWolfVersion = Objects.requireNonNullElse(minimumCineWolfVersion, "2.0.0");
        minimumFlashbackVersion = Objects.requireNonNullElse(minimumFlashbackVersion, "0.41.1");
        requiredIntegrationIds = Set.copyOf(requiredIntegrationIds == null ? Set.of() : requiredIntegrationIds);
        montagePresets = List.copyOf(montagePresets == null ? List.of() : montagePresets);
        shotPresets = List.copyOf(shotPresets == null ? List.of() : shotPresets);
        metadata = metadata == null ? BundleMetadata.empty() : metadata;
        checksum = Objects.requireNonNullElse(checksum, "");
    }
}
