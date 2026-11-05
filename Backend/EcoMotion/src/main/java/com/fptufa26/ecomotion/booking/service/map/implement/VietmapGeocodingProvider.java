package com.fptufa26.ecomotion.booking.service.map.implement;
import com.fptufa26.ecomotion.booking.service.map.MapGeocodingProvider;
import com.fptufa26.ecomotion.booking.service.MapGeocodingService.GeoPoint;
import com.fptufa26.ecomotion.location.service.implement.VietmapClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;
@Component @RequiredArgsConstructor
public class VietmapGeocodingProvider implements MapGeocodingProvider {
    private final VietmapClient client;
    public String getProviderName(){return "VIETMAP";}
    public Optional<GeoPoint> geocode(String query){return client.search(query).stream().findFirst().map(p -> new GeoPoint(p.latitude().doubleValue(),p.longitude().doubleValue(),p.address()));}
}
