package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record VehicleDescriptor(
        TargetReference root,
        List<TargetReference> connected,
        VehicleCategory category,
        String providerId,
        Vec3d forward,
        Vec3d up,
        BoundingBox boundingVolume,
        List<VehicleAnchor> anchors,
        double length,
        double width,
        double height
) {
    public VehicleDescriptor {
        Objects.requireNonNull(root, "root");
        connected = List.copyOf(Objects.requireNonNullElse(connected, List.of()));
        category = category == null ? VehicleCategory.GENERIC : category;
        providerId = Objects.requireNonNullElse(providerId, "builtin");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(up, "up");
        Objects.requireNonNull(boundingVolume, "boundingVolume");
        anchors = List.copyOf(Objects.requireNonNullElse(anchors, List.of()));
        length = Math.max(0.5, length);
        width = Math.max(0.5, width);
        height = Math.max(0.5, height);
    }

    public Optional<VehicleAnchor> anchor(VehicleAnchorKind kind) {
        return anchors.stream().filter(anchor -> anchor.kind() == kind).findFirst();
    }

    public Vec3d center() {
        return boundingVolume.center();
    }
}
