package pl.peterwolf.cinewolf.montage.analysis;

@FunctionalInterface
public interface CancellationToken {
    CancellationToken NONE = () -> false;

    boolean isCancelled();

    default void throwIfCancelled() {
        if (isCancelled()) throw new AnalysisCancelledException();
    }
}
