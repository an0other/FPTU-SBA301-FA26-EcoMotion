package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.dto.request.*;
import com.fptufa26.ecomotion.booking.dto.response.*;
import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.*;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.DriverAssignmentRepository;
import com.fptufa26.ecomotion.booking.service.*;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.common.exception.NotFoundException;
import com.fptufa26.ecomotion.dispute.service.DisputeService;
import com.fptufa26.ecomotion.finance.service.RefundService;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.user.enums.UserStatus;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingWorkflowServiceImpl implements BookingWorkflowService {
    private static final Set<BookingStatus> MUTABLE = Set.of(BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED,
            BookingStatus.DRIVER_ASSIGNED, BookingStatus.DRIVER_CONFIRMED);
    private final BookingRepository bookings;
    private final DriverAssignmentRepository assignments;
    private final BookingPaymentLedgerService ledger;
    private final ProviderBookingService providerBookings;
    private final BookingNotificationService notifications;
    private final RefundService refunds;
    private final DisputeService disputes;
    private final UserService users;
    private final VehicleService vehicles;
    private final EntityManager entityManager;
    private final Clock clock;

    @Override
    public BookingWorkflowResponse getWorkflow(Long currentUserId, String bookingCode) {
        Booking booking = booking(bookingCode, false);
        User user = participant(currentUserId, booking);
        boolean provider = user.getRole() == Role.ADMIN || booking.getVehicle().getProvider().getUser().getId().equals(currentUserId);
        var payments = ledger.listTransactions(booking.getId());
        BigDecimal paid = sum(payments, PaymentDirection.IN, PaymentStatus.PAID, null);
        BigDecimal returned = sum(payments, PaymentDirection.OUT, PaymentStatus.PAID, PaymentType.REFUND);
        BigDecimal pendingRefund = sum(payments, PaymentDirection.OUT, PaymentStatus.PENDING, PaymentType.REFUND);
        boolean reconcile = payments.stream().anyMatch(t -> t.getProcessingNote() != null && t.getProcessingNote().startsWith("RECONCILIATION_REQUIRED"));
        boolean verifying = payments.stream().anyMatch(t -> t.getMethod() == PaymentMethod.VNPAY && t.getStatus() == PaymentStatus.PENDING);
        BigDecimal extras = ledger.listCharges(booking.getId()).stream()
                .filter(c -> !c.isWaived() && c.getChargeType() != ChargeType.BASE_RENTAL && c.getChargeType() != ChargeType.DEPOSIT)
                .map(BookingCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean closed = Set.of(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.CANCELLATION_REVIEW).contains(booking.getStatus());
        BigDecimal remaining = closed ? BigDecimal.ZERO : booking.getTotalAmount().add(extras).subtract(paid.subtract(returned)).max(BigDecimal.ZERO);
        String payment = reconcile ? "RECONCILIATION_REQUIRED" : pendingRefund.signum() > 0 ? "REFUND_PENDING"
                : verifying ? "VERIFYING" : returned.signum() > 0 && paid.compareTo(returned) <= 0 ? "REFUNDED"
                : paid.signum() == 0 ? "UNPAID" : remaining.signum() > 0 ? "PARTIALLY_PAID" : "COVERED";
        boolean needsDriver = !closed && (booking.isDriverReplacementRequired()
                || booking.getStatus() == BookingStatus.CONFIRMED && booking.getDriver() == null
                || booking.getStatus() == BookingStatus.DRIVER_ASSIGNED && booking.getAssignmentExpiresAt() != null
                        && !booking.getAssignmentExpiresAt().isAfter(clock.instant()));
        String driver = closed ? "NOT_REQUIRED" : booking.isDriverReplacementRequired() ? "REPLACEMENT_REQUIRED"
                : booking.getDriver() == null ? "NOT_ASSIGNED"
                : booking.getDriverConfirmedAt() != null ? "ACCEPTED"
                : booking.getAssignmentExpiresAt() != null && !booking.getAssignmentExpiresAt().isAfter(clock.instant()) ? "RESPONSE_EXPIRED" : "PENDING_RESPONSE";
        String trip = closed ? "CANCELLED" : booking.getStatus() == BookingStatus.AWAITING_START_CONFIRMATION ? "AWAITING_START_CONFIRMATION"
                : booking.getStatus() == BookingStatus.AWAITING_END_CONFIRMATION ? "AWAITING_END_CONFIRMATION"
                : booking.getActualEndAt() != null ? "ENDED" : booking.getActualStartAt() != null ? "IN_PROGRESS" : "NOT_STARTED";
        String settlement = disputes.hasActiveDispute(booking.getId()) ? "DISPUTED"
                : booking.getStatus() == BookingStatus.COMPLETED ? "SETTLED"
                : booking.getStatus() == BookingStatus.AWAITING_FINAL_PAYMENT ? "AWAITING_FINAL_PAYMENT"
                : booking.getStatus() == BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION ? "REVIEWING_CHARGES" : "NOT_READY";
        var proposal = proposal(booking);
        List<BookingWorkflowResponse.DriverFeedback> feedback = provider ? assignments.findByBooking_IdOrderByCreatedAtDesc(booking.getId()).stream()
                .filter(a -> a.getStatus() == AssignmentStatus.REJECTED || a.getStatus() == AssignmentStatus.TIMEOUT)
                .limit(10).map(a -> new BookingWorkflowResponse.DriverFeedback(a.getDriver().getUser().getFullName(),
                        a.getStatus().name(), a.getRespondedAt(), a.getRejectionNote())).toList() : List.of();
        return new BookingWorkflowResponse(booking.getBookingCode(), booking.getStatus(), payment, driver, trip, settlement,
                paid, returned, pendingRefund, refunds.refundableAmount(currentUserId, booking.getBookingCode()), remaining,
                booking.getPickupTime(), booking.getAssignmentExpiresAt(), needsDriver,
                provider && MUTABLE.contains(booking.getStatus()), nextAction(booking, provider, payment, settlement, needsDriver, proposal),
                proposal, feedback, refunds.listRefundsByBooking(currentUserId, booking.getBookingCode()), booking.getUpdatedAt());
    }

    @Override
    @Transactional
    public OperationalResolutionResponse proposeAlternative(Long userId, String code, ResolveOperationalRiskRequest request) {
        Booking booking = booking(code, true);
        User user = participant(userId, booking);
        if (user.getRole() != Role.PROVIDER || !booking.getVehicle().getProvider().getUser().getId().equals(userId))
            throw new AccessDeniedException("Chỉ nhà xe quản lý đơn được gửi phương án");
        if (!MUTABLE.contains(booking.getStatus())) throw new ConflictException("Chỉ đề xuất thay đổi trước khi chuyến bắt đầu");
        if (request.reason() == null || request.reason().isBlank() || request.reason().trim().length() > 500)
            throw new IllegalArgumentException("Cần lý do từ 1 đến 500 ký tự");
        if (request.resolution() == OperationalResolutionType.CANCEL_AND_REFUND) {
            var pending = booking.getOperationalProposal();
            if (pending != null && pending.getRespondedAt() == null) respond(pending, userId, false, "Nhà xe hủy đơn và bắt đầu hoàn tiền");
            return providerBookings.resolveOperationalRisk(userId, code, request);
        }
        if (request.resolution() == OperationalResolutionType.CHANGE_VEHICLE) {
            if (request.replacementVehicleId() == null) throw new IllegalArgumentException("Chọn xe đề xuất");
            var vehicle = vehicles.requireApprovedProviderVehicleForUpdate(request.replacementVehicleId(), booking.getVehicle().getProvider().getId());
            if (vehicle.getId().equals(booking.getVehicle().getId()) || vehicle.getSeatCount() < booking.getPassengerCount() + 1)
                throw new ConflictException("Xe đề xuất phải khác xe hiện tại và đủ chỗ");
        } else if (request.resolution() == OperationalResolutionType.RESCHEDULE) {
            if (request.newPickupTime() == null || !request.newPickupTime().isAfter(clock.instant().plus(Duration.ofHours(1))))
                throw new IllegalArgumentException("Giờ nhận mới phải sau hiện tại ít nhất 1 giờ");
        } else throw new IllegalArgumentException("Phương án không hợp lệ");
        OperationalProposal current = booking.getOperationalProposal();
        if (current != null && current.getRespondedAt() == null) {
            if (current.getResolution() == request.resolution() && Objects.equals(current.getVehicleId(), request.replacementVehicleId())
                    && Objects.equals(current.getPickupTime(), request.newPickupTime()) && current.getReason().equals(request.reason().trim()))
                return proposedResponse(booking, request.resolution());
            throw new ConflictException("Đang chờ khách phản hồi đề xuất hiện tại; không thể ghi đè");
        }
        var proposal = new OperationalProposal(); proposal.setId(UUID.randomUUID().toString());
        proposal.setResolution(request.resolution()); proposal.setVehicleId(request.replacementVehicleId());
        proposal.setPickupTime(request.newPickupTime()); proposal.setReason(request.reason().trim());
        proposal.setCreatedBy(userId); proposal.setCreatedAt(clock.instant()); booking.setOperationalProposal(proposal);
        bookings.saveAndFlush(booking); notifications.changed(booking, "OPERATIONAL_PROPOSAL_CHANGED");
        return proposedResponse(booking, request.resolution());
    }

    @Override
    @Transactional
    public BookingWorkflowResponse respondToProposal(Long userId, String code, String id, RespondOperationalProposalRequest request) {
        Booking booking = booking(code, true);
        User user = participant(userId, booking);
        if (user.getRole() != Role.RENTER || !booking.getRenter().getUser().getId().equals(userId))
            throw new AccessDeniedException("Chỉ khách thuê của đơn được phản hồi");
        boolean accept;
        if ("ACCEPT".equals(request.action())) accept = true;
        else if ("REJECT".equals(request.action())) accept = false;
        else throw new IllegalArgumentException("Phản hồi phải là ACCEPT hoặc REJECT");
        OperationalProposal proposal = booking.getOperationalProposal();
        if (proposal == null || !proposal.getId().equals(id)) throw new ConflictException("Đề xuất đã thay đổi; hãy tải lại đơn");
        if (proposal.getRespondedAt() != null) {
            if (Objects.equals(proposal.getRespondedBy(), userId) && Boolean.TRUE.equals(proposal.getAccepted()) == accept)
                return getWorkflow(userId, code);
            throw new ConflictException("Đề xuất đã có phản hồi");
        }
        if (!MUTABLE.contains(booking.getStatus())) throw new ConflictException("Đề xuất không còn áp dụng cho trạng thái hiện tại");
        if (request.reason() != null && request.reason().length() > 500) throw new IllegalArgumentException("Lý do tối đa 500 ký tự");
        respond(proposal, userId, accept, request.reason());
        if (accept) {
            users.findById(proposal.getCreatedBy()).filter(u -> u.getStatus() == UserStatus.ACTIVE && u.getRole() == Role.PROVIDER)
                    .orElseThrow(() -> new ConflictException("Nhà xe không còn hoạt động; cần hỗ trợ xử lý"));
            providerBookings.resolveOperationalRisk(proposal.getCreatedBy(), code, new ResolveOperationalRiskRequest(
                    proposal.getResolution(), proposal.getVehicleId(), proposal.getPickupTime(), proposal.getReason()));
        }
        bookings.saveAndFlush(booking); notifications.changed(booking, "OPERATIONAL_PROPOSAL_CHANGED");
        return getWorkflow(userId, code);
    }

    private void respond(OperationalProposal proposal, Long userId, boolean accepted, String reason) {
        proposal.setAccepted(accepted); proposal.setRespondedAt(clock.instant()); proposal.setRespondedBy(userId);
        proposal.setResponseReason(reason == null ? null : reason.trim());
    }

    @Override
    @Transactional
    public BookingWorkflowResponse withdrawProposal(Long userId, String code, String id) {
        Booking booking = booking(code, true);
        User user = participant(userId, booking);
        if (user.getRole() != Role.PROVIDER || !booking.getVehicle().getProvider().getUser().getId().equals(userId))
            throw new AccessDeniedException("Chỉ nhà xe quản lý đơn được rút đề xuất");
        var proposal = booking.getOperationalProposal();
        if (proposal == null || !proposal.getId().equals(id)) throw new ConflictException("Đề xuất đã thay đổi; hãy tải lại");
        if (Boolean.TRUE.equals(proposal.getAccepted()) || proposal.getRespondedAt() != null && !Objects.equals(proposal.getRespondedBy(), userId))
            throw new ConflictException("Đề xuất đã có phản hồi của khách");
        if (proposal.getRespondedAt() == null) {
            respond(proposal, userId, false, "Nhà xe đã rút phương án để xem xét lại");
            bookings.saveAndFlush(booking); notifications.changed(booking, "OPERATIONAL_PROPOSAL_CHANGED");
        }
        return getWorkflow(userId, code);
    }

    private OperationalResolutionResponse proposedResponse(Booking booking, OperationalResolutionType resolution) {
        return new OperationalResolutionResponse(booking.getBookingCode(), booking.getStatus(), resolution,
                booking.getVehicle().getId(), booking.getPickupTime(), BigDecimal.ZERO,
                "Đã gửi đề xuất cho khách thuê. Xe và giờ đón hiện tại được giữ nguyên đến khi khách đồng ý.");
    }

    private Booking booking(String code, boolean lock) {
        if (code == null || code.isBlank()) throw new NotFoundException("Mã đơn không hợp lệ");
        Booking booking = bookings.findByBookingCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn thuê"));
        if (lock) entityManager.refresh(booking, LockModeType.PESSIMISTIC_WRITE);
        return booking;
    }

    private User participant(Long userId, Booking booking) {
        User user = users.findById(userId).filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản không còn hoạt động"));
        if (user.getRole() != Role.ADMIN && !booking.getRenter().getUser().getId().equals(userId)
                && !booking.getVehicle().getProvider().getUser().getId().equals(userId))
            throw new AccessDeniedException("Bạn không có quyền xem hoặc thay đổi đơn thuê này");
        return user;
    }

    private BigDecimal sum(List<PaymentTransaction> payments, PaymentDirection direction, PaymentStatus status, PaymentType type) {
        return payments.stream().filter(t -> t.getDirection() == direction && t.getStatus() == status && (type == null || t.getType() == type)
                        && t.getVerificationMethod() != com.fptufa26.ecomotion.finance.enums.PaymentVerificationMethod.MOCK_SIMULATED)
                .map(PaymentTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BookingWorkflowResponse.Proposal proposal(Booking booking) {
        OperationalProposal proposal = booking.getOperationalProposal();
        if (proposal == null || proposal.getId() == null) return null;
        String vehicle = proposal.getVehicleId() == null ? null : vehicles.findVehicleById(proposal.getVehicleId())
                .map(v -> v.getBrand() + " " + v.getModel() + " · " + v.getLicensePlate()).orElse("Xe đề xuất không còn khả dụng");
        String status = proposal.getRespondedAt() == null ? MUTABLE.contains(booking.getStatus()) ? "PENDING" : "EXPIRED"
                : Boolean.TRUE.equals(proposal.getAccepted()) ? "ACCEPTED"
                : Objects.equals(proposal.getRespondedBy(), proposal.getCreatedBy()) ? "WITHDRAWN" : "REJECTED";
        return new BookingWorkflowResponse.Proposal(proposal.getId(), proposal.getResolution(), proposal.getVehicleId(), vehicle,
                proposal.getPickupTime(), proposal.getReason(), status, proposal.getCreatedAt(), proposal.getRespondedAt(), proposal.getResponseReason());
    }

    private String nextAction(Booking booking, boolean provider, String payment, String settlement, boolean needsDriver, BookingWorkflowResponse.Proposal proposal) {
        if ("RECONCILIATION_REQUIRED".equals(payment)) return "Tiền đã được ghi nhận nhưng đơn cần đối soát. Không thanh toán lại; liên hệ nhà xe hoặc hỗ trợ để xử lý tiền đã nhận.";
        if ("REFUND_PENDING".equals(payment)) return provider ? "Có khoản hoàn đang xử lý. Mở Tài chính để xác nhận chuyển khoản và biên lai sau khi đã chuyển tiền." : "Khoản hoàn đang được xử lý. Đồng ý hoàn tiền chưa có nghĩa tiền đã vào tài khoản của bạn.";
        if (proposal != null && "PENDING".equals(proposal.status())) return provider ? "Đang chờ khách chấp thuận phương án. Chưa thay đổi xe hoặc giờ đón." : "Nhà xe gửi phương án thay thế. Xem thay đổi và chọn đồng ý hoặc từ chối; chưa có thay đổi nào được áp dụng.";
        if ("DISPUTED".equals(settlement)) return "Đang giải quyết tranh chấp. Chưa hoàn tất quyết toán; theo dõi kết quả xử lý trước khi thanh toán phần đang tranh chấp.";
        if (needsDriver) return provider ? "Chưa có tài xế xác nhận phù hợp. Thử tìm lại, đề xuất phương án cho khách hoặc hủy và bắt đầu hoàn tiền theo chính sách."
                : "Nhà xe đang tìm tài xế. Tiền đã nhận vẫn được ghi nhận; bạn có quyền từ chối phương án thay thế và yêu cầu xử lý hoàn tiền theo chính sách.";
        if ("VERIFYING".equals(payment)) return "Đang xác minh thanh toán với máy chủ. Không thanh toán lại trong lúc chờ kết quả.";
        return switch (booking.getStatus()) {
            case PENDING -> provider ? "Xem yêu cầu để chấp nhận hoặc từ chối." : "Chờ nhà xe duyệt. Bạn chưa cần thanh toán cọc.";
            case ACCEPTED, AWAITING_DEPOSIT -> provider ? "Chờ khách thanh toán trong thời gian giữ lịch." : "Thanh toán cọc trước khi hết thời gian giữ lịch.";
            case DRIVER_ASSIGNED -> "Đã gửi yêu cầu cho tài xế, đang chờ phản hồi. Phân công chưa phải tài xế đã chấp nhận.";
            case DRIVER_CONFIRMED -> "Tài xế đã xác nhận. Theo dõi giờ đón và liên hệ khi cần.";
            case DRIVER_ARRIVED, AWAITING_START_CONFIRMATION -> "Tài xế đã đến. Hai bên cần xác nhận bắt đầu chuyến.";
            case IN_PROGRESS -> "Chuyến đang diễn ra. Xác nhận kết thúc khi hai bên đã hoàn thành hành trình.";
            case AWAITING_END_CONFIRMATION -> "Một bên đã báo kết thúc. Chờ bên còn lại xác nhận; đơn chưa hoàn tất tài chính.";
            case AWAITING_EXTRA_CHARGE_CONFIRMATION -> provider ? "Kiểm tra bằng chứng, bổ sung phụ phí hợp lệ rồi chốt quyết toán." : "Kiểm tra các phụ phí; gửi tranh chấp nếu không đồng ý trước khi quyết toán.";
            case AWAITING_FINAL_PAYMENT -> "Chi phí đã chốt. Thanh toán phần còn lại để hoàn tất đơn.";
            case COMPLETED -> "Đơn đã hoàn tất theo trạng thái máy chủ. Bạn có thể xem lại chi phí và lịch sử thanh toán.";
            case CANCELLATION_REVIEW -> "Đơn không tiếp tục thực hiện; khoản tiền đang được đối soát hoặc hoàn trả.";
            case CANCELLED, REJECTED -> "Đơn đã kết thúc. Kiểm tra lịch sử tiền và yêu cầu hỗ trợ nếu còn nghĩa vụ chưa giải quyết.";
            default -> "Theo dõi cập nhật từ nhà xe.";
        };
    }
}
