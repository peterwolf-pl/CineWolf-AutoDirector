package pl.peterwolf.cinewolf.mixin.clip;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.peterwolf.cinewolf.clip.OcclusionClipController;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void cinewolf$hideOccludingEntities(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (OcclusionClipController.get().shouldHideEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
