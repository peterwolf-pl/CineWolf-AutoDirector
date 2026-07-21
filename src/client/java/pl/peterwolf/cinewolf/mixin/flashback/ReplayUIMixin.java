package pl.peterwolf.cinewolf.mixin.flashback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.peterwolf.cinewolf.CineWolfAutoDirectorClient;

@Mixin(targets = "com.moulberry.flashback.editor.ui.ReplayUI", remap = false)
public abstract class ReplayUIMixin {
    @Inject(
            method = "drawOverlayInternal",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/moulberry/flashback/editor/ui/windows/WindowType;renderAll()V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false,
            require = 1
    )
    private static void cinewolf$renderAutoDirectorPanel(CallbackInfo callbackInfo) {
        CineWolfAutoDirectorClient.renderPanel();
    }
}
