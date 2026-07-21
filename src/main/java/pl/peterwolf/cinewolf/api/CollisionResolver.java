package pl.peterwolf.cinewolf.api;

import pl.peterwolf.cinewolf.model.CameraPathPlan;

public interface CollisionResolver {
    CollisionResolutionResult resolve(CameraPathPlan originalPath, CollisionContext context, CollisionSettings settings);

    record CollisionContext(Object levelToken) {
    }

    record CollisionSettings(double minimumBlockDistance) {
    }

    record CollisionResolutionResult(CameraPathPlan path, boolean changed, String message) {
    }
}
