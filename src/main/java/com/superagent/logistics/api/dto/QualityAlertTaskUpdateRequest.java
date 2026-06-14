package com.superagent.logistics.api.dto;

import java.util.List;

public record QualityAlertTaskUpdateRequest(
        String tenantId,
        String userId,
        List<String> roles,
        String status,
        String ownerUserId,
        String comment
) {
}
