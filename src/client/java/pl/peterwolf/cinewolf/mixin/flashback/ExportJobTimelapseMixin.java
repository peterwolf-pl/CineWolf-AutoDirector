package pl.peterwolf.cinewolf.mixin.flashback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pl.peterwolf.cinewolf.integration.flashback.TimelapseExportController;

@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob", remap = false)
public abstract class ExportJobTimelapseMixin {
    /**
     * Adjust only the FPS used to build Flashback's replay-time sample list.
     * ExportSettings.framerate stays unchanged, so the encoder still writes
     * the user's selected output FPS.
     *
     * require = 0 is deliberate: if a future Flashback version changes this
     * private method, CineWolf must keep loading instead of crashing. In that
     * case export simply falls back to normal-speed Flashback sampling.
     */
    @ModifyVariable(
            method = "calculateTicks",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private static double cinewolf$applyTimelapseSampling(double outputFps) {
        return TimelapseExportController.samplingFps(outputFps);
    }
}
