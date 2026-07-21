package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.RankedReplayTarget;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Deterministic, registry-constrained montage planner. */
public final class DefaultMontagePlanner implements MontagePlanner {
    private static final Map<ReplayEventType, List<ShotType>> EVENT_SHOTS = eventShotMap();
    private final ShotTemplateResolver templateResolver;
    private final ShotDiversityScorer diversityScorer = new ShotDiversityScorer();

    public DefaultMontagePlanner() {
        this(new DefaultShotTemplateResolver());
    }

    public DefaultMontagePlanner(ShotTemplateResolver templateResolver) {
        this.templateResolver = Objects.requireNonNull(templateResolver);
    }

    @Override
    public MontagePlan createPlan(ReplayAnalysisResult analysis, MontageRequest request,
                                  MontagePlanningContext context) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        if (analysis.request().startReplayTime() > request.sourceStartReplayTime()
                || analysis.request().endReplayTime() < request.sourceEndReplayTime()) {
            throw new IllegalArgumentException("Montage request exceeds the analyzed replay range");
        }
        List<MontageWarning> warnings = new ArrayList<>();
        request = fitOutputDurationToSource(request, warnings);
        TargetReference target = selectTarget(analysis, request).orElseThrow(
                () -> new IllegalArgumentException("No analyzed target is available for montage planning"));
        if (!request.preferChronologicalOrder()) {
            warnings.add(MontageWarning.warning("montage.warning.flashback_requires_chronological_source"));
        }

        List<ScoredReplayEvent> candidates = candidates(analysis, request, target);
        if (candidates.isEmpty()) throw new IllegalArgumentException("No detected replay events can be planned");
        ContinuousLayout layout = continuousLayout(candidates, request);
        List<ScoredReplayEvent> selected = layout.events();
        if (selected.stream().map(value -> value.event().eventId()).distinct().count() < selected.size()) {
            warnings.add(MontageWarning.warning("montage.warning.events_reused",
                    selected.stream().map(value -> value.event().eventId()).distinct().count(), selected.size()));
        }
        int shotCount = selected.size();
        List<PlannedMontageShot> shots = new ArrayList<>(shotCount);
        long[] sourceBoundaries = layout.sourceBoundaries();
        int[] outputTicks = layout.outputTicks();
        double outputCursor = 0.0;
        ShotType previousType = null;
        for (int index = 0; index < shotCount; index++) {
            ScoredReplayEvent scored = selected.get(index);
            ReplayEvent event = scored.event();
            SourceInterval interval = new SourceInterval(sourceBoundaries[index], sourceBoundaries[index + 1]);
            double duration = outputTicks[index] / 20.0;
            double actualSpeed = ((interval.end - interval.start) / 20.0) / duration;
            FramingType framing = framing(event.type(), index, shotCount);
            ShotTypeSelection typeSelection = chooseShotType(event.type(), index, shotCount, previousType,
                    request, context);
            ShotType type = typeSelection.selected();
            ShotRequest shotRequest = templateResolver.createShotRequest(event, target, type, framing,
                    interval.start, interval.end, duration, request.cameraMovementIntensity(), index,
                    analysis, request, context);
            List<String> reasons = new ArrayList<>();
            reasons.add(index == 0 ? "montage.reason.introduction" : index == shotCount - 1
                    ? "montage.reason.outro" : "montage.reason.event_match");
            reasons.add("montage.reason.event." + event.type().name().toLowerCase(java.util.Locale.ROOT));
            reasons.addAll(scored.scoringReasons());
            if (typeSelection.fallback()) {
                reasons.add("montage.reason.shot_fallback;requested=" + shotTranslationKey(typeSelection.requested())
                        + ";chosen=" + shotTranslationKey(typeSelection.selected()));
            }
            if (event.peakReplayTime() < interval.start || event.peakReplayTime() > interval.end) {
                reasons.add("montage.reason.event_lead_in_or_out");
            }
            UUID shotId = UUID.nameUUIDFromBytes((event.eventId() + ":" + index + ":" + type)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            List<MontageWarning> shotWarnings = new ArrayList<>();
            if (request.aspectRatio() == pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio.VERTICAL_9_16
                    && (framing == FramingType.EXTREME_WIDE || type == ShotType.FLYBY)) {
                shotWarnings.add(MontageWarning.warning("montage.warning.vertical_framing_risk"));
            }
            shots.add(new PlannedMontageShot(shotId, index, event, scored.finalScore(), target, type, framing,
                    interval.start, interval.end, outputCursor, duration, actualSpeed, shotRequest,
                    true, false, reasons, shotWarnings));
            outputCursor += duration;
            previousType = type;
        }

        List<MontageTransition> transitions = transitions(shots);
        List<MontageTimeMapping> mappings = shots.stream().map(PlannedMontageShot::timeMapping).toList();
        MontageTimeMappingValidator.ValidationResult validation = new MontageTimeMappingValidator().validate(
                mappings, request.outputDurationSeconds(), request.minimumReplaySpeed(),
                request.maximumReplaySpeed(), request.maximumReplaySpeedChange());
        if (!validation.valid()) {
            validation.errors().forEach(code -> warnings.add(new MontageWarning(code,
                    MontageWarning.Severity.ERROR, List.of())));
        }
        validation.warnings().forEach(code -> warnings.add(MontageWarning.warning(code)));
        double diversity = diversityScorer.score(shots, context.shotDiversity());
        MontagePlanStatistics statistics = statistics(shots, diversity);
        UUID montageId = stableMontageId(request, shots);
        return new MontagePlan(montageId, request.preset(), request.sourceStartReplayTime(),
                request.sourceEndReplayTime(), request.outputDurationSeconds(), shots, transitions, mappings,
                statistics, warnings);
    }

