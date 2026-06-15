package com.superagent.logistics.api.dto;

import java.util.List;
import java.util.Map;

public record BusinessDataSourceResponse(
        String tenantId,
        String adapter,
        String sourceType,
        String databaseProduct,
        boolean simulatedData,
        String isolationStrategy,
        List<String> domains,
        Map<String, Integer> tableRows
) {
}
