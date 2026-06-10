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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        assertThat(migrations).isGreaterThanOrEqualTo(5);
        assertThat(ragExperiments).isNotNull().isGreaterThanOrEqualTo(2);
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
        assertThat(response.get("riskLevel").asText()).isEqualTo("L3");
        assertThat(response.get("answer").asText()).contains("WB202606010023", "运输时效与延误赔付政策");
        assertThat(response.get("citations")).isNotEmpty();
        assertThat(response.get("toolCalls")).hasSize(2);
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
}
