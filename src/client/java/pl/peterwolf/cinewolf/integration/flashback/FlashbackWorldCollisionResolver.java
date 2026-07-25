package pl.peterwolf.cinewolf.integration.flashback;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pl.peterwolf.cinewolf.api.CollisionResolver;
import pl.peterwolf.cinewolf.camera.CameraLookAtSolver;
import pl.peterwolf.cinewolf.camera.CameraPathMotionLimiter;
import pl.peterwolf.cinewolf.camera.CeilingClearanceClamp;
import pl.peterwolf.cinewolf.camera.CollisionPathContinuity;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Deterministic local-world collision pass used after shot generation and before final simplification. */
public final class FlashbackWorldCollisionResolver implements CollisionResolver {
    private final CameraLookAtSolver lookAtSolver = new CameraLookAtSolver();
    private final CollisionPathContinuity pathContinuity = new CollisionPathContinuity();
    private final pl.peterwolf.cinewolf.camera.CollisionStrategyResolver strategyResolver =
            new pl.peterwolf.cinewolf.camera.CollisionStrategyResolver();
    private final CameraPathMotionLimiter motionLimiter = new CameraPathMotionLimiter();

    @Override
    public CollisionResolutionResult resolve(CameraPathPlan originalPath, CollisionContext context,
                                               CollisionSettings settings) {
        return resolve(originalPath, context, settings, new TemporalState(), false);
    }

    public CollisionResolutionResult resolve(CameraPathPlan originalPath, CollisionContext context,
                                               CollisionSettings settings, TemporalState temporalState) {
        return resolve(originalPath, context, settings, temporalState, false);
    }

    /**
     * @param ceilingOnly when true, only pull the camera under solid ceilings (no lateral avoidance probes)
     */
    public CollisionResolutionResult resolve(CameraPathPlan originalPath, CollisionContext context,
                                               CollisionSettings settings, TemporalState temporalState,
                                               boolean ceilingOnly) {
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
        int ceilingClamped = 0;
        java.util.LinkedHashMap<String, Integer> unresolvedReasons = new java.util.LinkedHashMap<>();
        String lastUnresolvedReason = "";
        Vec3d previousAdjusted = null;
        for (CameraSample sample : originalPath.samples()) {
            if (sample.discontinuity()) {
                temporalState.reset();
                previousAdjusted = null;
            }
            double deltaSeconds = Double.isFinite(temporalState.previousCinematicTime)
                    ? sample.cinematicTimeSeconds() - temporalState.previousCinematicTime : 0.0;
            Vec3d position = sample.position();
            boolean resolvedSafely = true;
            boolean constrained = sample.collisionConstrained();

            // Indoor corner shots: snap the estimated corner onto real walls, eye-height tracking.
            if (originalPath.request() != null
                    && originalPath.request().shotType() == ShotType.ROOM_CORNER
                    && sample.lookAtPoint() != null) {
                Vec3d snapped = snapToRoomCorner(level, sample.lookAtPoint(), position, clearance);
                if (snapped.distanceTo(position) > 1.0e-4) {
                    position = snapped;
                    constrained = true;
                }
            }

            // 3rd person: keep the generator height (feet + height setting). Never lift into an overlook.
            boolean thirdPerson = originalPath.request() != null
                    && originalPath.request().shotType() == ShotType.THIRD_PERSON;
            double lockedCameraY = sample.position().y();

            if (!ceilingOnly && !thirdPerson) {
                java.util.function.Predicate<Vec3d> safety =
                        candidate -> isSafe(level, candidate, sample.lookAtPoint(), clearance);
                position = pathContinuity.resolve(sample.position(), sample.lookAtPoint(), clearance,
                        deltaSeconds, temporalState.positionState, safety).orElse(null);
                resolvedSafely = position != null && temporalState.positionState.lastResolutionSafe();
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
                    if (lastUnresolvedReason == null || lastUnresolvedReason.isBlank()) {
                        lastUnresolvedReason = "unknown";
                    }
                    unresolvedReasons.merge(lastUnresolvedReason, 1, Integer::sum);
                    unresolved++;
                }
                if (position == null) {
                    position = sample.position();
                }
            }

            Vec3d beforeCeiling = position;
            if (thirdPerson) {
                // Horizontal-only: pull toward subject if blocked, preserve planned camera Y (height setting).
                position = resolveThirdPersonHorizontal(level, sample.position(), sample.lookAtPoint(),
                        clearance);
                position = new Vec3d(position.x(), lockedCameraY, position.z());
            } else {
                position = clampUnderCeiling(level, position, sample.lookAtPoint(), clearance);
                if (position.distanceTo(beforeCeiling) > 1.0e-6) {
                    ceilingClamped++;
                    constrained = true;
                }
            }

            boolean moved = position.distanceTo(sample.position()) > 1.0e-6;
            if (moved) changed++;
            Vec3d lookAt = sample.lookAtPoint();
            if (thirdPerson) {
                // Keep look-at at the same Y as camera so collision never invents downward F5 pitch.
                lookAt = new Vec3d(lookAt.x(), lockedCameraY, lookAt.z());
            }
            CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(position, lookAt,
                    temporalState.previousYaw, temporalState.previousPitch, Math.max(1.0e-4, deltaSeconds),
                    120.0, thirdPerson ? 20.0 : 85.0);
            double pitch = orientation.pitch();
            double yaw = orientation.yaw();
            if (thirdPerson) {
                pitch = Math.max(-6.0, Math.min(6.0, pitch));
            }
            org.joml.Quaternionf rotation = thirdPerson
                    ? new org.joml.Quaternionf().rotationYXZ(
                    (float) Math.toRadians(-yaw), (float) Math.toRadians(pitch), 0.0f).normalize()
                    : orientation.quaternion();
            adjusted.add(new CameraSample(sample.cinematicTimeSeconds(), sample.replayTime(), position,
                    rotation, yaw, pitch, orientation.roll(), sample.fov(),
                    lookAt, sample.discontinuity() || orientation.degenerate(),
                    constrained || moved || !resolvedSafely));
            previousAdjusted = position;
            temporalState.previousCinematicTime = sample.cinematicTimeSeconds();
            temporalState.previousYaw = yaw;
            temporalState.previousPitch = pitch;
        }

