package pl.peterwolf.cinewolf.vehicle;

public enum VehicleCategory {
    MINECART,
    TRAIN,
    BOAT,
    HORSE,
    MOUNT,
    GROUND_VEHICLE,
    AIRCRAFT,
    ZIPLINE,
    /** Preferred name for zip-line vehicles; {@link #ZIPLINE} remains for compatibility. */
    ZIP_LINE,
    CONVOY,
    GENERIC
}
