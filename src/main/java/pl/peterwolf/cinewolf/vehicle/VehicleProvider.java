package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.Optional;

/** Soft provider for vehicle-aware framing. No hard mod dependencies. */
public interface VehicleProvider {
    String providerId();

    int priority();

    boolean supports(TargetReference target, TargetPose pose);

    Optional<VehicleDescriptor> describe(TargetReference target, TargetPose pose);
}