        List<CameraSample> continuous = adjusted;
        boolean thirdPersonPath = originalPath.request() != null
                && originalPath.request().shotType() == ShotType.THIRD_PERSON;
        // Skip lift-prone control points / motion limiting for player-level 3rd person.
        if (!ceilingOnly && !thirdPersonPath) {
            List<CameraSample> withControls = strategyResolver.insertControlPoints(adjusted, candidate -> {
                Vec3d focus = adjusted.isEmpty() ? candidate : adjusted.getFirst().lookAtPoint();
                for (CameraSample sample : adjusted) {
                    if (Math.abs(sample.position().distanceTo(candidate)) < 8.0) {
                        focus = sample.lookAtPoint();
                        break;
                    }
                }
                return isSafe(level, candidate, focus, clearance);
            }, lookAtSolver);
            continuous = motionLimiter.limit(withControls, 12.0, 20.0, 120.0, 85.0);
            // Re-apply ceiling after control-point insertion / motion limiting.
            List<CameraSample> underCeiling = new ArrayList<>(continuous.size());
            for (CameraSample sample : continuous) {
                Vec3d clamped = clampUnderCeiling(level, sample.position(), sample.lookAtPoint(), clearance);
                if (clamped.distanceTo(sample.position()) > 1.0e-6) {
                    ceilingClamped++;
                    CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(clamped, sample.lookAtPoint(),
                            sample.yaw(), sample.pitch(), 1.0 / 20.0, 120.0, 85.0);
                    underCeiling.add(new CameraSample(sample.cinematicTimeSeconds(), sample.replayTime(), clamped,
                            orientation.quaternion(), orientation.yaw(), orientation.pitch(), orientation.roll(),
                            sample.fov(), sample.lookAtPoint(), sample.discontinuity() || orientation.degenerate(),
                            true));
                } else {
                    underCeiling.add(sample);
                }
            }
            continuous = underCeiling;
        }

