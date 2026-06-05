package com.superagent.logistics.api.dto;

import java.util.List;

public record RiskAttribution(
        String dimension,
        String name,
        long count,
        double ratio,
        List<String> evidenceWaybillIds,
        String interpretation
) {
}
