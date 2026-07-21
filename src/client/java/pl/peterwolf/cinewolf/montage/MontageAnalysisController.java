package pl.peterwolf.cinewolf.montage;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import pl.peterwolf.cinewolf.config.CineWolfConfig;
import pl.peterwolf.cinewolf.config.MontageConfig;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.integration.flashback.ReplayActionCapture;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisCancelledException;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisLimits;
import pl.peterwolf.cinewolf.montage.analysis.AnalysisStage;
import pl.peterwolf.cinewolf.montage.analysis.DefaultReplayAnalyzer;
import pl.peterwolf.cinewolf.montage.analysis.ObservedReplayAction;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisRequest;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.analysis.ReplayMarkerSnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.analysis.ReplayTimeWindow;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.plan.DefaultMontagePlanner;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.MontagePlanEditor;
import pl.peterwolf.cinewolf.montage.plan.MontagePlanningContext;
import pl.peterwolf.cinewolf.montage.plan.MontageRequest;
import pl.peterwolf.cinewolf.montage.preset.MontagePreset;
import pl.peterwolf.cinewolf.montage.preset.MontagePresetRegistry;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the only seek-based montage analysis job. World/entity access stays on the client thread;
 * deterministic calculations operate on immutable samples on a worker thread.
 */
public final class MontageAnalysisController implements AutoCloseable {
    private static final int REQUIRED_STABLE_CLIENT_TICKS = 2;
    private final FlashbackReplayEditorAdapter adapter;
    private final CineWolfConfig config;
    private final Logger logger;
    private final DefaultReplayAnalyzer analyzer = DefaultReplayAnalyzer.createDefault();
    private final DefaultMontagePlanner planner = new DefaultMontagePlanner();
    private final MontagePlanEditor planEditor = new MontagePlanEditor();
    private final MontagePresetRegistry presets = MontagePresetRegistry.createDefault();
    private final MontagePlanningContext planningContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cinewolf-montage-analysis");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generations = new AtomicLong();

    private SamplingJob job;
    private volatile Phase phase = Phase.IDLE;
    private volatile AnalysisStage analysisStage = AnalysisStage.RANGE_VALIDATION;
    private volatile float progress;
    private volatile String statusKey = "cinewolf.montage.status.idle";
    private volatile List<String> statusArguments = List.of();
    private ReplayAnalysisResult analysisResult;
    private MontagePlan montagePlan;
    private boolean editorWasOpen;

    public MontageAnalysisController(FlashbackReplayEditorAdapter adapter, CineWolfConfig config, Logger logger) {
        this.adapter = Objects.requireNonNull(adapter);
        this.config = Objects.requireNonNull(config);
        this.logger = Objects.requireNonNull(logger);
        this.planningContext = new MontagePlanningContext(ShotGeneratorRegistry.createDefault().supportedTypes(),
                config.samplingSettings(), config.montage.shotDiversity);
    }

