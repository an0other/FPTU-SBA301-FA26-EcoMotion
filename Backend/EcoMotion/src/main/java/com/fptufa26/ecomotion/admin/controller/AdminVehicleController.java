package com.fptufa26.ecomotion.admin.controller;

import com.fptufa26.ecomotion.admin.dto.request.VehicleApprovalRequest;
import com.fptufa26.ecomotion.admin.dto.response.AdminVehicleDto;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.entity.VehicleDocument;
import com.fptufa26.ecomotion.vehicle.entity.VehicleImage;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.vehicle.repository.VehicleImageRepository;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/vehicles")
@RequiredArgsConstructor
@Tag(name = "Admin - Vehicle Approvals", description = "A03: Duyệt xe (Approve / Reject + lý do, không đòi tài xế)")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVehicleController {

    private final VehicleService vehicleService;
    private final VehicleImageRepository vehicleImageRepository;

    @GetMapping
    @Operation(summary = "Danh sách xe cần duyệt hoặc tất cả xe (lọc theo status, keyword)")
    public ResponseEntity<ApiResponse<List<AdminVehicleDto>>> listVehicles(
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) String keyword) {
        List<Vehicle> vehicles = vehicleService.listAdminVehicles(status, keyword);
        List<AdminVehicleDto> dtos = vehicles.stream().map(this::mapVehicleDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết xe cùng giấy tờ pháp lý (đăng ký, đăng kiểm, bảo hiểm)")
    public ResponseEntity<ApiResponse<AdminVehicleDto>> getVehicleDetail(@PathVariable Long id) {
        Vehicle vehicle = vehicleService.getAdminVehicle(id);
        return ResponseEntity.ok(ApiResponse.ok(mapVehicleDto(vehicle)));
    }

    @PatchMapping("/{id}/approval")
    @Operation(summary = "Duyệt hoặc từ chối xe (Approve / Reject + lý do)")
    public ResponseEntity<ApiResponse<AdminVehicleDto>> updateVehicleApproval(
            @PathVariable Long id,
            @Valid @RequestBody VehicleApprovalRequest request) {
        Vehicle updated = vehicleService.updateVehicleApproval(id, request.getStatus(), request.getReason());
        String msg = (request.getStatus() == VehicleStatus.APPROVED)
                ? "Duyệt xe thành công"
                : "Từ chối xe thành công";
        return ResponseEntity.ok(ApiResponse.ok(msg, mapVehicleDto(updated)));
    }

    private AdminVehicleDto mapVehicleDto(Vehicle v) {
        Provider p = v.getProvider();
        List<VehicleDocument> docs = vehicleService.getVehicleDocuments(v.getId());
        List<VehicleImage> images = vehicleImageRepository.findByVehicle_IdOrderByDisplayOrderAsc(v.getId());

        List<AdminVehicleDto.DocumentDto> docDtos = docs.stream()
                .map(d -> AdminVehicleDto.DocumentDto.builder()
                        .id(d.getId())
                        .docType(d.getDocType())
                        .docNumber(d.getDocNumber())
                        .expiryDate(d.getExpiryDate())
                        .fileUrl(d.getFileUrl())
                        .status(d.getStatus())
                        .build())
                .toList();

        List<String> imgUrls = images.stream().map(VehicleImage::getImageUrl).toList();

        return AdminVehicleDto.builder()
                .id(v.getId())
                .providerId(p != null ? p.getId() : null)
                .providerDisplayName(p != null ? p.getDisplayName() : null)
                .providerHotline(p != null ? p.getHotline() : null)
                .providerDepotAddress(p != null ? p.getDepotAddress() : null)
                .licensePlate(v.getLicensePlate())
                .chassisNo(v.getChassisNo())
                .engineNo(v.getEngineNo())
                .brand(v.getBrand())
                .model(v.getModel())
                .manufactureYear(v.getManufactureYear())
                .seatCount(v.getSeatCount())
                .transmission(v.getTransmission())
                .color(v.getColor())
                .fuelType(v.getFuelType())
                .status(v.getStatus())
                .maintenanceBufferMinutes(v.getMaintenanceBufferMinutes())
                .ratingAvg(v.getRatingAvg())
                .ratingCount(v.getRatingCount())
                .imageUrls(imgUrls)
                .documents(docDtos)
                .createdAt(v.getCreatedAt())
                .build();
    }
}
