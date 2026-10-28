package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.vehicle.dto.request.CreateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateRentalPlansRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleDocumentRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleDepositPolicyRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.UpdateVehicleRequest;
import com.fptufa26.ecomotion.vehicle.dto.request.VehicleVisibilityRequest;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderDashboardSummaryResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderRentalPlanResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDetailResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleDocumentResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehicleItemResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.ProviderVehiclePageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleDepositPolicyResponse;
import com.fptufa26.ecomotion.vehicle.enums.FuelType;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import java.util.List;

public interface ProviderVehicleService {

    ProviderDashboardSummaryResponse getDashboardSummary(Long userId);

    ProviderVehiclePageResponse getProviderVehicles(
            Long userId,
            String keyword,
            FuelType fuelType,
            VehicleStatus status,
            String sortBy,
            int page,
            int size
    );

    ProviderVehicleItemResponse toggleVehicleVisibility(
            Long userId,
            Long vehicleId,
            VehicleVisibilityRequest request
    );

    ProviderVehicleDetailResponse createVehicle(
            Long userId,
            CreateVehicleRequest request
    );

    ProviderVehicleDetailResponse getVehicleDetail(
            Long userId,
            Long vehicleId
    );

    ProviderVehicleDetailResponse updateVehicle(
            Long userId,
            Long vehicleId,
            UpdateVehicleRequest request
    );

    VehicleDepositPolicyResponse updateDepositPolicy(
            Long userId,
            Long vehicleId,
            UpdateVehicleDepositPolicyRequest request
    );

    VehicleDepositPolicyResponse getDepositPolicy(Long userId, Long vehicleId);

    List<ProviderRentalPlanResponse> getVehicleRentalPlans(
            Long userId,
            Long vehicleId
    );

    List<ProviderRentalPlanResponse> updateVehicleRentalPlans(
            Long userId,
            Long vehicleId,
            UpdateRentalPlansRequest request
    );

    List<ProviderVehicleDocumentResponse> getVehicleDocuments(
            Long userId,
            Long vehicleId
    );

    ProviderVehicleDocumentResponse updateVehicleDocument(
            Long userId,
            Long vehicleId,
            UpdateVehicleDocumentRequest request
    );
}
