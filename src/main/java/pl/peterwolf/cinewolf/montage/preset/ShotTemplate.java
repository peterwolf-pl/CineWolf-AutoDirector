package pl.peterwolf.cinewolf.montage.preset;

import pl.peterwolf.cinewolf.model.ShotType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ShotTemplate(
        String id,
        List<ShotType> preferredShotTypes,
        FramingType framing,
        double preferredDurationSeconds,
        double cameraMovementIntensity
) {
    public ShotTemplate {
        id = requireText(id, "id");
        Objects.requireNonNull(preferredShotTypes, "preferredShotTypes");
        if (preferredShotTypes.isEmpty()) {
            throw new IllegalArgumentException("preferredShotTypes must not be empty");
        }
        if (preferredShotTypes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("preferredShotTypes must not contain null");
        }
        if (new HashSet<>(preferredShotTypes).size() != preferredShotTypes.size()) {
            throw new IllegalArgumentException("preferredShotTypes must not contain duplicates");
        }
        preferredShotTypes = List.copyOf(preferredShotTypes);
        Objects.requireNonNull(framing, "framing");
        if (!Double.isFinite(preferredDurationSeconds) || preferredDurationSeconds <= 0.0) {
            throw new IllegalArgumentException("preferredDurationSeconds must be finite and greater than zero");
        }
        if (!Double.isFinite(cameraMovementIntensity)
                || cameraMovementIntensity < 0.0 || cameraMovementIntensity > 1.0) {
            throw new IllegalArgumentException("cameraMovementIntensity must be finite and between zero and one");
        }
    }

    public ShotType primaryShotType() {
        return preferredShotTypes.getFirst();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
