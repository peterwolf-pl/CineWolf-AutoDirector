package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable editor operations used by the montage shot-list UI. */
public final class MontagePlanEditor {
    private final ShotDiversityScorer diversityScorer = new ShotDiversityScorer();

    public MontagePlan setEnabled(MontagePlan plan, UUID shotId, boolean enabled) {
        if (!enabled && plan.enabledShots().size() <= 1
                && plan.enabledShots().stream().anyMatch(shot -> shot.shotId().equals(shotId))) return plan;
        return transform(plan, shotId, shot -> shot.withEnabled(enabled));
    }

    public MontagePlan setLocked(MontagePlan plan, UUID shotId, boolean locked) {
        return transform(plan, shotId, shot -> shot.withLocked(locked));
    }

    public MontagePlan replaceRequest(MontagePlan plan, UUID shotId, ShotRequest request,
                                      FramingType framing, List<String> reasons) {
        return transform(plan, shotId, shot -> shot.locked() ? shot : shot.withRequest(
                retimeRequest(request, shot.sourceReplayStartTime(), shot.sourceReplayEndTime(),
                        shot.outputDurationSeconds()), framing, reasons));
    }

    public MontagePlan move(MontagePlan plan, UUID shotId, int offset) {
        Objects.requireNonNull(plan, "plan");
        List<PlannedMontageShot> shots = new ArrayList<>(plan.shots());
        int index = indexOf(shots, shotId);
        if (index < 0 || shots.get(index).locked()) return plan;
        int target = Math.max(0, Math.min(shots.size() - 1, index + offset));
        if (target == index || shots.get(target).locked()) return plan;
        int first = Math.min(index, target);
        int last = Math.max(index, target);
        if (shots.subList(first, last + 1).stream().anyMatch(PlannedMontageShot::locked)) return plan;

        List<PlannedMontageShot> sourceSlots = List.copyOf(shots);
        PlannedMontageShot moved = shots.remove(index);
        shots.add(target, moved);
        List<PlannedMontageShot> remapped = new ArrayList<>(shots.size());
        for (int slot = 0; slot < shots.size(); slot++) {
            remapped.add(applyCreativeTreatment(shots.get(slot), sourceSlots.get(slot)));
        }
        return rebuild(plan, remapped);
    }

    public MontagePlan remove(MontagePlan plan, UUID shotId) {
        Objects.requireNonNull(plan, "plan");
        List<PlannedMontageShot> shots = new ArrayList<>(plan.shots());
        int index = indexOf(shots, shotId);
        if (index < 0 || shots.get(index).locked() || shots.size() <= 1) return plan;
        if (shots.get(index).enabled() && plan.enabledShots().size() <= 1) return plan;
        shots.remove(index);
        return rebuild(plan, shots);
    }

    public MontagePlan duplicate(MontagePlan plan, UUID shotId) {
        Objects.requireNonNull(plan, "plan");
        List<PlannedMontageShot> shots = new ArrayList<>(plan.shots());
        int index = indexOf(shots, shotId);
        if (index < 0) return plan;
        PlannedMontageShot source = shots.get(index);
        long peak = source.sourceEvent().peakReplayTime();
        if (source.locked() || peak <= source.sourceReplayStartTime() || peak >= source.sourceReplayEndTime()
                || source.sourceReplayEndTime() - source.sourceReplayStartTime() < 2
                || source.outputDurationSeconds() <= 0.05) return plan;
        UUID id = UUID.nameUUIDFromBytes((plan.montageId() + ":duplicate:" + source.shotId() + ':' + shots.size())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long split = peak;
        double firstFraction = (split - source.sourceReplayStartTime())
                / (double) (source.sourceReplayEndTime() - source.sourceReplayStartTime());
        double firstDuration = source.outputDurationSeconds() * firstFraction;
        double secondDuration = source.outputDurationSeconds() - firstDuration;
        PlannedMontageShot first = retimeShot(source, source.sourceEvent(), source.sourceEventScore(),
                source.sourceReplayStartTime(), split, source.outputStartSeconds(), firstDuration);
        PlannedMontageShot second = retimeShot(source.duplicate(id), source.sourceEvent(), source.sourceEventScore(),
                split, source.sourceReplayEndTime(), source.outputStartSeconds() + firstDuration, secondDuration);
        shots.set(index, first);
        shots.add(index + 1, second);
        return rebuild(plan, shots);
    }

    public List<PlannedMontageShot> generationShots(MontagePlan plan) {
        return plan.enabledShots();
    }

    public MontagePlan preserveLockedShots(MontagePlan previous, MontagePlan regenerated) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(regenerated, "regenerated");
        List<PlannedMontageShot> shots = new ArrayList<>(regenerated.shots());
        for (PlannedMontageShot locked : previous.shots()) {
            if (!locked.locked()) continue;
            int index = Math.max(0, Math.min(shots.size() - 1, locked.order()));
            shots.set(index, locked);
        }
        return rebuild(regenerated, shots);
    }

