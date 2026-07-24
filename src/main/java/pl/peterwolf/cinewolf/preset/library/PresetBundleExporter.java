package pl.peterwolf.cinewolf.preset.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pl.peterwolf.cinewolf.preset.user.UserMontagePresetFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PresetBundleExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final PresetBundleValidator validator = new PresetBundleValidator();

    public CineWolfPresetBundle createBundle(
            String bundleId,
            String displayName,
            String description,
            String author,
            List<UserMontagePresetFile> montagePresets,
            List<UserShotPresetFile> shotPresets,
            Set<String> requiredIntegrations,
            BundleMetadata metadata
    ) {
        CineWolfPresetBundle draft = new CineWolfPresetBundle(
                CineWolfPresetBundle.CURRENT_SCHEMA,
                bundleId,
                displayName,
                description,
                author,
                "AllRightsReserved",
                "2.0.0",
                "0.41.1",
                requiredIntegrations,
                montagePresets,
                shotPresets,
                metadata,
                ""
        );
        String checksum = validator.computeChecksum(draft);
        return new CineWolfPresetBundle(
                draft.schemaVersion(), draft.bundleId(), draft.displayName(), draft.description(),
                draft.author(), draft.license(), draft.minimumCineWolfVersion(), draft.minimumFlashbackVersion(),
                draft.requiredIntegrationIds(), draft.montagePresets(), draft.shotPresets(), draft.metadata(),
                checksum
        );
    }

    public String toJson(CineWolfPresetBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        return GSON.toJson(bundle);
    }

    public Path exportTo(Path target, CineWolfPresetBundle bundle) throws IOException {
        Objects.requireNonNull(target, "target");
        String json = toJson(bundle);
        Files.createDirectories(target.getParent());
        Files.writeString(target, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
        return target;
    }
}
