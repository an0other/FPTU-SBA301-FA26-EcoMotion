package com.fptufa26.ecomotion.common.security;

import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.enums.*;
import com.fptufa26.ecomotion.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProviderOnboardingSecurityTest {
    private final JwtService jwt = mock(JwtService.class);
    private final CustomUserDetailsService details = mock(CustomUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, details);
    private User user;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        user = new User();
        user.setId(1L);
        user.setRole(Role.PROVIDER);
        user.setStatus(UserStatus.PENDING);
        user.setEmailVerifiedAt(Instant.now());
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(jwt.extractUserId("token")).thenReturn(1L);
        var claims = mock(Claims.class);
        when(claims.get("scope", String.class)).thenReturn("ONBOARDING");
        when(jwt.parseClaims("token")).thenReturn(claims);
        when(details.loadUserById(1L)).thenAnswer(invocation -> new SecurityUser(user, false));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private MockHttpServletResponse invoke(String method, String path, FilterChain chain) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void pendingProviderHasOnlyOnboardingAuthority() throws Exception {
        var chain = mock(FilterChain.class);
        assertEquals(200, invoke("GET", "/api/provider/onboarding", chain).getStatus());
        verify(chain).doFilter(any(), any());
        var principal = (SecurityUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals("ONBOARDING", principal.getSessionScope());
        assertFalse(principal.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_PROVIDER")));
    }

    @Test
    void restrictedProviderCannotAccessOperationalOrGenericEndpoints() throws Exception {
        for (String path : new String[]{"/api/provider/vehicles", "/api/v1/provider/drivers", "/api/bookings", "/api/admin/users"}) {
            SecurityContextHolder.clearContext();
            var chain = mock(FilterChain.class);
            assertEquals(403, invoke("POST", path, chain).getStatus());
            verifyNoInteractions(chain);
        }
    }

    @Test
    void oldOnboardingTokenDoesNotGainOperationsAfterApproval() throws Exception {
        user.setStatus(UserStatus.ACTIVE);
        when(details.loadUserById(1L)).thenReturn(new SecurityUser(user, true));
        assertEquals(403, invoke("POST", "/api/provider/vehicles", mock(FilterChain.class)).getStatus());
    }

    @Test
    void lockedProviderIsNotAuthenticated() throws Exception {
        user.setStatus(UserStatus.LOCKED);
        invoke("GET", "/api/provider/onboarding", mock(FilterChain.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void databaseRestrictionsOverrideFullToken() throws Exception {
        var claims = mock(Claims.class);
        when(claims.get("scope", String.class)).thenReturn("FULL");
        when(jwt.parseClaims("token")).thenReturn(claims);
        user.setStatus(UserStatus.ACTIVE);
        assertEquals(403, invoke("POST", "/api/provider/vehicles", mock(FilterChain.class)).getStatus());
    }

    @Test
    void pendingRenterStillCannotAuthenticate() {
        user.setRole(Role.RENTER);
        assertFalse(new SecurityUser(user).isEnabled());
    }

    @Test
    void providerWithoutCanonicalIdentityDoesNotGetOperationalRole() {
        var users = mock(UserService.class);
        user.setStatus(UserStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));
        var result = (SecurityUser) new CustomUserDetailsService(users).loadUserById(1L);
        assertTrue(result.isRestrictedProvider());
    }
}
