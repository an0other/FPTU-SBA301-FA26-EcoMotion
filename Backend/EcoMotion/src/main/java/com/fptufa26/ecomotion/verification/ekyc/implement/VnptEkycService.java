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
public class VnptEkycService implements EkycProvider {

    private static final String NAME = "vnpt";
    private static final String DEFAULT_IMAGE_NAME = "image.jpg";

    private final RestClient restClient;
    private final String ocrUrl;
    private final String faceUrl;
    private final String documentType;
    private final String accessToken;
    private final String tokenId;
    private final String tokenKey;
    private final double minSimilarity;

    public VnptEkycService(
            @Value("${app.ekyc.vnpt.base-url:https://api.idg.vnpt.vn}") String baseUrl,
            @Value("${app.ekyc.vnpt.ocr-path:/ekyc/v1/ocr}") String ocrPath,
            @Value("${app.ekyc.vnpt.face-path:/ekyc/v1/face}") String facePath,
            @Value("${app.ekyc.vnpt.document-type:9}") String documentType,
            @Value("${app.ekyc.vnpt.access-token:}") String accessToken,
            @Value("${app.ekyc.vnpt.token-id:}") String tokenId,
            @Value("${app.ekyc.vnpt.token-key:}") String tokenKey,
            @Value("${app.ekyc.vnpt.min-similarity:70}") double minSimilarity,
            @Value("${app.ekyc.vnpt.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.ekyc.vnpt.read-timeout:15s}") Duration readTimeout) {
        this.restClient = EkycHttpClient.create(connectTimeout, readTimeout);
        this.ocrUrl = baseUrl + ocrPath;
        this.faceUrl = baseUrl + facePath;
        this.documentType = documentType;
        this.accessToken = accessToken;
        this.tokenId = tokenId;
        this.tokenKey = tokenKey;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public OcrResult ocrIdCard(byte[] image, String filename) {
        requireCredentials();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource(image, filename));
        body.add("type", documentType);

        Map<String, Object> response = post(ocrUrl, body);
        requireSuccess(response, "CCCD OCR");

        Map<String, Object> data = firstObject(response.get("data"));
        if (data == null) {
            throw new EkycFailedException("CCCD could not be read");
        }
        String address = firstNonBlank(str(data.get("address")), str(data.get("home")));
        return new OcrResult(
                firstNonBlank(str(data.get("name")), str(data.get("full_name"))),
                firstNonBlank(str(data.get("id")), str(data.get("id_number"))),
                firstNonBlank(str(data.get("birth_day")), str(data.get("dob"))),
                firstNonBlank(str(data.get("sex")), str(data.get("gender"))),
                address);
    }

    @Override
    public FaceMatchResult faceMatch(byte[] idPhoto, String idFilename, byte[] portrait, String portraitFilename) {
        requireCredentials();

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
                FaceMatchRule.toBoolean(firstNonNull(payload.get("isMatch"), payload.get("is_match"))),
                FaceMatchRule.toDouble(firstNonNull(payload.get("similarity"), payload.get("similar"))),
                minSimilarity);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, MultiValueMap<String, Object> body) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Token-id", tokenId)
                    .header("Token-key", tokenKey)
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
            log.error("VNPT eKYC call to {} failed", url, ex);
            throw new EkycProviderUnavailableException("eKYC provider is unavailable", ex);
        }
    }

    private void requireSuccess(Map<String, Object> response, String step) {
        Object errorCode = firstNonNull(response.get("errorCode"), response.get("error_code"));
        boolean ok = errorCode == null
                || (errorCode instanceof Number number && number.intValue() == 0)
                || "0".equals(String.valueOf(errorCode))
                || "ok".equalsIgnoreCase(String.valueOf(errorCode));
        if (!ok) {
            log.warn("VNPT {} failed: {}", step, response.get("errorMessage"));
            throw new EkycFailedException("eKYC " + step + " failed");
        }
    }

    private void requireCredentials() {
        if (isBlank(accessToken) && isBlank(tokenKey)) {
            throw new EkycFailedException("eKYC provider is not configured (missing VNPT_EKYC credentials)");
        }
    }

    private ByteArrayResource resource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return isBlank(filename) ? DEFAULT_IMAGE_NAME : filename;
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

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
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
