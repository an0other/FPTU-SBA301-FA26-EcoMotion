package com.fptufa26.ecomotion.finance.service;

public interface PaymentCodeGenerator {
    /**
     * Atomically generates the next transaction code with method prefix (e.g., "VP000001", "VQ000001").
     *
     * @param prefix 2-letter classification prefix: "VP" for VNPay, "VQ" for VietQR.
     * @return unique padded transaction code.
     */
    String generatePaymentCode(String prefix);
}
