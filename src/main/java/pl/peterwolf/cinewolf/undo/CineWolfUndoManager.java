package pl.peterwolf.cinewolf.undo;

import java.util.Optional;

public final class CineWolfUndoManager<T> {
    private T lastSnapshot;

    public void remember(T snapshot) {
        lastSnapshot = snapshot;
    }

    public Optional<T> peek() {
        return Optional.ofNullable(lastSnapshot);
    }

    public Optional<T> take() {
        T snapshot = lastSnapshot;
        lastSnapshot = null;
        return Optional.ofNullable(snapshot);
    }

    public void clear() {
        lastSnapshot = null;
    }
}
