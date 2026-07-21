package pl.peterwolf.cinewolf.montage.timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure conflict-policy resolution performed again under the Flashback scene write lock. */
public final class MontageTimelinePlacementResolver {
    private final MontageTimelineConflictDetector conflictDetector = new MontageTimelineConflictDetector();

    public Resolution resolve(MontageTimelineWritePlan plan, MontageTimelineWriteOptions options,
                              List<MontageTimelineConflictDetector.ExistingTrack> existingTracks) {
        Objects.requireNonNull(plan, "plan");
        options = options == null ? MontageTimelineWriteOptions.cancelOnConflict() : options;
        Objects.requireNonNull(existingTracks, "existingTracks");
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        MontageTimelineConflictReport detected = conflictDetector.detect(existingTracks, plan.outputInterval());
        MontageTimelineWritePlan resolved = plan;

        switch (options.conflictMode()) {
            case CANCEL -> {
                if (detected.hasConflicts()) errors.add("montage.timeline.conflicts_cancelled");
            }
            case ADD -> {
                if (detected.hasConflicts()) warnings.add("montage.timeline.conflicts_preserved");
            }
            case REPLACE -> {
                Optional<MontageTimelineInterval> confirmed = options.confirmedReplaceInterval();
                if (confirmed.isEmpty()) {
                    errors.add("montage.timeline.replace_not_confirmed");
                } else if (!confirmed.get().equals(plan.outputInterval())) {
                    errors.add("montage.timeline.replace_interval_mismatch");
                }
            }
            case PLACE_AFTER_LAST -> {
                // Camera samples and timelapse mappings are bound to source replay ticks. Moving
                // them would select different replay content instead of relocating the montage.
                errors.add("montage.timeline.place_after_last_source_bound");
            }
        }

        MontageTimelineConflictReport remaining = conflictDetector.detect(existingTracks, resolved.outputInterval());
        return new Resolution(errors.isEmpty() ? Optional.of(resolved) : Optional.empty(), detected, remaining,
                errors.stream().distinct().toList(), warnings.stream().distinct().toList());
    }

    public record Resolution(
            Optional<MontageTimelineWritePlan> plan,
            MontageTimelineConflictReport detectedConflicts,
            MontageTimelineConflictReport remainingConflicts,
            List<String> errors,
            List<String> warnings
    ) {
        public Resolution {
            plan = plan == null ? Optional.empty() : plan;
            detectedConflicts = detectedConflicts == null ? MontageTimelineConflictReport.empty() : detectedConflicts;
            remainingConflicts = remainingConflicts == null ? MontageTimelineConflictReport.empty() : remainingConflicts;
            errors = List.copyOf(errors == null ? List.of() : errors);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        public boolean valid() {
            return plan.isPresent() && errors.isEmpty();
        }
    }
}
