package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Objects;

public record VehicleAnchor(VehicleAnchorKind kind, Vec3d worldPosition) {
    public VehicleAnchor {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(worldPosition, "worldPosition");
    }
}