    public StartResult start(TargetReference selectedTarget) {
        if (job != null) {
            cancelActive(true, false);
            return new StartResult(false, statusKey);
        }
        if (!adapter.isReplayEditorOpen()) return reject("cinewolf.montage.error.open_replay");
        var range = adapter.getSelectedTimeRange();
        if (!range.selected()) return reject("cinewolf.montage.error.select_range");
        MontageConfig settings = config.montage;
        settings.normalize();
        MontagePreset preset = presets.get(settings.presetType).orElseThrow();
        Set<TargetReference> selectedTargets = selectedTarget == null ? Set.of() : Set.of(selectedTarget);
        if (selectedTargets.isEmpty() && !settings.automaticTargetDetection) {
            return reject("cinewolf.montage.error.select_target_or_auto");
        }

        long id = generations.incrementAndGet();
        Set<ReplayEventType> enabledTypes = enabledTypes(settings);
        ReplayAnalysisRequest request = new ReplayAnalysisRequest(range.startTick(), range.endTick(), selectedTargets,
                settings.automaticTargetDetection, enabledTypes, settings.eventSensitivity,
                settings.coarseSamplesPerSecond, settings.detailedSamplesPerSecond);
        List<ReplayMarkerSnapshot> markers = settings.includeReplayMarkers
                ? adapter.replayMarkers(range.startTick(), range.endTick()) : List.of();
        List<Long> ticks = coarseTicks(request, markers, settings.maximumTotalSamples);
        if (ticks.size() < 2) return reject("cinewolf.montage.error.sample_range_too_short");

        long restoreTick = adapter.getCurrentReplayTime();
        boolean restorePaused = adapter.replayPaused();
        ReplayActionCapture.begin(id, range.startTick(), range.endTick());
        adapter.setReplayPaused(true);
        job = new SamplingJob(id, request, preset, selectedTarget, markers, ticks, restoreTick, restorePaused);
        phase = Phase.COARSE_SAMPLING;
        analysisStage = AnalysisStage.COARSE_SAMPLING;
        progress = 0.0f;
        setStatus("cinewolf.montage.status.coarse", 0, ticks.size());
        adapter.goToReplayTick(ticks.getFirst());
        logger.info("CineWolf montage analysis started: replay={}, range={}..{}, coarseSamples={}, preset={}",
                adapter.replayIdentifier(), range.startTick(), range.endTick(), ticks.size(), preset.id());
        return new StartResult(true, statusKey);
    }

    public void tick() {
        boolean editorOpen = adapter.isReplayEditorOpen();
        if (editorWasOpen && !editorOpen) {
            cancelActive(false, false);
            clearResults();
            ReplayActionCapture.clear();
            setStatus("cinewolf.montage.status.replay_closed");
        }
        editorWasOpen = editorOpen;
        SamplingJob current = job;
        if (current == null || phase == Phase.PRELIMINARY_ANALYSIS || phase == Phase.FINAL_ANALYSIS) return;
        if (!editorOpen) {
            cancelActive(false, false);
            return;
        }
        if (phase == Phase.RESTORING) {
            tickRestore(current);
            return;
        }
        if (current.id != generations.get()) {
            cancelActive(editorOpen, false);
            return;
        }
        if (phase != Phase.COARSE_SAMPLING && phase != Phase.DETAILED_SAMPLING) return;
        tickSampling(current);
    }

    private void tickSampling(SamplingJob current) {
        if (current.index >= current.ticks.size()) {
            if (phase == Phase.COARSE_SAMPLING) startPreliminaryAnalysis(current);
            else startRestore(current, null);
            return;
        }
        long requestedTick = current.ticks.get(current.index);
        if (adapter.getCurrentReplayTime() != requestedTick || !adapter.replayStateReady(requestedTick)) {
            current.stableTicks = 0;
            if (adapter.getCurrentReplayTime() != requestedTick) adapter.goToReplayTick(requestedTick);
            return;
        }
        current.stableTicks++;
        if (current.stableTicks < REQUIRED_STABLE_CLIENT_TICKS) return;
        current.stableTicks = 0;
        ReplaySample sample;
        try {
            sample = adapter.captureReplaySample(requestedTick, current.request.selectedTargets(),
                    current.request.automaticTargetDetection(), config.montage.maximumTrackedEntities);
        } catch (RuntimeException exception) {
            logger.error("CineWolf replay sample capture failed at tick {}", requestedTick, exception);
            fail(current, "cinewolf.montage.error.analysis_failed", exception.getMessage());
            return;
        }
        current.samples.put(requestedTick, sample);
        current.index++;
        float sectionProgress = current.index / (float) current.ticks.size();
        if (phase == Phase.COARSE_SAMPLING) {
            progress = sectionProgress * 0.42f;
            setStatus("cinewolf.montage.status.coarse", current.index, current.ticks.size());
        } else {
            progress = 0.52f + sectionProgress * 0.25f;
            setStatus("cinewolf.montage.status.detailed", current.index, current.ticks.size());
        }
        if (current.index < current.ticks.size()) adapter.goToReplayTick(current.ticks.get(current.index));
        else if (phase == Phase.COARSE_SAMPLING) startPreliminaryAnalysis(current);
        else startRestore(current, null);
    }

