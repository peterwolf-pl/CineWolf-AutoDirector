package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class CoarseDetailedSampleSelector {
    private static final long DETAIL_PADDING_TICKS = 16L;
    /** Prefer short, high-signal pockets; avoid detailing entire long continuous action. */
    private static final long MAX_SINGLE_WINDOW_TICKS = 200L; // 10 s
    private static final double DEFAULT_COVERAGE_FRACTION = 0.35;

    public SampleSelection select(List<ReplaySample> availableSamples, ReplayAnalysisRequest request,
                                  DetectorThresholds thresholds) {
        return select(availableSamples, request, thresholds, DEFAULT_COVERAGE_FRACTION);
    }

    public SampleSelection select(List<ReplaySample> availableSamples, ReplayAnalysisRequest request,
                                  DetectorThresholds thresholds, double maximumCoverageFraction) {
        List<ReplaySample> inRange = inRange(availableSamples, request.startReplayTime(), request.endReplayTime());
        if (inRange.isEmpty()) return new SampleSelection(List.of(), List.of(), List.of(), List.of());
        List<ReplaySample> coarse = rateLimit(inRange, request.coarseSamplesPerSecond(), true);
        List<ReplayTimeWindow> windows = prioritizeWindows(
                candidateWindows(coarse, thresholds, request.startReplayTime(), request.endReplayTime()),
                coarse, thresholds, request.startReplayTime(), request.endReplayTime(), maximumCoverageFraction);
        List<ReplaySample> detailCandidates = inRange.stream()
                .filter(sample -> windows.stream().anyMatch(window -> window.contains(sample.replayTime())))
                .toList();
        List<ReplaySample> detailed = rateLimit(detailCandidates, request.detailedSamplesPerSecond(), true);
        TreeMap<Long, ReplaySample> combined = new TreeMap<>();
        coarse.forEach(sample -> combined.put(sample.replayTime(), sample));
        detailed.forEach(sample -> combined.put(sample.replayTime(), sample));
        inRange.stream().filter(CoarseDetailedSampleSelector::hasSignals)
                .forEach(sample -> combined.put(sample.replayTime(), sample));
        return new SampleSelection(coarse, detailed, List.copyOf(combined.values()), windows);
    }

    public List<ReplaySample> selectCoarse(List<ReplaySample> samples, long start, long end, int samplesPerSecond) {
        return rateLimit(inRange(samples, start, end), samplesPerSecond, true);
    }

    public List<ReplaySample> selectDetailed(List<ReplaySample> samples, List<ReplayTimeWindow> windows,
                                             int samplesPerSecond) {
        List<ReplaySample> candidates = samples.stream()
                .filter(sample -> windows.stream().anyMatch(window -> window.contains(sample.replayTime())))
                .toList();
        return rateLimit(candidates, samplesPerSecond, true);
    }

    private static List<ReplaySample> inRange(List<ReplaySample> samples, long start, long end) {
        TreeMap<Long, ReplaySample> unique = new TreeMap<>();
        samples.stream().filter(sample -> sample.replayTime() >= start && sample.replayTime() <= end)
                .forEach(sample -> unique.put(sample.replayTime(), sample));
        return List.copyOf(unique.values());
    }

    private static List<ReplaySample> rateLimit(List<ReplaySample> samples, int samplesPerSecond,
                                                 boolean preserveSignals) {
        if (samples.isEmpty()) return List.of();
        long intervalTicks = Math.max(1L, Math.round(20.0 / Math.max(1, samplesPerSecond)));
        LinkedHashSet<ReplaySample> selected = new LinkedHashSet<>();
        long lastRegularTick = Long.MIN_VALUE;
        for (ReplaySample sample : samples) {
            if (lastRegularTick == Long.MIN_VALUE || sample.replayTime() - lastRegularTick >= intervalTicks) {
                selected.add(sample);
                lastRegularTick = sample.replayTime();
            } else if (preserveSignals && hasSignals(sample)) {
                selected.add(sample);
            }
        }
        selected.add(samples.getLast());
        return selected.stream().sorted(Comparator.comparingLong(ReplaySample::replayTime)).toList();
    }

    private static List<ReplayTimeWindow> candidateWindows(List<ReplaySample> samples, DetectorThresholds thresholds,
                                                            long rangeStart, long rangeEnd) {
        List<ReplayTimeWindow> candidates = new ArrayList<>();
        for (int index = 0; index < samples.size(); index++) {
            ReplaySample current = samples.get(index);
            boolean active = hasSignals(current);
            if (index > 0) active |= changed(samples.get(index - 1), current, thresholds);
            if (active) candidates.add(new ReplayTimeWindow(Math.max(rangeStart, current.replayTime() - DETAIL_PADDING_TICKS),
                    Math.min(rangeEnd, current.replayTime() + DETAIL_PADDING_TICKS)));
        }
        if (candidates.isEmpty()) return List.of();
        candidates.sort(Comparator.comparingLong(ReplayTimeWindow::startReplayTime));
        List<ReplayTimeWindow> merged = new ArrayList<>();
        ReplayTimeWindow current = candidates.getFirst();
        for (int index = 1; index < candidates.size(); index++) {
            ReplayTimeWindow next = candidates.get(index);
            long mergedEnd = Math.max(current.endReplayTime(), next.endReplayTime());
            boolean adjacent = next.startReplayTime() <= current.endReplayTime() + 1;
            boolean wouldExceedCap = mergedEnd - current.startReplayTime() > MAX_SINGLE_WINDOW_TICKS;
            if (adjacent && !wouldExceedCap) {
                current = new ReplayTimeWindow(current.startReplayTime(), mergedEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    /**
     * Keeps the highest-signal windows first until the coverage budget is spent.
     * Long continuous activity no longer forces detailing of the whole range.
     */
    public static List<ReplayTimeWindow> prioritizeWindows(List<ReplayTimeWindow> windows, List<ReplaySample> coarse,
                                                           DetectorThresholds thresholds, long rangeStart, long rangeEnd,
                                                           double coverageFraction) {
        if (windows == null || windows.isEmpty()) return List.of();
        long rangeTicks = Math.max(1L, rangeEnd - rangeStart);
        double fraction = Double.isFinite(coverageFraction) ? Math.max(0.05, Math.min(1.0, coverageFraction)) : 0.35;
        long budget = Math.max(MAX_SINGLE_WINDOW_TICKS,
                Math.round(rangeTicks * fraction));

        List<ScoredWindow> scored = new ArrayList<>();
        for (ReplayTimeWindow window : windows) {
            double score = scoreWindow(window, coarse, thresholds);
            scored.add(new ScoredWindow(window, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredWindow::score).reversed()
                .thenComparingLong(value -> value.window().startReplayTime()));

        List<ReplayTimeWindow> selected = new ArrayList<>();
        long used = 0L;
        for (ScoredWindow entry : scored) {
            long duration = entry.window().endReplayTime() - entry.window().startReplayTime();
            if (duration <= 0) continue;
            if (used > 0 && used + duration > budget) continue;
            selected.add(entry.window());
            used += duration;
            if (used >= budget) break;
        }
        selected.sort(Comparator.comparingLong(ReplayTimeWindow::startReplayTime));
        return List.copyOf(selected);
    }

    private static double scoreWindow(ReplayTimeWindow window, List<ReplaySample> coarse,
                                      DetectorThresholds thresholds) {
        double score = 1.0;
        long duration = Math.max(1L, window.endReplayTime() - window.startReplayTime());
        int signals = 0;
        int changes = 0;
        ReplaySample previous = null;
        for (ReplaySample sample : coarse) {
            if (!window.contains(sample.replayTime())) continue;
            if (hasSignals(sample)) signals++;
            if (previous != null && changed(previous, sample, thresholds)) changes++;
            previous = sample;
        }
        score += signals * 3.0 + changes * 1.5;
        // Prefer compact pockets over long low-density stretches.
        score += 40.0 / Math.sqrt(duration);
        return score;
    }

    private record ScoredWindow(ReplayTimeWindow window, double score) {
    }

    private static boolean changed(ReplaySample previous, ReplaySample current, DetectorThresholds thresholds) {
        for (Map.Entry<TargetReference, ReplayEntitySnapshot> entry : current.entities().entrySet()) {
            ReplayEntitySnapshot before = previous.entities().get(entry.getKey());
            ReplayEntitySnapshot after = entry.getValue();
            if (before == null || before.alive() != after.alive() || before.inVehicle() != after.inVehicle()
                    || before.explicitFlight() != after.explicitFlight() || before.onGround() != after.onGround()
                    || !before.pose().dimension().equals(after.pose().dimension()) || after.pose().discontinuity()) return true;
            if (before.pose().position().distanceTo(after.pose().position()) >= thresholds.positionChangeDistance()) return true;
        }
        return false;
    }

    private static boolean hasSignals(ReplaySample sample) {
        return !sample.actions().isEmpty() || !sample.markers().isEmpty();
    }
}
