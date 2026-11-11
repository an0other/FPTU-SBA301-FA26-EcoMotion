package com.fptufa26.ecomotion.dispute.service.implement;

import com.fptufa26.ecomotion.dispute.entity.Dispute;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisputeQueryServiceImplTest {

    private final DisputeRepository repository = mock(DisputeRepository.class);
    private final DisputeQueryServiceImpl service = new DisputeQueryServiceImpl(repository);

    private void disputes(Dispute... rows) {
        when(repository.findByBooking_Id(1L)).thenReturn(List.of(rows));
    }

    private static Dispute dispute(DisputeCategory category, DisputeStatus status, Long paymentRequestId) {
        Dispute d = new Dispute();
        d.setCategory(category);
        d.setStatus(status);
        d.setPaymentRequestId(paymentRequestId);
        return d;
    }

    @Test
    void serviceComplaintNeverBlocksPayments() {
        disputes(dispute(DisputeCategory.SERVICE_QUALITY, DisputeStatus.OPEN, null));
        assertThat(service.blocksPayment(1L, 10L)).isFalse();
        assertThat(service.blocksPayment(1L, 11L)).isFalse();
    }

    @Test
    void mismatchBlocksOnlyTheDisputedRequest() {
        disputes(dispute(DisputeCategory.PAYMENT_MISMATCH, DisputeStatus.IN_REVIEW, 10L));
        assertThat(service.blocksPayment(1L, 10L)).isTrue();
        assertThat(service.blocksPayment(1L, 20L)).as("kỳ thanh toán khác vẫn trả được").isFalse();
    }

    @Test
    void chargeDisputeBlocksEveryPayment() {
        disputes(dispute(DisputeCategory.CHARGE, DisputeStatus.OPEN, null));
        assertThat(service.blocksPayment(1L, 30L)).isTrue();
        assertThat(service.blocksPayment(1L, 31L)).isTrue();
    }

    @Test
    void resolvedDisputesDoNotBlock() {
        disputes(dispute(DisputeCategory.CHARGE, DisputeStatus.RESOLVED, null),
                dispute(DisputeCategory.YEARLY_SETTLEMENT, DisputeStatus.REJECTED, null));
        assertThat(service.blocksPayment(1L, 30L)).isFalse();
    }
}
