package pl.peterwolf.cinewolf.montage.event;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.OTHER;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.PLAYER;

class EventMergerScorerTest {
    private final EventMerger merger = new EventMerger();

    @Test
    void eventIdsAreStableRegardlessOfTargetSetOrder() {
        ReplayEvent first = event(ReplayEventType.COMBAT, 10, 12, 15, 0.8, 0.9,
                Set.of(PLAYER, OTHER), new Vec3d(1, 2, 3));
        ReplayEvent second = event(ReplayEventType.COMBAT, 10, 12, 15, 0.8, 0.9,
                new java.util.LinkedHashSet<>(List.of(OTHER, PLAYER)), new Vec3d(100, 0, 0));

        assertEquals(first.eventId(), second.eventId());
    }

    @Test
    void mergesAdjacentSameTypeEventsAndKeepsStrongestPeak() {
        ReplayEvent first = event(ReplayEventType.DAMAGE, 10, 10, 12, 0.3, 0.8,
                Set.of(PLAYER), Vec3d.ZERO);
        ReplayEvent second = event(ReplayEventType.DAMAGE, 18, 18, 20, 0.9, 0.95,
                Set.of(PLAYER), new Vec3d(2, 0, 0));

        ReplayEvent merged = merger.mergeAndDeduplicate(List.of(second, first),
                DetectorThresholds.defaults(), 100).getFirst();

        assertEquals(10, merged.startReplayTime());
        assertEquals(18, merged.peakReplayTime());
        assertEquals(20, merged.endReplayTime());
        assertEquals(0.9, merged.magnitude(), 1.0e-9);
        assertEquals(2, merged.evidence().measurements().size());
    }

    @Test
    void damageAndCombatUseTheirDedicatedMergeWindows() {
        DetectorThresholds defaults = DetectorThresholds.defaults();
        ReplayEvent damageOne = event(ReplayEventType.DAMAGE, 0, 0, 0, 0.4, 0.9, Set.of(PLAYER), Vec3d.ZERO);
        ReplayEvent damageTwo = event(ReplayEventType.DAMAGE, defaults.damageMergeGapTicks() + 1,
                defaults.damageMergeGapTicks() + 1, defaults.damageMergeGapTicks() + 1,
                0.4, 0.9, Set.of(PLAYER), Vec3d.ZERO);
        ReplayEvent combatOne = event(ReplayEventType.COMBAT, 100, 100, 100, 0.4, 0.9,
                Set.of(PLAYER), Vec3d.ZERO);
        ReplayEvent combatTwo = event(ReplayEventType.COMBAT, 100 + defaults.eventMergeGapTicks() + 1,
                100 + defaults.eventMergeGapTicks() + 1, 100 + defaults.eventMergeGapTicks() + 1,
                0.4, 0.9, Set.of(PLAYER), Vec3d.ZERO);

        List<ReplayEvent> merged = merger.mergeAndDeduplicate(
                List.of(damageOne, damageTwo, combatOne, combatTwo), defaults, 100);

        assertEquals(2, merged.stream().filter(event -> event.type() == ReplayEventType.DAMAGE).count());
        assertEquals(1, merged.stream().filter(event -> event.type() == ReplayEventType.COMBAT).count());
    }

    @Test
    void annotatesRelatedEventsWithoutCollapsingThem() {
        ReplayEvent speed = event(ReplayEventType.HIGH_SPEED, 10, 15, 20, 0.8, 0.9,
                Set.of(PLAYER), Vec3d.ZERO);
        ReplayEvent acceleration = event(ReplayEventType.ACCELERATION, 12, 14, 18, 0.7, 0.8,
                Set.of(PLAYER), new Vec3d(1, 0, 0));

        List<ReplayEvent> result = merger.mergeAndDeduplicate(List.of(speed, acceleration),
                DetectorThresholds.defaults(), 100);

        assertEquals(2, result.size());
        assertTrue(result.stream().filter(event -> event.type() == ReplayEventType.HIGH_SPEED).findFirst()
                .orElseThrow().evidence().relatedTypes().contains(ReplayEventType.ACCELERATION));
        assertTrue(result.stream().filter(event -> event.type() == ReplayEventType.ACCELERATION).findFirst()
                .orElseThrow().evidence().relatedTypes().contains(ReplayEventType.HIGH_SPEED));
    }

