package pl.peterwolf.cinewolf.montage.plan;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.TestFixtures;
import pl.peterwolf.cinewolf.config.ShotDiversityConfig;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisWarning;
import pl.peterwolf.cinewolf.montage.analysis.RankedReplayTarget;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisRequest;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisStatistics;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.analysis.SampleSelection;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePacing;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetType;
import pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMontagePlannerTest {
    private static final Set<ShotType> AVAILABLE = EnumSet.allOf(ShotType.class);

    @Test
    void createsExactThirtySecondRegistryConstrainedPlan() {
        MontagePreset preset = MontagePresetRegistry.createDefault().get(MontagePresetType.THIRTY_SECONDS).orElseThrow();
        ReplayAnalysisResult analysis = analysis();
        MontageRequest request = MontageRequest.fromPreset(preset, 0, 2_400, Optional.of(TestFixtures.TARGET));

        MontagePlan plan = new DefaultMontagePlanner().createPlan(analysis, request,
                new MontagePlanningContext(AVAILABLE, SamplingSettings.defaults()));

        assertEquals(30.0, plan.statistics().plannedOutputDurationSeconds(), 1.0e-9);
        assertTrue(plan.shots().size() >= preset.minimumShotCount());
        assertTrue(plan.shots().size() <= preset.maximumShotCount());
        assertTrue(plan.shots().stream().allMatch(shot -> AVAILABLE.contains(shot.shotType())));
        assertPlanValid(plan);
    }

    @Test
    void warnsAndStillUsesMonotonicSourceForTrailer() {
        MontagePreset preset = MontagePresetRegistry.createDefault().get(MontagePresetType.TRAILER).orElseThrow();
        MontageRequest request = MontageRequest.fromPreset(preset, 0, 2_400, Optional.of(TestFixtures.TARGET));

        MontagePlan plan = new DefaultMontagePlanner().createPlan(analysis(), request,
                new MontagePlanningContext(AVAILABLE, SamplingSettings.defaults()));

        assertTrue(plan.warnings().stream().anyMatch(warning ->
                warning.code().equals("montage.warning.flashback_requires_chronological_source")));
        assertPlanValid(plan);
    }

    @Test
    void editorDoesNotRegenerateOrMoveLockedShot() {
        MontagePreset preset = MontagePresetRegistry.createDefault().get(MontagePresetType.FIFTEEN_SECONDS).orElseThrow();
        MontagePlan plan = new DefaultMontagePlanner().createPlan(analysis(),
                MontageRequest.fromPreset(preset, 0, 2_400, Optional.of(TestFixtures.TARGET)),
                new MontagePlanningContext(AVAILABLE, SamplingSettings.defaults()));
        MontagePlanEditor editor = new MontagePlanEditor();
        PlannedMontageShot first = plan.shots().getFirst();
        MontagePlan locked = editor.setLocked(plan, first.shotId(), true);

        MontagePlan moved = editor.move(locked, first.shotId(), 1);

        assertEquals(first.shotId(), moved.shots().getFirst().shotId());
        assertTrue(moved.shots().getFirst().locked());
        assertPlanValid(moved);
    }

    @Test
    void disableAndRemoveKeepEnabledMappingsContinuousAndValid() {
        MontagePlan plan = plan(MontagePresetType.THIRTY_SECONDS);
        MontagePlanEditor editor = new MontagePlanEditor();
        PlannedMontageShot first = plan.shots().getFirst();

        MontagePlan disabled = editor.setEnabled(plan, first.shotId(), false);
        assertFalse(disabled.shots().getFirst().enabled());
        assertEquals(plan.shots().size() - 1, editor.generationShots(disabled).size());
        assertEquals(plan.outputDurationSeconds() - first.outputDurationSeconds(),
                disabled.outputDurationSeconds(), 1.0e-9);
        assertPlanValid(disabled);

        MontagePlan reenabled = editor.setEnabled(disabled, first.shotId(), true);
        assertEquals(plan.timeMappings(), reenabled.timeMappings());
        assertPlanValid(reenabled);

        MontagePlan removed = editor.remove(plan, first.shotId());
        assertEquals(plan.shots().size() - 1, removed.shots().size());
        assertPlanValid(removed);
    }

    @Test
    void duplicateSplitsOneSourceSlotAtItsPeak() {
        MontagePlan plan = plan(MontagePresetType.THIRTY_SECONDS);
        MontagePlanEditor editor = new MontagePlanEditor();
        PlannedMontageShot source = plan.shots().stream().filter(shot ->
                        shot.sourceEvent().peakReplayTime() > shot.sourceReplayStartTime()
                                && shot.sourceEvent().peakReplayTime() < shot.sourceReplayEndTime())
                .findFirst().orElseThrow();
        int sourceIndex = source.order();

        MontagePlan duplicated = editor.duplicate(plan, source.shotId());

        assertEquals(plan.shots().size() + 1, duplicated.shots().size());
        PlannedMontageShot first = duplicated.shots().get(sourceIndex);
        PlannedMontageShot second = duplicated.shots().get(sourceIndex + 1);
        assertEquals(source.shotId(), first.shotId());
        assertNotEquals(source.shotId(), second.shotId());
        assertEquals(source.sourceReplayStartTime(), first.sourceReplayStartTime());
        assertEquals(source.sourceEvent().peakReplayTime(), first.sourceReplayEndTime());
        assertEquals(first.sourceReplayEndTime(), second.sourceReplayStartTime());
        assertEquals(source.sourceReplayEndTime(), second.sourceReplayEndTime());
        assertEquals(source.outputDurationSeconds(),
                first.outputDurationSeconds() + second.outputDurationSeconds(), 1.0e-9);
        assertEquals(source.replaySpeed(), first.replaySpeed(), 1.0e-9);
        assertEquals(source.replaySpeed(), second.replaySpeed(), 1.0e-9);
        assertPlanValid(duplicated);
    }

    @Test
    void moveKeepsChronologicalSourceSlotsAndMovesCreativeTreatment() {
        MontagePlan plan = plan(MontagePresetType.THIRTY_SECONDS);
        MontagePlanEditor editor = new MontagePlanEditor();
        PlannedMontageShot first = plan.shots().getFirst();

        MontagePlan moved = editor.move(plan, first.shotId(), 1);

        assertEquals(first.shotId(), moved.shots().get(1).shotId());
        for (int index = 0; index < plan.shots().size(); index++) {
            PlannedMontageShot before = plan.shots().get(index);
            PlannedMontageShot after = moved.shots().get(index);
            assertEquals(before.sourceReplayStartTime(), after.sourceReplayStartTime());
            assertEquals(before.sourceReplayEndTime(), after.sourceReplayEndTime());
            assertEquals(before.sourceEvent().eventId(), after.sourceEvent().eventId());
        }
        assertPlanValid(moved);
    }

    @Test
    void rebuildRemovesStaleEditorError() {
        MontagePlan plan = plan(MontagePresetType.THIRTY_SECONDS);
        List<MontageWarning> warnings = new java.util.ArrayList<>(plan.warnings());
        warnings.add(new MontageWarning("montage.warning.edited_source_not_monotonic",
                MontageWarning.Severity.ERROR, List.of()));
        MontagePlan stale = new MontagePlan(plan.montageId(), plan.preset(), plan.sourceStartReplayTime(),
                plan.sourceEndReplayTime(), plan.outputDurationSeconds(), plan.shots(), plan.transitions(),
                plan.timeMappings(), plan.statistics(), warnings);
        MontagePlanEditor editor = new MontagePlanEditor();

        MontagePlan repaired = editor.setLocked(stale, stale.shots().getFirst().shotId(), true);

        assertTrue(repaired.shots().getFirst().locked());
        assertFalse(repaired.warnings().stream().anyMatch(warning ->
                warning.code().equals("montage.warning.edited_source_not_monotonic")));
        assertPlanValid(repaired);
    }

    @Test
    void preservesExactLockedEditedShotDuringRegeneration() {
        MontagePlan plan = plan(MontagePresetType.THIRTY_SECONDS);
        MontagePlanEditor editor = new MontagePlanEditor();
        PlannedMontageShot source = plan.shots().get(2);
        ShotRequest editedRequest = withGeometry(source.shotRequest(),
                source.shotRequest().startAngleDegrees() + 31.0,
                source.shotRequest().distance() * 1.25,
                source.shotRequest().height() * 1.15,
                opposite(source.shotRequest().direction()));
        MontagePlan edited = editor.replaceRequest(plan, source.shotId(), editedRequest,
                source.framing(), List.of("montage.reason.manual_parameters"));
        MontagePlan locked = editor.setLocked(edited, source.shotId(), true);
        PlannedMontageShot expected = locked.shots().get(2);

        MontagePlan preserved = editor.preserveLockedShots(locked, plan(MontagePresetType.THIRTY_SECONDS));

        assertEquals(expected, preserved.shots().get(2));
        assertPlanValid(preserved);
    }

    @Test
    void customDiversityConfigFeedsPlanningStatistics() {
        MontagePreset preset = preset(MontagePresetType.THIRTY_SECONDS);
        MontageRequest request = MontageRequest.fromPreset(preset, 0, 2_400,
                Optional.of(TestFixtures.TARGET));
        ShotDiversityConfig disabled = zeroDiversityConfig();

        MontagePlan configured = new DefaultMontagePlanner().createPlan(analysis(), request,
                new MontagePlanningContext(AVAILABLE, SamplingSettings.defaults(), disabled));
        MontagePlan defaults = new DefaultMontagePlanner().createPlan(analysis(), request,
                new MontagePlanningContext(AVAILABLE, SamplingSettings.defaults()));

        assertEquals(0.0, configured.statistics().shotDiversityScore(), 1.0e-9);
        assertTrue(defaults.statistics().shotDiversityScore() > configured.statistics().shotDiversityScore());
    }

    @Test
    void diversityScorerPenalizesSimilarGeometryAndMovementDirection() {
        MontagePlan plan = plan(MontagePresetType.THIRTY_SECONDS);
        PlannedMontageShot source = plan.shots().get(2);
        ShotDiversityConfig config = zeroDiversityConfig();
        config.baseScore = 1.0;
        config.similarAzimuthPenalty = 0.1;
        config.similarDistancePenalty = 0.1;
        config.similarHeightPenalty = 0.1;
        config.repeatedMovementDirectionPenalty = 0.1;
        ShotDiversityProfile profile = ShotDiversityProfile.from(config);
        ShotRequest variedRequest = withGeometry(source.shotRequest(),
                source.shotRequest().startAngleDegrees() + 180.0,
                source.shotRequest().distance() * 2.0,
                source.shotRequest().height() * 2.0,
                opposite(source.shotRequest().direction()));
        PlannedMontageShot varied = source.withRequest(variedRequest, source.framing(), List.of());
        ShotDiversityScorer scorer = new ShotDiversityScorer();

        double similarScore = scorer.score(List.of(source, source), profile);
        double variedScore = scorer.score(List.of(source, varied), profile);

        assertTrue(variedScore > similarScore);
    }

    @Test
    void recordsLocalizableRequestedToChosenFallbackReason() {
        MontagePreset preset = preset(MontagePresetType.THIRTY_SECONDS);
        MontagePlan plan = new DefaultMontagePlanner().createPlan(analysis(),
                MontageRequest.fromPreset(preset, 0, 2_400, Optional.of(TestFixtures.TARGET)),
                new MontagePlanningContext(Set.of(ShotType.FOLLOW), SamplingSettings.defaults()));

        assertTrue(plan.shots().stream().allMatch(shot -> shot.shotType() == ShotType.FOLLOW));
        assertTrue(plan.shots().stream().flatMap(shot -> shot.planningReasons().stream()).anyMatch(reason ->
                reason.startsWith("montage.reason.shot_fallback;requested=cinewolf.shot.")
                        && reason.contains(";chosen=cinewolf.shot.follow")));
    }

    @Test
    void shortensOneTimesPlanToSelectedSourceRangeInsteadOfFailing() {
        MontagePreset preset = preset(MontagePresetType.CINEMATIC_SHOWCASE);
        MontageRequest request = new MontageRequest(preset, 195, 628, 25.0,
                OutputAspectRatio.VERTICAL_9_16, MontagePacing.CINEMATIC,
                Optional.of(TestFixtures.TARGET), true, 5.0, 15.0,
                0.1, 0.1, false, false, 1.0, 1.0, 0.0, 16);

        MontagePlan plan = new DefaultMontagePlanner().createPlan(analysis(), request,
                new MontagePlanningContext(AVAILABLE, SamplingSettings.defaults()));

        assertEquals(21.65, plan.outputDurationSeconds(), 1.0e-9);
        assertEquals(21.65, plan.statistics().plannedOutputDurationSeconds(), 1.0e-9);
        assertTrue(plan.warnings().stream().anyMatch(warning ->
                warning.code().equals("montage.warning.output_shortened_to_source")
                        && warning.arguments().equals(List.of("25.00", "21.65"))));
        assertPlanValid(plan);
    }

    private static MontagePlan plan(MontagePresetType type) {
        MontagePreset preset = preset(type);
        return new DefaultMontagePlanner().createPlan(analysis(),
                MontageRequest.fromPreset(preset, 0, 2_400, Optional.of(TestFixtures.TARGET)),
                new MontagePlanningContext(AVAILABLE, SamplingSettings.defaults()));
    }

    private static MontagePreset preset(MontagePresetType type) {
        return MontagePresetRegistry.createDefault().get(type).orElseThrow();
    }

    private static ReplayAnalysisResult analysis() {
        ReplayAnalysisRequest request = ReplayAnalysisRequest.defaults(0, 2_400);
        ReplayEntitySnapshot entity = ReplayEntitySnapshot.basic(TestFixtures.TARGET,
                TestFixtures.pose(Vec3d.ZERO, Vec3d.ZERO, 0.0));
        List<ReplaySample> samples = List.of(
                new ReplaySample(0, Map.of(TestFixtures.TARGET, entity), List.of(), List.of()),
                new ReplaySample(2_400, Map.of(TestFixtures.TARGET, entity), List.of(), List.of()));
        ReplayEventType[] types = {ReplayEventType.REPLAY_MARKER, ReplayEventType.HIGH_SPEED,
                ReplayEventType.SHARP_TURN, ReplayEventType.COMBAT, ReplayEventType.FLIGHT,
                ReplayEventType.LANDING, ReplayEventType.BLOCK_PLACEMENT, ReplayEventType.PAUSE,
                ReplayEventType.VEHICLE_MOVEMENT, ReplayEventType.DEATH};
        java.util.ArrayList<ScoredReplayEvent> scored = new java.util.ArrayList<>();
        for (int index = 0; index < types.length; index++) {
            long peak = 150L + index * 100L;
            ReplayEvent event = ReplayEvent.create(types[index], peak - 20, peak, peak + 20,
                    Set.of(TestFixtures.TARGET), new Vec3d(index, 64, index), 0.7, 0.9,
                    EventEvidence.of(EventEvidence.DetectionSource.DERIVED_MOVEMENT));
            scored.add(new ScoredReplayEvent(event, 0.8, 0.8, 0.8, 0.8,
                    0.0, 0.1, 0.0, 0.0, 0.8 - index * 0.01, List.of("test-score")));
        }
        List<ReplayEvent> events = scored.stream().map(ScoredReplayEvent::event).toList();
        SampleSelection selection = new SampleSelection(samples, List.of(), samples, List.of());
        ReplayAnalysisStatistics stats = new ReplayAnalysisStatistics(2_400, 2, 2, 0, 2, 1,
                events.size(), events.size(), 1, Map.of());
        return new ReplayAnalysisResult(request, selection, samples, Map.of(), events, events, scored,
                List.of(), List.of(new RankedReplayTarget(TestFixtures.TARGET, 1.0, 2_400,
                100.0, events.size(), List.of("test"))), stats, List.<AnalysisWarning>of());
    }

    private static void assertPlanValid(MontagePlan plan) {
        List<PlannedMontageShot> enabled = plan.enabledShots();
        long previousEnd = Long.MIN_VALUE;
        double outputCursor = 0.0;
        for (PlannedMontageShot shot : enabled) {
            if (previousEnd != Long.MIN_VALUE) assertEquals(previousEnd, shot.sourceReplayStartTime());
            assertTrue(shot.sourceEvent().peakReplayTime() >= shot.sourceReplayStartTime());
            assertTrue(shot.sourceEvent().peakReplayTime() <= shot.sourceReplayEndTime());
            assertEquals(outputCursor, shot.outputStartSeconds(), 1.0e-9);
            previousEnd = shot.sourceReplayEndTime();
            outputCursor = shot.outputEndSeconds();
        }
        assertEquals(outputCursor, plan.outputDurationSeconds(), 1.0e-9);
        assertEquals(outputCursor, plan.statistics().plannedOutputDurationSeconds(), 1.0e-9);
        assertEquals(enabled.size(), plan.timeMappings().size());
        assertEquals(enabled.stream().map(PlannedMontageShot::timeMapping).toList(), plan.timeMappings());
        assertEquals(Math.max(0, enabled.size() - 1), plan.transitions().size());
        MontageTimeMappingValidator.ValidationResult validation = new MontageTimeMappingValidator().validate(
                plan.timeMappings(), plan.outputDurationSeconds(), plan.preset().style().minimumReplaySpeed(),
                plan.preset().style().maximumReplaySpeed(),
                plan.preset().style().maximumReplaySpeedChange());
        assertTrue(validation.valid(), () -> "mapping errors: " + validation.errors());
        assertFalse(plan.warnings().stream().anyMatch(warning -> warning.severity() == MontageWarning.Severity.ERROR));
    }

    private static ShotRequest withGeometry(ShotRequest source, double angle, double distance, double height,
                                            RotationDirection direction) {
        return new ShotRequest(source.target(), source.shotType(), source.diameter(), height, distance,
                source.startDistance(), source.endDistance(), source.rpm(), source.durationSeconds(), angle,
                direction, source.cameraSpeed(), source.fov(), source.easing(), source.lookAheadSeconds(),
                source.replayStartTime(), source.replayEndTime());
    }

    private static RotationDirection opposite(RotationDirection direction) {
        return switch (direction) {
            case CLOCKWISE -> RotationDirection.COUNTERCLOCKWISE;
            case COUNTERCLOCKWISE -> RotationDirection.CLOCKWISE;
            case LEFT_TO_RIGHT -> RotationDirection.RIGHT_TO_LEFT;
            case RIGHT_TO_LEFT -> RotationDirection.LEFT_TO_RIGHT;
        };
    }

    private static ShotDiversityConfig zeroDiversityConfig() {
        ShotDiversityConfig config = new ShotDiversityConfig();
        config.baseScore = 0.0;
        config.shotTypeCoverageWeight = 0.0;
        config.framingCoverageWeight = 0.0;
        config.alternateShotReward = 0.0;
        config.alternateFramingReward = 0.0;
        config.repeatedShotPenalty = 0.0;
        config.repeatedFramingPenalty = 0.0;
        config.repeatedEventPenalty = 0.0;
        config.repeatedTargetAndShotPenalty = 0.0;
        config.similarAzimuthPenalty = 0.0;
        config.similarDistancePenalty = 0.0;
        config.similarHeightPenalty = 0.0;
        config.repeatedMovementDirectionPenalty = 0.0;
        config.alternateMovementDirectionReward = 0.0;
        config.differentHeightReward = 0.0;
        return config;
    }
}
