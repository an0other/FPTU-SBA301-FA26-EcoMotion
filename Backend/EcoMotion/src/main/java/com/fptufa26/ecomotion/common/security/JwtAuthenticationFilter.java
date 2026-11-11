package com.fptufa26.ecomotion.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (jwtService.isTokenValid(token)) {
                    Long userId = jwtService.extractUserId(token);
                    SecurityUser userDetails = (SecurityUser) userDetailsService.loadUserById(userId);
                    if ("ONBOARDING".equals(jwtService.parseClaims(token).get("scope", String.class))) {
                        userDetails.restrictToOnboarding();
                    }

                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
            }
        }

        var current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.getPrincipal() instanceof SecurityUser principal
                && principal.isRestrictedProvider() && !onboardingAllowed(request)) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"errorCode\":\"PROVIDER_ONBOARDING_ONLY\",\"message\":\"Provider approval is required\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean onboardingAllowed(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && (path.startsWith("/api/auth/activate/")
                || java.util.Set.of("/api/auth/login", "/api/auth/register", "/api/auth/register/send-otp",
                "/api/auth/register/verify-otp", "/api/auth/forgot-password", "/api/auth/reset-password").contains(path))) return true;
        if ("GET".equals(request.getMethod()) && java.util.Set.of("/api/location/map", "/api/location/map/reverse",
                "/api/location/map/search", "/api/location/route").contains(path)) return true;
        return path.equals("/api/auth/me") || path.equals("/api/auth/logout") || path.equals("/api/auth/refresh")
                || path.equals("/api/provider/onboarding") || path.startsWith("/api/provider/onboarding/")
                || path.equals("/api/identity/attempts") || path.startsWith("/api/identity/attempts/")
                || path.equals("/api/identity/links") || path.startsWith("/api/identity/links/")
                || path.equals("/api/identity/constraints") || path.equals("/api/identity/status")
                || path.equals("/api/identity/assessments");
    }

    // Chỉ nhận JWT qua header Authorization. Không đọc query string: token trên URL dễ lộ qua log và proxy.
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        return null;
    }
}
