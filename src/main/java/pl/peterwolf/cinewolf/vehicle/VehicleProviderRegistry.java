package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class VehicleProviderRegistry {
    private final List<VehicleProvider> providers = new ArrayList<>();

    public VehicleProviderRegistry register(VehicleProvider provider) {
        providers.add(Objects.requireNonNull(provider, "provider"));
        providers.sort(Comparator.comparingInt(VehicleProvider::priority).reversed()
                .thenComparing(VehicleProvider::providerId));
        return this;
    }

    public Optional<VehicleDescriptor> resolve(TargetReference target, TargetPose pose) {
        for (VehicleProvider provider : providers) {
            if (provider.supports(target, pose)) {
                Optional<VehicleDescriptor> descriptor = provider.describe(target, pose);
                if (descriptor.isPresent()) return descriptor;
            }
        }
        return Optional.empty();
    }

    public VehicleDescriptor requireOrGeneric(TargetReference target, TargetPose pose) {
        return resolve(target, pose).orElseGet(() ->
                new BuiltinVehicleProvider().describe(target, pose).orElseThrow());
    }

    public static VehicleProviderRegistry createDefault() {
        return new VehicleProviderRegistry()
                .register(new BuiltinVehicleProvider())
                .register(new SoftModVehicleProvider());
    }
}
