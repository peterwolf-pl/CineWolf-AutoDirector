package pl.peterwolf.cinewolf.clip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.peterwolf.cinewolf.config.CineWolfConfig;
import pl.peterwolf.cinewolf.config.ObstacleHandlingMode;
import pl.peterwolf.cinewolf.model.TargetReference;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime occlusion clipping: hides solid blocks (and optionally entities) on the line of sight
 * between the active camera and the cinematic subject, instead of moving the camera path.
 */
public final class OcclusionClipController {
    private static final OcclusionClipController INSTANCE = new OcclusionClipController();
    private static final int MAX_CLIPPED_BLOCKS = 384;
    private static final double RAY_INSET = 0.15;

    private final Set<Long> clippedBlocks = ConcurrentHashMap.newKeySet();
    private volatile boolean active;
    private volatile UUID subjectUuid;
    private volatile TargetReference preferredSubject;
    /**
     * When true, force CLIP behaviour even if config obstacle mode is AVOID (indoor montage fallback).
     */
    private volatile boolean indoorClipOverride;
    private CineWolfConfig config;

    private OcclusionClipController() {
    }

    public static OcclusionClipController get() {
        return INSTANCE;
    }

    public void bindConfig(CineWolfConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void setPreferredSubject(TargetReference subject) {
        this.preferredSubject = subject;
        this.subjectUuid = subject == null ? null : subject.uuid();
    }

    public void setIndoorClipOverride(boolean enabled) {
        this.indoorClipOverride = enabled;
        if (!enabled) {
            clearAndRestore(Minecraft.getInstance().level);
        }
    }

    public boolean indoorClipOverride() {
        return indoorClipOverride;
    }

    private boolean clipModeEnabled() {
        return config != null
                && (config.montage.obstacleHandling().clipsOccluders() || indoorClipOverride);
    }

    public boolean isActive() {
        return active && clipModeEnabled();
    }

    public boolean shouldClipBlock(BlockPos pos) {
        return isActive() && pos != null && clippedBlocks.contains(pos.asLong());
    }

    public boolean shouldHideEntity(Entity entity) {
        if (!isActive() || entity == null || config == null || !config.montage.clipEntities) return false;
        UUID subject = subjectUuid;
        if (subject != null && subject.equals(entity.getUUID())) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && entity.getUUID().equals(minecraft.player.getUUID())) return false;
        Entity camera = minecraft.getCameraEntity();
        if (camera != null && entity.getUUID().equals(camera.getUUID())) return false;
        // Hide only when the entity volume intersects the camera→subject segment.
        Entity subjectEntity = findSubject(minecraft.level);
        if (subjectEntity == null || camera == null) return false;
        // Critical: never erase the mount the subject is riding (planes, boats, minecarts, horses…).
        // The old test used AABB.contains(subject eyes), so any vehicle containing the player was
        // always treated as an occluder and disappeared on camera.
        if (isSubjectMountOrRelated(entity, subjectEntity)) return false;
        Vec3 subjectEye = subjectEntity.getEyePosition(1.0f);
        // Cockpit / shell: subject eyes sit inside the vehicle BB even when passenger links differ
        // across multiparts or soft plane mods.
        if (entity.getBoundingBox().inflate(0.35).contains(subjectEye)) return false;
        return entityIntersectsSegment(entity, camera.getEyePosition(1.0f), subjectEye);
    }

    /**
     * True when {@code entity} is the subject's vehicle, shares the same root vehicle tree
     * (co-passengers / multiparts), or carries the subject as a passenger.
     */
    static boolean isSubjectMountOrRelated(Entity entity, Entity subject) {
        if (entity == null || subject == null) return false;
        if (entity.getUUID().equals(subject.getUUID())) return true;

        Entity subjectRoot = subject.getRootVehicle();
        Entity entityRoot = entity.getRootVehicle();
        if (subjectRoot != null && entityRoot != null
                && subjectRoot.getUUID().equals(entityRoot.getUUID())
                && (!subjectRoot.getUUID().equals(subject.getUUID())
                || !entityRoot.getUUID().equals(entity.getUUID()))) {
            return true;
        }

        Entity vehicle = subject.getVehicle();
        while (vehicle != null) {
            if (vehicle.getUUID().equals(entity.getUUID())) return true;
            vehicle = vehicle.getVehicle();
        }

        return hasPassenger(entity, subject.getUUID(), 0);
    }

