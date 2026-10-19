package com.fptufa26.ecomotion.admin.controller;

import com.fptufa26.ecomotion.admin.dto.request.DriverApprovalRequest;
import com.fptufa26.ecomotion.admin.dto.request.EkycStatusRequest;
import com.fptufa26.ecomotion.admin.dto.request.ProviderStatusRequest;
import com.fptufa26.ecomotion.admin.dto.request.UserStatusRequest;
import com.fptufa26.ecomotion.admin.dto.response.AdminDriverDto;
import com.fptufa26.ecomotion.admin.dto.response.AdminEkycDto;
import com.fptufa26.ecomotion.admin.dto.response.AdminProviderDto;
import com.fptufa26.ecomotion.admin.dto.response.AdminUserDto;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.enums.EkycStatus;
import com.fptufa26.ecomotion.user.enums.ProviderStatus;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Users & Approvals", description = "A02: Quản lý người dùng, duyệt nhà cung cấp, duyệt tài xế, duyệt eKYC")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/users")
    @Operation(summary = "Danh sách người dùng hệ thống (lọc theo role, status, keyword)")
    public ResponseEntity<ApiResponse<List<AdminUserDto>>> listUsers(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String keyword) {
        List<User> users = userService.listAdminUsers(role, status, keyword);
        List<AdminUserDto> dtos = users.stream().map(this::mapUserDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Cập nhật trạng thái người dùng (Khóa / Mở khóa)")
    public ResponseEntity<ApiResponse<AdminUserDto>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request) {
        User updated = userService.updateUserStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái người dùng thành công", mapUserDto(updated)));
    }

    @GetMapping("/providers")
    @Operation(summary = "Danh sách nhà cung cấp (lọc theo status, vd: PENDING)")
    public ResponseEntity<ApiResponse<List<AdminProviderDto>>> listProviders(
            @RequestParam(required = false) ProviderStatus status) {
        List<Provider> providers = userService.listProviders(status);
        List<AdminProviderDto> dtos = providers.stream().map(this::mapProviderDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PatchMapping("/providers/{id}/status")
    @Operation(summary = "Duyệt / Khóa nhà cung cấp")
    public ResponseEntity<ApiResponse<AdminProviderDto>> updateProviderStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProviderStatusRequest request) {
        Provider updated = userService.updateProviderStatus(id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái nhà cung cấp thành công", mapProviderDto(updated)));
    }

    @GetMapping("/drivers")
    @Operation(summary = "Danh sách tài xế (lọc theo status, vd: PENDING)")
    public ResponseEntity<ApiResponse<List<AdminDriverDto>>> listDrivers(
            @RequestParam(required = false) DriverStatus status) {
        List<Driver> drivers = userService.listDrivers(status);
        List<AdminDriverDto> dtos = drivers.stream().map(this::mapDriverDto).toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PatchMapping("/drivers/{id}/status")
    @Operation(summary = "Duyệt / Từ chối tài xế (kèm lý do nếu từ chối)")
    public ResponseEntity<ApiResponse<AdminDriverDto>> updateDriverStatus(
            @PathVariable Long id,
            @Valid @RequestBody DriverApprovalRequest request) {
        Driver updated = userService.updateDriverStatus(id, request.getStatus(), request.getRejectReason());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái tài xế thành công", mapDriverDto(updated)));
    }

    @GetMapping("/ekyc")
    @Operation(summary = "Danh sách hồ sơ eKYC (chờ duyệt hoặc tất cả)")
    public ResponseEntity<ApiResponse<List<AdminEkycDto>>> listEkyc(
            @RequestParam(required = false) EkycStatus status) {
        List<User> users = userService.listAdminUsers(null, null, null);
        EkycStatus target = (status != null) ? status : EkycStatus.PENDING;
        List<AdminEkycDto> dtos = users.stream()
                .filter(u -> u.getEkycStatus() == target)
                .map(this::mapEkycDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PatchMapping("/ekyc/{userId}/status")
    @Operation(summary = "Xác nhận duyệt hoặc từ chối eKYC")
    public ResponseEntity<ApiResponse<AdminEkycDto>> updateEkycStatus(
            @PathVariable Long userId,
            @Valid @RequestBody EkycStatusRequest request) {
        User updated = userService.updateEkycStatus(userId, request.getStatus());
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái eKYC thành công", mapEkycDto(updated)));
    }

    private AdminUserDto mapUserDto(User user) {
        return AdminUserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .ekycStatus(user.getEkycStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AdminProviderDto mapProviderDto(Provider p) {
        User u = p.getUser();
        return AdminProviderDto.builder()
                .id(p.getId())
                .userId(u != null ? u.getId() : null)
                .userEmail(u != null ? u.getEmail() : null)
                .userFullName(u != null ? u.getFullName() : null)
                .userPhone(u != null ? u.getPhone() : null)
                .providerType(p.getProviderType())
                .displayName(p.getDisplayName())
                .hotline(p.getHotline())
                .depotAddress(p.getDepotAddress())
                .commissionRate(p.getCommissionRate())
                .depositRatio(p.getDepositRatio())
                .bankName(p.getBankName())
                .bankAccountNo(p.getBankAccountNo())
                .bankAccountHolder(p.getBankAccountHolder())
                .businessLicenseNo(p.getBusinessLicenseNo())
                .taxCode(p.getTaxCode())
                .status(p.getStatus())
                .ratingAvg(p.getRatingAvg())
                .ratingCount(p.getRatingCount())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private AdminDriverDto mapDriverDto(Driver d) {
        User u = d.getUser();
        Provider p = d.getProvider();
        return AdminDriverDto.builder()
                .id(d.getId())
                .userId(u != null ? u.getId() : null)
                .userEmail(u != null ? u.getEmail() : null)
                .userFullName(u != null ? u.getFullName() : null)
                .userPhone(u != null ? u.getPhone() : null)
                .providerId(p != null ? p.getId() : null)
                .providerDisplayName(p != null ? p.getDisplayName() : null)
                .licenseNo(d.getLicenseNo())
                .licenseClass(d.getLicenseClass())
                .licenseExpiry(d.getLicenseExpiry())
                .portraitUrl(d.getPortraitUrl())
                .licenseImageUrl(d.getLicenseImageUrl())
                .status(d.getStatus())
                .rejectReason(d.getRejectReason())
                .ratingAvg(d.getRatingAvg())
                .ratingCount(d.getRatingCount())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private AdminEkycDto mapEkycDto(User u) {
        Renter renter = userService.findRenterProfile(u.getId()).orElse(null);
        return AdminEkycDto.builder()
                .userId(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .role(u.getRole())
                .ekycStatus(u.getEkycStatus())
                .cccdNo(renter != null ? renter.getCccdNo() : null)
                .dateOfBirth(renter != null ? renter.getDateOfBirth() : null)
                .gender(renter != null ? renter.getGender() : null)
                .address(renter != null ? renter.getAddress() : null)
                .cccdImageUrl(renter != null ? renter.getCccdImageUrl() : null)
                .cccdImageBackUrl(renter != null ? renter.getCccdImageBackUrl() : null)
                .portraitUrl(renter != null ? renter.getPortraitUrl() : null)
                .createdAt(u.getCreatedAt())
                .build();
    }
}
