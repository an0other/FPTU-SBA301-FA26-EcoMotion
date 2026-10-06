package com.fptufa26.ecomotion.auth.service;

import com.fptufa26.ecomotion.auth.exception.CaptchaInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for {@link CaptchaService}. The Google siteverify call is stubbed with
 * {@link MockRestServiceServer}, so the suite never touches the network.
 */
class CaptchaServiceTest {

    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private static CaptchaService service(RestClient.Builder builder, boolean enabled, double minScore) {
        return new CaptchaService(builder, "test-secret", enabled, minScore);
    }

    @Test
    @DisplayName("disabled captcha skips the network call entirely")
    void disabledSkipsVerification() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaptchaService captcha = service(builder, false, 0.5);

        assertThatCode(() -> captcha.verify(null, "login", "127.0.0.1")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("a blank token is rejected before calling Google")
    void blankTokenRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaptchaService captcha = service(builder, true, 0.5);

        assertThatThrownBy(() -> captcha.verify("   ", "login", "127.0.0.1"))
                .isInstanceOf(CaptchaInvalidException.class);
        server.verify();
    }

    @Test
    @DisplayName("a valid token with a high score passes and sends the expected form body")
    void validTokenPasses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaptchaService captcha = service(builder, true, 0.5);

        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("response=good-token")))
                .andRespond(withSuccess(
                        "{\"success\":true,\"score\":0.9,\"action\":\"login\"}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> captcha.verify("good-token", "login", "127.0.0.1")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    @DisplayName("success=false is rejected")
    void unsuccessfulRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaptchaService captcha = service(builder, true, 0.5);

        server.expect(requestTo(VERIFY_URL))
                .andRespond(withSuccess(
                        "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> captcha.verify("bad-token", "login", "127.0.0.1"))
                .isInstanceOf(CaptchaInvalidException.class);
    }

    @Test
    @DisplayName("a score below the threshold is rejected")
    void lowScoreRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaptchaService captcha = service(builder, true, 0.5);

        server.expect(requestTo(VERIFY_URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"score\":0.1,\"action\":\"login\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> captcha.verify("low-score", "login", "127.0.0.1"))
                .isInstanceOf(CaptchaInvalidException.class);
    }

    @Test
    @DisplayName("an action mismatch is rejected")
    void actionMismatchRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaptchaService captcha = service(builder, true, 0.5);

        server.expect(requestTo(VERIFY_URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"score\":0.9,\"action\":\"register\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> captcha.verify("token", "login", "127.0.0.1"))
                .isInstanceOf(CaptchaInvalidException.class);
    }

    @Test
    @DisplayName("a network failure fails closed rather than letting the request through")
    void networkFailureFailsClosed() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CaptchaService captcha = service(builder, true, 0.5);

        server.expect(requestTo(VERIFY_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> captcha.verify("token", "login", "127.0.0.1"))
                .isInstanceOf(CaptchaInvalidException.class);
    }
}
