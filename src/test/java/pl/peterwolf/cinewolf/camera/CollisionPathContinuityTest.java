package pl.peterwolf.cinewolf.camera;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollisionPathContinuityTest {
    private final CollisionPathContinuity continuity = new CollisionPathContinuity();

    @Test
    void alternatingObstructionDoesNotSnapBackToTheOriginalPath() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        Vec3d desired = new Vec3d(0.0, 2.0, 8.0);
        Vec3d focus = new Vec3d(0.0, 2.0, 0.0);
        List<Vec3d> resolved = new ArrayList<>();

        for (int index = 0; index < 24; index++) {
            boolean obstructed = index % 2 == 0;
            Predicate<Vec3d> safe = obstructed ? position -> position.z() <= 4.0 : position -> true;
            resolved.add(continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state, safe).orElseThrow());
        }

        for (int index = 1; index < resolved.size(); index++) {
            assertTrue(resolved.get(index).distanceTo(resolved.get(index - 1)) < 0.02,
                    "Alternating collision state caused a visible camera pulse");
        }
        assertTrue(resolved.stream().allMatch(position -> position.z() <= 4.01));
    }

    @Test
    void stableClearanceReleasesTheOffsetGraduallyAfterHysteresis() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        Vec3d desired = new Vec3d(0.0, 2.0, 8.0);
        Vec3d focus = new Vec3d(0.0, 2.0, 0.0);
        Vec3d blocked = continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                position -> position.z() <= 4.0).orElseThrow();

        List<Vec3d> clearSamples = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            clearSamples.add(continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                    position -> true).orElseThrow());
        }

        assertEquals(blocked, clearSamples.get(2), "The collision offset was released before hysteresis elapsed");
        assertTrue(clearSamples.getLast().z() > blocked.z());
        for (int index = 1; index < clearSamples.size(); index++) {
            assertTrue(clearSamples.get(index).distanceTo(clearSamples.get(index - 1)) <= 0.151,
                    "Collision recovery exceeded its cinematic speed limit");
        }
    }

    @Test
    void unobstructedCameraUsesTheExactRequestedPosition() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        Vec3d desired = new Vec3d(3.0, 5.0, 8.0);
        Vec3d resolved = continuity.resolve(desired, Vec3d.ZERO, 0.28, 0.0, state,
                position -> true).orElseThrow();

        assertEquals(desired, resolved);
        assertFalse(state.avoidanceActive());
    }

    @Test
    void reportsUnresolvedWhenNoCandidateHasClearance() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        AtomicInteger probes = new AtomicInteger();
        Vec3d original = new Vec3d(0.0, 2.0, 8.0);
        assertEquals(original, continuity.resolve(original, Vec3d.ZERO, 0.28,
                1.0 / 12.0, state, position -> {
                    probes.incrementAndGet();
                    return false;
                }).orElseThrow());
        assertTrue(state.avoidanceActive());
        assertFalse(state.lastResolutionSafe());
        assertTrue(probes.get() <= 512, "An unresolved sample exceeded the synchronous probe budget");
    }

    @Test
    void keepsThePredictedPathInsteadOfSnappingToADistantBranch() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        Vec3d desired = new Vec3d(0.0, 2.0, 8.0);
        Vec3d focus = new Vec3d(0.0, 2.0, 0.0);
        assertEquals(desired, continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                position -> true).orElseThrow());

        Vec3d fallback = continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                position -> position.z() <= 4.0).orElseThrow();
        assertEquals(desired, fallback, "Collision fallback introduced a multi-block camera snap");
        assertFalse(state.lastResolutionSafe());
    }

    @Test
    void acceptsASmallContinuousCorrectionWhenEnteringAWallBoundary() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        Vec3d focus = new Vec3d(0.0, 2.0, 0.0);
        Predicate<Vec3d> beforeWall = position -> position.z() <= 4.0;
        Vec3d previous = new Vec3d(0.0, 2.0, 3.8);
        assertEquals(previous, continuity.resolve(previous, focus, 0.28, 1.0 / 12.0, state,
                beforeWall).orElseThrow());

        Vec3d resolved = continuity.resolve(new Vec3d(0.0, 2.0, 4.2), focus, 0.28,
                1.0 / 12.0, state, beforeWall).orElseThrow();
        assertTrue(resolved.z() >= 3.8 && resolved.z() <= 4.001);
    }

    @Test
    void stopsBeforeAnUnsafeGapBetweenSafeEndpoints() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        Vec3d focus = new Vec3d(0.0, 2.0, 0.0);
        Vec3d previous = new Vec3d(0.0, 2.0, 4.0);
        assertEquals(previous, continuity.resolve(previous, focus, 0.28, 1.0 / 12.0, state,
                position -> true).orElseThrow());

        Vec3d desired = new Vec3d(0.0, 2.0, 8.0);
        Predicate<Vec3d> disconnectedSafeRegions = position -> position.z() <= 4.2 || position.z() >= 7.8;
        Vec3d resolved = continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                disconnectedSafeRegions).orElseThrow();
        assertTrue(resolved.z() <= 4.2, "The selected camera segment crossed an unsafe gap");
        assertTrue(state.lastResolutionSafe());
    }

    @Test
    void unresolvedSampleKeepsThePreviousOffsetWithoutAPulse() {
        CollisionPathContinuity.State state = new CollisionPathContinuity.State();
        Vec3d desired = new Vec3d(0.0, 2.0, 8.0);
        Vec3d focus = new Vec3d(0.0, 2.0, 0.0);
        Vec3d adjusted = continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                position -> position.z() <= 4.0).orElseThrow();

        Vec3d fallback = continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                position -> false).orElseThrow();
        assertFalse(state.lastResolutionSafe());
        Vec3d firstClearSample = continuity.resolve(desired, focus, 0.28, 1.0 / 12.0, state,
                position -> true).orElseThrow();

        assertEquals(adjusted, fallback);
        assertEquals(fallback, firstClearSample, "Fallback offset was released as a one-sample pulse");
        assertTrue(state.lastResolutionSafe());
    }
}
