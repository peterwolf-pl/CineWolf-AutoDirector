package pl.peterwolf.cinewolf.montage.plan;

/** Only HARD_CUT is rendered in 1.2; the remaining values are planning metadata. */
public enum MontageTransitionType {
    HARD_CUT,
    MATCH_CUT,
    MOTION_CUT,
    DISSOLVE_METADATA,
    FADE_METADATA
}
