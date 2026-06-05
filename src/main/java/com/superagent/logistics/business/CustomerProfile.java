package com.superagent.logistics.business;

public record CustomerProfile(
        String customerId,
        String customerName,
        String industry,
        String customerLevel,
        String serviceOwner,
        String salesOwner,
        String contactName,
        String contactPhone,
        String region,
        String riskLevel,
        int monthlyVolume,
        String status
) {
}
