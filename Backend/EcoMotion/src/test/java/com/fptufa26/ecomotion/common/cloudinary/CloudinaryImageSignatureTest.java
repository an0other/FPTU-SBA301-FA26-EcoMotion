package com.fptufa26.ecomotion.common.cloudinary;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Xác thực chữ ký file ở tầng upload. Hợp lệ được xác nhận bằng việc vượt qua validation và dừng ở bước
 * cấu hình Cloudinary (không gọi mạng); không hợp lệ bị từ chối ngay bằng IllegalArgumentException.
 */
class CloudinaryImageSignatureTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 0x4A, 0x46, 0x49, 0x46, 0, 1};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50, 0x56, 0x50};
    private static final byte[] TEXT = "hello, this is plain text and not an image".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final CloudinaryConfig config = mock(CloudinaryConfig.class);
    private final CloudinaryServiceImpl service;

    CloudinaryImageSignatureTest() {
        // Cloudinary null: nếu validation sai, test sẽ lỗi NullPointerException thay vì pass giả.
        when(config.isMockOrDemo()).thenReturn(true);
        service = new CloudinaryServiceImpl(null, config, "ecomotion");
    }

    private static byte[] withPadding(byte[] head, int total) {
        byte[] out = new byte[total];
        System.arraycopy(head, 0, out, 0, head.length);
        return out;
    }

    private void assertPassesValidation(MockMultipartFile file) {
        // Hợp lệ: vượt qua validation và dừng ở bước cấu hình Cloudinary (trước khi gọi mạng).
        assertThatThrownBy(() -> service.uploadTripEvidence(file, 1L))
                .isInstanceOf(CloudinaryInfrastructureException.class);
    }

    @Test
    void validJpegPassesSignatureCheck() {
        assertPassesValidation(new MockMultipartFile("file", "a.jpg", "image/jpeg", withPadding(JPEG, 64)));
    }

    @Test
    void validPngPassesSignatureCheck() {
        assertPassesValidation(new MockMultipartFile("file", "a.png", "image/png", withPadding(PNG, 64)));
    }

    @Test
    void validWebpPassesSignatureCheck() {
        assertPassesValidation(new MockMultipartFile("file", "a.webp", "image/webp", withPadding(WEBP, 64)));
    }

    @Test
    void jpgAliasWithJpegSignatureIsAccepted() {
        assertPassesValidation(new MockMultipartFile("file", "a.jpg", "image/jpg", withPadding(JPEG, 64)));
    }

    @Test
    void fakeMimeTypeWithTextContentIsRejected() {
        MockMultipartFile fake = new MockMultipartFile("file", "evil.jpg", "image/jpeg", TEXT);
        assertThatThrownBy(() -> service.uploadTripEvidence(fake, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không khớp");
    }

    @Test
    void invalidSignatureForDeclaredTypeIsRejected() {
        // Khai báo PNG nhưng nội dung là JPEG: chữ ký không khớp.
        MockMultipartFile mismatch = new MockMultipartFile("file", "a.png", "image/png", withPadding(JPEG, 64));
        assertThatThrownBy(() -> service.uploadTripEvidence(mismatch, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không khớp");
    }

    @Test
    void fileAboveTenMegabytesIsRejectedBeforeContentCheck() {
        byte[] tooBig = withPadding(JPEG, 10 * 1024 * 1024 + 1);
        MockMultipartFile big = new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig);
        assertThatThrownBy(() -> service.uploadTripEvidence(big, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> service.uploadTripEvidence(empty, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectorRecognisesOnlyTheThreeSupportedSignatures() {
        assertThat(ImageSignature.detect(JPEG)).isEqualTo("image/jpeg");
        assertThat(ImageSignature.detect(PNG)).isEqualTo("image/png");
        assertThat(ImageSignature.detect(WEBP)).isEqualTo("image/webp");
        assertThat(ImageSignature.detect(TEXT)).isNull();
        assertThat(ImageSignature.detect(new byte[]{0x47, 0x49, 0x46, 0x38, 0x39, 0x61})).isNull(); // GIF
        assertThat(ImageSignature.detect(null)).isNull();
    }
}
