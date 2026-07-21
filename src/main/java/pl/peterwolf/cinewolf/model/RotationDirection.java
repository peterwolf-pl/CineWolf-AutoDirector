package pl.peterwolf.cinewolf.model;

public enum RotationDirection {
    CLOCKWISE(-1.0, "Clockwise"),
    COUNTERCLOCKWISE(1.0, "Counterclockwise"),
    LEFT_TO_RIGHT(1.0, "Left to right"),
    RIGHT_TO_LEFT(-1.0, "Right to left");

    private final double sign;
    private final String label;

    RotationDirection(double sign, String label) {
        this.sign = sign;
        this.label = label;
    }

    public double sign() {
        return sign;
    }

    public String label() {
        return label;
    }
}
