package pl.peterwolf.cinewolf.vehicle;

/** Coarse vehicle motion/activity state used by montage planning. */
public enum VehicleState {
    UNKNOWN,
    STOPPED,
    IDLE,
    ACCELERATING,
    CRUISING,
    BRAKING,
    TURNING,
    DRIFTING,
    TAKEOFF,
    CLIMBING,
    LEVEL_FLIGHT,
    DESCENDING,
    DIVING,
    LANDING,
    DEPARTING,
    ARRIVING,
    BOARDING,
    DISEMBARKING
}
