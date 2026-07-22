package pl.peterwolf.cinewolf.model.target;

import pl.peterwolf.cinewolf.model.TargetKind;

import java.util.UUID;

/**
 * Expanded cinematic target abstraction for framing groups, structures, vehicles, and details.
 * Entity-only {@link pl.peterwolf.cinewolf.model.TargetReference} remains the primary runtime handle;
 * these types describe how poses and framing should be interpreted.
 */
public sealed interface CinematicTarget
        permits EntityTarget, GroupTarget, StructureTarget, AreaTarget, VehicleTarget, DetailTarget {
    UUID id();

    TargetKind kind();

    String displayName();
}
