package pl.peterwolf.cinewolf.montage.event;

import org.junit.jupiter.api.Test;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures;
import pl.peterwolf.cinewolf.montage.analysis.DifferenceMethod;
import pl.peterwolf.cinewolf.montage.analysis.MovementMetrics;
import pl.peterwolf.cinewolf.montage.analysis.ObservedReplayAction;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplayMarkerSnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.detector.BlockActivityEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.CombatEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.PauseEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.ReplayMarkerEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.VehicleFlightEventDetector;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static pl.peterwolf.cinewolf.montage.analysis.AnalysisTestFixtures.*;

class StateEventDetectorsTest {
    @Test
    void detectsDamageAndObservedDeathButNotEntityUnload() {
        ReplayEntitySnapshot healthy = state(PLAYER, 0, 0, 0, 20, 20, 0,
                false, false, true, true, 0, Optional.empty(), Optional.empty(), false, false,
                "minecraft:overworld", false);
        ReplayEntitySnapshot damaged = state(PLAYER, 0, 0, 0, 15, 20, 5,
                false, false, true, true, 0, Optional.empty(), Optional.empty(), false, false,
                "minecraft:overworld", false);
        ReplayEntitySnapshot dead = state(PLAYER, 0, 0, 0, 0, 20, 0,
                false, false, false, true, 0, Optional.empty(), Optional.empty(), false, false,
                "minecraft:overworld", false);
        List<ReplaySample> samples = List.of(sample(0, healthy), sample(10, damaged), sample(20, dead),
                ReplaySample.empty(30));
        ReplaySampleWindow window = new ReplaySampleWindow(samples, Map.of(), Set.of(PLAYER));

        List<ReplayEvent> events = new CombatEventDetector().detect(window,
                ReplayAnalysisContext.defaults(samples), 0.5);

        assertEquals(1, events.stream().filter(event -> event.type() == ReplayEventType.DAMAGE).count());
        assertEquals(1, events.stream().filter(event -> event.type() == ReplayEventType.DEATH).count());
        assertTrue(events.stream().filter(event -> event.type() == ReplayEventType.DEATH)
                .allMatch(event -> event.peakReplayTime() == 20));
    }

    @Test
    void swingNearEntityIsLowConfidenceAndDoesNotInventVictim() {
        ReplayEntitySnapshot attacker = state(PLAYER, 0, 0, 0, 20, 20, 0,
                true, true, true, true, 0, Optional.empty(), Optional.empty(), false, false,
                "minecraft:overworld", false);
        ReplaySample sample = sample(10, attacker, snapshot(OTHER, 2, 0, 0));
        ReplaySampleWindow window = new ReplaySampleWindow(List.of(sample), Map.of(), Set.of(PLAYER));

        ReplayEvent combat = new CombatEventDetector().detect(window,
                ReplayAnalysisContext.defaults(List.of(sample)), 0.5).stream()
                .filter(event -> event.type() == ReplayEventType.COMBAT).findFirst().orElseThrow();

        assertTrue(combat.confidence() <= 0.5);
        assertEquals(Set.of(PLAYER), combat.targets());
    }

    @Test
    void victimlessPacketSwingDoesNotInventCombatWithoutNearbyEntityEvidence() {
        ReplayEntitySnapshot player = state(PLAYER, 0, 0, 0, 20, 20, 0,
                false, false, true, true, 0, Optional.empty(), Optional.empty(), false, false,
                "minecraft:overworld", false);
        ObservedReplayAction swing = new ObservedReplayAction.CombatSignal(10,
                ObservedReplayAction.CombatSignalType.ATTACK, Optional.of(PLAYER), Optional.empty(),
                Vec3d.ZERO, 0.2);
        ReplaySample sample = AnalysisTestFixtures.sample(10, Map.of(PLAYER, player), List.of(), List.of(swing));

        List<ReplayEvent> events = new CombatEventDetector().detect(
                new ReplaySampleWindow(List.of(sample), Map.of(), Set.of(PLAYER)),
                ReplayAnalysisContext.defaults(List.of(sample)), 0.5);

        assertTrue(events.stream().noneMatch(event -> event.type() == ReplayEventType.COMBAT));
    }

