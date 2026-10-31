package com.fptufa26.ecomotion.booking.service.map.implement;

import com.fptufa26.ecomotion.booking.service.MapGeocodingService.GeoPoint;
import com.fptufa26.ecomotion.booking.service.map.MapGeocodingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@Component
public class NominatimMapGeocodingProvider implements MapGeocodingProvider {

    private static final Logger log = LoggerFactory.getLogger(NominatimMapGeocodingProvider.class);
    private static final String PROVIDER_NAME = "NOMINATIM";

    private final String searchUrl;
    private final String userAgent;
    private final int maxRetries;
    private final long backoffMillis;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public NominatimMapGeocodingProvider(
            @Value("${app.map.nominatim.search-url:${app.map.search-url:https://nominatim.openstreetmap.org/search}}") String searchUrl,
            @Value("${app.map.nominatim.user-agent:${app.map.user-agent:EcoMotion-localhost/1.0}}") String userAgent,
            @Value("${app.map.connect-timeout:5s}") Duration connectTimeout,
            @Value("${app.map.request-timeout:8s}") Duration requestTimeout,
            @Value("${app.map.retry.max-attempts:2}") int maxRetries,
            @Value("${app.map.retry.backoff-ms:300}") long backoffMillis,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this(searchUrl, userAgent, connectTimeout, requestTimeout, maxRetries, backoffMillis, objectMapper, null);
    }

    public NominatimMapGeocodingProvider(
            String searchUrl,
            String userAgent,
            Duration connectTimeout,
            Duration requestTimeout,
            int maxRetries,
            long backoffMillis,
            ObjectMapper objectMapper,
            RestClient customRestClient) {
        this.searchUrl = searchUrl;
        this.userAgent = userAgent;
        this.maxRetries = Math.max(0, maxRetries);
        this.backoffMillis = Math.max(50, backoffMillis);
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        if (customRestClient != null) {
            this.restClient = customRestClient;
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(5));
            factory.setReadTimeout(requestTimeout != null ? requestTimeout : Duration.ofSeconds(8));
            this.restClient = RestClient.builder().requestFactory(factory).build();
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<GeoPoint> geocode(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        int attempts = 0;
        while (attempts <= maxRetries) {
            attempts++;
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
                        return Optional.of(new GeoPoint(lat, lon, query));
                    }
                }
                return Optional.empty();
            } catch (HttpClientErrorException ex) {
                log.warn("Nominatim client error ({}) for query '{}': {}", ex.getStatusCode(), query, ex.getMessage());
                return Optional.empty();
            } catch (ResourceAccessException | HttpServerErrorException ex) {
                log.warn("Nominatim transient network error (attempt {}/{}) for query '{}': {}",
                        attempts, maxRetries + 1, query, ex.getMessage());
                if (attempts > maxRetries) {
                    return Optional.empty();
                }
                try {
                    Thread.sleep(backoffMillis * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            } catch (Exception ex) {
                log.error("Unexpected error in Nominatim geocoding for query '{}': {}", query, ex.getMessage());
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
