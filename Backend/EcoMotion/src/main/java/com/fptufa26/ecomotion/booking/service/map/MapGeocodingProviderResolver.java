package com.fptufa26.ecomotion.booking.service.map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class MapGeocodingProviderResolver {

    private final Map<String, MapGeocodingProvider> providers;
    private final String defaultProviderName;

    public MapGeocodingProviderResolver(
            List<MapGeocodingProvider> providerList,
            @Value("${app.map.provider:NOMINATIM}") String defaultProviderName) {
        this.providers = providerList != null ? providerList.stream()
                .filter(p -> p != null && p.getProviderName() != null && !p.getProviderName().isBlank())
                .collect(Collectors.toMap(p -> p.getProviderName().trim().toUpperCase(), p -> p, (a, b) -> a))
                : Map.of();
        this.defaultProviderName = defaultProviderName != null ? defaultProviderName.trim().toUpperCase() : "NOMINATIM";
    }

    public MapGeocodingProvider resolveDefaultProvider() {
        return resolveProvider(defaultProviderName)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy MapGeocodingProvider cho: " + defaultProviderName));
    }

    public Optional<MapGeocodingProvider> resolveProvider(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(providerName.trim().toUpperCase()));
    }
}
