package pl.peterwolf.cinewolf.montage.preset;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontagePresetRegistryTest {
    @Test
    void registersEveryBuiltInExactlyOnceInStableOrder() {
        MontagePresetRegistry registry = MontagePresetRegistry.createDefault();

        assertEquals(MontagePresetType.values().length, registry.all().size());
        assertEquals(List.of(MontagePresetType.values()).stream().map(MontagePresetType::id).toList(),
                registry.all().stream().map(MontagePreset::id).toList());
        for (MontagePresetType type : MontagePresetType.values()) {
            MontagePreset preset = registry.get(type).orElseThrow();
            assertEquals(type.id(), preset.id());
            assertEquals(preset, registry.get("  " + type.id() + "  ").orElseThrow());
        }
    }

    @Test
    void builtInsUseOnlyActuallyRegisteredShotTypesAndCompleteEventWeights() {
        Set<ShotType> supportedShots = ShotGeneratorRegistry.createDefault().supportedTypes();

        for (MontagePreset preset : MontagePresetRegistry.createDefault().all()) {
            assertTrue(supportedShots.containsAll(preset.preferredShotTypes()), preset.id());
            assertTrue(supportedShots.containsAll(preset.introTemplate().preferredShotTypes()), preset.id());
            assertTrue(supportedShots.containsAll(preset.outroTemplate().preferredShotTypes()), preset.id());
            assertEquals(EnumSet.allOf(ReplayEventType.class), preset.eventWeights().keySet(), preset.id());
            assertTrue(preset.eventWeights().values().stream()
                    .allMatch(weight -> Double.isFinite(weight) && weight >= 0.0), preset.id());
            assertTrue(preset.minimumShotCount() * preset.minimumShotDuration()
                    <= preset.targetDurationSeconds(), preset.id());
            assertTrue(preset.maximumShotCount() * preset.maximumShotDuration()
                    >= preset.targetDurationSeconds(), preset.id());
        }
    }

    @Test
    void durationAndFormatPresetsRemainSeparateData() {
        MontagePresetRegistry registry = MontagePresetRegistry.createDefault();

        assertEquals(15.0, registry.get(MontagePresetType.FIFTEEN_SECONDS).orElseThrow().targetDurationSeconds());
        assertEquals(30.0, registry.get(MontagePresetType.THIRTY_SECONDS).orElseThrow().targetDurationSeconds());
        assertEquals(60.0, registry.get(MontagePresetType.SIXTY_SECONDS).orElseThrow().targetDurationSeconds());

        MontagePreset tiktok = registry.get(MontagePresetType.TIKTOK).orElseThrow();
        MontagePreset youtubeShort = registry.get(MontagePresetType.YOUTUBE_SHORT).orElseThrow();
        assertEquals(OutputAspectRatio.VERTICAL_9_16, tiktok.aspectRatio());
        assertEquals(OutputAspectRatio.VERTICAL_9_16, youtubeShort.aspectRatio());
        assertTrue(tiktok.style().centerSafeFraming());
        assertTrue(youtubeShort.style().centerSafeFraming());
        assertFalse(registry.get(MontagePresetType.FIFTEEN_SECONDS).orElseThrow().aspectRatio().vertical());
        assertEquals(9.0 / 16.0, OutputAspectRatio.VERTICAL_9_16.ratio(), 1.0e-12);
    }

    @Test
    void trailerAndShowcaseExpressDifferentEditingStyles() {
        MontagePresetRegistry registry = MontagePresetRegistry.createDefault();
        MontagePreset trailer = registry.get(MontagePresetType.TRAILER).orElseThrow();
        MontagePreset showcase = registry.get(MontagePresetType.CINEMATIC_SHOWCASE).orElseThrow();

        assertEquals(MontagePacing.PROGRESSIVE, trailer.pacing());
        assertTrue(trailer.style().allowReplaySpeedChanges());
        assertFalse(trailer.style().preferChronologicalOrder());
        assertEquals(MontagePacing.CINEMATIC, showcase.pacing());
        assertTrue(showcase.style().preferChronologicalOrder());
        assertTrue(showcase.style().cameraMovementIntensity() < trailer.style().cameraMovementIntensity());
        assertTrue(showcase.style().cutFrequency() < trailer.style().cutFrequency());
        assertTrue(showcase.eventWeight(ReplayEventType.BLOCK_PLACEMENT)
                > showcase.eventWeight(ReplayEventType.COMBAT));
    }

    @Test
    void exposesDefensiveImmutableViews() {
        MontagePresetRegistry registry = MontagePresetRegistry.createDefault();
        MontagePreset preset = registry.get(MontagePresetType.FIFTEEN_SECONDS).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
        assertThrows(UnsupportedOperationException.class, () -> preset.preferredShotTypes().add(ShotType.ORBIT));
        assertThrows(UnsupportedOperationException.class,
                () -> preset.eventWeights().put(ReplayEventType.PAUSE, 99.0));
        assertThrows(UnsupportedOperationException.class,
                () -> preset.introTemplate().preferredShotTypes().add(ShotType.FLYBY));
    }

    @Test
    void acceptsFutureUserDefinedPresetWithoutOverwritingExistingIds() {
        MontagePresetRegistry registry = MontagePresetRegistry.createDefault();
        MontagePreset base = registry.get(MontagePresetType.THIRTY_SECONDS).orElseThrow();
        MontagePreset custom = new MontagePreset(
                "user_slow_30", "cinewolf.montage.preset.user_slow_30", base.targetDurationSeconds(),
                base.aspectRatio(), MontagePacing.CINEMATIC, base.minimumShotDuration(), base.maximumShotDuration(),
                base.minimumShotCount(), base.maximumShotCount(), base.preferredShotTypes(), base.eventWeights(),
                base.introTemplate(), base.outroTemplate(), base.style());

        registry.register(custom);

        assertEquals(custom, registry.get(custom.id()).orElseThrow());
        assertEquals(MontagePresetType.values().length + 1, registry.all().size());
        assertThrows(IllegalArgumentException.class, () -> registry.register(custom));
        assertEquals(MontagePresetType.values().length + 1, registry.all().size());
    }

    @Test
    void rejectsInvalidStyleTemplateAndPresetDataAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new MontageStyleSettings(
                1.1, 0.5, FramingType.MEDIUM, 1.0, 1.0, 1.0, 0.0,
                false, true, false));
        assertThrows(IllegalArgumentException.class, () -> new MontageStyleSettings(
                0.5, 0.5, FramingType.MEDIUM, 2.0, 0.5, 1.5, 0.5,
                true, true, false));
        assertThrows(IllegalArgumentException.class, () -> new ShotTemplate(
                "duplicates", List.of(ShotType.ORBIT, ShotType.ORBIT), FramingType.WIDE, 2.0, 0.5));

        MontagePreset valid = MontagePresetRegistry.createDefault()
                .get(MontagePresetType.FIFTEEN_SECONDS).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> new MontagePreset(
                "invalid", "cinewolf.invalid", valid.targetDurationSeconds(), valid.aspectRatio(), valid.pacing(),
                valid.minimumShotDuration(), valid.maximumShotDuration(), valid.minimumShotCount(),
                valid.maximumShotCount(), valid.preferredShotTypes(), Map.of(ReplayEventType.COMBAT, -1.0),
                valid.introTemplate(), valid.outroTemplate(), valid.style()));
    }
}
