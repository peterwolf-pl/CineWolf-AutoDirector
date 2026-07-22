package pl.peterwolf.cinewolf.preset.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetType;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UserPresetManagerTest {
    @TempDir
    Path temp;

    @Test
    void exportImportRoundTripAndRejectBuiltinOverwrite() throws Exception {
        MontagePresetRegistry builtIns = MontagePresetRegistry.createDefault();
        UserPresetManager manager = new UserPresetManager(temp, builtIns, ShotGeneratorRegistry.createDefault());
        MontagePreset builtIn = builtIns.get(MontagePresetType.THIRTY_SECONDS).orElseThrow();

        Path exported = manager.exportPreset(builtIn, "My 30s", "custom export");
        assertTrue(Files.exists(exported));

        UserMontagePresetFile file = new com.google.gson.Gson().fromJson(Files.readString(exported),
                UserMontagePresetFile.class);
        assertTrue(UserPresetChecksum.matches(file));

        // Same id as built-in must be rejected on save.
        UserPresetManager.LoadResult rejected = manager.saveImported(file);
        assertTrue(rejected.preset().isEmpty());
        assertTrue(rejected.warnings().contains("user_preset.cannot_overwrite_builtin")
                || rejected.warnings().contains("user_preset.builtin_id_reserved"));

        UserMontagePresetFile custom = new UserMontagePresetFile(
                file.schemaVersion(), "user_demo_preset", "Demo", "desc",
                file.targetDurationSeconds(), file.aspectRatio(), file.pacing(),
                file.minimumShotDuration(), file.maximumShotDuration(), file.minimumShotCount(),
                file.maximumShotCount(), file.preferredShotTypes(), file.eventWeights(),
                file.introTemplate(), file.outroTemplate(), file.style(), "");
        custom = new UserMontagePresetFile(custom.schemaVersion(), custom.id(), custom.displayName(),
                custom.description(), custom.targetDurationSeconds(), custom.aspectRatio(), custom.pacing(),
                custom.minimumShotDuration(), custom.maximumShotDuration(), custom.minimumShotCount(),
                custom.maximumShotCount(), custom.preferredShotTypes(), custom.eventWeights(),
                custom.introTemplate(), custom.outroTemplate(), custom.style(),
                UserPresetChecksum.compute(custom));
        UserPresetManager.LoadResult saved = manager.saveImported(custom);
        assertTrue(saved.preset().isPresent());
        assertEquals("user_demo_preset", saved.preset().get().id());
    }

    @Test
    void corruptedPresetIsRejectedAndQuarantined() throws Exception {
        Path bad = temp.resolve("broken.json");
        Files.writeString(bad, "{not-json");
        UserPresetManager manager = new UserPresetManager(temp, MontagePresetRegistry.createDefault(),
                ShotGeneratorRegistry.createDefault());
        UserPresetManager.LoadResult result = manager.importFromPath(bad);
        assertTrue(result.preset().isEmpty());
        assertFalse(result.warnings().isEmpty());
    }
}
