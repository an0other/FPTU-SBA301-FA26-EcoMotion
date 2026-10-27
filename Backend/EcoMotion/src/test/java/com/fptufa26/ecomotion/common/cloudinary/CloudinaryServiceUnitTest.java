package com.fptufa26.ecomotion.common.cloudinary;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceUnitTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private CloudinaryConfig cloudinaryConfig;

    private CloudinaryServiceImpl cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryServiceImpl(cloudinary, cloudinaryConfig, "ecomotion");
    }

    @Test
    @DisplayName("Throws CloudinaryInfrastructureException when credentials are mock or unconfigured, never returns fake URL")
    void uploadTripEvidence_unconfigured_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "fake-image-bytes".getBytes()
        );

        when(cloudinaryConfig.isMockOrDemo()).thenReturn(true);

        assertThatThrownBy(() -> cloudinaryService.uploadTripEvidence(file, 101L))
                .isInstanceOf(CloudinaryInfrastructureException.class)
                .hasMessageContaining("chưa được cấu hình tài khoản hoặc đang dùng thông tin demo/mock");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException for empty file")
    void uploadTripEvidence_emptyFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> cloudinaryService.uploadTripEvidence(file, 101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không được để trống");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException for executable file")
    void uploadTripEvidence_executableFile_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.exe", "image/jpeg", "binary".getBytes()
        );

        assertThatThrownBy(() -> cloudinaryService.uploadTripEvidence(file, 101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("thực thi");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException for invalid content type")
    void uploadTripEvidence_invalidContentType_throwsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "pdf-data".getBytes()
        );

        assertThatThrownBy(() -> cloudinaryService.uploadTripEvidence(file, 101L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Định dạng file không hợp lệ");
    }

    @Test
    @DisplayName("deleteImage safely handles blank publicId and unconfigured credentials without throwing")
    void deleteImage_safeHandling() {
        cloudinaryService.deleteImage(null);
        cloudinaryService.deleteImage("   ");

        when(cloudinaryConfig.isMockOrDemo()).thenReturn(true);
        cloudinaryService.deleteImage("ecomotion/sample-id");
    }
}
