package com.fptufa26.ecomotion.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ddl-auto=update không sửa ràng buộc cũ: CHECK của enum (Hibernate sinh lúc tạo bảng) chặn giá trị enum mới,
 * và NOT NULL cũ chặn cột đã chuyển sang tuỳ chọn. Chạy idempotent lúc khởi động; bản SQL tương ứng nằm ở
 * db/migrations/20261110_payment_mismatch.sql.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchemaCompatibilityPatcher implements ApplicationRunner {

    private static final String[] STATEMENTS = {
            // Ảnh biên lai chuyển khoản là tuỳ chọn.
            "ALTER TABLE payment_evidence ALTER COLUMN proof_image_url DROP NOT NULL",
            // Loại khiếu nại/phán quyết mới (PAYMENT_MISMATCH, OTHER, ...): enum được kiểm ở tầng ứng dụng.
            "ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_category_check",
            "ALTER TABLE dispute DROP CONSTRAINT IF EXISTS dispute_decision_check",
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
