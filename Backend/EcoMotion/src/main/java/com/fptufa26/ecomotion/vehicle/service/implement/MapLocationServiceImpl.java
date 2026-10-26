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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MapLocationServiceImpl implements MapLocationService {

    private static final long MIN_REQUEST_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String reverseUrl;
    private final String searchUrl;
    private final String fallbackReverseUrl;
    private final String fallbackSearchUrl;
    private final String userAgent;
    private final Duration requestTimeout;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, List<AddressSearchResult>> searchCache = new ConcurrentHashMap<>();
    private long lastRequestNanos;

    public MapLocationServiceImpl(ObjectMapper objectMapper,
                                  @Value("${app.map.reverse-url:https://nominatim.openstreetmap.org/reverse}") String reverseUrl,
                                  @Value("${app.map.search-url:https://nominatim.openstreetmap.org/search}") String searchUrl,
                                  @Value("${app.map.fallback-reverse-url:https://photon.komoot.io/reverse}") String fallbackReverseUrl,
                                  @Value("${app.map.fallback-search-url:https://photon.komoot.io/api/}") String fallbackSearchUrl,
                                  @Value("${app.map.user-agent:EcoMotion-localhost/1.0}") String userAgent,
                                  @Value("${app.map.connect-timeout:5s}") Duration connectTimeout,
                                  @Value("${app.map.request-timeout:8s}") Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.reverseUrl = reverseUrl;
        this.searchUrl = searchUrl;
        this.fallbackReverseUrl = fallbackReverseUrl;
        this.fallbackSearchUrl = fallbackSearchUrl;
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

        String address = reverseWithNominatim(latitude, longitude);
        if (address == null) address = reverseWithPhoton(latitude, longitude);
        if (address != null) cache.put(key, address);
        return address;
    }

    @Override
    public synchronized List<AddressSearchResult> searchAddress(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 3 || normalized.length() > 200) {
            throw new IllegalArgumentException("Address query must be between 3 and 200 characters");
        }
        String key = normalized.toLowerCase(Locale.ROOT);
        if (searchCache.containsKey(key)) return searchCache.get(key);
        if (!awaitRequestSlot()) throw new MapLocationUnavailableException("Address lookup interrupted");

        try {
            List<AddressSearchResult> found;
            try {
                found = searchWithNominatim(normalized);
            } catch (IOException | RuntimeException ex) {
                found = null;
            }
            if (found == null) found = searchWithPhoton(normalized);
            searchCache.put(key, found);
            return found;
        } catch (IOException | RuntimeException ex) {
            throw new MapLocationUnavailableException("Address lookup unavailable", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MapLocationUnavailableException("Address lookup interrupted", ex);
        }
    }

    private String reverseWithNominatim(BigDecimal latitude, BigDecimal longitude) {
        String url = reverseUrl + "?format=jsonv2&zoom=18&addressdetails=0&accept-language=vi&lat="
                + latitude.toPlainString() + "&lon=" + longitude.toPlainString();
        try {
            JsonNode result = requestJson(url);
            String name = result.path("display_name").asText();
            return name.isBlank() ? null : name;
        } catch (IOException | RuntimeException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String reverseWithPhoton(BigDecimal latitude, BigDecimal longitude) {
        String url = fallbackReverseUrl + "?lat=" + latitude.toPlainString()
                + "&lon=" + longitude.toPlainString();
        try {
            JsonNode features = requestJson(url).path("features");
            if (!features.isArray() || features.isEmpty()) return null;
            return photonAddress(features.get(0).path("properties"));
        } catch (IOException | RuntimeException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private List<AddressSearchResult> searchWithNominatim(String query)
            throws IOException, InterruptedException {
        String url = searchUrl + "?format=jsonv2&addressdetails=0&accept-language=vi"
                + "&countrycodes=vn&limit=5&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            JsonNode results = requestJson(url);
            if (!results.isArray()) return null;
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
            return List.copyOf(matches);
        } catch (MapLocationUnavailableException ex) {
            return null;
        }
    }

    private List<AddressSearchResult> searchWithPhoton(String query)
            throws IOException, InterruptedException {
        String separator = fallbackSearchUrl.contains("?") ? "&" : "?";
        String url = fallbackSearchUrl + separator + "limit=5&q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        JsonNode features = requestJson(url).path("features");
        if (!features.isArray()) throw new MapLocationUnavailableException("Invalid fallback address response");

        List<AddressSearchResult> matches = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode properties = feature.path("properties");
            String countryCode = properties.path("countrycode").asText();
            if (!countryCode.isBlank() && !"VN".equalsIgnoreCase(countryCode)) continue;

            JsonNode coordinates = feature.path("geometry").path("coordinates");
            String address = photonAddress(properties);
            if (!address.isBlank() && coordinates.isArray() && coordinates.size() >= 2) {
                matches.add(new AddressSearchResult(address,
                        coordinates.get(1).decimalValue(), coordinates.get(0).decimalValue()));
            }
        }
        return List.copyOf(matches);
    }

    private JsonNode requestJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", userAgent)
                .timeout(requestTimeout)
                .GET()
                .build();
        lastRequestNanos = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new MapLocationUnavailableException("Address lookup returned HTTP " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private String photonAddress(JsonNode properties) {
        Set<String> parts = new LinkedHashSet<>();
        addAddressPart(parts, properties.path("name").asText());
        String street = properties.path("street").asText();
        String houseNumber = properties.path("housenumber").asText();
        addAddressPart(parts, houseNumber.isBlank() ? street : (houseNumber + " " + street).trim());
        addAddressPart(parts, properties.path("district").asText());
        addAddressPart(parts, properties.path("city").asText());
        addAddressPart(parts, properties.path("state").asText());
        addAddressPart(parts, properties.path("country").asText());
        return String.join(", ", parts);
    }

    private void addAddressPart(Set<String> parts, String value) {
        if (value != null && !value.isBlank()) parts.add(value.trim());
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
