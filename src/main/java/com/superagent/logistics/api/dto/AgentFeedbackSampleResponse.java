package com.superagent.logistics.api.dto;

import java.time.Instant;
import java.util.List;

public record AgentFeedbackSampleResponse(
        String feedbackId,
        String tenantId,
        String conversationId,
        String conversationTitle,
        String messageId,
        String traceId,
        String userId,
        String rating,
        String reason,
        String comment,
        String sourceQuestion,
        String sourceAnswer,
        List<Citation> citations,
        List<ToolCallSummary> toolCalls,
        String candidateId,
        String candidateStatus,
        Instant createdAt
) {
}
