package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Scores multiple collision avoidance strategies for a single sample and selects the best continuous result.
 * World-specific safety checks remain caller-supplied predicates so core stays Flashback-free.
 */
public final class CollisionStrategyResolver {
    private static final double LATERAL_STEP = 0.35;
    private static final double RADIUS_STEP = 0.25;
    private static final double SHORTEN_STEP = 0.2;

    public Optional<CollisionStrategyCandidate> resolveSample(Vec3d original, Vec3d focus, Vec3d previousAdjusted,
                                                              double clearance, Predicate<Vec3d> isSafe) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(focus, "focus");
        Predicate<Vec3d> safety = isSafe == null ? position -> true : isSafe;
        if (safety.test(original)) {
            return Optional.of(new CollisionStrategyCandidate(CollisionStrategy.NONE, original, 1.0,
                    "original_safe"));
        }

        List<CollisionStrategyCandidate> candidates = new ArrayList<>();
        // Raise
        for (double raise = 0.25; raise <= 6.0; raise += 0.5) {
            Vec3d raised = original.add(new Vec3d(0.0, raise, 0.0));
            if (safety.test(raised)) {
                candidates.add(score(CollisionStrategy.RAISE, raised, original, focus, previousAdjusted,
                        "raise=" + raise));
                break;
            }
        }
        // Radial pull toward focus
        Vec3d toFocus = focus.subtract(original);
        double distance = toFocus.length();
        if (distance > 1.0) {
            for (double pull = RADIUS_STEP; pull < distance - 1.0; pull += RADIUS_STEP) {
                Vec3d radial = original.add(toFocus.normalizeOr(Vec3d.ZERO).multiply(pull));
                if (safety.test(radial)) {
                    candidates.add(score(CollisionStrategy.RADIAL_PULL, radial, original, focus, previousAdjusted,
                            "radial_pull=" + pull));
                    break;
                }
            }
            // Orbit radius reduction: keep height, reduce horizontal radius around focus.
            Vec3d horizontal = new Vec3d(original.x() - focus.x(), 0.0, original.z() - focus.z());
            double radius = horizontal.length();
            if (radius > 1.5) {
                for (double factor = 0.9; factor >= 0.35; factor -= 0.1) {
                    Vec3d reduced = focus.add(horizontal.normalizeOr(Vec3d.ZERO).multiply(radius * factor))
                            .add(new Vec3d(0.0, original.y() - focus.y(), 0.0));
                    if (safety.test(reduced)) {
                        candidates.add(score(CollisionStrategy.ORBIT_RADIUS_REDUCTION, reduced, original, focus,
                                previousAdjusted, "radius_factor=" + factor));
                        break;
                    }
                }
            }
        }
        // Lateral translation
        Vec3d along = new Vec3d(original.x() - focus.x(), 0.0, original.z() - focus.z()).normalizeOr(new Vec3d(0, 0, 1));
        Vec3d lateral = Vec3d.UP.cross(along).normalizeOr(new Vec3d(1, 0, 0));
        for (double sign : new double[]{1.0, -1.0}) {
            for (double step = LATERAL_STEP; step <= 4.0; step += LATERAL_STEP) {
                Vec3d translated = original.add(lateral.multiply(sign * step));
                if (safety.test(translated)) {
                    candidates.add(score(CollisionStrategy.LATERAL_TRANSLATION, translated, original, focus,
                            previousAdjusted, "lateral=" + (sign * step)));
                    break;
                }
            }
        }
        // Path shortening toward previous safe position
        if (previousAdjusted != null && previousAdjusted.isFinite()) {
            for (double t = SHORTEN_STEP; t <= 1.0; t += SHORTEN_STEP) {
                Vec3d shortened = original.lerp(previousAdjusted, t);
                if (safety.test(shortened)) {
                    candidates.add(score(CollisionStrategy.PATH_SHORTENING, shortened, original, focus,
                            previousAdjusted, "shorten_t=" + t));
                    break;
                }
            }
            // Inserted control point midpoint with raise
            Vec3d midpoint = original.lerp(previousAdjusted, 0.5).add(new Vec3d(0.0, 0.75, 0.0));
            if (safety.test(midpoint)) {
                candidates.add(score(CollisionStrategy.INSERTED_CONTROL_POINTS, midpoint, original, focus,
                        previousAdjusted, "control_midpoint_raise"));
            }
        }

