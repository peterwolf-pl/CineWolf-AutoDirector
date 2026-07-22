package pl.peterwolf.cinewolf.model.target;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetKind;

import java.util.Objects;
import java.util.UUID;

public record AreaTarget(String areaId, BoundingBox bounds, String label) implements CinematicTarget {
    public AreaTarget {
        areaId = Objects.requireNonNullElse(areaId, UUID.randomUUID().toString()).trim();
        if (areaId.isEmpty()) throw new IllegalArgumentException("Area id cannot be blank");
        Objects.requireNonNull(bounds, "bounds");
        label = Objects.requireNonNullElse(label, areaId);
    }

    @Override
    public UUID id() {
        return UUID.nameUUIDFromBytes(areaId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public TargetKind kind() {
        return TargetKind.AREA;
    }

    @Override
    public String displayName() {
        return label;
    }
}
