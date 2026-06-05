package com.superagent.logistics.agent;

import com.superagent.logistics.api.dto.AgentChatRequest;
import com.superagent.logistics.api.dto.Citation;
import com.superagent.logistics.api.dto.CustomerDiagnosisRequest;
import com.superagent.logistics.api.dto.CustomerDiagnosisResponse;
import com.superagent.logistics.api.dto.DiagnosisWindow;
import com.superagent.logistics.api.dto.RiskAttribution;
import com.superagent.logistics.api.dto.SlaAssessment;
import com.superagent.logistics.api.dto.ToolCallSummary;
import com.superagent.logistics.audit.AgentAuditService;
import com.superagent.logistics.business.CustomerProfile;
import com.superagent.logistics.business.DiagnosisReport;
import com.superagent.logistics.business.ExceptionEvent;
import com.superagent.logistics.business.SlaRule;
import com.superagent.logistics.business.TicketRecord;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class CustomerDiagnosisAgentService {

    private static final int DEFAULT_DAYS = 30;

    private final KnowledgeSearchService knowledgeSearchService;
    private final LogisticsTools logisticsTools;
    private final PromptInjectionGuard promptInjectionGuard;
    private final SensitiveDataMasker masker;
    private final AgentAuditService auditService;
    private final ObjectProvider<ChatClient> deepSeekChatClient;
    private final boolean deepSeekEnabled;
    private final LocalDate seedDate;

    public CustomerDiagnosisAgentService(KnowledgeSearchService knowledgeSearchService,
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

    public CustomerDiagnosisResponse diagnose(CustomerDiagnosisRequest request) {
        long started = System.nanoTime();
        String traceId = "trace-" + DateTimeFormatter.BASIC_ISO_DATE.format(seedDate) + "-" + UUID.randomUUID().toString().substring(0, 8);
        AgentUserContext context = AgentUserContext.from(request.tenantId(), request.userId(), request.roles());
        ToolContext toolContext = logisticsTools.toolContext(context);
        DiagnosisWindow window = resolveWindow(request);
        String message = normalizedMessage(request, window);
        boolean suspicious = promptInjectionGuard.isSuspicious(message);

        List<ToolCallSummary> toolCalls = new ArrayList<>();
        CustomerProfile customer = callTool("getCustomerProfile", "customerId=" + request.customerId(),
                () -> logisticsTools.getCustomerProfile(request.customerId(), toolContext), toolCalls, this::summarizeCustomer);
        List<WaybillSummary> waybills = callTool("queryCustomerWaybills",
                "customerId=" + request.customerId() + ",from=" + window.from() + ",to=" + window.to() + ",limit=50",
                () -> logisticsTools.queryCustomerWaybills(request.customerId(), window.from(), window.to(), null, 50, toolContext),
                toolCalls, rows -> "返回 " + safeSize(rows) + " 条运单摘要");
        List<ExceptionEvent> exceptions = callTool("queryCustomerExceptions",
                "customerId=" + request.customerId() + ",from=" + window.from() + ",to=" + window.to() + ",limit=80",
                () -> logisticsTools.queryCustomerExceptions(request.customerId(), window.from(), window.to(), null, 80, toolContext),
                toolCalls, rows -> "返回 " + safeSize(rows) + " 条异常事件");
        List<TicketRecord> tickets = callTool("queryCustomerTickets",
                "customerId=" + request.customerId() + ",from=" + window.from() + ",to=" + window.to() + ",limit=80",
                () -> logisticsTools.queryCustomerTickets(request.customerId(), window.from(), window.to(), 80, toolContext),
                toolCalls, rows -> "返回 " + safeSize(rows) + " 条工单/投诉");
        DiagnosisReport diagnosis = callTool("generateCustomerDiagnosis",
                "customerId=" + request.customerId() + ",from=" + window.from() + ",to=" + window.to(),
                () -> logisticsTools.generateCustomerDiagnosis(request.customerId(), window.from(), window.to(), toolContext),
                toolCalls, this::summarizeDiagnosis);
        List<SlaRule> slaRules = customer == null ? List.of() : callTool("querySlaRules",
                "level=" + customer.customerLevel() + ",serviceType=null",
                () -> logisticsTools.querySlaRules(customer.customerLevel(), null, toolContext),
                toolCalls, rows -> "返回 " + safeSize(rows) + " 条 SLA/合同规则");

        List<KnowledgeSearchResult> knowledgeResults = knowledgeSearchService.search(context, knowledgeQuery(message, diagnosis), 5);
        List<Citation> citations = shouldReturnCitations(request)
                ? knowledgeResults.stream().map(this::toCitation).toList()
                : List.of();

        List<RiskAttribution> attributions = buildAttributions(exceptions, waybills);
        List<SlaAssessment> slaAssessments = buildSlaAssessments(waybills, exceptions, slaRules);
        List<String> nextActions = buildNextActions(customer, diagnosis, attributions, slaAssessments, tickets);
        String riskLevel = resolveRiskLevel(suspicious, diagnosis, slaAssessments, tickets);
        String deterministicNarrative = buildNarrative(window, customer, diagnosis, attributions, slaAssessments,
                nextActions, knowledgeResults, suspicious);
        ModelNarrative modelNarrative = maybeGenerateDeepSeekNarrative(message, deterministicNarrative, knowledgeResults,
                customer, diagnosis, attributions, slaAssessments, nextActions, toolCalls);
        String narrative = masker.maskText(modelNarrative.text());
        long latencyMs = (System.nanoTime() - started) / 1_000_000;

        AgentChatRequest auditRequest = new AgentChatRequest(
                request.conversationId(),
                context.userId(),
                context.tenantId(),
                List.copyOf(context.roles()),
                "客户异常诊断：" + message,
                request.returnCitations()
        );
        auditService.recordTrace(traceId, context, auditRequest, narrative, riskLevel, latencyMs);
        auditService.recordToolCalls(traceId, toolCalls);

        return new CustomerDiagnosisResponse(
                traceId,
                request.conversationId(),
                window,
                maskCustomer(customer),
                diagnosis,
                attributions,
                slaAssessments,
                citations,
                toolCalls,
                nextActions,
                riskLevel,
                confidence(toolCalls, knowledgeResults, diagnosis),
                narrative,
                modelNarrative.provider(),
                Instant.now()
        );
    }

    private CustomerProfile maskCustomer(CustomerProfile customer) {
        if (customer == null) {
            return null;
        }
        return new CustomerProfile(
                customer.customerId(),
                customer.customerName(),
                customer.industry(),
                customer.customerLevel(),
                customer.serviceOwner(),
                customer.salesOwner(),
                customer.contactName(),
                masker.maskPhone(customer.contactPhone()),
                customer.region(),
                customer.riskLevel(),
                customer.monthlyVolume(),
                customer.status()
        );
    }

    private <T> T callTool(String toolName, String arguments, Supplier<T> supplier,
                           List<ToolCallSummary> toolCalls, Function<T, String> summarizer) {
        long start = System.nanoTime();
        try {
            T result = supplier.get();
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary(toolName, "success",
                    summarizer.apply(result) + "；参数：" + arguments, latencyMs, null));
            return result;
        } catch (AccessDeniedException ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary(toolName, "denied",
                    ex.getMessage() + "；参数：" + arguments, latencyMs, "ACCESS_DENIED"));
            throw ex;
        } catch (RuntimeException ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary(toolName, "failed",
                    ex.getMessage() + "；参数：" + arguments, latencyMs, "TOOL_ERROR"));
            throw ex;
        }
    }

    private DiagnosisWindow resolveWindow(CustomerDiagnosisRequest request) {
        LocalDate to = request.to() == null ? seedDate : request.to();
        LocalDate from = request.from();
        if (from == null) {
            int days = request.days() == null ? DEFAULT_DAYS : Math.max(1, Math.min(120, request.days()));
            from = to.minusDays(days - 1L);
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from 不能晚于 to");
        }
        return new DiagnosisWindow(from, to, from + " 至 " + to);
    }

    private String normalizedMessage(CustomerDiagnosisRequest request, DiagnosisWindow window) {
        if (request.message() != null && !request.message().isBlank()) {
            return request.message().trim();
        }
        return "请诊断客户 " + request.customerId() + " 在 " + window.label()
                + " 的投诉上升原因、SLA/赔付候选和下一步处理建议。";
    }

    private String knowledgeQuery(String message, DiagnosisReport diagnosis) {
        String mainRisk = diagnosis == null ? "" : diagnosis.mainRisk();
        return message + " 客户风险 异常归因 投诉 SLA 赔付 补偿 处理 SOP " + mainRisk;
    }

    private List<RiskAttribution> buildAttributions(List<ExceptionEvent> exceptions, List<WaybillSummary> waybills) {
        if (exceptions == null || exceptions.isEmpty()) {
            return List.of();
        }
        Map<String, WaybillSummary> waybillMap = waybills == null ? Map.of() : waybills.stream()
                .collect(Collectors.toMap(WaybillSummary::waybillId, Function.identity(), (a, b) -> a));
        List<RiskAttribution> results = new ArrayList<>();
        results.addAll(groupAttribution("异常类型", exceptions, ExceptionEvent::exceptionType,
                value -> value + "是窗口内最集中的异常类型，应优先复盘对应流程和节点。"));
        results.addAll(groupAttribution("责任归属", exceptions, ExceptionEvent::responsibilityParty,
                value -> value + "相关异常占比较高，需要明确责任证据和对客口径。"));

        Map<String, List<ExceptionEvent>> byRoute = exceptions.stream()
                .collect(Collectors.groupingBy(event -> routeLabel(waybillMap.get(event.waybillId()))));
        byRoute.entrySet().stream()
                .filter(entry -> !"未知线路".equals(entry.getKey()))
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(2)
                .map(entry -> toAttribution("线路", entry.getKey(), entry.getValue(), exceptions.size(),
                        entry.getKey() + " 异常集中，建议检查干线、中转和末端派送资源。"))
                .forEach(results::add);
        return results.stream().limit(7).toList();
    }

    private List<RiskAttribution> groupAttribution(String dimension, List<ExceptionEvent> exceptions,
                                                   Function<ExceptionEvent, String> classifier,
                                                   Function<String, String> interpretation) {
        return exceptions.stream()
                .collect(Collectors.groupingBy(classifier))
                .entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(3)
                .map(entry -> toAttribution(dimension, entry.getKey(), entry.getValue(), exceptions.size(),
                        interpretation.apply(entry.getKey())))
                .toList();
    }

    private RiskAttribution toAttribution(String dimension, String name, List<ExceptionEvent> events,
                                          int total, String interpretation) {
        List<String> evidence = events.stream()
                .map(ExceptionEvent::waybillId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(5)
                .toList();
        return new RiskAttribution(dimension, name, events.size(), round(events.size() * 100.0 / total),
                evidence, interpretation);
    }

    private String routeLabel(WaybillSummary waybill) {
        if (waybill == null) {
            return "未知线路";
        }
        return waybill.originCity() + "-" + waybill.destCity();
    }

    private List<SlaAssessment> buildSlaAssessments(List<WaybillSummary> waybills,
                                                    List<ExceptionEvent> exceptions,
                                                    List<SlaRule> slaRules) {
        if (waybills == null || waybills.isEmpty()) {
            return List.of();
        }
        Set<String> exceptionWaybillIds = exceptions == null ? Set.of() : exceptions.stream()
                .map(ExceptionEvent::waybillId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, List<ExceptionEvent>> exceptionMap = exceptions == null ? Map.of() : exceptions.stream()
                .collect(Collectors.groupingBy(ExceptionEvent::waybillId));
        return waybills.stream()
                .filter(waybill -> exceptionWaybillIds.contains(waybill.waybillId()) || isDelayed(waybill)
                        || "EXCEPTION".equals(waybill.status()))
                .sorted(Comparator.comparing(WaybillSummary::orderDate).reversed())
                .limit(8)
                .map(waybill -> assessWaybillSla(waybill, exceptionMap.getOrDefault(waybill.waybillId(), List.of()), slaRules))
                .toList();
    }

    private SlaAssessment assessWaybillSla(WaybillSummary waybill, List<ExceptionEvent> exceptions, List<SlaRule> slaRules) {
        SlaRule matchedRule = matchRule(waybill, slaRules);
        long delayHours = delayHours(waybill);
        boolean delayed = delayHours > 0;
        boolean customerCaused = exceptions.stream().anyMatch(event -> "客户信息".equals(event.responsibilityParty()));
        boolean unresolved = exceptions.stream().anyMatch(event -> !event.resolved()) || "EXCEPTION".equals(waybill.status());

        String judgement;
        if (!delayed && !unresolved) {
            judgement = "暂不满足赔付候选：当前未看到超承诺时效或未解决异常。";
        } else if (customerCaused) {
            judgement = "需人工复核：存在客户信息原因，不能直接按承运责任赔付。";
        } else if (matchedRule == null) {
            judgement = "需人工复核：未匹配到明确 SLA 规则。";
        } else if (waybill.actualDeliveryTime() == null) {
            judgement = "赔付候选待定：运单尚未签收，需要等待实际签收时间和责任认定。";
        } else {
            judgement = "潜在赔付候选：已晚于承诺时效，需结合责任归属和合同凭证人工确认。";
        }

        List<String> evidence = new ArrayList<>();
        evidence.add("承诺送达：" + waybill.promisedDeliveryTime());
        evidence.add("实际签收：" + (waybill.actualDeliveryTime() == null ? "未签收" : waybill.actualDeliveryTime()));
        if (delayHours > 0) {
            evidence.add("超承诺时效约 " + delayHours + " 小时");
        }
        exceptions.stream().limit(3).forEach(event -> evidence.add(event.exceptionType() + "/"
                + event.severity() + "，责任归属 " + event.responsibilityParty() + "，影响 "
                + event.impactHours() + " 小时"));

        return new SlaAssessment(
                waybill.waybillId(),
                waybill.serviceType(),
                waybill.status(),
                matchedRule == null ? null : matchedRule.slaId(),
                matchedRule == null ? null : matchedRule.promiseHours(),
                waybill.promisedDeliveryTime(),
                waybill.actualDeliveryTime(),
                delayHours,
                judgement,
                matchedRule == null ? "未匹配到明确 SLA 规则" : matchedRule.delayCompensationRule(),
                evidence
        );
    }

    private SlaRule matchRule(WaybillSummary waybill, List<SlaRule> slaRules) {
        if (slaRules == null || slaRules.isEmpty()) {
            return null;
        }
        return slaRules.stream()
                .filter(rule -> rule.serviceType().equals(waybill.serviceType()))
                .findFirst()
                .orElse(null);
    }

    private boolean isDelayed(WaybillSummary waybill) {
        return delayHours(waybill) > 0;
    }

    private long delayHours(WaybillSummary waybill) {
        LocalDateTime actualOrObservation = waybill.actualDeliveryTime();
        if (actualOrObservation == null && "EXCEPTION".equals(waybill.status())) {
            actualOrObservation = seedDate.plusDays(1).atStartOfDay();
        }
        if (actualOrObservation == null || !actualOrObservation.isAfter(waybill.promisedDeliveryTime())) {
            return 0;
        }
        long minutes = Duration.between(waybill.promisedDeliveryTime(), actualOrObservation).toMinutes();
        return Math.max(1, (long) Math.ceil(minutes / 60.0));
    }

    private List<String> buildNextActions(CustomerProfile customer, DiagnosisReport diagnosis,
                                          List<RiskAttribution> attributions,
                                          List<SlaAssessment> slaAssessments,
                                          List<TicketRecord> tickets) {
        List<String> actions = new ArrayList<>();
        if (customer != null && "VIP".equals(customer.customerLevel())) {
            actions.add("按 VIP 客户异常沟通规范，回复中包含当前节点、原因、负责人和下一次同步时间。");
        }
        if (diagnosis != null && diagnosis.exceptionRate() >= 12) {
            actions.add("将客户标记为高关注，运营复盘主要异常类型和高频线路，未来 7 天开启节点预警。");
        }
        attributions.stream().findFirst().ifPresent(attribution ->
                actions.add("优先治理“" + attribution.name() + "”，抽取样本运单 " + String.join("、", attribution.evidenceWaybillIds()) + " 做根因复盘。"));
        if (slaAssessments.stream().anyMatch(assessment -> assessment.compensationJudgement().contains("候选"))) {
            actions.add("对 SLA/赔付候选运单补齐签收时间、异常责任归属、合同条款和原始凭证后再给最终结论。");
        }
        if (tickets != null && tickets.stream().anyMatch(ticket -> "PROCESSING".equals(ticket.status()))) {
            actions.add("对处理中工单设置预计反馈时间，减少客户重复投诉。");
        }
        if (actions.isEmpty()) {
            actions.add("当前风险可控，保持常规 SLA 监控并持续观察未来 7 天异常趋势。");
        }
        return actions;
    }

    private String resolveRiskLevel(boolean suspicious, DiagnosisReport diagnosis,
                                    List<SlaAssessment> slaAssessments,
                                    List<TicketRecord> tickets) {
        if (suspicious) {
            return "L4";
        }
        if (slaAssessments.stream().anyMatch(assessment -> assessment.compensationJudgement().contains("候选"))
                || (tickets != null && tickets.stream().anyMatch(ticket -> "P1".equals(ticket.priority())))) {
            return "L3";
        }
        if (diagnosis != null && (diagnosis.exceptionRate() >= 8 || diagnosis.ticketCount() > 0)) {
            return "L2";
        }
        return "L1";
    }

    private String buildNarrative(DiagnosisWindow window,
                                  CustomerProfile customer,
                                  DiagnosisReport diagnosis,
                                  List<RiskAttribution> attributions,
                                  List<SlaAssessment> slaAssessments,
                                  List<String> nextActions,
                                  List<KnowledgeSearchResult> knowledgeResults,
                                  boolean suspicious) {
        StringBuilder answer = new StringBuilder();
        answer.append("摘要：\n");
        if (suspicious) {
            answer.append("检测到疑似提示词注入或越权表达，已忽略该类指令，仅基于工具结果和知识库依据诊断。\n");
        }
        if (customer != null) {
            answer.append("客户 ").append(customer.customerId()).append("（").append(customer.customerName())
                    .append("）在 ").append(window.label()).append(" 的诊断已完成，客户等级 ")
                    .append(customer.customerLevel()).append("，当前风险 ").append(customer.riskLevel()).append("。\n");
        }
        if (diagnosis != null) {
            answer.append("- 运单 ").append(diagnosis.waybillCount()).append(" 票，异常 ")
                    .append(diagnosis.exceptionCount()).append(" 起，工单/投诉 ")
                    .append(diagnosis.ticketCount()).append(" 起。\n");
            answer.append("- 异常率 ").append(diagnosis.exceptionRate()).append("%，投诉率 ")
                    .append(diagnosis.complaintRate()).append("%，主要风险：")
                    .append(diagnosis.mainRisk()).append("。\n");
        }

        answer.append("\n异常归因：\n");
        if (attributions.isEmpty()) {
            answer.append("- 当前窗口没有足够异常事件形成聚类归因。\n");
        } else {
            attributions.stream().limit(5).forEach(attribution -> answer.append("- ")
                    .append(attribution.dimension()).append("：").append(attribution.name())
                    .append("，").append(attribution.count()).append(" 起，占异常 ")
                    .append(attribution.ratio()).append("%；样本运单 ")
                    .append(String.join("、", attribution.evidenceWaybillIds())).append("。")
                    .append(attribution.interpretation()).append("\n"));
        }

        answer.append("\nSLA/赔付候选：\n");
        if (slaAssessments.isEmpty()) {
            answer.append("- 当前窗口没有识别到明显 SLA/赔付候选运单。\n");
        } else {
            slaAssessments.stream().limit(5).forEach(assessment -> answer.append("- ")
                    .append(assessment.waybillId()).append("，").append(assessment.serviceType())
                    .append("，状态 ").append(assessment.status()).append("，超时 ")
                    .append(assessment.delayHours()).append(" 小时；")
                    .append(assessment.compensationJudgement()).append("\n"));
        }

        answer.append("\n相关制度 / 规则：\n");
        if (knowledgeResults.isEmpty()) {
            answer.append("- 本次没有检索到足够匹配的知识库片段。\n");
        } else {
            knowledgeResults.stream().limit(4).forEach(result -> answer.append("- ")
                    .append(result.chunk().title()).append("（")
                    .append(result.chunk().docId()).append(" / ")
                    .append(result.chunk().chunkId()).append("）：")
                    .append(excerpt(result.chunk().content(), 120)).append("\n"));
        }

        answer.append("\n建议下一步：\n");
        nextActions.forEach(action -> answer.append("- ").append(action).append("\n"));
        answer.append("\n风险提示：\n");
        answer.append("- 赔付、合同和责任认定只输出候选判断，最终结论需要人工复核合同、签收、温控、照片或分拨记录等原始凭证。\n");
        return answer.toString();
    }

    private ModelNarrative maybeGenerateDeepSeekNarrative(String message,
                                                          String fallbackNarrative,
                                                          List<KnowledgeSearchResult> knowledgeResults,
                                                          CustomerProfile customer,
                                                          DiagnosisReport diagnosis,
                                                          List<RiskAttribution> attributions,
                                                          List<SlaAssessment> slaAssessments,
                                                          List<String> nextActions,
                                                          List<ToolCallSummary> toolCalls) {
        if (!deepSeekEnabled) {
            return new ModelNarrative(fallbackNarrative, "local-structured-diagnosis");
        }
        ChatClient chatClient = deepSeekChatClient.getIfAvailable();
        if (chatClient == null) {
            return new ModelNarrative(fallbackNarrative, "local-structured-diagnosis");
        }
        long start = System.nanoTime();
        try {
            String answer = chatClient.prompt()
                    .user(buildDeepSeekPrompt(message, knowledgeResults, customer, diagnosis, attributions, slaAssessments, nextActions))
                    .call()
                    .content();
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary("deepSeekChatClient", "success",
                    "DeepSeek 已基于结构化证据生成客户异常诊断叙述", latencyMs, null));
            return new ModelNarrative(answer, "deepseek");
        } catch (RuntimeException ex) {
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            toolCalls.add(new ToolCallSummary("deepSeekChatClient", "failed",
                    "DeepSeek 诊断叙述生成失败，已回退本地结构化诊断：" + ex.getMessage(), latencyMs, "MODEL_ERROR"));
            return new ModelNarrative(fallbackNarrative, "local-structured-diagnosis");
        }
    }

    private String buildDeepSeekPrompt(String message,
                                       List<KnowledgeSearchResult> knowledgeResults,
                                       CustomerProfile customer,
                                       DiagnosisReport diagnosis,
                                       List<RiskAttribution> attributions,
                                       List<SlaAssessment> slaAssessments,
                                       List<String> nextActions) {
        return """
                用户问题：
                %s

                业务证据：
                客户：%s
                诊断指标：%s
                异常归因：%s
                SLA/赔付候选：%s
                建议动作：%s

                知识库依据：
                %s

                请输出企业内部客服/运营可用的客户异常诊断报告，必须包含：
                摘要、关键指标、异常归因、SLA/赔付候选、建议下一步、风险提示、引用来源。
                不得新增业务事实；赔付只能说候选或需人工复核。
                """.formatted(message, customer, diagnosis, attributions, slaAssessments, nextActions,
                formatKnowledgeContext(knowledgeResults));
    }

    private String formatKnowledgeContext(List<KnowledgeSearchResult> knowledgeResults) {
        if (knowledgeResults == null || knowledgeResults.isEmpty()) {
            return "未检索到匹配知识库片段。";
        }
        return knowledgeResults.stream()
                .map(result -> "- " + result.chunk().title() + "（" + result.chunk().docId()
                        + " / " + result.chunk().chunkId() + "）：" + result.chunk().content())
                .collect(Collectors.joining("\n"));
    }

    private Citation toCitation(KnowledgeSearchResult result) {
        return new Citation("knowledge", result.chunk().title(), result.chunk().docId(),
                result.chunk().chunkId(), excerpt(result.chunk().content(), 120));
    }

    private boolean shouldReturnCitations(CustomerDiagnosisRequest request) {
        return request.returnCitations() == null || request.returnCitations();
    }

    private double confidence(List<ToolCallSummary> toolCalls, List<KnowledgeSearchResult> knowledgeResults,
                              DiagnosisReport diagnosis) {
        double value = 0.55;
        if (diagnosis != null) {
            value += 0.15;
        }
        if (!knowledgeResults.isEmpty()) {
            value += 0.15;
        }
        if (!toolCalls.isEmpty() && toolCalls.stream().allMatch(call -> "success".equals(call.status()))) {
            value += 0.1;
        }
        return Math.min(0.95, round(value));
    }

    private String summarizeCustomer(CustomerProfile customer) {
        if (customer == null) {
            return "未找到客户";
        }
        return customer.customerId() + " " + customer.customerName() + "，等级 " + customer.customerLevel()
                + "，风险 " + customer.riskLevel();
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

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
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

    private record ModelNarrative(String text, String provider) {
    }
}
