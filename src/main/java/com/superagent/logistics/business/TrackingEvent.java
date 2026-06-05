package com.superagent.logistics.business;

import java.time.LocalDateTime;

public record TrackingEvent(
        String eventId,
        String waybillId,
        LocalDateTime eventTime,
        String nodeName,
        String city,
        String status,
        String description
) {
}
