package pl.peterwolf.cinewolf.integration.flashback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.editor.ui.ReplayUI;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.slf4j.Logger;
import pl.peterwolf.cinewolf.api.ReplayEditorAdapter;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.mixin.flashback.ReplayServerAccessor;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplayMarkerSnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FlashbackReplayEditorAdapter implements ReplayEditorAdapter {
    private final Logger logger;
    private final FlashbackEntityResolver entityResolver = new FlashbackEntityResolver();
    private final FlashbackKeyframeWriter keyframeWriter = new FlashbackKeyframeWriter();

    public FlashbackReplayEditorAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return FlashbackCompatibility.isSupportedRuntime();
    }

    @Override
    public boolean isReplayEditorOpen() {
        return isAvailable() && Flashback.isInReplay() && ReplayUI.isActive() && EditorStateManager.getCurrent() != null;
    }

    @Override
    public ReplayTimeRange getSelectedTimeRange() {
        EditorState state = EditorStateManager.getCurrent();
        if (state == null) return new ReplayTimeRange(-1, -1, false);
        EditorState.StartAndEnd range = state.getExportStartAndEnd();
        return new ReplayTimeRange(range.start(), range.end(), range.start() >= 0 && range.end() > range.start());
    }

    @Override
    public long getCurrentReplayTime() {
        ReplayServer server = Flashback.getReplayServer();
        return server == null ? -1 : server.getReplayTick();
    }

    @Override
    public long getTotalReplayTime() {
        ReplayServer server = Flashback.getReplayServer();
        return server == null ? -1 : server.getTotalReplayTicks();
    }

    @Override
    public List<ReplayEntityDescriptor> listEntities(long replayTime) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        boolean atRequestedTime = replayTime == getCurrentReplayTime();
        UUID viewer = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        List<Entity> entities = new ArrayList<>();
        level.entitiesForRendering().forEach(entity -> {
            if (!entity.getUUID().equals(viewer)) entities.add(entity);
        });
        entities.sort(Comparator.<Entity, Boolean>comparing(entity -> !(entity instanceof Player))
                .thenComparing(entity -> entity.getDisplayName().getString(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entity::getUUID));

        List<ReplayEntityDescriptor> descriptors = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            TargetReference reference = entityResolver.reference(entity);
            descriptors.add(new ReplayEntityDescriptor(reference, reference.displayName(), reference.entityType(),
                    reference.shortIdentifier(), atRequestedTime));
        }
        return List.copyOf(descriptors);
    }

    @Override
    public Optional<TargetPose> resolveEntity(TargetReference target, long replayTime) {
        if (replayTime != getCurrentReplayTime()) return Optional.empty();
        return findEntity(target.uuid()).flatMap(entity -> entityResolver.resolve(entity, target));
    }

    @Override
    public CameraPose getCurrentCameraPose() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera == null) return new CameraPose(Vec3d.ZERO, 0.0, 0.0, 0.0, 70.0);
        return new CameraPose(new Vec3d(camera.getX(), camera.getY(), camera.getZ()), camera.getYRot(), camera.getXRot(),
                0.0, minecraft.gameRenderer.mainCamera().getFov());
    }

    public boolean applyPreviewCameraPose(CameraPose pose) {
        if (pose == null || !Minecraft.getInstance().isSameThread()) return false;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) return false;
        camera.setPos(pose.position().x(), pose.position().y(), pose.position().z());
        camera.setYRot((float) pose.yaw());
        camera.setXRot((float) pose.pitch());
        camera.yRotO = (float) pose.yaw();
        camera.xRotO = (float) pose.pitch();
        return true;
    }

    @Override
    public KeyframeConflictReport detectConflicts(CameraPathPlan plan) {
        return keyframeWriter.detectConflicts(plan);
    }

    @Override
    public KeyframeWriteResult writeCameraPath(CameraPathPlan plan, KeyframeWriteOptions options) {
        KeyframeWriteResult result = keyframeWriter.write(plan, options);
        if (result.success()) {
            logger.info("Wrote CineWolf path: {} camera keyframes, {} FOV keyframes", result.cameraKeyframes(), result.fovKeyframes());
        } else {
            logger.warn("CineWolf path write rejected: {}", result.message());
        }
        return result;
    }

    @Override
    public UndoResult undoLastCineWolfOperation() {
        return keyframeWriter.undoLast();
    }

    @Override
    public void refreshTimeline() {
        EditorState state = EditorStateManager.getCurrent();
        if (state != null) state.markDirty();
    }

    public Optional<TargetReference> targetSelectedInFlashback() {
        UUID uuid = ReplayUI.getSelectedEntity();
        return uuid == null ? Optional.empty() : findEntity(uuid).map(entityResolver::reference);
    }

    public Optional<TargetReference> targetUnderCrosshair() {
        if (Minecraft.getInstance().hitResult instanceof EntityHitResult hit) {
            return Optional.of(entityResolver.reference(hit.getEntity()));
        }
        return Optional.empty();
    }

    public Optional<TargetReference> spectatedTarget() {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        Entity viewer = Minecraft.getInstance().player;
        if (camera == null || camera == viewer) return Optional.empty();
        return Optional.of(entityResolver.reference(camera));
    }

    public void goToReplayTick(long tick) {
        ReplayServer server = Flashback.getReplayServer();
        if (server != null) server.goToReplayTick(Math.toIntExact(tick));
    }

    public boolean replayPaused() {
        ReplayServer server = Flashback.getReplayServer();
        return server == null || server.replayPaused;
    }

    public void setReplayPaused(boolean paused) {
        ReplayServer server = Flashback.getReplayServer();
        if (server != null) server.replayPaused = paused;
    }

    public boolean replayStateReady() {
        ReplayServer server = Flashback.getReplayServer();
        return server != null && server.doClientRendering() && !server.isProcessingSnapshot && !server.fastForwarding
                && ((ReplayServerAccessor) (Object) server).cinewolf$currentReplayTick() == server.getReplayTick();
    }

    public boolean replayStateReady(long requestedReplayTime) {
        ReplayServer server = Flashback.getReplayServer();
        return server != null && requestedReplayTime >= 0 && requestedReplayTime <= Integer.MAX_VALUE
                && server.doClientRendering() && !server.isProcessingSnapshot && !server.fastForwarding
                && server.getReplayTick() == requestedReplayTime
                && ((ReplayServerAccessor) (Object) server).cinewolf$currentReplayTick() == requestedReplayTime;
    }

    public ReplaySample captureReplaySample(long replayTime, Set<TargetReference> requestedTargets,
                                            boolean automaticDiscovery, int maximumEntities) {
        if (!replayStateReady(replayTime)) return ReplaySample.empty(replayTime);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return ReplaySample.empty(replayTime);
        Set<UUID> requestedIds = requestedTargets == null ? Set.of()
                : requestedTargets.stream().map(TargetReference::uuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
        UUID viewer = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        List<Entity> candidates = new ArrayList<>();
        level.entitiesForRendering().forEach(entity -> {
            if (entity.getUUID().equals(viewer)) return;
            if (!automaticDiscovery && !requestedIds.contains(entity.getUUID())) return;
            candidates.add(entity);
        });
        candidates.sort(Comparator.<Entity, Boolean>comparing(entity -> !requestedIds.contains(entity.getUUID()))
                .thenComparing(entity -> !(entity instanceof Player))
                .thenComparing(Entity::getUUID));

        Map<TargetReference, ReplayEntitySnapshot> snapshots = new LinkedHashMap<>();
        int limit = Math.max(1, maximumEntities);
        for (Entity entity : candidates) {
            if (snapshots.size() >= limit) break;
            entityResolver.snapshot(entity).ifPresent(snapshot -> snapshots.put(snapshot.target(), snapshot));
        }
        return new ReplaySample(replayTime, snapshots, List.of(), List.of());
    }

    public List<ReplayMarkerSnapshot> replayMarkers(long startReplayTime, long endReplayTime) {
        ReplayServer server = Flashback.getReplayServer();
        if (server == null || server.getMetadata() == null || server.getMetadata().replayMarkers == null) return List.of();
        long clampedStart = Math.max(0L, Math.min(Integer.MAX_VALUE, startReplayTime));
        long clampedEnd = Math.max(0L, Math.min(Integer.MAX_VALUE, endReplayTime));
        if (clampedEnd < clampedStart) return List.of();
        UUID replayId = server.getMetadata().replayIdentifier == null ? new UUID(0L, 0L)
                : server.getMetadata().replayIdentifier;
        List<ReplayMarkerSnapshot> markers = new ArrayList<>();
        server.getMetadata().replayMarkers.subMap((int) clampedStart, true,
                (int) clampedEnd, true).forEach((tick, marker) -> {
            Optional<Vec3d> position = Optional.empty();
            if (marker.position() != null && marker.position().position() != null) {
                org.joml.Vector3f value = marker.position().position();
                position = Optional.of(new Vec3d(value.x(), value.y(), value.z()));
            }
            String label = marker.description() == null ? "" : marker.description();
            String identity = replayId + ":" + tick + ":" + marker.colour() + ":" + label;
            markers.add(new ReplayMarkerSnapshot(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)),
                    tick, label, position));
        });
        return List.copyOf(markers);
    }

    public UUID replayIdentifier() {
        ReplayServer server = Flashback.getReplayServer();
        return server == null || server.getMetadata() == null || server.getMetadata().replayIdentifier == null
                ? new UUID(0L, 0L) : server.getMetadata().replayIdentifier;
    }

    private Optional<Entity> findEntity(UUID uuid) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return Optional.empty();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) return Optional.of(entity);
        }
        return Optional.empty();
    }
}
