package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.api.CollisionResolver;
import pl.peterwolf.cinewolf.model.CameraPathPlan;

public final class NoOpCollisionResolver implements CollisionResolver {
    @Override
    public CollisionResolutionResult resolve(CameraPathPlan originalPath, CollisionContext context, CollisionSettings settings) {
        return new CollisionResolutionResult(originalPath, false, "Collision avoidance is planned for version 1.1");
    }
}
