package pl.peterwolf.cinewolf.montage;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import pl.peterwolf.cinewolf.api.CollisionResolver;
import pl.peterwolf.cinewolf.camera.CameraPathFinalizer;
import pl.peterwolf.cinewolf.camera.CameraPathPlanner;
import pl.peterwolf.cinewolf.camera.SampledTargetPoseResolver;
import pl.peterwolf.cinewolf.camera.VerticalFramingValidator;
import pl.peterwolf.cinewolf.config.CineWolfConfig;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackMontageTimelineWriter;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackWorldCollisionResolver;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathStatistics;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.PlannedMontageShot;
import pl.peterwolf.cinewolf.montage.timeline.MontageGeneratedShot;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWriteOptions;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWriteRequest;
import pl.peterwolf.cinewolf.preview.CameraPathPreviewRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Generates all paths before exposing one atomic Flashback write request. */
public final class MontageGenerationController implements AutoCloseable {
    private static final int REQUIRED_STABLE_CLIENT_TICKS = 2;
    private final FlashbackReplayEditorAdapter adapter;
    private final CineWolfConfig config;
    private final CameraPathPreviewRenderer renderer;
    private final Logger logger;
    private final CameraPathPlanner pathPlanner = CameraPathPlanner.createDefault();
    private final CameraPathFinalizer pathFinalizer = new CameraPathFinalizer();
    private final VerticalFramingValidator verticalFramingValidator = new VerticalFramingValidator();
    private final FlashbackWorldCollisionResolver collisionResolver = new FlashbackWorldCollisionResolver();
    private final FlashbackMontageTimelineWriter timelineWriter = new FlashbackMontageTimelineWriter();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cinewolf-montage-paths");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong generations = new AtomicLong();

    private GenerationJob job;
    private State state = State.IDLE;
    private float progress;
    private String statusKey = "cinewolf.montage.generation.idle";
    private List<String> statusArguments = List.of();
    private MontagePlan readyPlan;
    private List<GeneratedPath> generatedPaths = List.of();
    private FlashbackMontageTimelineWriter.InspectionResult lastInspection;
    private boolean editorWasOpen;

    public MontageGenerationController(FlashbackReplayEditorAdapter adapter, CineWolfConfig config,
                                       CameraPathPreviewRenderer renderer, Logger logger) {
        this.adapter = adapter;
        this.config = config;
        this.renderer = renderer;
        this.logger = logger;
    }

    public boolean generate(MontagePlan plan, ReplayAnalysisResult analysis) {
        if (cancel(true)) return false;
        if (plan == null || analysis == null || plan.enabledShots().isEmpty()) {
            fail("cinewolf.montage.error.plan_missing");
            return false;
        }
        if (plan.warnings().stream().anyMatch(warning -> warning.severity()
                == pl.peterwolf.cinewolf.montage.plan.MontageWarning.Severity.ERROR)) {
            fail("cinewolf.montage.error.plan_invalid");
            return false;
        }
        long id = generations.incrementAndGet();
        GenerationJob current = new GenerationJob(id, plan, analysis);
        job = current;
        state = State.GENERATING_PATHS;
        progress = 0.0f;
        setStatus("cinewolf.montage.generation.paths", 0, plan.enabledShots().size());
        executor.submit(() -> generatePaths(current));
        return true;
    }

