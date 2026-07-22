package pl.peterwolf.cinewolf.model.target;

import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.vehicle.VehicleCategory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record VehicleTarget(
        TargetReference root,
        List<TargetReference> connected,
        VehicleCategory category,
        String providerId
) implements CinematicTarget {
    public VehicleTarget {
        Objects.requireNonNull(root, "root");
        connected = List.copyOf(Objects.requireNonNullElse(connected, List.of()));
        category = category == null ? VehicleCategory.GENERIC : category;
        providerId = Objects.requireNonNullElse(providerId, "builtin");
    }

    @Override
    public UUID id() {
        return root.uuid();
    }

    @Override
    public TargetKind kind() {
        return TargetKind.VEHICLE;
    }

    @Override
    public String displayName() {
        return root.displayName();
    }
}