    private void startPreliminaryAnalysis(SamplingJob current) {
        phase = Phase.PRELIMINARY_ANALYSIS;
        analysisStage = AnalysisStage.MOVEMENT_METRICS;
        progress = 0.44f;
        setStatus("cinewolf.montage.status.finding_windows");
        List<ReplaySample> immutable = withSignals(current.samples.values(), current.markers,
                ReplayActionCapture.snapshot(current.id));
        ReplayAnalysisContext context = analysisContext(immutable, current.preset);
        executor.submit(() -> {
            try {
                ReplayAnalysisResult preliminary = analyzer.analyze(current.request, context,
                        (stage, stageProgress, completed, total) -> {
                            analysisStage = stage;
                            progress = (float) (0.44 + stageProgress * 0.07);
                        }, () -> current.id != generations.get());
                Minecraft.getInstance().execute(() -> acceptPreliminary(current, preliminary));
            } catch (AnalysisCancelledException ignored) {
                // A newer generation owns the controller.
            } catch (RuntimeException exception) {
                logger.error("CineWolf preliminary montage analysis failed", exception);
                Minecraft.getInstance().execute(() -> fail(current, "cinewolf.montage.error.analysis_failed",
                        exception.getMessage()));
            }
        });
    }

    private void acceptPreliminary(SamplingJob current, ReplayAnalysisResult preliminary) {
        if (current.id != generations.get() || job != current || !adapter.isReplayEditorOpen()) return;
        List<Long> detailed = detailedTicks(preliminary.sampleSelection().detailedWindows(), current.request,
                current.samples.keySet(), config.montage.maximumTotalSamples - current.samples.size());
        if (detailed.isEmpty()) {
            startRestore(current, null);
            return;
        }
        current.ticks = detailed;
        current.index = 0;
        current.stableTicks = 0;
        phase = Phase.DETAILED_SAMPLING;
        analysisStage = AnalysisStage.DETAILED_SAMPLING;
        progress = 0.52f;
        setStatus("cinewolf.montage.status.detailed", 0, detailed.size());
        adapter.goToReplayTick(detailed.getFirst());
    }

    private void startRestore(SamplingJob current, String failureKey) {
        current.failureKey = failureKey;
        current.restoreOutcome = failureKey == null ? RestoreOutcome.CONTINUE : RestoreOutcome.FAILED;
        current.index = 0;
        current.stableTicks = 0;
        phase = Phase.RESTORING;
        setStatus("cinewolf.montage.status.restoring");
        adapter.goToReplayTick(current.restoreTick);
    }

    private void tickRestore(SamplingJob current) {
        if (adapter.getCurrentReplayTime() != current.restoreTick || !adapter.replayStateReady(current.restoreTick)) {
            current.stableTicks = 0;
            if (adapter.getCurrentReplayTime() != current.restoreTick) adapter.goToReplayTick(current.restoreTick);
            return;
        }
        current.stableTicks++;
        if (current.stableTicks < REQUIRED_STABLE_CLIENT_TICKS) return;
        adapter.setReplayPaused(current.restorePaused);
        if (current.restoreOutcome == RestoreOutcome.CANCELLED
                || current.restoreOutcome == RestoreOutcome.CLEARED) {
            job = null;
            progress = 0.0f;
            if (current.restoreOutcome == RestoreOutcome.CANCELLED) {
                phase = Phase.CANCELLED;
                setStatus("cinewolf.montage.status.cancelled");
            } else {
                phase = Phase.IDLE;
                setStatus("cinewolf.montage.status.idle");
            }
            return;
        }
        List<ObservedReplayAction> actions = ReplayActionCapture.finish(current.id);
        if (current.failureKey != null) {
            job = null;
            phase = Phase.FAILED;
            setStatus(current.failureKey, current.failureArguments.toArray());
            return;
        }
        startFinalAnalysis(current, actions);
    }

