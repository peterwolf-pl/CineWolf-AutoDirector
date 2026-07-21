package pl.peterwolf.cinewolf.montage.event;

import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;

import java.util.List;
import java.util.Set;

public interface ReplayEventDetector {
    Set<ReplayEventType> supportedTypes();

    List<ReplayEvent> detect(ReplaySampleWindow samples, ReplayAnalysisContext context,
                             double sensitivity);
}
