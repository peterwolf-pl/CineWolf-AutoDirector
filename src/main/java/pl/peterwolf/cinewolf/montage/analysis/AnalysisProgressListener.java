package pl.peterwolf.cinewolf.montage.analysis;

@FunctionalInterface
public interface AnalysisProgressListener {
    AnalysisProgressListener NONE = (stage, progress, completed, total) -> {
    };

    void onProgress(AnalysisStage stage, double progress, int completed, int total);
}
