package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;

import java.util.Optional;

public interface PersonIdentityQueryService {
    Optional<PersonIdentity> findById(Long identityId);
}