        List<PathWarning> warnings = new ArrayList<>(originalPath.warnings());
        if (changed > 0 && !ceilingOnly) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "collision_adjusted",
                    "Collision avoidance moved " + changed + " camera samples", 0.0));
        }
        if (ceilingClamped > 0) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "ceiling_clamped",
                    "Camera pulled under ceiling for " + ceilingClamped + " sample(s)", 0.0));
        }
        if (unresolved > 0 && !ceilingOnly) {
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "collision_unresolved",
                    unresolved + ":" + formatReasonCounts(unresolvedReasons)
                            + (lastUnresolvedReason.isBlank() ? "" : " (last=" + lastUnresolvedReason + ")"),
                    0.0));
        }
        CameraPathPlan path = new CameraPathPlan(originalPath.request(), continuous, continuous, warnings,
                originalPath.statistics());
        if (!ceilingOnly) {
            path = strategyResolver.annotate(path, appliedStrategies);
        }
        return new CollisionResolutionResult(path, changed > 0 || ceilingClamped > 0 || !appliedStrategies.isEmpty(),
                unresolved == 0
                        ? (ceilingClamped > 0 ? "Ceiling clearance applied" : "Collision avoidance completed")
                        : "Continuity fallback: " + lastUnresolvedReason);
    }

    /**
     * Horizontal-only collision for 3rd person: if the planned eye-level point is inside a block,
     * pull toward the subject along XZ. Never changes height.
     */
    static Vec3d resolveThirdPersonHorizontal(ClientLevel level, Vec3d camera, Vec3d focus, double clearance) {
        if (level == null || camera == null || !camera.isFinite()) return camera;
        if (isSafe(level, camera, focus, clearance)) return camera;
        if (focus == null || !focus.isFinite()) return camera;
        // Pull toward subject eyes in several steps; keep original Y.
        for (int step = 1; step <= 8; step++) {
            double t = step / 8.0;
            double x = camera.x() + (focus.x() - camera.x()) * t * 0.85;
            double z = camera.z() + (focus.z() - camera.z()) * t * 0.85;
            Vec3d candidate = new Vec3d(x, camera.y(), z);
            if (isSafe(level, candidate, focus, clearance)) return candidate;
        }
        // Last resort: stand just behind the eyes on XZ.
        Vec3d near = new Vec3d(
                focus.x() + (camera.x() - focus.x()) * 0.15,
                camera.y(),
                focus.z() + (camera.z() - focus.z()) * 0.15);
        return near;
    }

    /**
     * Finds two roughly perpendicular nearby walls from the subject and places the camera in that
     * indoor corner at the subject's eye height, inset from both walls.
     */
    static Vec3d snapToRoomCorner(ClientLevel level, Vec3d focus, Vec3d estimatedCorner, double clearance) {
        if (level == null || focus == null || !focus.isFinite() || estimatedCorner == null) return estimatedCorner;
        double inset = Math.max(0.25, clearance + 0.15);
        double maxProbe = 12.0;
        // Cardinal wall probes from subject focus (horizontal).
        double[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        double[] hits = new double[4];
        boolean[] hit = new boolean[4];
        for (int i = 0; i < 4; i++) {
            OptionalDouble d = horizontalWallDistance(level, focus, dirs[i][0], dirs[i][1], maxProbe);
            if (d.isPresent()) {
                hits[i] = d.getAsDouble();
                hit[i] = true;
            }
        }
        // Pair X-axis walls (0:+X,1:-X) with Z-axis walls (2:+Z,3:-Z) → four possible corners.
        int[][] pairs = {{0, 2}, {0, 3}, {1, 2}, {1, 3}};
        double bestScore = Double.NEGATIVE_INFINITY;
        Vec3d best = estimatedCorner;
        for (int[] pair : pairs) {
            if (!hit[pair[0]] || !hit[pair[1]]) continue;
            double dx = dirs[pair[0]][0] * Math.max(0.0, hits[pair[0]] - inset);
            double dz = dirs[pair[1]][1] * Math.max(0.0, hits[pair[1]] - inset);
            Vec3d corner = new Vec3d(focus.x() + dx, focus.y(), focus.z() + dz);
            // Prefer the corner closest to the generator estimate (stable with path bounds).
            double score = -corner.distanceTo(new Vec3d(estimatedCorner.x(), focus.y(), estimatedCorner.z()));
            // Prefer tighter rooms (both walls close).
            score -= (hits[pair[0]] + hits[pair[1]]) * 0.05;
            if (score > bestScore) {
                bestScore = score;
                best = corner;
            }
        }
        // Always lock Y to subject eye height for this shot type.
        return new Vec3d(best.x(), focus.y(), best.z());
    }

    private static OptionalDouble horizontalWallDistance(ClientLevel level, Vec3d origin,
                                                          double dirX, double dirZ, double maxDistance) {
        Vec3 start = vector(origin);
        Vec3 end = new Vec3(origin.x() + dirX * maxDistance, origin.y(), origin.z() + dirZ * maxDistance);
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) return OptionalDouble.empty();
        double dx = hit.getLocation().x - origin.x();
        double dz = hit.getLocation().z - origin.z();
        double dist = Math.sqrt(dx * dx + dz * dz);
        return dist > 0.05 && dist < maxDistance ? OptionalDouble.of(dist) : OptionalDouble.empty();
    }

    /**
     * Raycasts upward from the subject head (and camera column) and pulls the camera just under
     * the lowest solid ceiling.
     */
    static Vec3d clampUnderCeiling(ClientLevel level, Vec3d camera, Vec3d focus, double clearance) {
        if (level == null || camera == null || !camera.isFinite()) return camera;
        OptionalDouble maxY = OptionalDouble.empty();
        // Prefer ceiling above the subject (player head ≈ look-at + eye offset).
        if (focus != null && focus.isFinite()) {
            Vec3d head = focus.add(new Vec3d(0.0, 1.62, 0.0));
            OptionalDouble subjectCeiling = findCeilingY(level, head, CeilingClearanceClamp.DEFAULT_MAX_PROBE);
            maxY = CeilingClearanceClamp.maxCameraY(subjectCeiling, clearance);
        }
        // Also respect a roof directly above the camera column (low beams / overhangs).
        OptionalDouble cameraCeiling = findCeilingY(level, camera, CeilingClearanceClamp.DEFAULT_MAX_PROBE);
        OptionalDouble cameraMax = CeilingClearanceClamp.maxCameraY(cameraCeiling, clearance);
        if (cameraMax.isPresent()) {
            maxY = maxY.isPresent()
                    ? OptionalDouble.of(Math.min(maxY.getAsDouble(), cameraMax.getAsDouble()))
                    : cameraMax;
        }
        return CeilingClearanceClamp.clamp(camera, maxY);
    }

    /** Upward block raycast; returns underside Y of the first solid hit, or empty if open sky. */
    static OptionalDouble findCeilingY(ClientLevel level, Vec3d origin, double maxDistance) {
        if (level == null || origin == null || !origin.isFinite() || maxDistance <= 0.0) {
            return OptionalDouble.empty();
        }
        Vec3 start = vector(origin);
        Vec3 end = vector(origin.add(new Vec3d(0.0, maxDistance, 0.0)));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) return OptionalDouble.empty();
        // Underside of the hit: use the hit location Y (clip lands on the face).
        return OptionalDouble.of(hit.getLocation().y);
    }

    private static String formatReasonCounts(java.util.Map<String, Integer> reasons) {
        if (reasons == null || reasons.isEmpty()) return "unknown";
        StringBuilder text = new StringBuilder();
        reasons.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    if (!text.isEmpty()) text.append(", ");
                    text.append(entry.getKey()).append('=').append(entry.getValue());
                });
        return text.toString();
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
        // Above a subject ceiling is never safe for indoor shots.
        OptionalDouble subjectCeiling = focus != null && focus.isFinite()
                ? findCeilingY(level, focus.add(new Vec3d(0.0, 1.62, 0.0)), CeilingClearanceClamp.DEFAULT_MAX_PROBE)
                : OptionalDouble.empty();
        OptionalDouble maxY = CeilingClearanceClamp.maxCameraY(subjectCeiling, clearance);
        if (maxY.isPresent() && camera.y() > maxY.getAsDouble() + 1.0e-4) return false;
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
        private double previousPitch = Double.NaN;

        public void reset() {
            positionState.reset();
            previousCinematicTime = Double.NaN;
            previousYaw = Double.NaN;
            previousPitch = Double.NaN;
        }
    }
}
