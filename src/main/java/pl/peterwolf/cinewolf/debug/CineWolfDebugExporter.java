package pl.peterwolf.cinewolf.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;
import pl.peterwolf.cinewolf.montage.plan.MontagePlan;
import pl.peterwolf.cinewolf.montage.project.MontageProject;
import pl.peterwolf.cinewolf.montage.project.MontageProjectManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

public final class CineWolfDebugExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final MontageProjectManager projectManager;
    private final DebugRedactionPolicy redactionPolicy;

    public CineWolfDebugExporter(MontageProjectManager projectManager) {
        this(projectManager, DebugRedactionPolicy.defaults());
    }

    public CineWolfDebugExporter(MontageProjectManager projectManager, DebugRedactionPolicy redactionPolicy) {
        this.projectManager = Objects.requireNonNull(projectManager, "projectManager");
        this.redactionPolicy = redactionPolicy == null ? DebugRedactionPolicy.defaults() : redactionPolicy;
    }

    public Path export(MontageProject project, ReplayAnalysisResult analysis, MontagePlan plan,
                       List<String> visibilityDiagnostics, List<String> collisionDiagnostics) throws IOException {
        CineWolfDebugExport export = CineWolfDebugExport.capture(project, analysis, plan,
                visibilityDiagnostics, collisionDiagnostics, redactionPolicy);
        Path target = projectManager.projectDirectory().resolve(project.projectId() + "-debug-v2.json");
        Files.createDirectories(projectManager.projectDirectory());
        Path temporary = Files.createTempFile(projectManager.projectDirectory(), ".debug-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(export), StandardCharsets.UTF_8, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
