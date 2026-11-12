package com.fptufa26.ecomotion.verification.documentai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.Map;

@Service
public class DocumentAiClient {

    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final Duration readTimeout;

    public DocumentAiClient(
            @Value("${app.document-ai.base-url:}") String baseUrl,
            @Value("${app.document-ai.api-key:}") String apiKey,
            @Value("${app.document-ai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.document-ai.read-timeout:180s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.readTimeout = readTimeout;
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Map<String, Object> readDrivingLicense(
            byte[] front,
            String frontFilename,
            byte[] back,
            String backFilename) {
        return readDocument(front, frontFilename, back, backFilename, "VN_DRIVING_LICENSE");
    }

    public Map<String, Object> readCitizenId(
            byte[] front,
            String frontFilename,
            byte[] back,
            String backFilename) {
        return readDocument(front, frontFilename, back, backFilename, "VN_CITIZEN_ID");
    }

    private Map<String, Object> readDocument(
            byte[] front, String frontFilename, byte[] back, String backFilename, String documentType) {
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new DocumentAiUnavailableException(
                    "DocumentAI is not configured. Set DOCUMENT_AI_BASE_URL and DOCUMENT_AI_API_KEY.");
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("front", resource(front, frontFilename));
        body.add("back", resource(back, backFilename));
        body.add("document_type", documentType);

        try {
            Map<String, Object> response = com.fptufa26.ecomotion.verification.progress.AiProgressContext.active()
                    ? com.fptufa26.ecomotion.verification.progress.AiJobPolling.run(restClient, baseUrl + "/v1/documents/jobs", apiKey, body, readTimeout)
                    : restClient.post()
                    .uri(baseUrl + "/v1/documents/read")
                    .header("X-API-Key", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(RESPONSE_TYPE);
            if (response == null) {
                throw new DocumentAiUnavailableException("DocumentAI returned an empty response.");
            }
            return response;
        } catch (DocumentAiUnavailableException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new DocumentAiUnavailableException(
                    "DocumentAI is unavailable. Check its endpoint, API key, and service status.", ex);
        }
    }

    private ByteArrayResource resource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
