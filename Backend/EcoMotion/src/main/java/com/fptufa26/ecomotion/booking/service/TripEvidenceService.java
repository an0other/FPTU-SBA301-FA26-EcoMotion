package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.TripEvidenceResponse;
import com.fptufa26.ecomotion.booking.enums.TripEvidenceType;
import com.fptufa26.ecomotion.user.enums.Role;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TripEvidenceService {

    TripEvidenceResponse uploadEvidence(
            Long currentUserId,
            Role currentUserRole,
            String bookingIdOrCode,
            MultipartFile file,
            TripEvidenceType type,
            String note
    );

    List<TripEvidenceResponse> listEvidence(
            Long currentUserId,
            Role currentUserRole,
            String bookingIdOrCode
    );

    void deleteEvidence(
            Long currentUserId,
            Role currentUserRole,
            String bookingIdOrCode,
            Long evidenceId
    );
}