    private void startFinalAnalysis(SamplingJob current, List<ObservedReplayAction> actions) {
        phase = Phase.FINAL_ANALYSIS;
        analysisStage = AnalysisStage.EVENT_DETECTION;
        progress = 0.78f;
        setStatus("cinewolf.montage.status.analyzing_events");
        List<ReplaySample> immutable = withSignals(current.samples.values(), current.markers, actions);
        ReplayAnalysisContext context = analysisContext(immutable, current.preset);
        executor.submit(() -> {
            long started = System.nanoTime();
            try {
                ReplayAnalysisResult result = analyzer.analyze(current.request, context,
                        (stage, stageProgress, completed, total) -> {
                            analysisStage = stage;
                            progress = (float) (0.78 + stageProgress * 0.15);
                        }, () -> current.id != generations.get());
                MontageRequest planningRequest = planningRequest(current, result);
                MontagePlan plan = planner.createPlan(result, planningRequest, planningContext);
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
                Minecraft.getInstance().execute(() -> acceptFinal(current, result, plan, elapsedMillis));
            } catch (AnalysisCancelledException ignored) {
                // A newer generation owns the controller.
            } catch (RuntimeException exception) {
                logger.error("CineWolf montage analysis failed", exception);
                Minecraft.getInstance().execute(() -> fail(current, "cinewolf.montage.error.analysis_failed",
                        exception.getMessage()));
            }
        });
    }

    private void acceptFinal(SamplingJob current, ReplayAnalysisResult result, MontagePlan plan, long elapsedMillis) {
        if (current.id != generations.get() || job != current || !adapter.isReplayEditorOpen()) return;
        analysisResult = result;
        montagePlan = plan;
        job = null;
        phase = Phase.READY;
        analysisStage = AnalysisStage.COMPLETE;
        progress = 1.0f;
        setStatus("cinewolf.montage.status.ready", result.statistics().mergedEventCount(), plan.shots().size());
        logger.info("CineWolf montage analysis complete: durationMs={}, samples={}, entities={}, events={}, scenes={}, shots={}",
                elapsedMillis, result.statistics().analyzedSampleCount(), result.statistics().entityCount(),
                result.statistics().mergedEventCount(), result.statistics().sceneCount(), plan.shots().size());
        logger.info("CineWolf montage diagnostics: eventCounts={}, detectedBeforeMerge={}, mergedOrAccepted={}, "
                        + "analysisWarnings={}, planWarnings={}, topRanked={}, plannedShots={}",
                result.statistics().eventCounts(), result.statistics().detectedEventCount(),
                result.statistics().mergedEventCount(),
                result.warnings().stream().map(warning -> warning.code()).toList(),
                plan.warnings().stream().map(warning -> warning.code()).toList(),
                result.rankedEvents().stream().limit(5).map(event -> event.event().type() + "@"
                        + event.event().peakReplayTime() + "=" + event.finalScore()).toList(),
                plan.shots().stream().map(shot -> shot.order() + ":" + shot.shotType() + "@"
                        + shot.sourceReplayStartTime() + ".." + shot.sourceReplayEndTime()
                        + " reasons=" + shot.planningReasons()).toList());
    }

    public void cancel() {
        cancelActive(true, true);
    }

    public void clear() {
        boolean restoring = cancelActive(true, false);
        clearResults();
        if (!restoring) {
            phase = Phase.IDLE;
            progress = 0.0f;
            setStatus("cinewolf.montage.status.idle");
        }
    }

    public void setPlan(MontagePlan replacement) {
        montagePlan = replacement;
    }

