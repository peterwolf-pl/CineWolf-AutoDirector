package pl.peterwolf.cinewolf.model;

public enum ShotType {
    ORBIT("Orbit"),
    FOLLOW("Follow"),
    FLYBY("Flyby"),
    DOLLY_IN("Dolly In"),
    DOLLY_OUT("Dolly Out"),
    REVEAL("Reveal"),
    CRANE_UP("Crane Up"),
    CRANE_DOWN("Crane Down"),
    SPIRAL("Spiral"),
    STATIC_TRACKING("Static Tracking"),
    SIDE_TRACKING("Side Tracking"),
    CHASE("Chase"),
    CLOSE_DETAIL("Close Detail"),
    VEHICLE_PROFILE("Vehicle Profile");

    private final String label;

    ShotType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isDynamicTracking() {
        return this == FOLLOW || this == CHASE || this == SIDE_TRACKING || this == STATIC_TRACKING;
    }

    public boolean prefersVehicleTargets() {
        return this == VEHICLE_PROFILE || this == CHASE || this == SIDE_TRACKING;
    }
}