    private void generatePaths(GenerationJob current) {
        try {
            List<GeneratedPath> paths = new ArrayList<>();
            int index = 0;
            for (PlannedMontageShot shot : current.plan.shots()) {
                if (current.id != generations.get()) return;
                if (!shot.enabled()) continue;
                TreeMap<Long, TargetPose> poses = new TreeMap<>();
                current.analysis.samples().forEach(sample -> {
                    var snapshot = sample.entities().get(shot.target());
                    if (snapshot != null) poses.put(sample.replayTime(), snapshot.pose());
                });
                ReplayContext context = new ReplayContext(new SampledTargetPoseResolver(poses),
                        config.samplingSettings(), adaptiveTicks(current.analysis, shot));
                var validation = pathPlanner.validate(shot.shotRequest(), context);
                if (!validation.isValid()) {
                    throw new IllegalStateException(validation.messages().stream()
                            .map(warning -> "cinewolf.path_warning." + warning.code())
                            .findFirst().orElse("cinewolf.montage.error.generated_path_invalid"));
                }
                CameraPathPlan path = pathPlanner.generate(shot.shotRequest(), context);
                if (!path.valid()) throw new IllegalStateException("cinewolf.montage.error.generated_path_invalid");
                paths.add(new GeneratedPath(shot, path));
                index++;
                int completed = index;
                Minecraft.getInstance().execute(() -> {
                    if (current.id == generations.get()) {
                        progress = completed / (float) Math.max(1, current.plan.enabledShots().size()) * 0.55f;
                        setStatus("cinewolf.montage.generation.paths", completed,
                                current.plan.enabledShots().size());
                    }
                });
            }
            List<GeneratedPath> immutable = List.copyOf(paths);
            Minecraft.getInstance().execute(() -> acceptGeneratedPaths(current, immutable));
        } catch (RuntimeException exception) {
            logger.error("CineWolf montage path generation failed", exception);
            Minecraft.getInstance().execute(() -> {
                if (current.id == generations.get()) fail("cinewolf.montage.error.path_generation_failed",
                        exception.getMessage());
            });
        }
    }

    private void acceptGeneratedPaths(GenerationJob current, List<GeneratedPath> paths) {
        if (current.id != generations.get() || job != current || !adapter.isReplayEditorOpen()) return;
        current.generated = paths;
        if (!config.montage.collisionAvoidance) {
            finish(current, paths);
            return;
        }
        current.restoreTick = adapter.getCurrentReplayTime();
        current.restorePaused = adapter.replayPaused();
        current.collisionItems = collisionItems(paths);
        if (current.collisionItems.isEmpty()) {
            finish(current, paths);
            return;
        }
        current.adjustedSamples = new ArrayList<>(paths.size());
        current.collisionStates = new ArrayList<>(paths.size());
        for (GeneratedPath path : paths) {
            current.adjustedSamples.add(new ArrayList<>(java.util.Collections.nCopies(path.path.samples().size(), null)));
            current.collisionStates.add(new FlashbackWorldCollisionResolver.TemporalState());
        }
        current.collisionIndex = 0;
        current.stableTicks = 0;
        adapter.setReplayPaused(true);
        state = State.COLLISION_SAMPLING;
        setStatus("cinewolf.montage.generation.collision", 0, current.collisionItems.size());
        adapter.goToReplayTick(current.collisionItems.getFirst().replayTick());
    }

    public void tick() {
        boolean editorOpen = adapter.isReplayEditorOpen();
        if (editorWasOpen && !editorOpen) {
            cancel(false);
            readyPlan = null;
            generatedPaths = List.of();
            lastInspection = null;
            renderer.clear();
            timelineWriter.clearUndo();
            state = State.IDLE;
        }
        editorWasOpen = editorOpen;
        GenerationJob current = job;
        if (current == null) return;
        if (!editorOpen) {
            cancel(false);
            return;
        }
        if (state == State.RESTORING) {
            tickRestore(current);
            return;
        }
        if (current.id != generations.get()) {
            cancel(false);
            return;
        }
        if (state == State.COLLISION_SAMPLING) tickCollision(current);
    }

