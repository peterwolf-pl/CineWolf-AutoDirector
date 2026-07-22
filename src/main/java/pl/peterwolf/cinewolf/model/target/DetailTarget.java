package pl.peterwolf.cinewolf.model.target;

import pl.peterwolf.cinewolf.model.DetailTargetType;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DetailTarget(
        TargetReference host,
        DetailTargetType detailType,
        Optional<Vec3d> customPoint
) implements CinematicTarget {
    public DetailTarget {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(detailType, "detailType");
        customPoint = customPoint == null ? Optional.empty() : customPoint;
    }

    @Override
    public UUID id() {
        return host.uuid();
    }

    @Override
    public TargetKind kind() {
        return TargetKind.DETAIL;
    }

    @Override
    public String displayName() {
        return host.displayName() + ":" + detailType.name().toLowerCase();
    }
}
