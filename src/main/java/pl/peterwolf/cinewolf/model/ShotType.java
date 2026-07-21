package pl.peterwolf.cinewolf.model;

public enum ShotType {
    ORBIT("Orbit"),
    FOLLOW("Follow"),
    FLYBY("Flyby"),
    DOLLY_IN("Dolly In"),
    DOLLY_OUT("Dolly Out");

    private final String label;

    ShotType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
