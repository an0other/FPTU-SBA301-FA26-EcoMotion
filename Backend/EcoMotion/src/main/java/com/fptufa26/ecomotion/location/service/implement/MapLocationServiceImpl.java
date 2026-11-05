package com.fptufa26.ecomotion.location.service.implement;
import com.fptufa26.ecomotion.location.service.MapLocationService;
import com.fptufa26.ecomotion.location.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
@Service @RequiredArgsConstructor
public class MapLocationServiceImpl implements MapLocationService {
    private final VietmapClient client;
    @Override public double distanceKm(double a,double b,double c,double d){ return client.route(a,b,c,d).distanceMeters()/1000; }
    @Override public RoadRouteResponse route(double a,double b,double c,double d){ return client.route(a,b,c,d); }
    @Override public String reverseGeocode(BigDecimal lat,BigDecimal lon){ return client.reverse(lat,lon); }
    @Override public List<AddressSearchResult> searchAddress(String query){ return client.search(query); }
}
