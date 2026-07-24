package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Rich vehicle profile for montage planning and camera anchors.
 * Compatible with the lighter {@link VehicleDescriptor} used by existing generators.
 */
public record VehicleProfile(
        String profileId,
        VehicleCategory category,
        TargetReference rootEntity,
        List<TargetReference> connectedEntities,
        OrientedBoundingVolume bounds,
        Vec3d forwardDirection,
        Vec3d upDirection,
        Vec3d velocity,
        Map<String, VehicleAnchor> anchors,
        VehicleState state,
        VehicleCapabilities capabilities,
        Map<String, String> metadata
) {
    public VehicleProfile {
        profileId = Objects.requireNonNullElse(profileId, "unknown");
        category = category == null ? VehicleCategory.GENERIC : category;
        Objects.requireNonNull(rootEntity, "rootEntity");
        connectedEntities = List.copyOf(connectedEntities == null ? List.of() : connectedEntities);
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(forwardDirection, "forwardDirection");
        Objects.requireNonNull(upDirection, "upDirection");
        velocity = velocity == null ? Vec3d.ZERO : velocity;
        anchors = Map.copyOf(anchors == null ? Map.of() : anchors);
        state = state == null ? VehicleState.UNKNOWN : state;
        capabilities = capabilities == null ? VehicleCapabilities.generic() : capabilities;
        metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
    }

    public Optional<VehicleAnchor> anchor(String key) {
        return Optional.ofNullable(anchors.get(key));
    }

    public Optional<VehicleAnchor> anchor(VehicleAnchorKind kind) {
        return anchors.values().stream().filter(a -> a.kind() == kind).findFirst();
    }

    public VehicleDescriptor toDescriptor() {
        return new VehicleDescriptor(
                rootEntity,
                connectedEntities,
                category,
                metadata.getOrDefault("providerId", "builtin"),
                forwardDirection,
                upDirection,
                bounds.bounds(),
                List.copyOf(anchors.values()),
                bounds.length(),
                bounds.width(),
                bounds.height()
        );
    }

    public static VehicleProfile fromDescriptor(VehicleDescriptor descriptor, Vec3d velocity, VehicleState state) {
        Objects.requireNonNull(descriptor, "descriptor");
        Map<String, VehicleAnchor> anchors = new LinkedHashMap<>();
        for (VehicleAnchor anchor : descriptor.anchors()) {
            anchors.put(anchor.kind().name().toLowerCase(), anchor);
        }
        OrientedBoundingVolume volume = new OrientedBoundingVolume(
                descriptor.boundingVolume(),
                descriptor.center(),
                descriptor.forward(),
                descriptor.up(),
                descriptor.length(),
                descriptor.width(),
                descriptor.height()
        );
        VehicleCapabilities capabilities = switch (descriptor.category()) {
            case MINECART -> VehicleCapabilities.minecart();
            case TRAIN, CONVOY -> VehicleCapabilities.train();
            case BOAT -> VehicleCapabilities.boat();
            case HORSE, MOUNT -> VehicleCapabilities.mount();
            case AIRCRAFT -> VehicleCapabilities.aircraft();
            case ZIPLINE, ZIP_LINE -> VehicleCapabilities.zipline();
            default -> VehicleCapabilities.generic();
        };
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("providerId", descriptor.providerId());
        return new VehicleProfile(
                descriptor.providerId() + ":" + descriptor.category().name().toLowerCase(),
                descriptor.category(),
                descriptor.root(),
                descriptor.connected(),
                volume,
                descriptor.forward(),
                descriptor.up(),
                velocity == null ? Vec3d.ZERO : velocity,
                anchors,
                state == null ? VehicleState.UNKNOWN : state,
                capabilities,
                metadata
        );
    }
}
