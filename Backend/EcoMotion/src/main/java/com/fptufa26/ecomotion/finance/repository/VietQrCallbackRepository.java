package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.VietQrCallback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VietQrCallbackRepository extends JpaRepository<VietQrCallback, Long> {
    Optional<VietQrCallback> findByTransactionId(String transactionId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM VietQrCallback c, PaymentRequest p WHERE c.paymentRequestId = p.id AND p.booking.vehicle.provider.user.id = :userId ORDER BY c.receivedAt DESC")
    org.springframework.data.domain.Page<VietQrCallback> findProviderCallbacks(
            @org.springframework.data.repository.query.Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);
}
