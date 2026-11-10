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
        var months = YearlyRentalRules.installments(b.getTotalAmount(), s.getRoundingUnit());
        int count = "FULL_CONTRACT".equals(r.mode()) ? 12 : YearlyRentalRules.startedMonths(started(b), r.returnedOn());
        var due = b.getPlannedEndAt().atZone(ZONE).toLocalDate();
        require(!"STANDARD".equals(r.mode()) || !r.returnedOn().isBefore(due), "Trả trước hạn cần chọn kết thúc sớm");
        BigDecimal rent = months.subList(0, count).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal early = "EARLY".equals(r.mode()) ? YearlyRentalRules.earlyTerminationFee(b.getTotalAmount(), rent, s.getRoundingUnit()) : BigDecimal.ZERO;
        BigDecimal late = YearlyRentalRules.lateFee(due, r.returnedOn(), s.getGraceDays(), s.getLateDailyRate());
        var cash = payments.yearlyCash(b.getId());
        BigDecimal vehicleDiscount = vehicleChangeDiscount(b, started(b), due, "FULL_CONTRACT".equals(r.mode()) || r.returnedOn().isAfter(due) ? due : r.returnedOn());
        var result = YearlySettlementMath.settle(rent.add(early).add(late).subtract(vehicleDiscount).max(BigDecimal.ZERO), cash.rentPaid(), cash.depositHeld(), BigDecimal.ZERO);
        return new Preview(r.returnedOn(), new Money(rent, early, late, BigDecimal.ZERO, BigDecimal.ZERO,
                cash.rentPaid(), cash.depositHeld(), result.rentRefund(), result.depositRefund(), result.balanceDue(),
                BigDecimal.ZERO, vehicleDiscount, "Chưa gồm phí vượt km và chi phí kiểm tra xe"), "ESTIMATE_ONLY");
    }

    @Override
    public View terms(Long userId, String code, Terms r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(b.getStatus() == BookingStatus.ACCEPTED, "Chỉ cấu hình điều khoản sau khi provider chấp nhận yêu cầu");
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
        require(incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(i -> "CLOSED".equals(i.getStatus())), "Còn report đang xử lý");
        require(Set.of("EARLY", "STANDARD", "FULL_CONTRACT").contains(r.mode()), "Loại trả xe không hợp lệ");
        require("TERMS_ACCEPTED".equals(s.getStage()), "Đã có yêu cầu trả xe");
        require(replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().noneMatch(x -> !Set.of("COMPLETED", "CANCELLED").contains(x.getStatus())), "Đang có yêu cầu đổi xe");
        s.setReturnMode(r.mode());
        s.setStage("RETURN_REQUESTED");
        return changed(b, s);
    }

    @Override
    public View inspectReturn(Long userId, String code, Inspection r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = active(b);
        version(s.getVersion(), r.version());
        require(Set.of("RETURN_REQUESTED", "RETURN_INSPECTED").contains(s.getStage()), "Chưa có yêu cầu trả xe");
        LocalDate start = started(b);
        require(!r.returnedOn().isBefore(start) && !r.returnedOn().isAfter(today()), "Ngày nhận lại xe không hợp lệ");
        require(r.finalKm() >= initialKm(b, s), "Số km trả nhỏ hơn số km nhận");
        photo(userId, b, r.evidenceId(), true);
        s.setReturnedOn(r.returnedOn());
        s.setFinalKm(r.finalKm());
        s.setReturnEvidenceId(r.evidenceId());
        s.setTotalKm(Math.addExact(s.getCompletedKm(), r.finalKm() - initialKm(b, s)));
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
    public View proposeSettlement(Long userId, String code, Settlement r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require(Set.of("RETURNED", "SETTLEMENT_PROPOSED").contains(s.getStage()), "Không thể thay đổi quyết toán ở bước này");
        if (r.otherCharges().signum() > 0) {
            require(r.note() != null && !r.note().isBlank() && r.evidenceId() != null, "Phụ phí cần lý do và bằng chứng");
            photo(userId, b, r.evidenceId(), true);
        }
        calculateSettlement(b, s, r);
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
        return changed(b, s);
    }

    @Override
    public View refundTransfer(Long userId, String code, Transfer r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require(Set.of("SETTLEMENT_ACCEPTED", "REFUND_TRANSFER_RECORDED").contains(s.getStage()), "Quyết toán chưa được xác nhận");
        require(payments.yearlyBalancePaid(b.getId(), s.getBalanceDue()), "Cần thanh toán khoản bổ sung trước khi hoàn cọc");
        require(s.getRentRefund().add(s.getDepositRefund()).signum() > 0, "Không có khoản cần hoàn");
        photo(userId, b, r.evidenceId(), true);
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
        payments.recordYearlyRefund(b.getId(), s.getRentRefund(), s.getDepositRefund(), s.getRefundReference(),
                photo(userId, b, s.getRefundEvidenceId(), false), userId);
        s.setStage("REFUND_RECEIVED");
        return changed(b, s);
    }

    @Override
    public View complete(Long userId, String code, Version r) {
        Booking b = booking(userId, code, true, true);
        YearlyWorkflow s = state(b);
        version(s.getVersion(), r.version());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại chưa được giải quyết");
        require(!refunds.hasOpenRefunds(b.getId()), "Còn nghĩa vụ hoàn tiền chưa xử lý");
        require(ledger.listTransactions(b.getId()).stream().noneMatch(tx -> tx.getStatus() == PaymentStatus.PENDING
                || RefundMath.unapplied(tx.getProcessingNote())), "Còn giao dịch đang đối soát");
        require(Set.of("SETTLEMENT_ACCEPTED", "REFUND_RECEIVED").contains(s.getStage()), "Chưa đủ bước quyết toán");
        require(s.getRentRefund().add(s.getDepositRefund()).signum() == 0 || "REFUND_RECEIVED".equals(s.getStage()), "Renter chưa xác nhận nhận tiền hoàn");
        require(payments.yearlyBalancePaid(b.getId(), s.getBalanceDue()), "Còn khoản thanh toán bổ sung");
        require(incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(x -> "CLOSED".equals(x.getStatus())), "Còn report chưa được xử lý/xác nhận");
        b.setStatus(BookingStatus.COMPLETED);
        s.setStage("COMPLETED");
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
        i.setCategory(r.category());
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
        require("OPEN".equals(i.getStatus()) && "TERMS_ACCEPTED".equals(s.getStage()), "Report không còn mở");
        require(Set.of("FIXED", "REIMBURSE", "RENTER_PAID", "END_PROVIDER", "REPLACE").contains(r.outcome()), "Kết quả không hợp lệ");
        boolean owner = "OWNER_MAINTENANCE".equals(i.getCategory());
        require(owner || "RENTER_PAID".equals(r.outcome()) || "FIXED".equals(r.outcome()), "Thiệt hại renter không dùng nhánh provider hoàn tiền/kết thúc");
        require(!"RENTER_PAID".equals(r.outcome()) || !owner, "Không gán hao mòn thành khoản renter tự chịu");
        require(r.approvedAmount().signum() == 0 || "REIMBURSE".equals(r.outcome()), "Chỉ hoàn ứng mới có số tiền được duyệt");
        require(r.approvedAmount().compareTo(i.getInvoiceAmount()) <= 0, "Hoàn ứng vượt số tiền yêu cầu");
        i.setOutcome(r.outcome());
        i.setResolutionNote(r.note());
        i.setApprovedAmount(r.approvedAmount());
        i.setStatus("REIMBURSE".equals(r.outcome()) && r.approvedAmount().signum() > 0 ? "AWAITING_TRANSFER" : "AWAITING_CONFIRMATION");
        if ("END_PROVIDER".equals(r.outcome())) {
            require(replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().allMatch(x -> Set.of("COMPLETED", "CANCELLED").contains(x.getStatus())), "Đang đổi xe");
            s.setReturnMode("PROVIDER_FAILURE");
            s.setFailureOn(i.getOccurredOn());
            s.setStage("RETURN_REQUESTED");
            workflows.save(s);
        }
        incidents.flush();
        notifications.changed(b, "BOOKING_CHANGED");
        return incident(i);
    }

    @Override
    public Incident incidentTransfer(Long userId, String code, Long id, Transfer r) {
        Booking b = booking(userId, code, true, true);
        YearlyIncident i = issue(b, id);
        version(i.getVersion(), r.version());
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
        require(Set.of("AWAITING_CONFIRMATION", "TRANSFER_RECORDED").contains(i.getStatus()), "Chưa có kết quả/bằng chứng để xác nhận");
        require(!"REPLACE".equals(i.getOutcome()), "Report đổi xe đóng khi nhận xe thay thế");
        i.setStatus("CLOSED");
        incidents.flush();
        notifications.changed(b, "BOOKING_CHANGED");
        return incident(i);
    }

    @Override
    public Replacement replacement(Long userId, String code, Replace r) {
        Booking b = booking(userId, code, true, true);
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


    private void calculateSettlement(Booking b, YearlyWorkflow s, Settlement r) {
        var cash = payments.yearlyCash(b.getId());
        var months = YearlyRentalRules.installments(b.getTotalAmount(), s.getRoundingUnit());
        int used = YearlyRentalRules.startedMonths(started(b), s.getReturnedOn());
        int charged = used;
        if ("PROVIDER_FAILURE".equals(s.getReturnMode())) {
            require(s.getFailureOn() != null, "Thiếu ngày sự cố được chấp nhận");
            charged = YearlyRentalRules.startedMonths(started(b), s.getFailureOn()) - 1;
        }
        if ("FULL_CONTRACT".equals(s.getReturnMode())) charged = 12;
        LocalDate due = b.getPlannedEndAt().atZone(ZONE).toLocalDate();
        if ("STANDARD".equals(s.getReturnMode())) require(!s.getReturnedOn().isBefore(due), "Trả trước hạn cần chọn trả sớm hoặc thanh toán đủ hợp đồng");
        BigDecimal rent = months.subList(0, charged).stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal early = "EARLY".equals(s.getReturnMode())
                ? YearlyRentalRules.earlyTerminationFee(b.getTotalAmount(), rent, s.getRoundingUnit()) : BigDecimal.ZERO;
        BigDecimal late = YearlyRentalRules.lateFee(due, s.getReturnedOn(), s.getGraceDays(), s.getLateDailyRate());
        long allowance = BigDecimal.valueOf(s.getAnnualKm()).multiply(BigDecimal.valueOf(used))
                .divide(BigDecimal.valueOf(12), 0, RoundingMode.DOWN).longValueExact();
        BigDecimal km = BigDecimal.valueOf(Math.max(0, s.getTotalKm() - allowance)).multiply(s.getExcessKmRate());
        // B1: giảm trừ do đổi xe rẻ hơn theo số ngày thực tế dùng từng xe; xe đắt hơn không thu thêm.
        LocalDate usedUntil = "FULL_CONTRACT".equals(s.getReturnMode()) || s.getReturnedOn().isAfter(due) ? due : s.getReturnedOn();
        BigDecimal vehicleDiscount = vehicleChangeDiscount(b, started(b), due, usedUntil);
        BigDecimal obligation = rent.add(early).add(late).add(km).add(r.otherCharges()).subtract(vehicleDiscount).max(BigDecimal.ZERO);
        YearlySettlementMath.Result result;
        try {
            result = YearlySettlementMath.settle(obligation, cash.rentPaid(), cash.depositHeld(), r.depositDeduction());
        } catch (IllegalArgumentException ex) {
            throw conflict("Khoản khấu trừ vượt cọc hoặc bị tính trùng với tiền đã thanh toán");
        }
        s.setRentDue(rent);
        s.setEarlyFee(early);
        s.setLateFee(late);
        s.setExcessKmFee(km);
        s.setOtherCharges(r.otherCharges());
        s.setSettlementEvidenceId(r.evidenceId());
        s.setSettlementNote(r.note());
        s.setRentPaid(cash.rentPaid());
        s.setDepositHeld(cash.depositHeld());
        s.setDepositDeduction(r.depositDeduction());
        s.setVehicleChangeDiscount(vehicleDiscount);
        s.setBalanceDue(result.balanceDue());
        s.setRentRefund(result.rentRefund());
        s.setDepositRefund(result.depositRefund());
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
        calculateSettlement(b,s,new Settlement(s.getVersion(),r.otherCharges(),r.depositDeduction(),r.note(),r.evidenceId()));
        bookings.saveAndFlush(b);
        workflows.saveAndFlush(s);
        disputes.resolveYearlyReview(r.disputeId(),adminId,r.note());
        require(!disputes.hasOpenDispute(b.getId()), "Còn khiếu nại khác cần giải quyết trước");
        payments.finalizeYearlyObligations(b.getId(),s.getBalanceDue()); s.setStage("SETTLEMENT_ACCEPTED");
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
    private Incident incident(YearlyIncident i) { return new Incident(i.getId(), i.getVersion(), i.getCategory(), i.getOccurredOn(), i.getDescription(), i.getEvidenceId(), i.getInvoiceAmount(), i.getStatus(), i.getOutcome(), i.getResolutionNote(), i.getApprovedAmount(), i.getTransferEvidenceId(), i.getBankReference()); }
    private String vehicleName(Long id) { return vehicles.findVehicleById(id).map(v -> v.getBrand() + " " + v.getModel() + " · " + v.getLicensePlate()).orElse("Xe #" + id); }
    private Replacement replacement(YearlyReplacement x) { return new Replacement(x.getId(), x.getVersion(), x.getIncidentId(), x.getOldVehicleId(), x.getNewVehicleId(), x.getPlannedOn(), x.getExchangedOn(), x.getOldFinalKm(), x.getNewInitialKm(), x.getOldEvidenceId(), x.getNewEvidenceId(), x.getStatus(),
            vehicleName(x.getOldVehicleId()), vehicleName(x.getNewVehicleId()), x.getOldPlanPrice(), x.getNewPlanPrice(), Boolean.TRUE.equals(x.getLowerQuality()), x.getQualityNotes(), Boolean.TRUE.equals(x.getQualityAcknowledged())); }
    private View view(Booking b, YearlyWorkflow s) {
        String stage = "TERMS_ACCEPTED".equals(s.getStage()) && b.getActualStartAt() != null ? "ACTIVE" : s.getStage();
        var contract = new Contract(s.getContractTotal() == null ? b.getTotalAmount() : s.getContractTotal(), s.getSecurityDeposit(),
                b.getPickupTime().atZone(ZONE).toLocalDate(), b.getActualStartAt() == null ? null : started(b),
                b.getPlannedEndAt() == null ? null : b.getPlannedEndAt().atZone(ZONE).toLocalDate(), b.getVehicle().getId());
        Long currentInitialKm = s.getCurrentInitialKm() != null ? s.getCurrentInitialKm()
                : handovers.findById(b.getId()).filter(h -> "RECEIVED".equals(h.getStage())).map(YearlyHandover::getInitialKm).orElse(null);
        return new View(s.getVersion() == null ? 0L : s.getVersion(), stage, contract, s.isTermsAccepted(), s.getAnnualKm(), s.getExcessKmRate(), s.getGraceDays(), s.getLateDailyRate(), s.getRoundingUnit(), s.getReturnMode(), s.getReturnedOn(), s.getFailureOn(), s.getFinalKm(), s.getReturnEvidenceId(), s.getTotalKm(), s.getCompletedKm(), currentInitialKm,
                new Money(s.getRentDue(), s.getEarlyFee(), s.getLateFee(), s.getExcessKmFee(), s.getOtherCharges(), s.getRentPaid(), s.getDepositHeld(), s.getRentRefund(), s.getDepositRefund(), s.getBalanceDue(), s.getDepositDeduction(), s.getVehicleChangeDiscount(), s.getSettlementNote()),
                s.getSettlementEvidenceId(), s.getRefundEvidenceId(), s.getRefundReference(), incidents.findByBookingIdOrderByIdAsc(b.getId()).stream().map(this::incident).toList(), replacements.findByBookingIdOrderByIdAsc(b.getId()).stream().map(this::replacement).toList());
    }
}
