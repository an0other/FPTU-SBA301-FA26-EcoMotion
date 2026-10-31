package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.service.MapGeocodingService;
import com.fptufa26.ecomotion.booking.service.map.MapGeocodingProvider;
import com.fptufa26.ecomotion.booking.service.map.MapGeocodingProviderResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MapGeocodingServiceImpl implements MapGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(MapGeocodingServiceImpl.class);
    private static final int MAX_CACHE_SIZE = 500;

    private final MapGeocodingProviderResolver providerResolver;
    private final MapGeocodingProvider fallbackProvider;
    private final Map<String, Optional<GeoPoint>> cache = new ConcurrentHashMap<>();

    @Autowired
    public MapGeocodingServiceImpl(MapGeocodingProviderResolver providerResolver) {
        this.providerResolver = providerResolver;
        this.fallbackProvider = null;
    }

    public MapGeocodingServiceImpl(MapGeocodingProvider fallbackProvider) {
        this.providerResolver = null;
        this.fallbackProvider = fallbackProvider;
    }

    @Override
    public Optional<GeoPoint> geocodeAddress(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        String normalizedAddress = address.trim().toLowerCase();
        Optional<GeoPoint> cached = cache.get(normalizedAddress);
        if (cached != null) {
            return cached;
        }

        Optional<GeoPoint> resolved = resolveCoordinates(address.trim());
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.put(normalizedAddress, resolved);
        }
        return resolved;
    }

    private Optional<GeoPoint> resolveCoordinates(String query) {
        try {
            MapGeocodingProvider provider = providerResolver != null
                    ? providerResolver.resolveDefaultProvider()
                    : fallbackProvider;

            if (provider == null) {
                log.warn("No MapGeocodingProvider configured for geocoding address '{}'", query);
                return Optional.empty();
            }

            return provider.geocode(query);
        } catch (Exception ex) {
            log.warn("Geocoding failed for address '{}': {}", query, ex.getMessage());
            return Optional.empty();
        }
    }
}
