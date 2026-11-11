package com.fptufa26.ecomotion.finance.job;

import com.fptufa26.ecomotion.finance.service.CommissionService;
import com.fptufa26.ecomotion.finance.service.implement.CommissionServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

/** Lập bảng kê hoa hồng đầu tháng và đánh dấu quá hạn (giờ Việt Nam). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommissionStatementJob {
    private final CommissionService commissions;
    private final Clock clock;

    /** 00:05 ngày 1: chốt kỳ tháng trước. */
    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Ho_Chi_Minh")
    public void issueLastMonth() {
        YearMonth last = YearMonth.now(clock.withZone(CommissionServiceImpl.VIETNAM)).minusMonths(1);
        commissions.generate(last);
    }

    /** 17:00 ngày 5 và mỗi giờ sau đó: bảng kê chưa nộp quá hạn → OVERDUE (idempotent, chạy lại sau khi khởi động). */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Ho_Chi_Minh")
    public void markOverdue() {
        int changed = commissions.markOverdue();
        if (changed > 0) log.info("Đánh dấu {} bảng kê hoa hồng quá hạn", changed);
    }
}
