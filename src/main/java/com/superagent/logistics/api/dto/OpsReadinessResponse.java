package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record OpsReadinessResponse(
        String application,
        List<String> activeProfiles,
        boolean ready,
        List<OpsCheckResponse> checks,
        Instant checkedAt
) {
}
