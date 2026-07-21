package pl.peterwolf.cinewolf.montage.timeline;

/** Inclusive native Flashback/source-replay interval used for conflict detection and replacement. */
public record MontageTimelineInterval(int startTick, int endTick) {
    public MontageTimelineInterval {
        if (startTick < 0 || endTick <= startTick) {
            throw new IllegalArgumentException("Montage timeline interval must move forwards");
        }
    }

    public int durationTicks() {
        return endTick - startTick;
    }

    public boolean contains(MontageTimelineInterval other) {
        return other != null && startTick <= other.startTick && endTick >= other.endTick;
    }

}
