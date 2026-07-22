package pl.peterwolf.cinewolf.debug;

import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.project.MontageDebugExport;
import pl.peterwolf.cinewolf.montage.project.MontageProject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Extended debug snapshot for 1.3.5: event strength, false-positive hints, visibility/collision notes. */
public record CineWolfDebugExport(
        int schemaVersion,
        UUID projectId,
        UUID replayId,
        long exportedAtEpochMillis,
        String cineWolfVersion,
        MontageDebugExport base,
        List<EventDiagnostic> eventDiagnostics,
        List<String> visibilityDiagnostics,
        List<String> collisionDiagnostics,
        List<FalsePositiveHint> falsePositiveHints,
        boolean redacted
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public CineWolfDebugExport {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported debug schema " + schemaVersion);
        }
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(replayId, "replayId");
        cineWolfVersion = Objects.requireNonNullElse(cineWolfVersion, CineWolfAutoDirector.VERSION);
        Objects.requireNonNull(base, "base");
        eventDiagnostics = List.copyOf(Objects.requireNonNullElse(eventDiagnostics, List.of()));
        visibilityDiagnostics = List.copyOf(Objects.requireNonNullElse(visibilityDiagnostics, List.of()));
        collisionDiagnostics = List.copyOf(Objects.requireNonNullElse(collisionDiagnostics, List.of()));
        falsePositiveHints = List.copyOf(Objects.requireNonNullElse(falsePositiveHints, List.of()));
    }

    public static CineWolfDebugExport capture(MontageProject project, ReplayAnalysisResult analysis,
                                              MontagePlan plan, List<String> visibilityDiagnostics,
                                              List<String> collisionDiagnostics, DebugRedactionPolicy redaction) {
        MontageDebugExport base = MontageDebugExport.capture(project, analysis, plan, Instant.now());
        List<EventDiagnostic> diagnostics = new ArrayList<>();
        List<FalsePositiveHint> hints = new ArrayList<>();
        for (ScoredReplayEvent scored : analysis.rankedEvents()) {
            double confidence = scored.event().confidence();
            String strength = EventDiagnostic.strengthFor(confidence);
            List<FalsePositiveHint> eventHints = new ArrayList<>();
            if (confidence < 0.45) {
                eventHints.add(FalsePositiveHint.weak("event.weak_confidence",
                        "Weak detection for " + scored.event().type() + " (confidence "
                                + String.format(java.util.Locale.ROOT, "%.2f", confidence) + ")",
                        confidence));
            } else if (confidence < 0.75) {
                eventHints.add(FalsePositiveHint.probable("event.probable_confidence",
                        "Probable detection for " + scored.event().type(), confidence));
            }
            if (scored.repetitionPenalty() > 0.25) {
                eventHints.add(FalsePositiveHint.probable("event.repetition",
                        "High repetition penalty may indicate a false-positive cluster",
                        confidence));
            }
            hints.addAll(eventHints);
            diagnostics.add(new EventDiagnostic(scored.event().eventId(), scored.event().type(), confidence,
                    scored.finalScore(), strength, scored.scoringReasons(), eventHints));
        }
        DebugRedactionPolicy policy = redaction == null ? DebugRedactionPolicy.defaults() : redaction;
        List<String> visibility = visibilityDiagnostics == null ? List.of()
                : visibilityDiagnostics.stream().map(policy::redactText).toList();
        List<String> collision = collisionDiagnostics == null ? List.of()
                : collisionDiagnostics.stream().map(policy::redactText).toList();
        return new CineWolfDebugExport(CURRENT_SCHEMA_VERSION, project.projectId(), project.replayId(),
                Instant.now().toEpochMilli(), CineWolfAutoDirector.VERSION, base, diagnostics, visibility,
                collision, hints, true);
    }
}
