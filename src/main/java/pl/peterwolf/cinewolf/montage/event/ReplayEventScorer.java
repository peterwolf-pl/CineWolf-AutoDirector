package pl.peterwolf.cinewolf.montage.event;

public interface ReplayEventScorer {
    ScoredReplayEvent score(ReplayEvent event, EventScoringContext context);
}