    private void tickCollision(GenerationJob current) {
        if (current.collisionIndex >= current.collisionItems.size()) {
            startRestore(current);
            return;
        }
        CollisionItem item = current.collisionItems.get(current.collisionIndex);
        if (adapter.getCurrentReplayTime() != item.replayTick() || !adapter.replayStateReady(item.replayTick())) {
            current.stableTicks = 0;
            if (adapter.getCurrentReplayTime() != item.replayTick()) adapter.goToReplayTick(item.replayTick());
            return;
        }
        current.stableTicks++;
        if (current.stableTicks < REQUIRED_STABLE_CLIENT_TICKS) return;
        current.stableTicks = 0;
        int cursor = current.collisionIndex;
        while (cursor < current.collisionItems.size()
                && current.collisionItems.get(cursor).replayTick() == item.replayTick()) {
            CollisionItem sameTick = current.collisionItems.get(cursor);
            GeneratedPath generated = current.generated.get(sameTick.pathIndex());
            CameraSample sample = generated.path.samples().get(sameTick.sampleIndex());
            CameraPathPlan oneSample = new CameraPathPlan(generated.path.request(), List.of(sample), List.of(sample),
                    generated.path.warnings(), generated.path.statistics());
            CollisionResolver.CollisionResolutionResult resolution = collisionResolver.resolve(oneSample,
                    new CollisionResolver.CollisionContext(Minecraft.getInstance().level),
                    new CollisionResolver.CollisionSettings(0.28),
                    current.collisionStates.get(sameTick.pathIndex()));
            CameraSample adjusted = resolution.path().samples().isEmpty()
                    ? sample : resolution.path().samples().getFirst();
            current.adjustedSamples.get(sameTick.pathIndex()).set(sameTick.sampleIndex(), adjusted);
            if (resolution.changed()) current.collisionAdjusted[sameTick.pathIndex()]++;
            boolean unresolved = resolution.path().warnings().stream()
                    .anyMatch(warning -> warning.code().equals("collision_unresolved"));
            if (unresolved) {
                if (current.collisionUnresolved[sameTick.pathIndex()] == 0) {
                    logger.warn("CineWolf collision continuity fallback: path={}, sample={}, replayTick={}, "
                                    + "shot={}, position={}, focus={}, reason={}",
                            sameTick.pathIndex(), sameTick.sampleIndex(), sameTick.replayTick(),
                            generated.shot.shotRequest().shotType(), sample.position(), sample.lookAtPoint(),
                            resolution.message());
                }
                current.collisionUnresolved[sameTick.pathIndex()]++;
            }
            if (resolution.path().warnings().stream()
                    .anyMatch(warning -> warning.code().equals("collision_world_unavailable"))) {
                current.collisionFatal = true;
            }
            cursor++;
        }
        current.collisionIndex = cursor;
        progress = 0.55f + current.collisionIndex / (float) current.collisionItems.size() * 0.4f;
        setStatus("cinewolf.montage.generation.collision", current.collisionIndex,
                current.collisionItems.size());
        if (current.collisionIndex >= current.collisionItems.size()) startRestore(current);
        else adapter.goToReplayTick(current.collisionItems.get(current.collisionIndex).replayTick());
    }

    private void startRestore(GenerationJob current) {
        state = State.RESTORING;
        current.stableTicks = 0;
        setStatus("cinewolf.montage.generation.restoring");
        adapter.goToReplayTick(current.restoreTick);
    }

