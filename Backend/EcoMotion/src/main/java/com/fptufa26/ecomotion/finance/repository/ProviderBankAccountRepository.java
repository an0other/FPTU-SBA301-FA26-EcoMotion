package com.fptufa26.ecomotion.finance.repository;

import com.fptufa26.ecomotion.finance.entity.ProviderBankAccount;
import com.fptufa26.ecomotion.finance.enums.BankAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderBankAccountRepository extends JpaRepository<ProviderBankAccount, Long> {

    Optional<ProviderBankAccount> findByProvider_IdAndStatus(Long providerId, BankAccountStatus status);

    List<ProviderBankAccount> findByProvider_IdOrderByCreatedAtDesc(Long providerId);

    Optional<ProviderBankAccount> findByIdAndProvider_Id(Long id, Long providerId);

    boolean existsByProvider_IdAndStatus(Long providerId, BankAccountStatus status);
}
