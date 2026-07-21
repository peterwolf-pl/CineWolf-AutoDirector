package pl.peterwolf.cinewolf.mixin.flashback;

import com.moulberry.flashback.playback.ReplayGamePacketHandler;
import com.moulberry.flashback.playback.ReplayServer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.peterwolf.cinewolf.integration.flashback.ReplayActionCapture;
import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.ObservedReplayAction;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mixin(value = ReplayGamePacketHandler.class, remap = false)
public abstract class ReplayGamePacketHandlerMixin {
    @Shadow(remap = false) @Final private ReplayServer replayServer;
    @Shadow(remap = false) private Int2ObjectMap<Entity> pendingEntities;

    @Shadow(remap = false) public abstract ServerLevel level();

    @Unique private final Map<BreakerKey, Breaker> cinewolf$recentBreakers = new HashMap<>();
    @Unique private long cinewolf$captureGeneration = Long.MIN_VALUE;

    @Inject(method = "handleAnimate", at = @At("HEAD"), remap = false)
    private void cinewolf$captureAnimation(ClientboundAnimatePacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        if (packet.getAction() != ClientboundAnimatePacket.SWING_MAIN_HAND
                && packet.getAction() != ClientboundAnimatePacket.SWING_OFF_HAND) return;
        Entity attacker = cinewolf$entity(packet.getId());
        if (attacker == null) return;
        ReplayActionCapture.record(new ObservedReplayAction.CombatSignal(cinewolf$tick(),
                ObservedReplayAction.CombatSignalType.ATTACK, Optional.of(cinewolf$reference(attacker)),
                Optional.empty(), cinewolf$position(attacker.position()), 0.2));
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"), remap = false)
    private void cinewolf$captureDamage(ClientboundDamageEventPacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        Entity victim = cinewolf$entity(packet.entityId());
        Entity attacker = cinewolf$entity(packet.sourceCauseId());
        Vec3 location = packet.sourcePosition().orElseGet(() -> victim == null ? Vec3.ZERO : victim.position());
        ReplayActionCapture.record(new ObservedReplayAction.CombatSignal(cinewolf$tick(),
                ObservedReplayAction.CombatSignalType.DAMAGE, cinewolf$optionalReference(attacker),
                cinewolf$optionalReference(victim), cinewolf$position(location), 0.0));
        Entity direct = cinewolf$entity(packet.sourceDirectId());
        if (direct instanceof Projectile projectile) {
            ReplayActionCapture.record(new ObservedReplayAction.ProjectileSignal(cinewolf$tick(), projectile.getUUID(),
                    ObservedReplayAction.ProjectileSignalType.HIT, cinewolf$optionalReference(projectile.getOwner()),
                    cinewolf$optionalReference(victim), cinewolf$position(location)));
        }
    }

    @Inject(method = "handleHurtAnimation", at = @At("HEAD"), remap = false)
    private void cinewolf$captureHurt(ClientboundHurtAnimationPacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        Entity victim = cinewolf$entity(packet.id());
        if (victim == null) return;
        ReplayActionCapture.record(new ObservedReplayAction.CombatSignal(cinewolf$tick(),
                ObservedReplayAction.CombatSignalType.DAMAGE, Optional.empty(), Optional.of(cinewolf$reference(victim)),
                cinewolf$position(victim.position()), 0.0));
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), remap = false)
    private void cinewolf$captureDeath(ClientboundEntityEventPacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture() || packet.getEventId() != EntityEvent.DEATH) return;
        Entity victim = packet.getEntity(level());
        if (victim == null) return;
        ReplayActionCapture.record(new ObservedReplayAction.CombatSignal(cinewolf$tick(),
                ObservedReplayAction.CombatSignalType.DEATH, Optional.empty(), Optional.of(cinewolf$reference(victim)),
                cinewolf$position(victim.position()), 1.0));
    }

    @Inject(method = "handleBlockDestruction", at = @At("HEAD"), remap = false)
    private void cinewolf$captureBreaker(ClientboundBlockDestructionPacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        BreakerKey key = cinewolf$breakerKey(packet.getPos());
        if (key == null) return;
        if (packet.getProgress() < 0) {
            cinewolf$recentBreakers.remove(key);
            return;
        }
        Entity actor = cinewolf$entity(packet.getId());
        if (actor != null) {
            cinewolf$recentBreakers.put(key, new Breaker(cinewolf$tick(), cinewolf$reference(actor)));
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), remap = false)
    private void cinewolf$captureBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        ServerLevel level = level();
        if (level != null) cinewolf$recordBlockChange(packet.getPos(), level.getBlockState(packet.getPos()), packet.getBlockState());
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), remap = false)
    private void cinewolf$captureSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        ServerLevel level = level();
        if (level != null) packet.runUpdates((position, state) -> cinewolf$recordBlockChange(position,
                level.getBlockState(position), state));
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"), remap = false)
    private void cinewolf$captureProjectileSpawn(ClientboundAddEntityPacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        Entity entity = pendingEntities == null ? null : pendingEntities.get(packet.getId());
        if (entity == null) entity = cinewolf$entity(packet.getId());
        if (!(entity instanceof Projectile projectile)) return;
        ReplayActionCapture.record(new ObservedReplayAction.ProjectileSignal(cinewolf$tick(), projectile.getUUID(),
                ObservedReplayAction.ProjectileSignalType.SPAWN, cinewolf$optionalReference(projectile.getOwner()),
                Optional.empty(), cinewolf$position(projectile.position())));
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"), remap = false)
    private void cinewolf$captureProjectileRemoval(ClientboundRemoveEntitiesPacket packet, CallbackInfo callbackInfo) {
        if (!cinewolf$canCapture()) return;
        packet.getEntityIds().forEach((int id) -> {
            Entity entity = cinewolf$entity(id);
            if (entity instanceof Projectile projectile) {
                ReplayActionCapture.record(new ObservedReplayAction.ProjectileSignal(cinewolf$tick(), projectile.getUUID(),
                        ObservedReplayAction.ProjectileSignalType.DESPAWN, cinewolf$optionalReference(projectile.getOwner()),
                        Optional.empty(), cinewolf$position(projectile.position())));
            }
        });
    }

    @Unique
    private void cinewolf$recordBlockChange(BlockPos position, BlockState oldState, BlockState newState) {
        if (oldState == newState || oldState.equals(newState)) return;
        int tick = cinewolf$tick();
        BreakerKey key = cinewolf$breakerKey(position);
        if (key == null) return;
        Breaker breaker = cinewolf$recentBreakers.get(key);
        Optional<TargetReference> actor = breaker != null && tick - breaker.tick <= 8
                ? Optional.of(breaker.actor) : Optional.empty();
        Vec3d location = new Vec3d(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        if (!oldState.isAir() && newState.isAir()) {
            ReplayActionCapture.record(new ObservedReplayAction.BlockDestroyed(tick, actor, location,
                    BuiltInRegistries.BLOCK.getKey(oldState.getBlock()).toString()));
        } else if (oldState.isAir() && !newState.isAir()) {
            ReplayActionCapture.record(new ObservedReplayAction.BlockPlaced(tick, Optional.empty(), location,
                    BuiltInRegistries.BLOCK.getKey(newState.getBlock()).toString()));
        }
        cinewolf$recentBreakers.entrySet().removeIf(entry -> tick - entry.getValue().tick > 8);
    }

    @Unique
    private boolean cinewolf$canCapture() {
        java.util.OptionalLong generation = ReplayActionCapture.currentGeneration();
        if (generation.isEmpty() || replayServer.isProcessingSnapshot) return false;
        if (generation.getAsLong() != cinewolf$captureGeneration) {
            cinewolf$captureGeneration = generation.getAsLong();
            cinewolf$recentBreakers.clear();
        }
        return true;
    }

    @Unique
    private int cinewolf$tick() {
        return ((ReplayServerAccessor) (Object) replayServer).cinewolf$currentReplayTick();
    }

    @Unique
    private Entity cinewolf$entity(int id) {
        return id < 0 || level() == null ? null : level().getEntity(id);
    }

    @Unique
    private BreakerKey cinewolf$breakerKey(BlockPos position) {
        ServerLevel level = level();
        if (level == null) return null;
        return new BreakerKey(level.dimension().identifier().toString(), position.asLong());
    }

    @Unique
    private static Optional<TargetReference> cinewolf$optionalReference(Entity entity) {
        return entity == null ? Optional.empty() : Optional.of(cinewolf$reference(entity));
    }

    @Unique
    private static TargetReference cinewolf$reference(Entity entity) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return new TargetReference(entity.getUUID(), type, entity.getDisplayName().getString());
    }

    @Unique
    private static Vec3d cinewolf$position(Vec3 value) {
        return new Vec3d(value.x, value.y, value.z);
    }

    @Unique
    private record Breaker(int tick, TargetReference actor) {
    }

    private record BreakerKey(String dimension, long blockPosition) {
    }
}
