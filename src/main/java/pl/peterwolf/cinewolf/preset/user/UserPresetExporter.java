package pl.peterwolf.cinewolf.preset.user;

import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.ShotTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UserPresetExporter {
    public UserMontagePresetFile export(MontagePreset preset, String displayName, String description) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map.Entry<ReplayEventType, Double> entry : preset.eventWeights().entrySet()) {
            weights.put(entry.getKey().name(), entry.getValue());
        }
        UserMontagePresetFile.Template intro = template(preset.introTemplate());
        UserMontagePresetFile.Template outro = template(preset.outroTemplate());
        UserMontagePresetFile.Style style = new UserMontagePresetFile.Style(
                preset.style().cameraMovementIntensity(),
                preset.style().cutFrequency(),
                preset.style().targetFraming(),
                preset.style().preferredReplaySpeed(),
                preset.style().minimumReplaySpeed(),
                preset.style().maximumReplaySpeed(),
                preset.style().maximumReplaySpeedChange(),
                preset.style().allowReplaySpeedChanges(),
                preset.style().preferChronologicalOrder(),
                preset.style().centerSafeFraming()
        );
        UserMontagePresetFile withoutChecksum = new UserMontagePresetFile(
                UserMontagePresetFile.CURRENT_SCHEMA_VERSION,
                preset.id(),
                displayName == null || displayName.isBlank() ? preset.id() : displayName,
                description == null ? "" : description,
                preset.targetDurationSeconds(),
                preset.aspectRatio(),
                preset.pacing(),
                preset.minimumShotDuration(),
                preset.maximumShotDuration(),
                preset.minimumShotCount(),
                preset.maximumShotCount(),
                List.copyOf(preset.preferredShotTypes()),
                weights,
                intro,
                outro,
                style,
                ""
        );
        return new UserMontagePresetFile(
                withoutChecksum.schemaVersion(), withoutChecksum.id(), withoutChecksum.displayName(),
                withoutChecksum.description(), withoutChecksum.targetDurationSeconds(), withoutChecksum.aspectRatio(),
                withoutChecksum.pacing(), withoutChecksum.minimumShotDuration(), withoutChecksum.maximumShotDuration(),
                withoutChecksum.minimumShotCount(), withoutChecksum.maximumShotCount(),
                withoutChecksum.preferredShotTypes(), withoutChecksum.eventWeights(), withoutChecksum.introTemplate(),
                withoutChecksum.outroTemplate(), withoutChecksum.style(), UserPresetChecksum.compute(withoutChecksum)
        );
    }

    private static UserMontagePresetFile.Template template(ShotTemplate template) {
        return new UserMontagePresetFile.Template(template.id(), List.copyOf(template.preferredShotTypes()),
                template.framing(), template.preferredDurationSeconds(), template.cameraMovementIntensity());
    }
}