    public void invalidatePlan() {
        montagePlan = null;
        if (analysisResult != null && !busy()) setStatus("cinewolf.montage.status.plan_invalidated");
    }

    public boolean regeneratePlan(TargetReference selectedTarget) {
        ReplayAnalysisResult result = analysisResult;
        MontagePlan previous = montagePlan;
        if (result == null) return false;
        MontagePreset preset = presets.get(config.montage.presetType).orElseThrow();
        SamplingJob synthetic = new SamplingJob(generations.get(), result.request(), preset, selectedTarget,
                List.of(), List.of(), adapter.getCurrentReplayTime(), adapter.replayPaused());
        MontagePlan regenerated = planner.createPlan(result, planningRequest(synthetic, result), planningContext);
        montagePlan = previous == null ? regenerated : planEditor.preserveLockedShots(previous, regenerated);
        phase = Phase.READY;
        setStatus("cinewolf.montage.status.replanned", montagePlan.shots().size());
        return true;
    }

    public ReplayAnalysisResult analysisResult() {
        return analysisResult;
    }

    public ReplayAnalysisRequest currentRequest() {
        SamplingJob current = job;
        return current != null ? current.request : analysisResult == null ? null : analysisResult.request();
    }

    public MontagePlan montagePlan() {
        return montagePlan;
    }

    public boolean busy() {
        return switch (phase) {
            case COARSE_SAMPLING, PRELIMINARY_ANALYSIS, DETAILED_SAMPLING, RESTORING, FINAL_ANALYSIS -> true;
            default -> false;
        };
    }

    public Phase phase() {
        return phase;
    }

    public AnalysisStage analysisStage() {
        return analysisStage;
    }

    public float progress() {
        return progress;
    }

    public String statusKey() {
        return statusKey;
    }

    public List<String> statusArguments() {
        return statusArguments;
    }

    private boolean cancelActive(boolean restoreEditor, boolean userRequested) {
        long next = generations.incrementAndGet();
        SamplingJob current = job;
        if (current != null) {
            ReplayActionCapture.cancel(current.id);
            if (restoreEditor && adapter.isReplayEditorOpen()) {
                current.restoreOutcome = userRequested ? RestoreOutcome.CANCELLED : RestoreOutcome.CLEARED;
                current.failureKey = null;
                current.failureArguments = List.of();
                current.stableTicks = 0;
                phase = Phase.RESTORING;
                progress = 0.0f;
                setStatus("cinewolf.montage.status.restoring");
                adapter.goToReplayTick(current.restoreTick);
                if (userRequested) logger.info("CineWolf montage analysis cancellation restoring generation {}", next);
                return true;
            }
        }
        job = null;
        if (userRequested) {
            phase = Phase.CANCELLED;
            progress = 0.0f;
            setStatus("cinewolf.montage.status.cancelled");
            logger.info("CineWolf montage analysis cancelled (generation {})", next);
        } else {
            phase = Phase.IDLE;
            progress = 0.0f;
        }
        return false;
    }

    private void fail(SamplingJob current, String key, Object... arguments) {
        if (current.id != generations.get() || job != current) return;
        current.failureKey = key;
        current.failureArguments = java.util.Arrays.stream(arguments).map(String::valueOf).toList();
        if (adapter.isReplayEditorOpen()) startRestore(current, key);
        else {
            ReplayActionCapture.cancel(current.id);
            job = null;
            phase = Phase.FAILED;
            setStatus(key, arguments);
        }
    }

    private StartResult reject(String key) {
        phase = Phase.FAILED;
        setStatus(key);
        return new StartResult(false, key);
    }

    private ReplayAnalysisContext analysisContext(List<ReplaySample> samples, MontagePreset preset) {
        MontageConfig settings = config.montage;
        return new ReplayAnalysisContext(samples, preset.eventWeights(), settings.detectorThresholds.toModel(),
                settings.eventScoring.toModel(), new AnalysisLimits(settings.maximumTrackedEntities,
                settings.maximumTotalSamples, settings.maximumDetectedEvents));
    }

