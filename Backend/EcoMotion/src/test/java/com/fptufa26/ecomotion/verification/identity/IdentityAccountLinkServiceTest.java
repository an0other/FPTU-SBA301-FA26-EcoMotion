package com.fptufa26.ecomotion.verification.identity;

import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.entity.*;
import com.fptufa26.ecomotion.verification.identity.enums.PersonIdentityStatus;
import com.fptufa26.ecomotion.verification.identity.repository.*;
import com.fptufa26.ecomotion.verification.identity.service.IdentityEvidenceStorageService;
import com.fptufa26.ecomotion.verification.identity.service.implement.IdentityAccountLinkServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IdentityAccountLinkServiceTest {
    private final IdentityAccountLinkRepository links = mock(IdentityAccountLinkRepository.class);
    private final PersonIdentityRepository identities = mock(PersonIdentityRepository.class);
    private final UserService users = mock(UserService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneOffset.UTC);
    private IdentityAccountLinkServiceImpl service;
    private IdentityAccountLink stored;
    private User source;
    private User target;

    @BeforeEach
    void setup() {
        service = new IdentityAccountLinkServiceImpl(links, identities, mock(IdentityVerificationAttemptRepository.class),
                mock(IdentityEvidenceStorageService.class), users, passwords, clock);
        ReflectionTestUtils.setField(service, "lifetime", Duration.ofMinutes(15));
        var identity = new PersonIdentity();
        identity.setId(8L);
        identity.setStatus(PersonIdentityStatus.VERIFIED);
        source = new User();
        source.setId(1L);
        source.setEmail("source@example.test");
        source.setRole(Role.RENTER);
        source.setStatus(UserStatus.ACTIVE);
        source.setPasswordHash("source-hash");
        source.setIdentity(identity);
        target = new User();
        target.setId(2L);
        target.setEmail("target@example.test");
        target.setRole(Role.PROVIDER);
        target.setStatus(UserStatus.PENDING);
        target.setPasswordHash("target-hash");
        target.setEmailVerifiedAt(clock.instant());
        target.setTermsAcceptedAt(clock.instant());
        when(users.findByEmail(target.getEmail())).thenReturn(Optional.of(target));
        when(users.findById(1L)).thenReturn(Optional.of(source));
        when(users.findById(2L)).thenReturn(Optional.of(target));
        when(users.lockForIdentityDecision(1L)).thenReturn(source);
        when(users.lockForIdentityDecision(2L)).thenReturn(target);
        when(passwords.matches("source-password", "source-hash")).thenReturn(true);
        when(passwords.matches("target-password", "target-hash")).thenReturn(true);
        when(identities.findByIdForUpdate(8L)).thenReturn(Optional.of(identity));
        when(links.saveAndFlush(any())).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            if (stored.getVersion() == null) stored.setVersion(0L);
            return stored;
        });
        when(links.findById(any())).thenAnswer(invocation -> Optional.ofNullable(stored));
        when(links.findForUpdate(any())).thenAnswer(invocation -> Optional.ofNullable(stored));
    }

    @Test
    void bothAccountPasswordsAreRequiredBeforeBinding() {
        var result = service.request(1L, "source-password", target.getEmail());
        assertThrows(AccessDeniedException.class, () -> service.confirm(2L, result.id(), 0, "wrong"));
        verify(users, never()).attachLinkedIdentity(anyLong(), anyLong());
        assertEquals("CONFIRMED", service.confirm(2L, result.id(), 0, "target-password").status());
        verify(users).attachLinkedIdentity(2L, 8L);
    }

    @Test
    void differentTargetCannotConfirmRequest() {
        var result = service.request(1L, "source-password", target.getEmail());
        assertThrows(AccessDeniedException.class, () -> service.confirm(3L, result.id(), 0, "target-password"));
        verify(users, never()).attachLinkedIdentity(anyLong(), anyLong());
    }

    @Test
    void expiredRequestCannotBindIdentity() {
        var result = service.request(1L, "source-password", target.getEmail());
        stored.setExpiresAt(clock.instant());
        assertThrows(RuntimeException.class, () -> service.confirm(2L, result.id(), 0, "target-password"));
        verify(users, never()).attachLinkedIdentity(anyLong(), anyLong());
    }

    @Test
    void sourcePasswordResetInvalidatesPendingProof() {
        var result = service.request(1L, "source-password", target.getEmail());
        source.setPasswordHash("changed-hash");
        assertThrows(RuntimeException.class, () -> service.confirm(2L, result.id(), 0, "target-password"));
        verify(users, never()).attachLinkedIdentity(anyLong(), anyLong());
    }

    @Test
    void sameRoleAccountCannotBeLinked() {
        target.setRole(Role.RENTER);
        assertThrows(RuntimeException.class, () -> service.request(1L, "source-password", target.getEmail()));
        verify(links, never()).saveAndFlush(any());
    }
}
