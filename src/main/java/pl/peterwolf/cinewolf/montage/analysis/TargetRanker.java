package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TargetRanker {
    public List<RankedReplayTarget> rank(List<ReplaySample> samples,
                                         Map<TargetReference, List<MovementMetrics>> metrics,
                                         List<ReplayEvent> events, Set<TargetReference> selectedTargets,
                                         int maximumTargets) {
        List<ReplaySample> orderedSamples = samples.stream()
                .sorted(Comparator.comparingLong(ReplaySample::replayTime)).toList();
        Set<TargetReference> selected = selectedTargets == null ? Set.of() : Set.copyOf(selectedTargets);
        Set<TargetReference> targets = new HashSet<>();
        orderedSamples.forEach(sample -> targets.addAll(sample.entities().keySet()));
        Map<TargetReference, Long> visible = visibleDurations(orderedSamples, targets);
        List<RankedReplayTarget> result = new ArrayList<>();
        long range = orderedSamples.isEmpty() ? 1L : Math.max(1L,
                orderedSamples.getLast().replayTime() - orderedSamples.getFirst().replayTime());
        for (TargetReference target : targets) {
            List<TargetPoint> targetPoints = targetPoints(target, orderedSamples);
            double distance = movementDistance(metrics.getOrDefault(target, List.of()));
            List<ReplayEvent> targetEvents = events.stream().filter(event -> event.targets().contains(target)).toList();
            int importantEvents = (int) targetEvents.stream().filter(event -> importance(event.type()) >= 0.7).count();
            double visibilityScore = Math.min(1.0, visible.getOrDefault(target, 0L) / (double) range);
            double movementScore = Math.min(1.0, distance / 128.0);
            double eventScore = Math.min(1.0, targetEvents.stream()
                    .mapToDouble(event -> importance(event.type()) * event.confidence()).sum() / 5.0);
            double activityVariety = Math.min(1.0, targetEvents.stream().map(ReplayEvent::type).distinct().count() / 6.0);
            double centrality = eventCentrality(targetPoints, events);
            double markerProximity = markerProximity(targetPoints, orderedSamples);
            double playerBonus = target.entityType().toLowerCase(Locale.ROOT).contains("player") ? 0.08 : 0.0;
            // An explicit user selection is an override, not a weak hint. A value of 1.0 is larger than
            // the complete automatic score range and therefore keeps an observed selected target first.
            double selectedBonus = selected.contains(target) ? 1.0 : 0.0;
            double score = visibilityScore * 0.25 + movementScore * 0.15 + eventScore * 0.25
                    + activityVariety * 0.10 + centrality * 0.10 + markerProximity * 0.07
                    + playerBonus + selectedBonus;
            List<String> reasons = List.of(
                    String.format(Locale.ROOT, "visibility=%.4f", visibilityScore),
                    String.format(Locale.ROOT, "movement=%.4f;distance=%.3f", movementScore, distance),
                    String.format(Locale.ROOT, "events=%.4f;count=%d", eventScore, targetEvents.size()),
                    String.format(Locale.ROOT, "activity_variety=%.4f", activityVariety),
                    String.format(Locale.ROOT, "event_centrality=%.4f", centrality),
                    String.format(Locale.ROOT, "marker_proximity=%.4f", markerProximity),
                    String.format(Locale.ROOT, "player_bonus=%.4f", playerBonus),
                    String.format(Locale.ROOT, "selected_bonus=%.4f", selectedBonus));
            result.add(new RankedReplayTarget(target, score, visible.getOrDefault(target, 0L), distance,
                    importantEvents, reasons));
        }
        result.sort(Comparator.comparingDouble(RankedReplayTarget::score).reversed()
                .thenComparing(target -> target.target().uuid().toString()));
        return result.stream().limit(Math.max(0, maximumTargets)).toList();
    }

    private static Map<TargetReference, Long> visibleDurations(List<ReplaySample> samples,
                                                                Set<TargetReference> targets) {
        Map<TargetReference, Long> result = new HashMap<>();
        Map<TargetReference, Long> previous = new HashMap<>();
        for (ReplaySample sample : samples) {
            for (TargetReference target : targets) {
                if (!sample.entities().containsKey(target)) {
                    previous.remove(target);
                    continue;
                }
                Long last = previous.put(target, sample.replayTime());
                if (last != null) result.merge(target, Math.max(0L, sample.replayTime() - last), Long::sum);
            }
        }
        return result;
    }

    private static double movementDistance(List<MovementMetrics> values) {
        List<MovementMetrics> ordered = values.stream()
                .sorted(Comparator.comparingLong(MovementMetrics::replayTime)).toList();
        double result = 0.0;
        for (int index = 1; index < ordered.size(); index++) {
            result += ordered.get(index - 1).position().distanceTo(ordered.get(index).position());
        }
        return result;
    }

    private static List<TargetPoint> targetPoints(TargetReference target, List<ReplaySample> samples) {
        return samples.stream().filter(sample -> sample.entities().containsKey(target))
                .map(sample -> new TargetPoint(sample.replayTime(), sample.entities().get(target)))
                .toList();
    }

    private static double eventCentrality(List<TargetPoint> points, List<ReplayEvent> events) {
        if (events.isEmpty() || points.isEmpty()) return 0.0;
        double total = 0.0;
        int compared = 0;
        for (ReplayEvent event : events) {
            ReplayEntitySnapshot nearest = nearest(points, event.peakReplayTime()).snapshot();
            total += 1.0 / (1.0 + nearest.pose().position().distanceTo(event.location()) / 32.0);
            compared++;
        }
        return compared == 0 ? 0.0 : total / compared;
    }

    private static double markerProximity(List<TargetPoint> points, List<ReplaySample> samples) {
        List<Long> markerTimes = samples.stream().flatMap(sample -> sample.markers().stream())
                .map(ReplayMarkerSnapshot::replayTime).distinct().toList();
        if (markerTimes.isEmpty() || points.isEmpty()) return 0.0;
        long closeMarkers = markerTimes.stream()
                .filter(markerTime -> tickDistance(nearest(points, markerTime).replayTime(), markerTime) <= 40L)
                .count();
        return closeMarkers / (double) markerTimes.size();
    }

    private static TargetPoint nearest(List<TargetPoint> points, long replayTime) {
        int low = 0;
        int high = points.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long value = points.get(middle).replayTime();
            if (value < replayTime) low = middle + 1;
            else if (value > replayTime) high = middle - 1;
            else return points.get(middle);
        }
        if (low == 0) return points.getFirst();
        if (low >= points.size()) return points.getLast();
        TargetPoint before = points.get(low - 1);
        TargetPoint after = points.get(low);
        return tickDistance(before.replayTime(), replayTime) <= tickDistance(after.replayTime(), replayTime)
                ? before : after;
    }

    private static long tickDistance(long first, long second) {
        return first >= second ? first - second : second - first;
    }

    private static double importance(ReplayEventType type) {
        return switch (type) {
            case DEATH -> 1.0;
            case COMBAT, FLIGHT_START, LANDING, REPLAY_MARKER -> 0.85;
            case DAMAGE, HIGH_SPEED, SHARP_TURN, FLIGHT, VEHICLE_MOVEMENT -> 0.75;
            case BLOCK_PLACEMENT, BLOCK_DESTRUCTION, ACCELERATION, DECELERATION,
                    ALTITUDE_GAIN, ALTITUDE_LOSS -> 0.6;
            case POSITION_CHANGE, VEHICLE_ENTER, VEHICLE_EXIT -> 0.5;
            case PAUSE -> 0.25;
        };
    }

    private record TargetPoint(long replayTime, ReplayEntitySnapshot snapshot) {
    }
}
