package com.fptufa26.ecomotion.booking.service;

import java.time.Instant;
import java.time.LocalDate;

public interface YearlyHandoverService {
    Handover dispatch(Long providerUserId, String code);
    Handover arrive(Long providerUserId, String code, long initialKm, long evidenceId);
    Handover receive(Long renterUserId, String code);
    Handover get(Long userId, String code);
    record Handover(String stage, Instant dispatchedAt, Instant arrivedAt,
                    LocalDate receivedOn, Long initialKm, String evidenceUrl) {}
}
