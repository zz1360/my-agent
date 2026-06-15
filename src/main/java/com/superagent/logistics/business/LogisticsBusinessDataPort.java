package com.superagent.logistics.business;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LogisticsBusinessDataPort {

    Optional<CustomerProfile> findCustomerProfile(String tenantId, String customerId);

    List<CustomerProfile> findHighRiskCustomers(String tenantId, int limit);

    List<WaybillSummary> findCustomerWaybills(String tenantId, String customerId,
                                              LocalDate from, LocalDate to, String status, int limit);

    Optional<WaybillSummary> findWaybillSummary(String tenantId, String waybillId);

    List<TrackingEvent> findTrackingEvents(String tenantId, String waybillId);

    List<ExceptionEvent> findCustomerExceptions(String tenantId, String customerId,
                                                LocalDate from, LocalDate to, String exceptionType, int limit);

    List<ExceptionEvent> findWaybillExceptions(String tenantId, String waybillId);

    List<TicketRecord> findCustomerTickets(String tenantId, String customerId,
                                           LocalDate from, LocalDate to, int limit);

    List<TicketRecord> findWaybillTickets(String tenantId, String waybillId);

    List<SlaRule> findSlaRules(String tenantId, String customerLevel, String serviceType);

    DiagnosisReport diagnoseCustomer(String tenantId, String customerId, LocalDate from, LocalDate to);
}
