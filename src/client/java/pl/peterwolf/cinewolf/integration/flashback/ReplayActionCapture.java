package pl.peterwolf.cinewolf.integration.flashback;

import pl.peterwolf.cinewolf.montage.analysis.ObservedReplayAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Thread-safe, short-lived journal populated by the Flashback packet observer during one analysis. */
public final class ReplayActionCapture {
    private static final Object CAPTURE_LOCK = new Object();
    private static Session active;

    private ReplayActionCapture() {
    }

    public static void begin(long generation, long startReplayTime, long endReplayTime) {
        if (generation < 0 || startReplayTime < 0 || endReplayTime <= startReplayTime) {
            throw new IllegalArgumentException("Invalid replay action capture range");
        }
        synchronized (CAPTURE_LOCK) {
            if (active != null) active.closed = true;
            active = new Session(generation, startReplayTime, endReplayTime);
        }
    }

    public static boolean isActive() {
        synchronized (CAPTURE_LOCK) {
            return active != null && !active.closed;
        }
    }

    public static OptionalLong currentGeneration() {
        synchronized (CAPTURE_LOCK) {
            return active == null || active.closed ? OptionalLong.empty() : OptionalLong.of(active.generation);
        }
    }

    public static void record(ObservedReplayAction action) {
        if (action == null) return;
        synchronized (CAPTURE_LOCK) {
            Session session = active;
            if (session == null || session.closed || action.replayTime() < session.startReplayTime
                    || action.replayTime() > session.endReplayTime) return;
            String key = key(action);
            session.actions.merge(key, action, ReplayActionCapture::preferRicherAction);
        }
    }

    public static List<ObservedReplayAction> finish(long generation) {
        synchronized (CAPTURE_LOCK) {
            Session session = active;
            if (session == null || session.closed || session.generation != generation) return List.of();
            session.closed = true;
            active = null;
            List<ObservedReplayAction> result = new ArrayList<>(session.actions.values());
            result.sort(Comparator.comparingLong(ObservedReplayAction::replayTime)
                    .thenComparing(action -> action.getClass().getSimpleName())
                    .thenComparing(ReplayActionCapture::key));
            return List.copyOf(result);
        }
    }

    public static List<ObservedReplayAction> snapshot(long generation) {
        synchronized (CAPTURE_LOCK) {
            Session session = active;
            if (session == null || session.closed || session.generation != generation) return List.of();
            return sortedCopy(session.actions.values());
        }
    }

    public static void cancel(long generation) {
        synchronized (CAPTURE_LOCK) {
            if (active != null && active.generation == generation) {
                active.closed = true;
                active = null;
            }
        }
    }

    public static void clear() {
        synchronized (CAPTURE_LOCK) {
            if (active != null) active.closed = true;
            active = null;
        }
    }

    private static String key(ObservedReplayAction action) {
        if (action instanceof ObservedReplayAction.CombatSignal signal) {
            String victim = signal.victim().map(target -> target.uuid().toString()).orElse("unknown");
            String attacker = signal.attacker().map(target -> target.uuid().toString()).orElse("unknown");
            if (signal.signalType() == ObservedReplayAction.CombatSignalType.DAMAGE) {
                return "combat|damage|" + signal.replayTime() + '|' + victim;
            }
            return "combat|" + signal.signalType() + '|' + signal.replayTime() + '|' + attacker + '|' + victim;
        }
        if (action instanceof ObservedReplayAction.ProjectileSignal signal) {
            return "projectile|" + signal.projectileId() + '|' + signal.signalType() + '|' + signal.replayTime();
        }
        return action.getClass().getName() + '|' + action.replayTime() + '|'
                + quantize(action.location().x()) + '|' + quantize(action.location().y()) + '|'
                + quantize(action.location().z()) + '|' + action;
    }

    private static ObservedReplayAction preferRicherAction(ObservedReplayAction previous,
                                                            ObservedReplayAction candidate) {
        if (previous instanceof ObservedReplayAction.CombatSignal oldSignal
                && candidate instanceof ObservedReplayAction.CombatSignal newSignal) {
            int oldEvidence = (oldSignal.attacker().isPresent() ? 2 : 0)
                    + (oldSignal.victim().isPresent() ? 1 : 0) + (oldSignal.magnitude() > 0.0 ? 1 : 0);
            int newEvidence = (newSignal.attacker().isPresent() ? 2 : 0)
                    + (newSignal.victim().isPresent() ? 1 : 0) + (newSignal.magnitude() > 0.0 ? 1 : 0);
            return newEvidence > oldEvidence ? candidate : previous;
        }
        return previous;
    }

    private static long quantize(double value) {
        return Math.round(value * 1000.0);
    }

    private static List<ObservedReplayAction> sortedCopy(java.util.Collection<ObservedReplayAction> actions) {
        List<ObservedReplayAction> result = new ArrayList<>(actions);
        result.sort(Comparator.comparingLong(ObservedReplayAction::replayTime)
                .thenComparing(action -> action.getClass().getSimpleName())
                .thenComparing(ReplayActionCapture::key));
        return List.copyOf(result);
    }

    private static final class Session {
        private final long generation;
        private final long startReplayTime;
        private final long endReplayTime;
        private final Map<String, ObservedReplayAction> actions = new LinkedHashMap<>();
        private boolean closed;

        private Session(long generation, long startReplayTime, long endReplayTime) {
            this.generation = generation;
            this.startReplayTime = startReplayTime;
            this.endReplayTime = endReplayTime;
        }
    }
}
