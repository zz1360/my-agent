package com.superagent.logistics.action;

import java.util.Map;

public record ActionExecutionResult(
        String targetSystem,
        String externalRefId,
        Map<String, Object> response
) {
}
