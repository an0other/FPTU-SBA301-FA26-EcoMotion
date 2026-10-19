package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.PaymentEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentEvidenceRepository extends JpaRepository<PaymentEvidence, Long> {

    List<PaymentEvidence> findByPaymentRequest_IdOrderByCreatedAtDesc(Long paymentRequestId);

    List<PaymentEvidence> findByPaymentRequest_Booking_Id(Long bookingId);
}
