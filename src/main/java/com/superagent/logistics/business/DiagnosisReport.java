package com.superagent.logistics.business;

public record DiagnosisReport(
        String customerId,
        String windowLabel,
        int waybillCount,
        int exceptionCount,
        int ticketCount,
        double exceptionRate,
        double complaintRate,
        String mainRisk,
        String recommendation
) {
}