        return candidates.stream().max(Comparator.comparingDouble(CollisionStrategyCandidate::score));
    }

    public CameraPathPlan annotate(CameraPathPlan path, List<CollisionStrategyCandidate> applied) {
        if (path == null || applied == null || applied.isEmpty()) return path;
        List<PathWarning> warnings = new ArrayList<>(path.warnings());
        long byStrategy = applied.stream().map(CollisionStrategyCandidate::strategy)
                .filter(strategy -> strategy != CollisionStrategy.NONE).count();
        if (byStrategy > 0) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "collision.strategies_applied",
                    "Applied " + byStrategy + " multi-strategy collision corrections", 0.0));
            applied.stream()
                    .filter(candidate -> candidate.strategy() != CollisionStrategy.NONE)
                    .limit(8)
                    .forEach(candidate -> warnings.add(new PathWarning(PathWarning.Severity.INFO,
                            "collision.strategy." + candidate.strategy().name().toLowerCase(),
                            candidate.diagnosis() + " score=" + String.format(java.util.Locale.ROOT, "%.3f",
                                    candidate.score()), 0.0)));
        }
        return new CameraPathPlan(path.request(), path.samples(), path.simplifiedSamples(), warnings, path.statistics());
    }

    public List<CameraSample> insertControlPoints(List<CameraSample> samples, Predicate<Vec3d> isSafe,
                                                  CameraLookAtSolver lookAtSolver) {
        if (samples == null || samples.size() < 2) return samples == null ? List.of() : samples;
        Predicate<Vec3d> safety = isSafe == null ? position -> true : isSafe;
        List<CameraSample> output = new ArrayList<>();
        double previousYaw = Double.NaN;
        for (int index = 0; index < samples.size(); index++) {
            CameraSample sample = samples.get(index);
            if (index > 0) {
                CameraSample previous = samples.get(index - 1);
                if (!segmentClear(previous.position(), sample.position(), safety)) {
                    Vec3d mid = previous.position().lerp(sample.position(), 0.5)
                            .add(new Vec3d(0.0, 0.8, 0.0));
                    if (!safety.test(mid)) {
                        mid = previous.position().lerp(sample.lookAtPoint(), 0.35)
                                .add(new Vec3d(0.0, 1.0, 0.0));
                    }
                    if (safety.test(mid)) {
                        double cinematic = (previous.cinematicTimeSeconds() + sample.cinematicTimeSeconds()) * 0.5;
                        long replay = (previous.replayTime() + sample.replayTime()) / 2L;
                        CameraLookAtSolver.Orientation orientation = lookAtSolver.solve(mid, sample.lookAtPoint(),
                                previousYaw);
                        CameraSample control = new CameraSample(cinematic, replay, mid, orientation.quaternion(),
                                orientation.yaw(), orientation.pitch(), orientation.roll(), sample.fov(),
                                sample.lookAtPoint(), false, true);
                        output.add(control);
                        previousYaw = orientation.yaw();
                    }
                }
            }
            output.add(sample);
            previousYaw = sample.yaw();
        }
        return List.copyOf(output);
    }

    private static boolean segmentClear(Vec3d start, Vec3d end, Predicate<Vec3d> isSafe) {
        for (int step = 1; step <= 4; step++) {
            if (!isSafe.test(start.lerp(end, step / 5.0))) return false;
        }
        return true;
    }

    private static CollisionStrategyCandidate score(CollisionStrategy strategy, Vec3d candidate, Vec3d original,
                                                    Vec3d focus, Vec3d previous, String diagnosis) {
        double displacementPenalty = Math.min(1.0, candidate.distanceTo(original) / 8.0);
        double focusDistance = Math.abs(candidate.distanceTo(focus) - original.distanceTo(focus));
        double focusPenalty = Math.min(1.0, focusDistance / 8.0);
        double continuityBonus = previous == null ? 0.0
                : Math.max(0.0, 1.0 - Math.min(1.0, candidate.distanceTo(previous) / 6.0));
        double strategyBias = switch (strategy) {
            case NONE -> 1.0;
            case LATERAL_TRANSLATION -> 0.9;
            case ORBIT_RADIUS_REDUCTION -> 0.88;
            case RADIAL_PULL -> 0.85;
            case RAISE -> 0.8;
            case PATH_SHORTENING -> 0.78;
            case INSERTED_CONTROL_POINTS -> 0.75;
        };
        double score = strategyBias * (1.0 - 0.45 * displacementPenalty - 0.25 * focusPenalty)
                + 0.2 * continuityBonus;
        return new CollisionStrategyCandidate(strategy, candidate, score, diagnosis);
    }
}
