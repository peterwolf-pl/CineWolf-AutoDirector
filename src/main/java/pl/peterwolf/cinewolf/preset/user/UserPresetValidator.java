package pl.peterwolf.cinewolf.preset.user;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class UserPresetValidator {
    private final Set<String> builtInIds;
    private final Set<ShotType> availableShots;

    public UserPresetValidator(MontagePresetRegistry builtIns, ShotGeneratorRegistry generators) {
        this.builtInIds = builtIns == null ? Set.of()
                : builtIns.all().stream().map(preset -> preset.id().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.availableShots = generators == null ? EnumSet.allOf(ShotType.class) : generators.supportedTypes();
    }

    public ValidationResult validate(UserMontagePresetFile file) {
        Objects.requireNonNull(file, "file");
        List<String> errors = new ArrayList<>();
        if (builtInIds.contains(file.id().toLowerCase(Locale.ROOT))) {
            errors.add("user_preset.builtin_id_reserved");
        }
        if (!Double.isFinite(file.targetDurationSeconds()) || file.targetDurationSeconds() <= 0.0) {
            errors.add("user_preset.invalid_duration");
        }
        if (file.minimumShotDuration() <= 0.0 || file.maximumShotDuration() < file.minimumShotDuration()) {
            errors.add("user_preset.invalid_shot_duration_range");
        }
        if (file.minimumShotCount() <= 0 || file.maximumShotCount() < file.minimumShotCount()) {
            errors.add("user_preset.invalid_shot_count_range");
        }
        if (file.preferredShotTypes().isEmpty()) {
            errors.add("user_preset.no_shot_types");
        }
        for (ShotType type : file.preferredShotTypes()) {
            if (type == null || !availableShots.contains(type)) {
                errors.add("user_preset.unsupported_shot_type");
            }
        }
        for (String key : file.eventWeights().keySet()) {
            try {
                ReplayEventType.valueOf(key);
            } catch (Exception ignored) {
                errors.add("user_preset.unknown_event_weight:" + key);
            }
        }
        if (!UserPresetChecksum.matches(file)) {
            errors.add("user_preset.checksum_mismatch");
        }
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors));
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public ValidationResult {
            errors = List.copyOf(Objects.requireNonNullElse(errors, List.of()));
        }
    }
}
