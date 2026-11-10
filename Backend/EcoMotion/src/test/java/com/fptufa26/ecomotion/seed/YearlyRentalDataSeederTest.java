package com.fptufa26.ecomotion.seed;

import com.fptufa26.ecomotion.booking.entity.*;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.policy.*;
import com.fptufa26.ecomotion.common.storage.service.*;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.*;
import com.fptufa26.ecomotion.finance.service.PaymentRequestService;
import com.fptufa26.ecomotion.user.entity.*;
import com.fptufa26.ecomotion.user.repository.*;
import com.fptufa26.ecomotion.vehicle.entity.*;
import com.fptufa26.ecomotion.vehicle.repository.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class YearlyRentalDataSeederTest {
    private final Map<String, Booking> bookings = new HashMap<>();
    private final Map<String, Vehicle> vehicles = new HashMap<>();
    private final Map<Long, RentalPlan> plans = new HashMap<>();
    private final Map<Long, List<PaymentRequest>> requests = new HashMap<>();
    private final Map<Long, YearlyWorkflow> workflows = new HashMap<>();
    private final List<YearlyHandover> handovers = new ArrayList<>();
    private final com.fptufa26.ecomotion.schedule.service.ScheduleService schedules = mock(com.fptufa26.ecomotion.schedule.service.ScheduleService.class);
    private YearlyRentalDataSeeder seeder;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        var users = mock(UserRepository.class);
        var renters = mock(RenterRepository.class);
        var vehicleRepo = mock(VehicleRepository.class);
        var images = mock(VehicleImageRepository.class);
        var payments = mock(PaymentRequestService.class);
        var em = mock(EntityManager.class);
        var clock = Clock.fixed(Instant.parse("2026-11-10T03:00:00Z"), ZoneOffset.UTC);
        seeder = new YearlyRentalDataSeeder(users, renters, vehicleRepo, images, mock(FileStorageService.class),
                mock(DocumentStorageService.class), payments, em, clock, new BookingStartPolicy(clock, 3), schedules);
        when(schedules.isRangeFree(anyLong(), any(), any())).thenReturn(true);
        ReflectionTestUtils.setField(seeder, "roundingUnit", new BigDecimal("100000"));
        int index = 108;
        for (String suffix : List.of("new", "monthly", "happy-end")) {
            var user = new User(); user.setId((long) index++); user.setEmail("renter.yearly." + suffix + "@ecomotion.vn");
            var renter = new Renter(); renter.setUser(user); renter.setLicenseNo("UPLOADED-LICENSE");
            when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
            when(renters.findByUser_Id(user.getId())).thenReturn(Optional.of(renter));
        }
        var providerUser = new User(); providerUser.setId(2L);
        var provider = new Provider(); provider.setUser(providerUser);
        var template = new Vehicle(); template.setId(1L); template.setProvider(provider);
        template.setBrand("Demo"); template.setModel("Car"); template.setLicensePlate("51A-11111");
        vehicles.put(template.getLicensePlate(), template);
        when(vehicleRepo.findByLicensePlate(anyString())).thenAnswer(i -> Optional.ofNullable(vehicles.get(i.getArgument(0))));
        var image = new VehicleImage(); image.setImageUrl("demo-image.png");
        when(images.findByVehicle_IdOrderByDisplayOrderAsc(anyLong())).thenReturn(List.of(image));
        var ids = new AtomicLong(100);
        doAnswer(i -> {
            Object value = i.getArgument(0);
            if (value instanceof Vehicle v) { v.setId(ids.incrementAndGet()); vehicles.put(v.getLicensePlate(), v); }
            if (value instanceof RentalPlan p) { p.setId(ids.incrementAndGet()); plans.put(p.getVehicle().getId(), p); }
            if (value instanceof Booking b) { b.setId(ids.incrementAndGet()); bookings.put(b.getBookingCode(), b); }
            if (value instanceof YearlyWorkflow w) workflows.put(w.getBookingId(), w);
            if (value instanceof YearlyHandover h) handovers.add(h);
            return null;
        }).when(em).persist(any());
        TypedQuery<Booking> bookingQuery = mock(TypedQuery.class);
        String[] currentCode = {null};
        when(em.createQuery(anyString(), eq(Booking.class))).thenReturn(bookingQuery);
        when(bookingQuery.setParameter(eq("code"), any())).thenAnswer(i -> { currentCode[0] = i.getArgument(1); return bookingQuery; });
        when(bookingQuery.getResultStream()).thenAnswer(i -> Stream.ofNullable(bookings.get(currentCode[0])));
        TypedQuery<RentalPlan> planQuery = mock(TypedQuery.class);
        Long[] vehicleId = {null};
        when(em.createQuery(anyString(), eq(RentalPlan.class))).thenReturn(planQuery);
        when(planQuery.setParameter(anyString(), any())).thenAnswer(i -> { if ("id".equals(i.getArgument(0))) vehicleId[0] = i.getArgument(1); return planQuery; });
        when(planQuery.getSingleResult()).thenAnswer(i -> plans.get(vehicleId[0]));
        TypedQuery<PaymentRequest> paymentQuery = mock(TypedQuery.class);
        Long[] bookingId = {null};
        when(em.createQuery(anyString(), eq(PaymentRequest.class))).thenReturn(paymentQuery);
        when(paymentQuery.setParameter(eq("id"), any())).thenAnswer(i -> { bookingId[0] = i.getArgument(1); return paymentQuery; });
        when(paymentQuery.getResultList()).thenAnswer(i -> requests.get(bookingId[0]));
        var nativeQuery = mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(payments.createDepositPaymentRequest(any())).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            var rows = new ArrayList<PaymentRequest>();
            var deposit = new PaymentRequest(); deposit.setSecurityDeposit(true); deposit.setAmount(new BigDecimal("50000000"));
            rows.add(deposit);
            var amounts = YearlyRentalRules.installments(b.getTotalAmount(), new BigDecimal("100000"));
            for (int month = 1; month <= 12; month++) {
                var row = new PaymentRequest(); row.setInstallmentNumber(month); row.setAmount(amounts.get(month - 1));
                row.setStatus(PaymentRequestStatus.PENDING); rows.add(row);
            }
            requests.put(b.getId(), rows);
            return deposit;
        });
    }

    @Test
    void threeRentersCoverActiveClosingAndSeparateCancellationWithoutFakeHandover() {
        seeder.seed();
        assertEquals(Set.of("EM-Y2-MONTHLY", "EM-Y2-HAPPY-END", "EM-Y2-CANCEL"), bookings.keySet());
        var monthly = bookings.get("EM-Y2-MONTHLY");
        var closing = bookings.get("EM-Y2-HAPPY-END");
        var cancel = bookings.get("EM-Y2-CANCEL");
        assertEquals(LocalDate.of(2026, 8, 8), monthly.getPickupTime().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
        assertEquals(LocalDate.of(2026, 11, 7), closing.getPlannedEndAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
        assertEquals(closing.getRenter(), cancel.getRenter());
        assertTrue(cancel.getPickupTime().isAfter(closing.getPlannedEndAt()));
        assertEquals(BookingStatus.CONFIRMED, cancel.getStatus()); assertNull(cancel.getActualStartAt());
        assertNull(workflows.get(cancel.getId()).getCurrentInitialKm());
        assertEquals(2, handovers.size());
        assertEquals(2, requests.get(cancel.getId()).stream().filter(r -> r.getStatus() == PaymentRequestStatus.CONFIRMED).count());
        assertEquals(4, requests.get(monthly.getId()).stream().filter(r -> r.getStatus() == PaymentRequestStatus.CONFIRMED).count());
        assertEquals(13, requests.get(closing.getId()).stream().filter(r -> r.getStatus() == PaymentRequestStatus.CONFIRMED).count());
    }

    @Test
    void restartingPreservesProgressAndDoesNotDuplicateBookingsOrPayments() {
        seeder.seed();
        var monthly = bookings.get("EM-Y2-MONTHLY");
        monthly.setStatus(BookingStatus.AWAITING_FINAL_PAYMENT);
        workflows.get(monthly.getId()).setStage("SETTLEMENT_PROPOSED");
        requests.get(monthly.getId()).get(4).setStatus(PaymentRequestStatus.CONFIRMED);
        seeder.seed();
        assertEquals(3, bookings.size()); assertEquals(3, requests.size()); assertEquals(2, handovers.size());
        assertEquals(BookingStatus.AWAITING_FINAL_PAYMENT, monthly.getStatus());
        assertEquals("SETTLEMENT_PROPOSED", workflows.get(monthly.getId()).getStage());
        assertEquals(PaymentRequestStatus.CONFIRMED, requests.get(monthly.getId()).get(4).getStatus());
    }

    @Test
    void occupiedDemoVehiclesAreNotOverwrittenBySeed() {
        when(schedules.isRangeFree(anyLong(), any(), any())).thenReturn(false);
        seeder.seed();
        assertTrue(bookings.isEmpty());
        assertTrue(requests.isEmpty());
        assertTrue(handovers.isEmpty());
    }
}
