package com.fptufa26.ecomotion.booking.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record PickupQuoteResponse(double distanceKm,int freeRadiusKm,int chargeableKm,BigDecimal outOfRadiusRate,
        BigDecimal pickupFee,BigDecimal originLatitude,BigDecimal originLongitude,String distanceSource,
        Long durationSeconds,List<List<Double>> coordinates,Instant quotedAt) {
    public PickupQuoteResponse(double distanceKm,int freeRadiusKm,int chargeableKm,BigDecimal outOfRadiusRate,BigDecimal pickupFee,BigDecimal originLatitude,BigDecimal originLongitude){
        this(distanceKm,freeRadiusKm,chargeableKm,outOfRadiusRate,pickupFee,originLatitude,originLongitude,null,null,null,null);
    }
    public PickupQuoteResponse(double distanceKm,int freeRadiusKm,int chargeableKm,BigDecimal outOfRadiusRate,BigDecimal pickupFee){
        this(distanceKm,freeRadiusKm,chargeableKm,outOfRadiusRate,pickupFee,null,null);
    }
}
