package pl.peterwolf.cinewolf.shot;

public record ShotCapabilities(
        boolean supportsDynamicTargets,
        boolean supportsGroups,
        boolean supportsStructures,
        boolean supportsVehicles,
        boolean supportsPreview,
        boolean montageCompatible
) {
    public static ShotCapabilities basicDynamic() {
        return new ShotCapabilities(true, false, false, false, true, true);
    }

    public static ShotCapabilities vehicleAware() {
        return new ShotCapabilities(true, false, false, true, true, true);
    }

    public static ShotCapabilities structureAware() {
        return new ShotCapabilities(true, true, true, false, true, true);
    }

    public static ShotCapabilities full() {
        return new ShotCapabilities(true, true, true, true, true, true);
    }
}
