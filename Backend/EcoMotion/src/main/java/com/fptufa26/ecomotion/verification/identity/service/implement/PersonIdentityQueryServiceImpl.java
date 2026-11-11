package com.fptufa26.ecomotion.verification.identity.service.implement;

import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;
import com.fptufa26.ecomotion.verification.identity.repository.PersonIdentityRepository;
import com.fptufa26.ecomotion.verification.identity.service.PersonIdentityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonIdentityQueryServiceImpl implements PersonIdentityQueryService {
    private final PersonIdentityRepository identities;

    @Override
    public Optional<PersonIdentity> findById(Long identityId) {
        return identities.findById(identityId);
    }
}
