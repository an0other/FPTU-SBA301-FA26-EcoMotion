package com.fptufa26.ecomotion.finance.controller;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.BookingPolicySnapshot;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.repository.BookingPolicySnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

final class VnpayBookingFixture {
    static Booking create(BookingRepository bookings, BookingPolicySnapshotRepository snapshots) {
        Booking sample = bookings.findAll().stream().filter(b -> b.getRenter() != null).findFirst().orElseThrow();
        Booking booking = new Booking();
        booking.setBookingCode("PAY-" + UUID.randomUUID().toString().substring(0, 20));
        booking.setRenter(sample.getRenter());
        booking.setVehicle(sample.getVehicle());
        booking.setPlan(sample.getPlan());
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setPickupAddress("Payment integration test pickup");
        booking.setPickupTime(Instant.now().plus(Duration.ofDays(90)));
        booking.setPassengerCount(1);
        booking.setTotalAmount(new BigDecimal("1400000"));
        booking = bookings.saveAndFlush(booking);
        BookingPolicySnapshot snapshot = new BookingPolicySnapshot();
        snapshot.setBooking(booking);
        snapshot.setSlotPrice(new BigDecimal("1400000"));
        snapshot.setSlotLengthMinutes(60);
        snapshot.setPlanType(com.fptufa26.ecomotion.vehicle.enums.PlanType.HOURLY);
        snapshot.setDepositRatio(new BigDecimal("0.3000"));
        snapshots.saveAndFlush(snapshot);
        return booking;
    }
}
