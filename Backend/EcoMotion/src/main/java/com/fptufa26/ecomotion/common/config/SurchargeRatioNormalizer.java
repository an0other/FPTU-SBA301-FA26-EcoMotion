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
public class SurchargeRatioNormalizer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int total = 0;
        for (String table : new String[]{"rental_plan", "booking_policy_snapshot"}) {
            for (String column : new String[]{"overtime_surcharge_ratio", "weekend_surcharge_ratio"}) {
                try {
                    total += jdbcTemplate.update(
                            "UPDATE " + table + " SET " + column + " = " + column + " - 1 WHERE " + column + " > 1");
                } catch (RuntimeException ex) {
                    log.warn("Bỏ qua chuẩn hoá {}.{}: {}", table, column, ex.getMessage());
                }
            }
        }
        if (total > 0) {
            log.info("Đã chuẩn hoá {} tỉ lệ phụ thu từ dạng hệ số sang phần trăm cộng thêm", total);
        }
    }
}
