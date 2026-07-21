package pl.peterwolf.cinewolf.montage.timeline;

import java.util.Objects;
import java.util.Optional;

public record MontageTimelineWriteOptions(
        MontageTimelineConflictMode conflictMode,
        Optional<MontageTimelineInterval> confirmedReplaceInterval
) {
    public MontageTimelineWriteOptions {
        conflictMode = Objects.requireNonNullElse(conflictMode, MontageTimelineConflictMode.CANCEL);
        confirmedReplaceInterval = Objects.requireNonNullElse(confirmedReplaceInterval, Optional.empty());
    }

    public static MontageTimelineWriteOptions cancelOnConflict() {
        return new MontageTimelineWriteOptions(MontageTimelineConflictMode.CANCEL, Optional.empty());
    }

    public static MontageTimelineWriteOptions add() {
        return new MontageTimelineWriteOptions(MontageTimelineConflictMode.ADD, Optional.empty());
    }

    public static MontageTimelineWriteOptions replace(MontageTimelineInterval confirmedInterval) {
        return new MontageTimelineWriteOptions(MontageTimelineConflictMode.REPLACE,
                Optional.of(Objects.requireNonNull(confirmedInterval, "confirmedInterval")));
    }

    public static MontageTimelineWriteOptions placeAfterLast() {
        return new MontageTimelineWriteOptions(MontageTimelineConflictMode.PLACE_AFTER_LAST, Optional.empty());
    }
}
