package com.superagent.logistics.business;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcLogisticsBusinessDataAdapter implements LogisticsBusinessDataPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcLogisticsBusinessDataAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CustomerProfile> findCustomerProfile(String tenantId, String customerId) {
        List<CustomerProfile> rows = jdbcTemplate.query("""
                SELECT * FROM logistics_customer
                WHERE tenant_id = ? AND customer_id = ?
                """, this::mapCustomer, tenantId, customerId);
        return rows.stream().findFirst();
    }

    @Override
    public List<CustomerProfile> findHighRiskCustomers(String tenantId, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM logistics_customer
                WHERE tenant_id = ? AND risk_level IN ('HIGH', 'MEDIUM')
                ORDER BY CASE risk_level WHEN 'HIGH' THEN 0 ELSE 1 END, monthly_volume DESC
                LIMIT ?
                """, this::mapCustomer, tenantId, limit);
    }

    @Override
    public List<WaybillSummary> findCustomerWaybills(String tenantId, String customerId,
                                                     LocalDate from, LocalDate to, String status, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM logistics_waybill
                WHERE tenant_id = ? AND customer_id = ? AND order_date BETWEEN ? AND ?
                """);
        List<Object> args = new ArrayList<>(List.of(tenantId, customerId, from, to));
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY order_date DESC, waybill_id DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapWaybill, args.toArray());
    }

    @Override
    public Optional<WaybillSummary> findWaybillSummary(String tenantId, String waybillId) {
        List<WaybillSummary> rows = jdbcTemplate.query("""
                SELECT * FROM logistics_waybill
                WHERE tenant_id = ? AND waybill_id = ?
                """, this::mapWaybill, tenantId, waybillId);
        return rows.stream().findFirst();
    }

    @Override
    public List<TrackingEvent> findTrackingEvents(String tenantId, String waybillId) {
        return jdbcTemplate.query("""
                SELECT * FROM logistics_tracking_event
                WHERE tenant_id = ? AND waybill_id = ?
                ORDER BY event_time ASC
                """, this::mapTrackingEvent, tenantId, waybillId);
    }

    @Override
    public List<ExceptionEvent> findCustomerExceptions(String tenantId, String customerId,
                                                       LocalDate from, LocalDate to,
                                                       String exceptionType, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM logistics_exception_event
                WHERE tenant_id = ? AND customer_id = ? AND event_time >= ? AND event_time < ?
                """);
        List<Object> args = new ArrayList<>(List.of(tenantId, customerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        if (exceptionType != null && !exceptionType.isBlank()) {
            sql.append(" AND exception_type = ?");
            args.add(exceptionType);
        }
        sql.append(" ORDER BY event_time DESC LIMIT ?");
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapExceptionEvent, args.toArray());
    }

    @Override
    public List<ExceptionEvent> findWaybillExceptions(String tenantId, String waybillId) {
        return jdbcTemplate.query("""
                SELECT * FROM logistics_exception_event
                WHERE tenant_id = ? AND waybill_id = ?
                ORDER BY event_time DESC
                """, this::mapExceptionEvent, tenantId, waybillId);
    }

    @Override
    public List<TicketRecord> findCustomerTickets(String tenantId, String customerId,
                                                  LocalDate from, LocalDate to, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM logistics_ticket
                WHERE tenant_id = ? AND customer_id = ? AND created_at >= ? AND created_at < ?
                ORDER BY created_at DESC LIMIT ?
                """, this::mapTicket, tenantId, customerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay(), limit);
    }

    @Override
    public List<TicketRecord> findWaybillTickets(String tenantId, String waybillId) {
        return jdbcTemplate.query("""
                SELECT * FROM logistics_ticket
                WHERE tenant_id = ? AND waybill_id = ?
                ORDER BY created_at DESC
                """, this::mapTicket, tenantId, waybillId);
    }

    @Override
    public List<SlaRule> findSlaRules(String tenantId, String customerLevel, String serviceType) {
        return jdbcTemplate.query("""
                SELECT * FROM logistics_sla
                WHERE tenant_id = ?
                  AND (? IS NULL OR customer_level = ?)
                  AND (? IS NULL OR service_type = ?)
                ORDER BY customer_level DESC, promise_hours ASC
                """, this::mapSlaRule, tenantId, customerLevel, customerLevel, serviceType, serviceType);
    }

    @Override
    public DiagnosisReport diagnoseCustomer(String tenantId, String customerId, LocalDate from, LocalDate to) {
        int waybillCount = count("""
                SELECT COUNT(*) FROM logistics_waybill
                WHERE tenant_id = ? AND customer_id = ? AND order_date BETWEEN ? AND ?
                """, tenantId, customerId, from, to);
        int exceptionCount = count("""
                SELECT COUNT(*) FROM logistics_exception_event
                WHERE tenant_id = ? AND customer_id = ? AND event_time >= ? AND event_time < ?
                """, tenantId, customerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        int ticketCount = count("""
                SELECT COUNT(*) FROM logistics_ticket
                WHERE tenant_id = ? AND customer_id = ? AND created_at >= ? AND created_at < ?
                """, tenantId, customerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        String mainRisk = jdbcTemplate.query("""
                SELECT exception_type, COUNT(*) AS cnt FROM logistics_exception_event
                WHERE tenant_id = ? AND customer_id = ? AND event_time >= ? AND event_time < ?
                GROUP BY exception_type ORDER BY cnt DESC LIMIT 1
                """, rs -> rs.next() ? rs.getString("exception_type") + "（" + rs.getInt("cnt") + "次）" : "暂无明显异常",
                tenantId, customerId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());

        double exceptionRate = waybillCount == 0 ? 0 : round(exceptionCount * 100.0 / waybillCount);
        double complaintRate = waybillCount == 0 ? 0 : round(ticketCount * 100.0 / waybillCount);
        String recommendation;
        if (exceptionRate >= 12 || ticketCount >= 3) {
            recommendation = "建议标记为高关注客户，运营复盘主要异常线路，并为未来 7 天设置节点预警。";
        } else if (exceptionRate >= 8 || ticketCount >= 1) {
            recommendation = "建议客服主动同步异常改进措施，并跟进最近未签收运单。";
        } else {
            recommendation = "当前风险可控，保持常规 SLA 监控。";
        }
        return new DiagnosisReport(customerId, from + " 至 " + to, waybillCount, exceptionCount, ticketCount,
                exceptionRate, complaintRate, mainRisk, recommendation);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private CustomerProfile mapCustomer(ResultSet rs, int rowNum) throws SQLException {
        return new CustomerProfile(
                rs.getString("customer_id"),
                rs.getString("customer_name"),
                rs.getString("industry"),
                rs.getString("customer_level"),
                rs.getString("service_owner"),
                rs.getString("sales_owner"),
                rs.getString("contact_name"),
                rs.getString("contact_phone"),
                rs.getString("region"),
                rs.getString("risk_level"),
                rs.getInt("monthly_volume"),
                rs.getString("status")
        );
    }

    private WaybillSummary mapWaybill(ResultSet rs, int rowNum) throws SQLException {
        return new WaybillSummary(
                rs.getString("waybill_id"),
                rs.getString("customer_id"),
                rs.getString("origin_city"),
                rs.getString("dest_city"),
                rs.getString("service_type"),
                rs.getString("cargo_type"),
                rs.getBigDecimal("weight_kg"),
                rs.getBigDecimal("volume_m3"),
                rs.getDate("order_date").toLocalDate(),
                rs.getTimestamp("promised_delivery_time").toLocalDateTime(),
                toLocalDateTime(rs.getTimestamp("actual_delivery_time")),
                rs.getString("status"),
                rs.getBigDecimal("freight_fee"),
                rs.getString("route_code")
        );
    }

    private TrackingEvent mapTrackingEvent(ResultSet rs, int rowNum) throws SQLException {
        return new TrackingEvent(
                rs.getString("event_id"),
                rs.getString("waybill_id"),
                rs.getTimestamp("event_time").toLocalDateTime(),
                rs.getString("node_name"),
                rs.getString("city"),
                rs.getString("status"),
                rs.getString("description")
        );
    }

    private ExceptionEvent mapExceptionEvent(ResultSet rs, int rowNum) throws SQLException {
        return new ExceptionEvent(
                rs.getString("exception_id"),
                rs.getString("waybill_id"),
                rs.getString("customer_id"),
                rs.getTimestamp("event_time").toLocalDateTime(),
                rs.getString("exception_type"),
                rs.getString("severity"),
                rs.getString("responsibility_party"),
                rs.getString("description"),
                rs.getBoolean("resolved"),
                rs.getInt("impact_hours")
        );
    }

    private TicketRecord mapTicket(ResultSet rs, int rowNum) throws SQLException {
        return new TicketRecord(
                rs.getString("ticket_id"),
                rs.getString("customer_id"),
                rs.getString("waybill_id"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("ticket_type"),
                rs.getString("priority"),
                rs.getString("status"),
                rs.getString("owner_team"),
                rs.getString("summary"),
                rs.getString("resolution"),
                rs.getBigDecimal("compensation_amount")
        );
    }

    private SlaRule mapSlaRule(ResultSet rs, int rowNum) throws SQLException {
        return new SlaRule(
                rs.getString("sla_id"),
                rs.getString("customer_level"),
                rs.getString("service_type"),
                rs.getInt("promise_hours"),
                rs.getString("delay_compensation_rule"),
                rs.getString("temp_range"),
                rs.getString("notes")
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
