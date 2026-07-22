package pl.peterwolf.cinewolf.timeline;

import pl.peterwolf.cinewolf.debug.EventDiagnostic;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Custom CineWolf event mini-timeline overlay model.
 * This is intentionally not a Flashback native timeline extension.
 */
public final class CineWolfEventTimelineOverlay {
    private List<TimelineEventMarker> markers = List.of();

    public void updateFromScoredEvents(List<ScoredReplayEvent> events) {
        if (events == null || events.isEmpty()) {
            markers = List.of();
            return;
        }
        List<TimelineEventMarker> next = new ArrayList<>(events.size());
        for (ScoredReplayEvent scored : events) {
            double confidence = scored.event().confidence();
            next.add(new TimelineEventMarker(
                    scored.event().eventId(),
                    scored.event().type(),
                    scored.event().startReplayTime(),
                    scored.event().peakReplayTime(),
                    scored.event().endReplayTime(),
                    confidence,
                    EventDiagnostic.strengthFor(confidence),
                    scored.finalScore(),
                    scored.event().type().name()
            ));
        }
        next.sort(Comparator.comparingLong(TimelineEventMarker::peakReplayTime)
                .thenComparing(marker -> marker.eventId().toString()));
        markers = List.copyOf(next);
    }

    public List<TimelineEventMarker> markers() {
        return markers;
    }

    public List<TimelineEventMarker> markersInRange(long start, long end) {
        return markers.stream()
                .filter(marker -> marker.endReplayTime() >= start && marker.startReplayTime() <= end)
                .toList();
    }

    public void clear() {
        markers = List.of();
    }

    public int size() {
        return markers.size();
    }

    public boolean isEmpty() {
        return markers.isEmpty();
    }

    public TimelineEventMarker require(int index) {
        return markers.get(Objects.checkIndex(index, markers.size()));
    }
}
