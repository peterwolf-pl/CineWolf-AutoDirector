package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Objects;

public record CollisionStrategyCandidate(
        CollisionStrategy strategy,
        Vec3d position,
        double score,
        String diagnosis
) {
    public CollisionStrategyCandidate {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(position, "position");
        score = Double.isFinite(score) ? score : 0.0;
        diagnosis = diagnosis == null ? "" : diagnosis;
    }
}
