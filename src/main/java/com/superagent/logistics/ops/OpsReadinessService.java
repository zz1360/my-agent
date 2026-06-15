package com.superagent.logistics.ops;

import com.superagent.logistics.api.dto.OpsCheckResponse;
import com.superagent.logistics.api.dto.OpsReadinessResponse;
import com.superagent.logistics.config.DeepSeekProperties;
import com.superagent.logistics.config.RetrievalProperties;
import com.superagent.logistics.knowledge.PgVectorKnowledgeStore;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpsReadinessService {

    private final Environment environment;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final PgVectorKnowledgeStore vectorKnowledgeStore;
    private final DeepSeekProperties deepSeekProperties;
    private final RetrievalProperties retrievalProperties;

    public OpsReadinessService(Environment environment,
                               DataSource dataSource,
                               JdbcTemplate jdbcTemplate,
                               PgVectorKnowledgeStore vectorKnowledgeStore,
                               DeepSeekProperties deepSeekProperties,
                               RetrievalProperties retrievalProperties) {
        this.environment = environment;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
        this.deepSeekProperties = deepSeekProperties;
        this.retrievalProperties = retrievalProperties;
    }

    public OpsReadinessResponse readiness() {
        List<OpsCheckResponse> checks = new ArrayList<>();
        checks.add(databaseCheck());
        checks.add(flywayCheck());
        checks.add(pgVectorCheck());
        checks.add(deepSeekCheck());
        checks.add(retrievalCheck());
        boolean ready = checks.stream().noneMatch(check -> "DOWN".equals(check.status()) || "OUT_OF_SERVICE".equals(check.status()));
        return new OpsReadinessResponse(
                environment.getProperty("spring.application.name", "logistics-agent"),
                Arrays.asList(environment.getActiveProfiles()),
                ready,
                checks,
                Instant.now()
        );
    }

    private OpsCheckResponse databaseCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            details.put("product", connection.getMetaData().getDatabaseProductName());
            details.put("valid", connection.isValid(2));
            return new OpsCheckResponse("businessDatabase", "UP", "业务数据库连接可用", details);
        } catch (Exception ex) {
            details.put("error", ex.getMessage());
            return new OpsCheckResponse("businessDatabase", "DOWN", "业务数据库连接失败", details);
        }
    }

    private OpsCheckResponse flywayCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        try {
            String version = jdbcTemplate.query("""
                    SELECT version FROM flyway_schema_history
                    WHERE success = true
                    ORDER BY installed_rank DESC
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString("version") : "none");
            details.put("version", version);
            return new OpsCheckResponse("flyway", "UP", "数据库迁移版本可读取", details);
        } catch (Exception ex) {
            details.put("error", ex.getMessage());
            return new OpsCheckResponse("flyway", "DOWN", "数据库迁移版本读取失败", details);
        }
    }

    private OpsCheckResponse pgVectorCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", vectorKnowledgeStore.isEnabled());
        details.put("ready", vectorKnowledgeStore.isReady());
        details.put("table", vectorKnowledgeStore.table());
        details.put("chunks", vectorKnowledgeStore.countChunks());
        String status = vectorKnowledgeStore.isEnabled() && !vectorKnowledgeStore.isReady() ? "DEGRADED" : "UP";
        return new OpsCheckResponse("pgVector", status, "PGVector 状态已检查", details);
    }

    private OpsCheckResponse deepSeekCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("enabled", deepSeekProperties.isEnabled());
        details.put("baseUrl", deepSeekProperties.getBaseUrl());
        details.put("model", deepSeekProperties.getModel());
        details.put("apiKeyFileConfigured", deepSeekProperties.getApiKeyFile() != null
                && !deepSeekProperties.getApiKeyFile().isBlank());
        String status = deepSeekProperties.isEnabled() && !(boolean) details.get("apiKeyFileConfigured") ? "DOWN" : "UP";
        return new OpsCheckResponse("deepSeek", status, "DeepSeek 配置已检查", details);
    }

    private OpsCheckResponse retrievalCheck() {
        Map<String, Object> details = new LinkedHashMap<>();
        String defaultMode = retrievalProperties.normalizedDefaultMode();
        details.put("defaultMode", defaultMode);
        details.put("vectorReady", vectorKnowledgeStore.isReady());
        boolean vectorRequired = "VECTOR_ONLY".equals(defaultMode);
        String status = vectorRequired && !vectorKnowledgeStore.isReady() ? "OUT_OF_SERVICE" : "UP";
        return new OpsCheckResponse("retrieval", status, "默认检索策略已检查", details);
    }
}
