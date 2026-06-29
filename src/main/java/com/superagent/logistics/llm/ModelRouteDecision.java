package com.superagent.logistics.llm;

import java.util.List;

public record ModelRouteDecision(
        String routeKey,
        List<ModelCandidate> candidates
) {
}
