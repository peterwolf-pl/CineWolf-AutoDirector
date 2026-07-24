package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Keeps collision corrections temporally coherent while delegating world-specific safety checks to the caller.
 */
public final class CollisionPathContinuity {
    private static final double RADIAL_STEP = 0.125;
    private static final double SWEEP_STEP = 0.05;
    private static final double RAISE_STEP = 0.25;
    private static final double MAX_RAISE = 6.0;
    private static final double MIN_FOCUS_DISTANCE = 1.0;
    private static final double RELEASE_DELAY_SECONDS = 0.45;
    private static final double RECOVERY_SPEED_BLOCKS_PER_SECOND = 1.35;
    private static final double MAX_CORRECTION_CHANGE_BLOCKS_PER_SECOND = 2.4;
    private static final double MINIMUM_MAX_CORRECTION_CHANGE = 0.28;
    private static final double POSITION_EPSILON = 1.0e-6;
    private static final int BOUNDARY_REFINEMENT_STEPS = 7;
    private static final int MAX_SAFETY_PROBES = 512;

    public Optional<Vec3d> resolve(Vec3d original, Vec3d focus, double clearance, double deltaSeconds,
                                   State state, Predicate<Vec3d> isSafe) {
        if (original == null || focus == null || state == null || isSafe == null
                || !original.isFinite() || !focus.isFinite()) {
            return Optional.empty();
        }

        double safeDeltaSeconds = Double.isFinite(deltaSeconds)
                ? Math.max(0.0, Math.min(0.5, deltaSeconds)) : 0.0;
        ProbeBudget boundedSafety = new ProbeBudget(isSafe, MAX_SAFETY_PROBES);
        boolean originalSafe = boundedSafety.test(original);
        Vec3d predicted = state.initialized
                ? state.previousAdjusted.add(original.subtract(state.previousOriginal)) : original;
        double maximumCorrectionChange = Math.max(MINIMUM_MAX_CORRECTION_CHANGE,
                MAX_CORRECTION_CHANGE_BLOCKS_PER_SECOND * safeDeltaSeconds);
        double maximumTransitionDistance = state.initialized
                ? state.previousAdjusted.distanceTo(predicted) + maximumCorrectionChange
                : Double.POSITIVE_INFINITY;
        boolean previousSafe = state.initialized && boundedSafety.test(state.previousAdjusted);

        if (!originalSafe) {
            state.avoidanceActive = true;
            state.unobstructedSeconds = 0.0;
        } else if (state.avoidanceActive) {
            state.unobstructedSeconds += safeDeltaSeconds;
        }

        Vec3d selected = null;
        if (state.initialized && state.avoidanceActive && boundedSafety.test(predicted)) {
            if (originalSafe && state.unobstructedSeconds >= RELEASE_DELAY_SECONDS) {
                double recoveryDistance = RECOVERY_SPEED_BLOCKS_PER_SECOND * safeDeltaSeconds;
                selected = advanceWhileSafe(predicted, original, recoveryDistance, boundedSafety);
            } else {
                selected = predicted;
            }
        }

        if (selected == null) {
            List<Vec3d> candidates = safeCandidates(original, focus, clearance, originalSafe, boundedSafety);
            if (candidates.isEmpty()) {
                candidates = combinedSafeCandidates(original, focus, clearance, boundedSafety);
            }
            if (!candidates.isEmpty()) {
                selected = closestSafeCandidate(candidates, state.initialized ? predicted : original,
                        state.initialized, boundedSafety);
            }
        }

        if (state.initialized && previousSafe) {
            selected = selected == null ? state.previousAdjusted
                    : routeSafely(state.previousAdjusted, selected, focus, maximumTransitionDistance, boundedSafety);
        }

        if (selected == null) {
            return fallback(original, predicted, state, boundedSafety,
                    boundedSafety.exhausted() ? "probe_budget_exhausted" : "no_safe_candidate");
        }
        if (state.initialized && !previousSafe
                && selected.distanceTo(state.previousAdjusted) > maximumTransitionDistance + POSITION_EPSILON) {
            return fallback(original, predicted, state, boundedSafety, "no_continuous_safe_transition");
        }

        if (originalSafe && selected.distanceTo(original) <= POSITION_EPSILON) {
            state.avoidanceActive = false;
            state.unobstructedSeconds = 0.0;
            selected = original;
        } else if (selected.distanceTo(original) > POSITION_EPSILON) {
            state.avoidanceActive = true;
        }
        state.update(original, selected);
        state.markResolved();
        return Optional.of(selected);
    }

