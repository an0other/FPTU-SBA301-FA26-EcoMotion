package com.fptufa26.ecomotion.verification.ekyc.implement;

import com.fptufa26.ecomotion.verification.ekyc.EkycProvider;
import com.fptufa26.ecomotion.verification.ekyc.EkycService;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycFailedException;
import com.fptufa26.ecomotion.verification.ekyc.exception.EkycProviderUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackEkycServiceTest {

    private final EkycProvider fptAi = provider("fptai");
    private final EkycProvider vnpt = provider("vnpt");
    private final EkycProvider mockProvider = provider("mock");

    private static EkycProvider provider(String name) {
        EkycProvider provider = mock(EkycProvider.class);
        when(provider.name()).thenReturn(name);
        return provider;
    }

    private FallbackEkycService service(String primary, String fallback) {
        return new FallbackEkycService(List.of(fptAi, vnpt, mockProvider), primary, fallback);
    }

    @Test
    void usesThePrimaryProviderWhenItSucceeds() {
        EkycService.OcrResult expected = new EkycService.OcrResult("A", "1", "1990-01-01", "Nam", "HCM");
        when(fptAi.ocrIdCard(any(), anyString())).thenReturn(expected);

        EkycService.OcrResult actual = service("fptai", "vnpt").ocrIdCard(new byte[]{1}, "cccd-front.jpg");

        assertThat(actual).isEqualTo(expected);
        verify(vnpt, never()).ocrIdCard(any(), anyString());
    }

    @Test
    void fallsBackWhenThePrimaryIsUnavailable() {
        when(fptAi.ocrIdCard(any(), anyString()))
                .thenThrow(new EkycProviderUnavailableException("fpt timeout"));
        EkycService.OcrResult expected = new EkycService.OcrResult("B", "2", "1991-02-02", "Nữ", "HN");
        when(vnpt.ocrIdCard(any(), anyString())).thenReturn(expected);

        EkycService.OcrResult actual = service("fptai", "vnpt").ocrIdCard(new byte[]{1}, "cccd-front.jpg");

        assertThat(actual).isEqualTo(expected);
        verify(fptAi).ocrIdCard(any(), anyString());
        verify(vnpt).ocrIdCard(any(), anyString());
    }

    @Test
    void doesNotFallBackOnAVerificationFailure() {
        when(fptAi.ocrIdCard(any(), anyString()))
                .thenThrow(new EkycFailedException("CCCD could not be read"));

        FallbackEkycService service = service("fptai", "vnpt");

        assertThatThrownBy(() -> service.ocrIdCard(new byte[]{1}, "cccd-front.jpg"))
                .isInstanceOf(EkycFailedException.class)
                .hasMessageContaining("CCCD could not be read");
        verify(vnpt, never()).ocrIdCard(any(), anyString());
    }

    @Test
    void propagatesTheLastFailureWhenEveryProviderIsUnavailable() {
        when(fptAi.faceMatch(any(), anyString(), any(), anyString()))
                .thenThrow(new EkycProviderUnavailableException("fpt down"));
        when(vnpt.faceMatch(any(), anyString(), any(), anyString()))
                .thenThrow(new EkycProviderUnavailableException("vnpt down"));

        FallbackEkycService service = service("fptai", "vnpt");

        assertThatThrownBy(() -> service.faceMatch(new byte[]{1}, "cccd-front.jpg", new byte[]{2}, "portrait.jpg"))
                .isInstanceOf(EkycProviderUnavailableException.class)
                .hasMessageContaining("vnpt down");
    }

    @Test
    void aSingleProviderIsUsedWithoutFallback() {
        EkycService.FaceMatchResult expected = new EkycService.FaceMatchResult(true, 98.7);
        when(mockProvider.faceMatch(any(), anyString(), any(), anyString())).thenReturn(expected);

        EkycService.FaceMatchResult actual =
                service("mock", "").faceMatch(new byte[]{1}, "a.jpg", new byte[]{2}, "b.jpg");

        assertThat(actual).isEqualTo(expected);
        verify(fptAi, never()).faceMatch(any(), anyString(), any(), anyString());
        verify(vnpt, never()).faceMatch(any(), anyString(), any(), anyString());
    }

    @Test
    void failsFastWhenTheConfiguredProviderIsNotAvailable() {
        assertThatThrownBy(() -> new FallbackEkycService(List.of(fptAi, vnpt), "mock", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ekyc.provider=mock");
    }

    @Test
    void rejectsAnUnknownProviderName() {
        assertThatThrownBy(() -> service("nope", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ekyc.provider=nope");
    }
}
