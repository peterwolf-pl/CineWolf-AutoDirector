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

    public static double exponential(double current, double target, double responsiveness, double deltaSeconds) {
        if (deltaSeconds <= 0.0) return current;
        double alpha = 1.0 - Math.exp(-Math.max(0.01, responsiveness) * deltaSeconds);
        return current + (target - current) * alpha;
    }

    public static Vec3d smoothDirection(Vec3d previous, Vec3d measured, double responsiveness, double deltaSeconds) {
        return exponential(previous, measured, responsiveness, deltaSeconds).normalizeOr(previous);
    }

    /**
     * Smooths a unit direction with an explicit maximum turn rate (degrees/second).
     * Prevents flight/chase tracking from whipping when velocity flips for a sample.
     */
    public static Vec3d smoothDirectionRateLimited(Vec3d previous, Vec3d measured, double responsiveness,
                                                    double deltaSeconds, double maxDegreesPerSecond) {
        Vec3d smoothed = smoothDirection(previous, measured, responsiveness, deltaSeconds);
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 1.0e-6 || !previous.isFinite()
                || previous.lengthSquared() < 1.0e-12) {
            return smoothed;
        }
        Vec3d from = previous.normalizeOr(smoothed);
        Vec3d to = smoothed.normalizeOr(from);
        double dot = Math.max(-1.0, Math.min(1.0, from.dot(to)));
        double angleDegrees = Math.toDegrees(Math.acos(dot));
        double maxAngle = Math.max(1.0, maxDegreesPerSecond) * deltaSeconds;
        if (angleDegrees <= maxAngle || angleDegrees < 1.0e-6) return to;
        double t = maxAngle / angleDegrees;
        return from.lerp(to, t).normalizeOr(from);
    }

    public static Vec3d clampStep(Vec3d previous, Vec3d desired, double maxDistance) {
        if (previous == null || !previous.isFinite()) return desired;
        if (desired == null || !desired.isFinite()) return previous;
        if (!Double.isFinite(maxDistance) || maxDistance <= 0.0) return previous;
        double distance = previous.distanceTo(desired);
        if (distance <= maxDistance) return desired;
        return previous.lerp(desired, maxDistance / distance);
    }
}
