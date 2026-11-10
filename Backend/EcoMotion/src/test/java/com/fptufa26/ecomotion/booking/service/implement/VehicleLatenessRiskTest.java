package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.schedule.service.ScheduleService;
import com.fptufa26.ecomotion.user.service.UserService;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "Nguy cơ xe về trễ" chỉ báo khi chuyến trước thực sự lấn vào giờ nhận, kèm giờ sẵn sàng và số phút trễ. */
class VehicleLatenessRiskTest {

    private static final Instant PICKUP = Instant.parse("2026-11-13T01:00:00Z"); // 08:00 giờ VN

    private final BookingRepository repo = mock(BookingRepository.class);

    private VehicleOperationalRiskServiceImpl service(Instant now) {
        return new VehicleOperationalRiskServiceImpl(repo, mock(UserService.class), Clock.fixed(now, ZoneOffset.UTC), mock(ScheduleService.class));
    }

    private Booking booking(long id, String code, Vehicle vehicle, Instant pickup, Instant plannedEnd, BookingStatus status) {
        Booking b = new Booking();
        b.setId(id); b.setBookingCode(code); b.setVehicle(vehicle);
        b.setPickupTime(pickup); b.setPlannedEndAt(plannedEnd); b.setStatus(status);
        return b;
    }

    private Vehicle vehicle() {
        Vehicle v = new Vehicle();
        v.setId(11L);
        v.setMaintenanceBufferMinutes(60);
        return v;
    }

    @Test
    void previousTripReturningWellBeforePickupIsNotARisk() {
        Vehicle v = vehicle();
        Booking current = booking(43L, "EM-2026-000043", v, PICKUP, PICKUP.plusSeconds(26 * 3600), BookingStatus.CONFIRMED);
        Booking previous = booking(9L, "DF-2026-9003", v, PICKUP.minusSeconds(3 * 86400),
                PICKUP.minusSeconds(2 * 86400), BookingStatus.IN_PROGRESS);
        when(repo.findByVehicle_IdAndStatusIn(eq(11L), any())).thenReturn(List.of(previous));

        assertThat(service(PICKUP.minusSeconds(4 * 86400)).assess(current).atRisk()).isFalse();
    }

    @Test
    void previousTripEndingInsideBufferReportsDelayAndDetails() {
        Vehicle v = vehicle();
        Booking current = booking(43L, "EM-2026-000043", v, PICKUP, PICKUP.plusSeconds(26 * 3600), BookingStatus.CONFIRMED);
        // Trả 07:30 + 60 phút chuẩn bị = sẵn sàng 08:30 → trễ 30 phút so với giờ nhận 08:00.
        Booking previous = booking(9L, "DF-2026-9003", v, PICKUP.minusSeconds(86400),
                PICKUP.minusSeconds(1800), BookingStatus.IN_PROGRESS);
        when(repo.findByVehicle_IdAndStatusIn(eq(11L), any())).thenReturn(List.of(previous));

        var risk = service(PICKUP.minusSeconds(6 * 3600)).assess(current);
        assertThat(risk.atRisk()).isTrue();
        assertThat(risk.blockingBookingCode()).isEqualTo("DF-2026-9003");
        assertThat(risk.estimatedDelayMinutes()).isEqualTo(30L);
        assertThat(risk.estimatedReadyAt()).isEqualTo(PICKUP.plusSeconds(1800));
    }

    @Test
    void overduePreviousTripUsesNowAsEstimatedReturn() {
        Vehicle v = vehicle();
        Booking current = booking(43L, "EM-2026-000043", v, PICKUP, PICKUP.plusSeconds(26 * 3600), BookingStatus.CONFIRMED);
        Booking previous = booking(9L, "DF-2026-9003", v, PICKUP.minusSeconds(86400),
                PICKUP.minusSeconds(4 * 3600), BookingStatus.IN_PROGRESS);
        when(repo.findByVehicle_IdAndStatusIn(eq(11L), any())).thenReturn(List.of(previous));

        // Đã 07:30, chuyến trước quá hạn từ 04:00 → sẵn sàng sớm nhất 08:30.
        var risk = service(PICKUP.minusSeconds(1800)).assess(current);
        assertThat(risk.atRisk()).isTrue();
        assertThat(risk.estimatedDelayMinutes()).isEqualTo(30L);
        assertThat(risk.userMessage()).contains("quá giờ trả");
    }
}
