package pl.peterwolf.cinewolf.montage.analysis;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Picks another entity to carry the 3rd-person camera (witness POV).
 * Prefers player-like entities; falls back to any other entity with coverage.
 */
public final class CameraHostPicker {
    private CameraHostPicker() {
    }

    public static Optional<TargetReference> pick(ReplayAnalysisResult analysis, TargetReference subject,
                                                 long startTick, long endTick) {
        if (analysis == null || subject == null || endTick <= startTick) return Optional.empty();
        UUID subjectId = subject.uuid();
        Map<UUID, Candidate> candidates = new HashMap<>();
        for (ReplaySample sample : analysis.samples()) {
            if (sample.replayTime() < startTick || sample.replayTime() > endTick) continue;
            Vec3d subjectPos = null;
            for (ReplayEntitySnapshot snap : sample.entities().values()) {
                if (snap != null && snap.target() != null && subjectId.equals(snap.target().uuid())
                        && snap.pose() != null && snap.pose().isFinite()) {
                    subjectPos = snap.pose().position();
                    break;
                }
            }
            for (ReplayEntitySnapshot entity : sample.entities().values()) {
                if (entity == null || entity.target() == null) continue;
                TargetReference ref = entity.target();
                if (subjectId.equals(ref.uuid())) continue;
                if (entity.pose() == null || !entity.pose().isFinite()) continue;
                Candidate candidate = candidates.computeIfAbsent(ref.uuid(), id -> new Candidate(ref));
                candidate.samples++;
                if (isPlayerLike(ref)) candidate.playerLike = true;
                if (subjectPos != null && subjectPos.isFinite()) {
                    candidate.distanceSum += entity.pose().position().distanceTo(subjectPos);
                    candidate.distanceCount++;
                }
            }
        }
        // Prefer a player-like host with any coverage; otherwise any entity with ≥2 samples.
        Optional<TargetReference> playerHost = best(candidates.values().stream()
                .filter(c -> c.playerLike && c.samples >= 1)
                .toList());
        if (playerHost.isPresent()) return playerHost;
        return best(candidates.values().stream()
                .filter(c -> c.samples >= 2)
                .toList());
    }

    private static Optional<TargetReference> best(Collection<Candidate> candidates) {
        return candidates.stream()
                .min((a, b) -> {
                    int byCoverage = Integer.compare(b.samples, a.samples);
                    if (byCoverage != 0) return byCoverage;
                    double da = a.averageDistance();
                    double db = b.averageDistance();
                    int byDistance = Double.compare(da, db);
                    if (byDistance != 0) return byDistance;
                    return a.ref.uuid().compareTo(b.ref.uuid());
                })
                .map(c -> c.ref);
    }

    public static boolean isPlayerLike(TargetReference ref) {
        if (ref == null || ref.entityType() == null) return false;
        String type = ref.entityType().toLowerCase(Locale.ROOT);
        return type.contains("player")
                || type.contains("humanoid")
                || type.endsWith(":mannequin")
                || type.contains("npc");
    }

    private static final class Candidate {
        private final TargetReference ref;
        private int samples;
        private double distanceSum;
        private int distanceCount;
        private boolean playerLike;

        private Candidate(TargetReference ref) {
            this.ref = ref;
        }

        private double averageDistance() {
            return distanceCount == 0 ? Double.POSITIVE_INFINITY : distanceSum / distanceCount;
        }
    }
}
