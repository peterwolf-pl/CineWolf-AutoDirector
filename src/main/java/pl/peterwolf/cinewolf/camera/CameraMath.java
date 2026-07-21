package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.Vec3d;

public final class CameraMath {
    private CameraMath() {
    }

    public static double unwrapDegrees(double previous, double current) {
        if (!Double.isFinite(previous)) {
            return current;
        }
        double result = current;
        while (result - previous > 180.0) result -= 360.0;
        while (result - previous < -180.0) result += 360.0;
        return result;
    }

    public static double angleDifferenceDegrees(double first, double second) {
        return Math.abs(unwrapDegrees(first, second) - first);
    }

    public static Vec3d horizontalDirectionFromYaw(double yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians)).normalizeOr(new Vec3d(0.0, 0.0, 1.0));
    }

    public static double pointLineDistance(Vec3d point, Vec3d start, Vec3d end) {
        Vec3d line = end.subtract(start);
        double lengthSquared = line.lengthSquared();
        if (lengthSquared < 1.0e-12) return point.distanceTo(start);
        double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(line) / lengthSquared));
        return point.distanceTo(start.add(line.multiply(t)));
    }
}
