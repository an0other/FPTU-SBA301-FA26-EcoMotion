package com.fptufa26.ecomotion.verification.ekyc.implement;

import com.fptufa26.ecomotion.verification.ekyc.EkycHttpClient;
import com.fptufa26.ecomotion.verification.ekyc.EkycProvider;
import com.fptufa26.ecomotion.verification.ekyc.EkycService;
import com.fptufa26.ecomotion.verification.ekyc.EkycService.FaceMatchResult;
import com.fptufa26.ecomotion.verification.ekyc.EkycService.OcrResult;
import com.fptufa26.ecomotion.verification.ekyc.FaceMatchRule;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycProviderUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FptAiEkycService implements EkycProvider {

    private static final String NAME = "fptai";

    private final RestClient restClient;
    private final String apiKey;
    private final String idrUrl;
    private final String faceUrl;
    private final double minSimilarity;

    public FptAiEkycService(
            @Value("${app.ekyc.fptai.api-key:}") String apiKey,
            @Value("${app.ekyc.fptai.idr-url:https://api.fpt.ai/vision/idr/vnm}") String idrUrl,
            @Value("${app.ekyc.fptai.face-url:https://api.fpt.ai/dmp/checkface/v1}") String faceUrl,
            @Value("${app.ekyc.fptai.min-similarity:70}") double minSimilarity,
            @Value("${app.ekyc.fptai.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.ekyc.fptai.read-timeout:15s}") Duration readTimeout) {
        this.restClient = EkycHttpClient.create(connectTimeout, readTimeout);
        this.apiKey = apiKey;
        this.idrUrl = idrUrl;
        this.faceUrl = faceUrl;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public OcrResult ocrIdCard(byte[] image, String filename) {
        requireApiKey();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", resource(image, filename));

        Map<String, Object> response = post(idrUrl, body);
        requireSuccess(response, "CCCD OCR");

        Map<String, Object> first = firstObject(response.get("data"));
        if (first == null) {
            throw new EkycFailedException("CCCD could not be read");
        }
        String address = firstNonBlank(str(first.get("address")), str(first.get("home")));
        return new OcrResult(
                str(first.get("name")),
                str(first.get("id")),
                str(first.get("dob")),
                str(first.get("sex")),
                address);
    }

    @Override
    public FaceMatchResult faceMatch(byte[] idPhoto, String idFilename, byte[] portrait, String portraitFilename) {
        requireApiKey();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file[]", resource(idPhoto, idFilename));
        body.add("file[]", resource(portrait, portraitFilename));

        Map<String, Object> response = post(faceUrl, body);
        requireSuccess(response, "face match");

        Map<String, Object> payload = firstObject(response.get("data"));
        if (payload == null) {
            payload = Map.of();
        }
        return FaceMatchRule.decide(
                FaceMatchRule.toBoolean(payload.get("isMatch")),
                FaceMatchRule.toDouble(payload.get("similarity")),
                minSimilarity);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, MultiValueMap<String, Object> body) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .header("api-key", apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new EkycProviderUnavailableException("Empty response from the eKYC provider");
            }
            return response;
        } catch (EkycFailedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("FPT.AI eKYC call to {} failed", url, ex);
            throw new EkycProviderUnavailableException("eKYC provider is unavailable", ex);
        }
    }

    private void requireSuccess(Map<String, Object> response, String step) {
        Object errorCode = response.get("errorCode");
        boolean ok = errorCode == null || (errorCode instanceof Number number && number.intValue() == 0);
        if (!ok) {
            log.warn("FPT.AI {} failed: {}", step, response.get("errorMessage"));
            throw new EkycFailedException("eKYC " + step + " failed");
        }
    }

    private void requireApiKey() {
        if (isBlank(apiKey)) {
            throw new EkycFailedException("eKYC provider is not configured (missing FPT_AI_API_KEY)");
        }
    }

    private ByteArrayResource resource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename == null || filename.isBlank() ? "image.jpg" : filename;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstObject(Object value) {
        if (value instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
