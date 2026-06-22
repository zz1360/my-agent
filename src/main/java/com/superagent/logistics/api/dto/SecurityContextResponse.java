package com.superagent.logistics.api.dto;

import java.util.List;

public record SecurityContextResponse(
        String tenantId,
        String userId,
        List<String> roles,
        List<String> permissions,
        boolean authenticated,
        boolean apiKeyRequired,
        String authenticationType
) {
}