    private MontageRequest planningRequest(SamplingJob current, ReplayAnalysisResult result) {
        MontageConfig settings = config.montage;
        return new MontageRequest(current.preset, current.request.startReplayTime(), current.request.endReplayTime(),
                settings.outputDurationSeconds, settings.aspectRatio, settings.pacing,
                java.util.Optional.ofNullable(current.selectedTarget), settings.automaticTargetDetection,
                settings.minimumShotDuration, settings.maximumShotDuration, settings.cameraMovementIntensity,
                settings.cutFrequency, settings.allowReplaySpeedChanges, settings.preferChronologicalOrder,
                settings.minimumReplaySpeed, settings.maximumReplaySpeed, settings.maximumReplaySpeedChange,
                settings.maximumPlannedShots);
    }

    private static Set<ReplayEventType> enabledTypes(MontageConfig settings) {
        EnumSet<ReplayEventType> enabled = EnumSet.allOf(ReplayEventType.class);
        if (!settings.includeReplayMarkers) enabled.remove(ReplayEventType.REPLAY_MARKER);
        if (!settings.includeCombat) enabled.removeAll(EnumSet.of(ReplayEventType.COMBAT,
                ReplayEventType.DAMAGE, ReplayEventType.DEATH));
        if (!settings.includeBuildingEvents) enabled.removeAll(EnumSet.of(ReplayEventType.BLOCK_PLACEMENT,
                ReplayEventType.BLOCK_DESTRUCTION));
        if (!settings.includeVehicles) enabled.removeAll(EnumSet.of(ReplayEventType.VEHICLE_ENTER,
                ReplayEventType.VEHICLE_EXIT, ReplayEventType.VEHICLE_MOVEMENT));
        if (!settings.includeFlight) enabled.removeAll(EnumSet.of(ReplayEventType.FLIGHT_START,
                ReplayEventType.FLIGHT, ReplayEventType.LANDING));
        return Set.copyOf(enabled);
    }

    private static List<Long> coarseTicks(ReplayAnalysisRequest request, List<ReplayMarkerSnapshot> markers,
                                          int maximumSamples) {
        long interval = Math.max(1L, Math.round(20.0 / request.coarseSamplesPerSecond()));
        LinkedHashSet<Long> ticks = new LinkedHashSet<>();
        for (long tick = request.startReplayTime(); tick <= request.endReplayTime(); tick += interval) {
            ticks.add(tick);
            if (tick > Long.MAX_VALUE - interval) break;
        }
        ticks.add(request.endReplayTime());
        markers.forEach(marker -> ticks.add(marker.replayTime()));
        return capEvenly(ticks.stream().sorted().toList(), maximumSamples);
    }

    private static List<Long> detailedTicks(List<ReplayTimeWindow> windows, ReplayAnalysisRequest request,
                                            Set<Long> alreadySampled, int maximumAdditional) {
        if (maximumAdditional <= 0 || windows.isEmpty()) return List.of();
        long interval = Math.max(1L, Math.round(20.0 / request.detailedSamplesPerSecond()));
        LinkedHashSet<Long> ticks = new LinkedHashSet<>();
        for (ReplayTimeWindow window : windows) {
            for (long tick = window.startReplayTime(); tick <= window.endReplayTime(); tick += interval) {
                if (!alreadySampled.contains(tick)) ticks.add(tick);
                if (tick > Long.MAX_VALUE - interval) break;
            }
            if (!alreadySampled.contains(window.endReplayTime())) ticks.add(window.endReplayTime());
        }
        return capEvenly(ticks.stream().sorted().toList(), maximumAdditional);
    }

