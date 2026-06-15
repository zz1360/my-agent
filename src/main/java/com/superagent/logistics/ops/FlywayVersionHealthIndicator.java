package com.superagent.logistics.ops;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("flywayVersion")
public class FlywayVersionHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public FlywayVersionHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            String version = jdbcTemplate.query("""
                    SELECT version FROM flyway_schema_history
                    WHERE success = true
                    ORDER BY installed_rank DESC
                    LIMIT 1
                    """, rs -> rs.next() ? rs.getString("version") : "none");
            return Health.up()
                    .withDetail("version", version)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