    @Test
    void groupsNearbyBlockActionsAndKeepsPlacementSeparateFromDestruction() {
        List<ObservedReplayAction> actions = List.of(
                new ObservedReplayAction.BlockPlaced(0, Optional.of(PLAYER), new Vec3d(0, 0, 0), "stone"),
                new ObservedReplayAction.BlockPlaced(5, Optional.of(PLAYER), new Vec3d(1, 0, 0), "stone"),
                new ObservedReplayAction.BlockPlaced(10, Optional.of(PLAYER), new Vec3d(2, 0, 0), "glass"),
                new ObservedReplayAction.BlockDestroyed(10, Optional.of(PLAYER), new Vec3d(2, 0, 1), "dirt"),
                new ObservedReplayAction.BlockPlaced(100, Optional.of(PLAYER), new Vec3d(50, 0, 0), "stone"));
        ReplaySample replaySample = AnalysisTestFixtures.sample(0, Map.of(PLAYER, snapshot(PLAYER, 0, 0, 0)),
                List.of(), actions);

        List<ReplayEvent> events = new BlockActivityEventDetector().detect(
                new ReplaySampleWindow(List.of(replaySample), Map.of(), Set.of(PLAYER)),
                ReplayAnalysisContext.defaults(List.of(replaySample)), 0.5);

        assertEquals(2, events.stream().filter(event -> event.type() == ReplayEventType.BLOCK_PLACEMENT).count());
        assertEquals(1, events.stream().filter(event -> event.type() == ReplayEventType.BLOCK_DESTRUCTION).count());
        ReplayEvent grouped = events.stream().filter(event -> event.type() == ReplayEventType.BLOCK_PLACEMENT
                && event.startReplayTime() == 0).findFirst().orElseThrow();
        assertEquals(3.0, grouped.evidence().measurements().stream()
                .filter(value -> value.name().equals("block_count")).findFirst().orElseThrow().value());
    }

    @Test
    void detectsVehicleTransitionsMovementFlightAndLanding() {
        List<ReplaySample> samples = List.of(
                sample(0, state(PLAYER, 0, 0, 0, 20, 20, 0, false, false, true,
                        true, 0, Optional.empty(), Optional.empty(), false, false, "minecraft:overworld", false)),
                sample(10, state(PLAYER, 2, 0, 0, 20, 20, 0, false, false, true,
                        true, 0, Optional.of(VEHICLE), Optional.of("minecraft:minecart"), false, false,
                        "minecraft:overworld", false)),
                sample(20, state(PLAYER, 5, 0, 0, 20, 20, 0, false, false, true,
                        true, 0, Optional.of(VEHICLE), Optional.of("minecraft:minecart"), false, false,
                        "minecraft:overworld", false)),
                sample(30, state(PLAYER, 6, 2, 0, 20, 20, 0, false, false, true,
                        false, 2, Optional.empty(), Optional.empty(), false, true, "minecraft:overworld", false)),
                sample(40, state(PLAYER, 8, 4, 0, 20, 20, 0, false, false, true,
                        false, 4, Optional.empty(), Optional.empty(), false, true, "minecraft:overworld", false)),
                sample(50, state(PLAYER, 9, 0, 0, 20, 20, 0, false, false, true,
                        true, 0, Optional.empty(), Optional.empty(), false, false, "minecraft:overworld", false)));
        List<MovementMetrics> metrics = List.of(metric(0, 0, 0, 0), metric(10, 2, 0, 4),
                metric(20, 5, 0, 6), metric(30, 6, 2, 4), metric(40, 8, 4, 4), metric(50, 9, 0, 2));
        ReplaySampleWindow window = new ReplaySampleWindow(samples, Map.of(PLAYER, metrics), Set.of(PLAYER));

        Set<ReplayEventType> types = new VehicleFlightEventDetector().detect(window,
                ReplayAnalysisContext.defaults(samples), 1.0).stream().map(ReplayEvent::type).collect(Collectors.toSet());

        assertTrue(types.containsAll(Set.of(ReplayEventType.VEHICLE_ENTER, ReplayEventType.VEHICLE_EXIT,
                ReplayEventType.VEHICLE_MOVEMENT, ReplayEventType.FLIGHT_START, ReplayEventType.FLIGHT,
                ReplayEventType.LANDING)), () -> "Detected: " + types);
    }

