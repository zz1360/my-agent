package com.superagent.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogisticsAgentApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void actionAdminConsoleStaticPageLoads() throws Exception {
        String body = mockMvc.perform(get("/admin/actions.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .contains("物流 Agent 管理台")
                .contains("执行指标")
                .contains("失败重试队列")
                .contains("业务回链")
                .contains("/api/agent/actions/executions/metrics");
    }

    @Test
    void chatConsoleStaticPageLoads() throws Exception {
        String body = mockMvc.perform(get("/chat.html"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .contains("物流 Agent 对话台")
                .contains("常用问题")
                .contains("回答详情")
                .contains("/api/agent/chat")
                .contains("/api/agent/chat/stream")
                .contains("/api/agent/conversations")
                .contains("data-feedback")
                .contains("/api/demo/questions");
    }

    @Test
    void flywayMigratesSchemaAndSeedsEvalCases() {
        Integer migrations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
        Integer evalCases = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_eval_case WHERE tenant_id = ?", Integer.class, "T001");
        Integer ragCases = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_eval_case
                WHERE tenant_id = ? AND eval_type = 'RAG'
                """, Integer.class, "T001");
        String coldChainExpectedDoc = jdbcTemplate.queryForObject("""
                SELECT expected_rag_doc_ids FROM ai_eval_case
                WHERE tenant_id = ? AND case_id = ?
                """, String.class, "T001", "rag-cold-chain-policy-hybrid");

        assertThat(migrations).isNotNull().isGreaterThanOrEqualTo(3);
        assertThat(evalCases).isNotNull().isGreaterThanOrEqualTo(5);
        assertThat(ragCases).isNotNull().isGreaterThanOrEqualTo(2);
        assertThat(coldChainExpectedDoc).contains("policy-cold-chain-v2");
        Integer ragExperiments = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_rag_experiment
                WHERE tenant_id = ?
                """, Integer.class, "T001");
        assertThat(migrations).isGreaterThanOrEqualTo(8);
        assertThat(ragExperiments).isNotNull().isGreaterThanOrEqualTo(2);
        Integer actionDrafts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_agent_action_draft", Integer.class);
        assertThat(actionDrafts).isNotNull();
        Integer actionExecutions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_agent_action_execution", Integer.class);
        assertThat(actionExecutions).isNotNull();
        Integer ticketNotes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM logistics_ticket_note", Integer.class);
        Integer opsTasks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM logistics_ops_task", Integer.class);
        Integer replyDrafts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM logistics_customer_reply_draft", Integer.class);
        Integer compensationReviews = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM logistics_compensation_review_task", Integer.class);
        assertThat(ticketNotes).isNotNull();
        assertThat(opsTasks).isNotNull();
        assertThat(replyDrafts).isNotNull();
        assertThat(compensationReviews).isNotNull();
        assertThat(migrations).isGreaterThanOrEqualTo(9);
        Integer conversations = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_agent_conversation", Integer.class);
        Integer messages = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_agent_message", Integer.class);
        Integer feedback = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_agent_message_feedback", Integer.class);
        assertThat(conversations).isNotNull();
        assertThat(messages).isNotNull();
        assertThat(feedback).isNotNull();
    }

    @Test
    void chatReturnsBusinessDataCitationsAndToolCalls() throws Exception {
        String payload = """
                {
                  "conversationId": "conv-test-001",
                  "userId": "u-cs-test",
                  "tenantId": "T001",
                  "roles": ["CUSTOMER_SERVICE"],
                  "message": "运单 WB202606010023 是否可能满足延误赔付条件？",
                  "returnCitations": true
                }
                """;

        String body = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("traceId").asText()).startsWith("trace-");
        assertThat(response.get("messageId").asText()).startsWith("msg-ai-");
        assertThat(response.get("riskLevel").asText()).isEqualTo("L3");
        assertThat(response.get("answer").asText()).contains("WB202606010023", "运输时效与延误赔付政策");
        assertThat(response.get("citations")).isNotEmpty();
        assertThat(response.get("toolCalls")).hasSize(2);
    }

    @Test
    void chatPersistsConversationHistoryAndFeedback() throws Exception {
        String conversationId = "conv-test-history";
        String payload = """
                {
                  "conversationId": "%s",
                  "userId": "u-cs-history",
                  "tenantId": "T001",
                  "roles": ["CUSTOMER_SERVICE"],
                  "message": "客户 C001 最近 30 天为什么投诉量上升？相关处理制度是什么？",
                  "returnCitations": true
                }
                """.formatted(conversationId);

        String chatBody = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode chat = objectMapper.readTree(chatBody);
        String messageId = chat.get("messageId").asText();
        assertThat(messageId).startsWith("msg-ai-");

        String listBody = mockMvc.perform(get("/api/agent/conversations")
                        .param("tenantId", "T001")
                        .param("userId", "u-cs-history")
                        .param("roles", "CUSTOMER_SERVICE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode conversations = objectMapper.readTree(listBody);
        assertThat(conversations).anySatisfy(row ->
                assertThat(row.get("conversationId").asText()).isEqualTo(conversationId));

        String detailBody = mockMvc.perform(get("/api/agent/conversations/{conversationId}", conversationId)
                        .param("tenantId", "T001")
                        .param("userId", "u-cs-history")
                        .param("roles", "CUSTOMER_SERVICE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detail = objectMapper.readTree(detailBody);
        assertThat(detail.get("messages")).hasSize(2);
        assertThat(detail.get("messages").get(1).get("messageId").asText()).isEqualTo(messageId);
        assertThat(detail.get("messages").get(1).get("citations")).isNotEmpty();

        String feedbackPayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-cs-history",
                  "roles": ["CUSTOMER_SERVICE"],
                  "conversationId": "%s",
                  "traceId": "%s",
                  "rating": "HELPFUL",
                  "reason": "ANSWER_USEFUL",
                  "comment": "引用和工具链路清楚"
                }
                """.formatted(conversationId, chat.get("traceId").asText());
        String feedbackBody = mockMvc.perform(post("/api/agent/messages/{messageId}/feedback", messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode feedback = objectMapper.readTree(feedbackBody);
        assertThat(feedback.get("feedbackId").asText()).startsWith("fb-");
        assertThat(feedback.get("rating").asText()).isEqualTo("HELPFUL");
    }

    @Test
    void chatStreamReturnsSseEvents() throws Exception {
        String payload = """
                {
                  "conversationId": "conv-test-stream",
                  "userId": "u-cs-stream",
                  "tenantId": "T001",
                  "roles": ["CUSTOMER_SERVICE"],
                  "message": "运单 WB202606010023 是否可能满足延误赔付条件？",
                  "returnCitations": true
                }
                """;

        var result = mockMvc.perform(post("/api/agent/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("event:status")
                .contains("event:delta")
                .contains("event:complete")
                .contains("traceId")
                .contains("messageId");
    }

    @Test
    void auditCanBeReadAfterChat() throws Exception {
        String payload = """
                {
                  "conversationId": "conv-test-audit",
                  "userId": "u-cs-test",
                  "tenantId": "T001",
                  "roles": ["CUSTOMER_SERVICE"],
                  "message": "客户 C001 最近 30 天为什么投诉量上升？相关处理制度是什么？",
                  "returnCitations": true
                }
                """;

        String chatBody = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String traceId = objectMapper.readTree(chatBody).get("traceId").asText();

        String auditBody = mockMvc.perform(get("/api/agent/audit/{traceId}", traceId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode audit = objectMapper.readTree(auditBody);
        assertThat(audit.get("traceId").asText()).isEqualTo(traceId);
        assertThat(audit.get("toolCalls")).isNotEmpty();
        assertThat(audit.get("finalAnswer").asText()).contains("客户 C001");
    }

    @Test
    void customerDiagnosisReturnsStructuredSlaCandidatesAndCanBeSearchedInAudit() throws Exception {
        String payload = """
                {
                  "conversationId": "conv-test-diagnosis",
                  "userId": "u-cs-test",
                  "tenantId": "T001",
                  "roles": ["CUSTOMER_SERVICE"],
                  "customerId": "C001",
                  "days": 30,
                  "message": "客户 C001 最近 30 天投诉为什么上升，是否满足赔付条件，下一步怎么处理？",
                  "returnCitations": true
                }
                """;

        String body = mockMvc.perform(post("/api/agent/customer-diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(body);
        String traceId = response.get("traceId").asText();
        assertThat(traceId).startsWith("trace-");
        assertThat(response.get("customer").get("customerId").asText()).isEqualTo("C001");
        assertThat(response.get("diagnosis").get("exceptionRate").asDouble()).isGreaterThan(0);
        assertThat(response.get("attributions")).isNotEmpty();
        assertThat(response.get("slaAssessments")).isNotEmpty();
        assertThat(response.get("narrative").asText()).contains("SLA/赔付候选", "人工复核");
        assertThat(response.get("citations")).isNotEmpty();
        assertThat(response.get("toolCalls")).hasSizeGreaterThanOrEqualTo(6);

        String auditBody = mockMvc.perform(get("/api/agent/audit")
                        .param("tenantId", "T001")
                        .param("customerId", "C001")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode auditRows = objectMapper.readTree(auditBody);
        assertThat(auditRows).anySatisfy(row ->
                assertThat(row.get("traceId").asText()).isEqualTo(traceId));
    }

    @Test
    void diagnosisCanGenerateReviewAndListHumanApprovedActionDrafts() throws Exception {
        String diagnosisPayload = """
                {
                  "conversationId": "conv-test-action-diagnosis",
                  "userId": "u-cs-test",
                  "tenantId": "T001",
                  "roles": ["CUSTOMER_SERVICE"],
                  "customerId": "C001",
                  "days": 30,
                  "message": "请基于客户 C001 的异常诊断生成后续动作建议。",
                  "returnCitations": true
                }
                """;

        String diagnosisBody = mockMvc.perform(post("/api/agent/customer-diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diagnosisPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String traceId = objectMapper.readTree(diagnosisBody).get("traceId").asText();

        String generatePayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-cs-test",
                  "roles": ["CUSTOMER_SERVICE"],
                  "traceId": "%s",
                  "conversationId": "conv-test-actions",
                  "customerId": "C001",
                  "days": 30
                }
                """.formatted(traceId);
        String actionBody = mockMvc.perform(post("/api/agent/actions/from-diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generatePayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode actions = objectMapper.readTree(actionBody);
        assertThat(actions).hasSizeGreaterThanOrEqualTo(3);
        assertThat(actions).allSatisfy(action -> {
            assertThat(action.get("status").asText()).isEqualTo("PENDING_REVIEW");
            assertThat(action.get("customerId").asText()).isEqualTo("C001");
            assertThat(action.get("evidenceJson").asText()).contains("diagnosis", "sampleWaybills");
        });
        assertThat(actions).anySatisfy(action ->
                assertThat(action.get("actionType").asText()).isEqualTo("CUSTOMER_REPLY"));
        assertThat(actions).anySatisfy(action ->
                assertThat(action.get("actionType").asText()).isEqualTo("TICKET_NOTE"));
        assertThat(actions).anySatisfy(action ->
                assertThat(action.get("actionType").asText()).isEqualTo("COMPENSATION_REVIEW"));

        String compensationActionId = null;
        for (JsonNode action : actions) {
            if ("COMPENSATION_REVIEW".equals(action.get("actionType").asText())) {
                compensationActionId = action.get("actionId").asText();
                assertThat(action.get("draftContent").asText()).contains("人工复核", "SLA/补偿争议");
                break;
            }
        }
        assertThat(compensationActionId).isNotBlank();
        String approvedActionId = compensationActionId;

        String reviewPayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-reviewer",
                  "roles": ["OPS_MANAGER"],
                  "status": "APPROVED",
                  "comment": "证据完整，进入人工赔付核算。"
                }
                """;
        String reviewedBody = mockMvc.perform(post("/api/agent/actions/{actionId}/review", compensationActionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode reviewed = objectMapper.readTree(reviewedBody);
        assertThat(reviewed.get("status").asText()).isEqualTo("APPROVED");
        assertThat(reviewed.get("reviewerId").asText()).isEqualTo("u-ops-reviewer");
        assertThat(reviewed.get("reviewComment").asText()).contains("证据完整");

        String approvedBody = mockMvc.perform(get("/api/agent/actions")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("customerId", "C001")
                        .param("status", "APPROVED")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode approvedActions = objectMapper.readTree(approvedBody);
        assertThat(approvedActions).anySatisfy(action ->
                assertThat(action.get("actionId").asText()).isEqualTo(approvedActionId));
    }

    @Test
    void approvedActionsCanBeExecutedWithLowRiskAutomationAndManualHighRiskGuard() throws Exception {
        JsonNode actions = generateActionDraftsForC001("conv-test-action-execution");
        String ticketActionId = firstActionId(actions, "TICKET_NOTE");
        String compensationActionId = firstActionId(actions, "COMPENSATION_REVIEW");

        approveAction(ticketActionId, "内部工单备注可以先保存为待发布草稿。");
        approveAction(compensationActionId, "赔付复核只进入人工队列，不直接赔付。");

        String automationPayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-automation",
                  "roles": ["OPS_MANAGER"],
                  "customerId": "C001",
                  "limit": 10
                }
                """;
        String automationBody = mockMvc.perform(post("/api/agent/actions/automation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(automationPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode automation = objectMapper.readTree(automationBody);
        assertThat(automation.get("scanned").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(automation.get("executed").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(automation.get("skipped").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(automation.get("executions")).anySatisfy(execution -> {
            assertThat(execution.get("actionId").asText()).isEqualTo(ticketActionId);
            assertThat(execution.get("targetSystem").asText()).isEqualTo("BUSINESS_TICKET_NOTE_TABLE");
            assertThat(execution.get("externalRefId").asText()).startsWith("ticket-note-");
            assertThat(execution.get("idempotencyKey").asText()).contains("auto-");
            assertThat(execution.get("lowRisk").asBoolean()).isTrue();
            assertThat(execution.get("status").asText()).isEqualTo("SUCCESS");
            assertThat(execution.get("responseJson").asText()).contains("ticketNoteDraftSaved");
        });
        Integer noteRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM logistics_ticket_note
                WHERE tenant_id = ? AND action_id = ?
                """, Integer.class, "T001", ticketActionId);
        assertThat(noteRows).isEqualTo(1);

        String appliedTicketBody = mockMvc.perform(get("/api/agent/actions/{actionId}", ticketActionId)
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(appliedTicketBody).get("status").asText()).isEqualTo("APPLIED");

        String stillApprovedCompensationBody = mockMvc.perform(get("/api/agent/actions/{actionId}", compensationActionId)
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(stillApprovedCompensationBody).get("status").asText()).isEqualTo("APPROVED");

        String executeWithoutForce = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-automation",
                  "roles": ["OPS_MANAGER"],
                  "comment": "尝试直接执行高风险动作"
                }
                """;
        mockMvc.perform(post("/api/agent/actions/{actionId}/execute", compensationActionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executeWithoutForce))
                .andExpect(status().isBadRequest());

        String executeWithForce = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-automation",
                  "roles": ["OPS_MANAGER"],
                  "force": true,
                  "comment": "人工确认只创建赔付复核队列，不直接赔付。"
                }
                """;
        String forcedExecutionBody = mockMvc.perform(post("/api/agent/actions/{actionId}/execute", compensationActionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executeWithForce))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode forcedExecution = objectMapper.readTree(forcedExecutionBody);
        assertThat(forcedExecution.get("targetSystem").asText()).isEqualTo("BUSINESS_COMPENSATION_REVIEW_TABLE");
        assertThat(forcedExecution.get("lowRisk").asBoolean()).isFalse();
        assertThat(forcedExecution.get("responseJson").asText()).contains("paymentCreated\":false", "manualAmountRequired");
        Integer compensationRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM logistics_compensation_review_task
                WHERE tenant_id = ? AND action_id = ?
                """, Integer.class, "T001", compensationActionId);
        assertThat(compensationRows).isEqualTo(1);

        String executionHistoryBody = mockMvc.perform(get("/api/agent/actions/{actionId}/executions", compensationActionId)
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode executionHistory = objectMapper.readTree(executionHistoryBody);
        assertThat(executionHistory).anySatisfy(execution ->
                assertThat(execution.get("executionId").asText()).isEqualTo(forcedExecution.get("executionId").asText()));
    }

    @Test
    void actionExecutionIsIdempotentAndFailedExecutionCanBeRetried() throws Exception {
        JsonNode actions = generateActionDraftsForC001("conv-test-action-retry");
        String opsActionId = firstActionId(actions, "OPERATIONS_FOLLOW_UP");
        approveAction(opsActionId, "运营复盘任务可以创建。");

        String failurePayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-retry",
                  "roles": ["OPS_MANAGER"],
                  "idempotencyKey": "ops-retry-key",
                  "simulateFailure": true,
                  "comment": "模拟外部任务中心失败"
                }
                """;
        mockMvc.perform(post("/api/agent/actions/{actionId}/execute", opsActionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failurePayload))
                .andExpect(status().isBadRequest());

        String failedExecutionId = jdbcTemplate.queryForObject("""
                SELECT execution_id FROM ai_agent_action_execution
                WHERE tenant_id = ? AND action_id = ? AND status = 'FAILED'
                ORDER BY started_at DESC LIMIT 1
                """, String.class, "T001", opsActionId);
        assertThat(failedExecutionId).isNotBlank();
        Integer retryableFailures = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_agent_action_execution
                WHERE tenant_id = ? AND execution_id = ? AND next_retry_at IS NOT NULL
                """, Integer.class, "T001", failedExecutionId);
        assertThat(retryableFailures).isEqualTo(1);

        String retryQueueBody = mockMvc.perform(get("/api/agent/actions/executions/retry-queue")
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER")
                        .param("dueOnly", "false")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode retryQueue = objectMapper.readTree(retryQueueBody);
        assertThat(retryQueue).anySatisfy(execution ->
                assertThat(execution.get("executionId").asText()).isEqualTo(failedExecutionId));

        String retryPayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-retry",
                  "roles": ["OPS_MANAGER"],
                  "comment": "外部任务中心恢复后重试"
                }
                """;
        String retryBody = mockMvc.perform(post("/api/agent/actions/executions/{executionId}/retry", failedExecutionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retryPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode retryExecution = objectMapper.readTree(retryBody);
        assertThat(retryExecution.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(retryExecution.get("retryCount").asInt()).isEqualTo(1);
        assertThat(retryExecution.get("targetSystem").asText()).isEqualTo("BUSINESS_OPS_TASK_TABLE");
        assertThat(retryExecution.get("responseJson").asText()).contains("opsTaskCreated");

        String searchBody = mockMvc.perform(get("/api/agent/actions/executions")
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER")
                        .param("status", "SUCCESS")
                        .param("actionType", "OPERATIONS_FOLLOW_UP")
                        .param("targetSystem", "BUSINESS_OPS_TASK_TABLE")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode searchResults = objectMapper.readTree(searchBody);
        assertThat(searchResults).anySatisfy(execution ->
                assertThat(execution.get("executionId").asText()).isEqualTo(retryExecution.get("executionId").asText()));

        String metricsBody = mockMvc.perform(get("/api/agent/actions/executions/metrics")
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode metrics = objectMapper.readTree(metricsBody);
        assertThat(metrics.get("totalExecutions").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(metrics.get("successCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.get("failedCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.get("retryableFailedCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(metrics.get("byActionType")).anySatisfy(row ->
                assertThat(row.get("name").asText()).isEqualTo("OPERATIONS_FOLLOW_UP"));

        Integer opsTaskRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM logistics_ops_task
                WHERE tenant_id = ? AND action_id = ?
                """, Integer.class, "T001", opsActionId);
        assertThat(opsTaskRows).isEqualTo(1);

        String businessLinkBody = mockMvc.perform(get("/api/agent/actions/{actionId}/business-link", opsActionId)
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode businessLink = objectMapper.readTree(businessLinkBody);
        assertThat(businessLink.get("businessTable").asText()).isEqualTo("logistics_ops_task");
        assertThat(businessLink.get("businessId").asText()).startsWith("ops-task-");
        assertThat(businessLink.get("latestExecutionId").asText()).isEqualTo(retryExecution.get("executionId").asText());

        String duplicatePayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-retry",
                  "roles": ["OPS_MANAGER"],
                  "idempotencyKey": "ops-retry-key",
                  "comment": "重复点击执行按钮"
                }
                """;
        String duplicateBody = mockMvc.perform(post("/api/agent/actions/{actionId}/execute", opsActionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicatePayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode duplicateExecution = objectMapper.readTree(duplicateBody);
        assertThat(duplicateExecution.get("executionId").asText()).isEqualTo(retryExecution.get("executionId").asText());
        Integer opsTaskRowsAfterDuplicate = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM logistics_ops_task
                WHERE tenant_id = ? AND action_id = ?
                """, Integer.class, "T001", opsActionId);
        assertThat(opsTaskRowsAfterDuplicate).isEqualTo(1);
    }

    @Test
    void knowledgeDocumentCanBeUpsertedSearchedAndDisabled() throws Exception {
        String payload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-test",
                  "roles": ["OPS_MANAGER"],
                  "docId": "sop-drone-delivery-test",
                  "title": "无人机派送预约 SOP 测试版",
                  "docType": "manual",
                  "bizDomain": "last_mile",
                  "version": "v0.1-test",
                  "aclRoles": ["CUSTOMER_SERVICE", "OPERATIONS", "OPS_MANAGER"],
                  "content": "无人机派送预约需要提前 2 小时确认天气、航线和收货人实名信息。若遇到低空管制，应切换为末端网点派送，并在工单中记录无人机派送预约失败原因。"
                }
                """;

        String upsertBody = mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode upsert = objectMapper.readTree(upsertBody);
        assertThat(upsert.get("docId").asText()).isEqualTo("sop-drone-delivery-test");
        assertThat(upsert.get("chunkCount").asInt()).isGreaterThanOrEqualTo(1);

        String searchBody = mockMvc.perform(get("/api/knowledge/search")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("query", "无人机派送预约失败怎么处理")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode search = objectMapper.readTree(searchBody);
        assertThat(search).anySatisfy(row ->
                assertThat(row.get("docId").asText()).isEqualTo("sop-drone-delivery-test"));

        mockMvc.perform(post("/api/knowledge/documents/{docId}/disable", "sop-drone-delivery-test")
                        .param("tenantId", "T001")
                        .param("userId", "u-ops-test")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk());

        String disabledSearchBody = mockMvc.perform(get("/api/knowledge/search")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("query", "无人机派送预约失败怎么处理")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode disabledSearch = objectMapper.readTree(disabledSearchBody);
        assertThat(disabledSearch).noneSatisfy(row ->
                assertThat(row.get("docId").asText()).isEqualTo("sop-drone-delivery-test"));
    }

    @Test
    void knowledgeDocumentSupportsPreviewDraftPublishExpireAndIndexJobs() throws Exception {
        String payload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-test",
                  "roles": ["OPS_MANAGER"],
                  "baseDocId": "sop-return-appointment-test",
                  "docId": "sop-return-appointment-test-v1",
                  "title": "逆向取件预约 SOP 测试版",
                  "docType": "manual",
                  "bizDomain": "reverse_logistics",
                  "version": "v1-test",
                  "status": "DRAFT",
                  "aclRoles": ["CUSTOMER_SERVICE", "OPERATIONS", "OPS_MANAGER"],
                  "content": "逆向取件预约需要先确认客户退货地址、可取件时间和包裹数量。\\n\\n若骑手两次联系失败，客服应生成二次预约任务，并记录失败原因。"
                }
                """;

        String previewBody = mockMvc.perform(post("/api/knowledge/documents/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode preview = objectMapper.readTree(previewBody);
        assertThat(preview.get("baseDocId").asText()).isEqualTo("sop-return-appointment-test");
        assertThat(preview.get("chunkCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(preview.get("chunks").get(0).get("metadata").asText()).contains("baseDocId=sop-return-appointment-test");

        String draftBody = mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode draft = objectMapper.readTree(draftBody);
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("baseDocId").asText()).isEqualTo("sop-return-appointment-test");
        assertThat(draft.get("indexJobId").asText()).startsWith("kb-index-");

        String draftSearchBody = mockMvc.perform(get("/api/knowledge/search")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("query", "逆向取件预约")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode draftSearch = objectMapper.readTree(draftSearchBody);
        assertThat(draftSearch).noneSatisfy(row ->
                assertThat(row.get("docId").asText()).isEqualTo("sop-return-appointment-test-v1"));

        String publishBody = mockMvc.perform(post("/api/knowledge/documents/{docId}/publish", "sop-return-appointment-test-v1")
                        .param("tenantId", "T001")
                        .param("userId", "u-ops-test")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode published = objectMapper.readTree(publishBody);
        assertThat(published.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(published.get("publishedAt").asText()).isNotBlank();
        String jobId = published.get("indexJobId").asText();
        JsonNode job = awaitIndexJob(jobId);
        assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.get("triggerType").asText()).isEqualTo("DOCUMENT_PUBLISH");
        assertThat(job.get("chunkCount").asInt()).isGreaterThanOrEqualTo(1);

        String listBody = mockMvc.perform(get("/api/knowledge/documents")
                        .param("tenantId", "T001")
                        .param("roles", "OPS_MANAGER")
                        .param("baseDocId", "sop-return-appointment-test")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode docs = objectMapper.readTree(listBody);
        assertThat(docs).anySatisfy(row ->
                assertThat(row.get("docId").asText()).isEqualTo("sop-return-appointment-test-v1"));

        String activeSearchBody = mockMvc.perform(get("/api/knowledge/search")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("query", "逆向取件预约")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode activeSearch = objectMapper.readTree(activeSearchBody);
        assertThat(activeSearch).anySatisfy(row ->
                assertThat(row.get("docId").asText()).isEqualTo("sop-return-appointment-test-v1"));

        mockMvc.perform(post("/api/knowledge/documents/{docId}/expire", "sop-return-appointment-test-v1")
                        .param("tenantId", "T001")
                        .param("userId", "u-ops-test")
                        .param("roles", "OPS_MANAGER"))
                .andExpect(status().isOk());

        String expiredSearchBody = mockMvc.perform(get("/api/knowledge/search")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("query", "逆向取件预约")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode expiredSearch = objectMapper.readTree(expiredSearchBody);
        assertThat(expiredSearch).noneSatisfy(row ->
                assertThat(row.get("docId").asText()).isEqualTo("sop-return-appointment-test-v1"));
    }

    @Test
    void hybridSearchRanksColdChainPolicyForTemperatureQuery() throws Exception {
        String body = mockMvc.perform(get("/api/knowledge/search")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("query", "冷链运输温度超过 10C 后客服应该怎么处理？")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode results = objectMapper.readTree(body);
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).get("docId").asText()).isEqualTo("policy-cold-chain-v2");
        assertThat(results.get(0).get("chunkId").asText()).isEqualTo("policy-cold-chain-v2-chunk-001");
        assertThat(results.get(0).get("excerpt").asText()).contains("温控异常工单");
    }

    @Test
    void evalCaseApiExposesRagExpectations() throws Exception {
        String body = mockMvc.perform(get("/api/agent/evals/cases")
                        .param("tenantId", "T001")
                        .param("enabledOnly", "true"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode cases = objectMapper.readTree(body);
        assertThat(cases).anySatisfy(item -> {
            assertThat(item.get("caseId").asText()).isEqualTo("rag-delay-policy-hybrid");
            assertThat(item.get("evalType").asText()).isEqualTo("RAG");
            assertThat(item.get("expectedRagDocIds")).anySatisfy(doc ->
                    assertThat(doc.asText()).isEqualTo("policy-delay-v3"));
            assertThat(item.get("expectedRagChunkIds")).anySatisfy(chunk ->
                    assertThat(chunk.asText()).isEqualTo("policy-delay-v3-chunk-001"));
            assertThat(item.get("expectedTopK").asInt()).isEqualTo(5);
            assertThat(item.get("ragQuery").asText()).contains("补偿");
        });
    }

    @Test
    void ragExperimentLabCanCreateRunAndCompareRetrievalModes() throws Exception {
        String defaultsBody = mockMvc.perform(get("/api/rag/experiments")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode defaults = objectMapper.readTree(defaultsBody);
        assertThat(defaults).anySatisfy(item ->
                assertThat(item.get("experimentId").asText()).isEqualTo("raglab-cold-chain-treatment"));

        String payload = """
                {
                  "tenantId": "T001",
                  "userId": "u-rag-test",
                  "roles": ["CUSTOMER_SERVICE"],
                  "experimentId": "raglab-test-cold-chain",
                  "name": "冷链超温实验台测试",
                  "description": "验证不同检索模式是否命中冷链温控规范",
                  "query": "冷链运输温度超过 10C 后客服应该怎么处理？",
                  "expectedDocIds": ["policy-cold-chain-v2"],
                  "expectedChunkIds": ["policy-cold-chain-v2-chunk-001"],
                  "topK": 5,
                  "modes": ["KEYWORD_ONLY", "HYBRID_RERANKER"]
                }
                """;

        String experimentBody = mockMvc.perform(post("/api/rag/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode experiment = objectMapper.readTree(experimentBody);
        assertThat(experiment.get("experimentId").asText()).isEqualTo("raglab-test-cold-chain");
        assertThat(experiment.get("modes")).hasSize(2);

        String runBody = mockMvc.perform(post("/api/rag/experiments/{experimentId}/run", "raglab-test-cold-chain")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("modes", "KEYWORD_ONLY")
                        .param("modes", "HYBRID_RERANKER"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode runs = objectMapper.readTree(runBody);
        assertThat(runs).hasSize(2);
        assertThat(runs).allSatisfy(result -> {
            assertThat(result.get("status").asText()).isEqualTo("PASSED");
            assertThat(result.get("recallAtK").asDouble()).isEqualTo(1.0);
            assertThat(result.get("mrr").asDouble()).isEqualTo(1.0);
            assertThat(result.get("topDocIds")).anySatisfy(doc ->
                    assertThat(doc.asText()).isEqualTo("policy-cold-chain-v2"));
            assertThat(result.get("metricsJson").asText())
                    .contains("\"mode\"", "\"scores\"", "\"latencyMs\"", "\"recallAtK\"");
        });
        assertThat(runs).anySatisfy(result ->
                assertThat(result.get("mode").asText()).isEqualTo("KEYWORD_ONLY"));
        assertThat(runs).anySatisfy(result ->
                assertThat(result.get("mode").asText()).isEqualTo("HYBRID_RERANKER"));

        String historyBody = mockMvc.perform(get("/api/rag/experiments/{experimentId}/runs", "raglab-test-cold-chain")
                        .param("tenantId", "T001")
                        .param("roles", "CUSTOMER_SERVICE")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode history = objectMapper.readTree(historyBody);
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void defaultAgentEvalRunPasses() throws Exception {
        String runBody = mockMvc.perform(post("/api/agent/evals/run")
                        .param("tenantId", "T001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode run = objectMapper.readTree(runBody);
        assertThat(run.get("totalCases").asInt()).isGreaterThanOrEqualTo(5);
        assertThat(run.get("failedCases").asInt()).isEqualTo(0);
        assertThat(run.get("status").asText()).isEqualTo("PASSED");
        assertThat(run.get("results")).allSatisfy(result ->
                assertThat(result.get("passed").asBoolean()).isTrue());
        assertThat(run.get("results")).anySatisfy(result -> {
            assertThat(result.get("caseId").asText()).isEqualTo("rag-cold-chain-policy-hybrid");
            assertThat(result.get("ragHitRate").asDouble()).isEqualTo(1.0);
            assertThat(result.get("ragRecallAtK").asDouble()).isEqualTo(1.0);
            assertThat(result.get("ragPrecisionAtK").asDouble()).isGreaterThan(0);
            assertThat(result.get("ragMrr").asDouble()).isEqualTo(1.0);
            assertThat(result.get("ragNdcg").asDouble()).isEqualTo(1.0);
            assertThat(result.get("ragExpectedTotal").asInt()).isEqualTo(2);
            assertThat(result.get("ragHitCount").asInt()).isEqualTo(2);
            assertThat(result.get("ragTopDocIds")).anySatisfy(doc ->
                    assertThat(doc.asText()).isEqualTo("policy-cold-chain-v2"));
            assertThat(result.get("ragTopChunkIds")).anySatisfy(chunk ->
                    assertThat(chunk.asText()).isEqualTo("policy-cold-chain-v2-chunk-001"));
            assertThat(result.get("ragMetricsJson").asText())
                    .contains("\"scores\"", "\"recallAtK\"", "\"precisionAtK\"", "\"mrr\"", "\"ndcg\"",
                            "\"rerankerProvider\"");
        });

        String runId = run.get("runId").asText();
        String foundBody = mockMvc.perform(get("/api/agent/evals/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode found = objectMapper.readTree(foundBody);
        assertThat(found.get("runId").asText()).isEqualTo(runId);
    }

    private JsonNode awaitIndexJob(String jobId) throws Exception {
        for (int i = 0; i < 20; i++) {
            String body = mockMvc.perform(get("/api/knowledge/index-jobs/{jobId}", jobId)
                            .param("tenantId", "T001")
                            .param("userId", "u-ops-test")
                            .param("roles", "OPS_MANAGER"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            JsonNode job = objectMapper.readTree(body);
            if ("COMPLETED".equals(job.get("status").asText()) || "FAILED".equals(job.get("status").asText())) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Index job did not finish: " + jobId);
    }

    private JsonNode generateActionDraftsForC001(String conversationId) throws Exception {
        String diagnosisPayload = """
                {
                  "conversationId": "%s-diagnosis",
                  "userId": "u-cs-test",
                  "tenantId": "T001",
                  "roles": ["CUSTOMER_SERVICE"],
                  "customerId": "C001",
                  "days": 30,
                  "message": "请基于客户 C001 的异常诊断生成后续动作建议。",
                  "returnCitations": true
                }
                """.formatted(conversationId);
        String diagnosisBody = mockMvc.perform(post("/api/agent/customer-diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diagnosisPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String traceId = objectMapper.readTree(diagnosisBody).get("traceId").asText();

        String generatePayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-cs-test",
                  "roles": ["CUSTOMER_SERVICE"],
                  "traceId": "%s",
                  "conversationId": "%s-actions",
                  "customerId": "C001",
                  "days": 30
                }
                """.formatted(traceId, conversationId);
        String actionBody = mockMvc.perform(post("/api/agent/actions/from-diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generatePayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(actionBody);
    }

    private JsonNode approveAction(String actionId, String comment) throws Exception {
        String reviewPayload = """
                {
                  "tenantId": "T001",
                  "userId": "u-ops-reviewer",
                  "roles": ["OPS_MANAGER"],
                  "status": "APPROVED",
                  "comment": "%s"
                }
                """.formatted(comment);
        String reviewedBody = mockMvc.perform(post("/api/agent/actions/{actionId}/review", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(reviewedBody);
    }

    private String firstActionId(JsonNode actions, String actionType) {
        for (JsonNode action : actions) {
            if (actionType.equals(action.get("actionType").asText())) {
                return action.get("actionId").asText();
            }
        }
        throw new AssertionError("Missing action type: " + actionType);
    }
}
