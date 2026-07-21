package pl.peterwolf.cinewolf.montage.plan;

import pl.peterwolf.cinewolf.montage.analysis.ReplayAnalysisResult;

public interface MontagePlanner {
    MontagePlan createPlan(ReplayAnalysisResult analysis, MontageRequest request, MontagePlanningContext context);
}
