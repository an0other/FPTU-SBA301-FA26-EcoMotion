package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.implement.UserServiceImpl;
import com.fptufa26.ecomotion.verification.identity.entity.PersonIdentity;
import com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserIdentityApprovalTest {
    @Test
    void approvedIdentityKeepsProviderPendingUntilApplicationApproval() {
        var repository = mock(UserRepository.class);
        var em = mock(EntityManager.class);
        var user = provider();
        var identity = verifiedIdentity();
        when(em.find(User.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(user);
        var service = new UserServiceImpl(repository, null, null, null, null, em, new StandardEnvironment());
        service.applyVerifiedIdentity(1L, identity);
        assertThat(user.getIdentity()).isSameAs(identity);
        assertThat(user.getIdentityReviewedAt()).isNotNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
        verify(repository).saveAndFlush(user);
    }

    @Test
    void missingPhoneVerificationReturnsSpecificConflictWithoutBindingIdentity() {
        var repository = mock(UserRepository.class);
        var em = mock(EntityManager.class);
        var user = provider();
        user.setPhoneVerifiedAt(null);
        when(em.find(User.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(user);
        var service = new UserServiceImpl(repository, null, null, null, null, em, new StandardEnvironment());
        assertThatThrownBy(() -> service.applyVerifiedIdentity(1L, verifiedIdentity()))
                .isInstanceOfSatisfying(IdentityAiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("IDENTITY_PHONE_VERIFICATION_REQUIRED"));
        assertThat(user.getIdentity()).isNull();
        verifyNoInteractions(repository);
    }

    private User provider() {
        var user = new User();
        user.setId(1L);
        user.setRole(Role.PROVIDER);
        user.setStatus(UserStatus.PENDING);
        user.setPhone("0900000000");
        user.setPhoneVerifiedAt(Instant.now());
        return user;
    }

    private PersonIdentity verifiedIdentity() {
        var identity = new PersonIdentity();
        identity.setId(8L);
        identity.setFullName("Demo Provider");
        identity.setStatus(PersonIdentityStatus.VERIFIED);
        return identity;
    }
}
