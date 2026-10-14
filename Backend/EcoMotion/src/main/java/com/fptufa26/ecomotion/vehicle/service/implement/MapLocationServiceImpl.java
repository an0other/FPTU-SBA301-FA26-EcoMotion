package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.vehicle.dto.response.AddressSearchResult;
import com.fptufa26.ecomotion.vehicle.exception.MapLocationUnavailableException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.fptufa26.ecomotion.vehicle.service.MapLocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MapLocationServiceImpl implements MapLocationService {

    private static final long MIN_REQUEST_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String reverseUrl;
    private final String searchUrl;
    private final String userAgent;
    private final Duration requestTimeout;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, List<AddressSearchResult>> searchCache = new ConcurrentHashMap<>();
    private long lastRequestNanos;

    public MapLocationServiceImpl(ObjectMapper objectMapper,
                                  @Value("${app.map.reverse-url}") String reverseUrl,
                                  @Value("${app.map.search-url}") String searchUrl,
                                  @Value("${app.map.user-agent}") String userAgent,
                                  @Value("${app.map.connect-timeout}") Duration connectTimeout,
                                  @Value("${app.map.request-timeout}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.reverseUrl = reverseUrl;
        this.searchUrl = searchUrl;
        this.userAgent = userAgent;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public synchronized String reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Invalid latitude or longitude");
        }

        String key = latitude.setScale(5, RoundingMode.HALF_UP) + ","
                + longitude.setScale(5, RoundingMode.HALF_UP);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        if (!awaitRequestSlot()) return null;

        String url = reverseUrl + "?format=jsonv2&zoom=18&addressdetails=0&accept-language=vi&lat="
                + latitude.toPlainString() + "&lon=" + longitude.toPlainString();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(requestTimeout)
                .GET()
                .build();
        try {
            lastRequestNanos = System.nanoTime();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode name = objectMapper.readTree(response.body()).path("display_name");
            if (!name.isTextual() || name.asText().isBlank()) {
                return null;
            }
            cache.put(key, name.asText());
            return name.asText();
        } catch (IOException | RuntimeException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public synchronized List<AddressSearchResult> searchAddress(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 3 || normalized.length() > 200) {
            throw new IllegalArgumentException("Address query must be between 3 and 200 characters");
        }
        String key = normalized.toLowerCase(java.util.Locale.ROOT);
        if (searchCache.containsKey(key)) return searchCache.get(key);
        if (!awaitRequestSlot()) throw new MapLocationUnavailableException("Address lookup interrupted");

        String url = searchUrl + "?format=jsonv2&addressdetails=0&accept-language=vi"
                + "&countrycodes=vn&limit=5&q=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(requestTimeout)
                .GET()
                .build();
        try {
            lastRequestNanos = System.nanoTime();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new MapLocationUnavailableException("Address lookup returned HTTP " + response.statusCode());
            }
            JsonNode results = objectMapper.readTree(response.body());
            if (!results.isArray()) throw new MapLocationUnavailableException("Invalid address lookup response");
            List<AddressSearchResult> matches = new ArrayList<>();
            for (JsonNode result : results) {
                String address = result.path("display_name").asText();
                String latitude = result.path("lat").asText();
                String longitude = result.path("lon").asText();
                if (!address.isBlank() && !latitude.isBlank() && !longitude.isBlank()) {
                    matches.add(new AddressSearchResult(address,
                            new BigDecimal(latitude), new BigDecimal(longitude)));
                }
            }
            List<AddressSearchResult> found = List.copyOf(matches);
            searchCache.put(key, found);
            return found;
        } catch (MapLocationUnavailableException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new MapLocationUnavailableException("Address lookup unavailable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MapLocationUnavailableException("Address lookup interrupted", ex);
        }
    }

    private boolean awaitRequestSlot() {
        long remainingNanos = MIN_REQUEST_INTERVAL_NANOS - (System.nanoTime() - lastRequestNanos);
        if (lastRequestNanos != 0 && remainingNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(remainingNanos));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
