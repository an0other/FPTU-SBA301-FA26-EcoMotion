package com.fptufa26.ecomotion.common.config;

import com.fptufa26.ecomotion.common.security.JwtAuthenticationFilter;
import com.fptufa26.ecomotion.common.security.RestAccessDeniedHandler;
import com.fptufa26.ecomotion.common.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                                                   RestAccessDeniedHandler restAccessDeniedHandler) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/register", "/api/auth/register/send-otp",
                                "/api/auth/register/verify-otp",
                                "/api/auth/activate/**").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/images/**").permitAll()
                        .requestMatchers("/api/files/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/vehicles", "/api/vehicles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/location/map", "/api/location/map/reverse",
                                "/api/location/map/search", "/api/location/route").permitAll()
                        .requestMatchers(
                                "/api/v1/payment/vnpay/ipn", "/api/v1/payment/vnpay/callback",
                                "/api/v1/payments/vnpay/ipn", "/api/v1/payments/vnpay/callback",
                                "/api/payment/vnpay/ipn", "/api/payment/vnpay/callback",
                                "/api/payments/vnpay/ipn", "/api/payments/vnpay/callback",
                                "/v1/payment/vnpay/ipn", "/v1/payment/vnpay/callback",
                                "/payment/vnpay/ipn", "/payment/vnpay/callback").permitAll()
                        .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/provider/**", "/api/v1/provider/**").hasAnyRole("PROVIDER", "ADMIN")
                        .requestMatchers("/api/driver/**", "/api/v1/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/renter/**", "/api/v1/renter/**").hasRole("RENTER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
