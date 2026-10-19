package com.fptufa26.ecomotion.booking.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptufa26.ecomotion.booking.service.MapGeocodingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MapGeocodingServiceImpl implements MapGeocodingService {

    private static final Logger log = LoggerFactory.getLogger(MapGeocodingServiceImpl.class);
    private static final int MAX_CACHE_SIZE = 500;

    private final String searchUrl;
    private final String userAgent;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Optional<GeoPoint>> cache = new ConcurrentHashMap<>();

    @Autowired
    public MapGeocodingServiceImpl(
            @Value("${app.map.search-url:https://nominatim.openstreetmap.org/search}") String searchUrl,
            @Value("${app.map.user-agent:EcoMotion-localhost/1.0}") String userAgent,
            @Value("${app.map.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.map.request-timeout:8s}") Duration requestTimeout) {
        this(searchUrl, userAgent, connectTimeout, requestTimeout, new ObjectMapper());
    }

    public MapGeocodingServiceImpl(
            @Value("${app.map.search-url:https://nominatim.openstreetmap.org/search}") String searchUrl,
            @Value("${app.map.user-agent:EcoMotion-localhost/1.0}") String userAgent,
            @Value("${app.map.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.map.request-timeout:8s}") Duration requestTimeout,
            ObjectMapper objectMapper) {
        this.searchUrl = searchUrl;
        this.userAgent = userAgent;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(requestTimeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
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

        Optional<GeoPoint> resolved = fetchCoordinates(address.trim());
        if (cache.size() < MAX_CACHE_SIZE) {
            cache.put(normalizedAddress, resolved);
        }
        return resolved;
    }

    private Optional<GeoPoint> fetchCoordinates(String query) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(searchUrl)
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("limit", 1)
                    .build()
                    .encode()
                    .toUri();

            String responseBody = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(responseBody);
            if (root.isArray() && !root.isEmpty()) {
                JsonNode first = root.get(0);
                if (first.has("lat") && first.has("lon")) {
                    double lat = Double.parseDouble(first.get("lat").asText());
                    double lon = Double.parseDouble(first.get("lon").asText());
                    return Optional.of(new GeoPoint(lat, lon));
                }
            }
        } catch (Exception ex) {
            log.warn("Geocoding failed for address: '{}' - {}", query, ex.getMessage());
        }
        return Optional.empty();
    }
}
