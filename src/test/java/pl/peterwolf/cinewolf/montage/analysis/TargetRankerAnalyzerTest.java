package pl.peterwolf.cinewolf.montage.analysis;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.*;

class TargetRankerAnalyzerTest {
    @Test
    void automaticRankingPrefersActivePlayerOverPassiveEntity() {
        List<ReplaySample> samples = List.of(sample(40, snapshot(PLAYER, 20, 0, 0), snapshot(OTHER, 50, 0, 0)),
                sample(0, snapshot(PLAYER, 0, 0, 0), snapshot(OTHER, 50, 0, 0)),
                sample(20, snapshot(PLAYER, 10, 0, 0), snapshot(OTHER, 50, 0, 0)));
        Map<pl.peterwolf.cinewolf.model.TargetReference, List<MovementMetrics>> metrics =
                new MovementMetricsCalculator().calculate(samples, 0.15);
        ReplayEvent combat = ReplayEvent.create(ReplayEventType.COMBAT, 20, 20, 20, Set.of(PLAYER),
                new Vec3d(10, 0, 0), 0.9, 0.9, evidence());

        List<RankedReplayTarget> ranked = new TargetRanker().rank(samples, metrics, List.of(combat), Set.of(), 10);

        assertEquals(PLAYER, ranked.getFirst().target());
        assertTrue(ranked.getFirst().score() > ranked.getLast().score());
        assertTrue(ranked.getFirst().scoringReasons().stream()
                .anyMatch(reason -> reason.startsWith("event_centrality=")));
    }

