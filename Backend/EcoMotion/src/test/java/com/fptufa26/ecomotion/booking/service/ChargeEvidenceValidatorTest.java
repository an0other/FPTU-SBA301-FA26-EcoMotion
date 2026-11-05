package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ChargeEvidenceValidatorTest {
    private final ChargeEvidenceValidator validator = new ChargeEvidenceValidator(2, 16);
    private MockMultipartFile file(String type, byte[] bytes) { return new MockMultipartFile("files", "x", type, bytes); }
    private final byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private final byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
    private final byte[] webp = {'R','I','F','F',0,0,0,0,'W','E','B','P'};

    @Test void acceptsMatchingSignaturesAndMultipleFiles() {
        assertThatCode(() -> validator.validate(List.of(file("image/png", png), file("image/jpeg", jpeg)), 0)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(List.of(file("image/webp", webp)), 1)).doesNotThrowAnyException();
    }
    @Test void rejectsSpoofingEmptyOversizeAndExcessCount() {
        assertThatThrownBy(() -> validator.validate(List.of(file("image/jpeg", png)), 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validate(List.of(file("image/png", new byte[0])), 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validate(List.of(file("image/png", new byte[17])), 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> validator.validate(List.of(file("image/png", png), file("image/png", png)), 1)).isInstanceOf(BadRequestException.class);
    }
}
