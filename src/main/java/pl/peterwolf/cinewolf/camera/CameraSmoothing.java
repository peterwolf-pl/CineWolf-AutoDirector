package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.Vec3d;

public final class CameraSmoothing {
    private CameraSmoothing() {
    }

    public static Vec3d exponential(Vec3d current, Vec3d target, double responsiveness, double deltaSeconds) {
        if (deltaSeconds <= 0.0) return current;
        double alpha = 1.0 - Math.exp(-Math.max(0.01, responsiveness) * deltaSeconds);
        return current.lerp(target, alpha);
    }

    public static Vec3d smoothDirection(Vec3d previous, Vec3d measured, double responsiveness, double deltaSeconds) {
        return exponential(previous, measured, responsiveness, deltaSeconds).normalizeOr(previous);
    }
}
