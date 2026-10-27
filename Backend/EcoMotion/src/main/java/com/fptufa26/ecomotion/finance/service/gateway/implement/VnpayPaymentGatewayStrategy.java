package com.fptufa26.ecomotion.finance.service.gateway.implement;

import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.config.VnpayUtils;
import com.fptufa26.ecomotion.finance.service.gateway.PaymentGatewayStrategy;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlCommand;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlResult;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentVerificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayPaymentGatewayStrategy implements PaymentGatewayStrategy {

    private static final DateTimeFormatter VNP_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final VnpayConfig vnpayConfig;

    @Override
    public PaymentMethod getPaymentMethod() {
        return PaymentMethod.VNPAY;
    }

    @Override
    public PaymentUrlResult generatePaymentUrl(PaymentUrlCommand command) {
        String vnpCreateDate = VNP_DATE_FORMATTER.format(command.createDate());
        String vnpExpireDate = VNP_DATE_FORMATTER.format(command.expireDate());
        long vnpAmount = command.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnpayConfig.getVersion());
        vnpParams.put("vnp_Command", vnpayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", vnpayConfig.getCurrCode());
        vnpParams.put("vnp_TxnRef", command.txnRef());
        vnpParams.put("vnp_OrderInfo", command.orderInfo());
        vnpParams.put("vnp_OrderType", vnpayConfig.getOrderType());
        vnpParams.put("vnp_Locale", command.language() != null && !command.language().isBlank() ? command.language() : "vn");
        vnpParams.put("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr", command.clientIp() != null && !command.clientIp().isBlank() ? command.clientIp() : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", vnpCreateDate);
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        if (command.bankCode() != null && !command.bankCode().isBlank()) {
            vnpParams.put("vnp_BankCode", command.bankCode().trim());
        }

        String hashData = VnpayUtils.buildHashData(vnpParams);
        String vnpSecureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        String queryUrl = VnpayUtils.buildQueryUrl(vnpParams) + "&vnp_SecureHash=" + vnpSecureHash;
        String paymentUrl = vnpayConfig.getPaymentUrl() + "?" + queryUrl;

        return new PaymentUrlResult(paymentUrl, command.txnRef(), command.amount(), command.expireDate());
    }

    @Override
    public PaymentVerificationResult verifyCallback(Map<String, String> params) {
        return verifyInternal(params);
    }

    @Override
    public PaymentVerificationResult verifyIpn(Map<String, String> params) {
        return verifyInternal(params);
    }

    private PaymentVerificationResult verifyInternal(Map<String, String> params) {
        boolean validSignature = verifySignature(params);
        String vnpTxnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String cardType = params.get("vnp_CardType");
        String payDate = params.get("vnp_PayDate");
        String amountStr = params.get("vnp_Amount");

        BigDecimal amount = BigDecimal.ZERO;
        if (amountStr != null) {
            try {
                amount = new BigDecimal(amountStr).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {
            }
        }

        boolean success = validSignature && "00".equals(responseCode);
        String message = resolveResponseMessage(responseCode, validSignature);

        return new PaymentVerificationResult(
                validSignature,
                success,
                vnpTxnRef,
                transactionNo,
                responseCode,
                amount,
                bankCode,
                cardType,
                payDate,
                message
        );
    }

    private boolean verifySignature(Map<String, String> params) {
        String vnpSecureHash = params.get("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isBlank()) {
            return false;
        }

        Map<String, String> cleanParams = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!"vnp_SecureHash".equals(entry.getKey()) && !"vnp_SecureHashType".equals(entry.getKey())) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    cleanParams.put(entry.getKey(), entry.getValue());
                }
            }
        }

        String hashData = VnpayUtils.buildHashData(cleanParams);
        String expectedHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        return expectedHash.equalsIgnoreCase(vnpSecureHash);
    }

    private String resolveResponseMessage(String responseCode, boolean validSignature) {
        if (!validSignature) {
            return "Chữ ký bảo mật không hợp lệ (Checksum failed)";
        }
        if (responseCode == null) {
            return "Không có mã phản hồi từ cổng thanh toán";
        }
        return switch (responseCode) {
            case "00" -> "Giao dịch thành công";
            case "07" -> "Trừ tiền thành công. Giao dịch bị nghi ngờ";
            case "09" -> "Giao dịch không thành công: Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking";
            case "10" -> "Giao dịch không thành công: Khách hàng xác thực không đúng quá 3 lần";
            case "11" -> "Giao dịch không thành công: Đã hết hạn chờ thanh toán";
            case "12" -> "Giao dịch không thành công: Thẻ/Tài khoản của khách hàng bị khóa";
            case "24" -> "Giao dịch không thành công: Khách hàng hủy giao dịch";
            case "51" -> "Giao dịch không thành công: Tài khoản không đủ số dư";
            case "65" -> "Giao dịch không thành công: Vượt quá hạn mức giao dịch trong ngày";
            case "75" -> "Ngân hàng thanh toán đang bảo trì";
            case "79" -> "Giao dịch không thành công: Nhập sai mật khẩu thanh toán quá số lần quy định";
            default -> "Giao dịch thất bại (Mã phản hồi: " + responseCode + ")";
        };
    }
}
