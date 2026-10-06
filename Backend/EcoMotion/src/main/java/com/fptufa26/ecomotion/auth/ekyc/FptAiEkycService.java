package com.fptufa26.ecomotion.auth.ekyc;

import com.fptufa26.ecomotion.auth.exception.EkycFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Real eKYC provider backed by the FPT.AI sandbox.
 *
 * <p>Two calls are made:</p>
 * <ul>
 *     <li><b>CCCD OCR</b> — {@code POST {idr-url}} with the front image as {@code image}; the
 *     {@code api-key} header carries the token.</li>
 *     <li><b>Face match</b> — {@code POST {face-url}} with both images as {@code file[]}.</li>
 * </ul>
 *
 * <p>The URLs are configurable so the exact sandbox contract can be adjusted without a code
 * change. Any transport error or non-zero {@code errorCode} is treated as a failed verification
 * (the account simply stays PENDING).</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "fptai")
public class FptAiEkycService implements EkycService {

    private final RestClient restClient;
    private final String apiKey;
    private final String idrUrl;
    private final String faceUrl;
    private final double minSimilarity;

    public FptAiEkycService(
            @Value("${app.ekyc.fptai.api-key:}") String apiKey,
            @Value("${app.ekyc.fptai.idr-url:https://api.fpt.ai/vision/idr/vnm}") String idrUrl,
            @Value("${app.ekyc.fptai.face-url:https://api.fpt.ai/dmp/checkface/v1}") String faceUrl,
            @Value("${app.ekyc.fptai.min-similarity:70}") double minSimilarity) {
        this.restClient = RestClient.builder().build();
        this.apiKey = apiKey;
        this.idrUrl = idrUrl;
        this.faceUrl = faceUrl;
        this.minSimilarity = minSimilarity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OcrResult ocrIdCard(byte[] image, String filename) {
        requireApiKey();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", resource(image, filename));

        Map<String, Object> response = post(idrUrl, body);
        requireSuccess(response, "CCCD OCR");

        Object data = response.get("data");
        Map<String, Object> first = null;
        if (data instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            first = (Map<String, Object>) map;
        } else if (data instanceof Map<?, ?> map) {
            first = (Map<String, Object>) map;
        }
        if (first == null) {
            throw new EkycFailedException("CCCD could not be read");
        }
        // FPT.AI IDR vnm field names: name, id, dob, sex, address/home, nationality, doe, ...
        String address = str(first.get("address"));
        if (address == null || address.isBlank()) {
            address = str(first.get("home"));
        }
        return new OcrResult(
                str(first.get("name")),
                str(first.get("id")),
                str(first.get("dob")),
                str(first.get("sex")),
                address);
    }

    @Override
    @SuppressWarnings("unchecked")
    public FaceMatchResult faceMatch(byte[] idPhoto, String idFilename, byte[] portrait, String portraitFilename) {
        requireApiKey();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file[]", resource(idPhoto, idFilename));
        body.add("file[]", resource(portrait, portraitFilename));

        Map<String, Object> response = post(faceUrl, body);
        requireSuccess(response, "face match");

        Object data = response.get("data");
        Map<String, Object> payload = data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        boolean isMatch = Boolean.TRUE.equals(payload.get("isMatch"));
        double similarity = payload.get("similarity") instanceof Number number ? number.doubleValue() : 0d;
        return new FaceMatchResult(isMatch || similarity >= minSimilarity, similarity);
    }

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
                throw new EkycFailedException("Empty response from the eKYC provider");
            }
            return response;
        } catch (EkycFailedException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("FPT.AI eKYC call to {} failed", url, ex);
            throw new EkycFailedException("eKYC provider is unavailable");
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
        if (apiKey == null || apiKey.isBlank()) {
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

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
