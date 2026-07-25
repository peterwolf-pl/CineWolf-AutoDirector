package pl.peterwolf.cinewolf.mixin.flashback;

import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.record.Recorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Recorder.class, remap = false)
public interface RecorderAccessor {
    @Accessor(value = "writtenTicks", remap = false)
    int cinewolf$writtenTicks();

    @Accessor(value = "metadata", remap = false)
    FlashbackMeta cinewolf$metadata();
}