    @Test
    void preservesDistinctMarkersAtSameTimeButDeduplicatesRepeatedObservation() {
        ReplayEvent first = marker("00000000-0000-0000-0000-000000000090", "first");
        ReplayEvent second = marker("00000000-0000-0000-0000-000000000091", "second");

        List<ReplayEvent> result = merger.mergeAndDeduplicate(List.of(first, first, second),
                DetectorThresholds.defaults(), 100);

        assertEquals(2, result.size());
        assertEquals(Set.of(first.eventId(), second.eventId()), result.stream()
                .map(ReplayEvent::eventId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void appliesDocumentedScoringFormulaAndExplainsEveryAdjustment() {
        ReplayEvent value = event(ReplayEventType.HIGH_SPEED, 10, 20, 30, 0.8, 0.6,
                Set.of(PLAYER), Vec3d.ZERO);
        EventScoringContext context = new EventScoringContext(EventScoringProfile.defaults(),
                Map.of(ReplayEventType.HIGH_SPEED, 0.5), Set.of(PLAYER),
                Map.of(ReplayEventType.HIGH_SPEED, 2), Set.of(20L));

        ScoredReplayEvent scored = new DefaultReplayEventScorer().score(value, context);

        assertEquals(0.802375, scored.finalScore(), 1.0e-9);
        assertEquals(0.5, scored.uniquenessScore(), 1.0e-9);
        assertEquals(0.5, scored.presetCompatibilityScore(), 1.0e-9);
        assertTrue(scored.scoringReasons().stream().anyMatch(reason -> reason.startsWith("marker_bonus=")));
        assertTrue(scored.scoringReasons().stream().anyMatch(reason -> reason.startsWith("selected_target_bonus=")));
        assertTrue(scored.scoringReasons().stream().anyMatch(reason -> reason.startsWith("repetition_penalty=")));
        assertTrue(scored.scoringReasons().stream().anyMatch(reason -> reason.startsWith("technical_risk_penalty=")));
    }

    @Test
    void repetitionPenaltyLowersOtherwiseEqualEventScore() {
        ReplayEvent value = event(ReplayEventType.POSITION_CHANGE, 10, 10, 20, 0.7, 0.9,
                Set.of(PLAYER), Vec3d.ZERO);
        DefaultReplayEventScorer scorer = new DefaultReplayEventScorer();
        ScoredReplayEvent unique = scorer.score(value, new EventScoringContext(EventScoringProfile.defaults(),
                Map.of(), Set.of(), Map.of(ReplayEventType.POSITION_CHANGE, 1), Set.of()));
        ScoredReplayEvent repeated = scorer.score(value, new EventScoringContext(EventScoringProfile.defaults(),
                Map.of(), Set.of(), Map.of(ReplayEventType.POSITION_CHANGE, 8), Set.of()));

        assertTrue(repeated.finalScore() < unique.finalScore());
        assertTrue(repeated.repetitionPenalty() > unique.repetitionPenalty());
    }

    @Test
    void eventLimitKeepsHighestQualityEventsDeterministically() {
        ReplayEvent low = event(ReplayEventType.PAUSE, 0, 0, 10, 0.2, 0.4,
                Set.of(PLAYER), Vec3d.ZERO);
        ReplayEvent medium = event(ReplayEventType.POSITION_CHANGE, 30, 30, 30, 0.6, 0.8,
                Set.of(PLAYER), new Vec3d(30, 0, 0));
        ReplayEvent high = event(ReplayEventType.DEATH, 60, 60, 60, 1.0, 1.0,
                Set.of(OTHER), new Vec3d(60, 0, 0));

        List<ReplayEvent> result = merger.mergeAndDeduplicate(List.of(medium, low, high),
                DetectorThresholds.defaults(), 2);

        assertEquals(Set.of(medium.eventId(), high.eventId()), result.stream()
                .map(ReplayEvent::eventId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(result, merger.mergeAndDeduplicate(List.of(high, low, medium),
                DetectorThresholds.defaults(), 2));
    }

    private static ReplayEvent event(ReplayEventType type, long start, long peak, long end,
                                     double magnitude, double confidence, Set<TargetReference> targets,
                                     Vec3d location) {
        EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT,
                EventEvidence.Measurement.observed("test", magnitude, "ratio"));
        return ReplayEvent.create(type, start, peak, end, targets, location, magnitude, confidence, evidence);
    }

    private static ReplayEvent marker(String id, String label) {
        UUID uuid = UUID.fromString(id);
        EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.REPLAY_MARKER,
                        EventEvidence.Measurement.observed("marker_present", 1, "boolean"))
                .withAttribute("marker_label", label);
        return new ReplayEvent(uuid, ReplayEventType.REPLAY_MARKER, 50, 50, 50,
                Set.of(), Vec3d.ZERO, 0.8, 1.0, evidence);
    }
}
