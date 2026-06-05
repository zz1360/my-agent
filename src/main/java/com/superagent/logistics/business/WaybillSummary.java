package com.superagent.logistics.business;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WaybillSummary(
        String waybillId,
        String customerId,
        String originCity,
        String destCity,
        String serviceType,
        String cargoType,
        BigDecimal weightKg,
        BigDecimal volumeM3,
        LocalDate orderDate,
        LocalDateTime promisedDeliveryTime,
        LocalDateTime actualDeliveryTime,
        String status,
        BigDecimal freightFee,
        String routeCode
) {
}
