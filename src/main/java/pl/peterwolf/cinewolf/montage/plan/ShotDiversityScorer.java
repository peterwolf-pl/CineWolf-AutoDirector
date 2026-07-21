package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.model.ShotType;
import pl.peterwolf.cinewolf.montage.preset.FramingType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ShotDiversityScorer {
    public double score(List<PlannedMontageShot> shots) {
        return score(shots, ShotDiversityProfile.defaults());
    }

    public double score(List<PlannedMontageShot> shots, ShotDiversityProfile profile) {
        if (shots.isEmpty()) return 0.0;
        profile = profile == null ? ShotDiversityProfile.defaults() : profile;
        double penalties = 0.0;
        double rewards = 0.0;
        Set<ShotType> types = new HashSet<>();
        Set<FramingType> framings = new HashSet<>();
        for (int index = 0; index < shots.size(); index++) {
            PlannedMontageShot current = shots.get(index);
            types.add(current.shotType());
            framings.add(current.framing());
            if (index == 0) continue;
            PlannedMontageShot previous = shots.get(index - 1);
            if (current.shotType() == previous.shotType()) penalties += profile.repeatedShotPenalty();
            else rewards += profile.alternateShotReward();
            if (current.framing() == previous.framing()) penalties += profile.repeatedFramingPenalty();
            else rewards += profile.alternateFramingReward();
            if (current.sourceEvent().type() == previous.sourceEvent().type()) {
                penalties += profile.repeatedEventPenalty();
            }
            if (current.target().equals(previous.target()) && current.shotType() == previous.shotType()) {
                penalties += profile.repeatedTargetAndShotPenalty();
            }
            if (angularDistance(current.shotRequest().startAngleDegrees(),
                    previous.shotRequest().startAngleDegrees()) <= 25.0) {
                penalties += profile.similarAzimuthPenalty();
            }
            if (relativeDifference(representativeDistance(current), representativeDistance(previous)) <= 0.15) {
                penalties += profile.similarDistancePenalty();
            }
            if (relativeDifference(current.shotRequest().height(), previous.shotRequest().height()) <= 0.15) {
                penalties += profile.similarHeightPenalty();
            } else {
                rewards += profile.differentHeightReward();
            }
            if (Double.compare(current.shotRequest().direction().sign(),
                    previous.shotRequest().direction().sign()) == 0) {
                penalties += profile.repeatedMovementDirectionPenalty();
            } else {
                rewards += profile.alternateMovementDirectionReward();
            }
        }
        double coverage = (types.size() / (double) Math.min(5, shots.size()))
                * profile.shotTypeCoverageWeight()
                + (framings.size() / (double) Math.min(5, shots.size()))
                * profile.framingCoverageWeight();
        return Math.max(0.0, Math.min(1.0,
                profile.baseScore() + coverage + rewards / shots.size() - penalties / shots.size()));
    }

    private static double angularDistance(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) return Double.POSITIVE_INFINITY;
        double delta = Math.abs((first - second) % 360.0);
        return Math.min(delta, 360.0 - delta);
    }

    private static double representativeDistance(PlannedMontageShot shot) {
        double direct = shot.shotRequest().distance();
        if (Double.isFinite(direct) && direct > 0.0) return direct;
        double start = shot.shotRequest().startDistance();
        double end = shot.shotRequest().endDistance();
        if (Double.isFinite(start) && start > 0.0 && Double.isFinite(end) && end > 0.0) {
            return (start + end) * 0.5;
        }
        double diameter = shot.shotRequest().diameter();
        return Double.isFinite(diameter) && diameter > 0.0 ? diameter * 0.5 : 0.0;
    }

    private static double relativeDifference(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) return Double.POSITIVE_INFINITY;
        double scale = Math.max(1.0e-6, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) / scale;
    }
}
