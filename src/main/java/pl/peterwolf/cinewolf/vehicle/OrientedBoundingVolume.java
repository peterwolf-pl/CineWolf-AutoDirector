package pl.peterwolf.cinewolf.vehicle;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Objects;

/** Axis-aligned bounds with optional orientation hints for framing. */
public record OrientedBoundingVolume(
        BoundingBox bounds,
        Vec3d center,
        Vec3d forward,
        Vec3d up,
        double length,
        double width,
        double height
) {
    public OrientedBoundingVolume {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(up, "up");
        length = Math.max(0.5, length);
        width = Math.max(0.5, width);
        height = Math.max(0.5, height);
    }

    public static OrientedBoundingVolume fromBox(BoundingBox box, Vec3d forward, Vec3d up) {
        Objects.requireNonNull(box, "box");
        Vec3d size = box.max().subtract(box.min());
        double length = Math.max(0.8, Math.max(size.x(), size.z()));
        double width = Math.max(0.6, Math.min(size.x(), size.z()));
        double height = Math.max(0.6, size.y());
        return new OrientedBoundingVolume(box, box.center(), forward, up, length, width, height);
    }
}
