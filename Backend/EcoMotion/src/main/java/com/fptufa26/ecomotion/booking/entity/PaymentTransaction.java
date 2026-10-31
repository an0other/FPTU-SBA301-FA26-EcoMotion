package com.fptufa26.ecomotion.booking.entity;

import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id")
    private BookingCharge charge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "applied_amount", precision = 14, scale = 2)
    private BigDecimal appliedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PaymentDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "transaction_code", length = 32, unique = true)
    private String transactionCode;

    @Column(name = "gateway_ref", length = 100)
    private String gatewayRef;

    @Column(name = "vnp_txn_ref", length = 100, unique = true)
    private String vnpTxnRef;

    @Column(name = "vnp_transaction_no", length = 100)
    private String vnpTransactionNo;

    @Column(name = "vnp_bank_code", length = 20)
    private String vnpBankCode;

    @Column(name = "vnp_card_type", length = 50)
    private String vnpCardType;

    @Column(name = "vnp_pay_date", length = 50)
    private String vnpPayDate;

    @Column(name = "vnp_response_code", length = 20)
    private String vnpResponseCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "receipt_url", length = 500)
    private String receiptUrl;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", length = 30)
    private PaymentVerificationMethod verificationMethod = PaymentVerificationMethod.PROVIDER_CONFIRMED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_request_id")
    private PaymentRequest paymentRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiving_bank_account_id")
    private com.fptufa26.ecomotion.finance.entity.ProviderBankAccount receivingBankAccount;

    @Column(name = "processing_note", length = 500)
    private String processingNote;

    @Column(name = "payout_gross_amount", precision = 14, scale = 2)
    private BigDecimal payoutGrossAmount;

    @Column(name = "payout_fee_amount", precision = 14, scale = 2)
    private BigDecimal payoutFeeAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_id")
    private User confirmedBy;

    @Column(name = "bank_code_snapshot", length = 20)
    private String bankCodeSnapshot;

    @Column(name = "bank_name_snapshot", length = 150)
    private String bankNameSnapshot;

    @Column(name = "bank_account_no_snapshot", length = 255)
    private String bankAccountNoSnapshot;

    @Column(name = "bank_account_holder_snapshot", length = 150)
    private String bankAccountHolderSnapshot;

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
            status = PaymentStatus.PENDING;
        }
        if (verificationMethod == null) {
            verificationMethod = PaymentVerificationMethod.PROVIDER_CONFIRMED;
        }
        if (appliedAmount == null) {
            appliedAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
