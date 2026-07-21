package pl.peterwolf.cinewolf.model;

public enum EasingType {
    LINEAR("Linear"),
    SMOOTHSTEP("Smoothstep"),
    SMOOTHERSTEP("Smootherstep"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT_CUBIC("Ease In Out Cubic");

    private final String label;

    EasingType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
