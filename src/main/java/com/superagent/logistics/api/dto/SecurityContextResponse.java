package com.superagent.logistics.api.dto;

import java.util.List;

public record SecurityContextResponse(
        String tenantId,
        String userId,
        List<String> roles,
        boolean authenticated,
        boolean apiKeyRequired,
        String authenticationType
) {
}
