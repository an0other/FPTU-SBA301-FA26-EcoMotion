package com.fptufa26.ecomotion.verification.identity.integration;

import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IdentityVerificationClientTest {

    private static final String VALID_RESULT = """
            {"sampledFrames":15,"motionChallenge":{"status":"PASS","expected":[],"completed":[]},
             "faceMatching":{"candidateFrames":8,"scoredFrames":8,"unusableFrames":0,
             "minimum":0.51,"median":0.55,"maximum":0.59,"frames":[]},
             "identityDecision":"NOT_EVALUATED","automaticApproval":false,
             "workflow":{"status":"READY_FOR_REVIEW","reasons":[]}}
            """;

    private MockRestServiceServer server;
    private IdentityVerificationClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IdentityVerificationClient(builder.build(), "http://identity.test/", "test-service-key");
    }

    @Test
    void readsPythonConstraintsWithoutSpringReadinessFlags() {
        server.expect(requestTo("http://identity.test/v1/constraints"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", "test-service-key"))
                .andRespond(withSuccess("""
                        {"maxImageBytes":8388608,"maxImagePixels":20000000,"maxVideoBytes":20971520,
                         "maxVideoSeconds":15.0,"maxVideoFps":120.0,"maxVideoFrames":1800,
                         "maxVideoPixels":2073600,"imageFormats":["JPEG","PNG"],
                         "videoContainers":["MP4","MOV","WebM","Matroska"],
                         "challenge":["CENTER","POSITIVE","NEGATIVE","CENTER"],"minimumHoldSeconds":0.5}
                        """, MediaType.APPLICATION_JSON));
        var result = client.constraints();
        assertEquals(15.0, result.maxVideoSeconds());
        assertEquals("CENTER", result.challenge().getFirst());
        server.verify();
    }

    @Test
    void sendsMultipartAndServiceKeyAndKeepsDecisionUnevaluated() {
        server.expect(requestTo("http://identity.test/v1/verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-API-Key", "test-service-key"))
                .andExpect(request -> assertTrue(request.getHeaders().getContentType()
                        .isCompatibleWith(MediaType.MULTIPART_FORM_DATA)))
                .andRespond(withSuccess(VALID_RESULT, MediaType.APPLICATION_JSON));
        var result = client.assess(new byte[]{1}, "front.png", new byte[]{2});
        assertEquals(0.55, result.faceMatching().median());
        assertEquals("NOT_EVALUATED", result.identityDecision());
        assertFalse(result.automaticApproval());
        server.verify();
    }

    @Test
    void mapsBusyResponseWithoutLeakingUpstreamBody() {
        server.expect(requestTo("http://identity.test/v1/verifications"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("private-upstream-value"));
        var error = assertThrows(IdentityAiException.class,
                () -> client.assess(new byte[]{1}, "front.png", new byte[]{2}));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("IDENTITY_AI_BUSY", error.getErrorCode());
        assertFalse(error.getMessage().contains("private-upstream-value"));
    }

    @Test
    void mapsInputRejectionSeparatelyFromInfrastructureFailure() {
        server.expect(requestTo("http://identity.test/v1/verifications"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));
        var error = assertThrows(IdentityAiException.class,
                () -> client.assess(new byte[]{1}, "front.png", new byte[]{2}));
        assertEquals(422, error.getStatus().value());
        assertEquals("IDENTITY_AI_INPUT_REJECTED", error.getErrorCode());
    }

    @Test
    void rejectsUnexpectedAutomaticApprovalContract() {
        server.expect(requestTo("http://identity.test/v1/verifications"))
                .andRespond(withSuccess(VALID_RESULT.replace("\"automaticApproval\":false",
                        "\"automaticApproval\":true"), MediaType.APPLICATION_JSON));
        var error = assertThrows(IdentityAiException.class,
                () -> client.assess(new byte[]{1}, "front.png", new byte[]{2}));
        assertEquals("IDENTITY_AI_INVALID_RESPONSE", error.getErrorCode());
    }

    @Test
    void rejectsImpossibleScoreSummary() {
        server.expect(requestTo("http://identity.test/v1/verifications"))
                .andRespond(withSuccess(VALID_RESULT.replace("\"median\":0.55", "\"median\":1.5"),
                        MediaType.APPLICATION_JSON));
        var error = assertThrows(IdentityAiException.class,
                () -> client.assess(new byte[]{1}, "front.png", new byte[]{2}));
        assertEquals("IDENTITY_AI_INVALID_RESPONSE", error.getErrorCode());
    }
}
