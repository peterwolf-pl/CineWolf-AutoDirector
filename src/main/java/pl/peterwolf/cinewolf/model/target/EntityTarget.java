package pl.peterwolf.cinewolf.model.target;

import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.Objects;
import java.util.UUID;

public record EntityTarget(TargetReference reference) implements CinematicTarget {
    public EntityTarget {
        Objects.requireNonNull(reference, "reference");
    }

    @Override
    public UUID id() {
        return reference.uuid();
    }

    @Override
    public TargetKind kind() {
        return TargetKind.ENTITY;
    }

    @Override
    public String displayName() {
        return reference.displayName();
    }
}