    private MontagePlan transform(MontagePlan plan, UUID shotId,
                                  java.util.function.UnaryOperator<PlannedMontageShot> transform) {
        Objects.requireNonNull(plan, "plan");
        List<PlannedMontageShot> shots = new ArrayList<>(plan.shots());
        int index = indexOf(shots, shotId);
        if (index < 0) return plan;
        shots.set(index, transform.apply(shots.get(index)));
        return rebuild(plan, shots);
    }

    private MontagePlan rebuild(MontagePlan plan, List<PlannedMontageShot> rawShots) {
        if (rawShots.isEmpty() || rawShots.stream().noneMatch(PlannedMontageShot::enabled)) return plan;
        List<PlannedMontageShot> sourceNormalized = normalizeEnabledSourceSlots(plan, rawShots);
        if (sourceNormalized == null) return plan;
        double cursor = 0.0;
        List<PlannedMontageShot> shots = new ArrayList<>(sourceNormalized.size());
        for (int index = 0; index < sourceNormalized.size(); index++) {
            PlannedMontageShot source = sourceNormalized.get(index);
            PlannedMontageShot shot = source.withOrderAndOutput(index, cursor);
            shots.add(shot);
            if (shot.enabled()) cursor += shot.outputDurationSeconds();
        }
        List<PlannedMontageShot> enabled = shots.stream().filter(PlannedMontageShot::enabled).toList();
        List<MontageTransition> transitions = new ArrayList<>();
        for (int index = 1; index < enabled.size(); index++) {
            PlannedMontageShot previous = enabled.get(index - 1);
            PlannedMontageShot current = enabled.get(index);
            transitions.add(new MontageTransition(previous.shotId(), current.shotId(),
                    MontageTransitionType.HARD_CUT, current.outputStartSeconds(),
                    List.of("montage.transition.hard_cut")));
        }
        List<MontageTimeMapping> mappings = enabled.stream().map(PlannedMontageShot::timeMapping).toList();
        List<MontageWarning> warnings = plan.warnings().stream()
                .filter(warning -> !warning.code().equals("montage.warning.edited_source_not_monotonic"))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!sourceMonotonic(shots)) {
            warnings.add(new MontageWarning("montage.warning.edited_source_not_monotonic",
                    MontageWarning.Severity.ERROR, List.of()));
        }
        MontagePlanStatistics stats = new MontagePlanStatistics(shots.size(),
                enabled.size(),
                (int) enabled.stream().map(shot -> shot.sourceEvent().eventId()).distinct().count(),
                (int) enabled.stream().map(PlannedMontageShot::shotType).distinct().count(),
                (int) enabled.stream().map(PlannedMontageShot::target).distinct().count(), cursor,
                diversityScorer.score(enabled));
        return new MontagePlan(plan.montageId(), plan.preset(), plan.sourceStartReplayTime(),
                plan.sourceEndReplayTime(), cursor, shots, transitions, mappings, stats,
                warnings.stream().distinct().toList());
    }

    private static List<PlannedMontageShot> normalizeEnabledSourceSlots(MontagePlan plan,
                                                                        List<PlannedMontageShot> rawShots) {
        List<Integer> enabledIndexes = new ArrayList<>();
        for (int index = 0; index < rawShots.size(); index++) {
            if (rawShots.get(index).enabled()) enabledIndexes.add(index);
        }
        if (enabledIndexes.size() <= 1 || alreadyContinuous(rawShots, enabledIndexes)) return List.copyOf(rawShots);

        List<PlannedMontageShot> enabled = enabledIndexes.stream().map(rawShots::get).toList();
        long[] boundaries = fixedLengthBoundaries(plan, enabled);
        if (boundaries == null) return null;

        List<PlannedMontageShot> result = new ArrayList<>(rawShots);
        for (int index = 0; index < enabled.size(); index++) {
            PlannedMontageShot shot = enabled.get(index);
            if (shot.sourceReplayStartTime() != boundaries[index]
                    || shot.sourceReplayEndTime() != boundaries[index + 1]) {
                if (shot.locked()) return null;
                result.set(enabledIndexes.get(index), retimeShot(shot, shot.sourceEvent(), shot.sourceEventScore(),
                        boundaries[index], boundaries[index + 1], shot.outputStartSeconds(),
                        shot.outputDurationSeconds()));
            }
        }
        return List.copyOf(result);
    }

    private static boolean alreadyContinuous(List<PlannedMontageShot> shots, List<Integer> enabledIndexes) {
        long previousEnd = Long.MIN_VALUE;
        for (int index : enabledIndexes) {
            PlannedMontageShot shot = shots.get(index);
            if (previousEnd != Long.MIN_VALUE && shot.sourceReplayStartTime() != previousEnd) return false;
            if (shot.sourceEvent().peakReplayTime() < shot.sourceReplayStartTime()
                    || shot.sourceEvent().peakReplayTime() > shot.sourceReplayEndTime()) return false;
            previousEnd = shot.sourceReplayEndTime();
        }
        return true;
    }

    /**
     * Closes source gaps by shifting intact source slots. Keeping every slot length unchanged also keeps its
     * replay speed unchanged, so disable/remove/re-enable edits remain valid against the original speed bounds.
     */
    private static long[] fixedLengthBoundaries(MontagePlan plan, List<PlannedMontageShot> shots) {
        int count = shots.size();
        if (count == 0) return null;
        long[] boundaries = new long[count + 1];
        long[] offsets = new long[count + 1];
        for (int index = 0; index < count; index++) {
            long length = shots.get(index).sourceReplayEndTime() - shots.get(index).sourceReplayStartTime();
            if (length <= 0 || Long.MAX_VALUE - offsets[index] < length) return null;
            offsets[index + 1] = offsets[index] + length;
        }

        long lowerStart = plan.sourceStartReplayTime();
        long upperStart = plan.sourceEndReplayTime() - offsets[count];
        for (int index = 0; index < count; index++) {
            PlannedMontageShot shot = shots.get(index);
            long peak = shot.sourceEvent().peakReplayTime();
            lowerStart = Math.max(lowerStart, peak - offsets[index + 1]);
            upperStart = Math.min(upperStart, peak - offsets[index]);
            if (shot.locked()) {
                long lockedStart = shot.sourceReplayStartTime() - offsets[index];
                lowerStart = Math.max(lowerStart, lockedStart);
                upperStart = Math.min(upperStart, lockedStart);
            }
        }
        if (lowerStart > upperStart) return null;
        long start = Math.max(lowerStart, Math.min(upperStart, shots.getFirst().sourceReplayStartTime()));
        for (int index = 0; index <= count; index++) boundaries[index] = start + offsets[index];
        return boundaries;
    }

    private static PlannedMontageShot applyCreativeTreatment(PlannedMontageShot creative,
                                                              PlannedMontageShot sourceSlot) {
        ShotRequest request = new ShotRequest(creative.target(), creative.shotType(),
                creative.shotRequest().diameter(), creative.shotRequest().height(), creative.shotRequest().distance(),
                creative.shotRequest().startDistance(), creative.shotRequest().endDistance(),
                creative.shotRequest().rpm(), sourceSlot.outputDurationSeconds(),
                creative.shotRequest().startAngleDegrees(), creative.shotRequest().direction(),
                creative.shotRequest().cameraSpeed(), creative.shotRequest().fov(),
                creative.shotRequest().easing(), creative.shotRequest().lookAheadSeconds(),
                sourceSlot.sourceReplayStartTime(), sourceSlot.sourceReplayEndTime());
        double speed = ((sourceSlot.sourceReplayEndTime() - sourceSlot.sourceReplayStartTime()) / 20.0)
                / sourceSlot.outputDurationSeconds();
        return new PlannedMontageShot(creative.shotId(), sourceSlot.order(), sourceSlot.sourceEvent(),
                sourceSlot.sourceEventScore(), creative.target(), creative.shotType(), creative.framing(),
                sourceSlot.sourceReplayStartTime(), sourceSlot.sourceReplayEndTime(),
                sourceSlot.outputStartSeconds(), sourceSlot.outputDurationSeconds(), speed, request,
                creative.enabled(), creative.locked(), creative.planningReasons(), creative.warnings());
    }

    private static PlannedMontageShot retimeShot(PlannedMontageShot shot,
                                                 pl.peterwolf.cinewolf.montage.event.ReplayEvent sourceEvent,
                                                 double sourceEventScore, long sourceStart, long sourceEnd,
                                                 double outputStart, double outputDuration) {
        ShotRequest request = retimeRequest(shot.shotRequest(), sourceStart, sourceEnd, outputDuration);
        double speed = ((sourceEnd - sourceStart) / 20.0) / outputDuration;
        return new PlannedMontageShot(shot.shotId(), shot.order(), sourceEvent, sourceEventScore, shot.target(),
                shot.shotType(), shot.framing(), sourceStart, sourceEnd, outputStart, outputDuration, speed,
                request, shot.enabled(), shot.locked(), shot.planningReasons(), shot.warnings());
    }

    private static ShotRequest retimeRequest(ShotRequest request, long sourceStart, long sourceEnd,
                                             double outputDuration) {
        return new ShotRequest(request.target(), request.shotType(), request.diameter(), request.height(),
                request.distance(), request.startDistance(), request.endDistance(), request.rpm(), outputDuration,
                request.startAngleDegrees(), request.direction(), request.cameraSpeed(), request.fov(),
                request.easing(), request.lookAheadSeconds(), sourceStart, sourceEnd);
    }

    private static boolean sourceMonotonic(List<PlannedMontageShot> shots) {
        long previousEnd = Long.MIN_VALUE;
        for (PlannedMontageShot shot : shots.stream().filter(PlannedMontageShot::enabled)
                .sorted(Comparator.comparingInt(PlannedMontageShot::order)).toList()) {
            if (previousEnd != Long.MIN_VALUE && shot.sourceReplayStartTime() != previousEnd) return false;
            previousEnd = shot.sourceReplayEndTime();
        }
        return true;
    }

    private static int indexOf(List<PlannedMontageShot> shots, UUID id) {
        for (int index = 0; index < shots.size(); index++) {
            if (shots.get(index).shotId().equals(id)) return index;
        }
        return -1;
    }
}
