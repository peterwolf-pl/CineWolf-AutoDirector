package pl.peterwolf.cinewolf.model;

import java.util.Objects;
import java.util.UUID;

public record TargetReference(UUID uuid, String entityType, String displayName) {
    public TargetReference {
        Objects.requireNonNull(uuid, "uuid");
        entityType = Objects.requireNonNullElse(entityType, "unknown");
        displayName = Objects.requireNonNullElse(displayName, entityType);
    }

    public String shortIdentifier() {
        String value = uuid.toString();
        return value.substring(0, 8);
    }
}
