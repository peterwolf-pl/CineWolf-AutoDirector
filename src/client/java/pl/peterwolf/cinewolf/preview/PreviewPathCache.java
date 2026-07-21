package pl.peterwolf.cinewolf.preview;

import com.moulberry.flashback.state.EditorState;
import pl.peterwolf.cinewolf.model.CameraPathPlan;
import pl.peterwolf.cinewolf.model.ShotRequest;

public record PreviewPathCache(
        CameraPathPlan plan,
        ShotRequest request,
        long replayTick,
        EditorState editorState,
        int editorModCount
) {
}
