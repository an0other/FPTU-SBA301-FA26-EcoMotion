package com.fptufa26.ecomotion.booking.service.map;

import com.fptufa26.ecomotion.booking.service.MapGeocodingService;
import com.fptufa26.ecomotion.booking.service.MapGeocodingService.GeoPoint;
import com.fptufa26.ecomotion.booking.service.implement.MapGeocodingServiceImpl;
import com.fptufa26.ecomotion.booking.service.map.implement.NominatimMapGeocodingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapGeocodingStrategyUnitTest {

    @Mock
    private MapGeocodingProvider mockProvider;

    @Test
    @DisplayName("MapGeocodingProviderResolver successfully resolves default NOMINATIM provider")
    void resolver_resolvesDefaultProvider() {
        when(mockProvider.getProviderName()).thenReturn("NOMINATIM");
        MapGeocodingProviderResolver resolver = new MapGeocodingProviderResolver(List.of(mockProvider), "NOMINATIM");

        MapGeocodingProvider resolved = resolver.resolveDefaultProvider();
        assertThat(resolved).isNotNull();
        assertThat(resolved.getProviderName()).isEqualTo("NOMINATIM");
    }

    @Test
    @DisplayName("MapGeocodingProviderResolver returns empty for unknown provider")
    void resolver_returnsEmptyForUnknownProvider() {
        when(mockProvider.getProviderName()).thenReturn("NOMINATIM");
        MapGeocodingProviderResolver resolver = new MapGeocodingProviderResolver(List.of(mockProvider), "NOMINATIM");

        Optional<MapGeocodingProvider> opt = resolver.resolveProvider("GOOGLE_MAPS");
        assertThat(opt).isEmpty();
    }

    @Test
    @DisplayName("MapGeocodingProviderResolver throws when default provider does not exist")
    void resolver_throwsWhenDefaultNotFound() {
        MapGeocodingProviderResolver resolver = new MapGeocodingProviderResolver(List.of(), "NOMINATIM");

        assertThatThrownBy(resolver::resolveDefaultProvider)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOMINATIM");
    }

    @Test
    @DisplayName("NominatimMapGeocodingProvider returns empty Optional for null or blank address without network call")
    void nominatimProvider_blankAddress_returnsEmpty() {
        NominatimMapGeocodingProvider provider = new NominatimMapGeocodingProvider(
                "https://nominatim.openstreetmap.org/search",
                "EcoMotion-Test",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                1,
                100,
                null,
                null
        );

        assertThat(provider.geocode(null)).isEmpty();
        assertThat(provider.geocode("   ")).isEmpty();
    }

    @Test
    @DisplayName("MapGeocodingServiceImpl caches resolved address and does not invoke provider repeatedly")
    void mapGeocodingService_cachesResult() {
        when(mockProvider.getProviderName()).thenReturn("NOMINATIM");
        when(mockProvider.geocode("123 Le Loi, Quan 1")).thenReturn(Optional.of(new GeoPoint(10.7769, 106.7009, "123 Le Loi, Quan 1")));

        MapGeocodingProviderResolver resolver = new MapGeocodingProviderResolver(List.of(mockProvider), "NOMINATIM");
        MapGeocodingService service = new MapGeocodingServiceImpl(resolver);

        Optional<GeoPoint> first = service.geocodeAddress("123 Le Loi, Quan 1");
        Optional<GeoPoint> second = service.geocodeAddress("123 Le Loi, Quan 1");

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().latitude()).isEqualTo(10.7769);
        assertThat(second.get().longitude()).isEqualTo(106.7009);

        verify(mockProvider, times(1)).geocode("123 Le Loi, Quan 1");
    }

    @Test
    @DisplayName("MapGeocodingServiceImpl handles blank address without invoking provider")
    void mapGeocodingService_blankAddress_noInvocation() {
        when(mockProvider.getProviderName()).thenReturn("NOMINATIM");
        MapGeocodingProviderResolver resolver = new MapGeocodingProviderResolver(List.of(mockProvider), "NOMINATIM");
        MapGeocodingService service = new MapGeocodingServiceImpl(resolver);

        Optional<GeoPoint> result = service.geocodeAddress("  ");
        assertThat(result).isEmpty();

        verify(mockProvider, never()).geocode(anyString());
    }
}
