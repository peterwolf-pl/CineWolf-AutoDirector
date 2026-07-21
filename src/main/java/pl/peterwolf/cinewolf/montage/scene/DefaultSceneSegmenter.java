package pl.peterwolf.cinewolf.montage.scene;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class DefaultSceneSegmenter implements SceneSegmenter {
    private static final Set<ReplayEventType> MAJOR_BOUNDARIES = Set.copyOf(EnumSet.of(
            ReplayEventType.COMBAT, ReplayEventType.DEATH, ReplayEventType.VEHICLE_ENTER,
            ReplayEventType.VEHICLE_EXIT, ReplayEventType.FLIGHT_START, ReplayEventType.LANDING,
            ReplayEventType.PAUSE, ReplayEventType.REPLAY_MARKER));

    @Override
    public List<ReplayScene> segment(long rangeStart, long rangeEnd, List<ReplaySample> samples,
                                     List<ReplayEvent> events, DetectorThresholds thresholds) {
        if (rangeEnd < rangeStart) return List.of();
        List<ReplaySample> orderedSamples = samples.stream()
                .filter(sample -> sample.replayTime() >= rangeStart && sample.replayTime() <= rangeEnd)
                .sorted(Comparator.comparingLong(ReplaySample::replayTime)).toList();
        TreeSet<Long> boundaries = new TreeSet<>();
        boundaries.add(rangeStart);
        boundaries.add(rangeEnd == Long.MAX_VALUE ? rangeEnd : rangeEnd + 1);
        for (int index = 1; index < orderedSamples.size(); index++) {
            ReplaySample previous = orderedSamples.get(index - 1);
            ReplaySample current = orderedSamples.get(index);
            if (current.replayTime() - previous.replayTime() > thresholds.sceneGapTicks()
                    || sceneStateChanged(previous, current, thresholds)) boundaries.add(current.replayTime());
        }
        orderedSamples.stream().flatMap(sample -> sample.markers().stream())
                .map(marker -> marker.replayTime()).filter(time -> time > rangeStart && time <= rangeEnd)
                .forEach(boundaries::add);
        for (ReplayEvent event : events) {
            if (!MAJOR_BOUNDARIES.contains(event.type())) continue;
            if (event.startReplayTime() > rangeStart && event.startReplayTime() <= rangeEnd) {
                boundaries.add(event.startReplayTime());
            }
            // Instantaneous events start a new scene but must not create a one-tick micro-scene.
            if (event.endReplayTime() > event.startReplayTime()) {
                long after = event.endReplayTime() == Long.MAX_VALUE ? Long.MAX_VALUE : event.endReplayTime() + 1;
                if (after > rangeStart && after <= rangeEnd) boundaries.add(after);
            }
        }

        List<Long> orderedBoundaries = List.copyOf(boundaries);
        List<ReplayScene> scenes = new ArrayList<>();
        for (int index = 0; index < orderedBoundaries.size() - 1; index++) {
            long start = orderedBoundaries.get(index);
            long end = Math.min(rangeEnd, orderedBoundaries.get(index + 1) - 1);
            if (end < start) continue;
            List<ReplayEvent> sceneEvents = events.stream().filter(event -> event.endReplayTime() >= start
                    && event.startReplayTime() <= end).sorted(Comparator.comparingLong(ReplayEvent::startReplayTime)
                    .thenComparing(ReplayEvent::type)).toList();
            List<ReplayEntitySnapshot> sceneEntities = orderedSamples.stream()
                    .filter(sample -> sample.replayTime() >= start && sample.replayTime() <= end)
                    .flatMap(sample -> sample.entities().values().stream()).toList();
            Set<TargetReference> targets = new HashSet<>();
            sceneEvents.forEach(event -> targets.addAll(event.targets()));
            if (targets.isEmpty()) sceneEntities.forEach(entity -> targets.add(entity.target()));
            List<Vec3d> locations = new ArrayList<>();
            sceneEvents.forEach(event -> locations.add(event.location()));
            if (locations.isEmpty()) sceneEntities.forEach(entity -> locations.add(entity.pose().position()));
            Vec3d center = average(locations);
            double radius = locations.stream().mapToDouble(center::distanceTo).max().orElse(0.0);
            SceneType type = classify(sceneEvents);
            double importance = sceneEvents.stream().mapToDouble(event -> event.magnitude() * event.confidence())
                    .max().orElse(0.1);
            scenes.add(ReplayScene.create(start, end, targets, center, radius, sceneEvents, type, importance));
        }
        return List.copyOf(scenes);
    }

    private static boolean sceneStateChanged(ReplaySample previous, ReplaySample current,
                                             DetectorThresholds thresholds) {
        for (Map.Entry<TargetReference, ReplayEntitySnapshot> entry : current.entities().entrySet()) {
            ReplayEntitySnapshot before = previous.entities().get(entry.getKey());
            ReplayEntitySnapshot after = entry.getValue();
            if (before == null) continue;
            if (!before.pose().dimension().equals(after.pose().dimension()) || after.pose().discontinuity()
                    || before.inVehicle() != after.inVehicle() || before.explicitFlight() != after.explicitFlight()
                    || before.pose().position().distanceTo(after.pose().position()) >= thresholds.teleportDistance()) {
                return true;
            }
        }
        return false;
    }

    private static SceneType classify(List<ReplayEvent> events) {
        if (events.isEmpty()) return SceneType.ESTABLISHING;
        Set<SceneType> types = new HashSet<>();
        for (ReplayEvent event : events) {
            types.add(switch (event.type()) {
                case COMBAT, DAMAGE, DEATH -> SceneType.COMBAT;
                case VEHICLE_ENTER, VEHICLE_EXIT, VEHICLE_MOVEMENT -> SceneType.VEHICLE;
                case FLIGHT_START, FLIGHT, LANDING -> SceneType.FLIGHT;
                case BLOCK_PLACEMENT, BLOCK_DESTRUCTION -> SceneType.BUILDING;
                case PAUSE -> SceneType.PAUSE;
                case POSITION_CHANGE, HIGH_SPEED, ACCELERATION, DECELERATION, SHARP_TURN,
                        ALTITUDE_GAIN, ALTITUDE_LOSS -> SceneType.MOVEMENT;
                case REPLAY_MARKER -> SceneType.ESTABLISHING;
            });
        }
        return types.size() == 1 ? types.iterator().next() : SceneType.MIXED;
    }

    private static Vec3d average(List<Vec3d> values) {
        if (values.isEmpty()) return Vec3d.ZERO;
        Vec3d sum = Vec3d.ZERO;
        for (Vec3d value : values) sum = sum.add(value);
        return sum.multiply(1.0 / values.size());
    }
}