    /**
     * A chronological 1x montage cannot be longer than its selected source. The UI deliberately allows an
     * arbitrary requested duration, so fit it to the source instead of spending the full analysis pass only to
     * reject an otherwise usable plan. When speed changes are enabled, the configured minimum replay speed
     * determines the longest output that the source can supply.
     */
    private static MontageRequest fitOutputDurationToSource(MontageRequest request,
                                                             List<MontageWarning> warnings) {
        long sourceTicks = request.sourceEndReplayTime() - request.sourceStartReplayTime();
        double minimumSpeed = request.allowReplaySpeedChanges() ? request.minimumReplaySpeed() : 1.0;
        long maximumOutputTicks = (long) Math.floor(sourceTicks / minimumSpeed + 1.0e-9);
        long requestedOutputTicks = Math.max(1L, Math.round(request.outputDurationSeconds() * 20.0));
        if (requestedOutputTicks <= maximumOutputTicks) return request;
        if (maximumOutputTicks < 1L) {
            throw new IllegalArgumentException(
                    "Selected replay range is too short for the configured minimum replay speed");
        }

        double fittedOutputDuration = maximumOutputTicks / 20.0;
        warnings.add(MontageWarning.warning("montage.warning.output_shortened_to_source",
                seconds(request.outputDurationSeconds()), seconds(fittedOutputDuration)));
        double fittedMinimumShotDuration = Math.min(request.minimumShotDuration(), fittedOutputDuration);
        return new MontageRequest(request.preset(), request.sourceStartReplayTime(), request.sourceEndReplayTime(),
                fittedOutputDuration, request.aspectRatio(), request.pacing(), request.mainTarget(),
                request.automaticTargetDetection(), fittedMinimumShotDuration,
                Math.max(fittedMinimumShotDuration, request.maximumShotDuration()),
                request.cameraMovementIntensity(), request.cutFrequency(), request.allowReplaySpeedChanges(),
                request.preferChronologicalOrder(), request.minimumReplaySpeed(), request.maximumReplaySpeed(),
                request.maximumReplaySpeedChange(), request.maximumPlannedShots());
    }

    private static String seconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static Optional<TargetReference> selectTarget(ReplayAnalysisResult analysis, MontageRequest request) {
        if (request.mainTarget().isPresent()) {
            TargetReference selected = request.mainTarget().get();
            boolean available = analysis.samples().stream().anyMatch(sample -> sample.entities().containsKey(selected));
            if (available) return Optional.of(selected);
        }
        return analysis.primaryTarget().map(RankedReplayTarget::target);
    }

