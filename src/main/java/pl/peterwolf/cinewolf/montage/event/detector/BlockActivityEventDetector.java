package pl.peterwolf.cinewolf.montage.event.detector;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.ObservedReplayAction;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ReplaySampleWindow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class BlockActivityEventDetector implements ReplayEventDetector {
    private static final Set<ReplayEventType> TYPES = Set.copyOf(EnumSet.of(
            ReplayEventType.BLOCK_PLACEMENT, ReplayEventType.BLOCK_DESTRUCTION));

    @Override
    public Set<ReplayEventType> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<ReplayEvent> detect(ReplaySampleWindow window, ReplayAnalysisContext context, double sensitivity) {
        List<BlockObservation> observations = new ArrayList<>();
        for (ReplaySample sample : window.samples()) {
            for (ObservedReplayAction action : sample.actions()) {
                if (action instanceof ObservedReplayAction.BlockPlaced placed) {
                    if (window.targetFilter().isEmpty() || placed.actor().map(window::includes).orElse(true)) {
                        observations.add(new BlockObservation(ReplayEventType.BLOCK_PLACEMENT, placed.replayTime(),
                                placed.actor(), placed.location(), placed.blockType()));
                    }
                } else if (action instanceof ObservedReplayAction.BlockDestroyed destroyed) {
                    if (window.targetFilter().isEmpty() || destroyed.actor().map(window::includes).orElse(true)) {
                        observations.add(new BlockObservation(ReplayEventType.BLOCK_DESTRUCTION, destroyed.replayTime(),
                                destroyed.actor(), destroyed.location(), destroyed.blockType()));
                    }
                }
            }
        }
        observations.sort(Comparator.comparing(BlockObservation::type).thenComparingLong(BlockObservation::replayTime)
                .thenComparing(observation -> observation.actor().map(target -> target.uuid().toString()).orElse("")));
        return group(observations, context.detectorThresholds());
    }

    private static List<ReplayEvent> group(List<BlockObservation> observations, DetectorThresholds thresholds) {
        List<ReplayEvent> events = new ArrayList<>();
        List<BlockObservation> current = new ArrayList<>();
        for (BlockObservation observation : observations) {
            boolean compatible = current.isEmpty() || (current.getFirst().type() == observation.type()
                    && current.getFirst().actor().equals(observation.actor())
                    && observation.replayTime() - current.getLast().replayTime() <= thresholds.blockGroupGapTicks()
                    && observation.location().distanceTo(current.getLast().location()) <= thresholds.blockGroupRadius());
            if (!compatible) {
                events.add(toEvent(current, thresholds));
                current.clear();
            }
            current.add(observation);
        }
        if (!current.isEmpty()) events.add(toEvent(current, thresholds));
        events.sort(Comparator.comparingLong(ReplayEvent::startReplayTime).thenComparing(ReplayEvent::type));
        return List.copyOf(events);
    }

    private static ReplayEvent toEvent(List<BlockObservation> group, DetectorThresholds thresholds) {
        Vec3d center = Vec3d.ZERO;
        Set<String> blockTypes = new HashSet<>();
        for (BlockObservation observation : group) {
            center = center.add(observation.location());
            blockTypes.add(observation.blockType());
        }
        center = center.multiply(1.0 / group.size());
        double radius = 0.0;
        for (BlockObservation observation : group) radius = Math.max(radius, center.distanceTo(observation.location()));
        BlockObservation peak = group.get(group.size() / 2);
        EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.AGGREGATED_ACTIONS,
                EventEvidence.Measurement.observed("block_count", group.size(), "blocks"),
                EventEvidence.Measurement.atMost("activity_radius", radius, "blocks", thresholds.blockGroupRadius()))
                .withAttribute("block_types", blockTypes.stream().sorted().reduce((left, right) -> left + "," + right)
                        .orElse("unknown"));
        return ReplayEvent.create(group.getFirst().type(), group.getFirst().replayTime(), peak.replayTime(),
                group.getLast().replayTime(), group.getFirst().actor().map(Set::of).orElseGet(Set::of), center,
                Math.min(1.0, group.size() / 8.0), 1.0, evidence);
    }

    private record BlockObservation(ReplayEventType type, long replayTime, Optional<TargetReference> actor,
                                    Vec3d location, String blockType) {
    }
}
