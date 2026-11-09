package com.fptufa26.ecomotion.verification.identity.repository;

import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PersonIdentityRepository extends JpaRepository<PersonIdentity, Long> {

    Optional<PersonIdentity> findByCccdFingerprint(String cccdFingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PersonIdentity i where i.id = :id")
    Optional<PersonIdentity> findByIdForUpdate(@Param("id") Long id);
}
