package com.superagent.logistics.business;

import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class LogisticsQueryService {

    private final LogisticsBusinessDataPort businessDataPort;
    private final AgentPermissionService permissionService;

    public LogisticsQueryService(LogisticsBusinessDataPort businessDataPort, AgentPermissionService permissionService) {
        this.businessDataPort = businessDataPort;
        this.permissionService = permissionService;
    }

    public Optional<CustomerProfile> getCustomerProfile(AgentUserContext context, String customerId) {
        permissionService.checkCustomerReadable(context, customerId);
        return businessDataPort.findCustomerProfile(context.tenantId(), customerId);
    }

    public List<CustomerProfile> queryHighRiskCustomers(AgentUserContext context, int limit) {
        permissionService.checkBusinessReadable(context);
        return businessDataPort.findHighRiskCustomers(context.tenantId(), Math.max(1, Math.min(limit, 30)));
    }

    public List<WaybillSummary> queryCustomerWaybills(AgentUserContext context, String customerId,
                                                      LocalDate from, LocalDate to, String status, int limit) {
        permissionService.checkCustomerReadable(context, customerId);
        return businessDataPort.findCustomerWaybills(context.tenantId(), customerId, from, to, status,
                Math.max(1, Math.min(limit, 50)));
    }

    public Optional<WaybillSummary> getWaybillSummary(AgentUserContext context, String waybillId) {
        permissionService.checkWaybillReadable(context, waybillId);
        Optional<WaybillSummary> row = businessDataPort.findWaybillSummary(context.tenantId(), waybillId);
        row.ifPresent(waybill -> permissionService.checkCustomerReadable(context, waybill.customerId()));
        return row;
    }

    public Optional<WaybillDetail> getWaybillDetail(AgentUserContext context, String waybillId) {
        Optional<WaybillSummary> waybill = getWaybillSummary(context, waybillId);
        if (waybill.isEmpty()) {
            return Optional.empty();
        }
        CustomerProfile customer = getCustomerProfile(context, waybill.get().customerId()).orElse(null);
        return Optional.of(new WaybillDetail(
                waybill.get(),
                customer,
                queryTrackingEvents(context, waybillId),
                queryWaybillExceptions(context, waybillId),
                queryWaybillTickets(context, waybillId)
        ));
    }

    public List<TrackingEvent> queryTrackingEvents(AgentUserContext context, String waybillId) {
        getWaybillSummary(context, waybillId);
        return businessDataPort.findTrackingEvents(context.tenantId(), waybillId);
    }

    public List<ExceptionEvent> queryCustomerExceptions(AgentUserContext context, String customerId,
                                                        LocalDate from, LocalDate to, String exceptionType, int limit) {
        permissionService.checkCustomerReadable(context, customerId);
        return businessDataPort.findCustomerExceptions(context.tenantId(), customerId, from, to, exceptionType,
                Math.max(1, Math.min(limit, 80)));
    }

    public List<ExceptionEvent> queryWaybillExceptions(AgentUserContext context, String waybillId) {
        Optional<WaybillSummary> waybill = getWaybillSummary(context, waybillId);
        if (waybill.isEmpty()) {
            return List.of();
        }
        return businessDataPort.findWaybillExceptions(context.tenantId(), waybillId);
    }

    public List<TicketRecord> queryCustomerTickets(AgentUserContext context, String customerId,
                                                   LocalDate from, LocalDate to, int limit) {
        permissionService.checkCustomerReadable(context, customerId);
        return businessDataPort.findCustomerTickets(context.tenantId(), customerId, from, to,
                Math.max(1, Math.min(limit, 80)));
    }

    public List<TicketRecord> queryWaybillTickets(AgentUserContext context, String waybillId) {
        Optional<WaybillSummary> waybill = getWaybillSummary(context, waybillId);
        if (waybill.isEmpty()) {
            return List.of();
        }
        return businessDataPort.findWaybillTickets(context.tenantId(), waybillId);
    }

    public List<SlaRule> querySlaRules(AgentUserContext context, String customerLevel, String serviceType) {
        permissionService.checkBusinessReadable(context);
        return businessDataPort.findSlaRules(context.tenantId(), customerLevel, serviceType);
    }

    public DiagnosisReport diagnoseCustomer(AgentUserContext context, String customerId, LocalDate from, LocalDate to) {
        permissionService.checkCustomerReadable(context, customerId);
        return businessDataPort.diagnoseCustomer(context.tenantId(), customerId, from, to);
    }
}
