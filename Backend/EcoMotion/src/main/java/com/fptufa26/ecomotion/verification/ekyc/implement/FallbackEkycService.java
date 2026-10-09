package com.fptufa26.ecomotion.verification.ekyc.implement;

import com.fptufa26.ecomotion.verification.ekyc.EkycProvider;
import com.fptufa26.ecomotion.verification.ekyc.EkycService;
import com.fptufa26.ecomotion.verification.ekyc.EkycService.FaceMatchResult;
import com.fptufa26.ecomotion.verification.ekyc.EkycService.OcrResult;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycProviderUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Primary
@Service
public class FallbackEkycService implements EkycService {

    private final Map<String, EkycService> providers;
    private final List<String> order;

    public FallbackEkycService(
            List<EkycProvider> providers,
            @Value("${app.ekyc.provider:mock}") String primary,
            @Value("${app.ekyc.fallback-provider:}") String fallback) {
        this.providers = new LinkedHashMap<>();
        for (EkycProvider provider : providers) {
            this.providers.put(provider.name(), provider);
        }
        this.order = resolveOrder(primary, fallback);
        log.info("eKYC provider chain: {}", this.order);
    }

    @Override
    public OcrResult ocrIdCard(byte[] image, String filename) {
        return delegate("CCCD OCR", provider -> provider.ocrIdCard(image, filename));
    }

    @Override
    public FaceMatchResult faceMatch(byte[] image, String imageName, byte[] portrait, String portraitName) {
        return delegate("face match", provider -> provider.faceMatch(image, imageName, portrait, portraitName));
    }

    private <T> T delegate(String step, Function<EkycService, T> call) {
        EkycProviderUnavailableException last = null;
        for (String name : order) {
            try {
                return call.apply(providers.get(name));
            } catch (EkycProviderUnavailableException ex) {
                last = ex;
                log.warn("eKYC provider '{}' is unavailable during {}", name, step, ex);
            }
        }
        if (last != null) {
            throw last;
        }
        throw new EkycFailedException("No eKYC provider is available");
    }

    private List<String> resolveOrder(String primary, String fallback) {
        List<String> resolved = new ArrayList<>();
        addProvider(resolved, primary);
        addProvider(resolved, fallback);
        if (resolved.isEmpty()) {
            throw new IllegalStateException(
                    "No usable eKYC provider for app.ekyc.provider=" + primary
                            + "; providers available in this environment: " + providers.keySet());
        }
        return List.copyOf(resolved);
    }

    private void addProvider(List<String> target, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!providers.containsKey(key)) {
            log.warn("eKYC provider '{}' is not available in this environment; known providers are {}",
                    name, providers.keySet());
            return;
        }
        if (!target.contains(key)) {
            target.add(key);
        }
    }
}
