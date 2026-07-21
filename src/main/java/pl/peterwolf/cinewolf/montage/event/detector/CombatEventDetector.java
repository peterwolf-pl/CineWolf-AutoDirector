package pl.peterwolf.cinewolf.montage.event.detector;

import pl.peterwolf.cinewolf.model.TargetReference;
import pl.peterwolf.cinewolf.model.Vec3d;
import pl.peterwolf.cinewolf.montage.analysis.ObservedReplayAction;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisContext;
import pl.peterwolf.cinewolf.montage.analysis.ReplayEntitySnapshot;
import pl.peterwolf.cinewolf.montage.analysis.ReplaySample;
import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;
import pl.peterwolf.cinewolf.montage.event.EventEvidence;
import pl.peterwolf.cinewolf.montage.event.ReplayEvent;
import pl.peterwolf.cinewolf.montage.event.ReplayEventDetector;
import pl.peterwolf.cinewolf.montage.event.ReplayEventType;
import pl.peterwolf.cinewolf.montage.event.ReplaySampleWindow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CombatEventDetector implements ReplayEventDetector {
    private static final Set<ReplayEventType> TYPES = Set.copyOf(EnumSet.of(
            ReplayEventType.COMBAT, ReplayEventType.DAMAGE, ReplayEventType.DEATH));

    @Override
    public Set<ReplayEventType> supportedTypes() {
        return TYPES;
    }

    @Override
    public List<ReplayEvent> detect(ReplaySampleWindow window, ReplayAnalysisContext context, double sensitivity) {
        List<ReplayEvent> events = new ArrayList<>();
        DetectorThresholds thresholds = context.detectorThresholds();
        detectDirectActions(window, events);
        detectEntityState(window, thresholds, events);
        events.sort(Comparator.comparingLong(ReplayEvent::startReplayTime).thenComparing(ReplayEvent::type));
        return List.copyOf(events);
    }

    private static void detectDirectActions(ReplaySampleWindow window, List<ReplayEvent> events) {
        for (ReplaySample sample : window.samples()) {
            for (ObservedReplayAction action : sample.actions()) {
                if (action instanceof ObservedReplayAction.CombatSignal signal) {
                    Set<TargetReference> targets = union(signal.attacker(), signal.victim());
                    if (!window.targetFilter().isEmpty() && targets.stream().noneMatch(window::includes)) continue;
                    switch (signal.signalType()) {
                        // A hand-swing packet alone also covers mining and air swings. Treat it as direct
                        // combat evidence only when the replay signal names a victim; victimless swings are
                        // handled conservatively by the entity-state proximity inference below.
                        case ATTACK -> {
                            if (signal.victim().isPresent()) {
                                events.add(direct(signal.replayTime(), ReplayEventType.COMBAT, targets,
                                        signal.location(), Math.max(0.35, signal.magnitude()), 0.78,
                                        "combat_signal", "attack"));
                            }
                        }
                        case DAMAGE -> {
                            events.add(direct(signal.replayTime(), ReplayEventType.DAMAGE, targets,
                                    signal.location(), Math.max(0.4, signal.magnitude()), 0.97,
                                    "combat_signal", "damage"));
                            if (signal.attacker().isPresent()) {
                                events.add(direct(signal.replayTime(), ReplayEventType.COMBAT, targets,
                                        signal.location(), Math.max(0.5, signal.magnitude()), 0.9,
                                        "combat_signal", "damage"));
                            }
                        }
                        case DEATH -> {
                            events.add(direct(signal.replayTime(), ReplayEventType.DEATH, targets,
                                    signal.location(), 1.0, 0.99, "combat_signal", "death"));
                            if (signal.attacker().isPresent()) {
                                events.add(direct(signal.replayTime(), ReplayEventType.COMBAT, targets,
                                        signal.location(), Math.max(0.8, signal.magnitude()), 0.92,
                                        "combat_signal", "death"));
                            }
                        }
                    }
                } else if (action instanceof ObservedReplayAction.ProjectileSignal signal
                        && signal.signalType() == ObservedReplayAction.ProjectileSignalType.HIT) {
                    Set<TargetReference> targets = union(signal.owner(), signal.target());
                    if (targets.isEmpty() || (!window.targetFilter().isEmpty()
                            && targets.stream().noneMatch(window::includes))) continue;
                    EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DIRECT_ACTION,
                            EventEvidence.Measurement.observed("projectile_hit", 1.0, "boolean"))
                            .withAttribute("projectile_id", signal.projectileId().toString());
                    events.add(ReplayEvent.create(ReplayEventType.COMBAT, signal.replayTime(), signal.replayTime(),
                            signal.replayTime(), targets, signal.location(), 0.65, 0.95, evidence));
                }
            }
        }
    }

    private static void detectEntityState(ReplaySampleWindow window, DetectorThresholds thresholds,
                                          List<ReplayEvent> events) {
        List<ReplaySample> samples = window.samples().stream()
                .sorted(Comparator.comparingLong(ReplaySample::replayTime)).toList();
        for (int index = 0; index < samples.size(); index++) {
            ReplaySample current = samples.get(index);
            ReplaySample previous = index == 0 ? null : samples.get(index - 1);
            for (Map.Entry<TargetReference, ReplayEntitySnapshot> entry : current.entities().entrySet()) {
                TargetReference target = entry.getKey();
                if (!window.includes(target)) continue;
                ReplayEntitySnapshot snapshot = entry.getValue();
                ReplayEntitySnapshot before = previous == null ? null : previous.entities().get(target);
                if (before != null) detectDamageAndDeath(current.replayTime(), target, before, snapshot,
                        thresholds, events);
                if (snapshot.attacking() || snapshot.swinging()) {
                    nearestVictim(current, target, snapshot, thresholds.combatProximity()).ifPresent(nearby -> {
                        // A swing plus proximity supports combat activity, but does not establish the nearby
                        // entity as the victim. Only direct damage/projectile signals may add that target.
                        Set<TargetReference> targets = Set.of(target);
                        Vec3d location = snapshot.pose().position();
                        EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.ENTITY_STATE,
                                EventEvidence.Measurement.atMost("target_distance",
                                        snapshot.pose().position().distanceTo(nearby.pose().position()), "blocks",
                                        thresholds.combatProximity()),
                                EventEvidence.Measurement.observed("attack_animation", snapshot.attacking() ? 1.0 : 0.0,
                                        "boolean"),
                                EventEvidence.Measurement.observed("swing_animation", snapshot.swinging() ? 1.0 : 0.0,
                                        "boolean"));
                        events.add(ReplayEvent.create(ReplayEventType.COMBAT, current.replayTime(), current.replayTime(),
                                current.replayTime(), targets, location, 0.35, 0.42, evidence));
                    });
                }
            }
        }
    }

    private static void detectDamageAndDeath(long replayTime, TargetReference target,
                                             ReplayEntitySnapshot before, ReplayEntitySnapshot current,
                                             DetectorThresholds thresholds, List<ReplayEvent> events) {
        boolean deathTransition = before.alive() && !current.alive();
        if (deathTransition) {
            EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.ENTITY_STATE,
                    EventEvidence.Measurement.observed("alive_transition", 1.0, "boolean"));
            events.add(ReplayEvent.create(ReplayEventType.DEATH, replayTime, replayTime, replayTime,
                    Set.of(target), current.pose().position(), 1.0, 0.98, evidence));
        }
        double damageFraction = 0.0;
        boolean healthDrop = before.healthKnown() && current.healthKnown()
                && before.health() > current.health();
        if (healthDrop) damageFraction = (before.health() - current.health()) / Math.max(1.0, before.maximumHealth());
        boolean hurtTransition = current.hurtTime() > 0 && current.hurtTime() > before.hurtTime();
        // A confirmed alive -> dead transition is represented by DEATH only. Emitting DAMAGE for the
        // same state transition would double-count one observation during scoring and scene selection.
        if (!deathTransition && (damageFraction >= thresholds.damageHealthDrop() || hurtTransition)) {
            double magnitude = healthDrop ? Math.max(0.1, Math.min(1.0, damageFraction * 4.0)) : 0.2;
            double confidence = healthDrop ? 0.95 : 0.75;
            EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.ENTITY_STATE,
                    EventEvidence.Measurement.atLeast("health_drop_fraction", damageFraction, "ratio",
                            thresholds.damageHealthDrop()),
                    EventEvidence.Measurement.observed("hurt_time", current.hurtTime(), "ticks"));
            events.add(ReplayEvent.create(ReplayEventType.DAMAGE, replayTime, replayTime, replayTime,
                    Set.of(target), current.pose().position(), magnitude, confidence, evidence));
        }
    }

    private static Optional<ReplayEntitySnapshot> nearestVictim(ReplaySample sample, TargetReference attacker,
                                                                ReplayEntitySnapshot attackerSnapshot,
                                                                double maximumDistance) {
        return sample.entities().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(attacker) && entry.getValue().alive())
                .filter(entry -> attackerSnapshot.pose().position().distanceTo(entry.getValue().pose().position())
                        <= maximumDistance)
                .min(Comparator.comparingDouble(entry -> attackerSnapshot.pose().position()
                        .distanceTo(entry.getValue().pose().position())))
                .map(Map.Entry::getValue);
    }

    private static ReplayEvent direct(long time, ReplayEventType type, Set<TargetReference> targets,
                                      Vec3d location, double magnitude, double confidence,
                                      String attributeName, String attributeValue) {
        EventEvidence evidence = EventEvidence.of(EventEvidence.DetectionSource.DIRECT_ACTION,
                EventEvidence.Measurement.observed("signal_magnitude", magnitude, "ratio"),
                EventEvidence.Measurement.observed("known_participants", targets.size(), "entities"))
                .withAttribute(attributeName, attributeValue);
        return ReplayEvent.create(type, time, time, time, targets, location,
                Math.min(1.0, magnitude), confidence, evidence);
    }

    private static Set<TargetReference> union(Optional<TargetReference> first, Optional<TargetReference> second) {
        Set<TargetReference> result = new HashSet<>();
        first.ifPresent(result::add);
        second.ifPresent(result::add);
        return Set.copyOf(result);
    }
}
