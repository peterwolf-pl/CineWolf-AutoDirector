package pl.peterwolf.cinewolf.mixin.clip;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.peterwolf.cinewolf.clip.OcclusionClipController;

/**
 * During section mesh compile, treat CineWolf-clipped occluders as air so they disappear from view
 * without moving the camera path.
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void cinewolf$clipBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (OcclusionClipController.get().shouldClipBlock(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void cinewolf$clipFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (OcclusionClipController.get().shouldClipBlock(pos)) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }
}
