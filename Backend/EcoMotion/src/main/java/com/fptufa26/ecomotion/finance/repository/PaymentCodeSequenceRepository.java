package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.PaymentCodeSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCodeSequenceRepository extends JpaRepository<PaymentCodeSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PaymentCodeSequence s WHERE s.codePrefix = :prefix")
    Optional<PaymentCodeSequence> findByPrefixForUpdate(@Param("prefix") String prefix);
}
