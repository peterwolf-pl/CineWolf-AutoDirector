package pl.peterwolf.cinewolf.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pl.peterwolf.cinewolf.CineWolfAutoDirector;
import pl.peterwolf.cinewolf.api.v2.IntegrationDiagnostic;
import pl.peterwolf.cinewolf.debug.DebugRedactionPolicy;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.event.ScoredReplayEvent;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.project.v2.CineWolfProjectV2;
import pl.peterwolf.cinewolf.project.v2.ReplayIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class CineWolfDebugExporterV2 {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final DebugRedactionPolicy redactionPolicy;

    public CineWolfDebugExporterV2() {
        this(DebugRedactionPolicy.defaults());
    }

    public CineWolfDebugExporterV2(DebugRedactionPolicy redactionPolicy) {
        this.redactionPolicy = redactionPolicy == null ? DebugRedactionPolicy.defaults() : redactionPolicy;
    }

    public CineWolfDebugExportV2 capture(
            CineWolfProjectV2 project,
            ReplayAnalysisResult analysis,
            MontagePlan plan,
            List<IntegrationDiagnostic> integrations,
            List<TimelineWriteDiagnostic> timelineWrites,
            List<String> visibility,
            List<String> collisions,
            Map<String, Long> performance,
            String flashbackVersion
    ) {
        Objects.requireNonNull(project, "project");
        ReplayIdentity identity = redactIdentity(project.replayIdentity());
        List<EventDiagnostic> events = new ArrayList<>();
        if (analysis != null) {
            for (ScoredReplayEvent scored : analysis.rankedEvents()) {
                double confidence = scored.event().confidence();
                String strength = confidence < 0.35 ? "weak" : confidence < 0.65 ? "probable" : "strong";
                List<String> hints = FalsePositiveHints.forEvent(scored.event().type().name(), confidence);
                events.add(new EventDiagnostic(
                        scored.event().eventId().toString(),
                        "detector:" + scored.event().type().name().toLowerCase(),
                        scored.event().type().name(),
                        "analysis",
                        confidence,
                        scored.finalScore(),
                        strength,
                        List.of("score=" + scored.finalScore()),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        hints
                ));
            }
        }
        Map<String, Object> montage = new LinkedHashMap<>();
        if (plan != null) {
            montage.put("shotCount", plan.shots().size());
            montage.put("outputDurationSeconds", plan.outputDurationSeconds());
            montage.put("sourceStart", plan.sourceStartReplayTime());
            montage.put("sourceEnd", plan.sourceEndReplayTime());
            montage.put("warnings", plan.warnings().stream().map(w -> w.code()).toList());
        }
        List<IntegrationDiagnosticRecord> integrationRecords = integrations == null
                ? List.of()
                : integrations.stream().map(IntegrationDiagnosticRecord::from).toList();
        List<String> warnings = new ArrayList<>();
        project.warnings().forEach(w -> warnings.add(redactionPolicy.redactText(w.code())));
        return new CineWolfDebugExportV2(
                CineWolfDebugExportV2.SCHEMA,
                CineWolfAutoDirector.VERSION,
                "26.2",
                "unknown",
                flashbackVersion,
                identity,
                Map.of("projectName", redactionPolicy.redactText(project.projectName())),
                Map.of(),
                List.of(),
                List.of(),
                events,
                List.of(),
                montage,
                List.of(),
                collisions == null ? List.of() : collisions.stream().map(c -> Map.<String, Object>of("message", redactionPolicy.redactText(c))).toList(),
                visibility == null ? List.of() : visibility.stream().map(v -> Map.<String, Object>of("message", redactionPolicy.redactText(v))).toList(),
                timelineWrites == null ? List.of() : timelineWrites,
                integrationRecords,
                warnings,
                performance == null ? Map.of() : performance
        );
    }

    public Path export(Path directory, CineWolfDebugExportV2 export) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(export, "export");
        Files.createDirectories(directory);
        Path target = directory.resolve("cinewolf-debug-v2.json");
        Path temporary = Files.createTempFile(directory, ".debug-v2-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(export), StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }

    private ReplayIdentity redactIdentity(ReplayIdentity identity) {
        if (identity == null) {
            return new ReplayIdentity("redacted", "redacted", 0, java.time.Instant.EPOCH, "", "");
        }
        return new ReplayIdentity(
                identity.stableId(),
                redactionPolicy.redactText(identity.displayName()),
                identity.replayDuration(),
                identity.replayCreatedAt(),
                identity.metadataFingerprint(),
                identity.fileFingerprint()
        );
    }
}
