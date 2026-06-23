package com.superagent.logistics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnterpriseFrontendV25Tests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesSecurityConfigAndServerSidePages() throws Exception {
        JsonNode config = json(mockMvc.perform(get("/api/agent/security/config"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(config.get("mode").asText()).isEqualTo("api-key");

        JsonNode actions = json(mockMvc.perform(get("/api/agent/actions/page")
                        .param("tenantId", "T001").param("userId", "admin-console")
                        .param("roles", "OPS_MANAGER").param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertPage(actions, 5);

        JsonNode documents = json(mockMvc.perform(get("/api/knowledge/documents/page")
                        .param("tenantId", "T001").param("userId", "admin-console")
                        .param("roles", "OPS_MANAGER").param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertPage(documents, 5);

        JsonNode alerts = json(mockMvc.perform(get("/api/agent/quality/alerts/page")
                        .param("tenantId", "T001").param("userId", "admin-console")
                        .param("roles", "OPS_MANAGER").param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertPage(alerts, 5);

        JsonNode runs = json(mockMvc.perform(get("/api/agent/evals/runs/page")
                        .param("tenantId", "T001").param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertPage(runs, 5);
    }

    @Test
    void acceptsBoundedFrontendTelemetryAndEchoesTraceId() throws Exception {
        mockMvc.perform(post("/api/ops/frontend-events")
                        .header("X-Trace-Id", "web-test-trace")
                        .contentType("application/json")
                        .content("""
                                {"type":"API_FAILURE","route":"/api/test","message":"timeout","status":503,"durationMs":1200}
                                """))
                .andExpect(status().isAccepted());

        String traceId = mockMvc.perform(get("/api/agent/security/config")
                        .header("X-Trace-Id", "web-test-trace"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Trace-Id");
        assertThat(traceId).isEqualTo("web-test-trace");
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private void assertPage(JsonNode page, int size) {
        assertThat(page.get("items").isArray()).isTrue();
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(page.get("size").asInt()).isEqualTo(size);
        assertThat(page.get("total").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(page.get("totalPages").asInt()).isGreaterThanOrEqualTo(0);
    }
}
