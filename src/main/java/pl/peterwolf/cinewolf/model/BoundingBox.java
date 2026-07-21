package pl.peterwolf.cinewolf.model;

public record BoundingBox(Vec3d min, Vec3d max) {
    public Vec3d center() {
        return min.lerp(max, 0.5);
    }

    public boolean contains(Vec3d point) {
        return contains(point, 0.0);
    }

    public boolean contains(Vec3d point, double margin) {
        return point.x() >= min.x() - margin && point.x() <= max.x() + margin
                && point.y() >= min.y() - margin && point.y() <= max.y() + margin
                && point.z() >= min.z() - margin && point.z() <= max.z() + margin;
    }

    public boolean isFinite() {
        return min.isFinite() && max.isFinite()
                && min.x() <= max.x() && min.y() <= max.y() && min.z() <= max.z();
    }
}
