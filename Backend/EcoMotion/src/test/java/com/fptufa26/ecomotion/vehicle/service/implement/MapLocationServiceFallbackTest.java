package com.fptufa26.ecomotion.vehicle.service.implement;

import com.fptufa26.ecomotion.vehicle.dto.response.AddressSearchResult;
import com.fptufa26.ecomotion.vehicle.service.MapLocationService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapLocationServiceFallbackTest {

    private HttpServer server;
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/primary/search", HttpExchange::close);
        server.createContext("/primary/reverse", exchange -> respond(exchange, 503, "unavailable"));
        server.createContext("/fallback/api", exchange -> respond(exchange, 200, """
                {"type":"FeatureCollection","features":[{
                  "type":"Feature",
                  "properties":{"name":"Vinhomes Central Park","district":"Bình Thạnh","city":"Thành phố Hồ Chí Minh","country":"Việt Nam"},
                  "geometry":{"type":"Point","coordinates":[106.7218,10.7949]}
                }]}
                """));
        server.createContext("/fallback/reverse", exchange -> respond(exchange, 200, """
                {"type":"FeatureCollection","features":[{
                  "type":"Feature",
                  "properties":{"name":"Vinhomes Central Park","district":"Bình Thạnh","city":"Thành phố Hồ Chí Minh","country":"Việt Nam"},
                  "geometry":{"type":"Point","coordinates":[106.7218,10.7949]}
                }]}
                """));
        server.start();

        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-map", Map.of(
                "app.map.search-url", baseUrl + "/primary/search",
                "app.map.reverse-url", baseUrl + "/primary/reverse",
                "app.map.fallback-search-url", baseUrl + "/fallback/api",
                "app.map.fallback-reverse-url", baseUrl + "/fallback/reverse",
                "app.map.user-agent", "EcoMotion-Test/1.0",
                "app.map.connect-timeout", "1s",
                "app.map.request-timeout", "1s"
        )));
        context.registerBean("objectMapper", ObjectMapper.class, () -> new ObjectMapper());
        context.register(MapLocationServiceImpl.class);
        context.refresh();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
        if (server != null) server.stop(0);
    }

    @Test
    void searchFallsBackToPhotonWhenPrimaryGeocoderIsUnavailable() {
        MapLocationService service = context.getBean(MapLocationService.class);

        List<AddressSearchResult> results = service.searchAddress("Vinhomes");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().address()).contains("Vinhomes Central Park", "Thành phố Hồ Chí Minh");
        assertThat(results.getFirst().latitude()).isEqualByComparingTo("10.7949");
        assertThat(results.getFirst().longitude()).isEqualByComparingTo("106.7218");
    }

    @Test
    void reverseGeocodeFallsBackToPhotonWhenPrimaryGeocoderIsUnavailable() {
        MapLocationService service = context.getBean(MapLocationService.class);

        String address = service.reverseGeocode(new BigDecimal("10.7949"), new BigDecimal("106.7218"));

        assertThat(address).contains("Vinhomes Central Park", "Thành phố Hồ Chí Minh");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
