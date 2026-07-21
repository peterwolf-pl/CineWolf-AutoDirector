package pl.peterwolf.cinewolf.montage.event;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EventMerger {
    private static final Set<TypePair> RELATED_TYPES = Set.of(
            TypePair.of(ReplayEventType.HIGH_SPEED, ReplayEventType.ACCELERATION),
            TypePair.of(ReplayEventType.HIGH_SPEED, ReplayEventType.VEHICLE_MOVEMENT),
            TypePair.of(ReplayEventType.HIGH_SPEED, ReplayEventType.FLIGHT),
            TypePair.of(ReplayEventType.FLIGHT_START, ReplayEventType.ALTITUDE_GAIN),
            TypePair.of(ReplayEventType.FLIGHT, ReplayEventType.ALTITUDE_GAIN),
            TypePair.of(ReplayEventType.FLIGHT, ReplayEventType.ALTITUDE_LOSS),
            TypePair.of(ReplayEventType.FLIGHT, ReplayEventType.LANDING),
            TypePair.of(ReplayEventType.COMBAT, ReplayEventType.DAMAGE),
            TypePair.of(ReplayEventType.COMBAT, ReplayEventType.DEATH),
            TypePair.of(ReplayEventType.VEHICLE_MOVEMENT, ReplayEventType.SHARP_TURN)
    );

    public List<ReplayEvent> mergeAndDeduplicate(List<ReplayEvent> input, DetectorThresholds thresholds,
                                                  int maximumEvents) {
        List<ReplayEvent> ordered = new ArrayList<>(input);
        ordered.sort(eventOrder());
        List<ReplayEvent> merged = new ArrayList<>();
        for (ReplayEvent event : ordered) {
            if (!merged.isEmpty() && canMerge(merged.getLast(), event, thresholds)) {
                merged.set(merged.size() - 1, merge(merged.getLast(), event));
            } else {
                merged.add(event);
            }
        }
        List<ReplayEvent> deduplicated = deduplicate(merged, thresholds);
        List<ReplayEvent> related = annotateRelated(deduplicated, thresholds);
        if (related.size() > maximumEvents) {
            related = related.stream()
                    .sorted(Comparator.comparingDouble((ReplayEvent event) -> event.magnitude() * event.confidence())
                            .reversed().thenComparing(eventOrder()))
                    .limit(maximumEvents).sorted(eventOrder()).toList();
        }
        return List.copyOf(related);
    }

    private static boolean canMerge(ReplayEvent left, ReplayEvent right, DetectorThresholds thresholds) {
        if (left.type() != right.type() || left.type() == ReplayEventType.REPLAY_MARKER) return false;
        if (right.startReplayTime() - left.endReplayTime() > mergeGap(left.type(), thresholds)) return false;
        if (!targetsOverlap(left, right)) return false;
        return left.location().distanceTo(right.location()) <= thresholds.eventMergeRadius();
    }

    private static long mergeGap(ReplayEventType type, DetectorThresholds thresholds) {
        return switch (type) {
            case COMBAT -> thresholds.combatMergeGapTicks();
            case DAMAGE -> thresholds.damageMergeGapTicks();
            default -> thresholds.eventMergeGapTicks();
        };
    }

    private static ReplayEvent merge(ReplayEvent left, ReplayEvent right) {
        ReplayEvent peak = Comparator.comparingDouble(ReplayEvent::magnitude)
                .thenComparingDouble(ReplayEvent::confidence)
                .thenComparingLong(event -> -event.peakReplayTime())
                .compare(left, right) >= 0 ? left : right;
        Set<TargetReference> targets = new HashSet<>(left.targets());
        targets.addAll(right.targets());
        double totalWeight = Math.max(1.0e-9, left.confidence() + right.confidence());
        Vec3d location = left.location().multiply(left.confidence() / totalWeight)
                .add(right.location().multiply(right.confidence() / totalWeight));
        return ReplayEvent.create(left.type(), Math.min(left.startReplayTime(), right.startReplayTime()),
                peak.peakReplayTime(), Math.max(left.endReplayTime(), right.endReplayTime()), targets, location,
                Math.max(left.magnitude(), right.magnitude()), Math.max(left.confidence(), right.confidence()),
                left.evidence().merge(right.evidence()));
    }

    private static List<ReplayEvent> deduplicate(List<ReplayEvent> events, DetectorThresholds thresholds) {
        List<ReplayEvent> result = new ArrayList<>();
        for (ReplayEvent candidate : events) {
            int duplicateIndex = -1;
            for (int index = 0; index < result.size(); index++) {
                ReplayEvent existing = result.get(index);
                boolean sameMarker = existing.type() == ReplayEventType.REPLAY_MARKER
                        && candidate.type() == ReplayEventType.REPLAY_MARKER
                        && existing.eventId().equals(candidate.eventId());
                boolean sameDerivedEvent = existing.type() != ReplayEventType.REPLAY_MARKER
                        && existing.type() == candidate.type() && existing.targets().equals(candidate.targets())
                        && Math.abs(existing.peakReplayTime() - candidate.peakReplayTime()) <= 1
                        && existing.location().distanceTo(candidate.location()) <= thresholds.eventMergeRadius();
                if (sameMarker || sameDerivedEvent) {
                    duplicateIndex = index;
                    break;
                }
            }
            if (duplicateIndex < 0) result.add(candidate);
            else if (quality(candidate) > quality(result.get(duplicateIndex))) result.set(duplicateIndex, candidate);
        }
        result.sort(eventOrder());
        return result;
    }

    private static List<ReplayEvent> annotateRelated(List<ReplayEvent> events, DetectorThresholds thresholds) {
        List<ReplayEvent> result = new ArrayList<>(events);
        for (int leftIndex = 0; leftIndex < result.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < result.size(); rightIndex++) {
                ReplayEvent left = result.get(leftIndex);
                ReplayEvent right = result.get(rightIndex);
                if (!RELATED_TYPES.contains(TypePair.of(left.type(), right.type())) || !targetsOverlap(left, right)
                        || right.startReplayTime() - left.endReplayTime() > thresholds.eventMergeGapTicks()
                        || left.startReplayTime() - right.endReplayTime() > thresholds.eventMergeGapTicks()) continue;
                result.set(leftIndex, left.withEvidence(left.evidence().withRelatedType(right.type())));
                result.set(rightIndex, right.withEvidence(right.evidence().withRelatedType(left.type())));
            }
        }
        result.sort(eventOrder());
        return result;
    }

    private static boolean targetsOverlap(ReplayEvent left, ReplayEvent right) {
        if (left.targets().isEmpty() || right.targets().isEmpty()) return true;
        return left.targets().stream().anyMatch(right.targets()::contains);
    }

    private static double quality(ReplayEvent event) {
        return event.confidence() * 0.6 + event.magnitude() * 0.4;
    }

    public static Comparator<ReplayEvent> eventOrder() {
        return Comparator.comparing(ReplayEvent::type).thenComparingLong(ReplayEvent::startReplayTime)
                .thenComparingLong(ReplayEvent::peakReplayTime).thenComparing(event -> event.eventId().toString());
    }

    private record TypePair(ReplayEventType first, ReplayEventType second) {
        private static TypePair of(ReplayEventType left, ReplayEventType right) {
            return left.ordinal() <= right.ordinal() ? new TypePair(left, right) : new TypePair(right, left);
        }
    }
}
