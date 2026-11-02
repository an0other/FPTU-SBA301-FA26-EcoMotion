package com.fptufa26.ecomotion.finance.service.gateway.implement;

import com.fptufa26.ecomotion.finance.service.VietQrTokenService;
import com.fptufa26.ecomotion.finance.service.gateway.VietQrPaymentGatewayStrategy;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class VietQrPaymentGatewayStrategyImpl implements VietQrPaymentGatewayStrategy {
    private final RestClient client;
    private final VietQrTokenService tokens;

    public VietQrPaymentGatewayStrategyImpl(@Qualifier("vietQrRestClient") RestClient client, VietQrTokenService tokens) {
        this.client = client;
        this.tokens = tokens;
    }

    @Override
    public DynamicQr generate(String orderId, String bankCode, String account, String holder, BigDecimal amount) {
        try {
            String name = Normalizer.normalize(holder, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .replace('Đ', 'D').replace('đ', 'd');
            var body = Map.of("orderId", orderId, "bankCode", bankCode, "bankAccount", account,
                    "userBankName", name, "amount", amount.longValueExact(), "content", orderId, "qrType", 0, "transType", "C");
            Map<?, ?> result = client.post().uri("/vqr/api/qr/generate-customer")
                    .headers(h -> h.setBearerAuth(tokens.getToken())).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(Map.class);
            if (result == null) throw new IllegalStateException();
            String content = (String) result.get("content");
            String qrCode = (String) result.get("qrCode");
            if (!orderId.equals(result.get("orderId")) || !account.equals(result.get("bankAccount"))
                    || !bankCode.equals(result.get("bankCode")) || content == null || content.length() > 100
                    || !java.util.regex.Pattern.compile("(?<![A-Z0-9])" + orderId + "(?![A-Z0-9])").matcher(content).find()
                    || qrCode == null || qrCode.isBlank() || amount.compareTo(new BigDecimal(result.get("amount").toString())) != 0)
                throw new IllegalStateException();
            return new DynamicQr(orderId, bankCode, account, amount, content, qrCode, (String) result.get("qrLink"));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "VietQR không trả về QR hợp lệ hoặc đã hết thời gian chờ; chưa ghi nhận thanh toán");
        }
    }
}
