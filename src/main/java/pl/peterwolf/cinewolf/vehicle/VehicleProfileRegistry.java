package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves rich vehicle profiles with caching-friendly immutable results. */
public final class VehicleProfileRegistry {
    private final VehicleProviderRegistry providers;

    public VehicleProfileRegistry(VehicleProviderRegistry providers) {
        this.providers = Objects.requireNonNull(providers, "providers");
    }

    public static VehicleProfileRegistry createDefault() {
        return new VehicleProfileRegistry(VehicleProviderRegistry.createDefault());
    }

    public VehicleProviderRegistry providers() {
        return providers;
    }

    public Optional<VehicleProfile> resolve(TargetReference target, TargetPose pose) {
        return providers.resolve(target, pose).map(descriptor ->
                VehicleProfile.fromDescriptor(descriptor, estimateVelocity(pose), estimateState(descriptor, pose)));
    }

    public VehicleProfile requireOrGeneric(TargetReference target, TargetPose pose) {
        return resolve(target, pose).orElseGet(() ->
                VehicleProfile.fromDescriptor(providers.requireOrGeneric(target, pose),
                        estimateVelocity(pose), VehicleState.UNKNOWN));
    }

    private static Vec3d estimateVelocity(TargetPose pose) {
        // Pose snapshots do not always carry velocity; zero is a safe planning default.
        return Vec3d.ZERO;
    }

    private static VehicleState estimateState(VehicleDescriptor descriptor, TargetPose pose) {
        if (descriptor.category() == VehicleCategory.AIRCRAFT) {
            double vertical = pose.velocity().y();
            if (vertical > 0.35) return VehicleState.CLIMBING;
            if (vertical < -0.35) return VehicleState.DESCENDING;
            if (pose.velocity().length() > 0.5) return VehicleState.LEVEL_FLIGHT;
            return VehicleState.UNKNOWN;
        }
        if (pose.velocity().length() < 0.05) return VehicleState.STOPPED;
        if (pose.inVehicle()) return VehicleState.CRUISING;
        return VehicleState.UNKNOWN;
    }
}
