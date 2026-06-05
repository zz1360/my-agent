package com.superagent.logistics.business;

public record SlaRule(
        String slaId,
        String customerLevel,
        String serviceType,
        int promiseHours,
        String delayCompensationRule,
        String tempRange,
        String notes
) {
}
