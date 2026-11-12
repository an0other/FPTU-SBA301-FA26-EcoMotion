package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.policy.YearlyRentalRules;
import com.fptufa26.ecomotion.booking.repository.*;
import com.fptufa26.ecomotion.booking.service.*;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fptufa26.ecomotion.finance.service.CancellationClosureService;
import com.fptufa26.ecomotion.dispute.enums.DisputeCategory;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.dispute.enums.DisputeStatus;
import com.fptufa26.ecomotion.verification.documentai.DrivingLicenseEligibilityPolicy;
import com.fptufa26.ecomotion.finance.service.FinancialAuditService;
import com.fptufa26.ecomotion.finance.service.RefundEventService;
import com.fptufa26.ecomotion.finance.service.RefundMath;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.vehicle.enums.VehicleStatus;
import com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests;
import com.fptufa26.ecomotion.booking.policy.YearlySettlementMath;

@Service
@Transactional
public class YearlyWorkflowServiceImpl implements YearlyWorkflowService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final BookingRepository bookings;
    private final YearlyWorkflowRepository workflows;
    private final YearlyIncidentRepository incidents;
    private final YearlyReplacementRepository replacements;
    private final YearlyHandoverRepository handovers;
    private final TripEvidenceService evidence;
    private final PaymentRequestService payments;
    private final ScheduleService schedules;
    private final VehicleService vehicles;
    private final BookingNotificationService notifications;
    private final Clock clock;
    private final BigDecimal unit;
    private final com.fptufa26.ecomotion.booking.service.RentalPricingService pricing;
    private final BookingPaymentLedgerService ledger;
    private final DisputeService disputes;
    private final FinancialAuditService audit;
    private final UserService users;
    private final RefundEventService refunds;
    private final CancellationClosureService closure;

    public YearlyWorkflowServiceImpl(BookingRepository bookings, YearlyWorkflowRepository workflows,
            YearlyIncidentRepository incidents, YearlyReplacementRepository replacements,
            YearlyHandoverRepository handovers, TripEvidenceService evidence, PaymentRequestService payments,
            ScheduleService schedules, VehicleService vehicles, BookingNotificationService notifications,
            Clock clock, BookingPaymentLedgerService ledger,
            DisputeService disputes,
            FinancialAuditService audit,
            UserService users,
            RefundEventService refunds,
            CancellationClosureService closure,
            RentalPricingService pricing, @Value("${booking.yearly.rounding-unit:100000}") BigDecimal unit) {
        this.bookings = bookings;
        this.workflows = workflows;
        this.incidents = incidents;
        this.replacements = replacements;
        this.handovers = handovers;
        this.evidence = evidence;
        this.payments = payments;
        this.schedules = schedules;
        this.vehicles = vehicles;
        this.notifications = notifications;
        this.clock = clock;
        if (unit == null || unit.signum() <= 0) throw new IllegalArgumentException("Invalid yearly rounding unit");
        this.unit = unit;
        this.pricing = pricing;
        this.ledger = ledger; this.disputes = disputes; this.audit = audit; this.users = users; this.refunds = refunds; this.closure = closure;
    }

    @Override
    @Transactional(readOnly = true)
    public Operations operations(Long userId, String code) {
        Booking b = booking(userId, code, false, null);
        YearlyWorkflow s = workflows.findById(b.getId()).orElseGet(() -> fresh(b));
        boolean provider = b.getVehicle().getProvider().getUser().getId().equals(userId);
        boolean review = disputes.hasOpenDispute(b.getId());
        boolean active = b.getStatus() == BookingStatus.IN_PROGRESS && b.getActualStartAt() != null && b.getActualEndAt() == null
                && payments.yearlyInitialPaymentsComplete(b.getId());
        boolean running = active && "TERMS_ACCEPTED".equals(s.getStage());
        boolean voluntary = active && "RETURN_REQUESTED".equals(s.getStage()) && !"PROVIDER_FAILURE".equals(s.getReturnMode());
        var reports = incidents.findByBookingIdOrderByIdAsc(b.getId());
        var swaps = replacements.findByBookingIdOrderByIdAsc(b.getId());
        boolean reportReady = reports.stream().allMatch(i -> Set.of("CLOSED","AWAITING_TRANSFER","TRANSFER_RECORDED").contains(i.getStatus()));
        boolean swapsClosed = swaps.stream().allMatch(x -> Set.of("COMPLETED","CANCELLED").contains(x.getStatus()));
        String financialIssue = null;
        boolean financialReady = true, balancePaid = s.getBalanceDue().signum() == 0;
        if (b.getActualStartAt() != null && !Set.of("TERMS_ACCEPTED","COMPLETED").contains(s.getStage())) {
            try {
                payments.yearlyCash(b.getId());
                balancePaid = balancePaid || payments.yearlyBalancePaid(b.getId(), s.getBalanceDue());
            } catch (com.fptufa26.ecomotion.finance.exception.InvalidPaymentOperationException ex) {
                financialReady = false; financialIssue = ex.getMessage();
            }
        }
        boolean canRequest = !provider && running && !review && reportReady && swapsClosed;
        boolean canEdit = !provider && voluntary && !review;
        boolean canInspect = provider && active && !review && Set.of("RETURN_REQUESTED","RETURN_INSPECTED").contains(s.getStage());
        boolean canConfirm = !provider && active && !review && "RETURN_INSPECTED".equals(s.getStage());
        boolean canPropose = provider && b.getActualEndAt() != null && !review && financialReady
                && Set.of("RETURNED","SETTLEMENT_PROPOSED").contains(s.getStage());
        boolean settlementCurrent = true;
        if ("SETTLEMENT_PROPOSED".equals(s.getStage()) && financialReady) {
            var cash = payments.yearlyCash(b.getId());
            settlementCurrent = cash.rentPaid().compareTo(s.getRentPaid()) == 0 && cash.depositHeld().compareTo(s.getDepositHeld()) == 0;
        }
        boolean canAccept = !provider && !review && financialReady && settlementCurrent && "SETTLEMENT_PROPOSED".equals(s.getStage());
        BigDecimal refundTotal = s.getRentRefund().add(s.getDepositRefund());
        boolean canTransfer = provider && !review && financialReady && balancePaid && !refunds.hasUnsettledYearlyRefunds(b.getId()) && refundTotal.signum() > 0
                && "SETTLEMENT_ACCEPTED".equals(s.getStage());
        boolean canConfirmRefund = !provider && !review && "REFUND_TRANSFER_RECORDED".equals(s.getStage());
        boolean canComplete = provider && !review && financialReady && balancePaid
                && Set.of("SETTLEMENT_ACCEPTED","REFUND_RECEIVED").contains(s.getStage())
                && (refundTotal.signum() == 0 || "REFUND_RECEIVED".equals(s.getStage()))
                && !refunds.hasUnsettledYearlyRefunds(b.getId()) && reports.stream().allMatch(i -> "CLOSED".equals(i.getStatus()))
                && ledger.listTransactions(b.getId()).stream().noneMatch(tx -> tx.getStatus() == PaymentStatus.PENDING || RefundMath.unapplied(tx.getProcessingNote()));
        List<Long> reopenIds = provider ? schedules.yearlyInspectionVehicleIds(b.getId()).stream().filter(id ->
                b.getActualEndAt() != null && b.getVehicle().getId().equals(id)
                || swaps.stream().anyMatch(x -> "COMPLETED".equals(x.getStatus()) && x.getOldVehicleId().equals(id))).toList() : List.of();
        String nextRole = "NONE", tab = "overview", label = "Xem tổng quan", message = "Bạn chưa cần thao tác.";
        if ("COMPLETED".equals(s.getStage())) {
            tab = "return"; label = "Xem tổng kết hợp đồng"; message = "Hồ sơ đã hoàn tất. Không cần chuyển thêm tiền.";
        } else if (review) {
            nextRole = "ADMIN"; tab = "return"; label = "Xem hồ sơ đang được xem xét";
            message = "Chờ kết luận hồ sơ. Không tự gửi lại tiền hoặc xác nhận nhận tiền khi chưa thực nhận.";
        } else if (running) {
            var open = reports.stream().filter(i -> !"CLOSED".equals(i.getStatus())).findFirst();
            if (open.isPresent()) {
                var i = open.get(); tab = "incidents"; label = "Xử lý report #" + i.getId();
                nextRole = Set.of("RESOLUTION_PROPOSED","TRANSFER_RECORDED").contains(i.getStatus()) ? "RENTER" : "PROVIDER";
                if ("REPLACE".equals(i.getOutcome())) {
                    var swap = swaps.stream().filter(x -> Objects.equals(x.getIncidentId(), i.getId()) && !Set.of("COMPLETED","CANCELLED").contains(x.getStatus())).findFirst();
                    if (swap.isPresent()) nextRole = Set.of("PROPOSED","HANDOVER_PROPOSED").contains(swap.get().getStatus()) ? "RENTER" : "PROVIDER";
                }
                message = switch (i.getStatus()) {
                    case "RESOLUTION_PROPOSED" -> "Người thuê xem trách nhiệm và phương án provider đề xuất, sau đó đồng ý hoặc phản hồi.";
                    case "AWAITING_TRANSFER" -> "Provider đã đồng ý hoàn chi phí; cần ghi nhận đã chuyển và gửi biên lai.";
                    case "TRANSFER_RECORDED" -> "Renter kiểm tra tài khoản rồi xác nhận đã nhận tiền bảo trì.";
                    case "AWAITING_CONFIRMATION" -> "REPLACE".equals(i.getOutcome()) ? "Theo dõi đề nghị đổi xe, đồng ý xe rồi xác nhận bàn giao là hai bước riêng." : "Người thuê cần xác nhận kết quả xử lý.";
                    default -> "Provider xem report và đề xuất trách nhiệm/phương án cho người thuê.";
                };
                if ("AWAITING_CONFIRMATION".equals(i.getStatus()) && !"REPLACE".equals(i.getOutcome())) nextRole = "RENTER";
            } else {
                tab = "payments"; label = "Xem kỳ thanh toán"; nextRole = "RENTER";
                message = "Xe đang thuê. Xem kỳ cần thanh toán hoặc mở mục Trả xe để gửi yêu cầu.";
                var rows = payments.yearlyPayments(userId, b.getBookingCode());
                if (rows != null && rows.stream().anyMatch(r -> "PROOF_SUBMITTED".equals(r.status()))) {
                    nextRole = "PROVIDER"; label = "Đối soát biên lai"; message = "Đã có biên lai chờ provider kiểm tra tiền thực nhận.";
                }
            }
        } else {
            tab = "return"; label = "Xem bước trả xe / quyết toán";
            switch (s.getStage()) {
                case "RETURN_REQUESTED" -> { nextRole = "PROVIDER"; message = "Provider nhận lại xe và gửi biên bản. Renter có thể sửa hoặc rút yêu cầu tự nguyện trước kiểm định."; }
                case "RETURN_INSPECTED" -> { nextRole = "RENTER"; message = "Renter xác nhận ngày trả, ảnh và km. Tiền chưa đối soát không ngăn ghi nhận đã trả xe."; }
                case "RETURNED" -> { nextRole = "PROVIDER"; message = "Đã trả xe. Provider lập bảng tiền; không yêu cầu renter nhận xe hoặc trả xe lần nữa."; }
                case "SETTLEMENT_PROPOSED" -> { nextRole = settlementCurrent ? "RENTER" : "PROVIDER"; message = settlementCurrent
                    ? "Renter xem bảng quyết toán, đồng ý phiên bản này hoặc nêu nội dung chưa đúng."
                    : "Tiền đã nhận thay đổi sau đề xuất. Provider cần tính và gửi lại quyết toán; renter chưa xác nhận bảng cũ."; }
                case "SETTLEMENT_ACCEPTED" -> {
                    if (!balancePaid) {
                        nextRole = "RENTER"; tab = "payments"; label = "Thanh toán khoản bổ sung";
                        message = "Chỉ thanh toán khoản bổ sung quyết toán, không trả tiếp kỳ thuê cũ.";
                        var rows = payments.yearlyPayments(userId, b.getBookingCode());
                        if (rows != null && rows.stream().anyMatch(r -> "ADJUSTMENT".equals(r.purpose()) && "PROOF_SUBMITTED".equals(r.status()))) {
                            nextRole = "PROVIDER"; label = "Đối soát khoản bổ sung"; message = "Renter đã gửi biên lai bổ sung; provider kiểm tra tiền thực nhận.";
                        }
                    } else { nextRole = "PROVIDER"; message = refundTotal.signum() > 0 ? "Provider chuyển phần tiền hoàn còn thiếu và gửi biên lai." : "Không có khoản hoàn; provider hoàn tất khi hết report và tiền treo."; }
                }
                case "REFUND_TRANSFER_RECORDED" -> { nextRole = "RENTER"; message = "Provider đã gửi biên lai. Renter xác nhận số tiền của lần chuyển hiện tại hoặc báo thiếu; chỉ hoàn tất khi đã nhận đủ tổng nghĩa vụ."; }
                case "REFUND_RECEIVED" -> { nextRole = "PROVIDER"; message = "Đã xác nhận nhận tiền hoàn. Provider hoàn tất khi tất cả nghĩa vụ đã xử lý."; }
                default -> { }
            }
            if (financialIssue != null && !Set.of("RETURN_REQUESTED","RETURN_INSPECTED").contains(s.getStage())) {
                nextRole = "BOTH"; tab = "payments"; label = "Xem khoản cần đối soát";
                message = "Xe đã trả nhưng bảng tiền chưa thể chốt: " + financialIssue;
            }
        }
        if (!review && Set.of("SETTLEMENT_ACCEPTED","REFUND_RECEIVED").contains(s.getStage()) && balancePaid) {
            var outstanding = reports.stream().filter(i -> !"CLOSED".equals(i.getStatus())).findFirst();
            if (outstanding.isPresent()) {
                var i = outstanding.get(); nextRole = "TRANSFER_RECORDED".equals(i.getStatus()) ? "RENTER" : "PROVIDER";
                tab = "incidents"; label = "Xử lý khoản bảo trì còn chờ";
                message = "Report #" + i.getId() + " chưa đóng. Hoàn tất chuyển tiền / xác nhận thực nhận trước khi đóng hợp đồng.";
            }
        }
        if (!review && Set.of("RETURNED","SETTLEMENT_PROPOSED","SETTLEMENT_ACCEPTED","REFUND_RECEIVED").contains(s.getStage())
                && refunds.hasUnsettledYearlyRefunds(b.getId())) {
            nextRole = "BOTH"; tab = "payments"; label = "Xử lý khoản hoàn riêng";
            message = "Còn khoản hoàn chuyển dư / đối soát chưa xong hoặc đang chờ xác nhận nhận tiền. Xem mục Hoàn tiền riêng; không gộp lại vào hoàn cọc.";
        }
        boolean needsAction = nextRole.equals(provider ? "PROVIDER" : "RENTER") || "BOTH".equals(nextRole);
        if (!needsAction && !"NONE".equals(nextRole)) message += " Bạn đang chờ bên còn lại; chỉ xem thông tin tại đây.";
        return new Operations(canRequest, canEdit, canEdit, canInspect, canConfirm, canPropose, canAccept,
                canTransfer, canConfirmRefund, canComplete, running && !review,
                "SETTLEMENT_ACCEPTED".equals(s.getStage()) && !review && financialReady, reopenIds,
                financialIssue, nextRole, tab, label, message, needsAction);
    }

    @Override
    @Transactional(readOnly = true)
    public Registration registration(Long userId, String code) {
        Booking b = booking(userId, code, false, null);
        YearlyWorkflow s = workflows.findById(b.getId()).orElseGet(() -> fresh(b));
        var snapshot = ledger.findSnapshot(b.getId()).orElseThrow(() -> conflict("Thiếu chính sách giá"));
        BigDecimal fee = snapshot.getPickupFee() == null ? BigDecimal.ZERO : snapshot.getPickupFee();
        BigDecimal first = YearlyRentalRules.installments(b.getTotalAmount(), s.getRoundingUnit()).get(0);
        boolean held = schedules.hasUnexpiredHold(b.getId(), clock.instant());
        boolean paid = payments.yearlyInitialPaymentsComplete(b.getId());
        String handover = handovers.findById(b.getId()).map(YearlyHandover::getStage).orElse("NOT_DISPATCHED");
        boolean accepted = b.getStatus() == BookingStatus.ACCEPTED && held;
        boolean contract = b.getContractUrl() != null && !b.getContractUrl().isBlank();
        boolean pay = b.getStatus() == BookingStatus.AWAITING_DEPOSIT && held && contract && s.isTermsAccepted();
        boolean ready = b.getStatus() == BookingStatus.CONFIRMED && paid && contract && s.isTermsAccepted();
        String phase = "CLOSED", role = "NONE", message = "Đăng ký không còn hoạt động. Xem trạng thái đơn và đối soát nếu có tiền đã nhận.";
        if (b.getActualStartAt() != null) {
            phase = "ACTIVE"; message = "Đã nhận xe. Hợp đồng và lịch thanh toán tính từ ngày nhận thực tế.";
        } else if (b.getStatus() == BookingStatus.PENDING && held) {
            phase = "REQUESTED"; role = "PROVIDER"; message = "Chờ nhà cung cấp chấp nhận yêu cầu. Chưa chuyển tiền.";
        } else if (accepted && !s.isTermsAccepted()) {
            phase = s.getAnnualKm() == null ? "TERMS_DRAFT" : "TERMS_REVIEW";
            role = s.getAnnualKm() == null ? "PROVIDER" : "RENTER";
            message = s.getAnnualKm() == null ? "Nhà cung cấp cần đề xuất điều khoản km và phí." : "Người thuê cần kiểm tra và đồng ý điều khoản.";
        } else if (accepted && s.isTermsAccepted()) {
            phase = "CONTRACT"; role = "RENTER"; message = "Nộp hợp đồng PDF đã ký. Đồng ý điều khoản chưa thay thế việc nộp hợp đồng.";
        } else if (pay) {
            phase = "PAYMENT"; role = "BOTH"; message = "Thanh toán riêng tiền cọc và tháng đầu. Nhà cung cấp đối soát tiền thực nhận; gửi biên lai chưa phải thanh toán thành công.";
        } else if (ready) {
            phase = handover; role = "ARRIVED".equals(handover) ? "RENTER" : "PROVIDER";
            message = switch (handover) {
                case "ARRIVED" -> "Xe đã đến. Người thuê kiểm tra ảnh, km và xác nhận ngày bắt đầu thực tế trước khi nhận.";
                case "DISPATCHED" -> "Nhà cung cấp đang giao xe; cần ghi ảnh và số km khi xe đến.";
                default -> "Đã đủ cọc và tháng đầu. Nhà cung cấp có thể bắt đầu giao xe.";
            };
        } else if ((b.getStatus() == BookingStatus.PENDING || b.getStatus() == BookingStatus.ACCEPTED
                || b.getStatus() == BookingStatus.AWAITING_DEPOSIT) && !held) {
            phase = "EXPIRED"; message = "Thời hạn giữ xe đã hết. Không chuyển thêm tiền; tải lại trạng thái và đối soát khoản đã chuyển trước khi tạo yêu cầu mới.";
        }
        String receiptIssue = null;
        if (ready && "ARRIVED".equals(handover)) {
            try {
                schedules.assertFreeForMove(b, b.getVehicle(), clock.instant(),
                        YearlyRentalRules.contractEnd(clock.instant()));
            } catch (ConflictException ex) {
                receiptIssue = ex.getMessage();
            }
        }
        return new Registration(phase, role, message, held, accepted && !s.isTermsAccepted(),
                accepted && !s.isTermsAccepted() && s.getAnnualKm() != null,
                accepted && s.isTermsAccepted(), pay, ready && "NOT_DISPATCHED".equals(handover),
                ready && Set.of("DISPATCHED", "ARRIVED").contains(handover), ready && "ARRIVED".equals(handover) && receiptIssue == null,
                fee, first, snapshot.getDepositAmount(), today(), today().plusMonths(12), schedules.holdExpiresAt(b.getId()), receiptIssue);
    }

    @Override
    @Transactional(readOnly = true)
    public View get(Long userId, String code) {
        Booking b = booking(userId, code, false, null);
        return view(b, workflows.findById(b.getId()).orElseGet(() -> fresh(b)));
    }

    @Override
    @Transactional(readOnly = true)
    public Preview preview(Long userId, String code, PreviewRequest r) {
        Booking b = booking(userId, code, false, false);
        require(b.getActualStartAt() != null && b.getActualEndAt() == null, "Chỉ dự tính khi xe đang thuê");
        YearlyWorkflow s = workflows.findById(b.getId()).orElseThrow(() -> conflict("Chưa chốt điều khoản năm"));
        require(s.isTermsAccepted() && payments.yearlyInitialPaymentsComplete(b.getId()), "Hợp đồng cũ chưa chuyển sang flow năm mới");
        require(Set.of("EARLY", "STANDARD", "FULL_CONTRACT").contains(r.mode()), "Loại trả xe không hợp lệ");
        require(!r.returnedOn().isBefore(started(b)), "Ngày trả trước ngày nhận");
        var calculation = assess(b, s, r.returnedOn(), r.mode(), 0L, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        return new Preview(r.returnedOn(), calculation.money(), "ESTIMATE_ONLY_EXCLUDES_KM_AND_INSPECTION_CHARGES");
    }

    @Override
    public View terms(Long userId, String code, Terms r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(b.getStatus() == BookingStatus.ACCEPTED, "Chỉ cấu hình điều khoản sau khi provider chấp nhận yêu cầu");
        require(schedules.hasUnexpiredHold(b.getId(), clock.instant()), "Thời hạn giữ xe đã hết");
        require(!s.isTermsAccepted() && b.getActualStartAt() == null, "Điều khoản đã chốt hoặc xe đã nhận");
        try {
            YearlyRentalRules.installments(b.getTotalAmount(), unit);
        } catch (IllegalArgumentException ex) {
            throw conflict("Giá năm phải chia được theo bước làm tròn đã cấu hình");
        }
        var snapshot = ledger.findSnapshot(b.getId()).orElseThrow(() -> conflict("Thiếu chính sách giá của đơn"));
        require(snapshot.getDepositAmount() != null && snapshot.getDepositAmount().signum() > 0, "Thiếu tiền bảo đảm cố định");
        s.setContractTotal(b.getTotalAmount());
        s.setSecurityDeposit(snapshot.getDepositAmount());
        s.setAnnualKm(r.annualKm());
        s.setExcessKmRate(r.excessKmRate());
        s.setGraceDays(r.graceDays());
        s.setLateDailyRate(r.lateDailyRate());
        s.setRoundingUnit(unit);
        s.setStage("TERMS_DRAFT");
        return changed(b, s);
    }

    @Override
    public View acceptTerms(Long userId, String code, Version r) {
        Booking b = booking(userId, code, true, false);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(b.getStatus() == BookingStatus.ACCEPTED, "Đơn không còn ở bước chốt hợp đồng");
        require(schedules.hasUnexpiredHold(b.getId(), clock.instant()), "Thời hạn giữ xe đã hết");
        require(s.getContractTotal() != null && s.getContractTotal().compareTo(b.getTotalAmount()) == 0, "Giá đã thay đổi, cần cập nhật điều khoản");
        require("TERMS_DRAFT".equals(s.getStage()) && s.getAnnualKm() != null, "Provider chưa cấu hình đủ điều khoản");
        s.setTermsAccepted(true);
        s.setStage("TERMS_ACCEPTED");
        return changed(b, s);
    }

    @Override
    public View requestReturn(Long userId, String code, Return r) {
        Booking b = booking(userId, code, true, false);
        YearlyWorkflow s = active(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn yêu cầu Admin xem xét chưa được giải quyết");
        require(incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(i -> Set.of("CLOSED", "AWAITING_TRANSFER", "TRANSFER_RECORDED").contains(i.getStatus())), "Còn report chưa có kết luận; bạn có thể yêu cầu Admin xem xét");
        require(Set.of("EARLY", "STANDARD", "FULL_CONTRACT").contains(r.mode()), "Loại trả xe không hợp lệ");
        require(Set.of("TERMS_ACCEPTED", "RETURN_REQUESTED").contains(s.getStage())
                && !"PROVIDER_FAILURE".equals(s.getReturnMode()), "Không thể sửa hình thức trả sau kiểm định hoặc nhánh lỗi provider");
        LocalDate requestedOn = r.requestedOn() == null ? today() : r.requestedOn();
        require(!requestedOn.isBefore(today()) && !requestedOn.isBefore(started(b)), "Ngày hẹn trả phải từ hôm nay trở đi");
        require(!"STANDARD".equals(r.mode()) || !requestedOn.isBefore(b.getPlannedEndAt().atZone(ZONE).toLocalDate()),
                "Ngày hẹn trước hạn trả; hãy chọn trả sớm hoặc thanh toán đủ hợp đồng");
        require(replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().noneMatch(x -> !Set.of("COMPLETED", "CANCELLED").contains(x.getStatus())), "Đang có yêu cầu đổi xe");
        String previous = s.getStage() + ":" + s.getReturnMode() + ":" + s.getRequestedReturnOn();
        s.setReturnMode(r.mode());
        s.setRequestedReturnOn(requestedOn);
        s.setStage("RETURN_REQUESTED");
        event(userId, b, "RETURN_REQUESTED", r.toString(), previous);
        return changed(b, s);
    }

    @Override
    public View withdrawReturn(Long userId, String code, Version r) {
        Booking b = booking(userId, code, true, false);
        YearlyWorkflow s = active(b);
        version(s.getVersion(), r.version());
        require("RETURN_REQUESTED".equals(s.getStage()) && !"PROVIDER_FAILURE".equals(s.getReturnMode()),
                "Chỉ rút yêu cầu tự nguyện trước khi provider gửi kiểm định");
        require(!disputes.hasOpenDispute(b.getId()), "Còn hồ sơ đang xem xét");
        event(userId, b, "RETURN_WITHDRAWN", r.toString(), s.getReturnMode() + ":" + s.getRequestedReturnOn());
        s.setReturnMode(null); s.setRequestedReturnOn(null); s.setStage("TERMS_ACCEPTED");
        return changed(b, s);
    }

    @Override
    public View inspectReturn(Long userId, String code, Inspection r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = active(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn hồ sơ đang xem xét");
        require(Set.of("RETURN_REQUESTED", "RETURN_INSPECTED").contains(s.getStage()), "Chưa có yêu cầu trả xe");
        LocalDate start = started(b);
        require(!r.returnedOn().isBefore(start) && !r.returnedOn().isAfter(today()), "Ngày nhận lại xe không hợp lệ");
        require(!"STANDARD".equals(s.getReturnMode())
                || !r.returnedOn().isBefore(b.getPlannedEndAt().atZone(ZONE).toLocalDate()),
                "Ngày kiểm định trước hạn trả; cần thống nhất hình thức trả sớm trước khi nhận lại xe");
        require(r.finalKm() >= initialKm(b, s), "Số km trả nhỏ hơn số km nhận");
        var legs = mileageLegs(b, s, r.returnedOn(), r.finalKm());
        photo(userId, b, r.evidenceId(), true);
        s.setReturnedOn(r.returnedOn());
        s.setFinalKm(r.finalKm());
        s.setReturnEvidenceId(r.evidenceId());
        s.setTotalKm(legs.stream().mapToLong(MileageLeg::usedKm).reduce(0L, Math::addExact));
        s.setStage("RETURN_INSPECTED");
        return changed(b, s);
    }

    @Override
    public View confirmReturn(Long userId, String code, Version r) {
        Booking b = booking(userId, code, true, false);
        YearlyWorkflow s = active(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require("RETURN_INSPECTED".equals(s.getStage()), "Chưa có biên bản nhận lại xe");
        var confirmedLegs = mileageLegs(b, s, s.getReturnedOn(), s.getFinalKm());
        require(s.getTotalKm() != null && s.getTotalKm() == confirmedLegs.stream().mapToLong(MileageLeg::usedKm).sum(),
                "Tổng km không khớp biên bản");
        require(!"STANDARD".equals(s.getReturnMode())
                || !s.getReturnedOn().isBefore(b.getPlannedEndAt().atZone(ZONE).toLocalDate()), "Ngày trả chưa tới hạn");
        event(userId, b, "RETURN_CONFIRMED", s.getReturnedOn() + ":" + s.getFinalKm(), s.getStage());
        vehicles.findVehicleByIdForUpdate(b.getVehicle().getId()).orElseThrow(() -> conflict("Không tìm thấy xe"));
        var end = s.getReturnedOn().atStartOfDay(ZONE).toInstant();
        b.setActualEndAt(end.isBefore(b.getActualStartAt()) ? b.getActualStartAt() : end);
        b.setRenterEndConfirmedAt(clock.instant());
        b.setProviderConfirmedAt(clock.instant());
        b.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
        schedules.release(b.getId());
        schedules.markYearlyInspection(b, b.getVehicle(), s.getReturnedOn());
        s.setStage("RETURNED");
        return changed(b, s);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnAssessment returnAssessment(Long userId, String code) {
        Booking b = booking(userId, code, false, null);
        YearlyWorkflow s = workflows.findById(b.getId()).orElseThrow(() -> conflict("Chưa có điều khoản năm"));
        return assessStoredReturn(b, s, s.getOtherCharges(), s.getDepositDeduction(), storedCharges(s));
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnAssessment settlementPreview(Long userId, String code, Settlement r) {
        Booking b = booking(userId, code, false, true);
        YearlyWorkflow s = workflows.findById(b.getId()).orElseThrow(() -> conflict("Chưa có điều khoản năm"));
        version(s.getVersion(), r.version());
        require(Set.of("RETURNED", "SETTLEMENT_PROPOSED").contains(s.getStage()), "Chưa xác nhận trả xe hoặc quyết toán đã chốt");
        validateCharges(userId, b, r);
        return assessStoredReturn(b, s, r.otherCharges(), r.depositDeduction(), r.charges() == null ? List.of() : r.charges());
    }

    @Override
    public View proposeSettlement(Long userId, String code, Settlement r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require(Set.of("RETURNED", "SETTLEMENT_PROPOSED").contains(s.getStage()), "Không thể thay đổi quyết toán ở bước này");
        validateCharges(userId, b, r);
        if (r.otherCharges().signum() > 0) {
            require(r.note() != null && !r.note().isBlank(), "Phụ phí cần lý do");
            if (r.evidenceId() != null) photo(userId, b, r.evidenceId(), true);
        }
        if (r.expectedRentPaid() != null || r.expectedDepositHeld() != null) {
            var cash = payments.yearlyCash(b.getId());
            require(r.expectedRentPaid() != null && r.expectedDepositHeld() != null
                    && cash.rentPaid().compareTo(r.expectedRentPaid()) == 0
                    && cash.depositHeld().compareTo(r.expectedDepositHeld()) == 0,
                    "Tiền đã nhận thay đổi sau khi dự tính; hãy cập nhật lại bảng tiền");
        }
        String previous = s.getStage() + ":" + s.getSettlementChargesJson() + ":" + s.getBalanceDue();
        calculateSettlement(b, s, r);
        event(userId, b, "SETTLEMENT_PROPOSED", new tools.jackson.databind.ObjectMapper().writeValueAsString(r), previous);
        return changed(b, s);
    }

    @Override
    public View acceptSettlement(Long userId, String code, Version r) {
        Booking b = booking(userId, code, true, false);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require("SETTLEMENT_PROPOSED".equals(s.getStage()), "Chưa có quyết toán để xác nhận");
        var cash = payments.yearlyCash(b.getId());
        require(cash.rentPaid().compareTo(s.getRentPaid()) == 0 && cash.depositHeld().compareTo(s.getDepositHeld()) == 0,
                "Tiền nhận đã thay đổi, cần lập lại quyết toán");
        payments.finalizeYearlyObligations(b.getId(), s.getBalanceDue());
        s.setStage("SETTLEMENT_ACCEPTED");
        s.setSettlementAcceptedAt(clock.instant()); s.setSettlementAcceptedBy(userId);
        event(userId, b, "SETTLEMENT_ACCEPTED", s.getVersion() + ":" + s.getSettlementChargesJson(), "SETTLEMENT_PROPOSED");
        return changed(b, s);
    }

    @Override
    public View refundTransfer(Long userId, String code, RefundPayment r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require("SETTLEMENT_ACCEPTED".equals(s.getStage()), "Còn lần chuyển hoàn đang chờ xác minh; không được ghi đè bằng chứng");
        require(!refunds.hasUnsettledYearlyRefunds(b.getId()), "Cần xử lý xong các khoản hoàn riêng (chuyển dư/đối soát) trước khi hoàn cọc và tiền thuê");
        require(payments.yearlyBalancePaid(b.getId(), s.getBalanceDue()), "Cần thanh toán khoản bổ sung trước khi hoàn cọc");
        require(s.getRentRefund().add(s.getDepositRefund()).signum() > 0, "Không có khoản cần hoàn");
        BigDecimal remaining = s.getRentRefund().add(s.getDepositRefund()).subtract(verifiedRefund(s));
        require(r.amount().signum() > 0 && r.amount().compareTo(remaining) <= 0, "Số chuyển hoàn phải lớn hơn 0 và không vượt phần còn thiếu");
        require(s.getRefundTransfers().stream().noneMatch(t -> t.getBankReference().equals(r.bankReference().trim()))
                && !r.bankReference().trim().equals(s.getRefundReference()), "Mã giao dịch hoàn đã dùng; không gửi lại cùng một lần chuyển");
        photo(userId, b, r.evidenceId(), true);
        var transfer = new YearlyRefundTransfer();
        transfer.setKey(java.util.UUID.randomUUID().toString()); transfer.setBankReference(r.bankReference().trim());
        transfer.setAmount(r.amount()); transfer.setEvidenceId(r.evidenceId()); transfer.setStatus("PENDING"); transfer.setSubmittedAt(clock.instant());
        s.getRefundTransfers().add(transfer);
        event(userId, b, "REFUND_TRANSFER_SUBMITTED", transfer.getKey() + ":" + r, s.getStage());
        s.setRefundReference(r.bankReference().trim());
        s.setRefundEvidenceId(r.evidenceId());
        s.setStage("REFUND_TRANSFER_RECORDED");
        return changed(b, s);
    }

    @Override
    public View confirmRefund(Long userId, String code, Version r) {
        Booking b = booking(userId, code, true, false);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require("REFUND_TRANSFER_RECORDED".equals(s.getStage()), "Chưa có bằng chứng hoàn tiền");
        var transfer = pendingRefund(s);
        verifyRefundPart(b, s, transfer, transfer.getAmount(), userId, false);
        s.setStage(verifiedRefund(s).compareTo(s.getRentRefund().add(s.getDepositRefund())) == 0 ? "REFUND_RECEIVED" : "SETTLEMENT_ACCEPTED");
        return changed(b, s);
    }

    @Override
    public View complete(Long userId, String code, Version r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require(!refunds.hasUnsettledYearlyRefunds(b.getId()), "Còn nghĩa vụ hoàn tiền chưa xử lý");
        require(ledger.listTransactions(b.getId()).stream().noneMatch(tx -> tx.getStatus() == PaymentStatus.PENDING
                || RefundMath.unapplied(tx.getProcessingNote())), "Còn giao dịch đang đối soát");
        require(Set.of("SETTLEMENT_ACCEPTED", "REFUND_RECEIVED").contains(s.getStage()), "Chưa đủ bước quyết toán");
        require(s.getRentRefund().add(s.getDepositRefund()).signum() == 0 || "REFUND_RECEIVED".equals(s.getStage()), "Renter chưa xác nhận nhận tiền hoàn");
        require(payments.yearlyBalancePaid(b.getId(), s.getBalanceDue()), "Còn khoản thanh toán bổ sung");
        require(incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(x -> "CLOSED".equals(x.getStatus())), "Còn report chưa được xử lý/xác nhận");
        b.setStatus(BookingStatus.COMPLETED);
        s.setStage("COMPLETED"); s.setCompletedAt(clock.instant());
        event(userId, b, "YEARLY_COMPLETED", r.toString(), "FINANCIAL_OBLIGATIONS_CLOSED");
        return changed(b, s);
    }

    @Override
    public View reopen(Long userId, String code, Reopen r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        long vehicleId = r.vehicleId() == null ? b.getVehicle().getId() : r.vehicleId();
        boolean known = b.getVehicle().getId().equals(vehicleId) || replacements.findByBookingIdOrderByIdAsc(b.getId()).stream()
                .anyMatch(x -> x.getOldVehicleId().equals(vehicleId) && Set.of("COMPLETED", "CANCELLED").contains(x.getStatus()));
        require(known, "Xe không thuộc lịch sử hợp đồng");
        require(r.availableOn().isAfter(today()), "Chỉ mở lịch từ ngày mai trở đi");
        vehicles.findVehicleByIdForUpdate(vehicleId).orElseThrow(() -> conflict("Không tìm thấy xe"));
        schedules.reopenYearlyVehicle(b.getId(), vehicleId, r.availableOn());
        return changed(b, s);
    }

    @Override
    public View disputeRefund(Long userId, String code, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.RefundComplaint r) {
        var b = booking(userId, code, true, false);
        var s = state(b);
        if (audit.replay("YEARLY", b.getId(), r.requestId(), userId, r.toString())) return view(b, s);
        version(s.getVersion(), r.version());
        require("REFUND_TRANSFER_RECORDED".equals(s.getStage()), "Chỉ báo chưa nhận sau khi nhà xe ghi nhận chuyển tiền");
        require(r.receivedAmount().compareTo(s.getRentRefund().add(s.getDepositRefund())) < 0, "Nếu đã nhận đủ tiền, hãy xác nhận nhận tiền hoàn");
        var image = photo(userId, b, r.evidenceId(), false);
        var opened = disputes.open(b, b.getRenter().getUser(), com.fptufa26.ecomotion.dispute.enums.DisputeCategory.YEARLY_REFUND,
                null, "Số tiền người thuê khai đã nhận: " + r.receivedAmount() + ". " + r.note(), List.of(image));
        s.setRefundReviewDisputeId(opened.disputeId());
        audit.record("YEARLY", b.getId(), r.requestId(), userId, "REFUND_DISPUTE", r.toString(), s.getStage());
        return changed(b, s);
    }

    @Override
    public View escalateIncident(Long userId, String code, Long incidentId, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.Complaint r) {
        var b = booking(userId, code, true, false);
        var s = state(b);
        if (audit.replay("YEARLY", b.getId(), r.requestId(), userId, incidentId + ":" + r)) return view(b, s);
        version(s.getVersion(), r.version());
        var incident = issue(b, incidentId);
        require(!"CLOSED".equals(incident.getStatus()) && !"COMPLETED".equals(s.getStage()), "Report đã đóng");
        var image = photo(userId, b, r.evidenceId(), false);
        var opened = disputes.open(b, b.getRenter().getUser(), com.fptufa26.ecomotion.dispute.enums.DisputeCategory.YEARLY_INCIDENT,
                null, "Report #" + incidentId + ": " + r.note(), List.of(image));
        incident.setReviewDisputeId(opened.disputeId());
        incidents.saveAndFlush(incident);
        audit.record("YEARLY", b.getId(), r.requestId(), userId, "INCIDENT_DISPUTE", incidentId + ":" + r, s.getStage());
        return changed(b, s);
    }

    private void requireReview(Booking b, Long disputeId, com.fptufa26.ecomotion.dispute.enums.DisputeCategory category) {
        var d = disputes.getDisputeById(disputeId).orElseThrow(() -> conflict("Không có hồ sơ xem xét"));
        require(d.getBooking().getId().equals(b.getId()) && d.getCategory() == category
                && Set.of(com.fptufa26.ecomotion.dispute.enums.DisputeStatus.OPEN, com.fptufa26.ecomotion.dispute.enums.DisputeStatus.IN_REVIEW).contains(d.getStatus()), "Hồ sơ không thuộc hợp đồng hoặc đã được giải quyết");
    }

    @Override
    public View adjudicateRefund(Long adminId, String code, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.RefundRuling r) {
        var b = adminBooking(adminId, code);
        var s = state(b);
        if (audit.replay("YEARLY", b.getId(), r.requestId(), adminId, r.toString())) return view(b, s);
        version(s.getVersion(), r.version());
        requireReview(b, r.disputeId(), com.fptufa26.ecomotion.dispute.enums.DisputeCategory.YEARLY_REFUND);
        require(Objects.equals(s.getRefundReviewDisputeId(), r.disputeId()) && "REFUND_TRANSFER_RECORDED".equals(s.getStage()), "Sai hồ sơ hoàn tiền đang xem xét");
        var total = s.getRentRefund().add(s.getDepositRefund());
        var previous = s.getRefundVerifiedReceived() == null ? BigDecimal.ZERO : s.getRefundVerifiedReceived();
        String previousState = s.getStage() + ":" + previous + ":" + s.getRefundReference();
        require(r.receivedAmount().compareTo(previous) >= 0 && r.receivedAmount().compareTo(total) <= 0, "Số tiền nhận đã xác minh không được giảm hoặc vượt tổng phải hoàn");
        if (!"HOLD".equals(r.outcome())) {
            if ("CONFIRMED_RECEIVED".equals(r.outcome())) {
                require(r.receivedAmount().compareTo(total) == 0, "Chưa xác minh nhận đủ tổng tiền hoàn");
                var transfer = pendingRefund(s);
                verifyRefundPart(b, s, transfer, r.receivedAmount().subtract(previous), adminId, true);
                s.setStage("REFUND_RECEIVED");
            } else {
                require("PARTIAL_RECEIVED".equals(r.outcome()) || "FAILED_VERIFIED".equals(r.outcome()), "Kết luận không hợp lệ");
                require(r.receivedAmount().compareTo(total) < 0, "Đã nhận đủ tiền");
                if ("PARTIAL_RECEIVED".equals(r.outcome())) require(r.receivedAmount().compareTo(previous) > 0, "Cần xác minh số tiền nhận thêm");
                else require(r.receivedAmount().compareTo(previous) == 0, "Kết luận chuyển thất bại không được ghi thêm tiền nhận");
                var transfer = pendingRefund(s);
                verifyRefundPart(b, s, transfer, r.receivedAmount().subtract(previous), adminId, true);
                s.setStage("SETTLEMENT_ACCEPTED");
            }
            s.setRefundVerifiedReceived(r.receivedAmount());
            disputes.resolveYearlyReview(r.disputeId(), adminId, r.note());
        }
        audit.record("YEARLY", b.getId(), r.requestId(), adminId, "REFUND_" + r.outcome(), r.toString(), previousState);
        return changed(b, s);
    }

    @Override
    public View adjudicateIncident(Long adminId, String code, com.fptufa26.ecomotion.booking.dto.request.YearlyReviewRequests.IncidentRuling r) {
        var b = adminBooking(adminId, code);
        var s = state(b);
        if (audit.replay("YEARLY", b.getId(), r.requestId(), adminId, r.toString())) return view(b, s);
        version(s.getVersion(), r.version());
        requireReview(b, r.disputeId(), com.fptufa26.ecomotion.dispute.enums.DisputeCategory.YEARLY_INCIDENT);
        var i = issue(b, r.incidentId());
        require(Objects.equals(i.getReviewDisputeId(), r.disputeId()) && !"CLOSED".equals(i.getStatus()), "Report không thuộc hồ sơ phân xử");
        String previous = i.getStatus() + ":" + i.getApprovedAmount() + ":" + i.getTransferEvidenceId() + ":" + i.getBankReference();
        if ("HOLD".equals(r.outcome())) {
            audit.record("YEARLY", b.getId(), r.requestId(), adminId, "INCIDENT_HOLD", r.toString(), previous);
            return view(b, s);
        }
        if (i.getTransferEvidenceId() != null) {
            require(Set.of("TRANSFER_VERIFIED", "FAILED_TRANSFER").contains(r.outcome()), "Cần đối soát giao dịch hoàn ứng cũ trước khi kết luận lại");
            require(r.approvedAmount().signum() == 0, "Không sửa số tiền hoàn ứng trong bước đối soát giao dịch");
            i.setResolutionNote(r.note() + " | " + r.evidenceReference());
            i.setStatus("TRANSFER_VERIFIED".equals(r.outcome()) ? "CLOSED" : "AWAITING_TRANSFER");
            if ("FAILED_TRANSFER".equals(r.outcome())) { i.setTransferEvidenceId(null); i.setBankReference(null); }
            incidents.saveAndFlush(i);
            disputes.resolveYearlyReview(r.disputeId(), adminId, r.note());
            audit.record("YEARLY", b.getId(), r.requestId(), adminId, "INCIDENT_" + r.outcome(), r.toString(), previous);
            return changed(b, s);
        }
        require(!Set.of("TRANSFER_VERIFIED", "FAILED_TRANSFER").contains(r.outcome()), "Chưa có giao dịch hoàn ứng cần đối soát");
        require(replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(x -> Set.of("COMPLETED", "CANCELLED").contains(x.getStatus())), "Cần xử lý hoặc hủy yêu cầu đổi xe trước khi phân xử report");
        require(r.approvedAmount().compareTo(i.getInvoiceAmount()) <= 0 && (r.approvedAmount().signum() == 0 || "REIMBURSE".equals(r.outcome())), "Khoản hoàn ứng không hợp lệ");
        require(!"REIMBURSE".equals(r.outcome()) || r.approvedAmount().signum() > 0, "Hoàn ứng cần số tiền lớn hơn 0");
        String responsibility = r.responsibility() != null ? r.responsibility()
                : i.getProposedCategory() == null ? i.getCategory() : i.getProposedCategory();
        require(Set.of("OWNER_MAINTENANCE", "RENTER_DAMAGE").contains(responsibility), "Trách nhiệm phân xử không hợp lệ");
        require("OWNER_MAINTENANCE".equals(responsibility) || !Set.of("REIMBURSE", "END_PROVIDER", "REPLACE").contains(r.outcome()), "Report hư hỏng phía người thuê không được tự chuyển thành hoàn ứng phía nhà xe");
        require(!"OWNER_MAINTENANCE".equals(responsibility) || !"RENTER_PAID".equals(r.outcome()), "Không chuyển chi phí hao mòn phía nhà xe sang người thuê");
        if ("END_PROVIDER".equals(r.outcome())) {
            require(b.getActualEndAt() == null && "TERMS_ACCEPTED".equals(s.getStage()), "Không thể đổi chế độ trả sau khi đã nhận lại xe");
            s.setReturnMode("PROVIDER_FAILURE"); s.setFailureOn(i.getOccurredOn()); s.setRequestedReturnOn(today()); s.setStage("RETURN_REQUESTED");
        }
        i.setCategory(responsibility);
        i.setOutcome(r.outcome()); i.setResolutionNote(r.note() + " | " + r.evidenceReference()); i.setApprovedAmount(r.approvedAmount());
        i.setStatus("REIMBURSE".equals(r.outcome()) && r.approvedAmount().signum() > 0 ? "AWAITING_TRANSFER" : "CLOSED");
        incidents.saveAndFlush(i);
        disputes.resolveYearlyReview(r.disputeId(), adminId, r.note());
        audit.record("YEARLY", b.getId(), r.requestId(), adminId, "INCIDENT_RULING", r.toString(), previous);
        return changed(b, s);
    }

    @Override
    public Incident report(Long userId, String code, Report r) {
        Booking b = booking(userId, code, true, false);
        YearlyWorkflow s = active(b);
        require("TERMS_ACCEPTED".equals(s.getStage()), "Không báo sự cố sau khi đã yêu cầu trả xe");
        var old = incidents.findByBookingIdAndRequestId(b.getId(), r.requestId());
        if (old.isPresent()) {
            YearlyIncident i = old.get();
            require(i.getCategory().equals(r.category()) && i.getDescription().equals(r.description())
                    && i.getOccurredOn().equals(r.occurredOn()) && i.getEvidenceId().equals(r.evidenceId())
                    && i.getInvoiceAmount().compareTo(r.invoiceAmount()) == 0, "Mã yêu cầu đã dùng cho nội dung khác");
            return incident(i);
        }
        require(incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().noneMatch(i -> i.getEvidenceId().equals(r.evidenceId())), "Bằng chứng này đã dùng trong report khác");
        require(incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(i -> "CLOSED".equals(i.getStatus())), "Xử lý report hiện tại trước khi mở report mới");
        require(Set.of("OWNER_MAINTENANCE", "RENTER_DAMAGE").contains(r.category()), "Loại sự cố không hợp lệ");
        require(!r.occurredOn().isBefore(started(b)) && !r.occurredOn().isAfter(today()), "Ngày sự cố không hợp lệ");
        photo(userId, b, r.evidenceId(), false);
        YearlyIncident i = new YearlyIncident();
        i.setBookingId(b.getId());
        i.setRequestId(r.requestId());
        i.setCategory(r.category()); i.setReportedCategory(r.category());
        i.setOccurredOn(r.occurredOn());
        i.setDescription(r.description());
        i.setEvidenceId(r.evidenceId());
        i.setInvoiceAmount(r.invoiceAmount());
        i.setStatus("OPEN");
        incidents.saveAndFlush(i);
        notifications.changed(b, "BOOKING_CHANGED");
        return incident(i);
    }

    @Override
    public Incident resolve(Long userId, String code, Long id, Resolution r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = active(b);
        YearlyIncident i = issue(b, id);
        version(i.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Report đang được Admin xem xét; chờ kết luận trước khi thay đổi");
        require(Set.of("OPEN", "RESOLUTION_PROPOSED").contains(i.getStatus()) && "TERMS_ACCEPTED".equals(s.getStage()),
                "Report không còn chờ đề xuất");
        String responsibility = r.responsibility() == null ? i.getCategory() : r.responsibility();
        require(Set.of("OWNER_MAINTENANCE", "RENTER_DAMAGE").contains(responsibility), "Trách nhiệm đề xuất không hợp lệ");
        require(Set.of("FIXED", "REIMBURSE", "RENTER_PAID", "END_PROVIDER", "REPLACE").contains(r.outcome()), "Kết quả không hợp lệ");
        boolean owner = "OWNER_MAINTENANCE".equals(responsibility);
        require(owner || Set.of("RENTER_PAID", "FIXED").contains(r.outcome()), "Nhánh người thuê không dùng hoàn ứng/đổi/kết thúc do provider");
        require(!owner || !"RENTER_PAID".equals(r.outcome()), "Không gán hao mòn cho renter");
        require(r.approvedAmount().signum() == 0 || "REIMBURSE".equals(r.outcome()), "Chỉ hoàn ứng có số tiền được duyệt");
        require(!"REIMBURSE".equals(r.outcome()) || r.approvedAmount().signum() > 0, "Hoàn ứng cần số tiền lớn hơn 0");
        require(r.approvedAmount().compareTo(i.getInvoiceAmount()) <= 0, "Hoàn ứng vượt số tiền yêu cầu");
        event(userId, b, "INCIDENT_PROPOSED", id + ":" + r, i.getStatus() + ":" + i.getOutcome());
        i.setProposedCategory(responsibility); i.setOutcome(r.outcome()); i.setResolutionNote(r.note());
        i.setApprovedAmount(r.approvedAmount()); i.setStatus("RESOLUTION_PROPOSED");
        incidents.flush();
        notifications.changed(b, "BOOKING_CHANGED");
        return incident(i);
    }

    @Override
    public Incident incidentTransfer(Long userId, String code, Long id, Transfer r) {
        Booking b = booking(userId, code, true, true);
        YearlyIncident i = issue(b, id);
        version(i.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Report đang được Admin xem xét; chờ kết luận trước khi thay đổi");
        require(Set.of("AWAITING_TRANSFER", "TRANSFER_RECORDED").contains(i.getStatus()), "Report không chờ hoàn ứng");
        photo(userId, b, r.evidenceId(), true);
        i.setTransferEvidenceId(r.evidenceId());
        i.setBankReference(r.bankReference());
        i.setStatus("TRANSFER_RECORDED");
        incidents.flush();
        return incident(i);
    }

    @Override
    public Incident incidentConfirm(Long userId, String code, Long id, Version r) {
        Booking b = booking(userId, code, true, false);
        YearlyIncident i = issue(b, id);
        version(i.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Report đang được Admin xem xét; chờ kết luận trước khi thay đổi");
        if ("RESOLUTION_PROPOSED".equals(i.getStatus())) {
            YearlyWorkflow s = active(b);
            require("TERMS_ACCEPTED".equals(s.getStage()), "Hợp đồng không còn ở bước xử lý sự cố");
            i.setCategory(i.getProposedCategory() == null ? i.getCategory() : i.getProposedCategory());
            if ("END_PROVIDER".equals(i.getOutcome())) {
                require(replacements.findByBookingIdOrderByIdAsc(b.getId()).stream()
                        .allMatch(x -> Set.of("COMPLETED", "CANCELLED").contains(x.getStatus())), "Còn đề nghị đổi xe");
                s.setReturnMode("PROVIDER_FAILURE"); s.setFailureOn(i.getOccurredOn());
                s.setRequestedReturnOn(today()); s.setStage("RETURN_REQUESTED"); workflows.save(s);
            }
            i.setStatus("REIMBURSE".equals(i.getOutcome()) && i.getApprovedAmount().signum() > 0 ? "AWAITING_TRANSFER"
                    : "REPLACE".equals(i.getOutcome()) ? "AWAITING_CONFIRMATION" : "CLOSED");
            event(userId, b, "INCIDENT_ACCEPTED", id + ":" + i.getCategory() + ":" + i.getOutcome(), "RESOLUTION_PROPOSED");
            incidents.flush(); notifications.changed(b, "BOOKING_CHANGED");
            return incident(i);
        }
        require(Set.of("AWAITING_CONFIRMATION", "TRANSFER_RECORDED").contains(i.getStatus()), "Chưa có kết quả/bằng chứng để xác nhận");
        require(!"REPLACE".equals(i.getOutcome()), "Report đổi xe đóng khi nhận xe thay thế");
        i.setStatus("CLOSED");
        incidents.flush();
        notifications.changed(b, "BOOKING_CHANGED");
        return incident(i);
    }

    @Override
    public Incident rejectResolution(Long userId, String code, Long id, IncidentReply r) {
        Booking b = booking(userId, code, true, false);
        active(b);
        YearlyIncident i = issue(b, id);
        version(i.getVersion(), r.version());
        require("RESOLUTION_PROPOSED".equals(i.getStatus()) && !disputes.hasOpenDispute(b.getId()), "Không còn đề xuất để phản hồi");
        event(userId, b, "INCIDENT_REJECTED", id + ":" + r.note(), i.getOutcome() + ":" + i.getProposedCategory());
        i.setStatus("OPEN"); i.setProposedCategory(null); i.setOutcome(null);
        i.setResolutionNote("Người thuê chưa đồng ý: " + r.note()); i.setApprovedAmount(BigDecimal.ZERO);
        incidents.flush(); notifications.changed(b, "BOOKING_CHANGED");
        return incident(i);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReplacementOption> replacementOptions(Long userId, String code, Long incidentId, LocalDate plannedOn) {
        Booking b = booking(userId, code, false, true);
        require(b.getStatus() == BookingStatus.IN_PROGRESS && b.getActualStartAt() != null && b.getActualEndAt() == null,
                "Chỉ đổi xe khi hợp đồng đang thuê");
        require(!disputes.hasOpenDispute(b.getId()), "Report đang được xem xét");
        YearlyIncident i = issue(b, incidentId);
        require("REPLACE".equals(i.getOutcome()) && "AWAITING_CONFIRMATION".equals(i.getStatus()),
                "Report chưa chấp nhận đổi xe");
        require(plannedOn != null && !plannedOn.isBefore(today())
                && plannedOn.isBefore(b.getPlannedEndAt().atZone(ZONE).toLocalDate()), "Ngày đổi xe không hợp lệ");
        var options = new java.util.ArrayList<ReplacementOption>();
        for (var target : vehicles.findApprovedProviderVehicles(b.getVehicle().getProvider().getId())) {
            if (target.getId().equals(b.getVehicle().getId())) continue;
            var window = AvailabilityPolicy.bufferedWindow(target, plannedOn.atStartOfDay(ZONE).toInstant(),
                    YearlyRentalRules.reservationEnd(b.getPlannedEndAt()));
            if (!schedules.isRangeFree(target.getId(), window.start(), window.end())) continue;
            try {
                DrivingLicenseEligibilityPolicy.requireEligibleForBooking(b.getRenter(), target, clock.instant(), b.getPlannedEndAt());
                BigDecimal price = pricing.yearlyPlanPrice(target.getId());
                var notes = com.fptufa26.ecomotion.booking.policy.VehicleChangePolicy.qualityDowngrades(b.getVehicle(), target);
                options.add(new ReplacementOption(vehicleInfo(target), price, !notes.isEmpty(), String.join("; ", notes)));
            } catch (ConflictException ex) {
                continue;
            }
        }
        return List.copyOf(options);
    }

    @Override
    public Replacement replacement(Long userId, String code, Replace r) {
        Booking b = booking(userId, code, true, true);
        require(!disputes.hasOpenDispute(b.getId()), "Chờ kết luận Admin trước khi thay đổi xe thay thế");
        YearlyWorkflow s = active(b);
        YearlyIncident i = issue(b, r.incidentId());
        require("TERMS_ACCEPTED".equals(s.getStage()) && "REPLACE".equals(i.getOutcome()) && "AWAITING_CONFIRMATION".equals(i.getStatus()), "Report chưa chấp nhận đổi xe");
        require(!r.plannedOn().isBefore(today()) && r.plannedOn().isBefore(b.getPlannedEndAt().atZone(ZONE).toLocalDate()), "Ngày hẹn đổi xe không hợp lệ");
        require(replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(x -> Set.of("COMPLETED", "CANCELLED").contains(x.getStatus())), "Đã có yêu cầu đổi xe đang mở");
        var target = vehicles.findVehicleByIdForUpdate(r.vehicleId()).orElseThrow(() -> conflict("Không tìm thấy xe thay thế"));
        require(target.getProvider().getId().equals(b.getVehicle().getProvider().getId()) && !target.getId().equals(b.getVehicle().getId()), "Phải chọn xe khác cùng provider");
        require(target.getStatus() == VehicleStatus.APPROVED, "Xe chưa được duyệt");
        DrivingLicenseEligibilityPolicy.requireEligibleForBooking(
                b.getRenter(), target, clock.instant(), b.getPlannedEndAt());
        var window = AvailabilityPolicy.bufferedWindow(target, r.plannedOn().atStartOfDay(ZONE).toInstant(), YearlyRentalRules.reservationEnd(b.getPlannedEndAt()));
        require(schedules.isRangeFree(target.getId(), window.start(), window.end()), "Xe thay thế đã có lịch");
        // B1: ghi nhận giá xe cũ/xe mới và chất lượng để khách xác nhận trước khi đổi; không đổi hợp đồng/cọc.
        var downgrades = com.fptufa26.ecomotion.booking.policy.VehicleChangePolicy.qualityDowngrades(b.getVehicle(), target);
        BigDecimal currentPrice = replacements.findByBookingIdOrderByIdAsc(b.getId()).stream()
                .filter(y -> "COMPLETED".equals(y.getStatus()) && y.getNewPlanPrice() != null)
                .max(java.util.Comparator.comparing(YearlyReplacement::getExchangedOn)).map(YearlyReplacement::getNewPlanPrice)
                .orElse(b.getTotalAmount());
        YearlyReplacement x = new YearlyReplacement();
        x.setOldPlanPrice(currentPrice);
        x.setNewPlanPrice(pricing.yearlyPlanPrice(target.getId()));
        x.setLowerQuality(!downgrades.isEmpty());
        x.setQualityNotes(downgrades.isEmpty() ? null : String.join("; ", downgrades));
        x.setQualityAcknowledged(false);
        x.setBookingId(b.getId());
        x.setIncidentId(i.getId());
        x.setOldVehicleId(b.getVehicle().getId());
        x.setNewVehicleId(target.getId());
        x.setPlannedOn(r.plannedOn());
        x.setStatus("PROPOSED");
        replacements.saveAndFlush(x);
        return replacement(x);
    }

    @Override
    public Replacement cancelReplacement(Long userId, String code, Long id, Version r) {
        Booking b = booking(userId, code, true, null);
        active(b);
        YearlyReplacement x = exchange(b, id);
        version(x.getVersion(), r.version());
        require(!Set.of("COMPLETED", "CANCELLED").contains(x.getStatus()), "Yêu cầu đổi xe đã kết thúc");
        x.setStatus("CANCELLED");
        issue(b, x.getIncidentId()).setStatus("OPEN");
        replacements.flush();
        notifications.changed(b, "BOOKING_CHANGED");
        return replacement(x);
    }

    @Override
    public Replacement acceptReplacement(Long userId, String code, Long id, AcceptReplacement r) {
        Booking b = booking(userId, code, true, false);
        require(!disputes.hasOpenDispute(b.getId()), "Chờ kết luận Admin trước khi thay đổi xe thay thế");
        YearlyReplacement x = exchange(b, id);
        version(x.getVersion(), r.version());
        require("PROPOSED".equals(x.getStatus()), "Yêu cầu đổi xe không còn chờ xác nhận");
        require(!Boolean.TRUE.equals(x.getLowerQuality()) || Boolean.TRUE.equals(r.acknowledgeLowerQuality()),
                "Xe thay thế có chất lượng thấp hơn (" + x.getQualityNotes() + "). Cần khách xác nhận đồng ý trước khi đổi xe");
        x.setQualityAcknowledged(Boolean.TRUE.equals(r.acknowledgeLowerQuality()));
        x.setStatus("ACCEPTED");
        replacements.flush();
        return replacement(x);
    }

    @Override
    public Replacement replacementInspection(Long userId, String code, Long id, Exchange r) {
        Booking b = booking(userId, code, true, true);
        require(!disputes.hasOpenDispute(b.getId()), "Chờ kết luận Admin trước khi thay đổi xe thay thế");
        YearlyWorkflow s = active(b);
        YearlyReplacement x = exchange(b, id);
        version(x.getVersion(), r.version());
        require(Set.of("ACCEPTED", "HANDOVER_PROPOSED").contains(x.getStatus()), "Renter chưa đồng ý đổi xe");
        require(r.oldFinalKm() >= initialKm(b, s), "Km xe cũ không hợp lệ");
        require(replacements.findByBookingIdOrderByIdAsc(b.getId()).stream()
                .filter(y -> "COMPLETED".equals(y.getStatus()))
                .allMatch(y -> !r.exchangedOn().isBefore(y.getExchangedOn())), "Ngày đổi xe trước lần đổi đã ghi nhận");
        require(!r.exchangedOn().isBefore(started(b)) && !r.exchangedOn().isAfter(today())
                && r.exchangedOn().isBefore(b.getPlannedEndAt().atZone(ZONE).toLocalDate()), "Ngày đổi xe không hợp lệ");
        photo(userId, b, r.oldEvidenceId(), true);
        photo(userId, b, r.newEvidenceId(), true);
        x.setExchangedOn(r.exchangedOn());
        x.setOldFinalKm(r.oldFinalKm());
        x.setNewInitialKm(r.newInitialKm());
        x.setOldEvidenceId(r.oldEvidenceId());
        x.setNewEvidenceId(r.newEvidenceId());
        x.setStatus("HANDOVER_PROPOSED");
        replacements.flush();
        return replacement(x);
    }

    @Override
    public Replacement confirmReplacement(Long userId, String code, Long id, Version r) {
        Booking b = booking(userId, code, true, false);
        require(!disputes.hasOpenDispute(b.getId()), "Chờ kết luận Admin trước khi thay đổi xe thay thế");
        YearlyWorkflow s = active(b);
        YearlyReplacement x = exchange(b, id);
        version(x.getVersion(), r.version());
        require("HANDOVER_PROPOSED".equals(x.getStatus()) && "TERMS_ACCEPTED".equals(s.getStage()), "Chưa có biên bản đổi xe");
        require(!Boolean.TRUE.equals(x.getLowerQuality()) || Boolean.TRUE.equals(x.getQualityAcknowledged()), "Khách chưa xác nhận xe chất lượng thấp hơn");
        long first = Math.min(x.getOldVehicleId(), x.getNewVehicleId());
        long second = Math.max(x.getOldVehicleId(), x.getNewVehicleId());
        vehicles.findVehicleByIdForUpdate(first).orElseThrow(() -> conflict("Không tìm thấy xe"));
        vehicles.findVehicleByIdForUpdate(second).orElseThrow(() -> conflict("Không tìm thấy xe"));
        var target = vehicles.findVehicleById(x.getNewVehicleId()).orElseThrow(() -> conflict("Không tìm thấy xe"));
        require(target.getProvider().getId().equals(b.getVehicle().getProvider().getId())
                && target.getStatus() == VehicleStatus.APPROVED, "Xe thay thế không còn hợp lệ");
        DrivingLicenseEligibilityPolicy.requireEligibleForBooking(
                b.getRenter(), target, clock.instant(), b.getPlannedEndAt());
        require(x.getExchangedOn() != null && !x.getExchangedOn().isAfter(today())
                && x.getOldFinalKm() != null && x.getNewInitialKm() != null
                && x.getNewInitialKm() >= 0 && x.getOldFinalKm() >= initialKm(b, s), "Biên bản đổi xe hoặc km không còn hợp lệ");
        mileageLegs(b, s, x.getExchangedOn(), x.getOldFinalKm());
        var oldVehicle = b.getVehicle();
        require(oldVehicle.getId().equals(x.getOldVehicleId()), "Xe đang sử dụng đã thay đổi");
        schedules.moveBooking(b, target, x.getExchangedOn().atStartOfDay(ZONE).toInstant(), b.getPlannedEndAt());
        schedules.markYearlyInspection(b, oldVehicle, x.getExchangedOn());
        s.setCompletedKm(Math.addExact(s.getCompletedKm(), x.getOldFinalKm() - initialKm(b, s)));
        s.setCurrentInitialKm(x.getNewInitialKm());
        b.setVehicle(target);
        x.setStatus("COMPLETED");
        issue(b, x.getIncidentId()).setStatus("CLOSED");
        workflows.saveAndFlush(s);
        replacements.flush();
        notifications.changed(b, "BOOKING_CHANGED");
        return replacement(x);
    }


    private List<ExtraCharge> storedCharges(YearlyWorkflow s) {
        if (s.getSettlementChargesJson() == null || s.getSettlementChargesJson().isBlank()) return List.of();
        try {
            return List.of(new tools.jackson.databind.ObjectMapper().readValue(s.getSettlementChargesJson(), ExtraCharge[].class));
        } catch (RuntimeException ex) {
            throw conflict("Không đọc được chi tiết phụ phí đã lưu; cần kiểm tra hồ sơ, không tính mặc định bằng 0");
        }
    }

    private void validateCharges(Long userId, Booking b, Settlement r) {
        require(r.charges() != null, "Quyết toán cần danh sách từng khoản phụ phí; nếu không có hãy gửi danh sách rỗng");
        BigDecimal total = BigDecimal.ZERO;
        var evidenceIds = new java.util.HashSet<Long>();
        for (ExtraCharge charge : r.charges()) {
            require(evidenceIds.add(charge.evidenceId()), "Không dùng cùng một bằng chứng cho nhiều khoản phụ phí");
            photo(users.findById(userId).map(u -> u.getRole() == Role.ADMIN).orElse(false) ? b.getVehicle().getProvider().getUser().getId() : userId, b, charge.evidenceId(), true);
            require(incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().noneMatch(i ->
                    Objects.equals(i.getEvidenceId(), charge.evidenceId()) && "RENTER_PAID".equals(i.getOutcome())),
                    "Khoản renter đã trả không được thu lại");
            if (charge.incidentId() != null) {
                var incident = issue(b, charge.incidentId());
                require("RENTER_DAMAGE".equals(incident.getCategory()) && !"RENTER_PAID".equals(incident.getOutcome()),
                        "Không thu lại khoản đã trả hoặc chi phí thuộc provider");
            }
            total = total.add(charge.amount());
        }
        require(total.compareTo(r.otherCharges()) == 0, "Tổng phụ phí không khớp từng khoản");
    }

    private List<MileageLeg> mileageLegs(Booking b, YearlyWorkflow s, LocalDate returnedOn, long finalKm) {
        var handover = handovers.findById(b.getId()).filter(h -> "RECEIVED".equals(h.getStage()) && h.getInitialKm() != null)
                .orElseThrow(() -> conflict("Thiếu biên bản km nhận xe ban đầu"));
        var done = replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().filter(x -> "COMPLETED".equals(x.getStatus()))
                .sorted(java.util.Comparator.comparing(YearlyReplacement::getExchangedOn,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())).thenComparing(YearlyReplacement::getId)).toList();
        long initial = handover.getInitialKm();
        Long vehicleId = done.isEmpty() ? b.getVehicle().getId() : done.get(0).getOldVehicleId();
        LocalDate from = started(b);
        long completed = 0L;
        var legs = new java.util.ArrayList<MileageLeg>();
        for (var x : done) {
            require(Objects.equals(vehicleId, x.getOldVehicleId()) && x.getExchangedOn() != null
                    && !x.getExchangedOn().isBefore(from) && x.getOldFinalKm() != null
                    && x.getOldFinalKm() >= initial && x.getNewInitialKm() != null && x.getNewInitialKm() >= 0,
                    "Lịch sử đổi xe hoặc số km không liên tục; cần đối soát trước khi quyết toán");
            long distance = x.getOldFinalKm() - initial;
            legs.add(new MileageLeg(vehicleId, vehicleName(vehicleId), from, x.getExchangedOn(), initial, x.getOldFinalKm(), distance));
            completed = Math.addExact(completed, distance);
            initial = x.getNewInitialKm(); vehicleId = x.getNewVehicleId(); from = x.getExchangedOn();
        }
        require(Objects.equals(vehicleId, b.getVehicle().getId()) && completed == s.getCompletedKm()
                && initial == initialKm(b, s), "Tổng km đã lưu không khớp biên bản các lần đổi xe");
        require(returnedOn != null && !returnedOn.isBefore(from) && finalKm >= initial,
                "Ngày trả trước lần đổi xe cuối hoặc km trả nhỏ hơn km nhận của xe hiện tại");
        legs.add(new MileageLeg(vehicleId, vehicleName(vehicleId), from, returnedOn, initial, finalKm, finalKm - initial));
        return List.copyOf(legs);
    }

    private ReturnAssessment assessStoredReturn(Booking b, YearlyWorkflow s, BigDecimal other, BigDecimal deduction, List<ExtraCharge> charges) {
        require(s.getReturnedOn() != null && s.getFinalKm() != null && s.getReturnMode() != null, "Chưa có biên bản kiểm định trả xe");
        var legs = mileageLegs(b, s, s.getReturnedOn(), s.getFinalKm());
        long total = legs.stream().mapToLong(MileageLeg::usedKm).reduce(0L, Math::addExact);
        require(s.getTotalKm() != null && total == s.getTotalKm(), "Tổng km trả xe không khớp lịch sử bàn giao");
        return assess(b, s, s.getReturnedOn(), s.getReturnMode(), total, other, deduction, legs, charges);
    }

    private ReturnAssessment assess(Booking b, YearlyWorkflow s, LocalDate returnedOn, String mode, long totalKm,
                                    BigDecimal other, BigDecimal deduction, List<MileageLeg> legs, List<ExtraCharge> charges) {
        LocalDate start = started(b), due = b.getPlannedEndAt().atZone(ZONE).toLocalDate();
        require(!returnedOn.isBefore(start), "Ngày trả trước ngày nhận");
        require(!"STANDARD".equals(mode) || !returnedOn.isBefore(due), "Trả trước hạn cần chọn trả sớm hoặc thanh toán đủ hợp đồng");
        var months = YearlyRentalRules.installments(b.getTotalAmount(), s.getRoundingUnit());
        int used = YearlyRentalRules.startedMonths(start, returnedOn), charged = "FULL_CONTRACT".equals(mode) ? 12 : used;
        if ("PROVIDER_FAILURE".equals(mode)) {
            require(s.getFailureOn() != null && !s.getFailureOn().isAfter(returnedOn), "Ngày sự cố không hợp lệ");
            charged = YearlyRentalRules.startedMonths(start, s.getFailureOn()) - 1;
        }
        int allowanceMonths = "FULL_CONTRACT".equals(mode) ? 12 : used;
        long allowance = BigDecimal.valueOf(s.getAnnualKm()).multiply(BigDecimal.valueOf(allowanceMonths))
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.DOWN).longValueExact();
        long excess = Math.max(0L, totalKm - allowance);
        BigDecimal rent = months.subList(0, charged).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal early = "EARLY".equals(mode) ? YearlyRentalRules.earlyTerminationFee(b.getTotalAmount(), rent, s.getRoundingUnit()) : BigDecimal.ZERO;
        long lateDays = Math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(due, returnedOn));
        long chargedLate = Math.max(0L, lateDays - s.getGraceDays());
        BigDecimal late = YearlyRentalRules.lateFee(due, returnedOn, s.getGraceDays(), s.getLateDailyRate());
        BigDecimal km = BigDecimal.valueOf(excess).multiply(s.getExcessKmRate());
        BigDecimal discount = vehicleChangeDiscount(b, start, due, "FULL_CONTRACT".equals(mode) || returnedOn.isAfter(due) ? due : returnedOn);
        var cash = payments.yearlyCash(b.getId());
        BigDecimal obligation = rent.add(early).add(late).add(km).add(other).subtract(discount).max(BigDecimal.ZERO);
        YearlySettlementMath.Result result;
        try { result = YearlySettlementMath.settle(obligation, cash.rentPaid(), cash.depositHeld(), deduction); }
        catch (IllegalArgumentException ex) { throw conflict("Khấu trừ vượt số cọc hoặc khoản thiếu thực tế; không được thu trùng"); }
        BigDecimal maxDeduction = obligation.subtract(cash.rentPaid()).max(BigDecimal.ZERO).min(cash.depositHeld());
        Money money = new Money(rent, early, late, km, other, cash.rentPaid(), cash.depositHeld(),
                result.rentRefund(), result.depositRefund(), result.balanceDue(), deduction, discount, s.getSettlementNote());
        return new ReturnAssessment(s.getVersion() == null ? 0L : s.getVersion(), s.getStage(), mode, start, due,
                returnedOn, b.getActualEndAt() != null, used, charged, allowanceMonths, s.getAnnualKm(),
                allowance, totalKm, excess, s.getExcessKmRate(), lateDays, s.getGraceDays(), chargedLate,
                s.getLateDailyRate(), legs, charges, money, maxDeduction);
    }

    private void calculateSettlement(Booking b, YearlyWorkflow s, Settlement r) {
        var calculation = assessStoredReturn(b, s, r.otherCharges(), r.depositDeduction(),
                r.charges() == null ? List.of() : r.charges());
        Money m = calculation.money();
        s.setRentDue(m.rentDue()); s.setEarlyFee(m.earlyFee()); s.setLateFee(m.lateFee());
        s.setExcessKmFee(m.excessKmFee()); s.setOtherCharges(m.otherCharges());
        s.setSettlementEvidenceId(r.evidenceId()); s.setSettlementNote(r.note());
        s.setRentPaid(m.rentPaid()); s.setDepositHeld(m.depositHeld()); s.setDepositDeduction(m.depositDeduction());
        s.setVehicleChangeDiscount(m.vehicleChangeDiscount()); s.setBalanceDue(m.balanceDue());
        s.setRentRefund(m.rentRefund()); s.setDepositRefund(m.depositRefund());
        s.setSettlementChargesJson(new tools.jackson.databind.ObjectMapper().writeValueAsString(
                r.charges() == null ? List.of() : r.charges()));
        s.setStage("SETTLEMENT_PROPOSED");
    }

    private Booking adminBooking(Long adminId, String code) {
        requireAdmin(adminId);
        var candidate = bookings.findByBookingCode(code.trim().toUpperCase(java.util.Locale.ROOT)).orElseThrow(() -> conflict("Không có hợp đồng"));
        var b = ledger.requireBookingForUpdate(candidate.getId());
        require(b.getPlan().getPlanType() == PlanType.YEARLY, "Chỉ áp dụng hợp đồng năm");
        return b;
    }

    @Override
    public View adminView(Long adminId, String code) {
        var b = adminBooking(adminId, code);
        return view(b, workflows.findById(b.getId()).orElseGet(() -> fresh(b)));
    }

    @Override
    @Transactional(readOnly=true)
    public List<ReviewCase> reviewCases(Long userId, String code) {
        reviewHistory(userId, code);
        var b = bookings.findByBookingCode(code.trim().toUpperCase(java.util.Locale.ROOT)).orElseThrow();
        return disputes.getDisputesByBookingId(b.getId()).stream().map(d -> new ReviewCase(d.getId(), d.getCategory().name(),
                d.getStatus().name(), d.getDescription(), d.getEvidenceUrlList(), d.getResolution())).toList();
    }

    private void requireAdmin(Long id) {
        if (users.findById(id).filter(u -> u.getRole() == Role.ADMIN && u.getStatus() == UserStatus.ACTIVE).isEmpty())
            throw new AccessDeniedException("Chỉ admin đang hoạt động được phân xử");
    }

    @Override
    public View disputeSettlement(Long userId, String code, YearlyReviewRequests.Complaint r) {
        var b = booking(userId, code, true, false); var s = state(b);
        if (audit.replay("YEARLY", b.getId(), r.requestId(), userId, r.toString())) return view(b, s);
        version(s.getVersion(), r.version());
        require(Set.of("RETURN_INSPECTED", "SETTLEMENT_PROPOSED").contains(s.getStage()), "Chỉ phản đối kiểm định hoặc quyết toán đang đề xuất");
        var image = photo(userId, b, r.evidenceId(), false);
        disputes.open(b, b.getRenter().getUser(), DisputeCategory.YEARLY_SETTLEMENT, null,
                r.note(), List.of(image));
        audit.record("YEARLY", b.getId(), r.requestId(), userId, "DISPUTE", r.toString(), s.getStage());
        return changed(b, s);
    }

    @Override
    public View replyReview(Long userId, String code, YearlyReviewRequests.Reply r) {
        var b = booking(userId, code, true, true); var s = state(b);
        if (audit.replay("YEARLY", b.getId(), r.requestId(), userId, r.toString())) return view(b, s);
        var d = disputes.getDisputeById(r.disputeId()).orElseThrow(() -> conflict("Không có khiếu nại"));
        require(d.getBooking().getId().equals(b.getId()) && Set.of(DisputeStatus.OPEN,
                DisputeStatus.IN_REVIEW).contains(d.getStatus()), "Khiếu nại không còn mở trên hợp đồng");
        photo(userId, b, r.evidenceId(), true);
        audit.record("YEARLY", b.getId(), r.requestId(), userId, "PROVIDER_REPLY", r.toString(), s.getStage());
        return changed(b, s);
    }

    @Override
    public View adjudicateSettlement(Long adminId, String code, YearlyReviewRequests.SettlementRuling r) {
        var b = adminBooking(adminId, code); var s = state(b);
        if (audit.replay("YEARLY", b.getId(), r.requestId(), adminId, r.toString())) return view(b, s);
        version(s.getVersion(), r.version());
        var d = disputes.getDisputeById(r.disputeId()).orElseThrow(() -> conflict("Không có khiếu nại"));
        require(d.getBooking().getId().equals(b.getId()) && d.getCategory() == DisputeCategory.YEARLY_SETTLEMENT,
                "Hồ sơ không phải quyết toán năm của hợp đồng");
        require(Set.of("RETURN_INSPECTED", "SETTLEMENT_PROPOSED").contains(s.getStage()), "Quyết toán đã được chốt");
        require(!r.returnedOn().isBefore(started(b)) && !r.returnedOn().isAfter(today()) && r.finalKm() >= initialKm(b,s), "Ngày trả hoặc km không hợp lệ");
        String before = s.getStage() + ":" + s.getFinalKm() + ":" + s.getReturnedOn() + ":" + s.getOtherCharges() + ":" + s.getDepositDeduction();
        photo(b.getVehicle().getProvider().getUser().getId(), b, r.evidenceId(), true);
        s.setReturnedOn(r.returnedOn()); s.setFinalKm(r.finalKm()); s.setReturnEvidenceId(r.evidenceId());
        s.setTotalKm(Math.addExact(s.getCompletedKm(), r.finalKm() - initialKm(b,s)));
        var end = r.returnedOn().atStartOfDay(ZONE).toInstant();
        b.setActualEndAt(end.isBefore(b.getActualStartAt()) ? b.getActualStartAt() : end);
        b.setProviderConfirmedAt(clock.instant()); b.setEndConfirmationMethod("ADMIN");
        b.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
        schedules.release(b.getId()); schedules.markYearlyInspection(b,b.getVehicle(),r.returnedOn());
        var settlement = new Settlement(s.getVersion(), r.otherCharges(), r.depositDeduction(), r.note(), r.evidenceId(), r.charges(), null, null);
        validateCharges(adminId, b, settlement);
        calculateSettlement(b, s, settlement);
        bookings.saveAndFlush(b);
        workflows.saveAndFlush(s);
        disputes.resolveYearlyReview(r.disputeId(),adminId,r.note());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại khác cần giải quyết trước");
        payments.finalizeYearlyObligations(b.getId(),s.getBalanceDue()); s.setStage("SETTLEMENT_ACCEPTED");
        s.setSettlementAcceptedAt(clock.instant()); s.setSettlementAcceptedBy(adminId);
        audit.record("YEARLY",b.getId(),r.requestId(),adminId,"SETTLEMENT_RULING",r.toString(),before);
        return changed(b,s);
    }

    @Override
    public void adjudicateCancellation(Long adminId, String code, YearlyReviewRequests.CancellationRuling r) {
        var b = adminBooking(adminId,code);
        if (audit.replay("YEARLY",b.getId(),r.requestId(),adminId,r.toString())) return;
        require(b.getCancelledAt()!=null && b.getActualStartAt()==null && b.getStatus()==BookingStatus.CANCELLATION_REVIEW,"Không phải hủy năm trước nhận xe");
        var d=disputes.getDisputeById(r.disputeId()).orElseThrow(()->conflict("Không có hồ sơ"));
        require(d.getBooking().getId().equals(b.getId()) && d.getCategory()==DisputeCategory.YEARLY_CANCELLATION,"Sai hồ sơ hủy năm");
        require(r.refundAmount().signum()>=0 && r.refundAmount().compareTo(refunds.remainingReceived(b.getId()))<=0,"Số hoàn vượt nguồn thực nhận");
        require(ledger.listTransactions(b.getId()).stream().noneMatch(tx -> tx.getStatus()==PaymentStatus.PENDING
                || RefundMath.unapplied(tx.getProcessingNote())),"Cần đối soát giao dịch chưa rõ kết quả trước");
        refunds.reserve(b.getId(),"YEARLY_CANCEL_RULING:"+r.disputeId(),r.refundAmount(),r.note(),users.findById(adminId).orElseThrow());
        disputes.resolveYearlyReview(r.disputeId(),adminId,r.note());
        audit.record("YEARLY",b.getId(),r.requestId(),adminId,"CANCELLATION_RULING",r.toString(),b.getStatus().name());
        closure.reconcile(b.getId());
    }

    @Override
    @Transactional(readOnly=true)
    public List<FinancialAuditService.Entry> reviewHistory(Long userId,String code) {
        var u=users.findById(userId).orElseThrow(()->new AccessDeniedException("Không có tài khoản"));
        Booking b;
        if(u.getRole()==Role.ADMIN) { requireAdmin(userId); b=bookings.findByBookingCode(code.trim().toUpperCase(java.util.Locale.ROOT)).orElseThrow(); }
        else b=booking(userId,code,false,null);
        return audit.history("YEARLY",b.getId());
    }

    private BigDecimal verifiedRefund(YearlyWorkflow s) {
        return s.getRefundVerifiedReceived() == null ? BigDecimal.ZERO : s.getRefundVerifiedReceived();
    }

    private YearlyRefundTransfer pendingRefund(YearlyWorkflow s) {
        var pending = s.getRefundTransfers().stream().filter(t -> "PENDING".equals(t.getStatus())).toList();
        if (pending.isEmpty() && s.getRefundTransfers().isEmpty() && s.getRefundEvidenceId() != null && s.getRefundReference() != null) {
            var legacy = new YearlyRefundTransfer(); legacy.setKey(java.util.UUID.randomUUID().toString());
            legacy.setAmount(s.getRentRefund().add(s.getDepositRefund()).subtract(verifiedRefund(s)));
            legacy.setBankReference(s.getRefundReference()); legacy.setEvidenceId(s.getRefundEvidenceId());
            legacy.setStatus("PENDING"); legacy.setSubmittedAt(clock.instant()); s.getRefundTransfers().add(legacy);
            return legacy;
        }
        require(pending.size() == 1, "Không xác định được lần chuyển hoàn đang chờ; cần kiểm tra lịch sử");
        return pending.get(0);
    }

    private void verifyRefundPart(Booking b, YearlyWorkflow s, YearlyRefundTransfer t, BigDecimal received, Long actor, boolean admin) {
        require("PENDING".equals(t.getStatus()) && received.signum() >= 0 && received.compareTo(t.getAmount()) <= 0,
                "Số xác minh vượt lần chuyển hiện tại hoặc lần chuyển đã được xác minh");
        BigDecimal previous = verifiedRefund(s);
        require(previous.add(received).compareTo(s.getRentRefund().add(s.getDepositRefund())) <= 0, "Vượt tổng tiền cần hoàn");
        if (received.signum() > 0) {
            BigDecimal rent = s.getRentRefund().subtract(previous).max(BigDecimal.ZERO).min(received);
            payments.recordYearlyRefundPart(b.getId(), rent, received.subtract(rent), t.getBankReference(),
                    photo(b.getVehicle().getProvider().getUser().getId(), b, t.getEvidenceId(), true), actor, admin, t.getKey());
        }
        t.setVerifiedAmount(received); t.setVerifiedAt(clock.instant()); t.setVerifiedBy(actor);
        t.setStatus(received.signum() == 0 ? "FAILED" : received.compareTo(t.getAmount()) == 0 ? "CONFIRMED" : "PARTIAL_FINAL");
        s.setRefundVerifiedReceived(previous.add(received));
        event(actor, b, "REFUND_TRANSFER_VERIFIED", t.getKey() + ":" + received + ":" + t.getStatus(), "PENDING");
    }

    private Booking booking(Long userId, String code, boolean lock, Boolean provider) {
        String normalizedCode = code.trim().toUpperCase(java.util.Locale.ROOT);
        Booking b = (lock ? bookings.findByBookingCodeForUpdate(normalizedCode) : bookings.findByBookingCode(normalizedCode))
                .orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê"));
        boolean owner = b.getVehicle().getProvider().getUser().getUserId().equals(userId);
        boolean renter = b.getRenter().getUser().getUserId().equals(userId);
        if (provider == null ? !owner && !renter : provider ? !owner : !renter) throw new AccessDeniedException("Không có quyền trên đơn thuê");
        require(b.getPlan().getPlanType() == PlanType.YEARLY, "Chỉ áp dụng cho gói năm");
        return b;
    }

    private YearlyWorkflow state(Booking b) { return workflows.findById(b.getId()).orElseGet(() -> workflows.saveAndFlush(fresh(b))); }
    private YearlyWorkflow fresh(Booking b) {
        YearlyWorkflow s = new YearlyWorkflow();
        s.setBookingId(b.getId());
        s.setStage("TERMS_DRAFT");
        s.setRoundingUnit(unit);
        return s;
    }
    private YearlyWorkflow active(Booking b) {
        require(b.getStatus() == BookingStatus.IN_PROGRESS && b.getActualStartAt() != null && b.getActualEndAt() == null, "Xe không ở trạng thái đang thuê");
        require(payments.yearlyInitialPaymentsComplete(b.getId()), "Hợp đồng cũ chưa được chuyển sang flow năm mới");
        return state(b);
    }
    private YearlyIncident issue(Booking b, Long id) { return incidents.findByIdAndBookingId(id, b.getId()).orElseThrow(() -> conflict("Report không thuộc đơn thuê")); }
    private YearlyReplacement exchange(Booking b, Long id) { return replacements.findByIdAndBookingId(id, b.getId()).orElseThrow(() -> conflict("Yêu cầu đổi xe không thuộc đơn thuê")); }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    private LocalDate started(Booking b) { return b.getActualStartAt().atZone(ZONE).toLocalDate(); }
    private long initialKm(Booking b, YearlyWorkflow s) {
        if (s.getCurrentInitialKm() != null) return s.getCurrentInitialKm();
        return handovers.findById(b.getId()).filter(h -> "RECEIVED".equals(h.getStage()))
                .orElseThrow(() -> conflict("Thiếu biên bản nhận xe")).getInitialKm();
    }
    private String photo(Long userId, Booking b, Long id, boolean provider) {
        return evidence.listEvidence(userId, provider ? Role.PROVIDER : Role.RENTER, b.getBookingCode()).stream()
                .filter(e -> e.id().equals(id)).findFirst().orElseThrow(() -> conflict("Bằng chứng không thuộc đơn thuê")).imageUrl();
    }
    private void version(Long actual, Long expected) { require(expected != null && expected.equals(actual == null ? 0L : actual), "Dữ liệu đã thay đổi, vui lòng tải lại"); }
    private void require(boolean condition, String message) { if (!condition) throw conflict(message); }
    private ConflictException conflict(String message) { return new ConflictException(message); }
    private void event(Long actor, Booking b, String action, String payload, String previous) {
        audit.record("YEARLY", b.getId(), java.util.UUID.randomUUID().toString(), actor, action, payload, previous);
    }
    private View changed(Booking b, YearlyWorkflow s) {
        workflows.saveAndFlush(s);
        notifications.changed(b, "BOOKING_CHANGED");
        return view(b, s);
    }
    private BigDecimal vehicleChangeDiscount(Booking b, LocalDate start, LocalDate due, LocalDate usedUntil) {
        var done = replacements.findByBookingIdOrderByIdAsc(b.getId()).stream()
                .filter(x -> "COMPLETED".equals(x.getStatus()) && x.getExchangedOn() != null && x.getNewPlanPrice() != null)
                .sorted(java.util.Comparator.comparing(YearlyReplacement::getExchangedOn).thenComparing(YearlyReplacement::getId)).toList();
        var segments = new java.util.ArrayList<com.fptufa26.ecomotion.booking.policy.VehicleChangePolicy.Segment>();
        for (int i = 0; i < done.size(); i++) {
            LocalDate to = i + 1 < done.size() ? done.get(i + 1).getExchangedOn() : usedUntil;
            segments.add(new com.fptufa26.ecomotion.booking.policy.VehicleChangePolicy.Segment(done.get(i).getNewPlanPrice(), done.get(i).getExchangedOn(), to));
        }
        return com.fptufa26.ecomotion.booking.policy.VehicleChangePolicy.yearlyDiscount(b.getTotalAmount(), start, due, segments);
    }
    private Incident incident(YearlyIncident i) { return new Incident(i.getId(), i.getVersion(), i.getCategory(), i.getOccurredOn(), i.getDescription(), i.getEvidenceId(), i.getInvoiceAmount(), i.getStatus(), i.getOutcome(), i.getResolutionNote(), i.getApprovedAmount(), i.getTransferEvidenceId(), i.getBankReference(),
            i.getReportedCategory() == null ? i.getCategory() : i.getReportedCategory(), i.getProposedCategory()); }
    private VehicleInfo vehicleInfo(com.fptufa26.ecomotion.vehicle.entity.Vehicle v) {
        return new VehicleInfo(v.getId(), v.getBrand() + " " + v.getModel(), v.getLicensePlate(),
                v.getManufactureYear(), v.getSeatCount(), v.getTransmission() == null ? null : v.getTransmission().name(),
                v.getFuelType() == null ? null : v.getFuelType().name(), vehicles.getCoverImageUrl(v.getId()));
    }
    private VehicleInfo vehicleInfo(Long id) {
        return vehicles.findVehicleById(id).map(this::vehicleInfo).orElse(null);
    }
    private String vehicleName(Long id) { return vehicles.findVehicleById(id).map(v -> v.getBrand() + " " + v.getModel() + " · " + v.getLicensePlate()).orElse("Xe #" + id); }
    private Replacement replacement(YearlyReplacement x) { return new Replacement(x.getId(), x.getVersion(), x.getIncidentId(), x.getOldVehicleId(), x.getNewVehicleId(), x.getPlannedOn(), x.getExchangedOn(), x.getOldFinalKm(), x.getNewInitialKm(), x.getOldEvidenceId(), x.getNewEvidenceId(), x.getStatus(),
            vehicleName(x.getOldVehicleId()), vehicleName(x.getNewVehicleId()), x.getOldPlanPrice(), x.getNewPlanPrice(), Boolean.TRUE.equals(x.getLowerQuality()), x.getQualityNotes(), Boolean.TRUE.equals(x.getQualityAcknowledged()), vehicleInfo(x.getOldVehicleId()), vehicleInfo(x.getNewVehicleId())); }
    private View view(Booking b, YearlyWorkflow s) {
        String stage = "TERMS_ACCEPTED".equals(s.getStage()) && b.getActualStartAt() != null ? "ACTIVE" : s.getStage();
        var contract = new Contract(s.getContractTotal() == null ? b.getTotalAmount() : s.getContractTotal(), s.getSecurityDeposit(),
                b.getPickupTime().atZone(ZONE).toLocalDate(), b.getActualStartAt() == null ? null : started(b),
                b.getPlannedEndAt() == null ? null : b.getPlannedEndAt().atZone(ZONE).toLocalDate(), b.getVehicle().getId());
        Long currentInitialKm = s.getCurrentInitialKm() != null ? s.getCurrentInitialKm()
                : handovers.findById(b.getId()).filter(h -> "RECEIVED".equals(h.getStage())).map(YearlyHandover::getInitialKm).orElse(null);
        return new View(s.getVersion() == null ? 0L : s.getVersion(), stage, contract, s.isTermsAccepted(), s.getAnnualKm(), s.getExcessKmRate(), s.getGraceDays(), s.getLateDailyRate(), s.getRoundingUnit(), s.getReturnMode(), s.getReturnedOn(), s.getFailureOn(), s.getFinalKm(), s.getReturnEvidenceId(), s.getTotalKm(), s.getCompletedKm(), currentInitialKm,
                new Money(s.getRentDue(), s.getEarlyFee(), s.getLateFee(), s.getExcessKmFee(), s.getOtherCharges(), s.getRentPaid(), s.getDepositHeld(), s.getRentRefund(), s.getDepositRefund(), s.getBalanceDue(), s.getDepositDeduction(), s.getVehicleChangeDiscount(), s.getSettlementNote()),
                s.getSettlementEvidenceId(), s.getRefundEvidenceId(), s.getRefundReference(), incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().map(this::incident).toList(), replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().map(this::replacement).toList(),
                s.getRefundVerifiedReceived() == null ? BigDecimal.ZERO : s.getRefundVerifiedReceived(),
                s.getRentRefund().add(s.getDepositRefund()).subtract(s.getRefundVerifiedReceived() == null ? BigDecimal.ZERO : s.getRefundVerifiedReceived()).max(BigDecimal.ZERO), b.getStatus().name(), vehicleInfo(b.getVehicle()), s.getRequestedReturnOn(), s.getSettlementAcceptedAt(), s.getCompletedAt(), storedCharges(s),
                s.getRefundTransfers().stream().map(t -> new RefundTransfer(s.getVersion(), t.getKey(), t.getBankReference(), t.getAmount(), t.getVerifiedAmount(), t.getStatus(), t.getEvidenceId(), t.getSubmittedAt(), t.getVerifiedAt(), t.getVerifiedBy())).toList());
    }
}