    private static List<Long> capEvenly(List<Long> values, int maximum) {
        if (values.size() <= maximum) return List.copyOf(values);
        if (maximum <= 0) return List.of();
        List<Long> result = new ArrayList<>(maximum);
        for (int index = 0; index < maximum; index++) {
            int source = Math.min(values.size() - 1,
                    (int) Math.round(index * (values.size() - 1.0) / Math.max(1, maximum - 1)));
            long value = values.get(source);
            if (result.isEmpty() || result.getLast() != value) result.add(value);
        }
        return List.copyOf(result);
    }

    private static List<ReplaySample> withSignals(java.util.Collection<ReplaySample> sampled,
                                                   List<ReplayMarkerSnapshot> markers,
                                                   List<ObservedReplayAction> actions) {
        TreeMap<Long, MutableSignals> values = new TreeMap<>();
        sampled.forEach(sample -> values.put(sample.replayTime(), new MutableSignals(sample)));
        markers.forEach(marker -> values.computeIfAbsent(marker.replayTime(), MutableSignals::new).markers.add(marker));
        actions.forEach(action -> values.computeIfAbsent(action.replayTime(), MutableSignals::new).actions.add(action));
        return values.values().stream().map(MutableSignals::immutable).toList();
    }

    private void clearResults() {
        analysisResult = null;
        montagePlan = null;
    }

    private void setStatus(String key, Object... arguments) {
        statusKey = key;
        statusArguments = java.util.Arrays.stream(arguments).map(String::valueOf).toList();
    }

    @Override
    public void close() {
        cancelActive(false, false);
        ReplayActionCapture.clear();
        clearResults();
        executor.shutdownNow();
    }

    public enum Phase {
        IDLE,
        COARSE_SAMPLING,
        PRELIMINARY_ANALYSIS,
        DETAILED_SAMPLING,
        RESTORING,
        FINAL_ANALYSIS,
        READY,
        CANCELLED,
        FAILED
    }

    private enum RestoreOutcome {
        CONTINUE,
        FAILED,
        CANCELLED,
        CLEARED
    }

    public record StartResult(boolean started, String statusKey) {
    }

    private static final class SamplingJob {
        private final long id;
        private final ReplayAnalysisRequest request;
        private final MontagePreset preset;
        private final TargetReference selectedTarget;
        private final List<ReplayMarkerSnapshot> markers;
        private final long restoreTick;
        private final boolean restorePaused;
        private final TreeMap<Long, ReplaySample> samples = new TreeMap<>();
        private List<Long> ticks;
        private int index;
        private int stableTicks;
        private String failureKey;
        private List<String> failureArguments = List.of();
        private RestoreOutcome restoreOutcome = RestoreOutcome.CONTINUE;

        private SamplingJob(long id, ReplayAnalysisRequest request, MontagePreset preset,
                            TargetReference selectedTarget, List<ReplayMarkerSnapshot> markers,
                            List<Long> ticks, long restoreTick, boolean restorePaused) {
            this.id = id;
            this.request = request;
            this.preset = preset;
            this.selectedTarget = selectedTarget;
            this.markers = List.copyOf(markers);
            this.ticks = ticks;
            this.restoreTick = restoreTick;
            this.restorePaused = restorePaused;
        }
    }

    private static final class MutableSignals {
        private final long replayTime;
        private final Map<TargetReference, pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot> entities;
        private final List<ReplayMarkerSnapshot> markers = new ArrayList<>();
        private final List<ObservedReplayAction> actions = new ArrayList<>();

        private MutableSignals(long replayTime) {
            this.replayTime = replayTime;
            this.entities = Map.of();
        }

        private MutableSignals(ReplaySample sample) {
            replayTime = sample.replayTime();
            entities = new HashMap<>(sample.entities());
            markers.addAll(sample.markers());
            actions.addAll(sample.actions());
        }

        private ReplaySample immutable() {
            return new ReplaySample(replayTime, entities, markers.stream().distinct().toList(),
                    actions.stream().distinct().toList());
        }
    }
}
