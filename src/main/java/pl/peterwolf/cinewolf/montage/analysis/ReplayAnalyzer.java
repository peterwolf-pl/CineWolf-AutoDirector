package pl.peterwolf.cinewolf.montage.analysis;

public interface ReplayAnalyzer {
    ReplayAnalysisResult analyze(ReplayAnalysisRequest request, ReplayAnalysisContext context,
                                 AnalysisProgressListener progressListener, CancellationToken cancellationToken);
}
