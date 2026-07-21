package pl.peterwolf.cinewolf.montage.scene;

import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;

import java.util.List;

public interface SceneSegmenter {
    List<ReplayScene> segment(long rangeStart, long rangeEnd, List<ReplaySample> samples,
                              List<ReplayEvent> events, DetectorThresholds thresholds);
}
