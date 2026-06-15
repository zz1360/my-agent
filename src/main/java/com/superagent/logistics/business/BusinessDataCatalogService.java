package com.superagent.logistics.business;

import com.superagent.logistics.api.dto.BusinessDataSourceResponse;
import com.superagent.logistics.security.AgentPermissionService;
import com.superagent.logistics.security.AgentUserContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BusinessDataCatalogService {

    private static final List<String> BUSINESS_TABLES = List.of(
            "logistics_customer",
            "logistics_waybill",
            "logistics_tracking_event",
            "logistics_exception_event",
            "logistics_ticket",
            "logistics_sla"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final AgentPermissionService permissionService;

    public BusinessDataCatalogService(JdbcTemplate jdbcTemplate, DataSource dataSource,
                                      AgentPermissionService permissionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.permissionService = permissionService;
    }

    public BusinessDataSourceResponse describe(String tenantId, String userId, List<String> roles) {
        AgentUserContext context = AgentUserContext.from(tenantId, userId, roles);
        permissionService.checkBusinessReadable(context);
        return new BusinessDataSourceResponse(
                context.tenantId(),
                JdbcLogisticsBusinessDataAdapter.class.getSimpleName(),
                "JDBC_READ_MODEL",
                databaseProduct(),
                true,
                "所有业务表按 tenant_id 查询隔离，Agent 层再叠加角色与客户访问校验",
                List.of("客户画像", "运单与轨迹", "异常事件", "客服工单", "SLA/赔付规则"),
                tableRows(context.tenantId())
        );
    }

    private Map<String, Integer> tableRows(String tenantId) {
        Map<String, Integer> rows = new LinkedHashMap<>();
        for (String table : BUSINESS_TABLES) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?",
                    Integer.class,
                    tenantId);
            rows.put(table, count == null ? 0 : count);
        }
        return rows;
    }

    private String databaseProduct() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException ex) {
            return "UNKNOWN: " + ex.getMessage();
        }
    }
}
