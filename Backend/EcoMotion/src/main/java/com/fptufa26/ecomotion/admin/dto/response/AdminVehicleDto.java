package com.fptufa26.ecomotion.admin.dto.response;

import com.fptufa26.ecomotion.vehicle.enums.DocumentStatus;
import com.fptufa26.ecomotion.vehicle.enums.DocumentType;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.Transmission;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVehicleDto {
    private Long id;
    private Long providerId;
    private String providerDisplayName;
    private String providerHotline;
    private String providerDepotAddress;
    private String licensePlate;
    private String chassisNo;
    private String engineNo;
    private String brand;
    private String model;
    private Integer manufactureYear;
    private Integer seatCount;
    private Transmission transmission;
    private String color;
    private FuelType fuelType;
    private VehicleStatus status;
    private Integer maintenanceBufferMinutes;
    private BigDecimal ratingAvg;
    private Integer ratingCount;
    private List<String> imageUrls;
    private List<DocumentDto> documents;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentDto {
        private Long id;
        private DocumentType docType;
        private String docNumber;
        private LocalDate expiryDate;
        private String fileUrl;
        private DocumentStatus status;
    }
}
