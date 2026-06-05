package com.superagent.logistics.business;

import java.util.List;

public record WaybillDetail(
        WaybillSummary waybill,
        CustomerProfile customer,
        List<TrackingEvent> trackingEvents,
        List<ExceptionEvent> exceptions,
        List<TicketRecord> tickets
) {
}
