package com.superagent.logistics.business;

import java.time.LocalDateTime;

public record ExceptionEvent(
        String exceptionId,
        String waybillId,
        String customerId,
        LocalDateTime eventTime,
        String exceptionType,
        String severity,
        String responsibilityParty,
        String description,
        boolean resolved,
        int impactHours
) {
}
