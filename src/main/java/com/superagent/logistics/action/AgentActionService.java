package com.superagent.logistics.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.logistics.api.dto.AgentActionGenerateRequest;
import com.superagent.logistics.api.dto.AgentActionResponse;
import com.superagent.logistics.api.dto.AgentActionReviewRequest;
import com.superagent.logistics.api.dto.AuditResponse;
import com.superagent.logistics.api.dto.PageResponse;
import com.superagent.logistics.audit.AgentAuditService;
import com.superagent.logistics.business.CustomerProfile;
import com.superagent.logistics.business.DiagnosisReport;
import com.superagent.logistics.business.ExceptionEvent;
import com.superagent.logistics.business.LogisticsQueryService;
import com.superagent.logistics.business.TicketRecord;
import com.superagent.logistics.business.WaybillSummary;
import com.superagent.logistics.security.AccessDeniedException;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentActionService {

    private static final int DEFAULT_DAYS = 30;
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LogisticsQueryService logisticsQueryService;
    private final AgentPermissionService permissionService;
    private final AgentAuditService auditService;
    private final LocalDate seedDate;

    public AgentActionService(JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              LogisticsQueryService logisticsQueryService,
                              AgentPermissionService permissionService,
                              AgentAuditService auditService,
                              @Value("${agent.demo.seed-date:2026-06-04}") String seedDate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.logisticsQueryService = logisticsQueryService;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.seedDate = LocalDate.parse(seedDate);
    }

    public List<AgentActionResponse> generateFromDiagnosis(AgentActionGenerateRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        permissionService.checkCustomerReadable(context, request.customerId());
        validateTrace(context, request.traceId());

        Window window = resolveWindow(request.from(), request.to(), request.days());
        CustomerProfile customer = logisticsQueryService.getCustomerProfile(context, request.customerId())
                .orElseThrow(() -> new IllegalArgumentException("未找到客户 " + request.customerId()));
        DiagnosisReport diagnosis = logisticsQueryService.diagnoseCustomer(context, request.customerId(), window.from(), window.to());
        List<WaybillSummary> waybills = logisticsQueryService.queryCustomerWaybills(context, request.customerId(), window.from(), window.to(), null, 50);
        List<ExceptionEvent> exceptions = logisticsQueryService.queryCustomerExceptions(context, request.customerId(), window.from(), window.to(), null, 80);
        List<TicketRecord> tickets = logisticsQueryService.queryCustomerTickets(context, request.customerId(), window.from(), window.to(), 80);

        List<ActionDraft> drafts = new ArrayList<>();
        drafts.add(customerReplyDraft(context, request, window, customer, diagnosis, waybills, exceptions, tickets));
        drafts.add(ticketNoteDraft(context, request, window, customer, diagnosis, waybills, exceptions, tickets));
        selectCompensationCandidate(waybills, exceptions)
                .ifPresent(candidate -> drafts.add(compensationReviewDraft(context, request, window, customer, diagnosis, exceptions, tickets, candidate)));
        if (diagnosis.exceptionRate() >= 8 || diagnosis.ticketCount() > 0) {
            drafts.add(operationsFollowUpDraft(context, request, window, customer, diagnosis, waybills, exceptions, tickets));
        }

        Instant now = Instant.now();
        drafts.forEach(draft -> insertDraft(draft, now));
        return drafts.stream()
                .map(draft -> findByActionId(context.tenantId(), draft.actionId()).orElseThrow())
                .toList();
    }

    public List<AgentActionResponse> list(String tenantId, String userId, List<String> roles,
                                          String customerId, String status, int limit) {
        return page(tenantId, userId, roles, customerId, status, 1, limit).items();
    }

    public PageResponse<AgentActionResponse> page(String tenantId, String userId, List<String> roles,
                                                   String customerId, String status, int page, int size) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        if (customerId == null || customerId.isBlank()) {
            permissionService.checkBusinessReadable(context);
        } else {
            permissionService.checkCustomerReadable(context, customerId);
        }

        int resolvedPage = PageResponse.normalizePage(page);
        int resolvedSize = PageResponse.normalizeSize(size);
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(context.tenantId());
        if (customerId != null && !customerId.isBlank()) {
            where.append(" AND customer_id = ?");
            args.add(customerId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(normalizeStatus(status));
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_agent_action_draft" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(resolvedSize);
        pageArgs.add((resolvedPage - 1) * resolvedSize);
        List<AgentActionResponse> items = jdbcTemplate.query(
                "SELECT * FROM ai_agent_action_draft" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                this::mapAction, pageArgs.toArray());
        return PageResponse.of(items, resolvedPage, resolvedSize, total == null ? 0 : total);
    }

    public AgentActionResponse get(String tenantId, String userId, List<String> roles, String actionId) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        AgentActionResponse action = findByActionId(context.tenantId(), actionId)
                .orElseThrow(() -> new IllegalArgumentException("未找到动作草稿 " + actionId));
        permissionService.checkCustomerReadable(context, action.customerId());
        return action;
    }

    public AgentActionResponse review(String actionId, AgentActionReviewRequest request) {
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        if (!context.hasAnyRole("OPERATIONS", "OPS_MANAGER", "ADMIN")) {
            throw new AccessDeniedException("当前用户没有动作复核权限");
        }
        AgentActionResponse current = findByActionId(context.tenantId(), actionId)
                .orElseThrow(() -> new IllegalArgumentException("未找到动作草稿 " + actionId));
        permissionService.checkCustomerReadable(context, current.customerId());

        String nextStatus = normalizeReviewStatus(request.status());
        if ("APPLIED".equals(nextStatus) && !"APPROVED".equals(current.status())) {
            throw new IllegalArgumentException("只有已通过的动作草稿才能标记为已执行");
        }

        Instant now = Instant.now();
        jdbcTemplate.update("""
                UPDATE ai_agent_action_draft
                SET status = ?, reviewer_id = ?, review_comment = ?, updated_at = ?, reviewed_at = ?
                WHERE tenant_id = ? AND action_id = ?
                """, nextStatus, context.userId(), request.comment(),
                Timestamp.from(now), Timestamp.from(now), context.tenantId(), actionId);
        return findByActionId(context.tenantId(), actionId).orElseThrow();
    }

    private void validateTrace(AgentUserContext context, String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        AuditResponse trace = auditService.findByTraceId(traceId)
                .orElseThrow(() -> new IllegalArgumentException("未找到诊断 trace: " + traceId));
        if (!context.tenantId().equals(trace.tenantId())) {
            throw new AccessDeniedException("当前租户无权访问诊断 trace " + traceId);
        }
    }

    private Window resolveWindow(LocalDate requestFrom, LocalDate requestTo, Integer requestDays) {
        LocalDate to = requestTo == null ? seedDate : requestTo;
        LocalDate from = requestFrom;
        if (from == null) {
            int days = requestDays == null ? DEFAULT_DAYS : Math.max(1, Math.min(120, requestDays));
            from = to.minusDays(days - 1L);
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from 不能晚于 to");
        }
        return new Window(from, to);
    }

    private ActionDraft customerReplyDraft(AgentUserContext context,
                                           AgentActionGenerateRequest request,
                                           Window window,
                                           CustomerProfile customer,
                                           DiagnosisReport diagnosis,
                                           List<WaybillSummary> waybills,
                                           List<ExceptionEvent> exceptions,
                                           List<TicketRecord> tickets) {
        String content = """
                建议客服话术：
                %s您好，我们已经复核 %s 至 %s 期间的运输记录。当前主要风险为%s，窗口内共 %d 票运单、%d 起异常、%d 个工单。
                我们会优先跟进仍未关闭的异常运单，并在 24 小时内同步节点证明、责任归属和预计处理时点。涉及 SLA 或补偿的部分将进入人工复核，不会自动承诺赔付金额。

                内部提醒：
                1. 对客沟通时不要直接承诺赔付，先确认签收时间、责任归属和合同规则。
                2. 如客户追问原因，可引用当前诊断建议：%s
                3. 回复前请人工复核异常明细和最新轨迹。
                """.formatted(customer.contactName(), window.from(), window.to(), diagnosis.mainRisk(),
                diagnosis.waybillCount(), diagnosis.exceptionCount(), diagnosis.ticketCount(), diagnosis.recommendation());
        return new ActionDraft(
                context.tenantId(),
                newActionId("reply"),
                request.traceId(),
                request.conversationId(),
                customer.customerId(),
                null,
                "CUSTOMER_REPLY",
                "客户 " + customer.customerId() + " 异常诊断回复话术",
                highTouchPriority(customer, diagnosis, tickets),
                "L2",
                content,
                evidenceJson(request, window, customer, diagnosis, waybills, exceptions, tickets),
                context.userId()
        );
    }

    private ActionDraft ticketNoteDraft(AgentUserContext context,
                                        AgentActionGenerateRequest request,
                                        Window window,
                                        CustomerProfile customer,
                                        DiagnosisReport diagnosis,
                                        List<WaybillSummary> waybills,
                                        List<ExceptionEvent> exceptions,
                                        List<TicketRecord> tickets) {
        String content = """
                工单备注草稿：
                客户 %s（%s）在 %s 至 %s 的诊断窗口内异常率 %.1f%%、投诉率 %.1f%%，主风险为%s。
                已识别 %d 条异常记录和 %d 个相关工单，建议将未解决异常按运单维度补充轨迹截图、责任归属和预计完成时间。

                建议处理路径：
                1. P1/处理中工单优先升级运营升级组。
                2. 有赔付可能的运单进入补偿复核任务。
                3. 工单备注发布前需人工复核，避免把模型推断写成事实。
                """.formatted(customer.customerName(), customer.customerId(), window.from(), window.to(),
                diagnosis.exceptionRate(), diagnosis.complaintRate(), diagnosis.mainRisk(),
                exceptions.size(), tickets.size());
        return new ActionDraft(
                context.tenantId(),
                newActionId("ticket"),
                request.traceId(),
                request.conversationId(),
                customer.customerId(),
                firstTicketWaybill(tickets),
                "TICKET_NOTE",
                "客户 " + customer.customerId() + " 工单备注草稿",
                tickets.stream().anyMatch(ticket -> "P1".equals(ticket.priority()) || "PROCESSING".equals(ticket.status())) ? "P1" : "P2",
                "L2",
                content,
                evidenceJson(request, window, customer, diagnosis, waybills, exceptions, tickets),
                context.userId()
        );
    }

    private ActionDraft compensationReviewDraft(AgentUserContext context,
                                                AgentActionGenerateRequest request,
                                                Window window,
                                                CustomerProfile customer,
                                                DiagnosisReport diagnosis,
                                                List<ExceptionEvent> exceptions,
                                                List<TicketRecord> tickets,
                                                WaybillSummary candidate) {
        long delayHours = delayHours(candidate);
        List<ExceptionEvent> candidateExceptions = exceptions.stream()
                .filter(event -> candidate.waybillId().equals(event.waybillId()))
                .toList();
        String content = """
                赔付复核任务：
                运单 %s 可能存在 SLA/补偿争议，请人工复核运输轨迹、签收时间、异常责任归属和客户合同条款。

                证据摘要：
                - 服务类型：%s
                - 线路：%s -> %s
                - 承诺送达：%s
                - 实际送达：%s
                - 预估延误：%d 小时
                - 相关异常数：%d
                - 相关工单数：%d

                复核要求：
                1. 未确认责任归属前，不自动创建赔付单。
                2. 如属于客户信息、天气管制等非承运责任，需要在结论中说明免责依据。
                3. 如确认承运责任，再由人工按 SLA 规则填写赔付金额。
                """.formatted(candidate.waybillId(), candidate.serviceType(), candidate.originCity(), candidate.destCity(),
                candidate.promisedDeliveryTime(), candidate.actualDeliveryTime() == null ? "尚未签收" : candidate.actualDeliveryTime(),
                delayHours, candidateExceptions.size(), tickets.stream().filter(ticket -> candidate.waybillId().equals(ticket.waybillId())).count());
        return new ActionDraft(
                context.tenantId(),
                newActionId("comp"),
                request.traceId(),
                request.conversationId(),
                customer.customerId(),
                candidate.waybillId(),
                "COMPENSATION_REVIEW",
                "运单 " + candidate.waybillId() + " 赔付复核任务",
                candidateExceptions.stream().anyMatch(event -> "HIGH".equals(event.severity())) ? "P1" : "P2",
                "L3",
                content,
                evidenceJson(request, window, customer, diagnosis, List.of(candidate), candidateExceptions, tickets),
                context.userId()
        );
    }

    private ActionDraft operationsFollowUpDraft(AgentUserContext context,
                                                AgentActionGenerateRequest request,
                                                Window window,
                                                CustomerProfile customer,
                                                DiagnosisReport diagnosis,
                                                List<WaybillSummary> waybills,
                                                List<ExceptionEvent> exceptions,
                                                List<TicketRecord> tickets) {
        String topResponsibility = exceptions.stream()
                .collect(java.util.stream.Collectors.groupingBy(ExceptionEvent::responsibilityParty, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("暂无明显责任归属");
        String content = """
                运营复盘动作：
                客户 %s 在诊断窗口内异常率 %.1f%%，主风险为%s，当前最集中的责任归属为%s。

                建议复盘范围：
                1. 抽查 TOP 异常运单的干线、中转、末端节点记录。
                2. 对未解决异常建立 7 天节点预警。
                3. 输出一页复盘结论，供客服和销售统一对客口径。
                4. 复盘结论发布前需人工复核引用证据。
                """.formatted(customer.customerId(), diagnosis.exceptionRate(), diagnosis.mainRisk(), topResponsibility);
        return new ActionDraft(
                context.tenantId(),
                newActionId("ops"),
                request.traceId(),
                request.conversationId(),
                customer.customerId(),
                null,
                "OPERATIONS_FOLLOW_UP",
                "客户 " + customer.customerId() + " 运营复盘动作",
                diagnosis.exceptionRate() >= 12 || tickets.size() >= 3 ? "P1" : "P2",
                "L2",
                content,
                evidenceJson(request, window, customer, diagnosis, waybills, exceptions, tickets),
                context.userId()
        );
    }

    private Optional<WaybillSummary> selectCompensationCandidate(List<WaybillSummary> waybills, List<ExceptionEvent> exceptions) {
        List<String> highExceptionWaybillIds = exceptions.stream()
                .filter(event -> !event.resolved() || "HIGH".equals(event.severity()))
                .map(ExceptionEvent::waybillId)
                .toList();
        return waybills.stream()
                .filter(waybill -> "EXCEPTION".equals(waybill.status()) || delayHours(waybill) > 0
                        || highExceptionWaybillIds.contains(waybill.waybillId()))
                .sorted(Comparator.comparing((WaybillSummary waybill) -> highExceptionWaybillIds.contains(waybill.waybillId())).reversed()
                        .thenComparing(AgentActionService::delayHoursStatic, Comparator.reverseOrder())
                        .thenComparing(WaybillSummary::orderDate, Comparator.reverseOrder()))
                .findFirst();
    }

    private String evidenceJson(AgentActionGenerateRequest request,
                                Window window,
                                CustomerProfile customer,
                                DiagnosisReport diagnosis,
                                List<WaybillSummary> waybills,
                                List<ExceptionEvent> exceptions,
                                List<TicketRecord> tickets) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("traceId", request.traceId());
        evidence.put("conversationId", request.conversationId());
        evidence.put("window", window.from() + " 至 " + window.to());
        evidence.put("customer", Map.of(
                "customerId", customer.customerId(),
                "customerName", customer.customerName(),
                "customerLevel", customer.customerLevel(),
                "riskLevel", customer.riskLevel()
        ));
        evidence.put("diagnosis", Map.of(
                "waybillCount", diagnosis.waybillCount(),
                "exceptionCount", diagnosis.exceptionCount(),
                "ticketCount", diagnosis.ticketCount(),
                "exceptionRate", diagnosis.exceptionRate(),
                "complaintRate", diagnosis.complaintRate(),
                "mainRisk", diagnosis.mainRisk(),
                "recommendation", diagnosis.recommendation()
        ));
        evidence.put("sampleWaybills", waybills.stream().limit(5).map(this::sampleWaybill).toList());
        evidence.put("sampleExceptions", exceptions.stream().limit(5).map(this::sampleException).toList());
        evidence.put("sampleTickets", tickets.stream().limit(5).map(this::sampleTicket).toList());
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("动作证据序列化失败", ex);
        }
    }

    private Map<String, Object> sampleWaybill(WaybillSummary waybill) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("waybillId", waybill.waybillId());
        row.put("route", waybill.originCity() + "-" + waybill.destCity());
        row.put("serviceType", waybill.serviceType());
        row.put("status", waybill.status());
        row.put("promisedDeliveryTime", waybill.promisedDeliveryTime());
        row.put("actualDeliveryTime", waybill.actualDeliveryTime());
        row.put("delayHours", delayHours(waybill));
        return row;
    }

    private Map<String, Object> sampleException(ExceptionEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("exceptionId", event.exceptionId());
        row.put("waybillId", event.waybillId());
        row.put("exceptionType", event.exceptionType());
        row.put("severity", event.severity());
        row.put("responsibilityParty", event.responsibilityParty());
        row.put("resolved", event.resolved());
        row.put("impactHours", event.impactHours());
        return row;
    }

    private Map<String, Object> sampleTicket(TicketRecord ticket) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ticketId", ticket.ticketId());
        row.put("waybillId", ticket.waybillId());
        row.put("priority", ticket.priority());
        row.put("status", ticket.status());
        row.put("summary", ticket.summary());
        return row;
    }

    private void insertDraft(ActionDraft draft, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO ai_agent_action_draft
                (tenant_id, action_id, trace_id, conversation_id, customer_id, waybill_id, action_type,
                 title, priority, risk_level, status, draft_content, evidence_json, created_by,
                 created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, draft.tenantId(), draft.actionId(), draft.traceId(), draft.conversationId(),
                draft.customerId(), draft.waybillId(), draft.actionType(), draft.title(), draft.priority(),
                draft.riskLevel(), STATUS_PENDING_REVIEW, draft.draftContent(), draft.evidenceJson(),
                draft.createdBy(), Timestamp.from(now), Timestamp.from(now));
    }

    private Optional<AgentActionResponse> findByActionId(String tenantId, String actionId) {
        List<AgentActionResponse> rows = jdbcTemplate.query("""
                SELECT * FROM ai_agent_action_draft
                WHERE tenant_id = ? AND action_id = ?
                LIMIT 1
                """, this::mapAction, tenantId, actionId);
        return rows.stream().findFirst();
    }

    private AgentActionResponse mapAction(ResultSet rs, int rowNum) throws SQLException {
        return new AgentActionResponse(
                rs.getString("tenant_id"),
                rs.getString("action_id"),
                rs.getString("trace_id"),
                rs.getString("conversation_id"),
                rs.getString("customer_id"),
                rs.getString("waybill_id"),
                rs.getString("action_type"),
                rs.getString("title"),
                rs.getString("priority"),
                rs.getString("risk_level"),
                rs.getString("status"),
                rs.getString("draft_content"),
                rs.getString("evidence_json"),
                rs.getString("created_by"),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("reviewed_at"))
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeStatus(String status) {
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeReviewStatus(String status) {
        String normalized = normalizeStatus(status);
        if (!List.of("APPROVED", "REJECTED", "APPLIED").contains(normalized)) {
            throw new IllegalArgumentException("复核状态只支持 APPROVED、REJECTED、APPLIED");
        }
        return normalized;
    }

    private String highTouchPriority(CustomerProfile customer, DiagnosisReport diagnosis, List<TicketRecord> tickets) {
        if ("VIP".equals(customer.customerLevel()) || diagnosis.ticketCount() >= 3
                || tickets.stream().anyMatch(ticket -> "P1".equals(ticket.priority()))) {
            return "P1";
        }
        return "P2";
    }

    private String firstTicketWaybill(List<TicketRecord> tickets) {
        return tickets.stream()
                .map(TicketRecord::waybillId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static long delayHoursStatic(WaybillSummary waybill) {
        return delayHours(waybill);
    }

    private static long delayHours(WaybillSummary waybill) {
        if (waybill.actualDeliveryTime() == null) {
            return "EXCEPTION".equals(waybill.status()) ? 1 : 0;
        }
        return Math.max(0, Duration.between(waybill.promisedDeliveryTime(), waybill.actualDeliveryTime()).toHours());
    }

    private String newActionId(String prefix) {
        return "act-" + prefix + "-" + seedDate.toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record Window(LocalDate from, LocalDate to) {
    }

    private record ActionDraft(
            String tenantId,
            String actionId,
            String traceId,
            String conversationId,
            String customerId,
            String waybillId,
            String actionType,
            String title,
            String priority,
            String riskLevel,
            String draftContent,
            String evidenceJson,
            String createdBy
    ) {
    }
}
