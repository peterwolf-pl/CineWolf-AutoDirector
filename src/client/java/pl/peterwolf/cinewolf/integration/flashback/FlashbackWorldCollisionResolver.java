package pl.peterwolf.cinewolf.integration.flashback;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pl.peterwolf.cinewolf.api.CollisionResolver;
import pl.peterwolf.cinewolf.camera.CameraLookAtSolver;
import pl.peterwolf.cinewolf.camera.CollisionPathContinuity;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Deterministic local-world collision pass used after shot generation and before final simplification. */
public final class FlashbackWorldCollisionResolver implements CollisionResolver {
    private final CameraLookAtSolver lookAtSolver = new CameraLookAtSolver();
    private final CollisionPathContinuity pathContinuity = new CollisionPathContinuity();
    private final pl.peterwolf.cinewolf.camera.CollisionStrategyResolver strategyResolver =
            new pl.peterwolf.cinewolf.camera.CollisionStrategyResolver();

    @Override
    public CollisionResolutionResult resolve(CameraPathPlan originalPath, CollisionContext context,
                                               CollisionSettings settings) {
        return resolve(originalPath, context, settings, new TemporalState());
    }

    public CollisionResolutionResult resolve(CameraPathPlan originalPath, CollisionContext context,
                                               CollisionSettings settings, TemporalState temporalState) {
        if (!(context.levelToken() instanceof ClientLevel level)) {
            return unresolved(originalPath, "collision_world_unavailable", "Replay world is unavailable");
        }
        if (originalPath.samples().isEmpty()) {
            return unresolved(originalPath, "collision_unresolved", "Path has no samples");
        }

        double clearance = Math.max(0.05, Math.min(1.0, settings.minimumBlockDistance()));
        List<CameraSample> adjusted = new ArrayList<>(originalPath.samples().size());
        List<pl.peterwolf.cinewolf.camera.CollisionStrategyCandidate> appliedStrategies = new ArrayList<>();
        int changed = 0;
        int unresolved = 0;
        String lastUnresolvedReason = "";
        Vec3d previousAdjusted = null;
        for (CameraSample sample : originalPath.samples()) {
            if (sample.discontinuity()) {
                temporalState.reset();
                previousAdjusted = null;
            }
            double deltaSeconds = Double.isFinite(temporalState.previousCinematicTime)
                    ? sample.cinematicTimeSeconds() - temporalState.previousCinematicTime : 0.0;
            java.util.function.Predicate<Vec3d> safety =
                    candidate -> isSafe(level, candidate, sample.lookAtPoint(), clearance);
            Vec3d position = pathContinuity.resolve(sample.position(), sample.lookAtPoint(), clearance,
                    deltaSeconds, temporalState.positionState, safety).orElse(null);
            boolean resolvedSafely = position != null && temporalState.positionState.lastResolutionSafe();
            if (!resolvedSafely) {
                var strategy = strategyResolver.resolveSample(sample.position(), sample.lookAtPoint(),
                        previousAdjusted, clearance, safety);
                if (strategy.isPresent()) {
                    position = strategy.get().position();
                    appliedStrategies.add(strategy.get());
                    resolvedSafely = safety.test(position);
                }
            }
            if (!resolvedSafely) {
                lastUnresolvedReason = position == null ? "invalid_collision_input"
                        : temporalState.positionState.lastFailureReason();
                unresolved++;
            }
            if (position == null) {
                position = sample.position();
            }
            boolean moved = position.distanceTo(sample.position()) > 1.0e-6;
            if (moved) changed++;
            CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, sample.lookAtPoint(),
                    temporalState.previousYaw);
            adjusted.add(new CameraSample(sample.cinematicTimeSeconds(), sample.replayTime(), position,
                    orientation.quaternion(), orientation.yaw(), orientation.pitch(), orientation.roll(), sample.fov(),
                    sample.lookAtPoint(), sample.discontinuity() || orientation.degenerate(),
                    sample.collisionConstrained() || moved || !resolvedSafely));
            previousAdjusted = position;
            temporalState.previousCinematicTime = sample.cinematicTimeSeconds();
            temporalState.previousYaw = orientation.yaw();
        }

        List<CameraSample> withControls = strategyResolver.insertControlPoints(adjusted, candidate -> {
            // Control-point insertion uses sample look-at from nearest original sample.
            Vec3d focus = adjusted.isEmpty() ? candidate : adjusted.getFirst().lookAtPoint();
            for (CameraSample sample : adjusted) {
                if (Math.abs(sample.position().distanceTo(candidate)) < 8.0) {
                    focus = sample.lookAtPoint();
                    break;
                }
            }
            return isSafe(level, candidate, focus, clearance);
        }, lookAtSolver);

        List<PathWarning> warnings = new ArrayList<>(originalPath.warnings());
        if (changed > 0) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "collision_adjusted",
                    "Collision avoidance moved " + changed + " camera samples", 0.0));
        }
        if (unresolved > 0) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "collision_unresolved",
                    "Collision avoidance used a continuity fallback for " + unresolved
                            + " camera samples (last reason: " + lastUnresolvedReason + ")", 0.0));
        }
        CameraPathPlan path = new CameraPathPlan(originalPath.request(), withControls, withControls, warnings,
                originalPath.statistics());
        path = strategyResolver.annotate(path, appliedStrategies);
        return new CollisionResolutionResult(path, changed > 0 || !appliedStrategies.isEmpty(), unresolved == 0
                ? "Collision avoidance completed" : "Continuity fallback: " + lastUnresolvedReason);
    }

    private static CollisionResolutionResult unresolved(CameraPathPlan originalPath, String code, String message) {
        List<PathWarning> warnings = new ArrayList<>(originalPath.warnings());
        warnings.add(new PathWarning(PathWarning.Severity.WARNING, code, message, 0.0));
        CameraPathPlan unresolvedPath = new CameraPathPlan(originalPath.request(), originalPath.samples(),
                originalPath.simplifiedSamples(), warnings, originalPath.statistics());
        return new CollisionResolutionResult(unresolvedPath, false, message);
    }

    private static boolean isSafe(ClientLevel level, Vec3d camera, Vec3d focus, double clearance) {
        AABB box = new AABB(camera.x() - clearance, camera.y() - clearance, camera.z() - clearance,
                camera.x() + clearance, camera.y() + clearance, camera.z() + clearance);
        if (!level.noCollision(box)) return false;
        HitResult hit = level.clip(new ClipContext(vector(focus), vector(camera), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static Vec3 vector(Vec3d value) {
        return new Vec3(value.x(), value.y(), value.z());
    }

    public static final class TemporalState {
        private final CollisionPathContinuity.State positionState = new CollisionPathContinuity.State();
        private double previousCinematicTime = Double.NaN;
        private double previousYaw = Double.NaN;

        public void reset() {
            positionState.reset();
            previousCinematicTime = Double.NaN;
            previousYaw = Double.NaN;
        }
    }
}
