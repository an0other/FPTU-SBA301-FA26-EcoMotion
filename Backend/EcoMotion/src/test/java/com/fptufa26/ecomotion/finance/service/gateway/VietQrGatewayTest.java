package com.fptufa26.ecomotion.finance.service.gateway;

import com.fptufa26.ecomotion.finance.config.VietQrProperties;
import com.fptufa26.ecomotion.finance.service.implement.VietQrCallbackAuthServiceImpl;
import com.fptufa26.ecomotion.finance.service.implement.VietQrTokenServiceImpl;
import com.fptufa26.ecomotion.finance.service.gateway.implement.VietQrPaymentGatewayStrategyImpl;
import java.math.BigDecimal;
import java.time.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class VietQrGatewayTest {
    private VietQrProperties properties() {
        var p = new VietQrProperties(); p.setEnabled(true); p.setUsername("outbound-test"); p.setPassword("outbound-password");
        p.setCallbackUsername("inbound-test"); p.setCallbackPassword("inbound-password");
        p.setCallbackSecret("test-callback-key-with-at-least-32-bytes"); return p;
    }

    @Test
    void tokenIsCachedAndRefreshedBeforeExpiry() {
        var clock = mock(Clock.class); var now = Instant.parse("2026-11-02T08:00:00Z"); when(clock.instant()).thenReturn(now);
        var builder = RestClient.builder().baseUrl("https://dev.vietqr.org"); var server = MockRestServiceServer.bindTo(builder).build();
        for (String token : new String[]{"first-token", "second-token"}) server.expect(requestTo("https://dev.vietqr.org/vqr/api/token_generate"))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString("outbound-test:outbound-password".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        var service = new VietQrTokenServiceImpl(builder.build(), properties(), clock);
        assertThat(service.getToken()).isEqualTo("first-token"); assertThat(service.getToken()).isEqualTo("first-token");
        when(clock.instant()).thenReturn(now.plusSeconds(271)); assertThat(service.getToken()).isEqualTo("second-token"); server.verify();
    }

    @Test
    void dynamicQrUsesActualResponseAndRejectsWrongBeneficiaryOrAmount() {
        var builder = RestClient.builder().baseUrl("https://dev.vietqr.org"); var server = MockRestServiceServer.bindTo(builder).build();
        String response = "{\"orderId\":\"VQ000001\",\"bankCode\":\"VCB\",\"bankAccount\":\"123456\",\"amount\":30000,\"content\":\"PAY VQ000001\",\"qrCode\":\"payload\",\"qrLink\":\"https://pro.vietqr.vn/fixture\"}";
        server.expect(requestTo("https://dev.vietqr.org/vqr/api/qr/generate-customer")).andExpect(header("Authorization", "Bearer fixture-token"))
                .andExpect(content().json("{\"qrType\":0,\"transType\":\"C\",\"orderId\":\"VQ000001\",\"amount\":30000}"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://dev.vietqr.org/vqr/api/qr/generate-customer"))
                .andRespond(withSuccess(response.replace("123456", "999999"), MediaType.APPLICATION_JSON));
        var gateway = new VietQrPaymentGatewayStrategyImpl(builder.build(), () -> "fixture-token");
        assertThat(gateway.generate("VQ000001", "VCB", "123456", "TEST OWNER", new BigDecimal("30000")).content()).isEqualTo("PAY VQ000001");
        assertThatThrownBy(() -> gateway.generate("VQ000001", "VCB", "123456", "TEST OWNER", new BigDecimal("30000")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class); server.verify();
    }

    @Test
    void timeoutDoesNotCreateAResult() {
        var builder = RestClient.builder().baseUrl("https://dev.vietqr.org"); var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException("fixture timeout")));
        var gateway = new VietQrPaymentGatewayStrategyImpl(builder.build(), () -> "fixture-token");
        assertThatThrownBy(() -> gateway.generate("VQ000001", "VCB", "123456", "TEST OWNER", new BigDecimal("30000")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class); server.verify();
    }

    @Test
    void inboundCredentialsAndTokensAreSeparateAndExpire() {
        var clock = mock(Clock.class); var now = Instant.parse("2026-11-02T08:00:00Z"); when(clock.instant()).thenReturn(now);
        var service = new VietQrCallbackAuthServiceImpl(properties(), clock);
        assertThatThrownBy(() -> service.issueToken("Basic wrong")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        String basic = "Basic " + Base64.getEncoder().encodeToString("inbound-test:inbound-password".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = (String) service.issueToken(basic).get("access_token"); service.authenticate("Bearer " + token);
        when(clock.instant()).thenReturn(now.plusSeconds(301));
        assertThatThrownBy(() -> service.authenticate("Bearer " + token)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void sixDigitCodeExhaustionFailsWithoutWrapping() {
        var repository = mock(com.fptufa26.ecomotion.finance.repository.PaymentCodeSequenceRepository.class);
        when(repository.findByPrefixForUpdate("VQ")).thenReturn(java.util.Optional.of(new com.fptufa26.ecomotion.finance.entity.PaymentCodeSequence("VQ", 1000000L)));
        var generator = new com.fptufa26.ecomotion.finance.service.implement.PaymentCodeGeneratorImpl(repository);
        assertThatThrownBy(() -> generator.generatePaymentCode("VQ")).isInstanceOf(com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void productionCannotUseSandboxOrAnUnapprovedHost() {
        var config = new com.fptufa26.ecomotion.finance.config.VietQrClientConfig();
        var environment = new org.springframework.mock.env.MockEnvironment(); environment.setActiveProfiles("production");
        var properties = properties();
        assertThatThrownBy(() -> config.vietQrRestClient(properties, environment)).isInstanceOf(IllegalStateException.class);
        properties.setEnvironment("production"); properties.setBaseUrl("https://unapproved.test");
        assertThatThrownBy(() -> config.vietQrRestClient(properties, environment)).isInstanceOf(IllegalStateException.class);
    }
}