    private static boolean hasPassenger(Entity host, UUID passengerId, int depth) {
        if (host == null || passengerId == null || depth > 8) return false;
        for (Entity passenger : host.getPassengers()) {
            if (passenger == null) continue;
            if (passengerId.equals(passenger.getUUID())) return true;
            if (hasPassenger(passenger, passengerId, depth + 1)) return true;
        }
        return false;
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (config == null || level == null || minecraft.player == null || !clipModeEnabled()) {
            clearAndRestore(level);
            return;
        }
        Entity camera = minecraft.getCameraEntity();
        Entity subject = findSubject(level);
        if (camera == null || subject == null) {
            clearAndRestore(level);
            return;
        }
        subjectUuid = subject.getUUID();
        Vec3 from = camera.getEyePosition(1.0f);
        Vec3 to = subject.getEyePosition(1.0f);
        Set<Long> next = collectOccluders(level, from, to);
        applySet(level, next);
        active = true;
    }

    public void clear() {
        clearAndRestore(Minecraft.getInstance().level);
    }

    private void clearAndRestore(ClientLevel level) {
        if (!active && clippedBlocks.isEmpty()) return;
        Set<Long> previous = new HashSet<>(clippedBlocks);
        clippedBlocks.clear();
        active = false;
        if (level != null) dirtySections(level, previous);
    }

    private void applySet(ClientLevel level, Set<Long> next) {
        if (next.equals(clippedBlocks)) return;
        Set<Long> dirty = new HashSet<>();
        for (Long packed : clippedBlocks) {
            if (!next.contains(packed)) dirty.add(packed);
        }
        for (Long packed : next) {
            if (!clippedBlocks.contains(packed)) dirty.add(packed);
        }
        clippedBlocks.clear();
        clippedBlocks.addAll(next);
        dirtySections(level, dirty);
    }

    private static void dirtySections(ClientLevel level, Set<Long> packedPositions) {
        if (level == null || packedPositions.isEmpty()) return;
        Set<Long> sections = new HashSet<>();
        for (Long packed : packedPositions) {
            BlockPos pos = BlockPos.of(packed);
            sections.add(SectionPos.asLong(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getY()),
                    SectionPos.blockToSectionCoord(pos.getZ())));
        }
        for (Long section : sections) {
            level.setSectionDirtyWithNeighbors(
                    SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }
    }

    private Set<Long> collectOccluders(ClientLevel level, Vec3 from, Vec3 to) {
        Set<Long> result = new HashSet<>();
        // Slight inset so we never clip the block the camera sits in / the subject stands on.
        Vec3 direction = to.subtract(from);
        double length = direction.length();
        if (length < 0.5) return result;
        Vec3 unit = direction.scale(1.0 / length);
        Vec3 start = from.add(unit.scale(RAY_INSET));
        Vec3 end = to.subtract(unit.scale(RAY_INSET));
        // Central ray + small parallel offsets for a thicker "clip tube".
        double[][] offsets = {
                {0, 0, 0},
                {0.35, 0, 0}, {-0.35, 0, 0},
                {0, 0.35, 0}, {0, -0.35, 0},
                {0, 0, 0.35}, {0, 0, -0.35}
        };
        for (double[] offset : offsets) {
            if (result.size() >= MAX_CLIPPED_BLOCKS) break;
            Vec3 o = new Vec3(offset[0], offset[1], offset[2]);
            traverseSolid(level, start.add(o), end.add(o), result);
        }
        return result;
    }

    private static void traverseSolid(ClientLevel level, Vec3 start, Vec3 end, Set<Long> out) {
        BlockGetter.traverseBlocks(start, end, null, (ignored, pos) -> {
            if (out.size() >= MAX_CLIPPED_BLOCKS) return Boolean.TRUE;
            BlockState state = level.getBlockState(pos);
            if (state == null || state.isAir()) return null;
            if (state.canOcclude() || !state.getCollisionShape(level, pos).isEmpty()) {
                out.add(pos.asLong());
            }
            FluidState fluid = level.getFluidState(pos);
            if (fluid != null && !fluid.isEmpty()) {
                out.add(pos.asLong());
            }
            return null;
        }, ignored -> null);
    }

    private Entity findSubject(ClientLevel level) {
        if (level == null) return null;
        if (preferredSubject != null) {
            for (Entity entity : level.entitiesForRendering()) {
                if (preferredSubject.uuid().equals(entity.getUUID())) return entity;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        // Spectating a non-local entity: use that as the clip subject.
        if (camera != null && minecraft.player != null && !camera.getUUID().equals(minecraft.player.getUUID())) {
            return camera;
        }
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 eye = camera == null ? Vec3.ZERO : camera.getEyePosition(1.0f);
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) continue;
            if (minecraft.player != null && player.getUUID().equals(minecraft.player.getUUID())) continue;
            double distance = player.position().distanceToSqr(eye);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static boolean entityIntersectsSegment(Entity entity, Vec3 from, Vec3 to) {
        AABB box = entity.getBoundingBox().inflate(0.15);
        // Do not use contains(to): for a ridden vehicle the subject eye is inside the BB, so the
        // mount would always "intersect" and get hidden. Intersection is ray-only + camera inside.
        return box.clip(from, to).isPresent() || box.contains(from);
    }
}
