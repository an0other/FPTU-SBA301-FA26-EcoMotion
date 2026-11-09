package com.fptufa26.ecomotion.vehicle.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record ProviderVehicleDetailResponse(
        Long id,
        String licensePlate,
        String chassisNo,
        String engineNo,
        String brand,
        String model,
        Integer manufactureYear,
        Integer seatCount,
        Transmission transmission,
        FuelType fuelType,
        String color,
        VehicleStatus status,
        Integer maintenanceBufferMinutes,
        String coverImageUrl,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Instant createdAt,
        Instant updatedAt,
        List<ProviderRentalPlanResponse> plans,
        List<ProviderVehicleDocumentResponse> documents,
        List<String> galleryImageUrls,
        List<String> imageUrls
) {

    public ProviderVehicleDetailResponse(
            Long id,
            String licensePlate,
            String chassisNo,
            String engineNo,
            String brand,
            String model,
            Integer manufactureYear,
            Integer seatCount,
            Transmission transmission,
            FuelType fuelType,
            String color,
            VehicleStatus status,
            Integer maintenanceBufferMinutes,
            String coverImageUrl,
            BigDecimal ratingAvg,
            Integer ratingCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                licensePlate,
                chassisNo,
                engineNo,
                brand,
                model,
                manufactureYear,
                seatCount,
                transmission,
                fuelType,
                color,
                status,
                maintenanceBufferMinutes,
                coverImageUrl,
                ratingAvg,
                ratingCount,
                createdAt,
                updatedAt,
                List.of(),
                List.of(),
                List.of(),
                coverImageUrl != null ? List.of(coverImageUrl) : List.of()
        );
    }

    public ProviderVehicleDetailResponse(
            Long id,
            String licensePlate,
            String chassisNo,
            String engineNo,
            String brand,
            String model,
            Integer manufactureYear,
            Integer seatCount,
            Transmission transmission,
            FuelType fuelType,
            String color,
            VehicleStatus status,
            Integer maintenanceBufferMinutes,
            String coverImageUrl,
            BigDecimal ratingAvg,
            Integer ratingCount,
            Instant createdAt,
            Instant updatedAt,
            List<ProviderRentalPlanResponse> plans
    ) {
        this(
                id,
                licensePlate,
                chassisNo,
                engineNo,
                brand,
                model,
                manufactureYear,
                seatCount,
                transmission,
                fuelType,
                color,
                status,
                maintenanceBufferMinutes,
                coverImageUrl,
                ratingAvg,
                ratingCount,
                createdAt,
                updatedAt,
                plans != null ? plans : List.of(),
                List.of(),
                List.of(),
                coverImageUrl != null ? List.of(coverImageUrl) : List.of()
        );
    }

    public ProviderVehicleDetailResponse(
            Long id,
            String licensePlate,
            String chassisNo,
            String engineNo,
            String brand,
            String model,
            Integer manufactureYear,
            Integer seatCount,
            Transmission transmission,
            FuelType fuelType,
            String color,
            VehicleStatus status,
            Integer maintenanceBufferMinutes,
            String coverImageUrl,
            BigDecimal ratingAvg,
            Integer ratingCount,
            Instant createdAt,
            Instant updatedAt,
            List<ProviderRentalPlanResponse> plans,
            List<ProviderVehicleDocumentResponse> documents
    ) {
        this(
                id,
                licensePlate,
                chassisNo,
                engineNo,
                brand,
                model,
                manufactureYear,
                seatCount,
                transmission,
                fuelType,
                color,
                status,
                maintenanceBufferMinutes,
                coverImageUrl,
                ratingAvg,
                ratingCount,
                createdAt,
                updatedAt,
                plans != null ? plans : List.of(),
                documents != null ? documents : List.of(),
                List.of(),
                coverImageUrl != null ? List.of(coverImageUrl) : List.of()
        );
    }

    public ProviderVehicleDetailResponse(
            Long id,
            String licensePlate,
            String chassisNo,
            String engineNo,
            String brand,
            String model,
            Integer manufactureYear,
            Integer seatCount,
            Transmission transmission,
            FuelType fuelType,
            String color,
            VehicleStatus status,
            Integer maintenanceBufferMinutes,
            String coverImageUrl,
            BigDecimal ratingAvg,
            Integer ratingCount,
            Instant createdAt,
            Instant updatedAt,
            List<ProviderRentalPlanResponse> plans,
            List<ProviderVehicleDocumentResponse> documents,
            List<String> galleryImageUrls
    ) {
        this(
                id,
                licensePlate,
                chassisNo,
                engineNo,
                brand,
                model,
                manufactureYear,
                seatCount,
                transmission,
                fuelType,
                color,
                status,
                maintenanceBufferMinutes,
                coverImageUrl,
                ratingAvg,
                ratingCount,
                createdAt,
                updatedAt,
                plans != null ? plans : List.of(),
                documents != null ? documents : List.of(),
                galleryImageUrls != null ? galleryImageUrls : List.of(),
                buildAllImageUrls(coverImageUrl, galleryImageUrls)
        );
    }

    private static List<String> buildAllImageUrls(String coverImageUrl, List<String> galleryUrls) {
        List<String> all = new ArrayList<>();
        if (coverImageUrl != null && !coverImageUrl.isBlank()) {
            all.add(coverImageUrl);
        }
        if (galleryUrls != null) {
            all.addAll(galleryUrls);
        }
        return List.copyOf(all);
    }
}
