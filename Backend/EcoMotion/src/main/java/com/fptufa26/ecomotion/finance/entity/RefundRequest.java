package com.fptufa26.ecomotion.finance.entity;

import com.fptufa26.ecomotion.common.crypto.EncryptedStringConverter;
import jakarta.persistence.Convert;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "refund_request")
@Getter
@Setter
@NoArgsConstructor
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_transaction_id")
    private PaymentTransaction paymentTransaction;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "recipient_bank_name", length = 150)
    private String recipientBankName;

    @Column(name = "recipient_account_holder", length = 150)
    private String recipientAccountHolder;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "recipient_account_number", length = 255)
    private String recipientAccountNumber;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status = RefundStatus.REQUESTED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "gateway_refund_ref", length = 100)
    private String gatewayRefundRef;

    @Column(name = "source_event_id", length = 120, unique = true)
    private String sourceEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_payment_transaction_id")
    private PaymentTransaction sourcePaymentTransaction;

    @Column(name = "vnp_request_id", length = 32)
    private String vnpRequestId;

    @Column(name = "gateway_submitted_at")
    private Instant gatewaySubmittedAt;

    @Column(name = "refund_transaction_no", length = 40)
    private String refundTransactionNo;

    @Column(name = "vnp_response_code", length = 10)
    private String vnpResponseCode;

    @Column(name = "vnp_transaction_status", length = 10)
    private String vnpTransactionStatus;

    @Column(name = "receipt_status")
    private String receiptStatus;

    @Column(name = "receipt_waiting_at")
    private Instant receiptWaitingAt;

    @Column(name = "receipt_deadline")
    private Instant receiptDeadline;

    @Column(name = "receipt_confirmed_at")
    private Instant receiptConfirmedAt;

    @Column(name = "receipt_confirmed_by")
    private Long receiptConfirmedBy;

    @Column(name = "receipt_reported_at")
    private Instant receiptReportedAt;

    @Column(name = "receipt_auto_confirmed_at")
    private Instant receiptAutoConfirmedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = RefundStatus.REQUESTED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
