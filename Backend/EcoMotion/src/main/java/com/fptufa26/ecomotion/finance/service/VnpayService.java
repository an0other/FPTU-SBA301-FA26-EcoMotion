package com.fptufa26.ecomotion.finance.service;

import com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest;
import com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayCallbackResultResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayIpnResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse;

import java.util.Map;

public interface VnpayService {

    VnpayPaymentUrlResponse createPaymentUrl(Long currentUserId, CreateVnpayPaymentUrlRequest request, String clientIp);

    RenterSettlementResponse getSettlementDetails(Long currentUserId, String bookingCodeOrId);

    VnpayPaymentUrlResponse createFinalPaymentUrl(Long currentUserId, String bookingCodeOrId, String clientIp);

    VnpayIpnResponse processIpn(Map<String, String> params);

    VnpayCallbackResultResponse processCallback(Map<String, String> params);

    VnpayCallbackResultResponse queryTransaction(String bookingCode, String vnpTxnRef);
}
