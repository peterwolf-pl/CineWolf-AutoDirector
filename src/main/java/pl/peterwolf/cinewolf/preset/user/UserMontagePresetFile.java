package pl.peterwolf.cinewolf.preset.user;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.preset.FramingType;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Versioned on-disk representation of a user-defined montage preset. */
public record UserMontagePresetFile(
        int schemaVersion,
        String id,
        String displayName,
        String description,
        double targetDurationSeconds,
        OutputAspectRatio aspectRatio,
        MontagePacing pacing,
        double minimumShotDuration,
        double maximumShotDuration,
        int minimumShotCount,
        int maximumShotCount,
        List<ShotType> preferredShotTypes,
        Map<String, Double> eventWeights,
        Template introTemplate,
        Template outroTemplate,
        Style style,
        String checksum
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public UserMontagePresetFile {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported user preset schema " + schemaVersion);
        }
        id = requireText(id, "id");
        displayName = requireText(displayName, "displayName");
        description = Objects.requireNonNullElse(description, "");
        Objects.requireNonNull(aspectRatio, "aspectRatio");
        Objects.requireNonNull(pacing, "pacing");
        preferredShotTypes = List.copyOf(Objects.requireNonNullElse(preferredShotTypes, List.of()));
        eventWeights = Map.copyOf(Objects.requireNonNullElse(eventWeights, Map.of()));
        Objects.requireNonNull(introTemplate, "introTemplate");
        Objects.requireNonNull(outroTemplate, "outroTemplate");
        Objects.requireNonNull(style, "style");
        checksum = Objects.requireNonNullElse(checksum, "");
    }

    public record Template(String id, List<ShotType> preferredShotTypes, FramingType framing,
                           double preferredDurationSeconds, double movementIntensity) {
        public Template {
            id = requireText(id, "template id");
            preferredShotTypes = List.copyOf(Objects.requireNonNullElse(preferredShotTypes, List.of()));
            Objects.requireNonNull(framing, "framing");
        }
    }

    public record Style(
            double movementIntensity,
            double cutFrequency,
            FramingType preferredFraming,
            double preferredReplaySpeed,
            double minimumReplaySpeed,
            double maximumReplaySpeed,
            double maximumAdjacentSpeedChange,
            boolean allowReplaySpeedChanges,
            boolean chronologicalSource,
            boolean centerSafeAreaGuide
    ) {
        public Style {
            Objects.requireNonNull(preferredFraming, "preferredFraming");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
