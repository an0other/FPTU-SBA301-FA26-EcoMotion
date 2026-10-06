package com.fptufa26.ecomotion.auth.ekyc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default eKYC provider for local development: performs no network calls and always succeeds,
 * returning deterministic sample data so the whole activation flow can be exercised end to end.
 * Set {@code app.ekyc.mock.force-fail=true} to simulate a rejected identity.
 */
@Service
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "mock", matchIfMissing = true)
public class MockEkycService implements EkycService {

    private final boolean forceFail;

    public MockEkycService(@org.springframework.beans.factory.annotation.Value("${app.ekyc.mock.force-fail:false}")
                           boolean forceFail) {
        this.forceFail = forceFail;
    }

    @Override
    public OcrResult ocrIdCard(byte[] image, String filename) {
        return new OcrResult(
                "NGUYỄN VĂN A",
                "079123456789",
                "1990-01-01",
                "Nam",
                "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh");
    }

    @Override
    public FaceMatchResult faceMatch(byte[] idPhoto, String idFilename, byte[] portrait, String portraitFilename) {
        return forceFail ? new FaceMatchResult(false, 12.5) : new FaceMatchResult(true, 98.7);
    }
}
