package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByBooking_Id(Long bookingId);
}
