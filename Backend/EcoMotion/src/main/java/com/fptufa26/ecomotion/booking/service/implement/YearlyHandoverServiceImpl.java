package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.YearlyHandover;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.exception.BookingNotFoundException;
import com.fptufa26.ecomotion.booking.policy.YearlyRentalRules;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.YearlyHandoverRepository;
import com.fptufa26.ecomotion.booking.service.YearlyHandoverService;
import com.fptufa26.ecomotion.booking.service.YearlyWorkflowService;
import com.fptufa26.ecomotion.booking.service.TripEvidenceService;
import com.fptufa26.ecomotion.booking.service.BookingNotificationService;
import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.enums.Role;
import com.fptufa26.ecomotion.vehicle.enums.PlanType;
import com.fptufa26.ecomotion.vehicle.service.VehicleService;
import com.fptufa26.ecomotion.verification.documentai.DrivingLicenseEligibilityPolicy;
import java.time.Clock;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class YearlyHandoverServiceImpl implements YearlyHandoverService {
    private final BookingRepository bookings;
    private final YearlyHandoverRepository handovers;
    private final ScheduleService schedules;
    private final PaymentRequestService payments;
    private final TripEvidenceService evidence;
    private final VehicleService vehicles;
    private final BookingNotificationService notifications;
    private final Clock clock;
    private final YearlyWorkflowService workflow;

    @Override
    @Transactional
    public Handover dispatch(Long userId, String code) {
        Booking booking = providerBooking(userId, code);
        YearlyHandover row = handovers.findById(booking.getId()).orElse(null);
        if (row != null) return view(row);
        if (!workflow.get(userId, code).termsAccepted()) throw new ConflictException("Renter chưa xác nhận điều khoản năm");
        if (booking.getStatus() != BookingStatus.CONFIRMED || !payments.yearlyInitialPaymentsComplete(booking.getId())) {
            throw new ConflictException("Cần xác nhận đủ tiền bảo đảm và tiền thuê tháng đầu");
        }
        payments.yearlyCash(booking.getId());
        row = new YearlyHandover();
        row.setBookingId(booking.getId());
        row.setStage("DISPATCHED");
        row.setDispatchedAt(clock.instant());
        notifications.changed(booking, "BOOKING_CHANGED");
        return view(handovers.saveAndFlush(row));
    }

    @Override
    @Transactional
    public Handover arrive(Long userId, String code, long initialKm, long evidenceId) {
        Booking booking = providerBooking(userId, code);
        YearlyHandover row = requiredHandover(booking);
        if ("ARRIVED".equals(row.getStage()) && row.getInitialKm() == initialKm && row.getEvidenceId() == evidenceId) return view(row);
        if (!java.util.Set.of("DISPATCHED", "ARRIVED").contains(row.getStage()) || initialKm < 0 || booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Không thể ghi nhận xe đã giao tới ở bước hiện tại");
        }
        var photo = evidence.listEvidence(userId, Role.PROVIDER, code).stream()
                .filter(item -> item.id().equals(evidenceId)).findFirst()
                .orElseThrow(() -> new ConflictException("Ảnh không thuộc booking này"));
        row.setStage("ARRIVED");
        if (row.getArrivedAt() == null) row.setArrivedAt(clock.instant());
        row.setInitialKm(initialKm);
        row.setEvidenceId(evidenceId);
        row.setEvidenceUrl(photo.imageUrl());
        notifications.changed(booking, "BOOKING_CHANGED");
        handovers.flush();
        return view(row);
    }

    @Override
    @Transactional
    public Handover receive(Long userId, String code, Long version) {
        Booking booking = requiredBooking(code);
        if (!booking.getRenter().getUser().getUserId().equals(userId)) throw new AccessDeniedException("Không thuộc đơn thuê của bạn");
        YearlyHandover row = requiredHandover(booking);
        if ("RECEIVED".equals(row.getStage())) return view(row);
        if (version == null || !version.equals(row.getVersion() == null ? 0L : row.getVersion())) {
            throw new ConflictException("Biên bản giao xe đã thay đổi. Vui lòng tải lại và kiểm tra ảnh, số km trước khi xác nhận.");
        }
        if (!"ARRIVED".equals(row.getStage()) || booking.getStatus() != BookingStatus.CONFIRMED
                || !payments.yearlyInitialPaymentsComplete(booking.getId())) throw new ConflictException("Xe chưa sẵn sàng xác nhận nhận");
        vehicles.findVehicleByIdForUpdate(booking.getVehicle().getId())
                .orElseThrow(() -> new ConflictException("Không tìm thấy xe"));
        if (!workflow.get(userId, code).termsAccepted()) throw new ConflictException("Chưa đồng ý điều khoản năm");
        payments.yearlyCash(booking.getId());
        var now = clock.instant();
        var end = YearlyRentalRules.contractEnd(now);
        DrivingLicenseEligibilityPolicy.requireEligibleForBooking(booking.getRenter(), booking.getVehicle(), now, end);
        schedules.moveBooking(booking, booking.getVehicle(), now, end);
        payments.rebaseYearlyInstallments(booking.getId(), now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
        booking.setPlannedEndAt(end);
        booking.setActualStartAt(now);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        row.setStage("RECEIVED");
        row.setReceivedOn(now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
        notifications.changed(booking, "BOOKING_CHANGED");
        handovers.flush();
        return view(row);
    }

    @Override
    @Transactional(readOnly = true)
    public Handover get(Long userId, String code) {
        Booking booking = bookings.findByBookingCode(code).orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê"));
        if (!booking.getRenter().getUser().getUserId().equals(userId)
                && !booking.getVehicle().getProvider().getUser().getUserId().equals(userId)) throw new AccessDeniedException("Không có quyền xem bàn giao");
        if (booking.getPlan().getPlanType() != PlanType.YEARLY) throw new ConflictException("Chỉ áp dụng cho gói năm");
        return handovers.findById(booking.getId()).map(this::view)
                .orElse(new Handover("NOT_DISPATCHED", null, null, null, null, null, null));
    }

    private Booking providerBooking(Long userId, String code) {
        Booking booking = requiredBooking(code);
        if (!booking.getVehicle().getProvider().getUser().getUserId().equals(userId)) throw new AccessDeniedException("Xe không thuộc provider của bạn");
        return booking;
    }

    private Booking requiredBooking(String code) {
        Booking booking = bookings.findByBookingCodeForUpdate(code).orElseThrow(() -> new BookingNotFoundException("Không tìm thấy đơn thuê"));
        if (booking.getPlan().getPlanType() != PlanType.YEARLY) throw new ConflictException("Chỉ áp dụng cho gói năm");
        return booking;
    }

    private YearlyHandover requiredHandover(Booking booking) {
        return handovers.findById(booking.getId()).orElseThrow(() -> new ConflictException("Chưa bắt đầu giao xe"));
    }

    private Handover view(YearlyHandover row) {
        return new Handover(row.getStage(), row.getDispatchedAt(), row.getArrivedAt(), row.getReceivedOn(), row.getInitialKm(), row.getEvidenceUrl(), row.getVersion() == null ? 0L : row.getVersion());
    }
}
