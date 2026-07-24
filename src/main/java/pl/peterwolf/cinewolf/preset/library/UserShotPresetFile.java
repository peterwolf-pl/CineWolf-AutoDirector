package pl.peterwolf.cinewolf.preset.library;

import pl.peterwolf.cinewolf.model.ShotType;

import java.util.Map;
import java.util.Objects;

/** Safe JSON-friendly shot preset without executable content. */
public record UserShotPresetFile(
        int schemaVersion,
        String id,
        String displayName,
        String description,
        ShotType shotType,
        Map<String, Double> numericParameters,
        Map<String, String> stringParameters
) {
    public UserShotPresetFile {
        if (schemaVersion <= 0) schemaVersion = 1;
        id = Objects.requireNonNullElse(id, "shot");
        displayName = Objects.requireNonNullElse(displayName, id);
        description = Objects.requireNonNullElse(description, "");
        shotType = shotType == null ? ShotType.ORBIT : shotType;
        numericParameters = Map.copyOf(numericParameters == null ? Map.of() : numericParameters);
        stringParameters = Map.copyOf(stringParameters == null ? Map.of() : stringParameters);
    }
}
