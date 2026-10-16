package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    @EntityGraph(attributePaths = "booking")
    Page<PaymentTransaction>
    findByBooking_Renter_User_IdAndTypeNotOrderByCreatedAtDescIdDesc(
            Long userId,
            PaymentType excludedType,
            Pageable pageable);
}