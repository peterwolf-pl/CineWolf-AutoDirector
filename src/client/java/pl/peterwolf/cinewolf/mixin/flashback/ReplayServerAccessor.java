package pl.peterwolf.cinewolf.mixin.flashback;

import com.moulberry.flashback.playback.ReplayServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ReplayServer.class, remap = false)
public interface ReplayServerAccessor {
    @Accessor(value = "currentTick", remap = false)
    int cinewolf$currentReplayTick();
}
