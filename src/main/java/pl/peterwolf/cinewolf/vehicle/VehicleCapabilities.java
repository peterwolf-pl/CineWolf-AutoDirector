package pl.peterwolf.cinewolf.vehicle;

public record VehicleCapabilities(
        boolean hasPassengers,
        boolean multiEntity,
        boolean airborne,
        boolean onRails,
        boolean onWater,
        boolean powered,
        boolean canTurnSharply,
        boolean supportsAnchors
) {
    public static VehicleCapabilities generic() {
        return new VehicleCapabilities(false, false, false, false, false, false, false, true);
    }

    public static VehicleCapabilities minecart() {
        return new VehicleCapabilities(true, false, false, true, false, false, false, true);
    }

    public static VehicleCapabilities train() {
        return new VehicleCapabilities(true, true, false, true, false, true, false, true);
    }

    public static VehicleCapabilities boat() {
        return new VehicleCapabilities(true, false, false, false, true, false, true, true);
    }

    public static VehicleCapabilities mount() {
        return new VehicleCapabilities(true, false, false, false, false, false, true, true);
    }

    public static VehicleCapabilities aircraft() {
        return new VehicleCapabilities(true, false, true, false, false, true, true, true);
    }

    public static VehicleCapabilities zipline() {
        return new VehicleCapabilities(true, false, true, false, false, false, false, true);
    }
}
