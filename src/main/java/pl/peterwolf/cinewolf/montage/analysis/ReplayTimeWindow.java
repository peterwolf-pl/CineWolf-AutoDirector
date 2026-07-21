package pl.peterwolf.cinewolf.montage.analysis;

public record ReplayTimeWindow(long startReplayTime, long endReplayTime) {
    public ReplayTimeWindow {
        if (startReplayTime < 0 || endReplayTime < startReplayTime) {
            throw new IllegalArgumentException("Invalid replay time window");
        }
    }

    public boolean contains(long replayTime) {
        return replayTime >= startReplayTime && replayTime <= endReplayTime;
    }
}
