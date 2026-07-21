package pl.peterwolf.cinewolf.model;

public record Vec3d(double x, double y, double z) {
    public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);
    public static final Vec3d UP = new Vec3d(0.0, 1.0, 0.0);

    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }

    public Vec3d subtract(Vec3d other) {
        return new Vec3d(x - other.x, y - other.y, z - other.z);
    }

    public Vec3d multiply(double amount) {
        return new Vec3d(x * amount, y * amount, z * amount);
    }

    public double dot(Vec3d other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3d cross(Vec3d other) {
        return new Vec3d(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vec3d normalizeOr(Vec3d fallback) {
        double length = length();
        return length < 1.0e-9 || !Double.isFinite(length) ? fallback : multiply(1.0 / length);
    }

    public double distanceTo(Vec3d other) {
        return subtract(other).length();
    }

    public Vec3d lerp(Vec3d other, double amount) {
        return new Vec3d(
                x + (other.x - x) * amount,
                y + (other.y - y) * amount,
                z + (other.z - z) * amount
        );
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
