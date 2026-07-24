package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.montage.plan.ReplaySourceSegment;

/** Gson-friendly mutable source segment for multi-region montage. */
public final class SourceSegmentConfig {
    public long startTick;
    public long endTick;
    public String label = "";

    public SourceSegmentConfig() {
    }

    public SourceSegmentConfig(long startTick, long endTick, String label) {
        this.startTick = startTick;
        this.endTick = endTick;
        this.label = label == null ? "" : label;
    }

    public void normalize() {
        if (startTick < 0) startTick = 0;
        if (endTick <= startTick) endTick = startTick + 20;
        if (label == null) label = "";
    }

    public ReplaySourceSegment toModel() {
        normalize();
        return new ReplaySourceSegment(startTick, endTick, label);
    }

    public static SourceSegmentConfig from(ReplaySourceSegment segment) {
        return new SourceSegmentConfig(segment.startTick(), segment.endTick(), segment.label());
    }
}
