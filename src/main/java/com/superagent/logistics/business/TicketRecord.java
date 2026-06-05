package com.superagent.logistics.business;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketRecord(
        String ticketId,
        String customerId,
        String waybillId,
        LocalDateTime createdAt,
        String ticketType,
        String priority,
        String status,
        String ownerTeam,
        String summary,
        String resolution,
        BigDecimal compensationAmount
) {
}
