package com.superagent.logistics.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.superagent.logistics.knowledge.KnowledgeChunk;
import com.superagent.logistics.knowledge.PgVectorKnowledgeStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
@Order(0)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final PgVectorKnowledgeStore vectorKnowledgeStore;
    private final LocalDate seedDate;
    private final boolean resetOnStart;

    public DemoDataInitializer(JdbcTemplate jdbcTemplate,
                               PgVectorKnowledgeStore vectorKnowledgeStore,
                               @Value("${agent.demo.seed-date:2026-06-04}") String seedDate,
                               @Value("${agent.demo.reset-on-start:false}") boolean resetOnStart) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorKnowledgeStore = vectorKnowledgeStore;
        this.seedDate = LocalDate.parse(seedDate);
        this.resetOnStart = resetOnStart;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (resetOnStart) {
            resetDemoData();
        }
        if (!resetOnStart && hasDemoData()) {
            syncKnowledgeVectors();
            log.info("Logistics Agent demo data retained: seedDate={}, resetOnStart={}", seedDate, resetOnStart);
            return;
        }

        seedCustomers();
        seedSlaRules();
        seedWaybills();
        seedKnowledge();
        syncKnowledgeVectors();
        log.info("Logistics Agent demo data initialized: seedDate={}, resetOnStart={}", seedDate, resetOnStart);
    }

    private void resetDemoData() {
        jdbcTemplate.execute("DELETE FROM ai_agent_tool_call");
        jdbcTemplate.execute("DELETE FROM ai_agent_trace");
        jdbcTemplate.execute("DELETE FROM ai_eval_case_result");
        jdbcTemplate.execute("DELETE FROM ai_eval_run");
        jdbcTemplate.execute("DELETE FROM ai_eval_case");
        jdbcTemplate.execute("DELETE FROM ai_knowledge_chunk");
        jdbcTemplate.execute("DELETE FROM ai_knowledge_document");
        jdbcTemplate.execute("DELETE FROM logistics_ticket");
        jdbcTemplate.execute("DELETE FROM logistics_exception_event");
        jdbcTemplate.execute("DELETE FROM logistics_tracking_event");
        jdbcTemplate.execute("DELETE FROM logistics_waybill");
        jdbcTemplate.execute("DELETE FROM logistics_sla");
        jdbcTemplate.execute("DELETE FROM logistics_customer");
    }

    private boolean hasDemoData() {
        Integer customerCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM logistics_customer
                WHERE tenant_id = ?
                """, Integer.class, "T001");
        return customerCount != null && customerCount > 0;
    }

    private void seedCustomers() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(customer("C001", "星河电商华东仓配", "电商零售", "VIP", "李青", "周岚", "赵敏", "13810240001", "华东", "HIGH", 18400, "ACTIVE"));
        rows.add(customer("C002", "晨曦生鲜冷链", "生鲜冷链", "VIP", "吴珊", "林杰", "唐羽", "13910240002", "华南", "MEDIUM", 9600, "ACTIVE"));
        rows.add(customer("C003", "远航汽车配件", "汽配制造", "A", "陈越", "马骁", "韩东", "13710240003", "华中", "LOW", 6200, "ACTIVE"));
        rows.add(customer("C004", "青藤医药供应链", "医药流通", "VIP", "郑一", "许然", "沈月", "13610240004", "华北", "MEDIUM", 7200, "ACTIVE"));
        rows.add(customer("C005", "北辰家电全国分销", "家电", "A", "孙宁", "郭涛", "钱峰", "13510240005", "全国", "LOW", 11000, "ACTIVE"));
        rows.add(customer("C006", "跃迁跨境贸易", "跨境贸易", "B", "张晨", "罗鸣", "冯雪", "15010240006", "华东", "MEDIUM", 3200, "ACTIVE"));
        rows.add(customer("C007", "云杉服饰", "服装", "B", "何雨", "董洁", "秦川", "15110240007", "西南", "LOW", 4500, "ACTIVE"));
        rows.add(customer("C008", "鲸蓝母婴用品", "母婴", "A", "杨驰", "谢林", "顾南", "15210240008", "华东", "MEDIUM", 8300, "ACTIVE"));
        rows.add(customer("C009", "骏驰工业设备", "工业设备", "A", "赵安", "袁磊", "陆平", "15710240009", "西北", "LOW", 2800, "ACTIVE"));
        rows.add(customer("C010", "松果社区团购", "社区团购", "VIP", "王澈", "高启", "夏宁", "15810240010", "华南", "HIGH", 15600, "ACTIVE"));

        String[] industries = {"电商零售", "食品饮料", "日化", "家居", "机械制造", "服装", "3C 数码", "图书文创"};
        String[] levels = {"A", "B", "VIP"};
        String[] regions = {"华东", "华南", "华北", "华中", "西南", "西北"};
        String[] risks = {"LOW", "MEDIUM", "LOW", "LOW", "MEDIUM"};
        for (int i = 11; i <= 45; i++) {
            rows.add(customer(
                    "C%03d".formatted(i),
                    "%s客户%02d".formatted(regions[i % regions.length], i),
                    industries[i % industries.length],
                    levels[i % levels.length],
                    "客服%02d".formatted(i % 12 + 1),
                    "销售%02d".formatted(i % 9 + 1),
                    "联系人%02d".formatted(i),
                    "13%d1024%04d".formatted(i % 9 + 1, i),
                    regions[i % regions.length],
                    risks[i % risks.length],
                    1000 + i * 190,
                    "ACTIVE"
            ));
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO logistics_customer
                (tenant_id, customer_id, customer_name, industry, customer_level, service_owner, sales_owner,
                 contact_name, contact_phone, region, risk_level, monthly_volume, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
    }

    private Object[] customer(String customerId, String customerName, String industry, String level,
                              String serviceOwner, String salesOwner, String contactName, String phone,
                              String region, String riskLevel, int monthlyVolume, String status) {
        return new Object[]{"T001", customerId, customerName, industry, level, serviceOwner, salesOwner,
                contactName, phone, region, riskLevel, monthlyVolume, status};
    }

    private void seedSlaRules() {
        List<Object[]> rows = List.of(
                sla("SLA-VIP-EXPRESS", "VIP", "整车直达", 36, "超承诺时效 4 小时以内免收加急费；超过 4 小时按运费 8% 赔付，上限 2000 元。", null, "VIP 客户需 30 分钟内响应异常。"),
                sla("SLA-VIP-COLD", "VIP", "冷链专车", 30, "因承运原因超温或延误，按货损评估赔付；客服需同步温控记录。", "2-8C", "冷链异常需运营经理复核。"),
                sla("SLA-A-EXPRESS", "A", "标准零担", 48, "超过承诺时效 8 小时后可申请运费 5% 补偿，上限 1000 元。", null, "需提供轨迹和中转记录。"),
                sla("SLA-B-STANDARD", "B", "标准零担", 72, "超过承诺时效 12 小时后按个案处理。", null, "低优先级客户以解释和补送为主。"),
                sla("SLA-VIP-WAREHOUSE", "VIP", "仓配一体", 24, "仓内延误导致未按时出库，按服务费 5% 减免。", null, "适用于仓配合同客户。")
        );

        jdbcTemplate.batchUpdate("""
                INSERT INTO logistics_sla
                (tenant_id, sla_id, customer_level, service_type, promise_hours, delay_compensation_rule, temp_range, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, rows);
    }

    private Object[] sla(String slaId, String level, String serviceType, int promiseHours, String rule,
                         String tempRange, String notes) {
        return new Object[]{"T001", slaId, level, serviceType, promiseHours, rule, tempRange, notes};
    }

    private void seedWaybills() {
        Random random = new Random(20260604);
        List<String> customers = new ArrayList<>();
        for (int i = 1; i <= 45; i++) {
            customers.add("C%03d".formatted(i));
        }

        insertSpecialWaybills();

        String[] origins = {"上海", "杭州", "苏州", "广州", "深圳", "北京", "天津", "武汉", "成都", "西安"};
        String[] dests = {"南京", "宁波", "合肥", "厦门", "长沙", "郑州", "青岛", "重庆", "昆明", "南昌"};
        String[] services = {"标准零担", "整车直达", "冷链专车", "仓配一体"};
        String[] cargoTypes = {"普通商品", "服装", "小家电", "生鲜食品", "医药耗材", "汽配件", "3C 数码"};

        int eventSeq = 2000;
        int exceptionSeq = 1000;
        int ticketSeq = 1000;

        for (int i = 1; i <= 1200; i++) {
            String customerId = customers.get(random.nextInt(customers.size()));
            boolean c001RecentBoost = "C001".equals(customerId) && random.nextDouble() < 0.55;
            LocalDate orderDate = c001RecentBoost
                    ? seedDate.minusDays(random.nextInt(29))
                    : seedDate.minusDays(random.nextInt(45));
            String waybillId = "WBG%s%05d".formatted(orderDate.format(DateTimeFormatter.BASIC_ISO_DATE), i);
            String origin = origins[random.nextInt(origins.length)];
            String dest = dests[random.nextInt(dests.length)];
            String service = services[random.nextInt(services.length)];
            String cargoType = service.equals("冷链专车") ? "生鲜食品" : cargoTypes[random.nextInt(cargoTypes.length)];
            int promiseHours = service.equals("整车直达") ? 36 : service.equals("冷链专车") ? 30 : service.equals("仓配一体") ? 24 : 60;
            LocalDateTime orderAt = orderDate.atTime(9 + random.nextInt(8), random.nextInt(60));
            LocalDateTime promisedAt = orderAt.plusHours(promiseHours);
            boolean hasException = c001RecentBoost || random.nextDouble() < 0.13;
            String status = resolveStatus(random, orderDate, hasException);
            LocalDateTime actualAt = status.equals("SIGNED") ? promisedAt.plusHours(hasException ? 4 + random.nextInt(20) : random.nextInt(5) - 2) : null;
            BigDecimal weight = BigDecimal.valueOf(20 + random.nextInt(980) + random.nextDouble()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal volume = BigDecimal.valueOf(0.2 + random.nextDouble() * 9).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = weight.multiply(BigDecimal.valueOf(service.equals("整车直达") ? 4.2 : 2.6)).add(volume.multiply(BigDecimal.valueOf(35))).setScale(2, RoundingMode.HALF_UP);

            jdbcTemplate.update("""
                    INSERT INTO logistics_waybill
                    (tenant_id, waybill_id, customer_id, origin_city, dest_city, service_type, cargo_type,
                     weight_kg, volume_m3, order_date, promised_delivery_time, actual_delivery_time, status, freight_fee, route_code)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, "T001", waybillId, customerId, origin, dest, service, cargoType, weight, volume,
                    Date.valueOf(orderDate), Timestamp.valueOf(promisedAt), toTimestamp(actualAt),
                    status, fee, "%s-%s-%02d".formatted(origin, dest, random.nextInt(20) + 1));

            eventSeq = insertTrackingEvents(eventSeq, waybillId, origin, dest, orderAt, promisedAt, actualAt, status, hasException);

            if (hasException) {
                String type = pickExceptionType(random, service);
                int impactHours = 4 + random.nextInt(28);
                String severity = impactHours >= 18 || type.equals("温控异常") ? "HIGH" : impactHours >= 10 ? "MEDIUM" : "LOW";
                String exceptionId = "EX%06d".formatted(exceptionSeq++);
                jdbcTemplate.update("""
                        INSERT INTO logistics_exception_event
                        (tenant_id, exception_id, waybill_id, customer_id, event_time, exception_type, severity,
                         responsibility_party, description, resolved, impact_hours)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, "T001", exceptionId, waybillId, customerId,
                        Timestamp.valueOf(orderAt.plusHours(16 + random.nextInt(24))), type, severity,
                        pickResponsibility(random, type), buildExceptionDescription(type, origin, dest, impactHours),
                        !status.equals("EXCEPTION"), impactHours);

                if (c001RecentBoost || random.nextDouble() < 0.55) {
                    String ticketId = "TK%06d".formatted(ticketSeq++);
                    jdbcTemplate.update("""
                            INSERT INTO logistics_ticket
                            (tenant_id, ticket_id, customer_id, waybill_id, created_at, ticket_type, priority, status,
                             owner_team, summary, resolution, compensation_amount)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, "T001", ticketId, customerId, waybillId,
                            Timestamp.valueOf(orderAt.plusDays(1).plusHours(random.nextInt(12))),
                            type.equals("温控异常") ? "冷链投诉" : "运输异常投诉",
                            severity.equals("HIGH") ? "P1" : "P2",
                            status.equals("EXCEPTION") ? "PROCESSING" : "CLOSED",
                            severity.equals("HIGH") ? "运营升级组" : "客服一线组",
                            "客户反馈运单 " + waybillId + " 出现" + type + "，要求说明原因和处理时效。",
                            status.equals("EXCEPTION") ? "已升级运营排查，待补充节点证明。" : "已解释异常原因并提供补偿方案。",
                            severity.equals("HIGH") ? BigDecimal.valueOf(800 + random.nextInt(1800)) : BigDecimal.valueOf(80 + random.nextInt(420)));
                }
            }
        }
    }

    private void insertSpecialWaybills() {
        insertWaybill("WB202606010023", "C001", "上海", "南京", "整车直达", "小家电",
                new BigDecimal("860.50"), new BigDecimal("6.40"), seedDate.minusDays(3),
                seedDate.minusDays(3).atTime(10, 20).plusHours(36), null,
                "EXCEPTION", new BigDecimal("4580.00"), "上海-南京-01");
        insertTracking("EVS0001", "WB202606010023", seedDate.minusDays(3).atTime(10, 20), "上海青浦揽收站", "上海", "已揽收", "司机完成揽收，预计当日 18:00 入华东分拨。");
        insertTracking("EVS0002", "WB202606010023", seedDate.minusDays(3).atTime(18, 10), "华东一号分拨", "上海", "已入库", "货物完成称重复核，等待装车。");
        insertTracking("EVS0003", "WB202606010023", seedDate.minusDays(2).atTime(2, 30), "沪宁干线", "苏州", "运输中", "干线车辆因高速管制延迟放行。");
        insertTracking("EVS0004", "WB202606010023", seedDate.minusDays(1).atTime(14, 40), "南京江宁分拨", "南京", "异常滞留", "目的分拨爆仓，暂未安排派送车次。");
        insertException("EXS0001", "WB202606010023", "C001", seedDate.minusDays(1).atTime(15, 5), "延误", "HIGH", "承运网络", "目的分拨爆仓叠加干线管制，预计影响 18 小时。", false, 18);
        insertTicket("TKS0001", "C001", "WB202606010023", seedDate.minusDays(1).atTime(16, 20), "时效投诉", "P1", "PROCESSING", "运营升级组", "客户要求解释延误原因并给出赔付判断。", "已升级南京分拨，等待补充签收时间。", new BigDecimal("1200.00"));

        insertWaybill("WB202605290088", "C001", "上海", "合肥", "标准零担", "服装",
                new BigDecimal("320.00"), new BigDecimal("3.20"), seedDate.minusDays(6),
                seedDate.minusDays(4).atTime(18, 0), seedDate.minusDays(4).atTime(23, 20),
                "SIGNED", new BigDecimal("1430.00"), "上海-合肥-03");
        insertTracking("EVS0005", "WB202605290088", seedDate.minusDays(6).atTime(11, 10), "上海闵行揽收站", "上海", "已揽收", "客户仓库交接完成。");
        insertTracking("EVS0006", "WB202605290088", seedDate.minusDays(5).atTime(7, 30), "合肥分拨", "合肥", "到达目的分拨", "目的分拨卸车完成。");
        insertTracking("EVS0007", "WB202605290088", seedDate.minusDays(4).atTime(23, 20), "合肥蜀山网点", "合肥", "已签收", "晚于承诺时间 5 小时 20 分签收。");
        insertException("EXS0002", "WB202605290088", "C001", seedDate.minusDays(4).atTime(18, 30), "延误", "MEDIUM", "末端网点", "派送车次不足导致签收延迟 5 小时。", true, 5);
        insertTicket("TKS0002", "C001", "WB202605290088", seedDate.minusDays(4).atTime(19, 5), "时效投诉", "P2", "CLOSED", "客服一线组", "客户反馈合肥门店到货晚于促销上架时间。", "已提供轨迹证明并按 SLA 免收加急费。", new BigDecimal("360.00"));

        insertWaybill("WB202605220041", "C001", "上海", "杭州", "冷链专车", "生鲜食品",
                new BigDecimal("180.00"), new BigDecimal("2.10"), seedDate.minusDays(13),
                seedDate.minusDays(12).atTime(16, 0), seedDate.minusDays(12).atTime(17, 30),
                "SIGNED", new BigDecimal("2180.00"), "上海-杭州-02");
        insertTracking("EVS0008", "WB202605220041", seedDate.minusDays(13).atTime(9, 10), "上海冷链仓", "上海", "已出库", "温控记录正常，车厢温度 4.3C。");
        insertTracking("EVS0009", "WB202605220041", seedDate.minusDays(12).atTime(10, 30), "杭嘉湖中转", "杭州", "温控告警", "车厢传感器显示连续 26 分钟高于 8C。");
        insertTracking("EVS0010", "WB202605220041", seedDate.minusDays(12).atTime(17, 30), "杭州余杭网点", "杭州", "已签收", "客户签收时备注需提供温控报告。");
        insertException("EXS0003", "WB202605220041", "C001", seedDate.minusDays(12).atTime(10, 35), "温控异常", "HIGH", "承运车辆", "冷链车厢连续 26 分钟超过 8C，需要出具温控曲线。", true, 2);
        insertTicket("TKS0003", "C001", "WB202605220041", seedDate.minusDays(12).atTime(18, 10), "冷链投诉", "P1", "CLOSED", "运营升级组", "客户要求确认超温是否影响商品销售。", "已提供温控曲线和质检建议，保留货损评估入口。", new BigDecimal("0.00"));
    }

    private void insertWaybill(String waybillId, String customerId, String origin, String dest, String service,
                               String cargoType, BigDecimal weight, BigDecimal volume, LocalDate orderDate,
                               LocalDateTime promisedAt, LocalDateTime actualAt, String status,
                               BigDecimal fee, String routeCode) {
        jdbcTemplate.update("""
                INSERT INTO logistics_waybill
                (tenant_id, waybill_id, customer_id, origin_city, dest_city, service_type, cargo_type,
                 weight_kg, volume_m3, order_date, promised_delivery_time, actual_delivery_time, status, freight_fee, route_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "T001", waybillId, customerId, origin, dest, service, cargoType, weight, volume,
                Date.valueOf(orderDate), Timestamp.valueOf(promisedAt), toTimestamp(actualAt), status, fee, routeCode);
    }

    private int insertTrackingEvents(int eventSeq, String waybillId, String origin, String dest, LocalDateTime orderAt,
                                     LocalDateTime promisedAt, LocalDateTime actualAt, String status, boolean hasException) {
        insertTracking("EV%06d".formatted(eventSeq++), waybillId, orderAt, origin + "揽收站", origin, "已揽收", "客户完成交货，系统生成运单。");
        insertTracking("EV%06d".formatted(eventSeq++), waybillId, orderAt.plusHours(7), origin + "分拨", origin, "已入库", "分拨完成称重、体积复核。");
        insertTracking("EV%06d".formatted(eventSeq++), waybillId, orderAt.plusHours(16), origin + "-" + dest + "干线", origin, "运输中", "干线车辆发车。");
        if (hasException) {
            insertTracking("EV%06d".formatted(eventSeq++), waybillId, promisedAt.minusHours(4), dest + "分拨", dest, "异常预警", "系统检测到时效风险或节点滞留。");
        }
        if (status.equals("SIGNED")) {
            insertTracking("EV%06d".formatted(eventSeq++), waybillId, actualAt.minusHours(3), dest + "网点", dest, "派送中", "末端网点安排派送。");
            insertTracking("EV%06d".formatted(eventSeq++), waybillId, actualAt, dest + "网点", dest, "已签收", "客户完成签收。");
        } else if (status.equals("EXCEPTION")) {
            insertTracking("EV%06d".formatted(eventSeq++), waybillId, promisedAt.plusHours(2), dest + "分拨", dest, "异常滞留", "等待运营补救方案。");
        } else {
            insertTracking("EV%06d".formatted(eventSeq++), waybillId, promisedAt.minusHours(8), dest + "分拨", dest, "运输中", "预计按计划到达目的城市。");
        }
        return eventSeq;
    }

    private void insertTracking(String eventId, String waybillId, LocalDateTime eventTime, String nodeName,
                                String city, String status, String description) {
        jdbcTemplate.update("""
                INSERT INTO logistics_tracking_event
                (tenant_id, event_id, waybill_id, event_time, node_name, city, status, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, "T001", eventId, waybillId, Timestamp.valueOf(eventTime), nodeName, city, status, description);
    }

    private void insertException(String exceptionId, String waybillId, String customerId, LocalDateTime eventTime,
                                 String type, String severity, String responsibilityParty, String description,
                                 boolean resolved, int impactHours) {
        jdbcTemplate.update("""
                INSERT INTO logistics_exception_event
                (tenant_id, exception_id, waybill_id, customer_id, event_time, exception_type, severity,
                 responsibility_party, description, resolved, impact_hours)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "T001", exceptionId, waybillId, customerId, Timestamp.valueOf(eventTime), type, severity,
                responsibilityParty, description, resolved, impactHours);
    }

    private void insertTicket(String ticketId, String customerId, String waybillId, LocalDateTime createdAt,
                              String type, String priority, String status, String ownerTeam, String summary,
                              String resolution, BigDecimal compensationAmount) {
        jdbcTemplate.update("""
                INSERT INTO logistics_ticket
                (tenant_id, ticket_id, customer_id, waybill_id, created_at, ticket_type, priority, status,
                 owner_team, summary, resolution, compensation_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "T001", ticketId, customerId, waybillId, Timestamp.valueOf(createdAt), type, priority, status,
                ownerTeam, summary, resolution, compensationAmount);
    }

    private String resolveStatus(Random random, LocalDate orderDate, boolean hasException) {
        if (hasException && random.nextDouble() < 0.38) {
            return "EXCEPTION";
        }
        if (orderDate.isBefore(seedDate.minusDays(4))) {
            return "SIGNED";
        }
        return random.nextDouble() < 0.65 ? "IN_TRANSIT" : "OUT_FOR_DELIVERY";
    }

    private String pickExceptionType(Random random, String serviceType) {
        if (serviceType.equals("冷链专车") && random.nextDouble() < 0.5) {
            return "温控异常";
        }
        String[] types = {"延误", "派送失败", "破损", "地址错误", "分拨滞留", "丢件风险"};
        return types[random.nextInt(types.length)];
    }

    private String pickResponsibility(Random random, String type) {
        if (type.equals("地址错误")) {
            return "客户信息";
        }
        if (type.equals("温控异常")) {
            return "承运车辆";
        }
        String[] parties = {"承运网络", "末端网点", "分拨中心", "外部交通"};
        return parties[random.nextInt(parties.length)];
    }

    private String buildExceptionDescription(String type, String origin, String dest, int impactHours) {
        return switch (type) {
            case "延误" -> "线路 " + origin + "-" + dest + " 出现时效延误，预计影响 " + impactHours + " 小时。";
            case "温控异常" -> "冷链运输过程中温度超过合同区间，需要调取温控曲线。";
            case "破损" -> "到达目的分拨后外包装破损，等待现场拍照复核。";
            case "派送失败" -> "末端派送联系收货方失败，已安排二次派送。";
            case "地址错误" -> "收货地址信息不完整，客服需联系客户补充。";
            case "丢件风险" -> "分拨盘点发现短少风险，已进入异常查找流程。";
            default -> "运输节点异常，运营团队处理中。";
        };
    }

    private Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private void seedKnowledge() {
        List<KnowledgeSeed> seeds = List.of(
                new KnowledgeSeed("policy-delay-v3", "运输时效与延误赔付政策 v3.0", "policy", "customer_service", "v3.0", "CUSTOMER_SERVICE,OPERATIONS,OPS_MANAGER,SALES",
                        "VIP 客户整车直达承诺时效通常为 36 小时；超过承诺 4 小时以内优先免收加急费，超过 4 小时可按运费 8% 申请补偿，上限 2000 元。赔付判断必须以运单承诺时间、实际签收时间、异常责任归属和客户合同为依据。"),
                new KnowledgeSeed("sop-delay-escalation", "物流延误客服升级 SOP", "manual", "customer_service", "v2.4", "CUSTOMER_SERVICE,OPERATIONS,OPS_MANAGER",
                        "客户反馈延误时，客服需先查询轨迹、确认承诺时效、识别责任归属；P1 客户或 VIP 客户需要 30 分钟内升级运营。若货物仍未签收，应给出预计到达时间和下一次同步时间，不得承诺未确认赔付。"),
                new KnowledgeSeed("policy-cold-chain-v2", "冷链运输温控异常处理规范 v2.1", "policy", "cold_chain", "v2.1", "CUSTOMER_SERVICE,OPERATIONS,OPS_MANAGER",
                        "冷链货物标准温区为 2-8C。连续超过 15 分钟或峰值超过 10C 应触发温控异常工单，运营需提供温控曲线、车厢记录和质检建议。是否赔付需结合货损评估，不允许客服直接判定商品不可售。"),
                new KnowledgeSeed("sop-failed-delivery", "派送失败处理 SOP", "manual", "last_mile", "v1.8", "CUSTOMER_SERVICE,OPERATIONS,SALES",
                        "派送失败需区分客户原因和承运原因。地址不完整、电话无人接听属于客户信息原因；网点车次不足、漏派属于承运原因。承运原因导致 VIP 客户延误时需升级至末端主管。"),
                new KnowledgeSeed("rule-customer-risk", "客户风险等级判定规则", "policy", "diagnosis", "v1.5", "CUSTOMER_SERVICE,OPERATIONS,OPS_MANAGER,SALES",
                        "近 30 天异常率超过 8% 或投诉率超过 3% 的客户应标记为中风险；异常率超过 12% 且存在 P1 工单，或连续两周投诉上升，应标记为高风险。风险解释必须列出主要异常类型和受影响线路。"),
                new KnowledgeSeed("faq-tracking", "运单轨迹查询 FAQ", "faq", "tracking", "v1.2", "PUBLIC",
                        "轨迹状态包括已揽收、已入库、运输中、异常预警、异常滞留、派送中、已签收。若最后一个节点超过 12 小时无更新，客服应查询分拨或网点处理记录。"),
                new KnowledgeSeed("sop-damage-lost", "破损与丢件异常处理流程", "manual", "exception", "v2.0", "CUSTOMER_SERVICE,OPERATIONS,OPS_MANAGER",
                        "破损异常需要现场照片、称重记录和客户签收备注；丢件风险需要分拨盘点记录和监控排查。未完成责任认定前，只能说明处理流程和预计反馈时间。"),
                new KnowledgeSeed("rule-warehouse", "仓配一体出库 SLA 规则", "policy", "warehouse", "v1.6", "CUSTOMER_SERVICE,OPERATIONS,SALES",
                        "仓配一体客户的出库承诺以系统下发时间为准。仓内原因导致超过 24 小时未出库，可按仓配服务费 5% 申请减免；爆仓、库存差异和拣货异常需分别记录。"),
                new KnowledgeSeed("sop-vip-communication", "VIP 客户异常沟通规范", "manual", "customer_service", "v1.7", "CUSTOMER_SERVICE,OPERATIONS,OPS_MANAGER,SALES",
                        "VIP 客户出现 P1 异常时，客服回复需包含当前节点、原因、预计恢复时间、负责人和下一次同步时间。对外口径应避免使用内部责任推诿表述。"),
                new KnowledgeSeed("rule-audit", "Agent 回答安全与审计规则", "policy", "agent_governance", "v1.0", "CUSTOMER_SERVICE,OPERATIONS,OPS_MANAGER,SALES",
                        "Agent 回答必须区分业务数据和知识库依据。缺少工具查询结果时不得编造运单、金额、时效或赔付结论。涉及赔付、合同和责任认定时只能给建议并提示人工确认。")
        );

        for (KnowledgeSeed seed : seeds) {
            jdbcTemplate.update("""
                    INSERT INTO ai_knowledge_document
                    (tenant_id, doc_id, title, doc_type, biz_domain, version, source_url, acl_roles,
                     effective_from, effective_to, status, content, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, "T001", seed.docId(), seed.title(), seed.docType(), seed.bizDomain(), seed.version(),
                    "kb://logistics/" + seed.docId(), seed.aclRoles(), Date.valueOf(seedDate.minusYears(1)), null,
                    "ACTIVE", seed.content(), Timestamp.valueOf(seedDate.atStartOfDay()), Timestamp.valueOf(seedDate.atStartOfDay()));

            jdbcTemplate.update("""
                    INSERT INTO ai_knowledge_chunk
                    (tenant_id, doc_id, chunk_id, title_path, content, metadata, acl_roles, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, "T001", seed.docId(), seed.docId() + "-chunk-001", seed.title(), seed.content(),
                    "docType=" + seed.docType() + ";version=" + seed.version() + ";bizDomain=" + seed.bizDomain(),
                    seed.aclRoles(), Timestamp.valueOf(seedDate.atStartOfDay()));
        }
    }

    private void syncKnowledgeVectors() {
        List<KnowledgeChunk> chunks = jdbcTemplate.query("""
                SELECT doc_id, chunk_id, title_path, content, metadata, acl_roles
                FROM ai_knowledge_chunk
                WHERE tenant_id = ?
                ORDER BY id
                """, (rs, rowNum) -> new KnowledgeChunk(
                rs.getString("doc_id"),
                rs.getString("chunk_id"),
                rs.getString("title_path"),
                rs.getString("content"),
                rs.getString("metadata"),
                rs.getString("acl_roles")
        ), "T001");
        vectorKnowledgeStore.syncChunks(chunks);
    }

    private record KnowledgeSeed(
            String docId,
            String title,
            String docType,
            String bizDomain,
            String version,
            String aclRoles,
            String content
    ) {
    }
}
