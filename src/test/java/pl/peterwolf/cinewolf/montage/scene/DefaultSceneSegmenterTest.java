package pl.peterwolf.cinewolf.montage.scene;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures;
import pl.peterwolf.cinewolf.montage.analysis.ReplayMarkerSnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.*;

class DefaultSceneSegmenterTest {
    private final DefaultSceneSegmenter segmenter = new DefaultSceneSegmenter();

    @Test
    void createsBoundaryAtTeleportOrMajorLocationChange() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)),
                sample(20, snapshot(PLAYER, 1, 0, 0)), sample(40, snapshot(PLAYER, 100, 0, 0)));

        List<ReplayScene> scenes = segmenter.segment(0, 60, samples, List.of(), DetectorThresholds.defaults());

        assertEquals(List.of(0L, 40L), scenes.stream().map(ReplayScene::startReplayTime).toList());
    }

    @Test
    void createsBoundaryAtDimensionChange() {
        ReplaySample overworld = sample(0, state(PLAYER, 0, 0, 0, 20, 20, 0,
                false, false, true, true, 0, Optional.empty(), Optional.empty(), false, false,
                "minecraft:overworld", false));
        ReplaySample nether = sample(30, state(PLAYER, 0, 0, 0, 20, 20, 0,
                false, false, true, true, 0, Optional.empty(), Optional.empty(), false, false,
                "minecraft:the_nether", false));

        List<ReplayScene> scenes = segmenter.segment(0, 60, List.of(overworld, nether),
                List.of(), DetectorThresholds.defaults());

        assertEquals(List.of(0L, 30L), scenes.stream().map(ReplayScene::startReplayTime).toList());
    }

    @Test
    void replayMarkerForcesBoundaryWithoutOneTickMicroScene() {
        ReplayMarkerSnapshot marker = new ReplayMarkerSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000092"), 40, "chapter", Optional.empty());
        ReplaySample marked = AnalysisTestFixtures.sample(40, Map.of(PLAYER, snapshot(PLAYER, 0, 0, 0)),
                List.of(marker), List.of());
        ReplayEvent event = new ReplayEvent(marker.markerId(), ReplayEventType.REPLAY_MARKER, 40, 40, 40,
                Set.of(), Vec3d.ZERO, 0.8, 1.0, evidence());

        List<ReplayScene> scenes = segmenter.segment(0, 80,
                List.of(sample(0, snapshot(PLAYER, 0, 0, 0)), marked,
                        sample(80, snapshot(PLAYER, 0, 0, 0))), List.of(event), DetectorThresholds.defaults());

        assertEquals(List.of(0L, 40L), scenes.stream().map(ReplayScene::startReplayTime).toList());
        assertEquals(39, scenes.getFirst().endReplayTime());
        assertEquals(80, scenes.getLast().endReplayTime());
    }

    @Test
    void segmentsVehicleCombatAndLongPauseTransitions() {
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)),
                sample(20, snapshot(PLAYER, 0, 0, 0)), sample(50, snapshot(PLAYER, 0, 0, 0)),
                sample(70, snapshot(PLAYER, 0, 0, 0)), sample(90, snapshot(PLAYER, 0, 0, 0)));
        List<ReplayEvent> events = List.of(
                ReplayEvent.create(ReplayEventType.VEHICLE_ENTER, 20, 20, 20, Set.of(PLAYER),
                        Vec3d.ZERO, 0.7, 1, evidence()),
                ReplayEvent.create(ReplayEventType.PAUSE, 50, 60, 70, Set.of(PLAYER),
                        Vec3d.ZERO, 0.5, 0.9, evidence()),
                ReplayEvent.create(ReplayEventType.COMBAT, 90, 90, 90, Set.of(PLAYER),
                        Vec3d.ZERO, 0.9, 0.9, evidence()));

        List<ReplayScene> scenes = segmenter.segment(0, 120, samples, events, DetectorThresholds.defaults());

        assertEquals(List.of(0L, 20L, 50L, 71L, 90L),
                scenes.stream().map(ReplayScene::startReplayTime).toList());
        assertEquals(SceneType.VEHICLE, scenes.get(1).type());
        assertEquals(SceneType.PAUSE, scenes.get(2).type());
        assertEquals(SceneType.COMBAT, scenes.getLast().type());
    }

    @Test
    void segmentationIsDeterministicForUnorderedInput() {
        List<ReplaySample> unordered = List.of(sample(40, snapshot(PLAYER, 100, 0, 0)),
                sample(0, snapshot(PLAYER, 0, 0, 0)), sample(20, snapshot(PLAYER, 1, 0, 0)));

        List<ReplayScene> first = segmenter.segment(0, 60, unordered, List.of(), DetectorThresholds.defaults());
        List<ReplayScene> second = segmenter.segment(0, 60, unordered.reversed(),
                List.of(), DetectorThresholds.defaults());

        assertEquals(first.stream().map(ReplayScene::sceneId).toList(),
                second.stream().map(ReplayScene::sceneId).toList());
    }

    private static EventEvidence evidence() {
        return EventEvidence.of(EventEvidence.DetectionSource.ENTITY_STATE,
                EventEvidence.Measurement.observed("test", 1, "boolean"));
    }
}