    private static Optional<Vec3d> fallback(Vec3d original, Vec3d predicted, State state,
                                             ProbeBudget budget, String reason) {
        Vec3d fallback = state.initialized ? predicted : original;
        state.update(original, fallback);
        state.markUnresolved(budget.exhausted() ? "probe_budget_exhausted" : reason);
        return Optional.of(fallback);
    }

    private static Vec3d routeSafely(Vec3d start, Vec3d target, Vec3d focus, double maximumDistance,
                                     Predicate<Vec3d> isSafe) {
        double targetDistance = start.distanceTo(target);
        if (targetDistance <= POSITION_EPSILON || maximumDistance <= 0.0) return start;
        double allowedDistance = Math.min(targetDistance, maximumDistance);
        Vec3d intended = start.lerp(target, allowedDistance / targetDistance);
        Vec3d direct = advanceWhileSafe(start, target, allowedDistance, isSafe);
        if (direct.distanceTo(intended) <= SWEEP_STEP) return direct;

        double retreatDistance = Math.max(0.0, start.distanceTo(focus) - MIN_FOCUS_DISTANCE);
        if (retreatDistance <= POSITION_EPSILON) return direct;
        Vec3d retreat = advanceWhileSafe(start, focus,
                Math.min(maximumDistance, retreatDistance), isSafe);
        return retreat.distanceTo(start) > POSITION_EPSILON ? retreat : direct;
    }

    private static List<Vec3d> safeCandidates(Vec3d original, Vec3d focus, double clearance,
                                               boolean originalSafe, Predicate<Vec3d> isSafe) {
        List<Vec3d> candidates = new ArrayList<>();
        if (originalSafe) candidates.add(original);

        findRadialCandidate(original, focus, clearance, isSafe).ifPresent(candidates::add);

        for (double raise = RAISE_STEP; raise <= MAX_RAISE + POSITION_EPSILON; raise += RAISE_STEP) {
            Vec3d candidate = original.add(Vec3d.UP.multiply(raise));
            if (isSafe.test(candidate)) {
                candidates.add(candidate);
                break;
            }
        }
        return candidates;
    }

    private static List<Vec3d> combinedSafeCandidates(Vec3d original, Vec3d focus, double clearance,
                                                       Predicate<Vec3d> isSafe) {
        List<Vec3d> candidates = new ArrayList<>();
        for (double raise = RAISE_STEP * 2.0; raise <= MAX_RAISE + POSITION_EPSILON;
             raise += RAISE_STEP * 2.0) {
            findRadialCandidate(original.add(Vec3d.UP.multiply(raise)), focus, clearance, isSafe)
                    .ifPresent(candidates::add);
        }
        return candidates;
    }

    private static Vec3d closestSafeCandidate(List<Vec3d> candidates, Vec3d preferred,
                                              boolean approachPreferred, Predicate<Vec3d> isSafe) {
        Vec3d selected = candidates.stream()
                .min(Comparator.comparingDouble(candidate -> candidate.distanceTo(preferred)))
                .orElseThrow();
        if (approachPreferred && !isSafe.test(preferred)) {
            selected = advanceWhileSafe(selected, preferred, selected.distanceTo(preferred), isSafe);
        }
        return selected;
    }

    private static Optional<Vec3d> findRadialCandidate(Vec3d desired, Vec3d focus, double clearance,
                                                        Predicate<Vec3d> isSafe) {
        Vec3d offset = desired.subtract(focus);
        double distance = offset.length();
        double minimumDistance = Math.max(MIN_FOCUS_DISTANCE, Math.max(0.05, clearance) * 2.0);
        if (!Double.isFinite(distance) || distance < minimumDistance) return Optional.empty();

        Vec3d direction = offset.multiply(1.0 / distance);
        double previousDistance = distance;
        for (double candidateDistance = distance; candidateDistance >= minimumDistance;
             candidateDistance -= RADIAL_STEP) {
            Vec3d candidate = focus.add(direction.multiply(candidateDistance));
            if (isSafe.test(candidate)) {
                double safeDistance = refineRadialBoundary(focus, direction, candidateDistance,
                        previousDistance, isSafe);
                return Optional.of(focus.add(direction.multiply(safeDistance)));
            }
            previousDistance = candidateDistance;
        }
        return Optional.empty();
    }