    private void tickRestore(GenerationJob current) {
        if (adapter.getCurrentReplayTime() != current.restoreTick || !adapter.replayStateReady(current.restoreTick)) {
            current.stableTicks = 0;
            if (adapter.getCurrentReplayTime() != current.restoreTick) adapter.goToReplayTick(current.restoreTick);
            return;
        }
        current.stableTicks++;
        if (current.stableTicks < REQUIRED_STABLE_CLIENT_TICKS) return;
        adapter.setReplayPaused(current.restorePaused);
        if (current.collisionFatal) {
            logger.error("CineWolf collision checking failed because the replay world became unavailable");
            fail("cinewolf.montage.error.collision_incomplete");
            return;
        }
        if (current.cancelAfterRestore) {
            job = null;
            state = State.IDLE;
            progress = 0.0f;
            setStatus("cinewolf.montage.generation.idle");
            return;
        }
        List<GeneratedPath> adjusted = new ArrayList<>();
        for (int pathIndex = 0; pathIndex < current.generated.size(); pathIndex++) {
            GeneratedPath original = current.generated.get(pathIndex);
            List<CameraSample> samples = current.adjustedSamples.get(pathIndex);
            if (samples.stream().anyMatch(java.util.Objects::isNull)) {
                fail("cinewolf.montage.error.collision_incomplete");
                return;
            }
            CameraPathPlan raw = new CameraPathPlan(original.path.request(), samples, samples,
                    collisionWarnings(original.path.warnings(), current.collisionAdjusted[pathIndex],
                            current.collisionUnresolved[pathIndex]), original.path.statistics());
            adjusted.add(new GeneratedPath(original.shot, pathFinalizer.finalizePath(raw, config.samplingSettings())));
        }
        if (java.util.Arrays.stream(current.collisionUnresolved).sum() > 0) {
            logger.warn("CineWolf collision pass completed with continuity fallbacks by shot: {}",
                    java.util.Arrays.toString(current.collisionUnresolved));
        }
        finish(current, List.copyOf(adjusted));
    }

    private void finish(GenerationJob current, List<GeneratedPath> paths) {
        if (current.id != generations.get()) return;
        paths = applyVerticalFramingWarnings(current, paths);
        readyPlan = current.plan;
        generatedPaths = paths;
        renderer.setPlans(paths.stream().map(GeneratedPath::path).toList());
        job = null;
        state = State.READY;
        progress = 1.0f;
        int keyframes = paths.stream().mapToInt(path -> path.path.simplifiedSamples().size()).sum();
        setStatus("cinewolf.montage.generation.ready", paths.size(), keyframes);
        logger.info("CineWolf montage paths ready: shots={}, simplifiedCameraKeys={}, warnings={}", paths.size(),
                keyframes, paths.stream().flatMap(path -> path.path.warnings().stream())
                        .map(PathWarning::code).toList());
    }

    private List<GeneratedPath> applyVerticalFramingWarnings(GenerationJob current, List<GeneratedPath> paths) {
        if (!config.montage.aspectRatio.vertical()) return paths;
        List<GeneratedPath> validated = new ArrayList<>(paths.size());
        for (GeneratedPath generated : paths) {
            TreeMap<Long, TargetPose> poses = new TreeMap<>();
            current.analysis.samples().forEach(sample -> {
                var snapshot = sample.entities().get(generated.shot.target());
                if (snapshot != null) poses.put(sample.replayTime(), snapshot.pose());
            });
            VerticalFramingValidator.Result result = verticalFramingValidator.validate(generated.path.samples(),
                    new SampledTargetPoseResolver(poses), generated.shot.target(), 9.0 / 16.0,
                    config.montage.verticalSafeArea);
            List<PathWarning> warnings = new ArrayList<>(generated.path.warnings());
            if (result.hasRisk()) {
                warnings.add(new PathWarning(PathWarning.Severity.WARNING, "vertical_framing_risk",
                        "Target bounds leave the vertical safe area in " + result.outsideSamples()
                                + " camera samples", 0.0));
            }
            if (result.incomplete()) {
                warnings.add(new PathWarning(PathWarning.Severity.WARNING, "vertical_framing_unverified",
                        "Vertical framing could not be verified in " + result.unavailableSamples()
                                + " camera samples", 0.0));
            }
            CameraPathPlan path = warnings.equals(generated.path.warnings()) ? generated.path
                    : new CameraPathPlan(generated.path.request(), generated.path.samples(),
                    generated.path.simplifiedSamples(), warnings, generated.path.statistics());
            validated.add(new GeneratedPath(generated.shot, path));
        }
        return List.copyOf(validated);
    }

