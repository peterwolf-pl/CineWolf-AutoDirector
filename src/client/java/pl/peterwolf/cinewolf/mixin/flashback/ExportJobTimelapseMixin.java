package pl.peterwolf.cinewolf.mixin.flashback;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.peterwolf.cinewolf.integration.flashback.TimelapseExportController;

@Mixin(targets = "com.moulberry.flashback.exporting.ExportJob", remap = false)
public abstract class ExportJobTimelapseMixin {
    @ModifyArg(
            method = "doExport",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/moulberry/flashback/exporting/ExportJob;calculateTicks(Lcom/moulberry/flashback/state/EditorState;IID)Ljava/util/List;",
                    remap = false
            ),
            index = 3,
            remap = false,
            require = 1
    )
    private double cinewolf$applyTimelapseSampling(double outputFps) {
        return TimelapseExportController.samplingFps(outputFps);
    }
}
