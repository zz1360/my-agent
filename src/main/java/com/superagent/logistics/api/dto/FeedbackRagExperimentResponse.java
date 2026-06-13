package com.superagent.logistics.api.dto;

import java.util.List;

public record FeedbackRagExperimentResponse(
        EvalCaseCandidateResponse candidate,
        RagExperimentResponse experiment,
        List<RagExperimentRunResponse> runs
) {
}
