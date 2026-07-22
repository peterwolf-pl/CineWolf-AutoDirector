package pl.peterwolf.cinewolf.model.target;

import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetKind;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record StructureTarget(
        String structureId,
        BoundingBox bounds,
        List<Vec3d> focusPoints,
        StructureTargetMetadata metadata
) implements CinematicTarget {
    public StructureTarget {
        structureId = Objects.requireNonNullElse(structureId, UUID.randomUUID().toString()).trim();
        if (structureId.isEmpty()) throw new IllegalArgumentException("Structure id cannot be blank");
        Objects.requireNonNull(bounds, "bounds");
        focusPoints = List.copyOf(Objects.requireNonNullElse(focusPoints, List.of()));
        metadata = metadata == null ? StructureTargetMetadata.EMPTY : metadata;
    }

    @Override
    public UUID id() {
        return UUID.nameUUIDFromBytes(structureId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public TargetKind kind() {
        return TargetKind.STRUCTURE;
    }

    @Override
    public String displayName() {
        return metadata.label().isBlank() ? structureId : metadata.label();
    }

    public Vec3d primaryFocus() {
        if (!focusPoints.isEmpty()) return focusPoints.getFirst();
        return bounds.center();
    }

    public double maximumDimension() {
        double width = bounds.max().x() - bounds.min().x();
        double height = bounds.max().y() - bounds.min().y();
        double depth = bounds.max().z() - bounds.min().z();
        return Math.max(0.5, Math.max(width, Math.max(height, depth)));
    }

    public double suggestedOrbitRadius() {
        return Math.max(4.0, maximumDimension() * 1.4);
    }

    public double suggestedFramingDistance() {
        return Math.max(6.0, maximumDimension() * 2.2);
    }

    public double suggestedElevation() {
        return Math.max(2.0, (bounds.max().y() - bounds.min().y()) * 0.65);
    }
}
