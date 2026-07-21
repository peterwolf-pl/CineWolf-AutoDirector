package pl.peterwolf.cinewolf.montage.timeline;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MontageTimelinePlacementResolverTest {
    private final MontageTimelinePlacementResolver resolver = new MontageTimelinePlacementResolver();
    private final List<MontageTimelineConflictDetector.ExistingTrack> existing = List.of(
            new MontageTimelineConflictDetector.ExistingTrack(
                    MontageTimelineConflictDetector.TrackType.CAMERA, true, List.of(80, 220)));

    @Test
    void cancelIsTheSafeDefaultWhenAnActiveSegmentConflicts() {
        MontageTimelinePlacementResolver.Resolution result = resolver.resolve(plan(),
                MontageTimelineWriteOptions.cancelOnConflict(), existing);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("montage.timeline.conflicts_cancelled"));
    }

    @Test
    void replaceRequiresTheExactConfirmedOutputInterval() {
        MontageTimelinePlacementResolver.Resolution missing = resolver.resolve(plan(),
                new MontageTimelineWriteOptions(MontageTimelineConflictMode.REPLACE, Optional.empty()), existing);
        MontageTimelinePlacementResolver.Resolution mismatched = resolver.resolve(plan(),
                MontageTimelineWriteOptions.replace(new MontageTimelineInterval(99, 161)), existing);
        MontageTimelinePlacementResolver.Resolution confirmed = resolver.resolve(plan(),
                MontageTimelineWriteOptions.replace(new MontageTimelineInterval(100, 160)), existing);

        assertTrue(missing.errors().contains("montage.timeline.replace_not_confirmed"));
        assertTrue(mismatched.errors().contains("montage.timeline.replace_interval_mismatch"));
        assertTrue(confirmed.valid());
    }

    @Test
    void placeAfterLastIsRejectedBecauseThePayloadIsBoundToSourceReplayTicks() {
        MontageTimelinePlacementResolver.Resolution result = resolver.resolve(plan(),
                MontageTimelineWriteOptions.placeAfterLast(), existing);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("montage.timeline.place_after_last_source_bound"));
        assertTrue(result.plan().isEmpty());
    }

    private static MontageTimelineWritePlan plan() {
        return new MontageTimelineWritePlan(new UUID(8L, 9L), 100,
                new MontageTimelineInterval(100, 160),
                List.of(new MontageTimelineWritePlan.CameraPoint(100, Vec3d.ZERO, 0, 0, 0, EasingType.LINEAR),
                        new MontageTimelineWritePlan.CameraPoint(160, Vec3d.UP, 0, 0, 0, EasingType.LINEAR)),
                List.of(new MontageTimelineWritePlan.FovPoint(100, 70, EasingType.LINEAR),
                        new MontageTimelineWritePlan.FovPoint(160, 70, EasingType.LINEAR)),
                List.of(new MontageTimelineWritePlan.TimelapsePoint(100, 20),
                        new MontageTimelineWritePlan.TimelapsePoint(160, 80)), 20);
    }
}
