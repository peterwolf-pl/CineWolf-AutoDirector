package pl.peterwolf.cinewolf.camera;

/** Resolves a requested shot interval against the finite Flashback replay timeline. */
public final class ReplayIntervalResolver {
    public Resolution resolve(boolean selectedRange, long selectedStart, long selectedEnd,
                              long currentReplayTick, long totalReplayTicks, double requestedDurationSeconds) {
        if (selectedRange) {
            long start = clampToReplay(selectedStart, totalReplayTicks);
            long end = clampToReplay(selectedEnd, totalReplayTicks);
            return new Resolution(start, end, Math.max(0.0, (end - start) / 20.0), true,
                    start != selectedStart || end != selectedEnd);
        }

        long start = clampToReplay(currentReplayTick, totalReplayTicks);
        long durationTicks = durationTicks(requestedDurationSeconds);
        long requestedEnd = saturatedAdd(start, durationTicks);
        long end = totalReplayTicks >= 0 ? Math.min(requestedEnd, totalReplayTicks) : requestedEnd;
        return new Resolution(start, end, Math.max(0.0, (end - start) / 20.0), false,
                end != requestedEnd);
    }

    private static long clampToReplay(long tick, long totalReplayTicks) {
        if (totalReplayTicks < 0) return tick;
        return Math.max(0L, Math.min(totalReplayTicks, tick));
    }

    private static long durationTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0.0) return 0L;
        double ticks = seconds * 20.0;
        return ticks >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(ticks));
    }

    private static long saturatedAdd(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }

    public record Resolution(long startTick, long endTick, double durationSeconds,
                             boolean selectedRange, boolean clippedToReplayEnd) {
    }
}
