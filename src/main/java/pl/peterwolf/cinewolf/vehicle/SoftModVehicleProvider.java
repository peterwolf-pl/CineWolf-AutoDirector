package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.Locale;
import java.util.Optional;

/**
 * Soft recognition for known third-party vehicle mods without hard dependencies.
 * Uses entity type id heuristics only.
 */
public final class SoftModVehicleProvider implements VehicleProvider {
    private final BuiltinVehicleProvider delegate = new BuiltinVehicleProvider();

    @Override
    public String providerId() {
        return "soft-mod";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(TargetReference target, TargetPose pose) {
        String type = (target.entityType() + " " + pose.entityType()).toLowerCase(Locale.ROOT);
        return type.contains("minecartchain")
                || type.contains("minecart_chain")
                || type.contains("peterwolf")
                || type.contains("planes")
                || type.contains("immersive_aircraft")
                || type.contains("simpleplanes")
                || type.contains("zipline")
                || type.contains("create:train")
                || type.contains("create_train");
    }

    @Override
    public Optional<VehicleDescriptor> describe(TargetReference target, TargetPose pose) {
        return delegate.describe(target, pose).map(descriptor -> new VehicleDescriptor(
                descriptor.root(),
                descriptor.connected(),
                refineCategory(target, pose, descriptor.category()),
                providerId(),
                descriptor.forward(),
                descriptor.up(),
                descriptor.boundingVolume(),
                descriptor.anchors(),
                descriptor.length(),
                descriptor.width(),
                descriptor.height()
        ));
    }

    private static VehicleCategory refineCategory(TargetReference target, TargetPose pose, VehicleCategory fallback) {
        String type = (target.entityType() + " " + pose.entityType()).toLowerCase(Locale.ROOT);
        if (type.contains("train") || type.contains("minecart_chain") || type.contains("minecartchain")) {
            return VehicleCategory.TRAIN;
        }
        if (type.contains("plane") || type.contains("aircraft")) return VehicleCategory.AIRCRAFT;
        if (type.contains("zipline")) return VehicleCategory.ZIPLINE;
        return fallback;
    }
}
