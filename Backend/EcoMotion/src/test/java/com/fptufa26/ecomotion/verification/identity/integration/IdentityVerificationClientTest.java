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

    @Test
    void validatesUncalibratedAssessmentAndRejectsContradictoryStatus() {
        String assessment="{\"status\":\"NOT_CALIBRATED\",\"score\":0.55,\"comparedFrames\":8,\"minimumFrames\":3,\"metric\":\"cosine_similarity\",\"modelSha256\":\""+"a".repeat(64)+"\",\"policyVersion\":\"test-v1\",\"calibrationStatus\":\"NOT_CALIBRATED\"}";
        String body=VALID_RESULT.stripTrailing();
        body=body.substring(0,body.length()-1)+",\"matchAssessment\":"+assessment+"}";
        server.expect(requestTo("http://identity.test/v1/verifications")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertEquals("NOT_CALIBRATED",client.assess(new byte[]{1},"front.png",new byte[]{2}).matchAssessment().status());
        server.verify();
        setUp();
        server.expect(requestTo("http://identity.test/v1/verifications")).andRespond(withSuccess(body.replace("NOT_CALIBRATED\",\"score", "LIKELY_MATCH\",\"score"),MediaType.APPLICATION_JSON));
        assertThrows(IdentityAiException.class,()->client.assess(new byte[]{1},"front.png",new byte[]{2}));
        server.verify();
    }

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IdentityVerificationClient(builder.build(), "http://identity.test/", "test-service-key");
    }

    @Test
    void asyncResultKeepsValidationAndClearsOperationProgress() {
        String id = "0123456789abcdef0123456789abcdef";
        var operation = java.util.UUID.randomUUID();
        String url = "http://identity.test/v1/verifications/jobs";
        server.expect(requestTo(url)).andExpect(header("X-API-Key", "test-service-key"))
                .andRespond(withSuccess("{\"jobId\":\"" + id + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url + "/" + id)).andRespond(withSuccess(
                "{\"state\":\"COMPLETED\",\"progress\":{\"stage\":\"FINALIZING\"},\"result\":" + VALID_RESULT + "}", MediaType.APPLICATION_JSON));
        try (var scope = com.fptufa26.ecomotion.verification.progress.AiProgressContext.open(operation)) {
            assertFalse(client.assess(new byte[]{1}, "front.png", new byte[]{2}).automaticApproval());
            assertEquals("FINALIZING", com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(operation).stage());
            var first = com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(operation);
            com.fptufa26.ecomotion.verification.progress.AiProgressContext.update(java.util.Map.of("stage", "FINALIZING", "sampledFrames", 12, "videoSeconds", 5.5));
            var next = com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(operation);
            assertEquals(first.updatedAt(), next.updatedAt());
            com.fptufa26.ecomotion.verification.progress.AiProgressContext.update(java.util.Map.of("stage", "FINALIZING", "events", java.util.List.of(
                    java.util.Map.of("stage", "READING_FIELDS", "side", "FRONT", "elapsedMs", 1),
                    java.util.Map.of("stage", "READING_FIELDS", "side", "BACK", "elapsedMs", 2),
                    java.util.Map.of("stage", "INVALID/<secret>", "side", "BACK", "elapsedMs", 3))));
            var stages = com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(operation).steps();
            assertEquals(2, stages.size());
            assertEquals("FRONT", stages.getFirst().side());
            assertEquals("BACK", stages.getLast().side());
            assertEquals(12L, next.sampledFrames());
            assertEquals(5.5, next.videoSeconds());
            com.fptufa26.ecomotion.verification.progress.AiProgressContext.update(java.util.Map.of("stage", "FINALIZING", "sampledFrames", -1, "videoSeconds", Double.NaN));
            assertEquals(null, com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(operation).sampledFrames());
            assertEquals(null, com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(operation).videoSeconds());
            assertEquals(null, com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(java.util.UUID.randomUUID()));
        }
        assertEquals(null, com.fptufa26.ecomotion.verification.progress.AiProgressContext.get(operation));
        server.verify();
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
