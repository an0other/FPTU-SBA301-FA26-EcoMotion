package com.fptufa26.ecomotion.booking.controller;

import com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import com.fptufa26.ecomotion.booking.service.TripEvidenceService;
import com.fptufa26.ecomotion.common.response.ApiResponse;
import com.fptufa26.ecomotion.common.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/evidences")
@RequiredArgsConstructor
@Tag(name = "Booking Evidences", description = "Trip evidence upload to Cloudinary and management APIs")
public class BookingEvidenceController {

    private final TripEvidenceService tripEvidenceService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('DRIVER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "Upload trip evidence file directly to Cloudinary and save metadata")
    public ApiResponse<TripEvidenceResponse> uploadEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("bookingId") String bookingId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") TripEvidenceType type,
            @RequestParam(value = "note", required = false) String note) {

        TripEvidenceResponse response = tripEvidenceService.uploadEvidence(
                principal.getUserId(),
                principal.getRole(),
                bookingId,
                file,
                type,
                note
        );
        return ApiResponse.success(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DRIVER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "List all trip evidences for a booking")
    public ApiResponse<List<TripEvidenceResponse>> listEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("bookingId") String bookingId) {

        List<TripEvidenceResponse> response = tripEvidenceService.listEvidence(
                principal.getUserId(),
                principal.getRole(),
                bookingId
        );
        return ApiResponse.success(response);
    }

    @DeleteMapping("/{evidenceId}")
    @PreAuthorize("hasAnyRole('DRIVER', 'PROVIDER', 'ADMIN')")
    @Operation(summary = "Delete a trip evidence record and remove file from Cloudinary")
    public ApiResponse<String> deleteEvidence(
            @AuthenticationPrincipal SecurityUser principal,
            @PathVariable("bookingId") String bookingId,
            @PathVariable("evidenceId") Long evidenceId) {

        tripEvidenceService.deleteEvidence(
                principal.getUserId(),
                principal.getRole(),
                bookingId,
                evidenceId
        );
        return ApiResponse.success("Đã xóa ảnh minh chứng thành công");
    }
}
