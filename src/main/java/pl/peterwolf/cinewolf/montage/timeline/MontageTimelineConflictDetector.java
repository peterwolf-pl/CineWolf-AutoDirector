package pl.peterwolf.cinewolf.montage.timeline;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Detects both keys inside an interval and enabled interpolation segments crossing it. */
public final class MontageTimelineConflictDetector {
    public MontageTimelineConflictReport detect(List<ExistingTrack> tracks, MontageTimelineInterval interval) {
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(interval, "interval");
        int cameraKeys = 0;
        int fovKeys = 0;
        int timelapseKeys = 0;
        int cameraSegments = 0;
        int fovSegments = 0;
        int timelapseSegments = 0;
        int lastTick = -1;

        for (ExistingTrack track : tracks) {
            List<Integer> ticks = track.keyframeTicks().stream().distinct().sorted().toList();
            if (!ticks.isEmpty()) lastTick = Math.max(lastTick, ticks.getLast());
            int keysInside = (int) ticks.stream()
                    .filter(tick -> tick >= interval.startTick() && tick <= interval.endTick()).count();
            int segments = 0;
            if (track.enabled()) {
                for (int index = 1; index < ticks.size(); index++) {
                    int left = ticks.get(index - 1);
                    int right = ticks.get(index);
                    if (left < interval.endTick() && right > interval.startTick()) segments++;
                }
            }
            switch (track.type()) {
                case CAMERA -> {
                    cameraKeys += keysInside;
                    cameraSegments += segments;
                }
                case FOV -> {
                    fovKeys += keysInside;
                    fovSegments += segments;
                }
                case TIMELAPSE -> {
                    timelapseKeys += keysInside;
                    timelapseSegments += segments;
                }
            }
        }
        return new MontageTimelineConflictReport(cameraKeys, fovKeys, timelapseKeys,
                cameraSegments, fovSegments, timelapseSegments, lastTick);
    }

    public enum TrackType { CAMERA, FOV, TIMELAPSE }

    public record ExistingTrack(TrackType type, boolean enabled, List<Integer> keyframeTicks) {
        public ExistingTrack {
            Objects.requireNonNull(type, "type");
            keyframeTicks = List.copyOf(Objects.requireNonNullElse(keyframeTicks, List.of()));
            if (keyframeTicks.stream().anyMatch(tick -> tick == null || tick < 0)) {
                throw new IllegalArgumentException("Existing timeline ticks cannot be null or negative");
            }
            keyframeTicks = keyframeTicks.stream().sorted(Comparator.naturalOrder()).distinct().toList();
        }
    }
}
