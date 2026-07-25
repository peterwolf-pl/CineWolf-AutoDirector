package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.IndoorSceneHeuristics;
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

        List<ReplaySourceSegment> segments = request.resolvedSourceSegments();
        if (segments.size() > 1) {
            warnings.add(MontageWarning.warning("montage.warning.multi_source_segments", segments.size()));
            return createMultiSegmentPlan(analysis, request, context, target, segments, warnings);
        }
        return createContinuousPlan(analysis, request, context, target, warnings);
    }

    private MontagePlan createMultiSegmentPlan(ReplayAnalysisResult analysis, MontageRequest request,
                                               MontagePlanningContext context, TargetReference target,
                                               List<ReplaySourceSegment> segments,
                                               List<MontageWarning> warnings) {
        long totalSourceTicks = Math.max(1L, ReplaySourceSegment.totalDurationTicks(segments));
        int totalOutputTicks = Math.max(1, (int) Math.round(request.outputDurationSeconds() * 20.0));
        int[] segmentOutputTicks = allocateSegmentOutputTicks(segments, totalSourceTicks, totalOutputTicks);

        List<PlannedMontageShot> shots = new ArrayList<>();
        ShotType previousType = null;
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            ReplaySourceSegment segment = segments.get(segmentIndex);
            double segmentOutput = segmentOutputTicks[segmentIndex] / 20.0;
            if (segmentOutput <= 0.0) continue;
            MontageRequest segmentRequest = request
                    .withSourceBounds(segment.startTick(), segment.endTick())
                    .withOutputDuration(segmentOutput)
                    .withShotDurations(
                            Math.min(request.minimumShotDuration(), segmentOutput),
                            Math.max(Math.min(request.minimumShotDuration(), segmentOutput),
                                    Math.min(request.maximumShotDuration(), segmentOutput)));
            List<ScoredReplayEvent> segmentCandidates = candidates(analysis, segmentRequest, target);
            if (segmentCandidates.isEmpty()) {
                warnings.add(MontageWarning.warning("montage.warning.segment_without_events",
                        segmentIndex + 1, segment.label().isBlank() ? (segmentIndex + 1) : segment.label()));
                continue;
            }
            try {
                SegmentShots planned = planShotsForRegion(segmentCandidates, segmentRequest, analysis, context,
                        target, previousType, warnings);
                shots.addAll(planned.shots());
                previousType = planned.lastShotType().orElse(previousType);
            } catch (IllegalArgumentException exception) {
                warnings.add(MontageWarning.warning("montage.warning.segment_layout_failed",
                        segmentIndex + 1, exception.getMessage()));
            }
        }
        if (shots.isEmpty()) {
            throw new IllegalArgumentException("No detected replay events can be planned across source segments");
        }
        // Re-index and pack output times; Timelapse bridges any source cuts between regions/highlights.
        List<PlannedMontageShot> packed = packShotsWithSegmentBridges(shots);
        return finalizePlan(request, context, packed, warnings);
    }

    private MontagePlan createContinuousPlan(ReplayAnalysisResult analysis, MontageRequest request,
                                             MontagePlanningContext context, TargetReference target,
                                             List<MontageWarning> warnings) {
        List<ScoredReplayEvent> candidates = candidates(analysis, request, target);
        if (candidates.isEmpty()) throw new IllegalArgumentException("No detected replay events can be planned");
        SegmentShots planned = planShotsForRegion(candidates, request, analysis, context, target, null, warnings);
        List<PlannedMontageShot> packed = packShotsWithSegmentBridges(planned.shots());
        return finalizePlan(request, context, packed, warnings);
    }

    /**
     * Prefer highlight-jump layout: pick the most attractive events anywhere in the region and leave
     * source gaps between them. Flashback Timelapse bridges those gaps with keyframe jumps. Falls back
     * to a continuous source window when isolated windows cannot be fitted.
     */
    private SegmentShots planShotsForRegion(List<ScoredReplayEvent> candidates, MontageRequest request,
                                            ReplayAnalysisResult analysis, MontagePlanningContext context,
                                            TargetReference target, ShotType previousType,
                                            List<MontageWarning> warnings) {
        Optional<HighlightLayout> highlight = highlightJumpLayout(candidates, request);
        if (highlight.isPresent()) {
            HighlightLayout layout = highlight.orElseThrow();
            List<ScoredReplayEvent> selected = layout.events();
            if (selected.stream().map(value -> value.event().eventId()).distinct().count() < selected.size()) {
                warnings.add(MontageWarning.warning("montage.warning.events_reused",
                        selected.stream().map(value -> value.event().eventId()).distinct().count(),
                        selected.size()));
            }
            int jumpCount = 0;
            for (int index = 1; index < selected.size(); index++) {
                if (layout.sourceStarts()[index] > layout.sourceEnds()[index - 1]) jumpCount++;
            }
            if (jumpCount > 0) {
                warnings.add(MontageWarning.warning("montage.warning.highlight_jumps", jumpCount));
            }
            List<PlannedMontageShot> shots = new ArrayList<>(selected.size());
            ShotType previous = previousType;
            double outputCursor = 0.0;
            for (int index = 0; index < selected.size(); index++) {
                PlannedMontageShot shot = buildShot(selected.get(index), layout.sourceStarts()[index],
                        layout.sourceEnds()[index], layout.outputTicks()[index] / 20.0, outputCursor, index,
                        selected.size(), previous, target, analysis, request, context, warnings);
                if (index > 0 && layout.sourceStarts()[index] > layout.sourceEnds()[index - 1]) {
                    List<String> reasons = new ArrayList<>(shot.planningReasons());
                    reasons.add("montage.reason.highlight_jump");
                    shot = shot.withPlanningReasons(reasons);
                }
                shots.add(shot);
                outputCursor += shot.outputDurationSeconds();
                previous = shot.shotType();
            }
            return new SegmentShots(shots, Optional.ofNullable(previous));
        }

        ContinuousLayout layout = continuousLayout(candidates, request);
        List<ScoredReplayEvent> selected = layout.events();
        if (selected.stream().map(value -> value.event().eventId()).distinct().count() < selected.size()) {
            warnings.add(MontageWarning.warning("montage.warning.events_reused",
                    selected.stream().map(value -> value.event().eventId()).distinct().count(), selected.size()));
        }
        List<PlannedMontageShot> shots = new ArrayList<>(selected.size());
        long[] sourceBoundaries = layout.sourceBoundaries();
        int[] outputTicks = layout.outputTicks();
        double outputCursor = 0.0;
        ShotType previous = previousType;
        for (int index = 0; index < selected.size(); index++) {
            PlannedMontageShot shot = buildShot(selected.get(index), sourceBoundaries[index],
                    sourceBoundaries[index + 1], outputTicks[index] / 20.0, outputCursor, index, selected.size(),
                    previous, target, analysis, request, context, warnings);
            shots.add(shot);
            outputCursor += shot.outputDurationSeconds();
            previous = shot.shotType();
        }
        return new SegmentShots(shots, Optional.ofNullable(previous));
    }

    private PlannedMontageShot buildShot(ScoredReplayEvent scored, long sourceStart, long sourceEnd,
                                         double duration, double outputCursor, int index, int shotCount,
                                         ShotType previousType, TargetReference target,
                                         ReplayAnalysisResult analysis, MontageRequest request,
                                         MontagePlanningContext context, List<MontageWarning> warnings) {
        ReplayEvent event = scored.event();
        SourceInterval interval = new SourceInterval(sourceStart, sourceEnd);
        double actualSpeed = ((interval.end - interval.start) / 20.0) / duration;
        boolean vertical = request.aspectRatio()
                == pl.peterwolf.cinewolf.montage.preset.OutputAspectRatio.VERTICAL_9_16;
        FramingType framing = framing(event.type(), index, shotCount, vertical);
        boolean indoor = IndoorSceneHeuristics.isLikelyIndoor(analysis, target, interval.start, interval.end);
        ShotTypeSelection typeSelection = chooseShotType(event.type(), index, shotCount, previousType,
                request, context, indoor);
        ShotType type = typeSelection.selected();
        // Prefer center-safe generators on 9:16 when a wide lateral flyby was selected.
        if (vertical && type == ShotType.FLYBY) {
            if (context.availableShotTypes().contains(ShotType.SIDE_TRACKING)) {
                type = ShotType.SIDE_TRACKING;
            } else if (context.availableShotTypes().contains(ShotType.FOLLOW)) {
                type = ShotType.FOLLOW;
            }
        }
        // Indoor: never use crane/orbit/spiral if a corner/static option is available.
        if (indoor && (type == ShotType.ORBIT || type == ShotType.SPIRAL
                || type == ShotType.CRANE_UP || type == ShotType.CRANE_DOWN || type == ShotType.FLYBY)) {
            if (context.availableShotTypes().contains(ShotType.ROOM_CORNER)
                    && request.shotPreferences().allows(ShotType.ROOM_CORNER)) {
                type = ShotType.ROOM_CORNER;
            } else if (context.availableShotTypes().contains(ShotType.STATIC_TRACKING)
                    && request.shotPreferences().allows(ShotType.STATIC_TRACKING)) {
                type = ShotType.STATIC_TRACKING;
            } else if (context.availableShotTypes().contains(ShotType.FOLLOW)
                    && request.shotPreferences().allows(ShotType.FOLLOW)) {
                type = ShotType.FOLLOW;
            }
        }
        ShotRequest shotRequest = templateResolver.createShotRequest(event, target, type, framing,
                interval.start, interval.end, duration, request.cameraMovementIntensity(), index,
                analysis, request, context);
        List<String> reasons = new ArrayList<>();
        reasons.add(index == 0 ? "montage.reason.introduction" : index == shotCount - 1
                ? "montage.reason.outro" : "montage.reason.event_match");
        reasons.add("montage.reason.event." + event.type().name().toLowerCase(java.util.Locale.ROOT));
        reasons.addAll(scored.scoringReasons());
        if (indoor) {
            reasons.add("montage.reason.indoor_scene");
            if (type == ShotType.ROOM_CORNER || type == ShotType.STATIC_TRACKING) {
                reasons.add("montage.reason.indoor_corner_tracking");
            }
        }
        if (vertical) {
            reasons.add("montage.reason.vertical_9_16_composition");
        }
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
        if (vertical && (framing == FramingType.EXTREME_WIDE || framing == FramingType.WIDE
                || type == ShotType.FLYBY || type == ShotType.SPIRAL)) {
            shotWarnings.add(MontageWarning.warning("montage.warning.vertical_framing_risk"));
        }
        return new PlannedMontageShot(shotId, index, event, scored.finalScore(), target, type, framing,
                interval.start, interval.end, outputCursor, duration, actualSpeed, shotRequest,
                true, false, reasons, shotWarnings);
    }

    private MontagePlan finalizePlan(MontageRequest request, MontagePlanningContext context,
                                     List<PlannedMontageShot> shots, List<MontageWarning> warnings) {
        List<MontageTransition> transitions = transitions(shots);
        List<MontageTimeMapping> mappings = shots.stream().map(PlannedMontageShot::timeMapping).toList();
        double actualOutput = shots.isEmpty() ? request.outputDurationSeconds()
                : shots.getLast().outputEndSeconds();
        MontageTimeMappingValidator.ValidationResult validation = new MontageTimeMappingValidator().validate(
                mappings, actualOutput, request.minimumReplaySpeed(),
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
                request.sourceEndReplayTime(), actualOutput, shots, transitions, mappings,
                statistics, warnings);
    }

    private static int[] allocateSegmentOutputTicks(List<ReplaySourceSegment> segments, long totalSourceTicks,
                                                    int totalOutputTicks) {
        int count = segments.size();
        int[] output = new int[count];
        int assigned = 0;
        for (int index = 0; index < count; index++) {
            if (index == count - 1) {
                output[index] = Math.max(1, totalOutputTicks - assigned);
            } else {
                double share = segments.get(index).durationTicks() / (double) totalSourceTicks;
                output[index] = Math.max(1, (int) Math.round(totalOutputTicks * share));
                assigned += output[index];
            }
        }
        // Keep the sum exact when intermediate rounding overshoots.
        int sum = 0;
        for (int value : output) sum += value;
        if (sum != totalOutputTicks && count > 0) {
            output[count - 1] = Math.max(1, output[count - 1] + (totalOutputTicks - sum));
        }
        return output;
    }

    /**
     * Rebuilds order indices and packs output time contiguously. Source cuts (highlight jumps / multi-region)
     * keep abutting output endpoints; Flashback Timelapse bridges them by advancing at least one output tick
     * at write time ({@code montage.timeline.source_cut_bridged}).
     */
    private static List<PlannedMontageShot> packShotsWithSegmentBridges(List<PlannedMontageShot> shots) {
        if (shots.isEmpty()) return List.of();
        List<PlannedMontageShot> result = new ArrayList<>(shots.size());
        double cursor = 0.0;
        for (int index = 0; index < shots.size(); index++) {
            PlannedMontageShot shot = shots.get(index);
            result.add(shot.withOrderAndOutput(index, cursor));
            cursor += shot.outputDurationSeconds();
        }
        return List.copyOf(result);
    }

    /**
     * A chronological 1x montage cannot be longer than its selected source. The UI deliberately allows an
     * arbitrary requested duration, so fit it to the source instead of spending the full analysis pass only to
     * reject an otherwise usable plan. When speed changes are enabled, the configured minimum replay speed
     * determines the longest output that the source can supply.
     */
    private static MontageRequest fitOutputDurationToSource(MontageRequest request,
                                                             List<MontageWarning> warnings) {
        long sourceTicks = request.totalSourceDurationTicks();
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
        return request.withOutputDuration(fittedOutputDuration)
                .withShotDurations(fittedMinimumShotDuration,
                        Math.max(fittedMinimumShotDuration, request.maximumShotDuration()));
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

    /**
     * Builds a montage from the best-scoring diverse events in the region. Each shot gets a tight source
     * window around its peak; non-adjacent windows become Timelapse keyframe jumps rather than dead air.
     */
    private static Optional<HighlightLayout> highlightJumpLayout(List<ScoredReplayEvent> candidates,
                                                                 MontageRequest request) {
        if (candidates.isEmpty()) return Optional.empty();
        int totalOutputTicks = Math.max(1, (int) Math.round(request.outputDurationSeconds() * 20.0));
        int minimumOutputTicks = Math.max(1, (int) Math.ceil(request.minimumShotDuration() * 20.0 - 1.0e-9));
        int maximumOutputTicks = Math.max(minimumOutputTicks,
                (int) Math.floor(request.maximumShotDuration() * 20.0 + 1.0e-9));
        int minimumSourceTicks = request.allowReplaySpeedChanges()
                ? Math.max(1, (int) Math.ceil(minimumOutputTicks * request.minimumReplaySpeed() - 1.0e-9))
                : minimumOutputTicks;
        int maximumSourceTicks = request.allowReplaySpeedChanges()
                ? Math.max(minimumSourceTicks,
                (int) Math.floor(maximumOutputTicks * request.maximumReplaySpeed() + 1.0e-9))
                : maximumOutputTicks;

        for (boolean allowReusedEvents : List.of(false, true)) {
            for (int count : shotCounts(request)) {
                if ((long) count * minimumOutputTicks > totalOutputTicks
                        || (long) count * maximumOutputTicks < totalOutputTicks) continue;
                List<ScoredReplayEvent> base = selectDiverseEvents(candidates, count);
                if (base.isEmpty()) continue;
                List<ScoredReplayEvent> selection = chronological(repeatToCount(base, count));
                boolean reusesEvent = selection.stream().map(value -> value.event().eventId())
                        .distinct().count() < selection.size();
                if (!allowReusedEvents && reusesEvent) continue;

                int[] outputTicks = allocateOutputTicksByScore(selection, totalOutputTicks,
                        minimumOutputTicks, maximumOutputTicks);
                if (outputTicks == null) continue;

                int[] sourceTicks = new int[count];
                for (int index = 0; index < count; index++) {
                    double speed = replaySpeed(selection.get(index).event(), request);
                    int desired = request.allowReplaySpeedChanges()
                            ? Math.max(1, (int) Math.round(outputTicks[index] * speed))
                            : outputTicks[index];
                    sourceTicks[index] = Math.max(minimumSourceTicks, Math.min(maximumSourceTicks, desired));
                }
                if (!validOutputAllocation(outputTicks, sourceTicks, totalOutputTicks,
                        minimumOutputTicks, maximumOutputTicks, request)) {
                    // Prefer 1x windows when speed allocation is inconsistent.
                    if (request.allowReplaySpeedChanges()) {
                        System.arraycopy(outputTicks, 0, sourceTicks, 0, count);
                        if (!validOutputAllocation(outputTicks, sourceTicks, totalOutputTicks,
                                minimumOutputTicks, maximumOutputTicks, request)) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }

                Optional<long[][]> windows = fitHighlightWindows(selection, sourceTicks,
                        request.sourceStartReplayTime(), request.sourceEndReplayTime());
                if (windows.isEmpty()) continue;
                long[] starts = windows.orElseThrow()[0];
                long[] ends = windows.orElseThrow()[1];
                return Optional.of(new HighlightLayout(selection, starts, ends, outputTicks));
            }
        }
        return Optional.empty();
    }

    private static int[] allocateOutputTicksByScore(List<ScoredReplayEvent> events, int totalTicks,
                                                    int minimumTicks, int maximumTicks) {
        int count = events.size();
        if (count == 0 || (long) count * minimumTicks > totalTicks
                || (long) count * maximumTicks < totalTicks) return null;
        double[] weights = new double[count];
        double weightSum = 0.0;
        for (int index = 0; index < count; index++) {
            weights[index] = Math.max(0.05, events.get(index).finalScore());
            weightSum += weights[index];
        }
        int[] output = new int[count];
        int assigned = 0;
        for (int index = 0; index < count; index++) {
            int share = (int) Math.round(totalTicks * (weights[index] / weightSum));
            output[index] = Math.max(minimumTicks, Math.min(maximumTicks, share));
            assigned += output[index];
        }
        int difference = totalTicks - assigned;
        while (difference != 0) {
            int best = -1;
            double bestPenalty = Double.POSITIVE_INFINITY;
            int direction = difference > 0 ? 1 : -1;
            for (int index = 0; index < count; index++) {
                int candidate = output[index] + direction;
                if (candidate < minimumTicks || candidate > maximumTicks) continue;
                double ideal = totalTicks * (weights[index] / weightSum);
                double penalty = Math.abs(candidate - ideal) - Math.abs(output[index] - ideal);
                if (penalty < bestPenalty - 1.0e-9) {
                    best = index;
                    bestPenalty = penalty;
                }
            }
            if (best < 0) return null;
            output[best] += direction;
            difference -= direction;
        }
        return output;
    }

    /**
     * Places non-overlapping source windows around each event peak. Later windows may start after earlier
     * ones end (source cut / keyframe jump); they may not reverse or overlap.
     */
    private static Optional<long[][]> fitHighlightWindows(List<ScoredReplayEvent> events, int[] sourceTicks,
                                                          long rangeStart, long rangeEnd) {
        int count = events.size();
        if (count == 0 || sourceTicks.length != count) return Optional.empty();
        long[] starts = new long[count];
        long[] ends = new long[count];
        long previousEnd = rangeStart;
        for (int index = 0; index < count; index++) {
            long peak = events.get(index).event().peakReplayTime();
            long length = sourceTicks[index];
            if (length <= 0 || peak < rangeStart || peak > rangeEnd) return Optional.empty();
            // start ∈ [peak - length, peak] ∩ [rangeStart, rangeEnd - length] ∩ [previousEnd, +∞)
            // so the peak stays inside [start, start + length] and windows never reverse/overlap.
            long lower = Math.max(previousEnd, Math.max(rangeStart, peak - length));
            long upper = Math.min(peak, rangeEnd - length);
            if (lower > upper) {
                // Peak is trapped under the previous window; try a minimal abutment that still covers peak.
                long abutStart = previousEnd;
                long abutEnd = abutStart + length;
                if (abutStart <= peak && peak <= abutEnd && abutEnd <= rangeEnd && abutStart >= rangeStart) {
                    starts[index] = abutStart;
                    ends[index] = abutEnd;
                    previousEnd = abutEnd;
                    continue;
                }
                return Optional.empty();
            }
            long preferred = peak - length / 2L;
            starts[index] = Math.max(lower, Math.min(upper, preferred));
            ends[index] = starts[index] + length;
            previousEnd = ends[index];
        }
        return Optional.of(new long[][]{starts, ends});
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
        // Pin user H/J/K highlights first so marked moments always appear in the montage when budget allows.
        for (ScoredReplayEvent candidate : ordered) {
            if (selected.size() >= count) break;
            if (!isUserHighlight(candidate)) continue;
            if (ids.add(candidate.event().eventId())) {
                selected.add(candidate);
                types.add(candidate.event().type());
            }
        }
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

    private static boolean isUserHighlight(ScoredReplayEvent scored) {
        return scored.event().evidence().attributes().stream()
                .anyMatch(attribute -> "user_highlight".equals(attribute.name())
                        && "true".equalsIgnoreCase(attribute.value()));
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
                                                    MontageRequest request, MontagePlanningContext context,
                                                    boolean indoor) {
        List<ShotType> requested = index == 0 ? request.preset().introTemplate().preferredShotTypes()
                : index == count - 1 ? request.preset().outroTemplate().preferredShotTypes()
                : EVENT_SHOTS.getOrDefault(event, List.of(ShotType.FOLLOW, ShotType.ORBIT));
        if (indoor) {
            // Prefer fixed indoor cameras over orbit/crane that pierce walls/ceilings.
            List<ShotType> indoorFirst = new ArrayList<>(List.of(
                    ShotType.ROOM_CORNER, ShotType.STATIC_TRACKING, ShotType.FOLLOW,
                    ShotType.CLOSE_DETAIL, ShotType.DOLLY_IN, ShotType.SIDE_TRACKING));
            for (ShotType type : requested) {
                if (!indoorFirst.contains(type)
                        && type != ShotType.ORBIT && type != ShotType.SPIRAL
                        && type != ShotType.CRANE_UP && type != ShotType.CRANE_DOWN
                        && type != ShotType.FLYBY) {
                    indoorFirst.add(type);
                }
            }
            requested = indoorFirst;
        }
        ShotType firstRequested = requested.getFirst();
        Set<ShotType> allowed = new java.util.HashSet<>(context.availableShotTypes());
        allowed.retainAll(request.shotPreferences().allowedShotTypes());
        if (allowed.isEmpty()) allowed = new java.util.HashSet<>(context.availableShotTypes());
        List<ShotType> candidates = new ArrayList<>();
        requested.stream().filter(allowed::contains).forEach(candidates::add);
        if (indoor) {
            // Demote wide outdoor-style shots to the end of the list.
            candidates.removeIf(type -> type == ShotType.ORBIT || type == ShotType.SPIRAL
                    || type == ShotType.CRANE_UP || type == ShotType.CRANE_DOWN || type == ShotType.FLYBY);
        }
        request.preset().preferredShotTypes().stream().sorted(Comparator.comparing(Enum::ordinal))
                .filter(allowed::contains).filter(type -> !candidates.contains(type))
                .filter(type -> !indoor || (type != ShotType.ORBIT && type != ShotType.CRANE_UP
                        && type != ShotType.CRANE_DOWN && type != ShotType.SPIRAL && type != ShotType.FLYBY))
                .forEach(candidates::add);
        allowed.stream().sorted(Comparator.comparing(Enum::ordinal))
                .filter(type -> !candidates.contains(type)).forEach(candidates::add);
        if (candidates.isEmpty()) {
            candidates.addAll(context.availableShotTypes());
        }
        ShotType selected = candidates.stream().filter(type -> type != previous).findFirst()
                .orElse(candidates.getFirst());
        return new ShotTypeSelection(firstRequested, selected, selected != firstRequested);
    }

    private static String shotTranslationKey(ShotType type) {
        return "cinewolf.shot." + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static FramingType framing(ReplayEventType type, int index, int count, boolean vertical) {
        // Vertical 9:16 keeps intro/outro tighter so subjects stay inside the tall safe area.
        if (index == 0 || index == count - 1) {
            return vertical ? FramingType.MEDIUM : FramingType.WIDE;
        }
        FramingType base = switch (type) {
            case COMBAT, DAMAGE, DEATH, PAUSE -> FramingType.CLOSE;
            case HIGH_SPEED, VEHICLE_MOVEMENT, FLIGHT, ALTITUDE_GAIN, ALTITUDE_LOSS -> FramingType.WIDE;
            default -> FramingType.MEDIUM;
        };
        if (!vertical) return base;
        return switch (base) {
            case EXTREME_WIDE, WIDE -> FramingType.MEDIUM;
            default -> base;
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
        map.put(ReplayEventType.POSITION_CHANGE, List.of(ShotType.ROOM_CORNER, ShotType.DOLLY_IN, ShotType.REVEAL, ShotType.ORBIT));
        map.put(ReplayEventType.HIGH_SPEED, List.of(ShotType.CHASE, ShotType.FOLLOW, ShotType.SIDE_TRACKING, ShotType.FLYBY));
        map.put(ReplayEventType.ACCELERATION, List.of(ShotType.CHASE, ShotType.FOLLOW, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.DECELERATION, List.of(ShotType.DOLLY_OUT, ShotType.FOLLOW, ShotType.STATIC_TRACKING, ShotType.ROOM_CORNER));
        map.put(ReplayEventType.SHARP_TURN, List.of(ShotType.ORBIT, ShotType.SPIRAL, ShotType.SIDE_TRACKING, ShotType.FOLLOW));
        map.put(ReplayEventType.ALTITUDE_GAIN, List.of(ShotType.CRANE_UP, ShotType.FLYBY, ShotType.SPIRAL, ShotType.ORBIT));
        map.put(ReplayEventType.ALTITUDE_LOSS, List.of(ShotType.CRANE_DOWN, ShotType.FLYBY, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.COMBAT, List.of(ShotType.FOLLOW, ShotType.STATIC_TRACKING, ShotType.ROOM_CORNER, ShotType.ORBIT, ShotType.FLYBY));
        map.put(ReplayEventType.DAMAGE, List.of(ShotType.CLOSE_DETAIL, ShotType.ORBIT, ShotType.FOLLOW));
        map.put(ReplayEventType.DEATH, List.of(ShotType.DOLLY_OUT, ShotType.CRANE_UP, ShotType.FOLLOW));
        map.put(ReplayEventType.VEHICLE_ENTER, List.of(ShotType.VEHICLE_PROFILE, ShotType.DOLLY_IN, ShotType.FOLLOW));
        map.put(ReplayEventType.VEHICLE_EXIT, List.of(ShotType.VEHICLE_PROFILE, ShotType.DOLLY_OUT, ShotType.ORBIT));
        map.put(ReplayEventType.VEHICLE_MOVEMENT, List.of(ShotType.VEHICLE_PROFILE, ShotType.SIDE_TRACKING, ShotType.CHASE, ShotType.FOLLOW));
        map.put(ReplayEventType.FLIGHT_START, List.of(ShotType.CRANE_UP, ShotType.CHASE, ShotType.FOLLOW, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.FLIGHT, List.of(ShotType.VEHICLE_PROFILE, ShotType.CHASE, ShotType.FOLLOW, ShotType.SPIRAL));
        map.put(ReplayEventType.LANDING, List.of(ShotType.CRANE_DOWN, ShotType.FLYBY, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.BLOCK_PLACEMENT, List.of(ShotType.ROOM_CORNER, ShotType.REVEAL, ShotType.ORBIT, ShotType.CRANE_UP, ShotType.DOLLY_OUT));
        map.put(ReplayEventType.BLOCK_DESTRUCTION, List.of(ShotType.ROOM_CORNER, ShotType.ORBIT, ShotType.CLOSE_DETAIL, ShotType.FLYBY));
        map.put(ReplayEventType.PAUSE, List.of(ShotType.ROOM_CORNER, ShotType.CLOSE_DETAIL, ShotType.DOLLY_IN, ShotType.STATIC_TRACKING, ShotType.ORBIT));
        map.put(ReplayEventType.REPLAY_MARKER, List.of(ShotType.REVEAL, ShotType.DOLLY_IN, ShotType.ORBIT, ShotType.ROOM_CORNER, ShotType.FLYBY));
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

    private record HighlightLayout(List<ScoredReplayEvent> events, long[] sourceStarts, long[] sourceEnds,
                                   int[] outputTicks) {
        private HighlightLayout {
            events = List.copyOf(events);
            sourceStarts = sourceStarts.clone();
            sourceEnds = sourceEnds.clone();
            outputTicks = outputTicks.clone();
        }

        @Override
        public long[] sourceStarts() {
            return sourceStarts.clone();
        }

        @Override
        public long[] sourceEnds() {
            return sourceEnds.clone();
        }

        @Override
        public int[] outputTicks() {
            return outputTicks.clone();
        }
    }

    private record SegmentShots(List<PlannedMontageShot> shots, Optional<ShotType> lastShotType) {
        private SegmentShots {
            shots = List.copyOf(shots);
            lastShotType = Objects.requireNonNullElse(lastShotType, Optional.empty());
        }
    }
}
