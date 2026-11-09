package com.fptufa26.ecomotion.verification.identity.integration;

import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityVideoResult;
import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityAiConstraintsResponse;
import com.fptufa26.ecomotion.verification.identity.exception.IdentityAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Set;

@Component
public class IdentityVerificationClient {

    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;

    @Autowired
    public IdentityVerificationClient(
            @Value("${app.identity.ai.base-url:http://127.0.0.1:8002}") String baseUrl,
            @Value("${app.identity.ai.api-key:}") String apiKey,
            @Value("${app.identity.ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.identity.ai.read-timeout:180s}") Duration readTimeout) {
        this(buildClient(connectTimeout, readTimeout), baseUrl, apiKey);
    }

    private static RestClient buildClient(Duration connectTimeout, Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }

    IdentityVerificationClient(RestClient client, String baseUrl, String apiKey) {
        this.client = client;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public IdentityVideoResult assess(byte[] front, String frontFilename, byte[] video) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AI_NOT_CONFIGURED",
                    "IdentityVerification chưa được cấu hình trên backend.");
        }
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("cccd", resource(front, frontFilename));
        body.add("video", resource(video, "capture.video"));
        try {
            IdentityVideoResult result = client.post()
                    .uri(baseUrl + "/v1/verifications")
                    .header("X-API-Key", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(IdentityVideoResult.class);
            validate(result);
            return result;
        } catch (RestClientResponseException exception) {
            int code = exception.getStatusCode().value();
            if (code == 503 || code == 429) {
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AI_BUSY",
                        "Dịch vụ xác minh đang bận, vui lòng thử lại.");
            }
            if (code == 400 || code == 413 || code == 415 || code == 422) {
                throw failure(HttpStatus.valueOf(code), "IDENTITY_AI_INPUT_REJECTED",
                        "Ảnh hoặc video chưa đáp ứng yêu cầu của dịch vụ xác minh.");
            }
            throw failure(HttpStatus.BAD_GATEWAY, "IDENTITY_AI_UPSTREAM_ERROR",
                    "Không thể nhận kết quả từ dịch vụ xác minh.");
        } catch (ResourceAccessException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException) {
                    throw failure(HttpStatus.GATEWAY_TIMEOUT, "IDENTITY_AI_TIMEOUT",
                            "Dịch vụ xác minh phản hồi quá thời gian cho phép.");
                }
            }
            throw failure(HttpStatus.BAD_GATEWAY, "IDENTITY_AI_UNAVAILABLE",
                    "Không thể kết nối dịch vụ xác minh.");
        } catch (RestClientException exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "IDENTITY_AI_INVALID_RESPONSE",
                    "Dịch vụ xác minh trả kết quả không hợp lệ.");
        }
    }

    public IdentityAiConstraintsResponse constraints() {
        if (baseUrl.isBlank() || apiKey.isBlank()) throw failure(HttpStatus.SERVICE_UNAVAILABLE,
                "IDENTITY_AI_NOT_CONFIGURED", "Identity verification is not configured");
        try {
            var result = client.get().uri(baseUrl + "/v1/constraints").header("X-API-Key", apiKey).retrieve()
                    .body(IdentityAiConstraintsResponse.class);
            if (result == null || result.maxVideoSeconds() == null || !Double.isFinite(result.maxVideoSeconds())
                    || result.maxVideoSeconds() <= 0 || result.maxImageBytes() == null || result.maxImageBytes() <= 0
                    || result.maxVideoBytes() == null || result.maxVideoBytes() <= 0 || result.challenge() == null) throw invalidResponse();
            return result;
        } catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 401 || error.getStatusCode().value() == 403) {
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AI_AUTH_FAILED", "Backend không xác thực được với dịch vụ AI. Vui lòng kiểm tra API key.");
            }
            if (error.getStatusCode().value() == 404) {
                throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AI_ENDPOINT_MISSING", "Dịch vụ AI chưa có endpoint cấu hình. Vui lòng cập nhật và khởi động lại dịch vụ AI.");
            }
            throw failure(HttpStatus.BAD_GATEWAY, "IDENTITY_AI_UPSTREAM_ERROR", "Dịch vụ AI trả lỗi khi lấy cấu hình xác minh.");
        } catch (ResourceAccessException error) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_AI_UNAVAILABLE", "Identity verification is unavailable");
        } catch (RestClientException error) {
            throw invalidResponse();
        }
    }

    private void validate(IdentityVideoResult result) {
        if (result == null || result.sampledFrames() == null || result.sampledFrames() < 1
                || result.motionChallenge() == null || result.faceMatching() == null
                || result.workflow() == null || result.workflow().status() == null
                || result.motionChallenge().status() == null
                || !Set.of("PASS", "INCOMPLETE").contains(result.motionChallenge().status())
                || !Set.of("READY_FOR_REVIEW", "RETRY_REQUIRED").contains(result.workflow().status())
                || !"NOT_EVALUATED".equals(result.identityDecision())
                || !Boolean.FALSE.equals(result.automaticApproval())) {
            throw invalidResponse();
        }
        var matching = result.faceMatching();
        if (matching.candidateFrames() == null || matching.scoredFrames() == null
                || matching.unusableFrames() == null || matching.scoredFrames() < 0
                || matching.unusableFrames() < 0
                || matching.candidateFrames() != matching.scoredFrames() + matching.unusableFrames()
                || matching.candidateFrames() > result.sampledFrames()) {
            throw invalidResponse();
        }
        if (matching.scoredFrames() > 0
                && (!validScore(matching.minimum()) || !validScore(matching.median())
                || !validScore(matching.maximum()) || matching.minimum() > matching.median()
                || matching.median() > matching.maximum())) {
            throw invalidResponse();
        }
        boolean ready = "PASS".equals(result.motionChallenge().status()) && matching.scoredFrames() > 0;
        if (ready != "READY_FOR_REVIEW".equals(result.workflow().status())) {
            throw invalidResponse();
        }
    }

    private boolean validScore(Double score) {
        return score != null && Double.isFinite(score) && score >= -1 && score <= 1;
    }

    private IdentityAiException invalidResponse() {
        return failure(HttpStatus.BAD_GATEWAY, "IDENTITY_AI_INVALID_RESPONSE",
                "Dịch vụ xác minh trả kết quả không hợp lệ.");
    }

    private IdentityAiException failure(HttpStatus status, String code, String message) {
        return new IdentityAiException(status, code, message);
    }

    private ByteArrayResource resource(byte[] content, String filename) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
