package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.booking.service.BookingPaymentLedgerService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.entity.RefundRequest;
import com.fptufa26.ecomotion.finance.enums.RefundStatus;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.repository.RefundRequestRepository;
import com.fptufa26.ecomotion.finance.service.RefundMath;
import com.fptufa26.ecomotion.finance.service.gateway.implement.VnpayPaymentGatewayStrategy;
import com.fptufa26.ecomotion.finance.service.gateway.implement.VnpayPaymentGatewayStrategy.VnpayRefundCallResult;
import com.fptufa26.ecomotion.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundEventService {

    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final BookingPaymentLedgerService ledger;
    private final RefundRequestRepository refundRequestRepository;
    private final VnpayPaymentGatewayStrategy vnpayPaymentGatewayStrategy;
    private final VnpayConfig vnpayConfig;
    private final BookingNotificationService bookingNotificationService;
    private final Clock clock;
    private final ObjectProvider<RefundEventService> self;

    @Transactional
    public List<RefundRequest> reserve(Long bookingId, String sourceEventId, BigDecimal amount, String reason, User actor) {
        return reserve(bookingId, sourceEventId, amount, reason, actor, null);
    }

    @Transactional
    public List<RefundRequest> reserveOnTransaction(Long bookingId, String sourceEventId, BigDecimal amount,
                                                    String reason, User actor, Long sourceTransactionId) {
        return reserve(bookingId, sourceEventId, amount, reason, actor, sourceTransactionId);
    }

    @Transactional(readOnly = true)
    public BigDecimal openAllocated(Long bookingId, BigDecimal obligation) {
        List<RefundRequest> requests = refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        List<PaymentTransaction> transactions = ledger.listTransactions(bookingId);
        BigDecimal allocated = transactions.stream()
                .filter(this::paidIn)
                .map(this::appliedPortion)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = allocatedRefundSum(requests, transactions, true);
        BigDecimal reserved = allocatedRefundSum(requests, transactions, false);
        return RefundMath.openAllocated(allocated, obligation, refunded, reserved);
    }

    @Transactional(readOnly = true)
    public BigDecimal remainingReceived(Long bookingId) {
        List<RefundRequest> requests = refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        List<PaymentTransaction> transactions = ledger.listTransactions(bookingId);
        BigDecimal received = transactions.stream().filter(this::paidIn).map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserved = requests.stream()
                .filter(refund -> RefundMath.RESERVED.contains(refund.getStatus()))
                .map(RefundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal legacy = unlinkedRefundOuts(transactions, requests).stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return RefundMath.money(received).subtract(reserved).subtract(legacy).max(BigDecimal.ZERO);
    }

    private List<RefundRequest> reserve(Long bookingId, String sourceEventId, BigDecimal amount, String reason,
                                        User actor, Long onlyTransactionId) {
        if (sourceEventId == null || sourceEventId.isBlank() || sourceEventId.contains(":TX:")) {
            throw new ConflictException("Mã sự kiện hoàn tiền không hợp lệ");
        }
        Booking booking = ledger.requireBookingForUpdate(bookingId);
        BigDecimal target = RefundMath.money(amount);
        if (target.signum() <= 0) {
            return List.of();
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new InvalidPaymentOperationException("Lý do hoàn tiền phải từ 1 đến 500 ký tự");
        }
        List<RefundRequest> existing = refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId).stream()
                .filter(refund -> belongsToEvent(refund, sourceEventId))
                .filter(refund -> refund.getStatus() != RefundStatus.REJECTED && refund.getStatus() != RefundStatus.FAILED)
                .toList();
        BigDecimal existingSum = existing.stream().map(RefundRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!existing.isEmpty()) {
            if (existingSum.compareTo(target) != 0) {
                throw new ConflictException("Sự kiện hoàn tiền đã tồn tại với số tiền khác");
            }
            return existing;
        }

        List<RefundRequest> all = refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(bookingId);
        List<PaymentTransaction> transactions = ledger.listTransactions(bookingId);
        boolean payoutHeld = transactions.stream()
                .anyMatch(tx -> tx.getType() == PaymentType.PAYOUT && tx.getStatus() != PaymentStatus.FAILED);
        List<PaymentTransaction> sources = transactions.stream()
                .filter(this::paidIn)
                .filter(tx -> onlyTransactionId == null || tx.getId().equals(onlyTransactionId))
                .sorted(sourceOrder(sourceEventId))
                .toList();
        List<PaymentTransaction> legacy = new ArrayList<>(unlinkedRefundOuts(transactions, all));
        PaymentTransaction adoptedWhole = legacy.stream()
                .filter(tx -> RefundMath.money(tx.getAmount()).compareTo(target) == 0)
                .findFirst()
                .orElse(null);
        if (adoptedWhole != null) {
            legacy.remove(adoptedWhole);
        }
        List<RefundRequest> created = new ArrayList<>();
        BigDecimal left = target;
        BigDecimal legacyPool = legacy.stream().map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (adoptedWhole != null) {
            PaymentTransaction source = sources.stream()
                    .filter(tx -> room(tx, all).compareTo(target) >= 0)
                    .findFirst()
                    .orElse(null);
            if (source == null) {
                throw new InvalidPaymentOperationException("Không còn giao dịch gốc đủ số tiền để hoàn");
            }
            created.add(persist(booking, source, adoptedWhole, target, sourceEventId, reason.trim(), actor, payoutHeld));
            left = BigDecimal.ZERO;
        }
        for (PaymentTransaction source : sources) {
            if (left.signum() <= 0) {
                break;
            }
            BigDecimal room = room(source, all);
            BigDecimal blocked = room.min(legacyPool);
            legacyPool = legacyPool.subtract(blocked);
            BigDecimal free = room.subtract(blocked);
            if (free.signum() <= 0) {
                continue;
            }
            BigDecimal slice = free.min(left);
            PaymentTransaction out = legacy.stream()
                    .filter(tx -> RefundMath.money(tx.getAmount()).compareTo(slice) == 0)
                    .findFirst()
                    .orElse(null);
            if (out != null) {
                legacy.remove(out);
            }
            created.add(persist(booking, source, out, slice, sourceEventId, reason.trim(), actor, payoutHeld));
            left = left.subtract(slice);
        }
        if (left.signum() > 0) {
            throw new InvalidPaymentOperationException("Số tiền hoàn vượt số tiền còn lại của giao dịch gốc");
        }
        List<Long> gatewayIds = created.stream()
                .filter(refund -> refund.getStatus() == RefundStatus.PROCESSING && refund.getVnpRequestId() != null)
                .map(RefundRequest::getId)
                .toList();
        if (!gatewayIds.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    gatewayIds.forEach(id -> self.getObject().submitGateway(id));
                }
            });
        }
        bookingNotificationService.changed(booking, "REFUND_UPDATED");
        return created;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitGateway(Long refundId) {
        RefundRequest refund = refundRequestRepository.findById(refundId).orElse(null);
        if (refund == null || refund.getStatus() != RefundStatus.PROCESSING || refund.getVnpRequestId() == null
                || refund.getSourcePaymentTransaction() == null || !vnpayConfig.isRefundEnabled()) {
            return;
        }
        PaymentTransaction source = refund.getSourcePaymentTransaction();
        if (source.getMethod() != PaymentMethod.VNPAY || source.getVnpTxnRef() == null) {
            return;
        }
        BigDecimal already = refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(refund.getBooking().getId()).stream()
                .filter(other -> other.getSourcePaymentTransaction() != null
                        && other.getSourcePaymentTransaction().getId().equals(source.getId()))
                .filter(other -> !other.getId().equals(refund.getId()))
                .filter(other -> RefundMath.RESERVED.contains(other.getStatus()))
                .map(RefundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String type = RefundMath.vnpayRefundType(refund.getAmount(), source.getAmount(), already);
        String payDate = source.getVnpPayDate() != null && source.getVnpPayDate().length() >= 14
                ? source.getVnpPayDate().substring(0, 14)
                : VNP_DATE.format(source.getCreatedAt() == null ? clock.instant() : source.getCreatedAt());
        long minor;
        try {
            minor = refund.getAmount().movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new InvalidPaymentOperationException("Số tiền hoàn phải chẵn đến đồng để gửi sang VNPay");
        }
        VnpayRefundCallResult result = vnpayPaymentGatewayStrategy.refund(
                refund.getVnpRequestId(), source.getVnpTxnRef(), source.getVnpTransactionNo(), payDate, type, minor,
                "ecomotion", "Refund " + refund.getSourceEventId());
        boolean referenceOk = result.checksumValid() && !result.timedOut()
                && (result.txnRef() == null || source.getVnpTxnRef().equals(result.txnRef()));
        RefundStatus mapped = RefundMath.mapGatewayResult(result.responseCode(), result.transactionStatus(),
                referenceOk || result.timedOut(), result.timedOut());
        if (!referenceOk && !result.timedOut()) {
            mapped = RefundStatus.PENDING_RECONCILIATION;
        }
        refund.setVnpResponseCode(result.responseCode());
        refund.setVnpTransactionStatus(result.transactionStatus());
        refund.setRefundTransactionNo(result.refundTransactionNo());
        refund.setStatus(mapped);
        refund.setUpdatedAt(clock.instant());
        if (mapped == RefundStatus.REFUNDED && refund.getPaymentTransaction() != null) {
            PaymentTransaction out = refund.getPaymentTransaction();
            out.setStatus(PaymentStatus.PAID);
            out.setPaidAt(clock.instant());
            out.setGatewayRef(result.refundTransactionNo());
            ledger.saveTransaction(out);
            refund.setGatewayRefundRef(result.refundTransactionNo());
        }
        if (mapped == RefundStatus.FAILED && refund.getPaymentTransaction() != null) {
            refund.getPaymentTransaction().setStatus(PaymentStatus.FAILED);
            ledger.saveTransaction(refund.getPaymentTransaction());
        }
        refundRequestRepository.save(refund);
    }

    @Transactional
    public RefundRequest releaseForFollowUp(Long refundId, User actor, boolean manual) {
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new ConflictException("Không tìm thấy yêu cầu hoàn tiền"));
        ledger.requireBookingForUpdate(refund.getBooking().getId());
        if (refund.getStatus() != RefundStatus.UNDER_REVIEW && refund.getStatus() != RefundStatus.FAILED) {
            throw new ConflictException("Chỉ tiếp tục được yêu cầu đang đối soát hoặc đã thất bại được xác minh");
        }
        if (refund.getStatus() == RefundStatus.FAILED && refund.getVnpResponseCode() == null && refund.getVnpRequestId() != null) {
            throw new ConflictException("Kết quả hoàn tiền chưa được xác minh");
        }
        boolean hanging = refundRequestRepository.findByBooking_IdOrderByCreatedAtDesc(refund.getBooking().getId()).stream()
                .filter(other -> !other.getId().equals(refund.getId()))
                .filter(other -> refund.getSourcePaymentTransaction() == null || other.getSourcePaymentTransaction() == null
                        || other.getSourcePaymentTransaction().getId().equals(refund.getSourcePaymentTransaction().getId()))
                .anyMatch(other -> other.getStatus() == RefundStatus.PROCESSING
                        || other.getStatus() == RefundStatus.PENDING_RECONCILIATION);
        if (hanging) {
            throw new ConflictException("Còn yêu cầu hoàn trên cổng thanh toán chưa xác định kết quả");
        }
        PaymentMethod method = refund.getSourcePaymentTransaction() == null
                ? PaymentMethod.BANK_TRANSFER : refund.getSourcePaymentTransaction().getMethod();
        if (manual || method != PaymentMethod.VNPAY) {
            refund.setStatus(RefundStatus.PROCESSING);
            if (refund.getPaymentTransaction() != null) {
                refund.getPaymentTransaction().setMethod(PaymentMethod.BANK_TRANSFER);
                refund.getPaymentTransaction().setStatus(PaymentStatus.PENDING);
                ledger.saveTransaction(refund.getPaymentTransaction());
            }
        } else {
            if (refund.getVnpRequestId() == null) {
                refund.setVnpRequestId(newRequestId());
            }
            refund.setStatus(RefundStatus.PROCESSING);
            refundRequestRepository.save(refund);
            Long id = refund.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    self.getObject().submitGateway(id);
                }
            });
        }
        refund.setApprovedBy(actor);
        refund.setUpdatedAt(clock.instant());
        return refundRequestRepository.save(refund);
    }

    private RefundRequest persist(Booking booking, PaymentTransaction source, PaymentTransaction existingOut,
                                  BigDecimal slice, String sourceEventId, String reason, User actor, boolean payoutHeld) {
        PaymentTransaction out = existingOut == null ? newOut(booking, source, slice, reason) : existingOut;
        if (existingOut == null) {
            out = ledger.saveTransaction(out);
        }
        RefundRequest refund = new RefundRequest();
        refund.setBooking(booking);
        refund.setPaymentTransaction(out);
        refund.setSourcePaymentTransaction(source);
        refund.setAmount(RefundMath.money(slice));
        refund.setReason(reason);
        refund.setSourceEventId(RefundMath.partId(sourceEventId, source.getId()));
        refund.setRequestedBy(actor);
        refund.setCreatedAt(clock.instant());
        refund.setUpdatedAt(clock.instant());
        if (payoutHeld) {
            refund.setStatus(RefundStatus.UNDER_REVIEW);
        } else if (source.getMethod() == PaymentMethod.VNPAY) {
            refund.setStatus(RefundStatus.PROCESSING);
            refund.setVnpRequestId(newRequestId());
        } else {
            refund.setStatus(RefundStatus.PROCESSING);
        }
        return refundRequestRepository.save(refund);
    }

    private PaymentTransaction newOut(Booking booking, PaymentTransaction source, BigDecimal slice, String reason) {
        PaymentTransaction out = new PaymentTransaction();
        out.setBooking(booking);
        out.setType(PaymentType.REFUND);
        out.setDirection(PaymentDirection.OUT);
        out.setMethod(source.getMethod() == PaymentMethod.VNPAY ? PaymentMethod.VNPAY : PaymentMethod.BANK_TRANSFER);
        out.setAmount(RefundMath.money(slice));
        out.setAppliedAmount(BigDecimal.ZERO);
        out.setStatus(PaymentStatus.PENDING);
        out.setProcessingNote(reason);
        out.setCreatedAt(clock.instant());
        out.setUpdatedAt(clock.instant());
        return out;
    }

    private BigDecimal room(PaymentTransaction source, List<RefundRequest> requests) {
        BigDecimal used = requests.stream()
                .filter(refund -> refund.getSourcePaymentTransaction() != null
                        && refund.getSourcePaymentTransaction().getId().equals(source.getId()))
                .filter(refund -> RefundMath.RESERVED.contains(refund.getStatus()))
                .map(RefundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return RefundMath.money(source.getAmount()).subtract(used).max(BigDecimal.ZERO);
    }

    private BigDecimal allocatedRefundSum(List<RefundRequest> requests, List<PaymentTransaction> transactions, boolean refunded) {
        BigDecimal fromRequests = requests.stream()
                .filter(refund -> RefundMath.countsTowardAllocatedRefund(refund.getSourceEventId()))
                .filter(refund -> refunded ? refund.getStatus() == RefundStatus.REFUNDED
                        : RefundMath.RESERVED.contains(refund.getStatus()) && refund.getStatus() != RefundStatus.REFUNDED)
                .map(RefundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fromLegacy = unlinkedRefundOuts(transactions, requests).stream()
                .filter(tx -> !legacyExcess(tx))
                .filter(tx -> refunded ? tx.getStatus() == PaymentStatus.PAID : tx.getStatus() == PaymentStatus.PENDING)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return fromRequests.add(fromLegacy);
    }

    private List<PaymentTransaction> unlinkedRefundOuts(List<PaymentTransaction> transactions, List<RefundRequest> requests) {
        List<Long> linked = requests.stream()
                .map(RefundRequest::getPaymentTransaction)
                .filter(Objects::nonNull)
                .map(PaymentTransaction::getId)
                .toList();
        return transactions.stream()
                .filter(tx -> tx.getType() == PaymentType.REFUND && tx.getDirection() == PaymentDirection.OUT)
                .filter(tx -> tx.getStatus() == PaymentStatus.PENDING || tx.getStatus() == PaymentStatus.PAID)
                .filter(tx -> tx.getId() != null && !linked.contains(tx.getId()))
                .toList();
    }

    private boolean legacyExcess(PaymentTransaction transaction) {
        String note = transaction.getProcessingNote();
        return note != null && (note.contains("thừa") || note.contains("EXCESS") || note.startsWith("LATE_IPN"));
    }

    private boolean paidIn(PaymentTransaction transaction) {
        return transaction.getStatus() == PaymentStatus.PAID && transaction.getDirection() == PaymentDirection.IN;
    }

    private BigDecimal appliedPortion(PaymentTransaction transaction) {
        return RefundMath.appliedPortion(transaction.getAmount(), transaction.getAppliedAmount(), transaction.getProcessingNote());
    }

    private boolean belongsToEvent(RefundRequest refund, String sourceEventId) {
        String id = refund.getSourceEventId();
        return sourceEventId.equals(id) || (id != null && id.startsWith(sourceEventId + ":TX:"));
    }

    private Comparator<PaymentTransaction> sourceOrder(String sourceEventId) {
        boolean depositFirst = sourceEventId.startsWith("CANCEL:") || sourceEventId.startsWith("PROVIDER_CANCEL:")
                || sourceEventId.startsWith("NO_DRIVER:");
        return (left, right) -> {
            int rank = Integer.compare(rank(left, depositFirst), rank(right, depositFirst));
            if (rank != 0) {
                return rank;
            }
            return depositFirst ? Long.compare(left.getId(), right.getId()) : Long.compare(right.getId(), left.getId());
        };
    }

    private int rank(PaymentTransaction transaction, boolean depositFirst) {
        boolean deposit = transaction.getType() == PaymentType.DEPOSIT_PAY;
        if (depositFirst) {
            return deposit ? 0 : 1;
        }
        return deposit ? 1 : 0;
    }

    private String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
