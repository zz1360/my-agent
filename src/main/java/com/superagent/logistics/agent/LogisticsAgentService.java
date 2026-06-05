package com.superagent.logistics.agent;

import com.superagent.logistics.api.dto.AgentChatRequest;
import com.superagent.logistics.api.dto.AgentChatResponse;
import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.ToolCallSummary;
import com.superagent.logistics.audit.AgentAuditService;
import com.superagent.logistics.business.CustomerProfile;
import com.superagent.logistics.business.DiagnosisReport;
import com.superagent.logistics.business.ExceptionEvent;
import com.superagent.logistics.business.SlaRule;
import com.superagent.logistics.business.TicketRecord;
import com.superagent.logistics.business.WaybillDetail;
import com.superagent.logistics.business.WaybillSummary;
import com.superagent.logistics.knowledge.KnowledgeSearchResult;
import com.superagent.logistics.knowledge.KnowledgeSearchService;
import com.superagent.logistics.security.AccessDeniedException;
import com.superagent.logistics.security.AgentUserContext;
import com.superagent.logistics.security.PromptInjectionGuard;
import com.superagent.logistics.security.SensitiveDataMasker;
import com.superagent.logistics.tools.LogisticsTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LogisticsAgentService {

    private static final Pattern CUSTOMER_ID_PATTERN = Pattern.compile("\\bC\\d{3}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WAYBILL_ID_PATTERN = Pattern.compile("\\bWB[A-Z0-9]{8,24}\\b", Pattern.CASE_INSENSITIVE);

    private final KnowledgeSearchService knowledgeSearchService;
    private final LogisticsTools logisticsTools;
    private final PromptInjectionGuard promptInjectionGuard;
    private final SensitiveDataMasker masker;
    private final AgentAuditService auditService;
    private final ObjectProvider<ChatClient> deepSeekChatClient;
    private final boolean deepSeekEnabled;
    private final LocalDate seedDate;

    public LogisticsAgentService(KnowledgeSearchService knowledgeSearchService,
                                 LogisticsTools logisticsTools,
                                 PromptInjectionGuard promptInjectionGuard,
                                 SensitiveDataMasker masker,
                                 AgentAuditService auditService,
                                 @Qualifier("deepSeekChatClient") ObjectProvider<ChatClient> deepSeekChatClient,
                                 @Value("${agent.deepseek.enabled:false}") boolean deepSeekEnabled,
                                 @Value("${agent.demo.seed-date:2026-06-04}") String seedDate) {
        this.knowledgeSearchService = knowledgeSearchService;
        this.logisticsTools = logisticsTools;
        this.promptInjectionGuard = promptInjectionGuard;
        this.masker = masker;
        this.auditService = auditService;
        this.deepSeekChatClient = deepSeekChatClient;
        this.deepSeekEnabled = deepSeekEnabled;
        this.seedDate = LocalDate.parse(seedDate);
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        long started = System.nanoTime();
        String traceId = "trace-" + DateTimeFormatter.BASIC_ISO_DATE.format(seedDate) + "-" + UUID.randomUUID().toString().substring(0, 8);
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        ToolContext toolContext = logisticsTools.toolContext(context);
        String message = request.message() == null ? "" : request.message().trim();
        DateRange range = resolveDateRange(message);
        boolean suspicious = promptInjectionGuard.isSuspicious(message);

        List<ToolCallSummary> toolCalls = new ArrayList<>();
        String customerId = extractCustomerId(message);
        String waybillId = extractWaybillId(message);

        List<KnowledgeSearchResult> knowledgeResults = knowledgeSearchService.search(context, message, 4);
        List<Citation> citations = shouldReturnCitations(request)
                ? knowledgeResults.stream().map(this::toCitation).toList()
                : List.of();

        if (deepSeekEnabled && !suspicious) {
            AgentChatResponse deepSeekResponse = tryDeepSeekChat(request, context, message, knowledgeResults,
                    citations, traceId, started, toolCalls, customerId, waybillId);
            if (deepSeekResponse != null) {
                return deepSeekResponse;
            }
        }

        CustomerProfile customer = null;
        WaybillDetail waybillDetail = null;
        List<WaybillSummary> waybills = List.of();
        List<ExceptionEvent> exceptions = List.of();
        List<TicketRecord> tickets = List.of();
        List<SlaRule> slaRules = List.of();
        DiagnosisReport diagnosis = null;
        List<CustomerProfile> highRiskCustomers = List.of();

        if (waybillId != null) {
            waybillDetail = callTool("getWaybillDetail", "waybillId=" + waybillId,
                    () -> logisticsTools.getWaybillDetail(waybillId, toolContext), toolCalls, this::summarizeWaybillDetail);
            if (waybillDetail != null && waybillDetail.waybill() != null) {
                customerId = customerId == null ? waybillDetail.waybill().customerId() : customerId;
                customer = waybillDetail.customer();
            }
        }

        if (customerId != null) {
            String finalCustomerId = customerId;
            boolean customerLevelQuestion = waybillId == null
                    || containsAny(message, "客户", "最近", "为什么", "诊断", "摘要", "投诉量", "上升", "风险");
            customer = customer == null
                    ? callTool("getCustomerProfile", "customerId=" + finalCustomerId,
                    () -> logisticsTools.getCustomerProfile(finalCustomerId, toolContext), toolCalls, this::summarizeCustomer)
                    : customer;

            if (customerLevelQuestion && shouldQueryWaybills(message, waybillId)) {
                String status = message.contains("异常") ? "EXCEPTION" : null;
                waybills = callTool("queryCustomerWaybills", "customerId=" + finalCustomerId + ",from=" + range.from() + ",to=" + range.to(),
                        () -> logisticsTools.queryCustomerWaybills(finalCustomerId, range.from(), range.to(), status, 12, toolContext),
                        toolCalls, rows -> "返回 " + safeSize(rows) + " 条运单摘要");
            }
            if (customerLevelQuestion && shouldQueryExceptions(message)) {
                exceptions = callTool("queryCustomerExceptions", "customerId=" + finalCustomerId + ",from=" + range.from() + ",to=" + range.to(),
                        () -> logisticsTools.queryCustomerExceptions(finalCustomerId, range.from(), range.to(), null, 20, toolContext),
                        toolCalls, rows -> "返回 " + safeSize(rows) + " 条异常事件");
            }
            if (customerLevelQuestion && shouldQueryTickets(message)) {
                tickets = callTool("queryCustomerTickets", "customerId=" + finalCustomerId + ",from=" + range.from() + ",to=" + range.to(),
                        () -> logisticsTools.queryCustomerTickets(finalCustomerId, range.from(), range.to(), 20, toolContext),
                        toolCalls, rows -> "返回 " + safeSize(rows) + " 条工单/投诉");
            }
            if (customerLevelQuestion && shouldGenerateDiagnosis(message)) {
                diagnosis = callTool("generateCustomerDiagnosis", "customerId=" + finalCustomerId + ",from=" + range.from() + ",to=" + range.to(),
                        () -> logisticsTools.generateCustomerDiagnosis(finalCustomerId, range.from(), range.to(), toolContext),
                        toolCalls, this::summarizeDiagnosis);
            }
            if (shouldQuerySla(message) && customer != null) {
                String serviceType = waybillDetail != null && waybillDetail.waybill() != null ? waybillDetail.waybill().serviceType() : null;
                CustomerProfile finalCustomer = customer;
                slaRules = callTool("querySlaRules", "level=" + customer.customerLevel() + ",serviceType=" + serviceType,
                        () -> logisticsTools.querySlaRules(finalCustomer.customerLevel(), serviceType, toolContext),
                        toolCalls, rows -> "返回 " + safeSize(rows) + " 条 SLA/合同规则");
            }
        } else if (message.contains("高风险客户") || message.contains("风险客户")) {
            highRiskCustomers = callTool("queryHighRiskCustomers", "limit=10",
                    () -> logisticsTools.queryHighRiskCustomers(10, toolContext), toolCalls,
                    rows -> "返回 " + safeSize(rows) + " 个风险客户");
        }

        String riskLevel = resolveRiskLevel(message, suspicious, toolCalls, customerId, waybillId);
        String answer = buildAnswer(message, range, suspicious, customer, waybillDetail, waybills, exceptions,
                tickets, slaRules, diagnosis, highRiskCustomers, knowledgeResults, toolCalls);
        answer = masker.maskText(answer);
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        auditService.recordTrace(traceId, context, request, answer, riskLevel, latencyMs);
        auditService.recordToolCalls(traceId, toolCalls);

        return new AgentChatResponse(
                traceId,
                request.conversationId(),
                answer,
                riskLevel,
                confidence(toolCalls, knowledgeResults, customerId, waybillId),
                citations,
                toolCalls,
                Instant.now()
        );
    }

    private <T> T callTool(String toolName, String arguments, Supplier<T> supplier,
                           List<ToolCallSummary> toolCalls, Function<T, String> summarizer) {
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            String summary = summarizer.apply(result);
            toolCalls.add(new ToolCallSummary(toolName, "success", summary + "；参数：" + arguments, latencyMs, null));
            return result;
        } catch (AccessDeniedException ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary(toolName, "denied", ex.getMessage() + "；参数：" + arguments, latencyMs, "ACCESS_DENIED"));
            return null;
        } catch (RuntimeException ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary(toolName, "failed", ex.getMessage() + "；参数：" + arguments, latencyMs, "TOOL_ERROR"));
            return null;
        }
    }

    private AgentChatResponse tryDeepSeekChat(AgentChatRequest request,
                                              AgentUserContext context,
                                              String message,
                                              List<KnowledgeSearchResult> knowledgeResults,
                                              List<Citation> citations,
                                              String traceId,
                                              long started,
                                              List<ToolCallSummary> toolCalls,
                                              String customerId,
                                              String waybillId) {
        ChatClient chatClient = deepSeekChatClient.getIfAvailable();
        if (chatClient == null) {
            return null;
        }
        long start = System.nanoTime();
        try {
            String answer = chatClient.prompt()
                    .tools(logisticsTools)
                    .toolContext(Map.of(
                            "tenantId", context.tenantId(),
                            "userId", context.userId(),
                            "roles", context.roles()
                    ))
                    .user(buildDeepSeekUserPrompt(message, knowledgeResults))
                    .call()
                    .content();
            long modelLatencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary(
                    "deepSeekChatClient",
                    "success",
                    "DeepSeek ChatClient 已生成回答；Spring AI 内部工具调用由模型自动决策执行",
                    modelLatencyMs,
                    null
            ));

            String maskedAnswer = masker.maskText(answer);
            String riskLevel = resolveRiskLevel(message, false, toolCalls, customerId, waybillId);
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            auditService.recordTrace(traceId, context, request, maskedAnswer, riskLevel, latencyMs);
            auditService.recordToolCalls(traceId, toolCalls);
            return new AgentChatResponse(
                    traceId,
                    request.conversationId(),
                    maskedAnswer,
                    riskLevel,
                    Math.min(0.94, confidence(toolCalls, knowledgeResults, customerId, waybillId)),
                    citations,
                    toolCalls,
                    Instant.now()
            );
        } catch (RuntimeException ex) {
            long modelLatencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary(
                    "deepSeekChatClient",
                    "failed",
                    "DeepSeek 调用失败，已回退本地规则编排：" + ex.getMessage(),
                    modelLatencyMs,
                    "MODEL_ERROR"
            ));
            return null;
        }
    }

    private String buildDeepSeekUserPrompt(String message, List<KnowledgeSearchResult> knowledgeResults) {
        return """
                用户问题：
                %s

                知识库片段：
                %s

                回答要求：
                - 如需查询客户、运单、轨迹、异常、工单、SLA 或诊断报告，请调用可用工具。
                - 回答必须引用知识库片段中的 docId/chunkId。
                - 如果工具没有返回业务数据，明确说明缺少数据，不得编造。
                - 涉及赔付、合同、责任认定或冷链货损时，只给建议并提示人工复核。
                - compensationAmount 不是“已支付金额”，只能称为登记补偿金额、建议补偿金额或待核算金额。
                - 不要使用表情符号；不要把 null 原样写给用户。
                """.formatted(message, formatKnowledgeContext(knowledgeResults));
    }

    private String formatKnowledgeContext(List<KnowledgeSearchResult> knowledgeResults) {
        if (knowledgeResults == null || knowledgeResults.isEmpty()) {
            return "未检索到匹配知识库片段。";
        }
        StringBuilder builder = new StringBuilder();
        for (KnowledgeSearchResult result : knowledgeResults) {
            builder.append("- title: ").append(result.chunk().title()).append("\n")
                    .append("  docId: ").append(result.chunk().docId()).append("\n")
                    .append("  chunkId: ").append(result.chunk().chunkId()).append("\n")
                    .append("  content: ").append(result.chunk().content()).append("\n");
        }
        return builder.toString();
    }

    private String buildAnswer(String message, DateRange range, boolean suspicious, CustomerProfile customer,
                               WaybillDetail waybillDetail, List<WaybillSummary> waybills,
                               List<ExceptionEvent> exceptions, List<TicketRecord> tickets,
                               List<SlaRule> slaRules, DiagnosisReport diagnosis,
                               List<CustomerProfile> highRiskCustomers,
                               List<KnowledgeSearchResult> knowledgeResults,
                               List<ToolCallSummary> toolCalls) {
        StringBuilder answer = new StringBuilder();
        answer.append("摘要：\n");
        if (suspicious) {
            answer.append("检测到疑似提示词注入或越权表达，我会忽略这类指令，只基于当前用户权限、工具查询结果和知识库依据回答。\n");
        }
        if (customer != null) {
            answer.append("客户 ").append(customer.customerId()).append("（").append(customer.customerName()).append("）当前风险等级为 ")
                    .append(customer.riskLevel()).append("，客户等级 ").append(customer.customerLevel()).append("。\n");
        } else if (waybillDetail != null && waybillDetail.waybill() != null) {
            answer.append("已查询运单 ").append(waybillDetail.waybill().waybillId()).append(" 的业务数据。\n");
        } else if (!highRiskCustomers.isEmpty()) {
            answer.append("已按当前权限查询风险客户列表。\n");
        } else if (knowledgeResults.isEmpty() && toolCalls.isEmpty()) {
            answer.append("我还缺少客户编号或运单号，无法查询业务数据；可以先根据知识库规则做一般说明。\n");
        } else {
            answer.append("已结合知识库依据和可用业务工具生成回答。\n");
        }

        appendWaybillDetail(answer, waybillDetail);
        appendDiagnosis(answer, diagnosis);
        appendExceptions(answer, exceptions);
        appendTickets(answer, tickets);
        appendWaybills(answer, waybills);
        appendHighRiskCustomers(answer, highRiskCustomers);

        answer.append("\n相关制度 / 规则：\n");
        if (knowledgeResults.isEmpty()) {
            answer.append("- 本次没有检索到足够匹配的知识库片段。\n");
        } else {
            knowledgeResults.stream().limit(3).forEach(result ->
                    answer.append("- ").append(result.chunk().title()).append("：")
                            .append(excerpt(result.chunk().content(), 130)).append("\n"));
        }
        if (!slaRules.isEmpty()) {
            answer.append("- SLA/合同规则：");
            answer.append(slaRules.stream().limit(3)
                    .map(rule -> rule.customerLevel() + "/" + rule.serviceType() + " 承诺 " + rule.promiseHours() + " 小时，" + rule.delayCompensationRule())
                    .collect(Collectors.joining("；")));
            answer.append("\n");
        }

        answer.append("\n判断与可能原因：\n");
        appendReasoning(answer, message, waybillDetail, exceptions, tickets, diagnosis);

        answer.append("\n建议下一步：\n");
        appendNextSteps(answer, message, customer, waybillDetail, diagnosis, exceptions, tickets);

        answer.append("\n不确定性：\n");
        if (toolCalls.stream().anyMatch(call -> !"success".equals(call.status()))) {
            answer.append("- 部分工具调用失败或被权限拒绝，回答只基于已成功获取的数据。\n");
        }
        if (message.contains("赔付") || message.contains("合同") || message.contains("责任")) {
            answer.append("- 涉及赔付、合同或责任认定时，本回答只能作为处理建议，最终结论需要人工复核合同和原始凭证。\n");
        } else {
            answer.append("- 第一版使用本地规则编排和模拟数据，真实上线时建议接入实际 TMS/WMS/CRM 和 Spring AI ChatClient。\n");
        }

        answer.append("\n引用来源：\n");
        if (knowledgeResults.isEmpty()) {
            answer.append("- 无知识库引用。\n");
        } else {
            knowledgeResults.stream().limit(3).forEach(result ->
                    answer.append("- ").append(result.chunk().title()).append("（")
                            .append(result.chunk().docId()).append(" / ")
                            .append(result.chunk().chunkId()).append("）\n"));
        }
        return answer.toString();
    }

    private void appendWaybillDetail(StringBuilder answer, WaybillDetail detail) {
        if (detail == null || detail.waybill() == null) {
            return;
        }
        WaybillSummary w = detail.waybill();
        answer.append("\n业务数据：\n");
        answer.append("- 运单 ").append(w.waybillId()).append("：").append(w.originCity()).append(" -> ")
                .append(w.destCity()).append("，服务类型 ").append(w.serviceType()).append("，状态 ")
                .append(w.status()).append("，承诺送达 ").append(w.promisedDeliveryTime());
        if (w.actualDeliveryTime() != null) {
            answer.append("，实际签收 ").append(w.actualDeliveryTime());
        }
        answer.append("。\n");
        if (!detail.trackingEvents().isEmpty()) {
            answer.append("- 最近轨迹：");
            detail.trackingEvents().stream()
                    .sorted(Comparator.comparing(event -> event.eventTime()))
                    .skip(Math.max(0, detail.trackingEvents().size() - 4))
                    .forEach(event -> answer.append(event.eventTime()).append(" ")
                            .append(event.status()).append("（").append(event.nodeName()).append("）").append("；"));
            answer.append("\n");
        }
        if (!detail.exceptions().isEmpty()) {
            answer.append("- 运单异常：");
            detail.exceptions().forEach(event -> answer.append(event.exceptionType()).append("/")
                    .append(event.severity()).append("，影响 ").append(event.impactHours()).append(" 小时，")
                    .append(event.description()).append("；"));
            answer.append("\n");
        }
        if (!detail.tickets().isEmpty()) {
            answer.append("- 关联工单：");
            detail.tickets().forEach(ticket -> answer.append(ticket.ticketId()).append("/")
                    .append(ticket.status()).append("，").append(ticket.summary()).append("；"));
            answer.append("\n");
        }
    }

    private void appendDiagnosis(StringBuilder answer, DiagnosisReport diagnosis) {
        if (diagnosis == null) {
            return;
        }
        answer.append("\n诊断摘要：\n");
        answer.append("- 窗口：").append(diagnosis.windowLabel()).append("，运单 ")
                .append(diagnosis.waybillCount()).append(" 票，异常 ")
                .append(diagnosis.exceptionCount()).append(" 起，工单/投诉 ")
                .append(diagnosis.ticketCount()).append(" 起。\n");
        answer.append("- 异常率 ").append(diagnosis.exceptionRate()).append("%，投诉率 ")
                .append(diagnosis.complaintRate()).append("%，主要风险：")
                .append(diagnosis.mainRisk()).append("。\n");
    }

    private void appendExceptions(StringBuilder answer, List<ExceptionEvent> exceptions) {
        if (exceptions == null || exceptions.isEmpty()) {
            return;
        }
        answer.append("\n异常明细：\n");
        exceptions.stream().limit(5).forEach(event -> answer.append("- ")
                .append(event.eventTime()).append(" ").append(event.waybillId()).append(" ")
                .append(event.exceptionType()).append("/").append(event.severity()).append("，责任归属 ")
                .append(event.responsibilityParty()).append("，影响 ").append(event.impactHours())
                .append(" 小时：").append(event.description()).append("\n"));
    }

    private void appendTickets(StringBuilder answer, List<TicketRecord> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        answer.append("\n工单 / 投诉：\n");
        tickets.stream().limit(5).forEach(ticket -> answer.append("- ")
                .append(ticket.createdAt()).append(" ").append(ticket.ticketId()).append(" ")
                .append(ticket.ticketType()).append("/").append(ticket.priority()).append("/")
                .append(ticket.status()).append("：").append(ticket.summary()).append("\n"));
    }

    private void appendWaybills(StringBuilder answer, List<WaybillSummary> waybills) {
        if (waybills == null || waybills.isEmpty()) {
            return;
        }
        answer.append("\n运单样本：\n");
        waybills.stream().limit(5).forEach(w -> answer.append("- ")
                .append(w.waybillId()).append(" ").append(w.originCity()).append(" -> ")
                .append(w.destCity()).append("，").append(w.serviceType()).append("，状态 ")
                .append(w.status()).append("，承诺 ").append(w.promisedDeliveryTime()).append("\n"));
    }

    private void appendHighRiskCustomers(StringBuilder answer, List<CustomerProfile> customers) {
        if (customers == null || customers.isEmpty()) {
            return;
        }
        answer.append("\n风险客户：\n");
        customers.stream().limit(10).forEach(customer -> answer.append("- ")
                .append(customer.customerId()).append(" ").append(customer.customerName()).append("，区域 ")
                .append(customer.region()).append("，等级 ").append(customer.customerLevel()).append("，风险 ")
                .append(customer.riskLevel()).append("，月单量 ").append(customer.monthlyVolume()).append("\n"));
    }

    private void appendReasoning(StringBuilder answer, String message, WaybillDetail detail,
                                 List<ExceptionEvent> exceptions, List<TicketRecord> tickets,
                                 DiagnosisReport diagnosis) {
        Set<String> reasons = new LinkedHashSet<>();
        if (detail != null && detail.waybill() != null) {
            if (!detail.exceptions().isEmpty()) {
                detail.exceptions().forEach(event -> reasons.add("运单存在 " + event.exceptionType() + "，责任归属为 " + event.responsibilityParty()));
            }
            if (detail.waybill().actualDeliveryTime() != null &&
                    detail.waybill().actualDeliveryTime().isAfter(detail.waybill().promisedDeliveryTime())) {
                reasons.add("实际签收晚于承诺送达时间，具备进一步核对 SLA 的条件");
            }
            if (detail.waybill().status().equals("EXCEPTION")) {
                reasons.add("当前运单仍处于异常状态，赔付或责任结论需要等签收和责任认定完成");
            }
        }
        if (exceptions != null && !exceptions.isEmpty()) {
            String grouped = exceptions.stream().collect(Collectors.groupingBy(ExceptionEvent::exceptionType, Collectors.counting()))
                    .entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .map(entry -> entry.getKey() + entry.getValue() + "起")
                    .collect(Collectors.joining("、"));
            reasons.add("查询窗口内异常主要集中在 " + grouped);
        }
        if (tickets != null && tickets.size() >= 3) {
            reasons.add("工单/投诉数量达到 " + tickets.size() + " 起，说明客户感知已明显受影响");
        }
        if (diagnosis != null && diagnosis.exceptionRate() >= 12) {
            reasons.add("异常率 " + diagnosis.exceptionRate() + "% 已超过高风险参考阈值");
        }
        if (message.contains("冷链") || message.contains("超温")) {
            reasons.add("冷链超温问题必须结合温控曲线和货损评估，不能只按签收时间判断");
        }
        if (reasons.isEmpty()) {
            answer.append("- 暂未发现足够业务数据支撑明确归因，建议补充客户编号、运单号或时间范围。\n");
            return;
        }
        reasons.forEach(reason -> answer.append("- ").append(reason).append("。\n"));
    }

    private void appendNextSteps(StringBuilder answer, String message, CustomerProfile customer,
                                 WaybillDetail detail, DiagnosisReport diagnosis,
                                 List<ExceptionEvent> exceptions, List<TicketRecord> tickets) {
        if (detail != null && detail.waybill() != null && detail.waybill().status().equals("EXCEPTION")) {
            answer.append("- 先让运营确认运单 ").append(detail.waybill().waybillId()).append(" 的预计恢复时间，并约定下一次同步节点。\n");
        }
        if (customer != null && "VIP".equals(customer.customerLevel())) {
            answer.append("- 按 VIP 异常沟通规范，回复中包含当前节点、原因、负责人和下一次同步时间。\n");
        }
        if (diagnosis != null && (diagnosis.exceptionRate() >= 8 || diagnosis.ticketCount() >= 1)) {
            answer.append("- 针对 ").append(diagnosis.mainRisk()).append(" 做线路复盘，并设置未来 7 天节点预警。\n");
        }
        boolean coldChainRelated = message.contains("冷链") || message.contains("超温")
                || (detail != null && detail.exceptions().stream().anyMatch(event -> "温控异常".equals(event.exceptionType())));
        if (coldChainRelated) {
            answer.append("- 冷链相关问题需要调取温控曲线、车厢记录和质检建议，再判断是否进入赔付流程。\n");
        }
        if (tickets != null && tickets.stream().anyMatch(ticket -> "PROCESSING".equals(ticket.status()))) {
            answer.append("- 对处理中工单给出预计反馈时间，避免客户重复投诉。\n");
        }
        if (answer.toString().endsWith("建议下一步：\n")) {
            answer.append("- 补充具体客户编号或运单号后，可继续查询业务数据并给出更精确判断。\n");
        }
    }

    private DateRange resolveDateRange(String message) {
        if (message.contains("本周")) {
            return new DateRange(seedDate.minusDays(6), seedDate);
        }
        Matcher matcher = Pattern.compile("最近(\\d{1,3})天").matcher(message);
        if (matcher.find()) {
            int days = Math.max(1, Math.min(120, Integer.parseInt(matcher.group(1))));
            return new DateRange(seedDate.minusDays(days - 1), seedDate);
        }
        if (message.contains("今天")) {
            return new DateRange(seedDate, seedDate);
        }
        return new DateRange(seedDate.minusDays(29), seedDate);
    }

    private String extractCustomerId(String message) {
        Matcher matcher = CUSTOMER_ID_PATTERN.matcher(message.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private String extractWaybillId(String message) {
        Matcher matcher = WAYBILL_ID_PATTERN.matcher(message.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private boolean shouldReturnCitations(AgentChatRequest request) {
        return request.returnCitations() == null || request.returnCitations();
    }

    private boolean shouldQueryWaybills(String message, String waybillId) {
        return waybillId == null && containsAny(message, "运单", "订单", "最近", "异常", "诊断", "摘要", "状态");
    }

    private boolean shouldQueryExceptions(String message) {
        return containsAny(message, "异常", "投诉", "为什么", "风险", "延误", "上升", "诊断", "摘要", "超温", "赔付", "赔", "补偿", "晚到");
    }

    private boolean shouldQueryTickets(String message) {
        return containsAny(message, "投诉", "工单", "客诉", "为什么", "上升", "诊断", "摘要");
    }

    private boolean shouldGenerateDiagnosis(String message) {
        return containsAny(message, "诊断", "摘要", "为什么", "风险", "上升", "最近");
    }

    private boolean shouldQuerySla(String message) {
        return containsAny(message, "SLA", "政策", "规则", "赔付", "赔", "补偿", "合同", "是否", "冷链", "延误", "晚到", "超时", "怎么处理");
    }

    private boolean containsAny(String message, String... words) {
        for (String word : words) {
            if (message.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String resolveRiskLevel(String message, boolean suspicious, List<ToolCallSummary> toolCalls,
                                    String customerId, String waybillId) {
        if (suspicious) {
            return "L4";
        }
        if (containsAny(message, "赔付", "赔", "补偿", "合同", "责任", "冷链")) {
            return "L3";
        }
        if (customerId != null || waybillId != null || !toolCalls.isEmpty()) {
            return "L2";
        }
        return "L1";
    }

    private double confidence(List<ToolCallSummary> toolCalls, List<KnowledgeSearchResult> knowledgeResults,
                              String customerId, String waybillId) {
        double value = 0.55;
        if (!knowledgeResults.isEmpty()) {
            value += 0.15;
        }
        if (!toolCalls.isEmpty() && toolCalls.stream().allMatch(call -> "success".equals(call.status()))) {
            value += 0.2;
        }
        if (customerId != null || waybillId != null) {
            value += 0.05;
        }
        return Math.min(0.95, Math.round(value * 100.0) / 100.0);
    }

    private Citation toCitation(KnowledgeSearchResult result) {
        return new Citation("knowledge", result.chunk().title(), result.chunk().docId(),
                result.chunk().chunkId(), excerpt(result.chunk().content(), 120));
    }

    private String summarizeCustomer(CustomerProfile customer) {
        if (customer == null) {
            return "未找到客户";
        }
        return customer.customerId() + " " + customer.customerName() + "，等级 " + customer.customerLevel()
                + "，风险 " + customer.riskLevel();
    }

    private String summarizeWaybillDetail(WaybillDetail detail) {
        if (detail == null || detail.waybill() == null) {
            return "未找到运单";
        }
        return detail.waybill().waybillId() + " 状态 " + detail.waybill().status()
                + "，轨迹 " + detail.trackingEvents().size() + " 条，异常 " + detail.exceptions().size()
                + " 起，工单 " + detail.tickets().size() + " 起";
    }

    private String summarizeDiagnosis(DiagnosisReport diagnosis) {
        if (diagnosis == null) {
            return "未生成诊断";
        }
        return "运单 " + diagnosis.waybillCount() + " 票，异常率 " + diagnosis.exceptionRate()
                + "%，投诉率 " + diagnosis.complaintRate() + "%";
    }

    private int safeSize(List<?> rows) {
        return rows == null ? 0 : rows.size();
    }

    private String excerpt(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
