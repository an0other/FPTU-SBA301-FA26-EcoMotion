package com.fptufa26.ecomotion.location.service.implement;

import com.fptufa26.ecomotion.location.dto.response.*;
import com.fptufa26.ecomotion.location.exception.MapLocationUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class VietmapClient {
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String key;
    private final Duration timeout;
    private final HttpClient http;
    private final Semaphore permits=new Semaphore(4);
    private record Cached(JsonNode value, Instant expires) { }
    private final Map<String,Cached> cache=Collections.synchronizedMap(new LinkedHashMap<>(128,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Cached> entry){ return size()>512; }
    });
    private final ConcurrentHashMap<String,CompletableFuture<JsonNode>> inFlight=new ConcurrentHashMap<>();
    public VietmapClient(ObjectMapper mapper,
        @Value("${app.vietmap.base-url:https://maps.vietmap.vn/api}") String baseUrl,
        @Value("${VIETMAP_SERVICES_KEY:}") String key,
        @Value("${app.vietmap.timeout:5s}") Duration timeout) {
        this.mapper=mapper; this.baseUrl=baseUrl.replaceAll("/+$",""); this.key=key; this.timeout=timeout;
        this.http=HttpClient.newBuilder().connectTimeout(timeout).build();
    }
    public static void validate(double lat,double lon) {
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||lat < -90||lat > 90||lon < -180||lon >180) throw new IllegalArgumentException("Tọa độ không hợp lệ");
    }
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private JsonNode get(String path) {
        if(key==null||key.isBlank()) throw new MapLocationUnavailableException("VIETMAP chưa được cấu hình. Vui lòng bổ sung services key");
        Cached old=cache.get(path); if(old!=null && old.expires.isAfter(Instant.now())) return old.value;
        var promise=new CompletableFuture<JsonNode>(); var existing=inFlight.putIfAbsent(path,promise);
        if(existing!=null) {
            try { return existing.get(timeout.toMillis()+2000,TimeUnit.MILLISECONDS); }
            catch(Exception ex){ throw new MapLocationUnavailableException("Không thể lấy dữ liệu VIETMAP. Vui lòng thử lại"); }
        }
        boolean acquired=false;
        try {
            acquired=permits.tryAcquire(1,TimeUnit.SECONDS);
            if(!acquired) throw new MapLocationUnavailableException("Dịch vụ định tuyến đang bận. Vui lòng thử lại");
            var request=HttpRequest.newBuilder(URI.create(baseUrl+path+"&apikey="+enc(key))).timeout(timeout).GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200) throw new MapLocationUnavailableException(response.statusCode()==423 ? "VIETMAP hết hạn mức hoặc API chưa được cấp quyền" : "VIETMAP tạm thời không khả dụng hoặc key không hợp lệ");
            JsonNode result=mapper.readTree(response.body());
            cache.put(path,new Cached(result,Instant.now().plusSeconds(60))); promise.complete(result); return result;
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); var error=new MapLocationUnavailableException("Đã dừng yêu cầu định tuyến"); promise.completeExceptionally(error); throw error; }
        catch(Exception ex) { var error=ex instanceof MapLocationUnavailableException e?e:new MapLocationUnavailableException("Không thể lấy dữ liệu VIETMAP. Vui lòng thử lại"); promise.completeExceptionally(error); throw error; }
        finally { if(acquired) permits.release(); inFlight.remove(path,promise); }
    }
    public RoadRouteResponse route(double fromLat,double fromLon,double toLat,double toLon) {
        validate(fromLat,fromLon); validate(toLat,toLon);
        var result=get("/route/v4?vehicle=car&points_encoded=false&point="+fromLat+","+fromLon+"&point="+toLat+","+toLon);
        var path=result.path("paths").path(0); var points=path.path("points").path("coordinates");
        if(!path.path("distance").isNumber()||!path.path("time").isNumber()||!points.isArray()||points.size()<2)
            throw new MapLocationUnavailableException("Không tìm được tuyến ô tô hợp lệ giữa hai điểm");
        double distance=path.path("distance").asDouble(), millis=path.path("time").asDouble();
        if(!Double.isFinite(distance)||distance<0||!Double.isFinite(millis)||millis<0) throw new MapLocationUnavailableException("Dữ liệu tuyến đường không hợp lệ");
        List<List<Double>> coords=new ArrayList<>();
        for(var point:points) {
            if(!point.isArray()||point.size()<2||!point.path(0).isNumber()||!point.path(1).isNumber()) throw new MapLocationUnavailableException("Hình học tuyến đường không hợp lệ");
            double lon=point.path(0).asDouble(),lat=point.path(1).asDouble(); validate(lat,lon); coords.add(List.of(lon,lat));
        }
        return new RoadRouteResponse(distance,(long)Math.ceil(millis/1000),List.copyOf(coords),"VIETMAP_CAR",Instant.now());
    }
    public List<AddressSearchResult> search(String query) {
        if(query==null||query.trim().length()<3) return List.of();
        var result=get("/autocomplete/v4?display_type=1&text="+enc(query.trim()));
        if(!result.isArray()) throw new MapLocationUnavailableException("Dữ liệu địa chỉ không hợp lệ");
        List<AddressSearchResult> items=new ArrayList<>();
        for(var suggestion:result) {
            if(items.size()>=5) break;
            String ref=suggestion.path("ref_id").asString(""); if(ref.isBlank()) continue;
            var detail=get("/place/v4?display_type=1&refid="+enc(ref)); if(detail.isArray()) detail=detail.path(0);
            if(!detail.path("lat").isNumber()||!detail.path("lng").isNumber()) continue;
            double lat=detail.path("lat").asDouble(),lon=detail.path("lng").asDouble(); validate(lat,lon);
            String address=detail.path("display").asString(suggestion.path("display").asString(""));
            if(!address.isBlank()) items.add(new AddressSearchResult(address,BigDecimal.valueOf(lat),BigDecimal.valueOf(lon)));
        }
        return List.copyOf(items);
    }
    public String reverse(BigDecimal latitude,BigDecimal longitude) {
        if(latitude==null||longitude==null) throw new IllegalArgumentException("Thiếu tọa độ");
        validate(latitude.doubleValue(),longitude.doubleValue());
        var result=get("/reverse/v4?display_type=1&lat="+latitude+"&lng="+longitude);
        String address=result.path(0).path("display").asString("");
        if(address.isBlank()) throw new MapLocationUnavailableException("Không tìm được địa chỉ tại tọa độ này");
        return address;
    }
}
