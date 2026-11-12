package com.fptufa26.ecomotion.verification.identity;

import com.fptufa26.ecomotion.auth.service.TokenService;
import com.fptufa26.ecomotion.auth.exception.ActivationTokenInvalidException;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.verification.identity.controller.IdentityReadController;
import com.fptufa26.ecomotion.verification.identity.service.IdentityReadService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityActivationStatusTest {
    @Test void validActivationTokenCanReadActiveStatusButNotLockedOrDifferentOwner() {
        var users=mock(UserService.class);var tokens=mock(TokenService.class);var reads=mock(IdentityReadService.class);
        var controller=new IdentityReadController(reads,tokens,users);
        var user=new User();user.setId(1L);user.setEmail("renter@example.test");user.setRole(Role.RENTER);
        when(tokens.verifyActivation("valid")).thenReturn(new TokenService.ActivationPrincipal(1L,"renter@example.test",Role.RENTER));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        var request=new IdentityReadController.Activation("valid");
        user.setStatus(UserStatus.PENDING);assertDoesNotThrow(()->controller.activationStatus(request));
        user.setStatus(UserStatus.ACTIVE);assertDoesNotThrow(()->controller.activationStatus(request));
        verify(reads,times(2)).status(1L);
        user.setStatus(UserStatus.LOCKED);assertThrows(ActivationTokenInvalidException.class,()->controller.activationStatus(request));
        user.setStatus(UserStatus.ACTIVE);user.setEmail("other@example.test");assertThrows(ActivationTokenInvalidException.class,()->controller.activationStatus(request));
        verifyNoMoreInteractions(reads);
    }
}
