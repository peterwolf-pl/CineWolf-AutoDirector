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
import pl.peterwolf.cinewolf.montage.event.detector.ExplorationEventDetector;
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
    void specializesTreeCuttingMiningAndFarmingFromBlockTypes() {
        List<ObservedReplayAction> actions = List.of(
                new ObservedReplayAction.BlockDestroyed(0, Optional.of(PLAYER), new Vec3d(0, 64, 0), "minecraft:oak_log"),
                new ObservedReplayAction.BlockDestroyed(5, Optional.of(PLAYER), new Vec3d(0, 65, 0), "minecraft:oak_log"),
                new ObservedReplayAction.BlockDestroyed(10, Optional.of(PLAYER), new Vec3d(0, 66, 0), "minecraft:birch_log"),
                new ObservedReplayAction.BlockDestroyed(40, Optional.of(PLAYER), new Vec3d(20, 12, 0), "minecraft:iron_ore"),
                new ObservedReplayAction.BlockDestroyed(45, Optional.of(PLAYER), new Vec3d(21, 12, 0), "minecraft:deepslate_iron_ore"),
                new ObservedReplayAction.BlockDestroyed(50, Optional.of(PLAYER), new Vec3d(22, 12, 0), "minecraft:stone"),
                new ObservedReplayAction.BlockPlaced(80, Optional.of(PLAYER), new Vec3d(40, 64, 0), "minecraft:wheat"),
                new ObservedReplayAction.BlockPlaced(85, Optional.of(PLAYER), new Vec3d(41, 64, 0), "minecraft:carrots"),
                new ObservedReplayAction.BlockDestroyed(120, Optional.of(PLAYER), new Vec3d(50, 64, 0), "minecraft:wheat"),
                new ObservedReplayAction.BlockDestroyed(125, Optional.of(PLAYER), new Vec3d(51, 64, 0), "minecraft:potatoes"));
        ReplaySample replaySample = AnalysisTestFixtures.sample(0, Map.of(PLAYER, snapshot(PLAYER, 0, 64, 0)),
                List.of(), actions);

        List<ReplayEvent> events = new BlockActivityEventDetector().detect(
                new ReplaySampleWindow(List.of(replaySample), Map.of(), Set.of(PLAYER)),
                ReplayAnalysisContext.defaults(List.of(replaySample)), 0.5);

        assertEquals(1, events.stream().filter(event -> event.type() == ReplayEventType.TREE_CUTTING).count());
        assertEquals(1, events.stream().filter(event -> event.type() == ReplayEventType.MINING).count());
        assertEquals(2, events.stream().filter(event -> event.type() == ReplayEventType.FARMING).count());
        ReplayEvent planting = events.stream()
                .filter(event -> event.type() == ReplayEventType.FARMING && event.startReplayTime() == 80)
                .findFirst().orElseThrow();
        assertTrue(planting.evidence().attributes().stream()
                .anyMatch(attribute -> "activity_mode".equals(attribute.name())
                        && "planting".equals(attribute.value())));
        ReplayEvent harvest = events.stream()
                .filter(event -> event.type() == ReplayEventType.FARMING && event.startReplayTime() == 120)
                .findFirst().orElseThrow();
        assertTrue(harvest.evidence().attributes().stream()
                .anyMatch(attribute -> "activity_mode".equals(attribute.name())
                        && "harvesting".equals(attribute.value())));
    }

    @Test
    void detectsExplorationFromSustainedModerateMovement() {
        java.util.ArrayList<MovementMetrics> metrics = new java.util.ArrayList<>();
        java.util.ArrayList<ReplaySample> samples = new java.util.ArrayList<>();
        for (int index = 0; index <= 20; index++) {
            long tick = index * 10L;
            double x = index * 1.2;
            double z = Math.sin(index * 0.4) * 3.0;
            Vec3d position = new Vec3d(x, 64, z);
            Vec3d velocity = new Vec3d(2.4, 0, 0);
            double headingChange = index % 3 == 0 ? 18.0 : 4.0;
            metrics.add(new MovementMetrics(PLAYER, tick, position, velocity, velocity, velocity,
                    2.4, 2.4, 0.0, 0.0, index * 10.0, headingChange, headingChange * 2.0, 64.0, 0.0, 0,
                    DifferenceMethod.CENTRAL));
            samples.add(sample(tick, snapshot(PLAYER, x, 64, z)));
        }
        ReplaySampleWindow window = new ReplaySampleWindow(samples, Map.of(PLAYER, metrics), Set.of(PLAYER));

        List<ReplayEvent> events = new ExplorationEventDetector().detect(window,
                ReplayAnalysisContext.defaults(samples), 0.5);

        assertEquals(1, events.stream().filter(event -> event.type() == ReplayEventType.EXPLORATION).count());
        ReplayEvent exploration = events.stream()
                .filter(event -> event.type() == ReplayEventType.EXPLORATION).findFirst().orElseThrow();
        assertTrue(exploration.durationTicks() >= 80);
        assertTrue(exploration.evidence().attributes().stream()
                .anyMatch(attribute -> "activity_mode".equals(attribute.name())
                        && "exploration".equals(attribute.value())));
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
    void defaultDetectorSetCoversExactlyAllEventTypes() {
        Set<ReplayEventType> supported = EnumSet.noneOf(ReplayEventType.class);
        supported.addAll(new pl.peterwolf.cinewolf.montage.event.detector.MovementEventDetector().supportedTypes());
        supported.addAll(new CombatEventDetector().supportedTypes());
        supported.addAll(new VehicleFlightEventDetector().supportedTypes());
        supported.addAll(new BlockActivityEventDetector().supportedTypes());
        supported.addAll(new ExplorationEventDetector().supportedTypes());
        supported.addAll(new PauseEventDetector().supportedTypes());
        supported.addAll(new ReplayMarkerEventDetector().supportedTypes());

        assertEquals(EnumSet.allOf(ReplayEventType.class), supported);
        assertEquals(24, supported.size());
    }

    private static MovementMetrics metric(long tick, double x, double y, double speed) {
        Vec3d position = new Vec3d(x, y, 0);
        Vec3d velocity = new Vec3d(speed, 0, 0);
        return new MovementMetrics(PLAYER, tick, position, Vec3d.ZERO, velocity, velocity, speed, speed,
                0, 0, 0, 0, 0, y, 0, 0, DifferenceMethod.CENTRAL);
    }
}
