package pl.peterwolf.cinewolf.preview;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import pl.peterwolf.cinewolf.camera.AdaptiveTargetSampling;
import pl.peterwolf.cinewolf.camera.CameraPathPlanner;
import pl.peterwolf.cinewolf.camera.SampledTargetPoseResolver;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.SamplingSettings;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.TargetPose;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class PreviewController implements AutoCloseable {
    private final FlashbackReplayEditorAdapter adapter;
    private final CameraPathPlanner planner;
    private final AdaptiveTargetSampling adaptiveTargetSampling = new AdaptiveTargetSampling();
    private final CameraPathPreviewRenderer renderer;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cinewolf-path-planner");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generation = new AtomicLong();
    private SamplingJob samplingJob;
    private PreviewPathCache cache;
    private String statusKey = "cinewolf.preview.status.none";
    private List<String> statusArguments = List.of();
    private float progress;
    private boolean planning;
    private boolean editorWasOpen;

    public PreviewController(FlashbackReplayEditorAdapter adapter, CameraPathPlanner planner,
                             CameraPathPreviewRenderer renderer, Logger logger) {
        this.adapter = adapter;
        this.planner = planner;
        this.renderer = renderer;
        this.logger = logger;
    }

    public void requestPreview(ShotRequest request, SamplingSettings settings) {
        if (samplingJob != null) {
            generation.incrementAndGet();
            cancelSampling(true);
            cache = null;
            renderer.clear();
            return;
        }
        long id = generation.incrementAndGet();
        cache = null;
        renderer.clear();
        if (!adapter.isReplayEditorOpen()) {
            setStatus("cinewolf.preview.status.open_replay");
            return;
        }
        int count = Math.max(2, (int) Math.ceil(request.durationSeconds() * settings.samplesPerSecond()) + 1);
        if (count > settings.maximumSamples()) {
            setStatus("cinewolf.preview.status.sample_limit", settings.maximumSamples());
            return;
        }
        List<Long> cameraTicks = cameraTicks(request, count);
        List<Long> ticks = requiredTicks(request, cameraTicks);
        if (ticks.size() > settings.maximumSamples()) {
            setStatus("cinewolf.preview.status.lookahead_sample_limit", settings.maximumSamples());
            return;
        }
        long restoreTick = adapter.getCurrentReplayTime();
        boolean restorePaused = adapter.replayPaused();
        adapter.setReplayPaused(true);
        samplingJob = new SamplingJob(id, request, settings, cameraTicks, ticks, restoreTick, restorePaused,
                new TreeMap<>());
        progress = 0.0f;
        setStatus("cinewolf.preview.status.sampling", 0, ticks.size());
        adapter.goToReplayTick(ticks.getFirst());
    }

    public void tick() {
        boolean open = adapter.isReplayEditorOpen();
        if (editorWasOpen && !open) {
            generation.incrementAndGet();
            cancelSampling(false);
            cache = null;
            renderer.clear();
            planning = false;
        }
        editorWasOpen = open;
        SamplingJob job = samplingJob;
        if (job == null) return;
        if (!open) {
            cancelSampling(false);
            return;
        }

        if (job.restoring) {
            if (adapter.getCurrentReplayTime() == job.restoreTick && adapter.replayStateReady(job.restoreTick)) {
                job.arrivalWait++;
                if (job.arrivalWait >= 2) {
                    adapter.setReplayPaused(job.restorePaused);
                    samplingJob = null;
                    if (job.cancelAfterRestore) {
                        setStatus("cinewolf.preview.status.none");
                        progress = 0.0f;
                    } else if (job.failureKey == null) {
                        generateAsync(job);
                    } else {
                        statusKey = job.failureKey;
                        statusArguments = job.failureArguments;
                    }
                }
            } else {
                adapter.goToReplayTick(job.restoreTick);
            }
            return;
        }
        if (job.id != generation.get()) {
            cancelSampling(true);
            return;
        }

        long tick = job.ticks.get(job.index);
        if (adapter.getCurrentReplayTime() != tick || !adapter.replayStateReady(tick)) {
            job.arrivalWait = 0;
            if (adapter.getCurrentReplayTime() != tick) adapter.goToReplayTick(tick);
            return;
        }
        job.arrivalWait++;
        if (job.arrivalWait < 2) return;
        job.arrivalWait = 0;

        TargetPose pose = adapter.resolveEntity(job.request.target(), tick).orElse(null);
        if (pose == null) {
            failAndRestore(job, "cinewolf.preview.status.target_unavailable", tick);
            return;
        }
        job.poses.put(tick, pose);
        job.index++;
        progress = job.index / (float) job.ticks.size();
        setStatus("cinewolf.preview.status.sampling", job.index, job.ticks.size());
        if (job.index >= job.ticks.size()) {
            if (!job.adaptivePassComplete && startAdaptivePass(job)) return;
            job.restoring = true;
            job.arrivalWait = 0;
            adapter.goToReplayTick(job.restoreTick);
        } else {
            adapter.goToReplayTick(job.ticks.get(job.index));
        }
    }

    private void generateAsync(SamplingJob job) {
        setStatus("cinewolf.preview.status.planning");
        progress = 1.0f;
        planning = true;
        executor.submit(() -> {
            try {
                ReplayContext context = new ReplayContext(new SampledTargetPoseResolver(job.poses), job.settings,
                        job.adaptiveCameraTicks);
                CameraPathPlan generated = planner.generate(job.request, context);
                if (job.request.easing() == pl.peterwolf.cinewolf.model.EasingType.SMOOTHSTEP
                        || job.request.easing() == pl.peterwolf.cinewolf.model.EasingType.SMOOTHERSTEP) {
                    List<PathWarning> warnings = new ArrayList<>(generated.warnings());
                    warnings.add(new PathWarning(PathWarning.Severity.WARNING, "easing_baked",
                            "Flashback has no exact native mapping for this easing; CineWolf baked it into linear samples", 0.0));
                    generated = new CameraPathPlan(generated.request(), generated.samples(), generated.simplifiedSamples(),
                            warnings, generated.statistics());
                }
                CameraPathPlan result = generated;
                Minecraft.getInstance().execute(() -> acceptGenerated(job, result));
            } catch (RuntimeException exception) {
                logger.error("CineWolf path generation failed", exception);
                Minecraft.getInstance().execute(() -> {
                    if (job.id == generation.get()) {
                        planning = false;
                        setStatus("cinewolf.preview.status.generation_failed");
                    }
                });
            }
        });
    }

    private void acceptGenerated(SamplingJob job, CameraPathPlan plan) {
        if (job.id != generation.get() || !adapter.isReplayEditorOpen()) return;
        planning = false;
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) return;
        cache = new PreviewPathCache(plan, job.request, job.restoreTick, editorState, editorState.modCount);
        if (plan.valid()) {
            renderer.setPlan(plan);
            setStatus("cinewolf.preview.status.ready", plan.samples().size(), plan.simplifiedSamples().size());
            logger.debug("Generated {}: samples={}, keyframes={}, length={}, maxSpeed={}", job.request.shotType(),
                    plan.statistics().previewSamples(), plan.statistics().simplifiedKeyframes(),
                    plan.statistics().pathLength(), plan.statistics().maximumSpeed());
        } else {
            renderer.setPlan(plan);
            setStatus("cinewolf.preview.status.invalid");
        }
    }

    public boolean canGenerate(ShotRequest currentRequest) {
        EditorState state = EditorStateManager.getCurrent();
        return cache != null && cache.plan().valid() && cache.request().equals(currentRequest)
                && cache.replayTick() == adapter.getCurrentReplayTime()
                && cache.editorState() == state && state != null && cache.editorModCount() == state.modCount;
    }

    public CameraPathPlan currentPlan() {
        return cache == null ? null : cache.plan();
    }

    public boolean busy() {
        return samplingJob != null || planning;
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

    public void clear() {
        generation.incrementAndGet();
        boolean restoring = cancelSampling(true);
        cache = null;
        renderer.clear();
        planning = false;
        setStatus(restoring ? "cinewolf.preview.status.restoring" : "cinewolf.preview.status.none");
        progress = 0.0f;
    }

    private void failAndRestore(SamplingJob job, String key, Object... arguments) {
        setStatus(key, arguments);
        job.failureKey = key;
        job.failureArguments = statusArguments;
        job.restoring = true;
        job.arrivalWait = 0;
        adapter.goToReplayTick(job.restoreTick);
    }

    private boolean cancelSampling(boolean restore) {
        if (samplingJob != null && restore && adapter.isReplayEditorOpen()) {
            samplingJob.cancelAfterRestore = true;
            samplingJob.restoring = true;
            samplingJob.arrivalWait = 0;
            adapter.goToReplayTick(samplingJob.restoreTick);
            return true;
        }
        samplingJob = null;
        return false;
    }

    private boolean startAdaptivePass(SamplingJob job) {
        job.adaptivePassComplete = true;
        int remaining = job.settings.maximumSamples() - job.poses.size();
        if (remaining <= 0) return false;
        List<Long> candidates = adaptiveTargetSampling.selectAdditionalTicks(job.poses, job.baseCameraTicks, remaining);
        if (candidates.isEmpty()) return false;

        LinkedHashSet<Long> toSample = new LinkedHashSet<>();
        List<Long> acceptedCameraTicks = new ArrayList<>();
        for (long candidate : candidates) {
            List<Long> required = requiredTicks(job.request, List.of(candidate));
            long newSamples = required.stream().filter(tick -> !job.poses.containsKey(tick) && !toSample.contains(tick)).count();
            if (toSample.size() + newSamples > remaining) continue;
            acceptedCameraTicks.add(candidate);
            for (long tick : required) if (!job.poses.containsKey(tick)) toSample.add(tick);
        }
        if (toSample.isEmpty()) return false;

        job.adaptiveCameraTicks = List.copyOf(acceptedCameraTicks);
        job.ticks = toSample.stream().sorted().toList();
        job.index = 0;
        job.arrivalWait = 0;
        progress = 0.0f;
        setStatus("cinewolf.preview.status.adaptive_sampling");
        adapter.goToReplayTick(job.ticks.getFirst());
        return true;
    }

    private static List<Long> cameraTicks(ShotRequest request, int count) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            double progress = i / (double) (count - 1);
            result.add(Math.round(request.replayStartTime()
                    + (request.replayEndTime() - request.replayStartTime()) * progress));
        }
        List<Long> sorted = new ArrayList<>(result);
        sorted.sort(Long::compareTo);
        return List.copyOf(sorted);
    }

    private static List<Long> requiredTicks(ShotRequest request, List<Long> cameraTicks) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (long tick : cameraTicks) {
            result.add(tick);
            result.add(Math.min(request.replayEndTime(), tick + Math.round(request.lookAheadSeconds() * 20.0)));
        }
        List<Long> sorted = new ArrayList<>(result);
        sorted.sort(Long::compareTo);
        return List.copyOf(sorted);
    }

    private void setStatus(String key, Object... arguments) {
        statusKey = key;
        statusArguments = java.util.Arrays.stream(arguments).map(String::valueOf).toList();
    }

    @Override
    public void close() {
        generation.incrementAndGet();
        cancelSampling(false);
        planning = false;
        cache = null;
        renderer.clear();
        executor.shutdownNow();
    }

    private static final class SamplingJob {
        private final long id;
        private final ShotRequest request;
        private final SamplingSettings settings;
        private final List<Long> baseCameraTicks;
        private List<Long> adaptiveCameraTicks = List.of();
        private List<Long> ticks;
        private final long restoreTick;
        private final boolean restorePaused;
        private final Map<Long, TargetPose> poses;
        private int index;
        private int arrivalWait;
        private boolean restoring;
        private boolean cancelAfterRestore;
        private boolean adaptivePassComplete;
        private String failureKey;
        private List<String> failureArguments = List.of();

        private SamplingJob(long id, ShotRequest request, SamplingSettings settings, List<Long> baseCameraTicks,
                            List<Long> ticks,
                            long restoreTick, boolean restorePaused, Map<Long, TargetPose> poses) {
            this.id = id;
            this.request = request;
            this.settings = settings;
            this.baseCameraTicks = baseCameraTicks;
            this.ticks = ticks;
            this.restoreTick = restoreTick;
            this.restorePaused = restorePaused;
            this.poses = poses;
        }
    }
}
