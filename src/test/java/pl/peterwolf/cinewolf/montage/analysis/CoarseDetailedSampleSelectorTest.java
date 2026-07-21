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
}
