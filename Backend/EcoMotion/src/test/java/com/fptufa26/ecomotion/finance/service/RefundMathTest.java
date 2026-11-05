package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.config.VnpayUtils;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RefundMathTest {

    @Test
    void openAllocatedSubtractsObligationAndReservedIncludingWaive() {
        assertThat(RefundMath.openAllocated(new BigDecimal("300000"), new BigDecimal("1100000"),
                BigDecimal.ZERO, BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(RefundMath.openAllocated(new BigDecimal("300000"), new BigDecimal("100000"),
                BigDecimal.ZERO, new BigDecimal("100000"))).isEqualByComparingTo("100000");
        assertThat(RefundMath.openAllocated(new BigDecimal("300000"), new BigDecimal("100000"),
                BigDecimal.ZERO, new BigDecimal("200000"))).isEqualByComparingTo("0");
    }

    @Test
    void excessAndLateIpnStayOutsideAllocatedRefunds() {
        assertThat(RefundMath.countsTowardAllocatedRefund("EXCESS:15")).isFalse();
        assertThat(RefundMath.countsTowardAllocatedRefund("LATE_IPN:REF1")).isFalse();
        assertThat(RefundMath.countsTowardAllocatedRefund("WAIVE:9")).isTrue();
        assertThat(RefundMath.countsTowardAllocatedRefund("SETTLE:3")).isTrue();
    }

    @Test
    void vnpayTypeIsFullOnlyWhenTheTransactionWasNeverRefunded() {
        assertThat(RefundMath.vnpayRefundType(new BigDecimal("300000"), new BigDecimal("300000"), BigDecimal.ZERO)).isEqualTo("02");
        assertThat(RefundMath.vnpayRefundType(new BigDecimal("200000"), new BigDecimal("300000"), BigDecimal.ZERO)).isEqualTo("03");
        assertThat(RefundMath.vnpayRefundType(new BigDecimal("100000"), new BigDecimal("300000"), new BigDecimal("200000"))).isEqualTo("03");
    }

    @Test
    void gateway94AndPartialSuccessAreNotRefunded() {
        assertThat(RefundMath.mapGatewayResult("94", "00", true, false)).isEqualTo(RefundStatus.PENDING_RECONCILIATION);
        assertThat(RefundMath.mapGatewayResult("00", "05", true, false)).isEqualTo(RefundStatus.PROCESSING);
        assertThat(RefundMath.mapGatewayResult("00", "00", true, false)).isEqualTo(RefundStatus.REFUNDED);
        assertThat(RefundMath.mapGatewayResult("97", null, true, false)).isEqualTo(RefundStatus.FAILED);
        assertThat(RefundMath.mapGatewayResult("98", null, true, true)).isEqualTo(RefundStatus.PENDING_RECONCILIATION);
    }

    @Test
    void refundHashUsesRefundFieldOrderNotQueryString() {
        String data = VnpayUtils.refundRequestData("abc", "2.1.0", "TMN", "02", "REF1", "30000000",
                "123", "20261031120000", "ecomotion", "20261031120100", "127.0.0.1", "Refund");
        assertThat(data).isEqualTo("abc|2.1.0|refund|TMN|02|REF1|30000000|123|20261031120000|ecomotion|20261031120100|127.0.0.1|Refund");
        assertThat(data).doesNotContain("vnp_");
        assertThat(data).doesNotContain("&");
    }
}
