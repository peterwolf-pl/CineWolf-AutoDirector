package pl.peterwolf.cinewolf.integration.flashback;

import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pl.peterwolf.cinewolf.model.BoundingBox;
import pl.peterwolf.cinewolf.model.TargetPose;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;

import java.util.Optional;

public final class FlashbackEntityResolver {
    public Optional<TargetPose> resolve(Entity entity, TargetReference reference) {
        if (entity == null || !entity.getUUID().equals(reference.uuid())) return Optional.empty();
        Vec3 renderedPosition = entity.position();
        Vec3 position = stableSamplePosition(entity, renderedPosition);
        Vec3 interpolationOffset = position.subtract(renderedPosition);
        Vec3 focus = entity.getEyePosition().add(interpolationOffset);
        AABB box = entity.getBoundingBox();
        if (interpolationOffset.lengthSqr() > 0.0) {
            box = box.move(interpolationOffset);
        }

        // Passenger free-look is useless for cinematic vehicle framing. Prefer the mount's body
        // orientation, type id (so plane providers match), and expanded bounding volume.
        Entity vehicle = entity.getVehicle();
        float yaw;
        float pitch;
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        boolean inVehicle = vehicle != null;
        if (vehicle != null) {
            Orientation vehicleOrientation = sampleOrientation(vehicle);
            yaw = vehicleOrientation.yaw();
            pitch = vehicleOrientation.pitch();
            entityType = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString();
            AABB vehicleBox = vehicle.getBoundingBox();
            Vec3 vehicleRendered = vehicle.position();
            Vec3 vehicleStable = stableSamplePosition(vehicle, vehicleRendered);
            Vec3 vehicleOffset = vehicleStable.subtract(vehicleRendered);
            if (vehicleOffset.lengthSqr() > 0.0) {
                vehicleBox = vehicleBox.move(vehicleOffset);
            }
            box = box.minmax(vehicleBox);
        } else {
            Orientation self = sampleOrientation(entity);
            yaw = self.yaw();
            pitch = self.pitch();
        }

        return Optional.of(new TargetPose(
                vector(position),
                vector(focus),
                new BoundingBox(new Vec3d(box.minX, box.minY, box.minZ), new Vec3d(box.maxX, box.maxY, box.maxZ)),
                yaw,
                pitch,
                Vec3d.ZERO,
                entityType,
                inVehicle || entity.isPassenger(),
                entity.level().dimension().identifier().toString(),
                false
        ));
    }

    private static Orientation sampleOrientation(Entity entity) {
        InterpolationHandler interpolation = entity.getInterpolation();
        PositionAndRotation target = interpolation != null && interpolation.hasActiveInterpolation()
                ? interpolation.target() : null;
        float yaw = target != null ? target.yRot() : entity.getYRot();
        float pitch = target != null ? target.xRot() : entity.getXRot();
        return new Orientation(yaw, pitch);
    }

    /**
     * Replay seeking can leave a remote entity part-way through its client interpolation steps. Sampling the
     * mid-lerp {@link Entity#position()} bakes a periodic forward/back pulse into camera keys.
     * <p>
     * Prefer the interpolation destination when active. If the destination is unreasonably far from the
     * rendered position (stale seek state), fall back to the rendered pose rather than inventing a teleport.
     */
    private static Vec3 stableSamplePosition(Entity entity, Vec3 fallback) {
        InterpolationHandler interpolation = entity.getInterpolation();
        if (interpolation == null || !interpolation.hasActiveInterpolation()) return fallback;
        PositionAndRotation destination = interpolation.target();
        if (destination == null) return fallback;
        Vec3 target = destination.position();
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) {
            return fallback;
        }
        double jump = target.distanceTo(fallback);
        // Destinations more than ~10 blocks from the rendered pose after a seek are usually stale.
        if (jump > 10.0) return fallback;
        return target;
    }

    public Optional<ReplayEntitySnapshot> snapshot(Entity entity) {
        if (entity == null) return Optional.empty();
        TargetReference reference = reference(entity);
        TargetPose pose = resolve(entity, reference).orElse(null);
        if (pose == null) return Optional.empty();

        double health = Double.NaN;
        double maximumHealth = Double.NaN;
        int hurtTime = 0;
        boolean attacking = false;
        boolean swinging = false;
        boolean elytraFlying = false;
        if (entity instanceof LivingEntity living) {
            health = living.getHealth();
            maximumHealth = living.getMaxHealth();
            hurtTime = living.hurtTime;
            attacking = living.getSwingAnimation(0.0f) > 0.01f;
            swinging = living.isSwinging();
            elytraFlying = living.isFallFlying();
        }

        Entity vehicle = entity.getVehicle();
        Optional<java.util.UUID> vehicleUuid = vehicle == null ? Optional.empty() : Optional.of(vehicle.getUUID());
        Optional<String> vehicleType = vehicle == null ? Optional.empty()
                : Optional.of(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString());
        // Flashback does not replay ClientboundPlayerAbilitiesPacket, so creative flight cannot be
        // treated as direct state. The detector may still infer sustained airborne motion conservatively.
        boolean creativeFlying = false;
        return Optional.of(new ReplayEntitySnapshot(reference, pose, health, maximumHealth, hurtTime,
                attacking, swinging, entity.isAlive() && !entity.isRemoved(), entity.onGround(),
                groundProximity(entity), vehicleUuid, vehicleType, creativeFlying, elytraFlying));
    }

    public TargetReference reference(Entity entity) {
        String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        String name = entity.getDisplayName().getString();
        return new TargetReference(entity.getUUID(), entityType, name);
    }

    private static Vec3d vector(Vec3 vector) {
        return new Vec3d(vector.x, vector.y, vector.z);
    }

    private static double groundProximity(Entity entity) {
        if (entity.onGround()) return 0.0;
        Vec3 start = entity.position();
        Vec3 end = start.add(0.0, -64.0, 0.0);
        HitResult hit = entity.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, entity));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : Math.max(0.0, start.y - hit.getLocation().y);
    }

    private record Orientation(float yaw, float pitch) {
    }
}
