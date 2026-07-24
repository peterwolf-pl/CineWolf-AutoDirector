package pl.peterwolf.cinewolf.montage.highlight;

import pl.peterwolf.cinewolf.montage.plan.ReplaySourceSegment;

import java.util.Objects;
import java.util.UUID;

/** User-marked moment or fragment for later montage analysis/planning. */
public record MontageHighlight(
        UUID id,
        long startTick,
        long endTick,
        String label,
        Kind kind
) {
    public enum Kind {
        /** Single interesting moment; stored as a short padded window. */
        MOMENT,
        /** Explicit start→end fragment chosen while watching the replay. */
        FRAGMENT
    }

    public MontageHighlight {
        Objects.requireNonNull(id, "id");
        if (startTick < 0 || endTick < startTick) {
            throw new IllegalArgumentException("Highlight range must not reverse");
        }
        if (endTick == startTick) endTick = startTick + 1;
        label = Objects.requireNonNullElse(label, "").trim();
        kind = Objects.requireNonNullElse(kind, Kind.MOMENT);
    }

    public long durationTicks() {
        return endTick - startTick;
    }

    public double durationSeconds() {
        return durationTicks() / 20.0;
    }

    public long peakTick() {
        return startTick + (endTick - startTick) / 2L;
    }

    public ReplaySourceSegment toSourceSegment() {
        return new ReplaySourceSegment(startTick, endTick, label.isBlank() ? kind.name().toLowerCase() : label);
    }

    public static MontageHighlight moment(long tick, String label, long paddingTicks) {
        long pad = Math.max(1L, paddingTicks);
        long start = Math.max(0L, tick - pad);
        long end = Math.max(start + 1L, tick + pad);
        return new MontageHighlight(UUID.randomUUID(), start, end,
                label == null || label.isBlank() ? "moment" : label, Kind.MOMENT);
    }

    public static MontageHighlight fragment(long startTick, long endTick, String label) {
        long start = Math.min(startTick, endTick);
        long end = Math.max(startTick, endTick);
        if (end <= start) end = start + 1;
        return new MontageHighlight(UUID.randomUUID(), start, end,
                label == null || label.isBlank() ? "fragment" : label, Kind.FRAGMENT);
    }
}
