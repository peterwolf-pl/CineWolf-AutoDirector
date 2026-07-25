package pl.peterwolf.cinewolf.montage;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import pl.peterwolf.cinewolf.api.CollisionResolver;
import pl.peterwolf.cinewolf.camera.CameraPathFinalizer;
import pl.peterwolf.cinewolf.camera.CameraPathPlanner;
import pl.peterwolf.cinewolf.camera.MultiSampledTargetPoseResolver;
import pl.peterwolf.cinewolf.camera.SampledTargetPoseResolver;
import pl.peterwolf.cinewolf.camera.VerticalFramingCorrector;
import pl.peterwolf.cinewolf.camera.VerticalFramingValidator;
import pl.peterwolf.cinewolf.config.CineWolfConfig;
import pl.peterwolf.cinewolf.clip.OcclusionClipController;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackExportAspectSync;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackMontageTimelineWriter;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackReplayEditorAdapter;
import pl.peterwolf.cinewolf.integration.flashback.FlashbackWorldCollisionResolver;
import pl.peterwolf.cinewolf.CineWolfAutoDirectorClient;
import pl.peterwolf.cinewolf.montage.analysis.IndoorSceneHeuristics;
import pl.peterwolf.cinewolf.montage.preset.VerticalComposition;
import net.minecraft.client.multiplayer.ClientLevel;
import pl.peterwolf.cinewolf.model.EasingType;
import pl.peterwolf.cinewolf.model.RotationDirection;
import pl.peterwolf.cinewolf.model.ShotRequest;
import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.CameraSample;
import pl.peterwolf.cinewolf.model.PathStatistics;
import pl.peterwolf.cinewolf.model.PathWarning;
import pl.peterwolf.cinewolf.model.ReplayContext;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.plan.PlannedMontageShot;
import pl.peterwolf.cinewolf.shot.ShotGeneratorRegistry;
import pl.peterwolf.cinewolf.montage.timeline.MontageGeneratedShot;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWriteOptions;
import pl.peterwolf.cinewolf.montage.timeline.MontageTimelineWriteRequest;
import pl.peterwolf.cinewolf.preview.CameraPathPreviewRenderer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Generates all paths before exposing one atomic Flashback write request. */
public final class MontageGenerationController implements AutoCloseable {
    private static final int REQUIRED_STABLE_CLIENT_TICKS = 2;
    /** Fraction of samples with blocked LOS (or unresolved collision) that triggers a shot-type retry. */
    private static final double OCCLUSION_RETRY_FRACTION = 0.28;
    /** How many full alternate-type rounds are allowed per montage generation. */
    private static final int MAX_OCCLUSION_ROUNDS = 1;
    private static final int MAX_ALTERNATES_PER_SHOT = 3;
    private final FlashbackReplayEditorAdapter adapter;
    private final CineWolfConfig config;
    private final CameraPathPreviewRenderer renderer;
    private final Logger logger;
    private final CameraPathPlanner pathPlanner = CameraPathPlanner.createDefault();
    private final CameraPathFinalizer pathFinalizer = new CameraPathFinalizer();
    private final VerticalFramingValidator verticalFramingValidator = new VerticalFramingValidator();
    private final VerticalFramingCorrector verticalFramingCorrector = new VerticalFramingCorrector();
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
                ShotRequest request = applyGlobalThirdPersonHeight(shot.shotRequest());
                java.util.Map<UUID, java.util.Map<Long, TargetPose>> posesByTarget = new java.util.HashMap<>();
                java.util.Map<Long, TargetPose> subjectPoses = new TreeMap<>();
                current.analysis.samples().forEach(sample -> {
                    TargetPose pose = poseForUuid(sample, shot.target().uuid());
                    if (pose != null) subjectPoses.put(sample.replayTime(), pose);
                });
                posesByTarget.put(shot.target().uuid(), subjectPoses);
                ReplayContext context = new ReplayContext(new MultiSampledTargetPoseResolver(posesByTarget),
                        config.samplingSettings(), adaptiveTicks(current.analysis, shot));
                var validation = pathPlanner.validate(request, context);
                if (!validation.isValid()) {
                    throw new IllegalStateException(validation.messages().stream()
                            .map(warning -> "cinewolf.path_warning." + warning.code())
                            .findFirst().orElse("cinewolf.montage.error.generated_path_invalid"));
                }
                CameraPathPlan path = pathPlanner.generate(request, context);
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
        boolean avoid = config.montage.obstacleHandling().adjustsCameraPath();
        boolean indoor = paths.stream().anyMatch(path -> path.shot.shotType() == ShotType.ROOM_CORNER
                || IndoorSceneHeuristics.isLikelyIndoor(current.analysis, path.shot.target(),
                path.shot.sourceReplayStartTime(), path.shot.sourceReplayEndTime()));
        // Indoor + AVOID → skip thrashing probes (ceiling only) and enable CLIP-style occlusion instead.
        current.indoorClipOverride = indoor && avoid;
        current.ceilingOnly = !avoid || current.indoorClipOverride;
        if (current.indoorClipOverride) {
            OcclusionClipController.get().setIndoorClipOverride(true);
            logger.info("CineWolf indoor scene: relaxing AVOID to ceiling+CLIP for {} shot(s)", paths.size());
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
        setStatus(current.ceilingOnly
                        ? "cinewolf.montage.generation.ceiling"
                        : "cinewolf.montage.generation.collision",
                0, current.collisionItems.size());
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
                    current.collisionStates.get(sameTick.pathIndex()),
                    current.ceilingOnly);
            CameraSample adjusted = resolution.path().samples().isEmpty()
                    ? sample : resolution.path().samples().getFirst();
            current.adjustedSamples.get(sameTick.pathIndex()).set(sameTick.sampleIndex(), adjusted);
            if (resolution.changed()) current.collisionAdjusted[sameTick.pathIndex()]++;
            boolean unresolved = resolution.path().warnings().stream()
                    .anyMatch(warning -> warning.code().equals("collision_unresolved"));
            if (unresolved) {
                String reasonDetail = resolution.path().warnings().stream()
                        .filter(warning -> warning.code().equals("collision_unresolved"))
                        .map(PathWarning::message)
                        .findFirst().orElse(resolution.message());
                if (current.collisionUnresolved[sameTick.pathIndex()] == 0) {
                    logger.warn("CineWolf collision continuity fallback: path={}, sample={}, replayTick={}, "
                                    + "shot={}, position={}, focus={}, reason={}",
                            sameTick.pathIndex(), sameTick.sampleIndex(), sameTick.replayTick(),
                            generated.shot.shotRequest().shotType(), sample.position(), sample.lookAtPoint(),
                            reasonDetail);
                }
                current.collisionUnresolved[sameTick.pathIndex()]++;
                current.collisionReasonSummaries.computeIfAbsent(sameTick.pathIndex(), key -> new ArrayList<>())
                        .add(reasonDetail);
            }
            if (resolution.path().warnings().stream()
                    .anyMatch(warning -> warning.code().equals("collision_world_unavailable"))) {
                current.collisionFatal = true;
            }
            cursor++;
        }
        current.collisionIndex = cursor;
        progress = 0.55f + current.collisionIndex / (float) current.collisionItems.size() * 0.4f;
        setStatus(current.ceilingOnly
                        ? "cinewolf.montage.generation.ceiling"
                        : "cinewolf.montage.generation.collision",
                current.collisionIndex, current.collisionItems.size());
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
            String reasonSummary = summarizeReasons(current.collisionReasonSummaries.get(pathIndex));
            CameraPathPlan raw = new CameraPathPlan(original.path.request(), samples, samples,
                    collisionWarnings(original.path.warnings(), current.collisionAdjusted[pathIndex],
                            current.collisionUnresolved[pathIndex], reasonSummary, current.indoorClipOverride),
                    original.path.statistics());
            adjusted.add(new GeneratedPath(original.shot, pathFinalizer.finalizePath(raw, config.samplingSettings())));
        }
        if (java.util.Arrays.stream(current.collisionUnresolved).sum() > 0) {
            logger.warn("CineWolf collision pass completed with continuity fallbacks by shot: {}",
                    java.util.Arrays.toString(current.collisionUnresolved));
        }

        // If blocks still sit between camera and subject, try a different shot generator once.
        if (current.occlusionRound < MAX_OCCLUSION_ROUNDS) {
            List<Integer> occluded = findOccludedPathIndexes(current, adjusted);
            if (!occluded.isEmpty()) {
                current.occlusionRound++;
                logger.info("CineWolf occlusion: retrying {} shot(s) with alternate generators (round {})",
                        occluded.size(), current.occlusionRound);
                setStatus("cinewolf.montage.generation.occlusion_retry", occluded.size());
                List<GeneratedPath> snapshot = List.copyOf(adjusted);
                List<Integer> retryIndexes = List.copyOf(occluded);
                state = State.GENERATING_PATHS;
                progress = 0.52f;
                executor.submit(() -> retryOccludedPaths(current, snapshot, retryIndexes));
                return;
            }
        }

        finish(current, List.copyOf(adjusted));
    }

    /**
     * Paths where LOS is blocked on many samples (or collision stayed unresolved) should try another framing.
     */
    private List<Integer> findOccludedPathIndexes(GenerationJob current, List<GeneratedPath> adjusted) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        List<Integer> occluded = new ArrayList<>();
        for (int pathIndex = 0; pathIndex < adjusted.size(); pathIndex++) {
            GeneratedPath path = adjusted.get(pathIndex);
            List<CameraSample> samples = path.path.samples();
            if (samples.isEmpty()) continue;
            int blocked = 0;
            int probes = 0;
            // Sample up to ~12 points along the path for LOS.
            int step = Math.max(1, samples.size() / 12);
            for (int i = 0; i < samples.size(); i += step) {
                CameraSample sample = samples.get(i);
                if (sample == null || sample.lookAtPoint() == null) continue;
                probes++;
                if (!FlashbackWorldCollisionResolver.lineOfSightClear(level, sample.position(),
                        sample.lookAtPoint())) {
                    blocked++;
                }
            }
            double blockedFraction = probes == 0 ? 0.0 : blocked / (double) probes;
            double unresolvedFraction = samples.isEmpty() ? 0.0
                    : current.collisionUnresolved[pathIndex] / (double) samples.size();
            if (blockedFraction >= OCCLUSION_RETRY_FRACTION || unresolvedFraction >= OCCLUSION_RETRY_FRACTION) {
                occluded.add(pathIndex);
                logger.info("CineWolf occlusion candidate: shot={}, type={}, blocked={}/{} ({}), unresolved={}",
                        pathIndex, path.shot.shotType(), blocked, probes,
                        String.format(java.util.Locale.ROOT, "%.0f%%", blockedFraction * 100.0),
                        current.collisionUnresolved[pathIndex]);
            }
        }
        return occluded;
    }

    private void retryOccludedPaths(GenerationJob current, List<GeneratedPath> adjusted,
                                    List<Integer> occludedIndexes) {
        try {
            if (current.id != generations.get()) return;
            List<GeneratedPath> next = new ArrayList<>(adjusted);
            for (int pathIndex : occludedIndexes) {
                if (current.id != generations.get()) return;
                GeneratedPath original = next.get(pathIndex);
                ShotType failedType = original.shot.shotType();
                current.triedShotTypes
                        .computeIfAbsent(pathIndex, key -> new LinkedHashSet<>())
                        .add(failedType);
                boolean indoor = IndoorSceneHeuristics.isLikelyIndoor(current.analysis, original.shot.target(),
                        original.shot.sourceReplayStartTime(), original.shot.sourceReplayEndTime());
                List<ShotType> alternates = occlusionAlternateTypes(failedType, indoor,
                        current.triedShotTypes.get(pathIndex));
                GeneratedPath best = original;
                for (ShotType alternate : alternates) {
                    if (current.triedShotTypes.get(pathIndex).contains(alternate)) continue;
                    current.triedShotTypes.get(pathIndex).add(alternate);
                    GeneratedPath candidate = regenerateWithType(current, original, alternate);
                    if (candidate == null) continue;
                    best = candidate;
                    logger.info("CineWolf occlusion: path {} {} → {} (retry)",
                            pathIndex, failedType, alternate);
                    break;
                }
                if (best != original) {
                    List<PathWarning> warnings = new ArrayList<>(best.path.warnings());
                    warnings.add(new PathWarning(PathWarning.Severity.INFO, "occlusion_shot_fallback",
                            failedType.name() + "→" + best.shot.shotType().name(), 0.0));
                    CameraPathPlan annotated = new CameraPathPlan(best.path.request(), best.path.samples(),
                            best.path.simplifiedSamples(), warnings, best.path.statistics());
                    next.set(pathIndex, new GeneratedPath(best.shot, annotated));
                }
            }
            List<GeneratedPath> immutable = List.copyOf(next);
            Minecraft.getInstance().execute(() -> {
                if (current.id != generations.get() || job != current) return;
                // Re-run world collision only for the new paths.
                current.generated = immutable;
                current.collisionItems = collisionItems(immutable);
                current.adjustedSamples = new ArrayList<>(immutable.size());
                current.collisionStates = new ArrayList<>(immutable.size());
                current.collisionAdjusted = new int[immutable.size()];
                current.collisionUnresolved = new int[immutable.size()];
                current.collisionReasonSummaries.clear();
                for (GeneratedPath path : immutable) {
                    current.adjustedSamples.add(new ArrayList<>(
                            java.util.Collections.nCopies(path.path.samples().size(), null)));
                    current.collisionStates.add(new FlashbackWorldCollisionResolver.TemporalState());
                }
                current.collisionIndex = 0;
                current.stableTicks = 0;
                if (current.collisionItems.isEmpty()) {
                    finish(current, immutable);
                    return;
                }
                state = State.COLLISION_SAMPLING;
                setStatus(current.ceilingOnly
                                ? "cinewolf.montage.generation.ceiling"
                                : "cinewolf.montage.generation.collision",
                        0, current.collisionItems.size());
                adapter.setReplayPaused(true);
                adapter.goToReplayTick(current.collisionItems.getFirst().replayTick());
            });
        } catch (RuntimeException exception) {
            logger.error("CineWolf occlusion retry failed", exception);
            Minecraft.getInstance().execute(() -> {
                if (current.id == generations.get()) {
                    // Keep the best paths we already have rather than failing the whole montage.
                    finish(current, adjusted);
                }
            });
        }
    }

    private GeneratedPath regenerateWithType(GenerationJob current, GeneratedPath original, ShotType type) {
        try {
            ShotRequest request = requestAsShotType(original.shot.shotRequest(), type);
            request = applyGlobalThirdPersonHeight(request);
            Map<UUID, Map<Long, TargetPose>> posesByTarget = new java.util.HashMap<>();
            Map<Long, TargetPose> subjectPoses = new TreeMap<>();
            current.analysis.samples().forEach(sample -> {
                TargetPose pose = poseForUuid(sample, original.shot.target().uuid());
                if (pose != null) subjectPoses.put(sample.replayTime(), pose);
            });
            posesByTarget.put(original.shot.target().uuid(), subjectPoses);
            ReplayContext context = new ReplayContext(new MultiSampledTargetPoseResolver(posesByTarget),
                    config.samplingSettings(), adaptiveTicks(current.analysis, original.shot));
            if (!pathPlanner.validate(request, context).isValid()) return null;
            CameraPathPlan path = pathPlanner.generate(request, context);
            if (!path.valid() || path.samples().size() < 2) return null;
            PlannedMontageShot shot = original.shot.withRequest(request, original.shot.framing(),
                    List.of("montage.reason.occlusion_shot_fallback;from="
                            + original.shot.shotType().name() + ";to=" + type.name()));
            return new GeneratedPath(shot, path);
        } catch (RuntimeException exception) {
            logger.debug("CineWolf occlusion alternate {} failed: {}", type, exception.toString());
            return null;
        }
    }

    private List<ShotType> occlusionAlternateTypes(ShotType failed, boolean indoor, Set<ShotType> alreadyTried) {
        List<ShotType> preferred = indoor
                ? List.of(ShotType.ROOM_CORNER, ShotType.STATIC_TRACKING, ShotType.FOLLOW,
                ShotType.SIDE_TRACKING, ShotType.CLOSE_DETAIL, ShotType.CHASE)
                : List.of(ShotType.SIDE_TRACKING, ShotType.FOLLOW, ShotType.STATIC_TRACKING,
                ShotType.CHASE, ShotType.CLOSE_DETAIL, ShotType.DOLLY_IN, ShotType.ROOM_CORNER);
        Set<ShotType> allowed = config.montage.shotSettings.resolvedAllowedTypes(
                ShotGeneratorRegistry.createDefault().supportedTypes());
        // When 3rd-person mode is on, still allow clear tracking fallbacks for occlusion.
        List<ShotType> result = new ArrayList<>();
        for (ShotType type : preferred) {
            if (type == failed) continue;
            if (alreadyTried != null && alreadyTried.contains(type)) continue;
            if (!allowed.contains(type) && type != ShotType.FOLLOW && type != ShotType.STATIC_TRACKING
                    && type != ShotType.ROOM_CORNER && type != ShotType.SIDE_TRACKING) {
                continue;
            }
            // Prefer generators that typically keep a cleaner subject view.
            if (type == ShotType.ORBIT || type == ShotType.SPIRAL || type == ShotType.FLYBY
                    || type == ShotType.CRANE_UP || type == ShotType.CRANE_DOWN) {
                continue;
            }
            result.add(type);
            if (result.size() >= MAX_ALTERNATES_PER_SHOT) break;
        }
        return result;
    }

    private static ShotRequest requestAsShotType(ShotRequest source, ShotType type) {
        RotationDirection direction = source.direction();
        boolean lateral = type == ShotType.FLYBY || type == ShotType.SIDE_TRACKING
                || type == ShotType.REVEAL || type == ShotType.VEHICLE_PROFILE;
        if (lateral && direction != RotationDirection.LEFT_TO_RIGHT
                && direction != RotationDirection.RIGHT_TO_LEFT) {
            direction = RotationDirection.LEFT_TO_RIGHT;
        }
        if (!lateral && direction != RotationDirection.CLOCKWISE
                && direction != RotationDirection.COUNTERCLOCKWISE) {
            direction = RotationDirection.CLOCKWISE;
        }
        double height = source.height();
        double distance = Math.max(1.0, source.distance());
        double startDistance = source.startDistance();
        double endDistance = source.endDistance();
        if (type == ShotType.ROOM_CORNER || type == ShotType.STATIC_TRACKING) {
            height = Math.min(Math.abs(height) < 1.0e-3 ? 0.35 : height, 0.6);
            distance = Math.min(distance, 6.0);
        } else if (type == ShotType.SIDE_TRACKING || type == ShotType.FOLLOW || type == ShotType.CHASE) {
            height = Math.min(Math.max(height, 0.8), 2.2);
            distance = Math.max(2.5, Math.min(distance, 10.0));
        } else if (type == ShotType.CLOSE_DETAIL) {
            distance = Math.min(distance, 3.5);
            height = Math.min(height, 1.2);
        } else if (type == ShotType.DOLLY_IN || type == ShotType.DOLLY_OUT) {
            startDistance = Math.max(distance * 1.3, distance + 1.0);
            endDistance = Math.max(1.5, distance * 0.7);
            if (type == ShotType.DOLLY_OUT) {
                double tmp = startDistance;
                startDistance = endDistance;
                endDistance = tmp;
            }
        }
        return new ShotRequest(source.target(), type, source.diameter(), height, distance,
                startDistance, endDistance, source.rpm(), source.durationSeconds(),
                source.startAngleDegrees(), direction, source.cameraSpeed(), source.fov(),
                source.easing() == null ? EasingType.LINEAR : source.easing(),
                source.lookAheadSeconds(), source.replayStartTime(), source.replayEndTime(),
                source.options());
    }

    private void finish(GenerationJob current, List<GeneratedPath> paths) {
        if (current.id != generations.get()) return;
        paths = applyVerticalFramingWarnings(current, paths);
        readyPlan = current.plan;
        generatedPaths = paths;
        // Brand bug for preview + Flashback export of this AutoDirector montage.
        CineWolfAutoDirectorClient.setExportWatermarkActive(true);
        renderer.setPlans(paths.stream().map(GeneratedPath::path).toList());
        // Keep indoor CLIP override while the generated paths are active for preview/export review.
        OcclusionClipController.get().setIndoorClipOverride(current.indoorClipOverride);
        if (current.indoorClipOverride && !paths.isEmpty()) {
            OcclusionClipController.get().setPreferredSubject(paths.getFirst().shot.target());
        }
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
        // Ensure Flashback export/preview are already in 9:16 when paths become ready.
        FlashbackExportAspectSync.apply(config.montage.aspectRatio);
        List<GeneratedPath> validated = new ArrayList<>(paths.size());
        for (GeneratedPath generated : paths) {
            // Player-level 3rd person must not be pulled back/up for 9:16 framing.
            if (generated.shot.shotType() == ShotType.THIRD_PERSON) {
                validated.add(generated);
                continue;
            }
            TreeMap<Long, TargetPose> poses = new TreeMap<>();
            current.analysis.samples().forEach(sample -> {
                var snapshot = sample.entities().get(generated.shot.target());
                if (snapshot != null) poses.put(sample.replayTime(), snapshot.pose());
            });
            SampledTargetPoseResolver resolver = new SampledTargetPoseResolver(poses);
            VerticalFramingCorrector.CorrectionResult correction = verticalFramingCorrector.correct(
                    generated.path.samples(), resolver, generated.shot.target(),
                    VerticalComposition.WIDTH_TO_HEIGHT, config.montage.verticalSafeArea);
            List<CameraSample> samples = correction.samples();
            List<PathWarning> warnings = new ArrayList<>(generated.path.warnings());
            warnings.addAll(correction.warnings());
            CameraPathPlan path;
            if (correction.adjustedSamples() > 0) {
                CameraPathPlan raw = new CameraPathPlan(generated.path.request(), samples, samples,
                        warnings, generated.path.statistics());
                path = pathFinalizer.finalizePath(raw, config.samplingSettings());
            } else if (!warnings.equals(generated.path.warnings())) {
                path = new CameraPathPlan(generated.path.request(), generated.path.samples(),
                        generated.path.simplifiedSamples(), warnings, generated.path.statistics());
            } else {
                path = generated.path;
            }
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
        if (config.montage.aspectRatio.vertical() || config.montage.aspectRatio != null) {
            FlashbackExportAspectSync.apply(config.montage.aspectRatio);
        }
        FlashbackMontageTimelineWriter.WriteResult result = timelineWriter.write(request, options);
        if (result.success()) {
            FlashbackExportAspectSync.apply(config.montage.aspectRatio);
            setStatus("cinewolf.montage.generation.written", result.cameraKeyframes(),
                    result.fovKeyframes(), result.timelapseKeyframes());
        } else {
            setStatus("cinewolf.montage.error.timeline_write_failed");
        }
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
        OcclusionClipController.get().setIndoorClipOverride(false);
        CineWolfAutoDirectorClient.setExportWatermarkActive(false);
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
        OcclusionClipController.get().setIndoorClipOverride(false);
        job = null;
        state = State.FAILED;
        progress = 0.0f;
        setStatus(key, arguments);
    }

    private void setStatus(String key, Object... arguments) {
        statusKey = key;
        statusArguments = java.util.Arrays.stream(arguments).map(String::valueOf).toList();
    }

    /**
     * Global montage.thirdPersonHeight always wins for THIRD_PERSON so the user does not need to
     * retune every shot by hand — one config value drives path generation.
     */
    private ShotRequest applyGlobalThirdPersonHeight(ShotRequest request) {
        if (request == null || request.shotType() != ShotType.THIRD_PERSON) return request;
        double height = config.montage.thirdPersonHeight;
        if (!Double.isFinite(height)) height = 0.0;
        height = Math.max(-2.25, Math.min(2.25, height));
        if (Math.abs(request.height() - height) < 1.0e-9) return request;
        return new ShotRequest(request.target(), request.shotType(), request.diameter(), height,
                request.distance(), request.startDistance(), request.endDistance(), request.rpm(),
                request.durationSeconds(), request.startAngleDegrees(), request.direction(),
                request.cameraSpeed(), request.fov(), request.easing(), request.lookAheadSeconds(),
                request.replayStartTime(), request.replayEndTime(), request.options());
    }

    private static TargetPose poseForUuid(pl.peterwolf.cinewolf.montage.analysis.ReplaySample sample, UUID uuid) {
        for (var entry : sample.entities().entrySet()) {
            if (entry.getKey().uuid().equals(uuid) && entry.getValue() != null
                    && entry.getValue().pose() != null) {
                return entry.getValue().pose();
            }
        }
        return null;
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

    private static List<PathWarning> collisionWarnings(List<PathWarning> original, int adjusted, int unresolved,
                                                       String reasonSummary, boolean indoorClip) {
        List<PathWarning> warnings = new ArrayList<>(original);
        if (indoorClip) {
            warnings.add(new PathWarning(PathWarning.Severity.INFO, "indoor_clip_preferred",
                    "Indoor scene: AVOID relaxed to ceiling clearance + CLIP occlusion", 0.0));
        }
        if (adjusted > 0) warnings.add(new PathWarning(PathWarning.Severity.INFO, "collision_adjusted",
                String.valueOf(adjusted), 0.0));
        if (unresolved > 0) {
            String detail = unresolved + (reasonSummary == null || reasonSummary.isBlank()
                    ? "" : ":" + reasonSummary);
            warnings.add(new PathWarning(PathWarning.Severity.WARNING, "collision_unresolved", detail, 0.0));
        }
        return List.copyOf(warnings);
    }

    private static String summarizeReasons(List<String> rawMessages) {
        if (rawMessages == null || rawMessages.isEmpty()) return "";
        // Messages look like "6:probe_budget_exhausted=4,no_safe_candidate=2 (last=...)"
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String raw : rawMessages) {
            if (raw == null || raw.isBlank()) continue;
            String body = raw;
            int colon = raw.indexOf(':');
            if (colon >= 0 && colon + 1 < raw.length()) body = raw.substring(colon + 1);
            int paren = body.indexOf(" (last=");
            if (paren > 0) body = body.substring(0, paren);
            for (String part : body.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) continue;
                int eq = token.indexOf('=');
                String key = eq > 0 ? token.substring(0, eq).trim() : token;
                int value = 1;
                if (eq > 0) {
                    try {
                        value = Integer.parseInt(token.substring(eq + 1).trim());
                    } catch (NumberFormatException ignored) {
                        value = 1;
                    }
                }
                counts.merge(key, value, Integer::sum);
            }
        }
        if (counts.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    if (!text.isEmpty()) text.append(", ");
                    text.append(entry.getKey()).append('=').append(entry.getValue());
                });
        return text.toString();
    }

    @Override
    public void close() {
        cancel(false);
        readyPlan = null;
        generatedPaths = List.of();
        lastInspection = null;
        renderer.clear();
        OcclusionClipController.get().setIndoorClipOverride(false);
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
        /** When true, world pass only pulls cameras under ceilings (no lateral AVOID probes). */
        private boolean ceilingOnly;
        /** Indoor + user AVOID → ceiling path + runtime CLIP occlusion. */
        private boolean indoorClipOverride;
        private final java.util.Map<Integer, List<String>> collisionReasonSummaries = new java.util.HashMap<>();
        /** How many occlusion-driven generator retries have been attempted. */
        private int occlusionRound;
        /** Shot types already tried per path index (original + fallbacks). */
        private final Map<Integer, Set<ShotType>> triedShotTypes = new java.util.HashMap<>();

        private GenerationJob(long id, MontagePlan plan, ReplayAnalysisResult analysis) {
            this.id = id;
            this.plan = plan;
            this.analysis = analysis;
            this.collisionAdjusted = new int[plan.enabledShots().size()];
            this.collisionUnresolved = new int[plan.enabledShots().size()];
        }
    }
}
