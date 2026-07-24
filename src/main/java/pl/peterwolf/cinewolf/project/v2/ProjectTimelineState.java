package pl.peterwolf.cinewolf.project.v2;

import java.util.Objects;

public record ProjectTimelineState(
        long selectionStart,
        long selectionEnd,
        long playheadTick,
        boolean hasSelection
) {
    public ProjectTimelineState {
        selectionStart = Math.max(0L, selectionStart);
        selectionEnd = Math.max(selectionStart, selectionEnd);
        playheadTick = Math.max(0L, playheadTick);
    }

    public static ProjectTimelineState empty() {
        return new ProjectTimelineState(0, 0, 0, false);
    }
}
