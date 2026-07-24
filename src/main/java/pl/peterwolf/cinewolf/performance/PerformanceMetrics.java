package pl.peterwolf.cinewolf.performance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lightweight local timings for diagnostics. */
public final class PerformanceMetrics {
    private final Map<String, Long> totalsNanos = new ConcurrentHashMap<>();
    private final Map<String, Long> counts = new ConcurrentHashMap<>();

    public void record(String key, long nanos) {
        if (key == null || nanos < 0) return;
        totalsNanos.merge(key, nanos, Long::sum);
        counts.merge(key, 1L, Long::sum);
    }

    public Timed start(String key) {
        return new Timed(key, System.nanoTime());
    }

    public Map<String, Long> snapshotMillis() {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : totalsNanos.entrySet()) {
            map.put(entry.getKey(), entry.getValue() / 1_000_000L);
        }
        return Map.copyOf(map);
    }

    public final class Timed implements AutoCloseable {
        private final String key;
        private final long start;

        private Timed(String key, long start) {
            this.key = key;
            this.start = start;
        }

        @Override
        public void close() {
            record(key, System.nanoTime() - start);
        }
    }
}
