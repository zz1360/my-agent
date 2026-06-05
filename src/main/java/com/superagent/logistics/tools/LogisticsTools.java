package com.superagent.logistics.tools;

import com.superagent.logistics.business.CustomerProfile;
import com.superagent.logistics.business.DiagnosisReport;
import com.superagent.logistics.business.ExceptionEvent;
import com.superagent.logistics.business.LogisticsQueryService;
import com.superagent.logistics.business.SlaRule;
import com.superagent.logistics.business.TicketRecord;
import com.superagent.logistics.business.TrackingEvent;
import com.superagent.logistics.business.WaybillDetail;
import com.superagent.logistics.business.WaybillSummary;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LogisticsTools {

    private final LogisticsQueryService queryService;

    public LogisticsTools(LogisticsQueryService queryService) {
        this.queryService = queryService;
    }

    @Tool(description = "查询物流客户基础画像，包括客户名称、行业、等级、风险等级、负责人、月单量和状态")
    public CustomerProfile getCustomerProfile(String customerId, ToolContext toolContext) {
        return queryService.getCustomerProfile(toUserContext(toolContext), customerId).orElse(null);
    }

    @Tool(description = "查询客户在指定日期范围内的运单列表，可按运单状态过滤")
    public List<WaybillSummary> queryCustomerWaybills(String customerId, LocalDate from, LocalDate to,
                                                      String status, Integer limit, ToolContext toolContext) {
        return queryService.queryCustomerWaybills(toUserContext(toolContext), customerId, from, to, status, limit == null ? 10 : limit);
    }

    @Tool(description = "查询单个运单详情，包括客户、运单、轨迹、异常和相关工单")
    public WaybillDetail getWaybillDetail(String waybillId, ToolContext toolContext) {
        return queryService.getWaybillDetail(toUserContext(toolContext), waybillId).orElse(null);
    }

    @Tool(description = "查询运单轨迹节点，按时间正序返回")
    public List<TrackingEvent> queryTrackingEvents(String waybillId, ToolContext toolContext) {
        return queryService.queryTrackingEvents(toUserContext(toolContext), waybillId);
    }

    @Tool(description = "查询客户指定日期范围内的异常事件，可按异常类型过滤")
    public List<ExceptionEvent> queryCustomerExceptions(String customerId, LocalDate from, LocalDate to,
                                                        String exceptionType, Integer limit, ToolContext toolContext) {
        return queryService.queryCustomerExceptions(toUserContext(toolContext), customerId, from, to,
                exceptionType, limit == null ? 20 : limit);
    }

    @Tool(description = "查询客户指定日期范围内的投诉和服务工单")
    public List<TicketRecord> queryCustomerTickets(String customerId, LocalDate from, LocalDate to,
                                                   Integer limit, ToolContext toolContext) {
        return queryService.queryCustomerTickets(toUserContext(toolContext), customerId, from, to, limit == null ? 20 : limit);
    }

    @Tool(description = "查询物流 SLA 和合同规则，可按客户等级和服务类型过滤")
    public List<SlaRule> querySlaRules(String customerLevel, String serviceType, ToolContext toolContext) {
        return queryService.querySlaRules(toUserContext(toolContext), customerLevel, serviceType);
    }

    @Tool(description = "生成客户在日期范围内的物流服务诊断报告，包括异常率、投诉率、主要风险和建议")
    public DiagnosisReport generateCustomerDiagnosis(String customerId, LocalDate from, LocalDate to,
                                                     ToolContext toolContext) {
        return queryService.diagnoseCustomer(toUserContext(toolContext), customerId, from, to);
    }

    @Tool(description = "查询当前租户下高风险或中风险客户列表")
    public List<CustomerProfile> queryHighRiskCustomers(Integer limit, ToolContext toolContext) {
        return queryService.queryHighRiskCustomers(toUserContext(toolContext), limit == null ? 10 : limit);
    }

    public ToolContext toolContext(AgentUserContext userContext) {
        return new ToolContext(Map.of(
                "tenantId", userContext.tenantId(),
                "userId", userContext.userId(),
                "roles", userContext.roles()
        ));
    }

    private AgentUserContext toUserContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return AgentUserContext.from(null, null, null);
        }
        Map<String, Object> context = toolContext.getContext();
        String tenantId = stringValue(context.get("tenantId"));
        String userId = stringValue(context.get("userId"));
        Object rolesObject = context.get("roles");
        if (rolesObject instanceof Collection<?> collection) {
            Set<String> roles = collection.stream().map(String::valueOf).collect(Collectors.toSet());
            return new AgentUserContext(tenantId, userId, roles);
        }
        String roles = stringValue(rolesObject);
        List<String> roleList = roles == null ? null : List.of(roles.split(","));
        return AgentUserContext.from(tenantId, userId, roleList);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
