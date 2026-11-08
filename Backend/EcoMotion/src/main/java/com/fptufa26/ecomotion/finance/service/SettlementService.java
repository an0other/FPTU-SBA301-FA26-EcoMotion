package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse;

public interface SettlementService {

    RenterSettlementResponse getSettlementDetails(Long currentUserId, String bookingCodeOrId);
}
