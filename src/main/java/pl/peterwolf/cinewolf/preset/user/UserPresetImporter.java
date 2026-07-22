package pl.peterwolf.cinewolf.preset.user;

import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontageStyleSettings;
import pl.peterwolf.cinewolf.montage.preset.ShotTemplate;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class UserPresetImporter {
    private final UserPresetValidator validator;

    public UserPresetImporter(UserPresetValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public ImportResult importPreset(UserMontagePresetFile file) {
        UserPresetValidator.ValidationResult validation = validator.validate(file);
        if (!validation.valid()) {
            return new ImportResult(false, null, validation.errors());
        }
        try {
            Map<ReplayEventType, Double> weights = new EnumMap<>(ReplayEventType.class);
            for (ReplayEventType type : ReplayEventType.values()) weights.put(type, 1.0);
            file.eventWeights().forEach((key, value) -> weights.put(ReplayEventType.valueOf(key), value));
            ShotTemplate intro = new ShotTemplate(file.introTemplate().id(), file.introTemplate().preferredShotTypes(),
                    file.introTemplate().framing(), file.introTemplate().preferredDurationSeconds(),
                    file.introTemplate().movementIntensity());
            ShotTemplate outro = new ShotTemplate(file.outroTemplate().id(), file.outroTemplate().preferredShotTypes(),
                    file.outroTemplate().framing(), file.outroTemplate().preferredDurationSeconds(),
                    file.outroTemplate().movementIntensity());
            MontageStyleSettings style = new MontageStyleSettings(
                    file.style().movementIntensity(),
                    file.style().cutFrequency(),
                    file.style().preferredFraming(),
                    file.style().preferredReplaySpeed(),
                    file.style().minimumReplaySpeed(),
                    file.style().maximumReplaySpeed(),
                    file.style().maximumAdjacentSpeedChange(),
                    file.style().allowReplaySpeedChanges(),
                    file.style().chronologicalSource(),
                    file.style().centerSafeAreaGuide()
            );
            Set<pl.peterwolf.cinewolf.model.ShotType> preferred =
                    EnumSet.copyOf(new LinkedHashSet<>(file.preferredShotTypes()));
            MontagePreset preset = new MontagePreset(
                    file.id(),
                    "cinewolf.montage.preset.user." + file.id(),
                    file.targetDurationSeconds(),
                    file.aspectRatio(),
                    file.pacing(),
                    file.minimumShotDuration(),
                    file.maximumShotDuration(),
                    file.minimumShotCount(),
                    file.maximumShotCount(),
                    preferred,
                    weights,
                    intro,
                    outro,
                    style
            );
            return new ImportResult(true, preset, validation.errors());
        } catch (Exception exception) {
            return new ImportResult(false, null, java.util.List.of("user_preset.import_failed:" + exception.getMessage()));
        }
    }

    public record ImportResult(boolean success, MontagePreset preset, java.util.List<String> errors) {
        public ImportResult {
            errors = java.util.List.copyOf(Objects.requireNonNullElse(errors, java.util.List.of()));
        }
    }
}
