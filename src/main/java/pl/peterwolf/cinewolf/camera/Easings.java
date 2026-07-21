package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.EasingType;

public final class Easings {
    private Easings() {
    }

    public static double apply(EasingType type, double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return switch (type) {
            case LINEAR -> t;
            case SMOOTHSTEP -> t * t * (3.0 - 2.0 * t);
            case SMOOTHERSTEP -> t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
            case EASE_IN -> t * t * t;
            case EASE_OUT -> 1.0 - Math.pow(1.0 - t, 3.0);
            case EASE_IN_OUT_CUBIC -> t < 0.5
                    ? 4.0 * t * t * t
                    : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
        };
    }
}
