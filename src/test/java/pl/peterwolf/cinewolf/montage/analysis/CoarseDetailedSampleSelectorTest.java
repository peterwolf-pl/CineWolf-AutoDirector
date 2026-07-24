package pl.peterwolf.cinewolf.montage.analysis;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.PLAYER;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.sample;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.snapshot;

class CoarseDetailedSampleSelectorTest {
    private final CoarseDetailedSampleSelector selector = new CoarseDetailedSampleSelector();

    @Test
    void keepsCoarseBoundariesAndAddsDetailAroundMotionChanges() {
        List<ReplaySample> samples = new ArrayList<>();
        for (int tick = 0; tick <= 100; tick++) {
            double x = tick < 50 ? 0.0 : tick - 50.0;
            samples.add(sample(tick, snapshot(PLAYER, x, 0, 0)));
        }
        ReplayAnalysisRequest request = new ReplayAnalysisRequest(0, 100, java.util.Set.of(PLAYER), false,
                EnumSet.allOf(pl.peterwolf.cinewolf.montage.event.ReplayEventType.class), 0.5, 2, 20);

        SampleSelection result = selector.select(samples, request, DetectorThresholds.defaults());

        assertEquals(0, result.coarseSamples().getFirst().replayTime());
        assertEquals(100, result.coarseSamples().getLast().replayTime());
        assertFalse(result.detailedWindows().isEmpty());
        assertTrue(result.detailedSamples().size() > result.coarseSamples().size());
        assertTrue(result.combinedSamples().stream().anyMatch(value -> value.replayTime() == 50));
    }

    @Test
    void preservesSignalsEvenWhenTheyFallBetweenRateLimitedTicks() {
        List<ReplaySample> samples = new ArrayList<>();
        for (int tick = 0; tick <= 20; tick++) samples.add(sample(tick, snapshot(PLAYER, 0, 0, 0)));
        ReplaySample signalled = AnalysisTestFixtures.sample(7, Map.of(PLAYER, snapshot(PLAYER, 0, 0, 0)), List.of(),
                List.of(new ObservedReplayAction.BlockPlaced(7, Optional.of(PLAYER), Vec3d.ZERO, "stone")));
        samples.set(7, signalled);
        ReplayAnalysisRequest request = ReplayAnalysisRequest.defaults(0, 20);

        SampleSelection result = selector.select(samples, request, DetectorThresholds.defaults());

        assertTrue(result.coarseSamples().stream().anyMatch(value -> value.replayTime() == 7));
        assertTrue(result.combinedSamples().stream().anyMatch(value -> value.replayTime() == 7));
    }

    @Test
    void selectsNoDetailForCompletelyInactiveSamples() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)),
                sample(10, snapshot(PLAYER, 0, 0, 0)), sample(20, snapshot(PLAYER, 0, 0, 0)));

        SampleSelection result = selector.select(samples, ReplayAnalysisRequest.defaults(0, 20),
                DetectorThresholds.defaults());

        assertTrue(result.detailedWindows().isEmpty());
        assertTrue(result.detailedSamples().isEmpty());
    }

    @Test
    void coverageBudgetLimitsDetailWindowsOnLongActiveReplays() {
        List<ReplaySample> samples = new ArrayList<>();
        // Continuous motion for 5 minutes of ticks → without a budget this becomes one huge detail region.
        for (int tick = 0; tick <= 6_000; tick += 5) {
            samples.add(sample(tick, snapshot(PLAYER, tick * 0.5, 0, 0)));
        }
        ReplayAnalysisRequest request = new ReplayAnalysisRequest(0, 6_000, java.util.Set.of(PLAYER), false,
                EnumSet.allOf(pl.peterwolf.cinewolf.montage.event.ReplayEventType.class), 0.5, 4, 16);

        SampleSelection unlimited = selector.select(samples, request, DetectorThresholds.defaults(), 1.0);
        SampleSelection limited = selector.select(samples, request, DetectorThresholds.defaults(), 0.2);

        long unlimitedSpan = unlimited.detailedWindows().stream()
                .mapToLong(window -> window.endReplayTime() - window.startReplayTime()).sum();
        long limitedSpan = limited.detailedWindows().stream()
                .mapToLong(window -> window.endReplayTime() - window.startReplayTime()).sum();

        assertTrue(limitedSpan < unlimitedSpan,
                "Coverage budget should shrink detailed windows: limited=" + limitedSpan
                        + " unlimited=" + unlimitedSpan);
        assertTrue(limitedSpan <= 6_000 * 0.25 + 250,
                "Limited coverage should stay near the budget: " + limitedSpan);
    }
}
