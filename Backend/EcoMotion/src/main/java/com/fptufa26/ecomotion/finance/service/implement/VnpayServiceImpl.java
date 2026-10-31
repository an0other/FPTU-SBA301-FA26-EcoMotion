package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.booking.dto.response.BookingChargeLineResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingCharge;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.ChargeType;
import com.fptufa26.ecomotion.booking.enums.PaymentDirection;
import com.fptufa26.ecomotion.booking.enums.PaymentMethod;
import com.fptufa26.ecomotion.booking.enums.PaymentStatus;
import com.fptufa26.ecomotion.booking.enums.PaymentType;
import com.fptufa26.ecomotion.booking.service.AvailabilityPolicy;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.booking.service.DriverOptimizationService;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest;
import com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayCallbackResultResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayIpnResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.service.PaymentCodeGenerator;
import com.fptufa26.ecomotion.finance.service.VnpayService;
import com.fptufa26.ecomotion.finance.service.gateway.PaymentGatewayResolver;
import com.fptufa26.ecomotion.finance.service.gateway.PaymentGatewayStrategy;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlCommand;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentUrlResult;
import com.fptufa26.ecomotion.finance.service.gateway.dto.PaymentVerificationResult;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnpayServiceImpl implements VnpayService {

    private final PaymentGatewayResolver paymentGatewayResolver;
    private final BookingQueryService bookingQueryService;
    private final BookingNotificationService bookingNotificationService;
    private final PaymentRequestRepository paymentRequestRepository;
    private final DisputeService disputeService;
    private final UserService userService;
    private final VehicleService vehicleService;
    private final ScheduleService scheduleService;
    private final DriverOptimizationService driverOptimizationService;
    private final PaymentCodeGenerator paymentCodeGenerator;
    private final Clock clock;
    private final VnpayConfig vnpayConfig;
    private final PlatformTransactionManager transactionManager;

    @Override
    @Transactional
    public VnpayPaymentUrlResponse createPaymentUrl(Long currentUserId, CreateVnpayPaymentUrlRequest request, String clientIp) {
        Booking booking = bookingQueryService.lockBookingForPayment(resolveBooking(request.bookingCode()).getId());

        validateRenterAccess(booking, currentUserId);

        boolean isFinalPayment = "FINAL_PAYMENT".equalsIgnoreCase(request.paymentType())
                || "FINAL_BALANCE".equalsIgnoreCase(request.paymentType())
                || "SETTLEMENT".equalsIgnoreCase(request.paymentType());

        BigDecimal amount;
        PaymentType paymentType;
        if (!isFinalPayment) {
            boolean alreadyPaid = bookingQueryService.findTransactionsByBookingId(booking.getId()).stream()
                    .anyMatch(tx -> tx.getType() == PaymentType.DEPOSIT_PAY && tx.getStatus() == PaymentStatus.PAID);
            if (alreadyPaid || booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.DRIVER_ASSIGNED
                    || booking.getStatus() == BookingStatus.DRIVER_CONFIRMED) {
                throw new InvalidPaymentOperationException("Tiền đặt cọc cho đơn thuê này đã được thanh toán thành công");
            }
            if (booking.getStatus() != BookingStatus.ACCEPTED
                    && booking.getStatus() != BookingStatus.AWAITING_DEPOSIT) {
                throw new InvalidPaymentOperationException("Chỉ có thể đặt cọc khi đơn thuê đang chờ cọc hoặc đã chấp nhận (Hiện tại: " + booking.getStatus() + ")");
            }

            Optional<PaymentRequest> depositPr = paymentRequestRepository
                    .findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT);
            if (depositPr.isPresent() && depositPr.get().getExpiresAt() != null
                    && !depositPr.get().getExpiresAt().isAfter(clock.instant())) {
                throw new InvalidPaymentOperationException("Thời hạn thanh toán tiền cọc cho đơn thuê này đã hết hạn");
            }
            if (booking.getPickupTime() != null && booking.getPickupTime().isBefore(clock.instant())) {
                throw new InvalidPaymentOperationException("Thời hạn đón xe đã trôi qua, không thể thanh toán đặt cọc");
            }

            if (booking.getStatus() == BookingStatus.ACCEPTED) {
                Vehicle vehicle = vehicleService.findVehicleByIdForUpdate(booking.getVehicle().getId()).orElseThrow();
                int minutes = requireSnapshot(booking).getSlotLengthMinutes();
                int quantity = bookingQueryService.findChargeByBookingIdAndType(booking.getId(), ChargeType.BASE_RENTAL)
                        .map(BookingCharge::getQuantity).orElse(1);
                Instant end = booking.getPickupTime().plus(Duration.ofMinutes((long) minutes * quantity));
                var window = AvailabilityPolicy.bufferedWindow(vehicle, booking.getPickupTime(), end);
                if (!scheduleService.isRangeFree(vehicle.getId(), window.start(), window.end())) {
                    throw new InvalidPaymentOperationException("Xe không còn trống, không thể giữ chỗ để thanh toán");
                }
                scheduleService.hold(vehicle, booking, window.start(), window.end(), clock.instant().plus(Duration.ofMinutes(15)));
                booking.setStatus(BookingStatus.AWAITING_DEPOSIT);
                bookingQueryService.saveBooking(booking);
            }
            if (!scheduleService.hasUnexpiredHold(booking.getId(), clock.instant())) {
                throw new InvalidPaymentOperationException("Lịch giữ chỗ đã hết hạn hoặc không còn hợp lệ; cần đối soát trước khi thanh toán");
            }

            amount = calculateDepositAmount(booking);
            paymentType = PaymentType.DEPOSIT_PAY;
        } else {
            // Check if final payment is already settled or booking already completed (TC06)
            boolean alreadySettled = bookingQueryService.findTransactionsByBookingId(booking.getId()).stream()
                    .anyMatch(tx -> tx.getType() == PaymentType.SETTLEMENT && tx.getStatus() == PaymentStatus.PAID);
            if (alreadySettled || booking.getStatus() == BookingStatus.COMPLETED) {
                throw new InvalidPaymentOperationException("Số dư quyết toán của đơn thuê này đã được thanh toán hoàn tất");
            }

            // Business rule: Trip must be finished (TC02)
            if (booking.getActualEndAt() == null && booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT) {
                throw new InvalidPaymentOperationException("Chuyến đi chưa kết thúc, không thể thực hiện quyết toán số dư");
            }

            // Eligible status check (TC14)
            if (booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT) {
                throw new InvalidPaymentOperationException("Đơn thuê chưa ở trạng thái chờ quyết toán số dư (Hiện tại: " + booking.getStatus() + ")");
            }

            // Check active disputes (TC14)
            if (disputeService.hasActiveDispute(booking.getId())) {
                throw new InvalidPaymentOperationException("Không thể quyết toán khi đơn thuê đang có tranh chấp khiếu nại chưa giải quyết");
            }

            amount = calculateFinalPayable(booking);

            // If final payable is 0, complete obligations immediately without creating 0 VND VNPay txn (TC09)
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingQueryService.saveBooking(booking);
                paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                        .ifPresent(pr -> {
                            pr.setStatus(PaymentRequestStatus.CONFIRMED);
                            pr.setPaidAmount(pr.getAmount());
                            pr.setConfirmedAt(clock.instant());
                            paymentRequestRepository.save(pr);
                        });
                log.info("Booking {} final payable is 0. Completed directly without VNPay gateway call.", booking.getBookingCode());
                notifyPaymentAfterCommit(booking);
                return new VnpayPaymentUrlResponse(null, null, BigDecimal.ZERO, booking.getBookingCode(), clock.instant());
            }

            paymentType = PaymentType.SETTLEMENT;
        }

        paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(),
                        isFinalPayment ? PaymentRequestType.FINAL_BALANCE : PaymentRequestType.DEPOSIT)
                .ifPresent(pr -> {
                    if (pr.getStatus() != PaymentRequestStatus.PENDING && pr.getStatus() != PaymentRequestStatus.REJECTED
                            || pr.getPaidAmount() != null && pr.getPaidAmount().signum() > 0
                            || pr.getExpiresAt() != null && !pr.getExpiresAt().isAfter(clock.instant())) {
                        throw new InvalidPaymentOperationException("Yêu cầu thanh toán đang đối soát, đã thu tiền hoặc đã kết thúc; không thể tạo giao dịch VNPay mới");
                    }
                });
        Instant now = clock.instant();
        var pending = bookingQueryService.findTransactionsByBookingId(booking.getId()).stream()
                .filter(tx -> tx.getType() == paymentType && tx.getMethod() == PaymentMethod.VNPAY
                        && tx.getStatus() == PaymentStatus.PENDING).findFirst();
        if (pending.isPresent() && (pending.get().getCreatedAt() == null
                || !pending.get().getCreatedAt().plus(Duration.ofMinutes(15)).isAfter(now)
                || pending.get().getAmount().compareTo(amount) != 0)) {
            throw new InvalidPaymentOperationException("Giao dịch trước đang chờ đối soát. Vui lòng kiểm tra kết quả trước khi thanh toán lại.");
        }
        PaymentTransaction transaction = bookingQueryService.findTransactionsByBookingId(booking.getId()).stream()
                .filter(tx -> tx.getType() == paymentType
                        && tx.getStatus() == PaymentStatus.PENDING
                        && tx.getMethod() == PaymentMethod.VNPAY
                        && tx.getVnpTxnRef() != null
                        && tx.getAmount() != null
                        && tx.getAmount().compareTo(amount) == 0
                        && tx.getCreatedAt() != null
                        && tx.getCreatedAt().isAfter(now.minus(Duration.ofMinutes(15))))
                .max(Comparator.comparing(PaymentTransaction::getCreatedAt))
                .orElseGet(() -> {
                    String prefix = isFinalPayment ? "VNP" + booking.getBookingCode() + "F" : "VNP" + booking.getBookingCode();
                    String vnpTxnRef = prefix + UUID.randomUUID().toString().replace("-", "");
                    PaymentTransaction newTx = new PaymentTransaction();
                    newTx.setBooking(booking);
                    newTx.setType(paymentType);
                    newTx.setDirection(PaymentDirection.IN);
                    newTx.setMethod(PaymentMethod.VNPAY);
                    newTx.setAmount(amount);
                    newTx.setStatus(PaymentStatus.PENDING);
                    newTx.setVerificationMethod(PaymentVerificationMethod.MANUAL_UNVERIFIED);
                    newTx.setTransactionCode(paymentCodeGenerator.generatePaymentCode("VP"));
                    newTx.setVnpTxnRef(vnpTxnRef);
                    newTx.setGatewayRef(vnpTxnRef);
                    newTx.setCreatedAt(now);
                    newTx.setUpdatedAt(now);
                    return bookingQueryService.saveTransaction(newTx);
                });

        String vnpTxnRef = transaction.getVnpTxnRef();
        Instant expireTime = transaction.getCreatedAt().plus(Duration.ofMinutes(15));
        PaymentGatewayStrategy gateway = paymentGatewayResolver.resolve(PaymentMethod.VNPAY);

        PaymentUrlCommand command = new PaymentUrlCommand(
                booking.getBookingCode(),
                vnpTxnRef,
                amount,
                (isFinalPayment ? "Quyet toan don thue " : "Dat coc thue xe ") + booking.getBookingCode(),
                clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1",
                request.language() != null && !request.language().isBlank() ? request.language() : "vn",
                request.bankCode(),
                transaction.getCreatedAt(),
                expireTime
        );

        PaymentUrlResult urlResult = gateway.generatePaymentUrl(command);
        return new VnpayPaymentUrlResponse(urlResult.paymentUrl(), urlResult.txnRef(), amount, booking.getBookingCode(), expireTime);
    }

    @Override
    @Transactional(readOnly = true)
    public RenterSettlementResponse getSettlementDetails(Long currentUserId, String bookingCodeOrId) {
        Booking booking = resolveBooking(bookingCodeOrId);
        validateAccessToBooking(booking, currentUserId);

        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        List<BookingCharge> charges = bookingQueryService.findChargesByBookingIdOrderByCreatedAtAsc(booking.getId());
        List<BookingChargeLineResponse> chargeLines = charges.stream()
                .filter(c -> !c.isWaived() && c.getChargeType() != ChargeType.DEPOSIT)
                .map(c -> new BookingChargeLineResponse(
                        c.getId(),
                        c.getChargeType(),
                        c.getDescription(),
                        c.getQuantity(),
                        c.getUnitPrice(),
                        c.getAppliedRate(),
                        c.getAmount(),
                        c.isWaived(),
                        c.isWaived() ? BigDecimal.ZERO : c.getAmount()
                ))
                .toList();

        BigDecimal extraCharges = charges.stream()
                .filter(c -> !c.isWaived()
                        && c.getChargeType() != ChargeType.DEPOSIT
                        && c.getChargeType() != ChargeType.BASE_RENTAL)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalObligation = baseRental.add(extraCharges);

        List<PaymentTransaction> transactions = bookingQueryService.findTransactionsByBookingId(booking.getId());
        BigDecimal depositPaid = transactions.stream()
                .filter(t -> t.getType() == PaymentType.DEPOSIT_PAY && t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal otherPaid = transactions.stream()
                .filter(t -> t.getType() != PaymentType.DEPOSIT_PAY && t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = depositPaid.add(otherPaid);

        BigDecimal refunds = transactions.stream()
                .filter(t -> t.getType() == PaymentType.REFUND && t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.OUT)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPaid = totalPaid.subtract(refunds);
        BigDecimal netBalance = totalObligation.subtract(netPaid);
        BigDecimal finalPayable = netBalance.signum() > 0 ? netBalance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        BigDecimal overpayment = netBalance.signum() < 0 ? netBalance.abs().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        boolean hasDispute = disputeService.hasActiveDispute(booking.getId());
        boolean isSettled = booking.getStatus() == BookingStatus.COMPLETED
                || transactions.stream().anyMatch(t -> t.getType() == PaymentType.SETTLEMENT && t.getStatus() == PaymentStatus.PAID);
        boolean eligibleForPayment = booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT && !isSettled && finalPayable.signum() > 0 && !hasDispute;

        String message;
        if (isSettled) {
            message = "Đơn thuê đã hoàn tất quyết toán.";
        } else if (hasDispute) {
            message = "Đơn thuê đang có khiếu nại tranh chấp, tạm thời chưa thể quyết toán.";
        } else if (booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT) {
            message = "Đơn thuê chưa ở trạng thái chờ thanh toán quyết toán.";
        } else if (finalPayable.signum() == 0) {
            message = "Số dư thanh toán bằng 0 ₫. Bạn không cần thanh toán thêm.";
        } else {
            message = "Vui lòng thanh toán số dư qua cổng VNPAY Sandbox để hoàn tất đơn thuê.";
        }

        return new RenterSettlementResponse(
                booking.getBookingCode(),
                booking.getStatus(),
                baseRental,
                chargeLines,
                extraCharges,
                depositPaid,
                otherPaid,
                totalPaid,
                refunds,
                finalPayable,
                overpayment,
                eligibleForPayment,
                isSettled,
                message
        );
    }

    @Override
    @Transactional
    public VnpayPaymentUrlResponse createFinalPaymentUrl(Long currentUserId, String bookingCodeOrId, String clientIp) {
        return createPaymentUrl(currentUserId, new CreateVnpayPaymentUrlRequest(bookingCodeOrId, "FINAL_PAYMENT", null, "vn"), clientIp);
    }

    @Override
    @Transactional
    public VnpayIpnResponse processIpn(Map<String, String> params) {
        PaymentGatewayStrategy gateway = paymentGatewayResolver.resolve(PaymentMethod.VNPAY);
        PaymentVerificationResult verification = gateway.verifyIpn(params);

        if (!verification.validSignature()) {
            return new VnpayIpnResponse("97", "Invalid Checksum");
        }

        String vnpTxnRef = verification.txnRef();
        if (vnpTxnRef == null || vnpTxnRef.isBlank()) {
            return new VnpayIpnResponse("01", "Order not found");
        }

        PaymentTransaction candidate = bookingQueryService.findTransactionByVnpTxnRef(vnpTxnRef).orElse(null);
        if (candidate == null) return new VnpayIpnResponse("01", "Order not found");
        bookingQueryService.lockBookingForPayment(candidate.getBooking().getId());
        PaymentTransaction transaction = bookingQueryService.findTransactionByVnpTxnRefForUpdate(vnpTxnRef).orElse(null);
        if (transaction == null) {
            return new VnpayIpnResponse("01", "Order not found");
        }

        return applyVerifiedPayment(transaction, verification, params);
    }

    private VnpayIpnResponse applyVerifiedPayment(PaymentTransaction transaction,
            PaymentVerificationResult verification, Map<String, String> params) {
        String vnpAmountStr = params.get("vnp_Amount");
        try {
            if (vnpAmountStr == null || !vnpAmountStr.matches("[0-9]+") || new BigDecimal(vnpAmountStr).compareTo(transaction.getAmount().movePointRight(2)) != 0)
                return new VnpayIpnResponse("04", "Invalid Amount");
        } catch (NumberFormatException exception) {
            return new VnpayIpnResponse("04", "Invalid Amount");
        }

        if (transaction.getStatus() == PaymentStatus.PAID || transaction.getStatus() == PaymentStatus.FAILED) {
            return new VnpayIpnResponse("02", "Order already confirmed");
        }

        if (transaction.getMethod() != PaymentMethod.VNPAY || transaction.getDirection() != PaymentDirection.IN) {
            return new VnpayIpnResponse("01", "Order not found");
        }
        String responseCode = verification.responseCode();
        boolean success = verification.success();
        if (!success && !"02".equals(params.get("vnp_TransactionStatus"))) {
            return new VnpayIpnResponse("99", "Transaction requires reconciliation");
        }

        transaction.setVnpTransactionNo(verification.gatewayTransactionNo());
        transaction.setVnpBankCode(verification.bankCode());
        transaction.setVnpCardType(verification.cardType());
        transaction.setVnpPayDate(verification.payDate());
        transaction.setVnpResponseCode(responseCode);
        transaction.setUpdatedAt(clock.instant());

        if (!success) {
            transaction.setStatus(PaymentStatus.FAILED);
            transaction.setVerificationMethod(PaymentVerificationMethod.GATEWAY_VERIFIED);
            bookingQueryService.saveTransaction(transaction);
            return new VnpayIpnResponse("00", "Confirm Success");
        }

        Booking booking = transaction.getBooking();
        boolean alreadyPaid = bookingQueryService.findTransactionsByBookingId(booking.getId()).stream()
                .anyMatch(tx -> !tx.getId().equals(transaction.getId())
                        && tx.getType() == transaction.getType() && tx.getDirection() == PaymentDirection.IN
                        && tx.getStatus() == PaymentStatus.PAID);
        if (alreadyPaid) {
            return recordPaymentForReconciliation(transaction, "Another payment already covers this obligation");
        }
        if (transaction.getType() == PaymentType.DEPOSIT_PAY) {
            if (booking.getStatus() == BookingStatus.ACCEPTED
                    || booking.getStatus() == BookingStatus.AWAITING_DEPOSIT) {
                Vehicle vehicle = vehicleService.findVehicleByIdForUpdate(booking.getVehicle().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Xe không tồn tại"));

                BookingPolicySnapshot snapshot = requireSnapshot(booking);
                int slotMinutes = snapshot.getSlotLengthMinutes();
                int slots = bookingQueryService.findChargeByBookingIdAndType(booking.getId(), ChargeType.BASE_RENTAL)
                        .map(BookingCharge::getQuantity).orElse(1);
                Instant end = booking.getPickupTime().plus(Duration.ofMinutes((long) slotMinutes * slots));
                AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(vehicle, booking.getPickupTime(), end);

                if (booking.getStatus() != BookingStatus.AWAITING_DEPOSIT
                        && !scheduleService.isRangeFree(vehicle.getId(), window.start(), window.end())) {
                    return recordPaymentForReconciliation(transaction, "Slot conflict after gateway payment");
                }

                if (booking.getStatus() == BookingStatus.AWAITING_DEPOSIT
                        && !scheduleService.confirmUnexpiredHold(booking.getId(), clock.instant())) {
                    return recordPaymentForReconciliation(transaction, "Hold expired, missing or slot no longer available");
                }

                BookingCharge depositCharge = bookingQueryService.findChargeByBookingIdAndType(booking.getId(), ChargeType.DEPOSIT)
                        .orElseGet(() -> {
                            BookingCharge c = new BookingCharge();
                            c.setBooking(booking);
                            c.setChargeType(ChargeType.DEPOSIT);
                            c.setDescription("Cọc giữ chỗ qua VNPAY");
                            c.setUnitPrice(booking.getTotalAmount());
                            c.setAppliedRate(snapshot.getDepositRatio());
                            c.setAmount(transaction.getAmount());
                            c.setWaived(false);
                            c.setCreatedBy(booking.getRenter().getUser());
                            return bookingQueryService.saveCharge(c);
                        });

                transaction.setCharge(depositCharge);
                transaction.setStatus(PaymentStatus.PAID);
                transaction.setAppliedAmount(transaction.getAmount());
                transaction.setVerificationMethod(PaymentVerificationMethod.GATEWAY_VERIFIED);
                transaction.setPaidAt(clock.instant());
                bookingQueryService.saveTransaction(transaction);

                if (booking.getStatus() != BookingStatus.AWAITING_DEPOSIT) {
                    scheduleService.book(vehicle, booking, booking.getPickupTime(), end);
                }
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingQueryService.saveBooking(booking);
                notifyPaymentAfterCommit(booking);
                bookingNotificationService.depositPaid(booking, transaction.getAmount());

                paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT)
                        .ifPresent(pr -> {
                            pr.setStatus(PaymentRequestStatus.CONFIRMED);
                            pr.setPaidAmount(transaction.getAmount());
                            transaction.setPaymentRequest(pr);
                            pr.setConfirmedAt(clock.instant());
                            paymentRequestRepository.save(pr);
                        });

                if (booking.getDriver() == null && booking.getAssignmentMode() == com.fptufa26.ecomotion.booking.enums.AssignmentMode.FULL_AUTO)
                    assignDriverAfterCommit(booking.getId());
            } else {
                return recordPaymentForReconciliation(transaction, "Booking state not eligible after gateway payment");
            }
        } else if (transaction.getType() == PaymentType.SETTLEMENT) {
            if (booking.getStatus() != BookingStatus.AWAITING_FINAL_PAYMENT
                    || disputeService.hasActiveDispute(booking.getId())
                    || transaction.getAmount().compareTo(calculateFinalPayable(booking)) != 0) {
                return recordPaymentForReconciliation(transaction, "Settlement no longer eligible or dispute is active");
            }
            transaction.setStatus(PaymentStatus.PAID);
            transaction.setAppliedAmount(transaction.getAmount());
            transaction.setVerificationMethod(PaymentVerificationMethod.GATEWAY_VERIFIED);
            transaction.setPaidAt(clock.instant());
            bookingQueryService.saveTransaction(transaction);
            if (booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingQueryService.saveBooking(booking);
            }
            paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                    .ifPresent(pr -> {
                        pr.setStatus(PaymentRequestStatus.CONFIRMED);
                        pr.setPaidAmount(transaction.getAmount());
                        pr.setConfirmedAt(clock.instant());
                        transaction.setPaymentRequest(pr);
                        paymentRequestRepository.save(pr);
                    });
            notifyPaymentAfterCommit(booking);
        }

        return new VnpayIpnResponse("00", "Confirm Success");
    }

    private VnpayIpnResponse recordPaymentForReconciliation(PaymentTransaction transaction, String reason) {
        transaction.setStatus(PaymentStatus.PAID);
        transaction.setAppliedAmount(BigDecimal.ZERO);
        transaction.setPaidAt(clock.instant());
        transaction.setVerificationMethod(PaymentVerificationMethod.GATEWAY_VERIFIED);
        transaction.setProcessingNote("RECONCILIATION_REQUIRED: " + reason);
        bookingQueryService.saveTransaction(transaction);
        Booking booking = transaction.getBooking();
        if (transaction.getType() == PaymentType.DEPOSIT_PAY
                && Set.of(BookingStatus.ACCEPTED, BookingStatus.AWAITING_DEPOSIT, BookingStatus.PENDING).contains(booking.getStatus())) {
            booking.setStatus(BookingStatus.CANCELLATION_REVIEW);
            bookingQueryService.saveBooking(booking);
            scheduleService.release(booking.getId());
        }
        paymentRequestRepository.findByBooking_IdAndPaymentType(transaction.getBooking().getId(),
                transaction.getType() == PaymentType.DEPOSIT_PAY ? PaymentRequestType.DEPOSIT : PaymentRequestType.FINAL_BALANCE)
                .filter(pr -> pr.getStatus() != PaymentRequestStatus.CONFIRMED)
                .ifPresent(pr -> {
                    pr.setStatus(PaymentRequestStatus.REVIEW_REQUIRED);
                    pr.setIssueNote(transaction.getProcessingNote());
                    paymentRequestRepository.save(pr);
                });
        bookingNotificationService.changed(transaction.getBooking(), "PAYMENT_RECONCILIATION_REQUIRED");
        return new VnpayIpnResponse("00", "Payment recorded, reconciliation required");
    }

    private void notifyPaymentAfterCommit(Booking booking) {
        bookingNotificationService.changed(booking, "PAYMENT_VERIFIED");
    }

    private void assignDriverAfterCommit(Long bookingId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            var template = new TransactionTemplate(transactionManager);
                            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                            template.executeWithoutResult(status -> driverOptimizationService.autoAssign(bookingId));
                        } catch (Exception ex) {
                            log.warn("Auto assignment post-payment failed for booking {}: {}", bookingId, ex.getClass().getSimpleName());
                        }
                    }
                });
    }

    private BookingPolicySnapshot requireSnapshot(Booking booking) {
        return bookingQueryService.findPolicySnapshotByBookingId(booking.getId())
                .orElseThrow(() -> new InvalidPaymentOperationException("Đơn thuê thiếu chính sách giá đã chốt, cần đối soát"));
    }

    @Override
    @Transactional
    public VnpayCallbackResultResponse processCallback(Map<String, String> params) {
        PaymentVerificationResult verification = paymentGatewayResolver.resolve(PaymentMethod.VNPAY).verifyCallback(params);
        if (!verification.validSignature()) {
            return invalidCallback("Chữ ký hoặc thông tin merchant không hợp lệ");
        }
        String vnpTxnRef = verification.txnRef();
        if (vnpTxnRef == null || vnpTxnRef.isBlank()) {
            return invalidCallback("Mã tham chiếu giao dịch không tồn tại");
        }
        PaymentTransaction candidate = bookingQueryService.findTransactionByVnpTxnRef(vnpTxnRef).orElse(null);
        if (candidate == null) return invalidCallback("Giao dịch không tồn tại");
        try {
            String amount = params.get("vnp_Amount");
            if (amount == null || !amount.matches("[0-9]+")
                    || new BigDecimal(amount).compareTo(candidate.getAmount().movePointRight(2)) != 0) {
                return invalidCallback("Số tiền giao dịch không khớp");
            }
        } catch (NumberFormatException ex) {
            return invalidCallback("Số tiền giao dịch không hợp lệ");
        }

        bookingQueryService.lockBookingForPayment(candidate.getBooking().getId());
        PaymentTransaction transaction = bookingQueryService.findTransactionByVnpTxnRefForUpdate(vnpTxnRef).orElse(candidate);

        if (transaction.getStatus() == PaymentStatus.PENDING) {
            applyVerifiedPayment(transaction, verification, params);
            transaction = bookingQueryService.findTransactionByVnpTxnRef(vnpTxnRef).orElse(transaction);
        }

        return transactionResult(transaction);
    }

    @Override
    @Transactional
    public VnpayCallbackResultResponse queryTransaction(String bookingCode, String vnpTxnRef) {
        PaymentTransaction candidate = bookingQueryService.findTransactionByVnpTxnRef(vnpTxnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));
        if (!candidate.getBooking().getBookingCode().equals(bookingCode)) {
            throw new ResourceNotFoundException("Giao dịch không thuộc đơn thuê");
        }
        bookingQueryService.lockBookingForPayment(candidate.getBooking().getId());
        PaymentTransaction transaction = bookingQueryService.findTransactionByVnpTxnRefForUpdate(vnpTxnRef).orElseThrow();
        Instant now = clock.instant();
        if (transaction.getStatus() == PaymentStatus.PENDING && transaction.getCreatedAt() != null
                && (transaction.getUpdatedAt() == null || !transaction.getUpdatedAt().plus(vnpayConfig.getQueryInterval()).isAfter(now))) {
            transaction.setUpdatedAt(now);
            bookingQueryService.saveTransaction(transaction);
            paymentGatewayResolver.resolve(PaymentMethod.VNPAY).queryTransaction(vnpTxnRef, transaction.getCreatedAt(), now)
                    .ifPresent(params -> {
                        boolean success = "00".equals(params.get("vnp_TransactionStatus"));
                        PaymentVerificationResult verification = new PaymentVerificationResult(true, success, vnpTxnRef,
                                params.get("vnp_TransactionNo"), success ? "00" : "99", transaction.getAmount(),
                                params.get("vnp_BankCode"), params.get("vnp_CardType"), params.get("vnp_PayDate"), "");
                        applyVerifiedPayment(transaction, verification, params);
                    });
        }
        return transactionResult(transaction);
    }

    private VnpayCallbackResultResponse invalidCallback(String message) {
        return new VnpayCallbackResultResponse(null, null, null, null, BigDecimal.ZERO, null, null, null, null,
                false, message, "PENDING_VERIFICATION", false);
    }

    private VnpayCallbackResultResponse transactionResult(PaymentTransaction transaction) {
        boolean reconciliation = transaction.getProcessingNote() != null
                && transaction.getProcessingNote().startsWith("RECONCILIATION_REQUIRED");
        boolean success = transaction.getStatus() == PaymentStatus.PAID && !reconciliation;
        boolean failed = transaction.getStatus() == PaymentStatus.FAILED;
        String status = reconciliation ? "RECONCILIATION_REQUIRED" : success ? "SUCCESS" : failed ? "FAILED" : "PENDING_VERIFICATION";
        String message = success ? "Thanh toán đã được xác minh và ghi nhận"
                : failed ? "Giao dịch không thành công, đã được máy chủ xác minh"
                : transaction.getProcessingNote() != null && transaction.getProcessingNote().startsWith("RECONCILIATION_REQUIRED")
                    ? "Thanh toán cần được đối soát. Vui lòng liên hệ hỗ trợ và không thanh toán lại."
                    : "Đang xác minh thanh toán. Vui lòng không thanh toán lại trong lúc chờ đối soát.";
        Booking booking = transaction.getBooking();
        boolean eligible = transaction.getType() == PaymentType.DEPOSIT_PAY
                ? Set.of(BookingStatus.ACCEPTED, BookingStatus.AWAITING_DEPOSIT).contains(booking.getStatus())
                    && (booking.getPickupTime() == null || !booking.getPickupTime().isBefore(clock.instant()))
                : booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT && !disputeService.hasActiveDispute(booking.getId());
        boolean unsettledOrPaid = bookingQueryService.findTransactionsByBookingId(booking.getId()).stream()
                .anyMatch(tx -> tx.getType() == transaction.getType() && tx.getStatus() != PaymentStatus.FAILED);
        boolean requestActive = paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(),
                        transaction.getType() == PaymentType.DEPOSIT_PAY ? PaymentRequestType.DEPOSIT : PaymentRequestType.FINAL_BALANCE)
                .map(pr -> (pr.getStatus() == PaymentRequestStatus.PENDING || pr.getStatus() == PaymentRequestStatus.REJECTED)
                        && (pr.getPaidAmount() == null || pr.getPaidAmount().signum() == 0)
                        && (pr.getExpiresAt() == null || pr.getExpiresAt().isAfter(clock.instant()))).orElse(true);
        return new VnpayCallbackResultResponse(booking.getBookingCode(), transaction.getTransactionCode(),
                transaction.getVnpTxnRef(), transaction.getVnpTransactionNo(), transaction.getAmount(),
                transaction.getVnpBankCode(), transaction.getVnpCardType(), transaction.getVnpPayDate(),
                transaction.getVnpResponseCode(), success, message, status, failed && eligible && requestActive && !unsettledOrPaid);
    }

    private BigDecimal calculateDepositAmount(Booking booking) {
        Optional<BookingCharge> depositCharge = bookingQueryService.findChargeByBookingIdAndType(booking.getId(), ChargeType.DEPOSIT);
        if (depositCharge.isPresent() && depositCharge.get().getAmount() != null && depositCharge.get().getAmount().compareTo(BigDecimal.ZERO) > 0) {
            return depositCharge.get().getAmount().setScale(0, RoundingMode.HALF_UP);
        }

        BookingPolicySnapshot snapshot = requireSnapshot(booking);
        if (snapshot.getDepositAmount() != null && snapshot.getDepositAmount().signum() > 0) {
            return snapshot.getDepositAmount().setScale(0, RoundingMode.UNNECESSARY);
        }
        PlanType planType = snapshot.getPlanType();
        BigDecimal upfrontAmount = snapshot.getUpfrontAmount();

        if (planType == PlanType.YEARLY && upfrontAmount != null && upfrontAmount.compareTo(BigDecimal.ZERO) > 0) {
            return upfrontAmount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ratio = snapshot.getDepositRatio();
        if (ratio == null) throw new InvalidPaymentOperationException("Đơn thuê thiếu tỷ lệ cọc đã chốt");

        BigDecimal base = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
        return base.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateFinalPayable(Booking booking) {
        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        List<BookingCharge> charges = bookingQueryService.findChargesByBookingIdOrderByCreatedAtAsc(booking.getId());
        BigDecimal extraCharges = charges.stream()
                .filter(c -> !c.isWaived()
                        && c.getChargeType() != ChargeType.DEPOSIT
                        && c.getChargeType() != ChargeType.BASE_RENTAL)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalObligation = baseRental.add(extraCharges);

        List<PaymentTransaction> transactions = bookingQueryService.findTransactionsByBookingId(booking.getId());
        BigDecimal confirmedIncoming = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.IN)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal confirmedRefunds = transactions.stream()
                .filter(t -> t.getStatus() == PaymentStatus.PAID && t.getDirection() == PaymentDirection.OUT && t.getType() == PaymentType.REFUND)
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netPaid = confirmedIncoming.subtract(confirmedRefunds);
        BigDecimal payable = totalObligation.subtract(netPaid);
        return payable.signum() > 0 ? payable.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    private Booking resolveBooking(String bookingCodeOrId) {
        if (bookingCodeOrId == null || bookingCodeOrId.isBlank()) {
            throw new ResourceNotFoundException("Mã đơn thuê không được để trống");
        }
        Optional<Booking> opt = bookingQueryService.findBookingByCode(bookingCodeOrId.trim());
        if (opt.isEmpty()) {
            try {
                Long id = Long.parseLong(bookingCodeOrId.trim());
                opt = bookingQueryService.findBookingById(id);
            } catch (NumberFormatException ignored) {
            }
        }
        return opt.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê với mã: " + bookingCodeOrId));
    }

    private void validateAccessToBooking(Booking booking, Long currentUserId) {
        if (currentUserId == null) throw new AccessDeniedException("Vui lòng đăng nhập để tiếp tục");
        if (booking.getRenter().getUser().getId().equals(currentUserId)) return;
        User user = userService.findById(currentUserId).orElse(null);
        if (user != null && user.getRole() == Role.ADMIN) return;
        if (user != null && booking.getVehicle().getProvider().getUser().getId().equals(currentUserId)) return;
        throw new AccessDeniedException("Bạn không có quyền truy cập thông tin quyết toán của đơn thuê này");
    }

    private void validateRenterAccess(Booking booking, Long currentUserId) {
        if (currentUserId == null) throw new AccessDeniedException("Vui lòng đăng nhập để tiếp tục");
        if (!booking.getRenter().getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Bạn không có quyền thanh toán cho đơn thuê này");
        }
    }
}

