package com.fptufa26.ecomotion.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchemaCompatibilityPatcher implements ApplicationRunner {

    private static final String[] STATEMENTS = {
            "ALTER TABLE payment_evidence ALTER COLUMN proof_image_url DROP NOT NULL",
            "ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_category_check",
            "ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_decision_check",
            "ALTER TABLE booking DROP CONSTRAINT IF EXISTS booking_proposal_resolution_check",
            "ALTER TABLE booking DROP CONSTRAINT IF EXISTS booking_operational_resolution_check",
            "UPDATE booking_charge SET waived_source = 'ADMIN_DISPUTE' WHERE is_waived AND waived_source IS NULL"
                    + " AND id IN (SELECT charge_id FROM dispute WHERE decision = 'WAIVE_CHARGE' AND charge_id IS NOT NULL)",
            "UPDATE booking_charge SET waived_source = 'SYSTEM' WHERE is_waived AND waived_source IS NULL AND charge_type = 'OUT_OF_RADIUS'",
            "UPDATE booking_charge SET waived_source = 'PROVIDER_WITHDRAWN' WHERE is_waived AND waived_source IS NULL",
    };

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (RuntimeException ex) {
                log.debug("Bỏ qua vá schema '{}': {}", sql, ex.getMessage());
            }
        }
    }
}
