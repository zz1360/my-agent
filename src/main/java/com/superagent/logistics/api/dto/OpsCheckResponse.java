package com.superagent.logistics.api.dto;

import java.util.Map;

public record OpsCheckResponse(
        String name,
        String status,
        String summary,
        Map<String, Object> details
) {
}