    public FlashbackMontageTimelineWriter.InspectionResult inspect(long absoluteOutputStartTick,
                                                                   MontageTimelineWriteOptions options) {
        MontageTimelineWriteRequest request = writeRequest(absoluteOutputStartTick);
        if (request == null) return null;
        lastInspection = timelineWriter.inspect(request, options);
        return lastInspection;
    }

    public FlashbackMontageTimelineWriter.WriteResult write(long absoluteOutputStartTick,
                                                             MontageTimelineWriteOptions options) {
        MontageTimelineWriteRequest request = writeRequest(absoluteOutputStartTick);
        if (request == null) return null;
        FlashbackMontageTimelineWriter.WriteResult result = timelineWriter.write(request, options);
        if (result.success()) setStatus("cinewolf.montage.generation.written", result.cameraKeyframes(),
                result.fovKeyframes(), result.timelapseKeyframes());
        else setStatus("cinewolf.montage.error.timeline_write_failed");
        logger.info("CineWolf montage timeline write: success={}, montage={}, cameraKeys={}, fovKeys={}, "
                        + "timelapseKeys={}, conflictKeys={}, conflictSegments={}, errors={}, warnings={}",
                result.success(), result.montageId(), result.cameraKeyframes(), result.fovKeyframes(),
                result.timelapseKeyframes(), result.conflicts().keyframeCount(),
                result.conflicts().activeSegmentCount(), result.errors(), result.warnings());
        return result;
    }

    public FlashbackMontageTimelineWriter.UndoResult undoLast() {
        FlashbackMontageTimelineWriter.UndoResult result = timelineWriter.undoLast();
        setStatus(result.success() ? "cinewolf.montage.undo.success" : "cinewolf.montage.undo.unavailable");
        return result;
    }

    private MontageTimelineWriteRequest writeRequest(long absoluteOutputStartTick) {
        if (state != State.READY || readyPlan == null || generatedPaths.isEmpty()) return null;
        Map<UUID, CameraPathPlan> byId = new LinkedHashMap<>();
        generatedPaths.forEach(path -> byId.put(path.shot.shotId(), path.path));
        List<MontageGeneratedShot> shots = readyPlan.shots().stream().filter(PlannedMontageShot::enabled)
                .filter(shot -> byId.containsKey(shot.shotId()))
                .map(shot -> new MontageGeneratedShot(shot.outputStartSeconds(), byId.get(shot.shotId()))).toList();
        return new MontageTimelineWriteRequest(readyPlan.montageId(), absoluteOutputStartTick, shots,
                readyPlan.timeMappings(), config.montage.maximumMontageKeyframes);
    }

    public boolean cancel(boolean restore) {
        generations.incrementAndGet();
        GenerationJob current = job;
        if (restore && current != null && current.restoreTick >= 0 && adapter.isReplayEditorOpen()) {
            current.cancelAfterRestore = true;
            current.stableTicks = 0;
            state = State.RESTORING;
            setStatus("cinewolf.montage.generation.restoring");
            adapter.goToReplayTick(current.restoreTick);
            return true;
        }
        job = null;
        if (state != State.READY) state = State.IDLE;
        return false;
    }

    public void clear() {
        boolean restoring = cancel(true);
        readyPlan = null;
        generatedPaths = List.of();
        lastInspection = null;
        renderer.clear();
        progress = 0.0f;
        if (!restoring) {
            state = State.IDLE;
            setStatus("cinewolf.montage.generation.idle");
        }
    }

    public boolean busy() {
        return state == State.GENERATING_PATHS || state == State.COLLISION_SAMPLING || state == State.RESTORING;
    }

    public boolean ready() {
        return state == State.READY;
    }

    public boolean readyFor(MontagePlan plan) {
        return state == State.READY && plan != null && plan.equals(readyPlan) && !generatedPaths.isEmpty();
    }

    public boolean readyFor(MontagePlan plan, long generationId) {
        return generations.get() == generationId && readyFor(plan);
    }

    public long generationId() {
        return generations.get();
    }

