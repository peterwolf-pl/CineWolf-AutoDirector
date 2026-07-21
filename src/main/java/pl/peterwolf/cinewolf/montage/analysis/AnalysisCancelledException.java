package pl.peterwolf.cinewolf.montage.analysis;

public final class AnalysisCancelledException extends RuntimeException {
    public AnalysisCancelledException() {
        super("Replay analysis was cancelled");
    }
}