    private static double refineRadialBoundary(Vec3d focus, Vec3d direction, double safeDistance,
                                                double unsafeDistance, Predicate<Vec3d> isSafe) {
        double low = safeDistance;
        double high = Math.max(safeDistance, unsafeDistance);
        for (int iteration = 0; iteration < BOUNDARY_REFINEMENT_STEPS; iteration++) {
            double middle = (low + high) * 0.5;
            if (isSafe.test(focus.add(direction.multiply(middle)))) low = middle;
            else high = middle;
        }
        return low;
    }

    private static Vec3d advanceWhileSafe(Vec3d start, Vec3d target, double maximumDistance,
                                           Predicate<Vec3d> isSafe) {
        double totalDistance = start.distanceTo(target);
        if (totalDistance <= POSITION_EPSILON || maximumDistance <= 0.0) return start;
        double travel = Math.min(totalDistance, maximumDistance);
        Vec3d endpoint = start.lerp(target, travel / totalDistance);
        int segments = Math.max(1, (int) Math.ceil(travel / SWEEP_STEP));
        Vec3d lastSafe = start;
        double lastSafeAmount = 0.0;
        for (int segment = 1; segment <= segments; segment++) {
            double amount = segment / (double) segments;
            Vec3d candidate = start.lerp(endpoint, amount);
            if (!isSafe.test(candidate)) {
                double low = lastSafeAmount;
                double high = amount;
                for (int iteration = 0; iteration < BOUNDARY_REFINEMENT_STEPS; iteration++) {
                    double middle = (low + high) * 0.5;
                    Vec3d refined = start.lerp(endpoint, middle);
                    if (isSafe.test(refined)) {
                        low = middle;
                        lastSafe = refined;
                    } else {
                        high = middle;
                    }
                }
                return lastSafe;
            }
            lastSafe = candidate;
            lastSafeAmount = amount;
        }
        return endpoint;
    }

    public static final class State {
        private Vec3d previousOriginal = Vec3d.ZERO;
        private Vec3d previousAdjusted = Vec3d.ZERO;
        private boolean initialized;
        private boolean avoidanceActive;
        private double unobstructedSeconds;
        private boolean lastResolutionSafe = true;
        private String lastFailureReason = "";

        public void reset() {
            previousOriginal = Vec3d.ZERO;
            previousAdjusted = Vec3d.ZERO;
            initialized = false;
            avoidanceActive = false;
            unobstructedSeconds = 0.0;
            lastResolutionSafe = true;
            lastFailureReason = "";
        }

        public boolean avoidanceActive() {
            return avoidanceActive;
        }

        public boolean lastResolutionSafe() {
            return lastResolutionSafe;
        }

        public String lastFailureReason() {
            return lastFailureReason;
        }

        private void update(Vec3d original, Vec3d adjusted) {
            previousOriginal = original;
            previousAdjusted = adjusted;
            initialized = true;
        }

        private void markResolved() {
            lastResolutionSafe = true;
            lastFailureReason = "";
        }

        private void markUnresolved(String reason) {
            lastResolutionSafe = false;
            lastFailureReason = reason;
            avoidanceActive = true;
            unobstructedSeconds = 0.0;
        }
    }

    private static final class ProbeBudget implements Predicate<Vec3d> {
        private final Predicate<Vec3d> delegate;
        private int remaining;

        private ProbeBudget(Predicate<Vec3d> delegate, int maximumProbes) {
            this.delegate = delegate;
            this.remaining = maximumProbes;
        }

        @Override
        public boolean test(Vec3d position) {
            if (remaining <= 0) return false;
            remaining--;
            return delegate.test(position);
        }

        private boolean exhausted() {
            return remaining <= 0;
        }
    }
}
