package pl.peterwolf.cinewolf.montage.preset;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record MontagePreset(
        String id,
        String displayNameKey,
        double targetDurationSeconds,
        OutputAspectRatio aspectRatio,
        MontagePacing pacing,
        double minimumShotDuration,
        double maximumShotDuration,
        int minimumShotCount,
        int maximumShotCount,
        Set<ShotType> preferredShotTypes,
        Map<ReplayEventType, Double> eventWeights,
        ShotTemplate introTemplate,
        ShotTemplate outroTemplate,
        MontageStyleSettings style
) {
    public MontagePreset {
        id = requireText(id, "id");
        displayNameKey = requireText(displayNameKey, "displayNameKey");
        requirePositiveFinite(targetDurationSeconds, "targetDurationSeconds");
        Objects.requireNonNull(aspectRatio, "aspectRatio");
        Objects.requireNonNull(pacing, "pacing");
        requirePositiveFinite(minimumShotDuration, "minimumShotDuration");
        requirePositiveFinite(maximumShotDuration, "maximumShotDuration");
        if (maximumShotDuration < minimumShotDuration) {
            throw new IllegalArgumentException("maximumShotDuration must be at least minimumShotDuration");
        }
        if (minimumShotCount <= 0 || maximumShotCount < minimumShotCount) {
            throw new IllegalArgumentException("shot count range must be positive and ordered");
        }
        if (minimumShotCount * minimumShotDuration > targetDurationSeconds) {
            throw new IllegalArgumentException("minimum shot layout cannot fit inside targetDurationSeconds");
        }
        if (maximumShotCount * maximumShotDuration < targetDurationSeconds) {
            throw new IllegalArgumentException("maximum shot layout cannot fill targetDurationSeconds");
        }

        Objects.requireNonNull(preferredShotTypes, "preferredShotTypes");
        if (preferredShotTypes.isEmpty()) {
            throw new IllegalArgumentException("preferredShotTypes must not be empty");
        }
        if (preferredShotTypes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("preferredShotTypes must not contain null");
        }
        preferredShotTypes = Collections.unmodifiableSet(EnumSet.copyOf(preferredShotTypes));

        Objects.requireNonNull(eventWeights, "eventWeights");
        EnumMap<ReplayEventType, Double> copiedWeights = new EnumMap<>(ReplayEventType.class);
        for (Map.Entry<ReplayEventType, Double> entry : eventWeights.entrySet()) {
            ReplayEventType type = Objects.requireNonNull(entry.getKey(), "eventWeights key");
            Double weight = Objects.requireNonNull(entry.getValue(), "eventWeights value");
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("event weight for " + type + " must be finite and non-negative");
            }
            copiedWeights.put(type, weight);
        }
        eventWeights = Collections.unmodifiableMap(copiedWeights);

        Objects.requireNonNull(introTemplate, "introTemplate");
        Objects.requireNonNull(outroTemplate, "outroTemplate");
        Objects.requireNonNull(style, "style");
        validateTemplateDuration(introTemplate, minimumShotDuration, maximumShotDuration);
        validateTemplateDuration(outroTemplate, minimumShotDuration, maximumShotDuration);
    }

    public double eventWeight(ReplayEventType type) {
        return eventWeights.getOrDefault(Objects.requireNonNull(type, "type"), 1.0);
    }

    private static void validateTemplateDuration(ShotTemplate template, double minimum, double maximum) {
        if (template.preferredDurationSeconds() < minimum || template.preferredDurationSeconds() > maximum) {
            throw new IllegalArgumentException("template " + template.id() + " duration must fit the preset shot range");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and greater than zero");
        }
    }
}
