package com.fptufa26.ecomotion.booking.dto.response;

import com.fptufa26.ecomotion.booking.entity.TripEvidence;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;

import java.time.Instant;

public record TripEvidenceResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        TripEvidenceType type,
        String imageUrl,
        String publicId,
        String originalFileName,
        Long fileSize,
        Integer width,
        Integer height,
        String note,
        UploadedByDto uploadedBy,
        Instant createdAt
) {
    public record UploadedByDto(
            Long id,
            String name,
            String role
    ) {
    }

    public static TripEvidenceResponse fromEntity(TripEvidence ev) {
        UploadedByDto uploader = null;
        if (ev.getUploadedBy() != null) {
            String name = ev.getUploadedBy().getFullName() != null && !ev.getUploadedBy().getFullName().isBlank()
                    ? ev.getUploadedBy().getFullName()
                    : ev.getUploadedBy().getEmail();
            String roleStr = ev.getUploadedBy().getRole() != null ? ev.getUploadedBy().getRole().name() : null;
            uploader = new UploadedByDto(ev.getUploadedBy().getId(), name, roleStr);
        } else if (ev.getDriver() != null && ev.getDriver().getUser() != null) {
            String name = ev.getDriver().getUser().getFullName() != null && !ev.getDriver().getUser().getFullName().isBlank()
                    ? ev.getDriver().getUser().getFullName()
                    : ev.getDriver().getUser().getEmail();
            uploader = new UploadedByDto(ev.getDriver().getUser().getId(), name, "DRIVER");
        }

        return new TripEvidenceResponse(
                ev.getId(),
                ev.getBooking() != null ? ev.getBooking().getId() : null,
                ev.getBooking() != null ? ev.getBooking().getBookingCode() : null,
                ev.getEvidenceType(),
                ev.getImageUrl(),
                ev.getCloudinaryPublicId(),
                ev.getOriginalFileName(),
                ev.getFileSize(),
                ev.getWidth(),
                ev.getHeight(),
                ev.getNote(),
                uploader,
                ev.getCreatedAt()
        );
    }
}
