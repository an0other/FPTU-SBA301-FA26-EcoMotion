package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;

import java.util.Optional;

/** Đọc hồ sơ định danh cho module khác (thay cho truy vấn thẳng entity qua EntityManager). */
public interface PersonIdentityQueryService {
    Optional<PersonIdentity> findById(Long identityId);
}
