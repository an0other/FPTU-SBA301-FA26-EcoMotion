package com.fptufa26.ecomotion.booking.dto.response;

import java.time.Instant;

public record TripEndHistoryResponse(Long id, Long reportedBy, Instant reportedAt, Instant deadline, String state,
        String confirmationMethod, Instant confirmedAt, Long confirmedBy, Long disputeId, Long adminId, String adminNote, Instant adminActionAt, String adminAction) { }
