package com.fptufa26.ecomotion.finance.service.implement;

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
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.booking.dto.response.BookingChargeLineResponse;
import com.fptufa26.ecomotion.booking.repository.BookingChargeRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.PaymentTransactionRepository;
import com.fptufa26.ecomotion.booking.service.DriverOptimizationService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.ResourceNotFoundException;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.dispute.repository.DisputeRepository;
import com.fptufa26.ecomotion.finance.config.VnpayConfig;
import com.fptufa26.ecomotion.finance.config.VnpayUtils;
import com.fptufa26.ecomotion.finance.dto.request.CreateVnpayPaymentUrlRequest;
import com.fptufa26.ecomotion.finance.dto.response.RenterSettlementResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayCallbackResultResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayIpnResponse;
import com.fptufa26.ecomotion.finance.dto.response.VnpayPaymentUrlResponse;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestType;
import com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException;
import com.fptufa26.ecomotion.finance.repository.PaymentRequestRepository;
import com.fptufa26.ecomotion.finance.service.VnpayService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import com.fptufa26.ecomotion.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnpayServiceImpl implements VnpayService {

    private final VnpayConfig vnpayConfig;
    private final BookingRepository bookingRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingChargeRepository bookingChargeRepository;
    private final BookingPolicySnapshotRepository bookingPolicySnapshotRepository;
    private final PaymentRequestRepository paymentRequestRepository;
    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final ScheduleService scheduleService;
    private final DriverOptimizationService driverOptimizationService;
    private final Clock clock;

    private static final DateTimeFormatter VNP_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Override
    @Transactional
    public VnpayPaymentUrlResponse createPaymentUrl(Long currentUserId, CreateVnpayPaymentUrlRequest request, String clientIp) {
        Booking booking = resolveBooking(request.bookingCode());

        validateRenterAccess(booking, currentUserId);

        boolean isFinalPayment = "FINAL_PAYMENT".equalsIgnoreCase(request.paymentType())
                || "FINAL_BALANCE".equalsIgnoreCase(request.paymentType())
                || "SETTLEMENT".equalsIgnoreCase(request.paymentType());

        BigDecimal amount;
        PaymentType paymentType;
        if (!isFinalPayment) {
            boolean alreadyPaid = paymentTransactionRepository.findByBooking_Id(booking.getId()).stream()
                    .anyMatch(tx -> tx.getType() == PaymentType.DEPOSIT_PAY && tx.getStatus() == PaymentStatus.PAID);
            if (alreadyPaid || booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.DRIVER_ASSIGNED
                    || booking.getStatus() == BookingStatus.DRIVER_CONFIRMED) {
                throw new InvalidPaymentOperationException("Tiền đặt cọc cho đơn thuê này đã được thanh toán thành công");
            }
            if (booking.getStatus() != BookingStatus.ACCEPTED
                    && booking.getStatus() != BookingStatus.PENDING
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

            amount = calculateDepositAmount(booking);
            paymentType = PaymentType.DEPOSIT_PAY;
        } else {
            // Check if final payment is already settled or booking already completed (TC06)
            boolean alreadySettled = paymentTransactionRepository.findByBooking_Id(booking.getId()).stream()
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
            if (disputeRepository.existsByBooking_IdAndStatusIn(booking.getId(), List.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW))) {
                throw new InvalidPaymentOperationException("Không thể quyết toán khi đơn thuê đang có tranh chấp khiếu nại chưa giải quyết");
            }

            amount = calculateFinalPayable(booking);

            // If final payable is 0, complete obligations immediately without creating 0 VND VNPay txn (TC09)
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);
                paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                        .ifPresent(pr -> {
                            pr.setStatus(PaymentRequestStatus.CONFIRMED);
                            pr.setPaidAmount(pr.getAmount());
                            pr.setConfirmedAt(clock.instant());
                            paymentRequestRepository.save(pr);
                        });
                log.info("Booking {} final payable is 0. Completed directly without VNPay gateway call.", booking.getBookingCode());
                return new VnpayPaymentUrlResponse(null, null, BigDecimal.ZERO, booking.getBookingCode(), clock.instant());
            }

            paymentType = PaymentType.SETTLEMENT;
        }

        Instant now = clock.instant();
        PaymentTransaction transaction = paymentTransactionRepository.findByBooking_Id(booking.getId()).stream()
                .filter(tx -> tx.getType() == paymentType
                        && tx.getStatus() == PaymentStatus.PENDING
                        && tx.getMethod() == PaymentMethod.VNPAY
                        && tx.getVnpTxnRef() != null
                        && tx.getAmount() != null
                        && tx.getAmount().compareTo(amount) == 0
                        && tx.getCreatedAt() != null
                        && tx.getCreatedAt().isAfter(now.minus(Duration.ofMinutes(14))))
                .max(Comparator.comparing(PaymentTransaction::getCreatedAt))
                .orElseGet(() -> {
                    String prefix = isFinalPayment ? "VNP" + booking.getBookingCode() + "F" : "VNP" + booking.getBookingCode();
                    String vnpTxnRef = prefix + System.currentTimeMillis();
                    PaymentTransaction newTx = new PaymentTransaction();
                    newTx.setBooking(booking);
                    newTx.setType(paymentType);
                    newTx.setDirection(PaymentDirection.IN);
                    newTx.setMethod(PaymentMethod.VNPAY);
                    newTx.setAmount(amount);
                    newTx.setStatus(PaymentStatus.PENDING);
                    newTx.setVnpTxnRef(vnpTxnRef);
                    newTx.setGatewayRef(vnpTxnRef);
                    newTx.setCreatedAt(now);
                    newTx.setUpdatedAt(now);
                    return paymentTransactionRepository.save(newTx);
                });

        String vnpTxnRef = transaction.getVnpTxnRef();
        Instant expireTime = transaction.getCreatedAt().plus(Duration.ofMinutes(15));
        String vnpCreateDate = VNP_DATE_FORMATTER.format(transaction.getCreatedAt());
        String vnpExpireDate = VNP_DATE_FORMATTER.format(expireTime);
        long vnpAmount = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnpayConfig.getVersion());
        vnpParams.put("vnp_Command", vnpayConfig.getCommand());
        vnpParams.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpAmount));
        vnpParams.put("vnp_CurrCode", vnpayConfig.getCurrCode());
        vnpParams.put("vnp_TxnRef", vnpTxnRef);
        vnpParams.put("vnp_OrderInfo", (isFinalPayment ? "Quyet toan don thue " : "Dat coc thue xe ") + booking.getBookingCode());
        vnpParams.put("vnp_OrderType", vnpayConfig.getOrderType());
        vnpParams.put("vnp_Locale", request.language() != null && !request.language().isBlank() ? request.language() : "vn");
        vnpParams.put("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
        vnpParams.put("vnp_IpAddr", clientIp != null && !clientIp.isBlank() ? clientIp : "127.0.0.1");
        vnpParams.put("vnp_CreateDate", vnpCreateDate);
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        if (request.bankCode() != null && !request.bankCode().isBlank()) {
            vnpParams.put("vnp_BankCode", request.bankCode().trim());
        }

        String hashData = VnpayUtils.buildHashData(vnpParams);
        String vnpSecureHash = VnpayUtils.hmacSHA512(vnpayConfig.getHashSecret(), hashData);
        String queryUrl = VnpayUtils.buildQueryUrl(vnpParams) + "&vnp_SecureHash=" + vnpSecureHash;
        String paymentUrl = vnpayConfig.getPaymentUrl() + "?" + queryUrl;

        return new VnpayPaymentUrlResponse(paymentUrl, vnpTxnRef, amount, booking.getBookingCode(), expireTime);
    }

    @Override
    @Transactional(readOnly = true)
    public RenterSettlementResponse getSettlementDetails(Long currentUserId, String bookingCodeOrId) {
        Booking booking = resolveBooking(bookingCodeOrId);
        validateAccessToBooking(booking, currentUserId);

        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        List<BookingCharge> charges = bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
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

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBooking_Id(booking.getId());
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

        boolean hasDispute = disputeRepository.existsByBooking_IdAndStatusIn(booking.getId(), List.of(DisputeStatus.OPEN, DisputeStatus.IN_REVIEW));
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
        if (!verifySignature(params)) {
            return new VnpayIpnResponse("97", "Invalid Checksum");
        }

        String vnpTxnRef = params.get("vnp_TxnRef");
        if (vnpTxnRef == null || vnpTxnRef.isBlank()) {
            return new VnpayIpnResponse("01", "Order not found");
        }

        PaymentTransaction transaction = paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef).orElse(null);
        if (transaction == null) {
            return new VnpayIpnResponse("01", "Order not found");
        }

        String vnpAmountStr = params.get("vnp_Amount");
        if (vnpAmountStr != null) {
            long receivedAmount = Long.parseLong(vnpAmountStr) / 100;
            if (transaction.getAmount().setScale(0, RoundingMode.HALF_UP).longValue() != receivedAmount) {
                return new VnpayIpnResponse("04", "Invalid Amount");
            }
        }

        if (transaction.getStatus() == PaymentStatus.PAID) {
            return new VnpayIpnResponse("02", "Order already confirmed");
        }

        String responseCode = params.get("vnp_ResponseCode");
        boolean success = "00".equals(responseCode);

        transaction.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        transaction.setVnpBankCode(params.get("vnp_BankCode"));
        transaction.setVnpCardType(params.get("vnp_CardType"));
        transaction.setVnpPayDate(params.get("vnp_PayDate"));
        transaction.setVnpResponseCode(responseCode);
        transaction.setUpdatedAt(clock.instant());

        if (!success) {
            transaction.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(transaction);
            return new VnpayIpnResponse("00", "Confirm Success");
        }

        Booking booking = transaction.getBooking();
        if (transaction.getType() == PaymentType.DEPOSIT_PAY) {
            if (booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.DRIVER_ASSIGNED
                    || booking.getStatus() == BookingStatus.DRIVER_CONFIRMED) {
                transaction.setStatus(PaymentStatus.PAID);
                if (transaction.getPaidAt() == null) {
                    transaction.setPaidAt(clock.instant());
                }
                paymentTransactionRepository.save(transaction);
                return new VnpayIpnResponse("02", "Order already confirmed");
            }
            if (booking.getStatus() == BookingStatus.ACCEPTED
                    || booking.getStatus() == BookingStatus.PENDING
                    || booking.getStatus() == BookingStatus.AWAITING_DEPOSIT) {
                Vehicle vehicle = vehicleRepository.findByIdForUpdate(booking.getVehicle().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Xe không tồn tại"));

                BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
                int slotMinutes = snapshot != null ? snapshot.getSlotLengthMinutes() : booking.getPlan().getSlotLengthMinutes();
                int slots = bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.BASE_RENTAL)
                        .map(BookingCharge::getQuantity).orElse(1);
                Instant end = booking.getPickupTime().plus(Duration.ofMinutes((long) slotMinutes * slots));
                AvailabilityPolicy.Window window = AvailabilityPolicy.bufferedWindow(vehicle, booking.getPickupTime(), end);

                if (booking.getStatus() != BookingStatus.AWAITING_DEPOSIT
                        && !scheduleService.isRangeFree(vehicle.getId(), window.start(), window.end())) {
                    transaction.setStatus(PaymentStatus.FAILED);
                    paymentTransactionRepository.save(transaction);
                    return new VnpayIpnResponse("00", "Slot conflict, requires refund reconciliation");
                }

                BookingCharge depositCharge = bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.DEPOSIT)
                        .orElseGet(() -> {
                            BookingCharge c = new BookingCharge();
                            c.setBooking(booking);
                            c.setChargeType(ChargeType.DEPOSIT);
                            c.setDescription("Cọc giữ chỗ qua VNPAY");
                            c.setUnitPrice(booking.getTotalAmount());
                            c.setAppliedRate(snapshot != null ? snapshot.getDepositRatio() : (booking.getVehicle().getProvider() != null ? booking.getVehicle().getProvider().getDepositRatio() : new BigDecimal("0.3000")));
                            c.setAmount(transaction.getAmount());
                            c.setWaived(false);
                            c.setCreatedBy(booking.getRenter().getUser());
                            return bookingChargeRepository.save(c);
                        });

                transaction.setCharge(depositCharge);
                transaction.setStatus(PaymentStatus.PAID);
                transaction.setPaidAt(clock.instant());
                paymentTransactionRepository.save(transaction);

                if (booking.getStatus() == BookingStatus.AWAITING_DEPOSIT) {
                    scheduleService.confirmHold(booking.getId());
                } else {
                    scheduleService.book(vehicle, booking, booking.getPickupTime(), end);
                }
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);

                paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.DEPOSIT)
                        .ifPresent(pr -> {
                            pr.setStatus(PaymentRequestStatus.CONFIRMED);
                            pr.setPaidAmount(transaction.getAmount());
                            pr.setConfirmedAt(clock.instant());
                            paymentRequestRepository.save(pr);
                        });

                if (booking.getDriver() == null) {
                    try {
                        driverOptimizationService.autoAssign(booking.getId());
                    } catch (Exception ex) {
                        log.warn("Auto assignment post-payment failed for booking {}: {}", booking.getBookingCode(), ex.getMessage());
                    }
                }
            } else {
                transaction.setStatus(PaymentStatus.FAILED);
                paymentTransactionRepository.save(transaction);
                return new VnpayIpnResponse("00", "Booking state not eligible for confirmation");
            }
        } else if (transaction.getType() == PaymentType.SETTLEMENT) {
            transaction.setStatus(PaymentStatus.PAID);
            transaction.setPaidAt(clock.instant());
            paymentTransactionRepository.save(transaction);
            if (booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);
            }
            paymentRequestRepository.findByBooking_IdAndPaymentType(booking.getId(), PaymentRequestType.FINAL_BALANCE)
                    .ifPresent(pr -> {
                        pr.setStatus(PaymentRequestStatus.CONFIRMED);
                        pr.setPaidAmount(pr.getAmount());
                        pr.setConfirmedAt(clock.instant());
                        paymentRequestRepository.save(pr);
                    });
        }

        return new VnpayIpnResponse("00", "Confirm Success");
    }

    @Override
    @Transactional
    public VnpayCallbackResultResponse processCallback(Map<String, String> params) {
        boolean valid = verifySignature(params);
        String vnpTxnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String cardType = params.get("vnp_CardType");
        String payDate = params.get("vnp_PayDate");

        PaymentTransaction transaction = vnpTxnRef != null
                ? paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef).orElse(null)
                : null;

        if (transaction == null) {
            return new VnpayCallbackResultResponse(null, vnpTxnRef, transactionNo, BigDecimal.ZERO, bankCode, cardType, payDate, responseCode, false, "Giao dịch không tồn tại");
        }

        Booking booking = transaction.getBooking();
        boolean success = valid && "00".equals(responseCode);

        if (success && transaction.getStatus() != PaymentStatus.PAID) {
            processIpn(params);
        }

        String message = success ? "Thanh toán VNPAY thành công" : "Thanh toán không thành công (Mã phản hồi: " + responseCode + ")";
        return new VnpayCallbackResultResponse(
                booking.getBookingCode(),
                vnpTxnRef,
                transactionNo,
                transaction.getAmount(),
                bankCode,
                cardType,
                payDate,
                responseCode,
                success,
                message
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VnpayCallbackResultResponse queryTransaction(String bookingCode, String vnpTxnRef) {
        PaymentTransaction transaction = paymentTransactionRepository.findByVnpTxnRef(vnpTxnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + vnpTxnRef));

        boolean success = transaction.getStatus() == PaymentStatus.PAID;
        return new VnpayCallbackResultResponse(
                transaction.getBooking().getBookingCode(),
                transaction.getVnpTxnRef(),
                transaction.getVnpTransactionNo(),
                transaction.getAmount(),
                transaction.getVnpBankCode(),
                transaction.getVnpCardType(),
                transaction.getVnpPayDate(),
                transaction.getVnpResponseCode(),
                success,
                success ? "Giao dịch thành công" : "Giao dịch chưa thanh toán hoặc thất bại"
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

    private BigDecimal calculateDepositAmount(Booking booking) {
        Optional<BookingCharge> depositCharge = bookingChargeRepository.findByBooking_IdAndChargeType(booking.getId(), ChargeType.DEPOSIT);
        if (depositCharge.isPresent() && depositCharge.get().getAmount() != null && depositCharge.get().getAmount().compareTo(BigDecimal.ZERO) > 0) {
            return depositCharge.get().getAmount().setScale(0, RoundingMode.HALF_UP);
        }

        BookingPolicySnapshot snapshot = bookingPolicySnapshotRepository.findByBooking_Id(booking.getId()).orElse(null);
        PlanType planType = snapshot != null ? snapshot.getPlanType() : booking.getPlan().getPlanType();
        BigDecimal upfrontAmount = snapshot != null ? snapshot.getUpfrontAmount() : booking.getPlan().getUpfrontAmount();

        if (planType == PlanType.YEARLY && upfrontAmount != null && upfrontAmount.compareTo(BigDecimal.ZERO) > 0) {
            return upfrontAmount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal ratio = snapshot != null && snapshot.getDepositRatio() != null
                ? snapshot.getDepositRatio()
                : (booking.getVehicle().getProvider().getDepositRatio() != null
                ? booking.getVehicle().getProvider().getDepositRatio()
                : new BigDecimal("0.3000"));

        BigDecimal base = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
        return base.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateFinalPayable(Booking booking) {
        BigDecimal baseRental = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;

        List<BookingCharge> charges = bookingChargeRepository.findByBooking_IdOrderByCreatedAtAsc(booking.getId());
        BigDecimal extraCharges = charges.stream()
                .filter(c -> !c.isWaived()
                        && c.getChargeType() != ChargeType.DEPOSIT
                        && c.getChargeType() != ChargeType.BASE_RENTAL)
                .map(BookingCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalObligation = baseRental.add(extraCharges);

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByBooking_Id(booking.getId());
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
        Optional<Booking> opt = bookingRepository.findByBookingCode(bookingCodeOrId.trim());
        if (opt.isEmpty()) {
            try {
                Long id = Long.parseLong(bookingCodeOrId.trim());
                opt = bookingRepository.findById(id);
            } catch (NumberFormatException ignored) {
            }
        }
        return opt.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn thuê với mã: " + bookingCodeOrId));
    }

    private void validateAccessToBooking(Booking booking, Long currentUserId) {
        if (currentUserId == null) return;
        if (booking.getRenter().getUser().getId().equals(currentUserId)) return;
        User user = userRepository.findById(currentUserId).orElse(null);
        if (user != null && user.getRole() == Role.ADMIN) return;
        if (user != null && booking.getVehicle().getProvider().getUser().getId().equals(currentUserId)) return;
        throw new AccessDeniedException("Bạn không có quyền truy cập thông tin quyết toán của đơn thuê này");
    }

    private void validateRenterAccess(Booking booking, Long currentUserId) {
        if (currentUserId == null) return;
        if (!booking.getRenter().getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Bạn không có quyền thanh toán cho đơn thuê này");
        }
    }
}

