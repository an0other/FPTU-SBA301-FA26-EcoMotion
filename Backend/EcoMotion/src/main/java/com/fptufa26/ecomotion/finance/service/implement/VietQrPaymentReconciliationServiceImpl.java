package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.finance.dto.request.VietQrTransactionSyncRequest;
import com.fptufa26.ecomotion.finance.entity.VietQrCallback;
import com.fptufa26.ecomotion.finance.repository.*;
import com.fptufa26.ecomotion.finance.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
@RequiredArgsConstructor
public class VietQrPaymentReconciliationServiceImpl implements VietQrPaymentReconciliationService {
    private final VietQrCallbackRepository callbacks;
    private final PaymentRequestRepository requests;
    private final BookingPaymentLedgerService ledger;
    private final PaymentRequestService payments;
    private final EntityManager em;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public String reconcile(VietQrTransactionSyncRequest input) {
        String payload = mapper.writeValueAsString(input);
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        var candidate = requests.findByVietQrOrderId(input.orderId()).orElse(null);
        if (candidate != null) {
            ledger.requireBookingForUpdate(candidate.getBooking().getId());
            em.detach(candidate);
            candidate = requests.findByIdForUpdate(candidate.getId()).orElseThrow();
            em.refresh(candidate, LockModeType.PESSIMISTIC_WRITE);
        }
        var existing = callbacks.findByTransactionId(input.transactionid()).orElse(null);
        if (existing != null) {
            if (!hash.equals(existing.getPayloadHash())) throw new ResponseStatusException(CONFLICT, "Transaction ID đã tồn tại với nội dung khác");
            return existing.getOutcome();
        }
        var audit = new VietQrCallback();
        audit.setTransactionId(input.transactionid());
        audit.setPayloadHash(hash);
        audit.setPayload(payload);
        audit.setOrderId(input.orderId());
        audit.setReceivedAt(clock.instant());
        String outcome = "REVIEW_REQUIRED: UNKNOWN_ORDER";
        if (candidate != null) {
            audit.setPaymentRequestId(candidate.getId());
            if (!candidate.isVietQrManaged()) outcome = "REVIEW_REQUIRED: QR_NOT_ISSUED";
            else if (!"C".equals(input.transType())) outcome = "REVIEW_REQUIRED: DEBIT_TRANSACTION";
            else if (!candidate.getAccountNumberSnapshot().equals(input.bankaccount())) outcome = "REVIEW_REQUIRED: WRONG_ACCOUNT";
            else if (!input.content().equals(candidate.getTransferReference())
                    || !java.util.regex.Pattern.compile("(?<![A-Z0-9])" + candidate.getPaymentCode() + "(?![A-Z0-9])")
                        .matcher(input.content()).find()) outcome = "REVIEW_REQUIRED: WRONG_CONTENT";
            else outcome = payments.applyVietQrPayment(candidate, input);
        }
        audit.setOutcome(outcome);
        callbacks.saveAndFlush(audit);
        return outcome;
    }
}