    private static List<ScoredReplayEvent> candidates(ReplayAnalysisResult analysis, MontageRequest request,
                                                       TargetReference target) {
        List<ScoredReplayEvent> targeted = analysis.rankedEvents().stream()
                .filter(value -> value.event().peakReplayTime() >= request.sourceStartReplayTime()
                        && value.event().peakReplayTime() <= request.sourceEndReplayTime())
                .filter(value -> value.event().targets().isEmpty() || value.event().targets().contains(target))
                .toList();
        if (!targeted.isEmpty()) return targeted;
        return analysis.rankedEvents().stream()
                .filter(value -> value.event().peakReplayTime() >= request.sourceStartReplayTime()
                        && value.event().peakReplayTime() <= request.sourceEndReplayTime()).toList();
    }

    private static ContinuousLayout continuousLayout(List<ScoredReplayEvent> candidates, MontageRequest request) {
        int totalOutputTicks = Math.max(1, (int) Math.round(request.outputDurationSeconds() * 20.0));
        int minimumOutputTicks = Math.max(1, (int) Math.ceil(request.minimumShotDuration() * 20.0 - 1.0e-9));
        int maximumOutputTicks = Math.max(minimumOutputTicks,
                (int) Math.floor(request.maximumShotDuration() * 20.0 + 1.0e-9));
        for (boolean allowReusedEvents : List.of(false, true)) {
            for (int count : shotCounts(request)) {
                if ((long) count * minimumOutputTicks > totalOutputTicks
                        || (long) count * maximumOutputTicks < totalOutputTicks) continue;
                int minimumSourceTicks = request.allowReplaySpeedChanges()
                        ? Math.max(1, (int) Math.ceil(minimumOutputTicks * request.minimumReplaySpeed() - 1.0e-9))
                        : minimumOutputTicks;
                int maximumSourceTicks = request.allowReplaySpeedChanges()
                        ? Math.max(minimumSourceTicks,
                        (int) Math.floor(maximumOutputTicks * request.maximumReplaySpeed() + 1.0e-9))
                        : maximumOutputTicks;
                for (int sourceSpan : sourceSpans(candidates, request, count, totalOutputTicks,
                        minimumSourceTicks, maximumSourceTicks)) {
                    List<List<ScoredReplayEvent>> clusters = sourceClusters(candidates, sourceSpan);
                    for (List<ScoredReplayEvent> cluster : clusters) {
                        for (List<ScoredReplayEvent> selection : eventSelections(cluster, count)) {
                            boolean reusesEvent = selection.stream().map(value -> value.event().eventId())
                                    .distinct().count() < selection.size();
                            if (!allowReusedEvents && reusesEvent) continue;
                            Optional<long[]> boundaries = fitSourceBoundaries(selection, sourceSpan,
                                    minimumSourceTicks, maximumSourceTicks, request.sourceStartReplayTime(),
                                    request.sourceEndReplayTime());
                            if (boundaries.isEmpty()) continue;
                            long[] sourceBoundaries = boundaries.orElseThrow();
                            int[] sourceTicks = new int[count];
                            for (int index = 0; index < count; index++) {
                                sourceTicks[index] = Math.toIntExact(
                                        sourceBoundaries[index + 1] - sourceBoundaries[index]);
                            }
                            int[] outputTicks = allocateOutputTicks(selection, sourceTicks, totalOutputTicks,
                                    minimumOutputTicks, maximumOutputTicks, request);
                            if (!validOutputAllocation(outputTicks, sourceTicks, totalOutputTicks,
                                    minimumOutputTicks, maximumOutputTicks, request)) continue;
                            return new ContinuousLayout(selection, sourceBoundaries, outputTicks);
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException(
                "No continuous source layout can keep every planned event peak inside its shot");
    }

    private static List<Integer> sourceSpans(List<ScoredReplayEvent> candidates, MontageRequest request, int count,
                                             int totalOutputTicks, int minimumSourceTicks,
                                             int maximumSourceTicks) {
        long rangeLength = request.sourceEndReplayTime() - request.sourceStartReplayTime();
        long minimum = (long) count * minimumSourceTicks;
        long maximum = Math.min(rangeLength, (long) count * maximumSourceTicks);
        if (request.allowReplaySpeedChanges()) {
            minimum = Math.max(minimum,
                    (long) Math.ceil(totalOutputTicks * request.minimumReplaySpeed() - 1.0e-9));
            maximum = Math.min(maximum,
                    (long) Math.floor(totalOutputTicks * request.maximumReplaySpeed() + 1.0e-9));
        } else {
            minimum = Math.max(minimum, totalOutputTicks);
            maximum = Math.min(maximum, totalOutputTicks);
        }
        if (minimum > maximum || minimum > Integer.MAX_VALUE) return List.of();
        int minimumSpan = Math.toIntExact(minimum);
        int maximumSpan = Math.toIntExact(Math.min(Integer.MAX_VALUE, maximum));
        int preferred = Math.max(minimumSpan, Math.min(maximumSpan, totalOutputTicks));
        LinkedHashSet<Integer> spans = new LinkedHashSet<>();
        spans.add(preferred);
        spans.add(maximumSpan);
        spans.add(minimumSpan);
        if (!candidates.isEmpty()) {
            long eventSpan = candidates.stream().mapToLong(value -> value.event().peakReplayTime()).max().orElse(0L)
                    - candidates.stream().mapToLong(value -> value.event().peakReplayTime()).min().orElse(0L);
            spans.add(Math.max(minimumSpan, Math.min(maximumSpan,
                    Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(1L, eventSpan))))));
        }
        return List.copyOf(spans);
    }

    private static List<Integer> shotCounts(MontageRequest request) {
        int minimumByMaximumDuration = Math.max(1,
                (int) Math.ceil(request.outputDurationSeconds() / request.maximumShotDuration()));
        int maximumByMinimumDuration = Math.max(1,
                (int) Math.floor(request.outputDurationSeconds() / request.minimumShotDuration()));
        int presetMinimum = Math.min(request.preset().minimumShotCount(), maximumByMinimumDuration);
        int presetMaximum = Math.min(request.preset().maximumShotCount(), request.maximumPlannedShots());
        int minimum = Math.max(minimumByMaximumDuration, presetMinimum);
        int maximum = Math.min(maximumByMinimumDuration, presetMaximum);
        if (maximum < minimum) throw new IllegalArgumentException("Output duration cannot fit the configured shot range");
        int preferred = (int) Math.round(minimum + (maximum - minimum) * request.cutFrequency());
        preferred = Math.max(minimum, Math.min(maximum, preferred));
        List<Integer> result = new ArrayList<>();
        result.add(preferred);
        for (int distance = 1; result.size() < maximum - minimum + 1; distance++) {
            if (preferred - distance >= minimum) result.add(preferred - distance);
            if (preferred + distance <= maximum) result.add(preferred + distance);
        }
        return List.copyOf(result);
    }

    private static List<List<ScoredReplayEvent>> sourceClusters(List<ScoredReplayEvent> candidates, long sourceSpan) {
        List<ScoredReplayEvent> chronological = candidates.stream()
                .sorted(Comparator.comparingLong((ScoredReplayEvent value) -> value.event().peakReplayTime())
                        .thenComparing(value -> value.event().eventId()))
                .toList();
        List<EventCluster> clusters = new ArrayList<>();
        for (int left = 0; left < chronological.size(); left++) {
            int right = left;
            long end = chronological.get(left).event().peakReplayTime() + sourceSpan;
            while (right + 1 < chronological.size()
                    && chronological.get(right + 1).event().peakReplayTime() <= end) right++;
            List<ScoredReplayEvent> values = List.copyOf(chronological.subList(left, right + 1));
            double score = values.stream().mapToDouble(ScoredReplayEvent::finalScore).sum()
                    + values.stream().map(value -> value.event().type()).distinct().count() * 0.15;
            clusters.add(new EventCluster(values, score, values.getFirst().event().peakReplayTime()));
        }
        clusters.sort(Comparator.comparingDouble(EventCluster::score).reversed()
                .thenComparingLong(EventCluster::startReplayTime));
        return clusters.stream().map(EventCluster::events).toList();
    }

    private static List<List<ScoredReplayEvent>> eventSelections(List<ScoredReplayEvent> cluster, int count) {
        List<List<ScoredReplayEvent>> result = new ArrayList<>();
        List<ScoredReplayEvent> diverse = chronological(repeatToCount(selectDiverseEvents(cluster, count), count));
        if (!diverse.isEmpty()) result.add(diverse);

        List<ScoredReplayEvent> chronological = chronological(cluster);
        List<ScoredReplayEvent> coverage = new ArrayList<>();
        if (!chronological.isEmpty()) {
            if (chronological.size() >= count) {
                for (int index = 0; index < count; index++) {
                    int sourceIndex = count == 1 ? chronological.size() / 2
                            : (int) Math.round(index * (chronological.size() - 1.0) / (count - 1.0));
                    coverage.add(chronological.get(sourceIndex));
                }
            } else {
                coverage.addAll(repeatToCount(chronological, count));
            }
        }
        List<ScoredReplayEvent> orderedCoverage = chronological(coverage);
        if (!orderedCoverage.isEmpty() && !orderedCoverage.equals(diverse)) result.add(orderedCoverage);
        return List.copyOf(result);
    }

    private static List<ScoredReplayEvent> chronological(List<ScoredReplayEvent> values) {
        return values.stream().sorted(Comparator
                .comparingLong((ScoredReplayEvent value) -> value.event().peakReplayTime())
                .thenComparing(value -> value.event().eventId())).toList();
    }

    private static Optional<long[]> fitSourceBoundaries(List<ScoredReplayEvent> events, int totalTicks,
                                                        int minimumTicks, int maximumTicks,
                                                        long rangeStart, long rangeEnd) {
        if (events.isEmpty() || (long) events.size() * minimumTicks > totalTicks
                || (long) events.size() * maximumTicks < totalTicks) return Optional.empty();
        long firstPeak = events.getFirst().event().peakReplayTime();
        long minimumStart = Math.max(rangeStart, firstPeak - maximumTicks);
        long maximumStart = Math.min(firstPeak, rangeEnd - totalTicks);
        if (minimumStart > maximumStart) return Optional.empty();
        long idealFirstLength = Math.max(minimumTicks,
                Math.min(maximumTicks, Math.round(totalTicks / (double) events.size())));
        long preferredStart = Math.max(minimumStart,
                Math.min(maximumStart, firstPeak - Math.max(1L, idealFirstLength / 2L)));
        long maximumDistance = Math.max(preferredStart - minimumStart, maximumStart - preferredStart);
        for (long distance = 0; distance <= maximumDistance; distance++) {
            long left = preferredStart - distance;
            if (left >= minimumStart) {
                Optional<long[]> boundaries = fitSourceBoundariesAtStart(events, totalTicks, minimumTicks,
                        maximumTicks, left, rangeEnd);
                if (boundaries.isPresent()) return boundaries;
            }
            long right = preferredStart + distance;
            if (distance > 0 && right <= maximumStart) {
                Optional<long[]> boundaries = fitSourceBoundariesAtStart(events, totalTicks, minimumTicks,
                        maximumTicks, right, rangeEnd);
                if (boundaries.isPresent()) return boundaries;
            }
        }
        return Optional.empty();
    }

    private static Optional<long[]> fitSourceBoundariesAtStart(List<ScoredReplayEvent> events, int totalTicks,
                                                               int minimumTicks, int maximumTicks,
                                                               long start, long rangeEnd) {
        int count = events.size();
        long[] reachableMinimum = new long[count + 1];
        long[] reachableMaximum = new long[count + 1];
        reachableMinimum[0] = start;
        reachableMaximum[0] = start;
        for (int index = 0; index < count; index++) {
            long peak = events.get(index).event().peakReplayTime();
            if (reachableMinimum[index] > peak) return Optional.empty();
            long latestStart = Math.min(reachableMaximum[index], peak);
            reachableMinimum[index + 1] = Math.max(peak, reachableMinimum[index] + minimumTicks);
            reachableMaximum[index + 1] = Math.min(rangeEnd, latestStart + maximumTicks);
            if (reachableMinimum[index + 1] > reachableMaximum[index + 1]) return Optional.empty();
        }
        long targetEnd = start + totalTicks;
        if (targetEnd < reachableMinimum[count] || targetEnd > reachableMaximum[count]) return Optional.empty();

        long[] boundaries = new long[count + 1];
        boundaries[count] = targetEnd;
        for (int index = count - 1; index >= 0; index--) {
            long peak = events.get(index).event().peakReplayTime();
            if (peak > boundaries[index + 1]) return Optional.empty();
            long minimum = Math.max(reachableMinimum[index], boundaries[index + 1] - maximumTicks);
            long maximum = Math.min(Math.min(reachableMaximum[index], peak),
                    boundaries[index + 1] - minimumTicks);
            if (minimum > maximum) return Optional.empty();
            long ideal = index == 0 ? start
                    : Math.round((events.get(index - 1).event().peakReplayTime() + peak) * 0.5);
            boundaries[index] = Math.max(minimum, Math.min(maximum, ideal));
        }
        if (boundaries[0] != start) return Optional.empty();
        return Optional.of(boundaries);
    }

    private static List<ScoredReplayEvent> selectDiverseEvents(List<ScoredReplayEvent> candidates, int count) {
        List<ScoredReplayEvent> ordered = candidates.stream()
                .sorted(Comparator.comparingDouble(ScoredReplayEvent::finalScore).reversed()
                        .thenComparing(value -> value.event().peakReplayTime())
                        .thenComparing(value -> value.event().eventId()))
                .toList();
        List<ScoredReplayEvent> selected = new ArrayList<>();
        Set<UUID> ids = new LinkedHashSet<>();
        Set<ReplayEventType> types = new LinkedHashSet<>();
        for (ScoredReplayEvent candidate : ordered) {
            if (selected.size() >= count) break;
            if (types.add(candidate.event().type())) {
                selected.add(candidate);
                ids.add(candidate.event().eventId());
            }
        }
        for (ScoredReplayEvent candidate : ordered) {
            if (selected.size() >= count) break;
            if (ids.add(candidate.event().eventId())) selected.add(candidate);
        }
        return List.copyOf(selected);
    }

    private static List<ScoredReplayEvent> repeatToCount(List<ScoredReplayEvent> selected, int count) {
        if (selected.isEmpty()) return selected;
        List<ScoredReplayEvent> result = new ArrayList<>(selected);
        int cursor = 0;
        while (result.size() < count) result.add(selected.get(cursor++ % selected.size()));
        return List.copyOf(result);
    }

    private static double replaySpeed(ReplayEvent event, MontageRequest request) {
        if (!request.allowReplaySpeedChanges()) return 1.0;
        double desired = switch (event.type()) {
            case COMBAT, DAMAGE, DEATH, LANDING, SHARP_TURN -> 0.75;
            case PAUSE, POSITION_CHANGE -> 1.25;
            default -> 1.0;
        };
        return Math.max(request.minimumReplaySpeed(), Math.min(request.maximumReplaySpeed(), desired));
    }

    private static int[] allocateOutputTicks(List<ScoredReplayEvent> events, int[] sourceTicks, int totalTicks,
                                             int minimumTicks, int maximumTicks, MontageRequest request) {
        int[] normalSpeed = sourceTicks.clone();
        if (!request.allowReplaySpeedChanges()) return normalSpeed;
        int count = sourceTicks.length;
        int[] minimum = new int[count];
        int[] maximum = new int[count];
        int[] output = new int[count];
        double[] desired = new double[count];
        long minimumTotal = 0;
        long maximumTotal = 0;
        for (int index = 0; index < count; index++) {
            minimum[index] = Math.max(minimumTicks,
                    (int) Math.ceil(sourceTicks[index] / request.maximumReplaySpeed() - 1.0e-9));
            maximum[index] = Math.min(maximumTicks,
                    (int) Math.floor(sourceTicks[index] / request.minimumReplaySpeed() + 1.0e-9));
            if (minimum[index] > maximum[index]) return normalSpeed;
            minimumTotal += minimum[index];
            maximumTotal += maximum[index];
            desired[index] = sourceTicks[index] / replaySpeed(events.get(index).event(), request);
            output[index] = Math.max(minimum[index], Math.min(maximum[index], (int) Math.round(desired[index])));
        }
        if (minimumTotal > totalTicks || maximumTotal < totalTicks) return normalSpeed;
        int difference = totalTicks - java.util.Arrays.stream(output).sum();
        while (difference != 0) {
            int best = -1;
            double bestPenalty = Double.POSITIVE_INFINITY;
            int direction = difference > 0 ? 1 : -1;
            for (int index = 0; index < count; index++) {
                int candidate = output[index] + direction;
                if (candidate < minimum[index] || candidate > maximum[index]) continue;
                double penalty = Math.abs(candidate - desired[index]) - Math.abs(output[index] - desired[index]);
                if (penalty < bestPenalty - 1.0e-9) {
                    best = index;
                    bestPenalty = penalty;
                }
            }
            if (best < 0) return normalSpeed;
            output[best] += direction;
            difference -= direction;
        }
        for (int index = 1; index < count; index++) {
            double previous = sourceTicks[index - 1] / (double) output[index - 1];
            double current = sourceTicks[index] / (double) output[index];
            if (Math.abs(current - previous) > request.maximumReplaySpeedChange() + 1.0e-9) {
                return normalSpeed;
            }
        }
        return output;
    }

    private static boolean validOutputAllocation(int[] outputTicks, int[] sourceTicks, int totalTicks,
                                                 int minimumTicks, int maximumTicks, MontageRequest request) {
        if (outputTicks.length != sourceTicks.length
                || java.util.Arrays.stream(outputTicks).sum() != totalTicks) return false;
        double previousSpeed = Double.NaN;
        for (int index = 0; index < outputTicks.length; index++) {
            if (outputTicks[index] < minimumTicks || outputTicks[index] > maximumTicks) return false;
            double speed = sourceTicks[index] / (double) outputTicks[index];
            if (speed < request.minimumReplaySpeed() - 1.0e-9
                    || speed > request.maximumReplaySpeed() + 1.0e-9) return false;
            if (Double.isFinite(previousSpeed)
                    && Math.abs(speed - previousSpeed) > request.maximumReplaySpeedChange() + 1.0e-9) return false;
            previousSpeed = speed;
        }
        return true;
    }

    private static ShotTypeSelection chooseShotType(ReplayEventType event, int index, int count, ShotType previous,
                                                    MontageRequest request, MontagePlanningContext context) {
        List<ShotType> requested = index == 0 ? request.preset().introTemplate().preferredShotTypes()
                : index == count - 1 ? request.preset().outroTemplate().preferredShotTypes()
                : EVENT_SHOTS.getOrDefault(event, List.of(ShotType.FOLLOW, ShotType.ORBIT));
        ShotType firstRequested = requested.getFirst();
        List<ShotType> candidates = new ArrayList<>();
        requested.stream().filter(context.availableShotTypes()::contains).forEach(candidates::add);
        request.preset().preferredShotTypes().stream().sorted(Comparator.comparing(Enum::ordinal))
                .filter(context.availableShotTypes()::contains).filter(type -> !candidates.contains(type))
                .forEach(candidates::add);
        context.availableShotTypes().stream().sorted(Comparator.comparing(Enum::ordinal))
                .filter(type -> !candidates.contains(type)).forEach(candidates::add);
        ShotType selected = candidates.stream().filter(type -> type != previous).findFirst()
                .orElse(candidates.getFirst());
        return new ShotTypeSelection(firstRequested, selected, selected != firstRequested);
    }

    private static String shotTranslationKey(ShotType type) {
        return "cinewolf.shot." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static FramingType framing(ReplayEventType type, int index, int count) {
        if (index == 0 || index == count - 1) return FramingType.WIDE;
        return switch (type) {
            case COMBAT, DAMAGE, DEATH, PAUSE -> FramingType.CLOSE;
            case HIGH_SPEED, VEHICLE_MOVEMENT, FLIGHT, ALTITUDE_GAIN, ALTITUDE_LOSS -> FramingType.WIDE;
            default -> FramingType.MEDIUM;
        };
    }

    private static List<MontageTransition> transitions(List<PlannedMontageShot> shots) {
        List<MontageTransition> result = new ArrayList<>();
        for (int index = 1; index < shots.size(); index++) {
            PlannedMontageShot previous = shots.get(index - 1);
            PlannedMontageShot current = shots.get(index);
            result.add(new MontageTransition(previous.shotId(), current.shotId(), MontageTransitionType.HARD_CUT,
                    current.outputStartSeconds(), List.of("montage.transition.hard_cut")));
        }
        return List.copyOf(result);
    }

    private static MontagePlanStatistics statistics(List<PlannedMontageShot> shots, double diversity) {
        return new MontagePlanStatistics(shots.size(), (int) shots.stream().filter(PlannedMontageShot::enabled).count(),
                (int) shots.stream().map(shot -> shot.sourceEvent().eventId()).distinct().count(),
                (int) shots.stream().map(PlannedMontageShot::shotType).distinct().count(),
                (int) shots.stream().map(PlannedMontageShot::target).distinct().count(),
                shots.stream().mapToDouble(PlannedMontageShot::outputDurationSeconds).sum(), diversity);
    }

    private static UUID stableMontageId(MontageRequest request, List<PlannedMontageShot> shots) {
        StringBuilder key = new StringBuilder(request.preset().id()).append(':')
                .append(request.sourceStartReplayTime()).append(':').append(request.sourceEndReplayTime()).append(':')
                .append(request.outputDurationSeconds());
        shots.forEach(shot -> key.append(':').append(shot.shotId()));
        return UUID.nameUUIDFromBytes(key.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Map<ReplayEventType, List<ShotType>> eventShotMap() {
        EnumMap<ReplayEventType, List<ShotType>> map = new EnumMap<>(ReplayEventType.class);
        map.put(ReplayEventType.POSITION_CHANGE, List.of(ShotType.DOLLY_IN, ShotType.ORBIT));
        map.put(ReplayEventType.HIGH_SPEED, List.of(ShotType.FOLLOW, ShotType.FLYBY));
        map.put(ReplayEventType.ACCELERATION, List.of(ShotType.FOLLOW, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.DECELERATION, List.of(ShotType.DOLLY_OUT, ShotType.FOLLOW));
        map.put(ReplayEventType.SHARP_TURN, List.of(ShotType.ORBIT, ShotType.FOLLOW));
        map.put(ReplayEventType.ALTITUDE_GAIN, List.of(ShotType.FLYBY, ShotType.ORBIT));
        map.put(ReplayEventType.ALTITUDE_LOSS, List.of(ShotType.FLYBY, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.COMBAT, List.of(ShotType.FOLLOW, ShotType.ORBIT, ShotType.FLYBY));
        map.put(ReplayEventType.DAMAGE, List.of(ShotType.ORBIT, ShotType.FOLLOW));
        map.put(ReplayEventType.DEATH, List.of(ShotType.DOLLY_OUT, ShotType.FOLLOW));
        map.put(ReplayEventType.VEHICLE_ENTER, List.of(ShotType.DOLLY_IN, ShotType.FOLLOW));
        map.put(ReplayEventType.VEHICLE_EXIT, List.of(ShotType.DOLLY_OUT, ShotType.ORBIT));
        map.put(ReplayEventType.VEHICLE_MOVEMENT, List.of(ShotType.FOLLOW, ShotType.FLYBY));
        map.put(ReplayEventType.FLIGHT_START, List.of(ShotType.FOLLOW, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.FLIGHT, List.of(ShotType.FOLLOW, ShotType.FLYBY, ShotType.ORBIT));
        map.put(ReplayEventType.LANDING, List.of(ShotType.FLYBY, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.BLOCK_PLACEMENT, List.of(ShotType.ORBIT, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.BLOCK_DESTRUCTION, List.of(ShotType.ORBIT, ShotType.FLYBY));
        map.put(ReplayEventType.PAUSE, List.of(ShotType.DOLLY_IN, ShotType.ORBIT));
        map.put(ReplayEventType.REPLAY_MARKER, List.of(ShotType.DOLLY_IN, ShotType.ORBIT, ShotType.FLYBY));
        return Map.copyOf(map);
    }

    private record SourceInterval(long start, long end) {
    }

    private record EventCluster(List<ScoredReplayEvent> events, double score, long startReplayTime) {
    }

    private record ShotTypeSelection(ShotType requested, ShotType selected, boolean fallback) {
    }

    private record ContinuousLayout(List<ScoredReplayEvent> events, long[] sourceBoundaries, int[] outputTicks) {
        private ContinuousLayout {
            events = List.copyOf(events);
            sourceBoundaries = sourceBoundaries.clone();
            outputTicks = outputTicks.clone();
        }

        @Override
        public long[] sourceBoundaries() {
            return sourceBoundaries.clone();
        }

        @Override
        public int[] outputTicks() {
            return outputTicks.clone();
        }
    }
}
