package pl.peterwolf.cinewolf.montage.event.detector;

import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplayMarkerSnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ReplaySampleWindow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ReplayMarkerEventDetector implements ReplayEventDetector {
    @Override
    public Set<ReplayEventType> supportedTypes() {
        return Set.of(ReplayEventType.REPLAY_MARKER);
    }

    @Override
    public List<ReplayEvent> detect(ReplaySampleWindow window, ReplayAnalysisContext context, double sensitivity) {
        List<ReplayEvent> events = new ArrayList<>();
        for (ReplaySample sample : window.samples()) {
            for (ReplayMarkerSnapshot marker : sample.markers()) {
                EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.REPLAY_MARKER,
                                EventEvidence.Measurement.observed("marker_present", 1.0, "boolean"))
                        .withAttribute("marker_id", marker.markerId().toString())
                        .withAttribute("marker_label", marker.label());
                Vec3d location = marker.location().orElseGet(() -> sample.entities().values().stream()
                        .findFirst().map(entity -> entity.pose().position()).orElse(Vec3d.ZERO));
                // The marker UUID is the stable source identity. Using it directly keeps two user markers at the
                // same replay timestamp distinct while repeated adapter observations remain deduplicatable.
                events.add(new ReplayEvent(marker.markerId(), ReplayEventType.REPLAY_MARKER, marker.replayTime(),
                        marker.replayTime(), marker.replayTime(), Set.of(), location, 0.8, 1.0, evidence));
            }
        }
        events.sort(Comparator.comparingLong(ReplayEvent::peakReplayTime)
                .thenComparing(event -> event.eventId().toString()));
        return List.copyOf(events);
    }
}