    @Test
    void visibilityDoesNotIncludeIntervalsWhileTargetIsAbsent() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)), ReplaySample.empty(20),
                sample(40, snapshot(PLAYER, 0, 0, 0)));

        RankedReplayTarget result = new TargetRanker().rank(samples, Map.of(PLAYER, List.of()),
                List.of(), Set.of(), 1).getFirst();

        assertEquals(0, result.visibleDurationTicks());
    }

    @Test
    void explicitTargetSelectionOverridesAutomaticActivityRanking() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0), snapshot(OTHER, 50, 0, 0)),
                sample(20, snapshot(PLAYER, 10, 0, 0), snapshot(OTHER, 50, 0, 0)),
                sample(40, snapshot(PLAYER, 20, 0, 0), snapshot(OTHER, 50, 0, 0)));
        Map<pl.peterwolf.cinewolf.model.TargetReference, List<MovementMetrics>> metrics =
                new MovementMetricsCalculator().calculate(samples, 0.15);

        List<RankedReplayTarget> ranked = new TargetRanker().rank(samples, metrics, List.of(), Set.of(OTHER), 10);

        assertEquals(OTHER, ranked.getFirst().target());
        assertTrue(ranked.getFirst().scoringReasons().stream()
                .anyMatch(reason -> reason.equals("selected_bonus=1.0000")));
    }

    @Test
    void completePipelineProducesRankedImmutableDeterministicResult() {
        List<ReplaySample> samples = representativeSamples();
        ReplayAnalysisRequest request = new ReplayAnalysisRequest(0, 100, Set.of(PLAYER), true,
                EnumSet.allOf(ReplayEventType.class), 0.7, 4, 16);
        List<AnalysisStage> stages = new ArrayList<>();
        DefaultReplayAnalyzer analyzer = DefaultReplayAnalyzer.createDefault();

        ReplayAnalysisResult first = analyzer.analyze(request, ReplayAnalysisContext.defaults(samples),
                (stage, progress, completed, total) -> stages.add(stage), CancellationToken.NONE);
        ReplayAnalysisResult second = analyzer.analyze(request, ReplayAnalysisContext.defaults(samples),
                AnalysisProgressListener.NONE, CancellationToken.NONE);

        assertFalse(first.mergedEvents().isEmpty());
        assertEquals(first.mergedEvents().size(), first.rankedEvents().size());
        assertTrue(java.util.stream.IntStream.range(1, first.rankedEvents().size())
                .allMatch(index -> first.rankedEvents().get(index - 1).finalScore()
                        >= first.rankedEvents().get(index).finalScore()));
        assertEquals(first.scenes().size(), first.statistics().sceneCount());
        assertEquals(PLAYER, first.primaryTarget().orElseThrow().target());
        assertEquals(AnalysisStage.COMPLETE, stages.getLast());
        assertTrue(stages.containsAll(EnumSet.allOf(AnalysisStage.class)));
        assertEquals(first.mergedEvents().stream().map(ReplayEvent::eventId).toList(),
                second.mergedEvents().stream().map(ReplayEvent::eventId).toList());
        assertEquals(first.scenes().stream().map(scene -> scene.sceneId()).toList(),
                second.scenes().stream().map(scene -> scene.sceneId()).toList());
        assertThrows(UnsupportedOperationException.class, () -> first.samples().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.movementMetrics().clear());
    }

    @Test
    void cancellationStopsAnalysisBeforeExpensiveStages() {
        ReplayAnalysisRequest request = ReplayAnalysisRequest.defaults(0, 100);

        assertThrows(AnalysisCancelledException.class, () -> DefaultReplayAnalyzer.createDefault().analyze(
                request, ReplayAnalysisContext.defaults(representativeSamples()), AnalysisProgressListener.NONE,
                () -> true));
    }

    @Test
    void emptyRangeReturnsWarningAndCompletedProgress() {
        List<AnalysisStage> stages = new ArrayList<>();

        ReplayAnalysisResult result = DefaultReplayAnalyzer.createDefault().analyze(
                ReplayAnalysisRequest.defaults(200, 300), ReplayAnalysisContext.defaults(representativeSamples()),
                (stage, progress, completed, total) -> stages.add(stage), CancellationToken.NONE);

        assertTrue(result.samples().isEmpty());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.code().equals("analysis.no_samples_in_range")));
        assertEquals(AnalysisStage.COMPLETE, stages.getLast());
    }

    @Test
    void sampleLimitPreservesRangeEndpointsAndSignalSamples() {
        List<ReplaySample> samples = new ArrayList<>();
        for (int tick = 0; tick < 30; tick++) {
            List<ReplayMarkerSnapshot> markers = tick == 7 || tick == 14 || tick == 23
                    ? List.of(new ReplayMarkerSnapshot(UUID.nameUUIDFromBytes(("marker-" + tick).getBytes()),
                    tick, "marker-" + tick, Optional.empty())) : List.of();
            samples.add(AnalysisTestFixtures.sample(tick, Map.of(PLAYER, snapshot(PLAYER, tick, 0, 0)),
                    markers, List.of()));
        }
        ReplayAnalysisContext context = new ReplayAnalysisContext(samples, Map.of(), null, null,
                new AnalysisLimits(4, 5, 100));

        ReplayAnalysisResult result = DefaultReplayAnalyzer.createDefault().analyze(
                new ReplayAnalysisRequest(0, 29, Set.of(PLAYER), false, Set.of(ReplayEventType.REPLAY_MARKER),
                        0.5, 20, 20), context, AnalysisProgressListener.NONE, CancellationToken.NONE);

        assertEquals(5, result.samples().size());
        assertEquals(0, result.samples().getFirst().replayTime());
        assertEquals(29, result.samples().getLast().replayTime());
        assertEquals(Set.of(7L, 14L, 23L), result.samples().stream().filter(sample -> !sample.markers().isEmpty())
                .map(ReplaySample::replayTime).collect(java.util.stream.Collectors.toSet()));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.code().equals("analysis.sample_limit_applied")));
    }

    @Test
    void requestValidationClampsSensitivityAndSamplingRates() {
        ReplayAnalysisRequest request = new ReplayAnalysisRequest(0, 20, Set.of(), true, Set.of(),
                99, -4, 999);

        assertEquals(1.0, request.sensitivity());
        assertEquals(1, request.coarseSamplesPerSecond());
        assertEquals(60, request.detailedSamplesPerSecond());
        assertEquals(EnumSet.allOf(ReplayEventType.class), request.enabledEventTypes());
        assertThrows(IllegalArgumentException.class, () -> ReplayAnalysisRequest.defaults(20, 20));
    }

    @Test
    void analyzerRejectsSignalsWhosePeakFallsOutsideSelectedRange() {
        ObservedReplayAction outside = new ObservedReplayAction.BlockPlaced(75, Optional.of(PLAYER),
                Vec3d.ZERO, "stone");
        ReplaySample bundled = AnalysisTestFixtures.sample(10, Map.of(PLAYER, snapshot(PLAYER, 0, 0, 0)),
                List.of(), List.of(outside));

        ReplayAnalysisResult result = DefaultReplayAnalyzer.createDefault().analyze(
                new ReplayAnalysisRequest(0, 20, Set.of(PLAYER), false,
                        Set.of(ReplayEventType.BLOCK_PLACEMENT), 0.5, 4, 16),
                ReplayAnalysisContext.defaults(List.of(sample(0, snapshot(PLAYER, 0, 0, 0)), bundled,
                        sample(20, snapshot(PLAYER, 0, 0, 0)))),
                AnalysisProgressListener.NONE, CancellationToken.NONE);

        assertTrue(result.mergedEvents().isEmpty());
    }

    private static List<ReplaySample> representativeSamples() {
        List<ReplaySample> samples = new ArrayList<>();
        for (int tick = 0; tick <= 100; tick += 10) {
            ReplayEntitySnapshot player = snapshot(PLAYER, tick / 2.0, tick >= 60 ? (tick - 60) / 5.0 : 0, 0);
            double health = tick >= 50 ? 15 : 20;
            boolean alive = tick < 70;
            ReplayEntitySnapshot other = state(OTHER, 22, 0, 0, alive ? health : 0, 20,
                    tick == 50 ? 5 : 0, false, false, alive, true, 0,
                    Optional.empty(), Optional.empty(), false, false, "minecraft:overworld", false);
            List<ObservedReplayAction> actions = tick == 40 ? List.of(new ObservedReplayAction.CombatSignal(40,
                    ObservedReplayAction.CombatSignalType.ATTACK, Optional.of(PLAYER), Optional.of(OTHER),
                    new Vec3d(21, 0, 0), 0.8)) : List.of();
            List<ReplayMarkerSnapshot> markers = tick == 60 ? List.of(new ReplayMarkerSnapshot(
                    UUID.fromString("00000000-0000-0000-0000-000000000093"), 60, "climax",
                    Optional.of(new Vec3d(30, 0, 0)))) : List.of();
            samples.add(AnalysisTestFixtures.sample(tick, Map.of(PLAYER, player, OTHER, other), markers, actions));
        }
        return List.copyOf(samples);
    }

    private static EventEvidence evidence() {
        return EventEvidence.of(EventEvidence.DetectionSource.DIRECT_ACTION,
                EventEvidence.Measurement.observed("test", 1, "boolean"));
    }
}
