package com.fptufa26.ecomotion.verification.ekyc.implement;

import com.fptufa26.ecomotion.verification.ekyc.EkycProvider;
import com.fptufa26.ecomotion.verification.ekyc.EkycService;
import com.fptufa26.ecomotion.verification.ekyc.EkycService.FaceMatchResult;
import com.fptufa26.ecomotion.verification.ekyc.EkycService.OcrResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Profile({"dev", "test"})
@Service
public class MockEkycService implements EkycProvider {

    private static final String NAME = "mock";

    private final boolean forceFail;

    public MockEkycService(@Value("${app.ekyc.mock.force-fail:false}") boolean forceFail) {
        this.forceFail = forceFail;
        log.warn("MockEkycService is active: eKYC results are simulated and identities are not "
                + "verified for real. This provider must never run in production.");
    }

    @Override
    public String name() {
        return NAME;
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