    public boolean processing(MontagePlan plan) {
        return (state == State.GENERATING_PATHS || state == State.COLLISION_SAMPLING)
                && plan != null && job != null && plan.equals(job.plan);
    }

    public State state() {
        return state;
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

    public List<GeneratedPath> generatedPaths() {
        return generatedPaths;
    }

    public FlashbackMontageTimelineWriter.InspectionResult lastInspection() {
        return lastInspection;
    }

    private void fail(String key, Object... arguments) {
        job = null;
        state = State.FAILED;
        progress = 0.0f;
        setStatus(key, arguments);
    }

    private void setStatus(String key, Object... arguments) {
        statusKey = key;
        statusArguments = java.util.Arrays.stream(arguments).map(String::valueOf).toList();
    }

    private static List<Long> adaptiveTicks(ReplayAnalysisResult analysis, PlannedMontageShot shot) {
        return analysis.rankedEvents().stream()
                .map(value -> value.event().peakReplayTime())
                .filter(tick -> tick >= shot.sourceReplayStartTime() && tick <= shot.sourceReplayEndTime())
                .distinct().sorted().toList();
    }

    private static List<CollisionItem> collisionItems(List<GeneratedPath> paths) {
        List<CollisionItem> result = new ArrayList<>();
        for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
            List<CameraSample> samples = paths.get(pathIndex).path.samples();
            for (int sampleIndex = 0; sampleIndex < samples.size(); sampleIndex++) {
                result.add(new CollisionItem(samples.get(sampleIndex).replayTime(), pathIndex, sampleIndex));
            }
        }
        result.sort(Comparator.comparingLong(CollisionItem::replayTick)
                .thenComparingInt(CollisionItem::pathIndex).thenComparingInt(CollisionItem::sampleIndex));
        return List.copyOf(result);
    }

    private static List<PathWarning> collisionWarnings(List<PathWarning> original, int adjusted, int unresolved) {
        List<PathWarning> warnings = new ArrayList<>(original);
        if (adjusted > 0) warnings.add(new PathWarning(PathWarning.Severity.INFO, "collision_adjusted",
                "Collision avoidance moved " + adjusted + " camera samples", 0.0));
        if (unresolved > 0) warnings.add(new PathWarning(PathWarning.Severity.WARNING, "collision_unresolved",
                "Collision avoidance could not resolve " + unresolved + " camera samples", 0.0));
        return List.copyOf(warnings);
    }

    @Override
    public void close() {
        cancel(false);
        readyPlan = null;
        generatedPaths = List.of();
        lastInspection = null;
        renderer.clear();
        executor.shutdownNow();
    }

    public enum State { IDLE, GENERATING_PATHS, COLLISION_SAMPLING, RESTORING, READY, FAILED }

    public record GeneratedPath(PlannedMontageShot shot, CameraPathPlan path) {
    }

    private record CollisionItem(long replayTick, int pathIndex, int sampleIndex) {
    }

    private static final class GenerationJob {
        private final long id;
        private final MontagePlan plan;
        private final ReplayAnalysisResult analysis;
        private List<GeneratedPath> generated = List.of();
        private List<CollisionItem> collisionItems = List.of();
        private List<List<CameraSample>> adjustedSamples = List.of();
        private List<FlashbackWorldCollisionResolver.TemporalState> collisionStates = List.of();
        private int collisionIndex;
        private int[] collisionAdjusted;
        private int[] collisionUnresolved;
        private int stableTicks;
        private long restoreTick = -1;
        private boolean restorePaused;
        private boolean cancelAfterRestore;
        private boolean collisionFatal;

        private GenerationJob(long id, MontagePlan plan, ReplayAnalysisResult analysis) {
            this.id = id;
            this.plan = plan;
            this.analysis = analysis;
            this.collisionAdjusted = new int[plan.enabledShots().size()];
            this.collisionUnresolved = new int[plan.enabledShots().size()];
        }
    }
}