    @Test
    void doesNotTreatSingleCreativeFlightHintAsReliableFlight() {
        ReplaySample sample = sample(0, state(PLAYER, 0, 0, 0, 20, 20, 0, false, false, true,
                true, 0, Optional.empty(), Optional.empty(), true, false, "minecraft:overworld", false));
        ReplaySampleWindow window = new ReplaySampleWindow(List.of(sample), Map.of(PLAYER, List.of(metric(0, 0, 0, 0))),
                Set.of(PLAYER));

        assertTrue(new VehicleFlightEventDetector().detect(window,
                ReplayAnalysisContext.defaults(List.of(sample)), 0.5).stream()
                .noneMatch(event -> event.type() == ReplayEventType.FLIGHT));
    }

    @Test
    void detectsPauseAndReplayMarker() {
        ReplayMarkerSnapshot marker = new ReplayMarkerSnapshot(UUID.fromString("00000000-0000-0000-0000-000000000099"),
                20, "climax", Optional.of(new Vec3d(1, 2, 3)));
        List<ReplaySample> samples = List.of(sample(0, snapshot(PLAYER, 0, 0, 0)),
                sample(10, snapshot(PLAYER, 0, 0, 0)),
                AnalysisTestFixtures.sample(20, Map.of(PLAYER, snapshot(PLAYER, 0, 0, 0)), List.of(marker), List.of()),
                sample(30, snapshot(PLAYER, 0, 0, 0)), sample(40, snapshot(PLAYER, 0, 0, 0)));
        List<MovementMetrics> metrics = List.of(metric(0, 0, 0, 0), metric(10, 0, 0, 0),
                metric(20, 0, 0, 0), metric(30, 0, 0, 0), metric(40, 0, 0, 0));
        ReplaySampleWindow window = new ReplaySampleWindow(samples, Map.of(PLAYER, metrics), Set.of(PLAYER));

        assertEquals(1, new PauseEventDetector().detect(window, ReplayAnalysisContext.defaults(samples), 0.5).size());
        ReplayEvent markerEvent = new ReplayMarkerEventDetector().detect(window,
                ReplayAnalysisContext.defaults(samples), 0.5).getFirst();
        assertEquals(ReplayEventType.REPLAY_MARKER, markerEvent.type());
        assertTrue(markerEvent.evidence().attributes().stream().anyMatch(value -> value.value().equals("climax")));
    }

    @Test
    void defaultDetectorSetCoversExactlyAllTwentyEventTypes() {
        Set<ReplayEventType> supported = EnumSet.noneOf(ReplayEventType.class);
        supported.addAll(new pl.peterwolf.cinewolf.montage.event.detector.MovementEventDetector().supportedTypes());
        supported.addAll(new CombatEventDetector().supportedTypes());
        supported.addAll(new VehicleFlightEventDetector().supportedTypes());
        supported.addAll(new BlockActivityEventDetector().supportedTypes());
        supported.addAll(new PauseEventDetector().supportedTypes());
        supported.addAll(new ReplayMarkerEventDetector().supportedTypes());

        assertEquals(EnumSet.allOf(ReplayEventType.class), supported);
        assertEquals(20, supported.size());
    }

    private static MovementMetrics metric(long tick, double x, double y, double speed) {
        Vec3d position = new Vec3d(x, y, 0);
        Vec3d velocity = new Vec3d(speed, 0, 0);
        return new MovementMetrics(PLAYER, tick, position, Vec3d.ZERO, velocity, velocity, speed, speed,
                0, 0, 0, 0, 0, y, 0, 0, DifferenceMethod.CENTRAL);
    }
}
