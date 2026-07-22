package pl.peterwolf.cinewolf.model.target;

import java.util.Objects;

public record StructureTargetMetadata(String label, String source, int blockCountEstimate) {
    public static final StructureTargetMetadata EMPTY = new StructureTargetMetadata("", "manual", 0);

    public StructureTargetMetadata {
        label = Objects.requireNonNullElse(label, "");
        source = Objects.requireNonNullElse(source, "manual");
        blockCountEstimate = Math.max(0, blockCountEstimate);
    }
}
