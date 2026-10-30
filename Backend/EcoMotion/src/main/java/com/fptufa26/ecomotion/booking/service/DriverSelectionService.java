package com.fptufa26.ecomotion.booking.service;

import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import java.util.List;

public interface DriverSelectionService {
    Selection select(List<DriverCandidateResponse> candidates);
    long penalty(DriverCandidateResponse candidate);

    record Selection(String status, DriverCandidateResponse candidate) {}
}
