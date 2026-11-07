package com.fptufa26.ecomotion.finance.service;
public interface RefundReceiptService {
    void respond(Long userId, Long id, boolean received);
    void confirmExpired();
}
