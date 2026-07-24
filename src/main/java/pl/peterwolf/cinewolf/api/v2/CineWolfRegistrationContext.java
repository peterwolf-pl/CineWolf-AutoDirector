package pl.peterwolf.cinewolf.api.v2;

import pl.peterwolf.cinewolf.api.ShotGenerator;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.vehicle.VehicleProvider;

/**
 * Safe registration surface for third-party integrations.
 * Implementations reject invalid IDs, unsupported API versions, and unsafe overrides.
 */
public interface CineWolfRegistrationContext {
    void registerVehicleProvider(VehicleProvider provider);

    void registerTargetProvider(CinematicTargetProvider provider);

    void registerReplayEventDetector(ReplayEventDetector detector);

    void registerMontageProfile(MontageProfileProvider provider);

    void registerShotGenerator(ShotType type, ShotGenerator generator);

    void registerPresetProvider(PresetProvider provider);

    void registerFramingProvider(FramingProvider provider);

    String integrationId();

    CineWolfApiVersion apiVersion();
}
