package pl.peterwolf.cinewolf.montage.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** One selected source window from the replay used for multi-region montage assembly. */
public record ReplaySourceSegment(long startTick, long endTick, String label) {
    public ReplaySourceSegment {
        if (startTick < 0 || endTick <= startTick) {
            throw new IllegalArgumentException("Source segment must move forwards");
        }
        label = Objects.requireNonNullElse(label, "").trim();
    }

    public long durationTicks() {
        return endTick - startTick;
    }

    public double durationSeconds() {
        return durationTicks() / 20.0;
    }

    public boolean contains(long tick) {
        return tick >= startTick && tick <= endTick;
    }

    public static ReplaySourceSegment of(long startTick, long endTick) {
        return new ReplaySourceSegment(startTick, endTick, "");
    }

    public static List<ReplaySourceSegment> normalize(List<ReplaySourceSegment> segments) {
        if (segments == null || segments.isEmpty()) return List.of();
        List<ReplaySourceSegment> ordered = segments.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(ReplaySourceSegment::startTick)
                        .thenComparingLong(ReplaySourceSegment::endTick))
                .toList();
        List<ReplaySourceSegment> merged = new ArrayList<>();
        for (ReplaySourceSegment segment : ordered) {
            if (merged.isEmpty()) {
                merged.add(segment);
                continue;
            }
            ReplaySourceSegment last = merged.getLast();
            if (segment.startTick() <= last.endTick()) {
                merged.set(merged.size() - 1, new ReplaySourceSegment(last.startTick(),
                        Math.max(last.endTick(), segment.endTick()),
                        last.label().isBlank() ? segment.label() : last.label()));
            } else {
                merged.add(segment);
            }
        }
        return List.copyOf(merged);
    }

    public static long totalDurationTicks(List<ReplaySourceSegment> segments) {
        return normalize(segments).stream().mapToLong(ReplaySourceSegment::durationTicks).sum();
    }
}
