package com.fptufa26.ecomotion.location.service;

import com.fptufa26.ecomotion.location.service.implement.VietmapClient;
import com.fptufa26.ecomotion.location.exception.MapLocationUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class VietmapClientTest {
    HttpServer server; String url;
    @BeforeEach void setup() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);url="http://127.0.0.1:"+server.getAddress().getPort();}
    @AfterEach void stop(){server.stop(0);}
    VietmapClient client(String key){return new VietmapClient(new ObjectMapper(),url,key,Duration.ofSeconds(2));}
    static final String ROUTE="{\"paths\":[{\"distance\":4200,\"time\":125000,\"points\":{\"coordinates\":[[106.6,10.7],[106.7,10.8]]}}]}";
    @Test void roadDistanceGeometryAndConcurrentRequestsAreNotStraightLineEstimates() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        server.createContext("/route/v4", ex -> { calls.incrementAndGet(); assertThat(ex.getRequestURI().getQuery()).contains("vehicle=car","points_encoded=false","point=10.7,106.6");byte[] body=ROUTE.getBytes();ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close(); });server.start();
        var maps=client("test-only"); var workers=Executors.newFixedThreadPool(8);
        try {
            var tasks=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<8;i++) tasks.add(workers.submit(() -> {
                var route=maps.route(10.7,106.6,10.8,106.7);assertThat(route.distanceMeters()).isEqualTo(4200);
                assertThat(route.durationSeconds()).isEqualTo(125);assertThat(route.coordinates().getFirst()).containsExactly(106.6,10.7);
            }));
            for(var task:tasks) task.get(5,TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(1);
        } finally {workers.shutdownNow();}
    }
    @Test void missingKeyAndInvalidCoordinatesDoNotCallProviderOrInventZeroDistance(){
        server.start();assertThatThrownBy(() -> client("").route(10,106,11,107)).isInstanceOf(MapLocationUnavailableException.class);
        assertThatThrownBy(() -> client("test").route(106,10,11,107)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void noRoadAndExhaustedQuotaAreExplicitFailures(){
        server.createContext("/route/v4", ex -> {byte[] body="{\"paths\":[]}".getBytes();ex.sendResponseHeaders(200,body.length);ex.getResponseBody().write(body);ex.close();});server.start();
        assertThatThrownBy(() -> client("test").route(10,106,11,107)).isInstanceOf(MapLocationUnavailableException.class);
        server.removeContext("/route/v4");server.createContext("/route/v4", ex -> {ex.sendResponseHeaders(423,-1);ex.close();});
        assertThatThrownBy(() -> client("test").route(10,106,11,107)).hasMessageContaining("hạn mức");
    }
    @Test void autocompleteUsesPlaceDetailCoordinatesAndReverseUsesDisplay(){
        server.createContext("/autocomplete/v4", ex -> {byte[] b="[{\"ref_id\":\"poi:test\",\"display\":\"Địa điểm\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);ex.sendResponseHeaders(200,b.length);ex.getResponseBody().write(b);ex.close();});
        server.createContext("/place/v4", ex -> {assertThat(ex.getRequestURI().getQuery()).contains("refid=poi:test");byte[] b="{\"lat\":10.7,\"lng\":106.6,\"display\":\"Địa điểm VN\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);ex.sendResponseHeaders(200,b.length);ex.getResponseBody().write(b);ex.close();});
        server.createContext("/reverse/v4", ex -> {byte[] b="[{\"display\":\"Địa chỉ VN\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);ex.sendResponseHeaders(200,b.length);ex.getResponseBody().write(b);ex.close();});server.start();
        var maps=client("test");assertThat(maps.search("Địa điểm").getFirst().latitude()).isEqualByComparingTo("10.7");
        assertThat(maps.reverse(new java.math.BigDecimal("10.7"),new java.math.BigDecimal("106.6"))).isEqualTo("Địa chỉ VN");
    }
}
