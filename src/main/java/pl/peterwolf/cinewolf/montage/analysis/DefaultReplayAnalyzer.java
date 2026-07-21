package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.event.DefaultReplayEventScorer;
import pl.peterwolf.cinewolf.montage.event.EventMerger;
import pl.peterwolf.cinewolf.montage.event.EventScoringContext;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.montage.event.ReplayEventScorer;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ReplaySampleWindow;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.event.detector.BlockActivityEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.CombatEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.MovementEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.PauseEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.ReplayMarkerEventDetector;
import pl.peterwolf.cinewolf.montage.event.detector.VehicleFlightEventDetector;
import pl.peterwolf.cinewolf.montage.scene.DefaultSceneSegmenter;
import pl.peterwolf.cinewolf.montage.scene.ReplayScene;
import pl.peterwolf.cinewolf.montage.scene.SceneSegmenter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class DefaultReplayAnalyzer implements ReplayAnalyzer {
    private final List<ReplayEventDetector> detectors;
    private final ReplayEventScorer scorer;
    private final EventMerger eventMerger;
    private final SceneSegmenter sceneSegmenter;
    private final MovementMetricsCalculator movementCalculator;
    private final CoarseDetailedSampleSelector sampleSelector;
    private final TargetRanker targetRanker;

    public DefaultReplayAnalyzer(List<ReplayEventDetector> detectors, ReplayEventScorer scorer,
                                 EventMerger eventMerger, SceneSegmenter sceneSegmenter,
                                 MovementMetricsCalculator movementCalculator,
                                 CoarseDetailedSampleSelector sampleSelector, TargetRanker targetRanker) {
        this.detectors = List.copyOf(detectors);
        this.scorer = Objects.requireNonNull(scorer);
        this.eventMerger = Objects.requireNonNull(eventMerger);
        this.sceneSegmenter = Objects.requireNonNull(sceneSegmenter);
        this.movementCalculator = Objects.requireNonNull(movementCalculator);
        this.sampleSelector = Objects.requireNonNull(sampleSelector);
        this.targetRanker = Objects.requireNonNull(targetRanker);
    }

    public static DefaultReplayAnalyzer createDefault() {
        return new DefaultReplayAnalyzer(List.of(new MovementEventDetector(), new CombatEventDetector(),
                new VehicleFlightEventDetector(), new BlockActivityEventDetector(), new PauseEventDetector(),
                new ReplayMarkerEventDetector()), new DefaultReplayEventScorer(), new EventMerger(),
                new DefaultSceneSegmenter(), new MovementMetricsCalculator(), new CoarseDetailedSampleSelector(),
                new TargetRanker());
    }

    @Override
    public ReplayAnalysisResult analyze(ReplayAnalysisRequest request, ReplayAnalysisContext context,
                                        AnalysisProgressListener progressListener, CancellationToken cancellationToken) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        AnalysisProgressListener progress = Objects.requireNonNullElse(progressListener, AnalysisProgressListener.NONE);
        CancellationToken cancellation = Objects.requireNonNullElse(cancellationToken, CancellationToken.NONE);
        List<AnalysisWarning> warnings = new ArrayList<>();

        report(progress, AnalysisStage.RANGE_VALIDATION, 0.0, 0, 1);
        cancellation.throwIfCancelled();
        List<ReplaySample> input = context.samples().stream()
                .filter(sample -> sample.replayTime() >= request.startReplayTime()
                        && sample.replayTime() <= request.endReplayTime())
                .sorted(Comparator.comparingLong(ReplaySample::replayTime)).toList();
        if (input.isEmpty()) {
            warnings.add(AnalysisWarning.warning("analysis.no_samples_in_range", request.startReplayTime(),
                    request.endReplayTime()));
            return emptyResult(request, context.samples().size(), warnings, progress);
        }
        report(progress, AnalysisStage.RANGE_VALIDATION, 1.0, 1, 1);

        cancellation.throwIfCancelled();
        report(progress, AnalysisStage.COARSE_SAMPLING, 0.0, 0, input.size());
        SampleSelection initialSelection = sampleSelector.select(input, request, context.detectorThresholds());
        List<ReplaySample> selectedSamples = capSamples(initialSelection.combinedSamples(), context.limits().maximumSamples());
        if (selectedSamples.size() < initialSelection.combinedSamples().size()) {
            warnings.add(AnalysisWarning.warning("analysis.sample_limit_applied",
                    initialSelection.combinedSamples().size(), selectedSamples.size()));
        }
        Set<Long> retainedTimes = selectedSamples.stream().map(ReplaySample::replayTime).collect(java.util.stream.Collectors.toSet());
        SampleSelection selection = new SampleSelection(initialSelection.coarseSamples().stream()
                .filter(sample -> retainedTimes.contains(sample.replayTime())).toList(),
                initialSelection.detailedSamples().stream()
                        .filter(sample -> retainedTimes.contains(sample.replayTime())).toList(),
                selectedSamples, initialSelection.detailedWindows());
        report(progress, AnalysisStage.COARSE_SAMPLING, 1.0, selection.coarseSamples().size(), input.size());
        report(progress, AnalysisStage.DETAILED_SAMPLING, 1.0, selection.detailedSamples().size(), input.size());

        cancellation.throwIfCancelled();
        report(progress, AnalysisStage.MOVEMENT_METRICS, 0.0, 0, selectedSamples.size());
        Map<TargetReference, List<MovementMetrics>> movement = movementCalculator.calculate(selectedSamples,
                context.detectorThresholds().pauseMaximumSpeed());
        report(progress, AnalysisStage.MOVEMENT_METRICS, 1.0, selectedSamples.size(), selectedSamples.size());

        cancellation.throwIfCancelled();
        report(progress, AnalysisStage.TARGET_DISCOVERY, 0.0, 0, movement.size());
        List<RankedReplayTarget> preliminaryTargets = targetRanker.rank(selectedSamples, movement, List.of(),
                request.selectedTargets(), Math.max(context.limits().maximumTrackedEntities(),
                        request.selectedTargets().size()));
        request.selectedTargets().stream().filter(target -> !movement.containsKey(target))
                .sorted(Comparator.comparing(target -> target.uuid().toString()))
                .forEach(target -> warnings.add(AnalysisWarning.warning(
                        "analysis.selected_target_not_observed", target.uuid(), target.displayName())));
        Set<TargetReference> trackedTargets = chooseTrackedTargets(request, preliminaryTargets,
                context.limits().maximumTrackedEntities(), warnings);
        report(progress, AnalysisStage.TARGET_DISCOVERY, 1.0, trackedTargets.size(), movement.size());

        cancellation.throwIfCancelled();
        ReplaySampleWindow window = new ReplaySampleWindow(selectedSamples, movement, trackedTargets);
        List<ReplayEvent> detected = new ArrayList<>();
        Set<ReplayEventType> supported = EnumSet.noneOf(ReplayEventType.class);
        detectors.forEach(detector -> supported.addAll(detector.supportedTypes()));
        for (ReplayEventType type : request.enabledEventTypes()) {
            if (!supported.contains(type)) warnings.add(AnalysisWarning.warning("analysis.detector_unavailable", type));
        }
        int detectorIndex = 0;
        for (ReplayEventDetector detector : detectors) {
            cancellation.throwIfCancelled();
            report(progress, AnalysisStage.EVENT_DETECTION, detectorIndex / (double) detectors.size(),
                    detectorIndex, detectors.size());
            if (detector.supportedTypes().stream().anyMatch(request.enabledEventTypes()::contains)) {
                detector.detect(window, context, request.sensitivity()).stream()
                        .filter(event -> request.enabledEventTypes().contains(event.type()))
                        .filter(event -> event.peakReplayTime() >= request.startReplayTime()
                                && event.peakReplayTime() <= request.endReplayTime())
                        .forEach(detected::add);
            }
            detectorIndex++;
        }
        detected.sort(EventMerger.eventOrder());
        report(progress, AnalysisStage.EVENT_DETECTION, 1.0, detectors.size(), detectors.size());

        cancellation.throwIfCancelled();
        report(progress, AnalysisStage.EVENT_MERGING, 0.0, 0, detected.size());
        List<ReplayEvent> merged = eventMerger.mergeAndDeduplicate(detected, context.detectorThresholds(),
                context.limits().maximumEvents());
        if (merged.size() == context.limits().maximumEvents() && detected.size() > merged.size()) {
            warnings.add(AnalysisWarning.warning("analysis.event_limit_applied", detected.size(), merged.size()));
        }
        report(progress, AnalysisStage.EVENT_MERGING, 1.0, merged.size(), detected.size());

        cancellation.throwIfCancelled();
        report(progress, AnalysisStage.EVENT_SCORING, 0.0, 0, merged.size());
        EventScoringContext scoringContext = scoringContext(request, context, merged, selectedSamples);
        List<ScoredReplayEvent> rankedEvents = new ArrayList<>();
        for (int index = 0; index < merged.size(); index++) {
            cancellation.throwIfCancelled();
            rankedEvents.add(scorer.score(merged.get(index), scoringContext));
            report(progress, AnalysisStage.EVENT_SCORING, (index + 1.0) / Math.max(1, merged.size()),
                    index + 1, merged.size());
        }
        rankedEvents.sort(Comparator.comparingDouble(ScoredReplayEvent::finalScore).reversed()
                .thenComparingLong(event -> event.event().peakReplayTime())
                .thenComparing(event -> event.event().eventId().toString()));

        cancellation.throwIfCancelled();
        report(progress, AnalysisStage.SCENE_SEGMENTATION, 0.0, 0, 1);
        List<ReplayScene> scenes = sceneSegmenter.segment(request.startReplayTime(), request.endReplayTime(),
                selectedSamples, merged, context.detectorThresholds());
        report(progress, AnalysisStage.SCENE_SEGMENTATION, 1.0, scenes.size(), scenes.size());

        List<RankedReplayTarget> rankedTargets = targetRanker.rank(selectedSamples, movement, merged,
                request.selectedTargets(), context.limits().maximumTrackedEntities());
        if (merged.isEmpty()) warnings.add(AnalysisWarning.warning("analysis.no_events_detected"));
        ReplayAnalysisStatistics statistics = statistics(request, context.samples().size(), selection, movement,
                detected, merged, scenes);
        report(progress, AnalysisStage.COMPLETE, 1.0, 1, 1);
        return new ReplayAnalysisResult(request, selection, selectedSamples, movement, detected, merged,
                rankedEvents, scenes, rankedTargets, statistics, warnings);
    }

    private static ReplayAnalysisResult emptyResult(ReplayAnalysisRequest request, int inputSamples,
                                                    List<AnalysisWarning> warnings,
                                                    AnalysisProgressListener progress) {
        SampleSelection selection = new SampleSelection(List.of(), List.of(), List.of(), List.of());
        ReplayAnalysisStatistics statistics = new ReplayAnalysisStatistics(
                request.endReplayTime() - request.startReplayTime(), inputSamples, 0, 0, 0,
                0, 0, 0, 0, Map.of());
        report(progress, AnalysisStage.COMPLETE, 1.0, 1, 1);
        return new ReplayAnalysisResult(request, selection, List.of(), Map.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), statistics, warnings);
    }

    private static Set<TargetReference> chooseTrackedTargets(ReplayAnalysisRequest request,
                                                              List<RankedReplayTarget> preliminary,
                                                              int maximumTargets,
                                                              List<AnalysisWarning> warnings) {
        LinkedHashSet<TargetReference> targets = new LinkedHashSet<>();
        request.selectedTargets().stream().sorted(Comparator.comparing(target -> target.uuid().toString()))
                .limit(maximumTargets).forEach(targets::add);
        if (request.selectedTargets().size() > maximumTargets) {
            warnings.add(AnalysisWarning.warning("analysis.selected_target_limit_applied",
                    request.selectedTargets().size(), maximumTargets));
        }
        if (request.automaticTargetDetection()) {
            for (RankedReplayTarget ranked : preliminary) {
                if (targets.size() >= maximumTargets) break;
                targets.add(ranked.target());
            }
        }
        if (targets.isEmpty()) warnings.add(AnalysisWarning.warning("analysis.no_targets_selected"));
        if (preliminary.size() > maximumTargets) {
            warnings.add(AnalysisWarning.warning("analysis.target_limit_applied", preliminary.size(), maximumTargets));
        }
        return Set.copyOf(targets);
    }

    private static EventScoringContext scoringContext(ReplayAnalysisRequest request, ReplayAnalysisContext context,
                                                       List<ReplayEvent> events, List<ReplaySample> samples) {
        EnumMap<ReplayEventType, Integer> counts = new EnumMap<>(ReplayEventType.class);
        events.forEach(event -> counts.merge(event.type(), 1, Integer::sum));
        Set<Long> markerTimes = new HashSet<>();
        samples.forEach(sample -> sample.markers().forEach(marker -> markerTimes.add(marker.replayTime())));
        return new EventScoringContext(context.scoringProfile(), context.presetEventWeights(),
                request.selectedTargets(), counts, markerTimes);
    }

    private static ReplayAnalysisStatistics statistics(ReplayAnalysisRequest request, int inputCount,
                                                        SampleSelection selection,
                                                        Map<TargetReference, List<MovementMetrics>> movement,
                                                        List<ReplayEvent> detected, List<ReplayEvent> merged,
                                                        List<ReplayScene> scenes) {
        EnumMap<ReplayEventType, Integer> eventCounts = new EnumMap<>(ReplayEventType.class);
        merged.forEach(event -> eventCounts.merge(event.type(), 1, Integer::sum));
        return new ReplayAnalysisStatistics(request.endReplayTime() - request.startReplayTime(), inputCount,
                selection.coarseSamples().size(), selection.detailedSamples().size(),
                selection.combinedSamples().size(), movement.size(), detected.size(), merged.size(), scenes.size(),
                eventCounts);
    }

    private static List<ReplaySample> capSamples(List<ReplaySample> samples, int maximum) {
        if (samples.size() <= maximum) return samples;
        TreeMap<Long, ReplaySample> retained = new TreeMap<>();
        retained.put(samples.getFirst().replayTime(), samples.getFirst());
        retained.put(samples.getLast().replayTime(), samples.getLast());
        List<ReplaySample> signals = samples.stream()
                .filter(sample -> !sample.actions().isEmpty() || !sample.markers().isEmpty())
                .filter(sample -> !retained.containsKey(sample.replayTime())).toList();
        addEvenly(retained, signals, maximum);
        addEvenly(retained, samples, maximum);
        return List.copyOf(retained.values());
    }

    private static void addEvenly(TreeMap<Long, ReplaySample> retained, List<ReplaySample> candidates, int maximum) {
        int slots = maximum - retained.size();
        if (slots <= 0 || candidates.isEmpty()) return;
        List<ReplaySample> available = candidates.stream()
                .filter(sample -> !retained.containsKey(sample.replayTime())).toList();
        if (available.size() <= slots) {
            available.forEach(sample -> retained.put(sample.replayTime(), sample));
            return;
        }
        if (slots == 1) {
            ReplaySample sample = available.get(available.size() / 2);
            retained.put(sample.replayTime(), sample);
            return;
        }
        for (int index = 0; index < slots; index++) {
            int sourceIndex = Math.min(available.size() - 1,
                    (int) Math.round(index * (available.size() - 1.0) / Math.max(1, slots - 1)));
            ReplaySample sample = available.get(sourceIndex);
            retained.put(sample.replayTime(), sample);
        }
    }

    private static void report(AnalysisProgressListener listener, AnalysisStage stage, double progress,
                               int completed, int total) {
        listener.onProgress(stage, Math.max(0.0, Math.min(1.0, progress)), Math.max(0, completed), Math.max(0, total));
    }
}
