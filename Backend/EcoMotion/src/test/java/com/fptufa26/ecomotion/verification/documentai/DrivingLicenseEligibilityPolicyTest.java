package com.fptufa26.ecomotion.verification.documentai;

import com.fptufa26.ecomotion.common.exception.ConflictException;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrivingLicenseEligibilityPolicyTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Test
    void onlyCurrentClassBWithFiniteCurrentExpiryIsAutoEligible() {
        assertThat(DrivingLicenseEligibilityPolicy.isEligibleForYearlyRental(
                " b ",
                LocalDate.of(2025, 1, 1),
                LocalDate.now(VIETNAM_ZONE).plusYears(1),
                false)).isTrue();

        assertThat(DrivingLicenseEligibilityPolicy.isEligibleForYearlyRental(
                "B2",
                LocalDate.of(2024, 12, 31),
                LocalDate.now(VIETNAM_ZONE).plusYears(1),
                false)).isFalse();
        assertThat(DrivingLicenseEligibilityPolicy.isEligibleForYearlyRental(
                "B",
                LocalDate.of(2025, 1, 1),
                null,
                true)).isFalse();
    }

    @Test
    void bookingRequiresVerifiedLicenseThatCoversTheRentalAndAge() {
        Instant start = LocalDate.now(VIETNAM_ZONE).plusDays(5)
                .atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant end = start.plusSeconds(365L * 24 * 60 * 60);
        Renter renter = eligibleRenter(LocalDate.ofInstant(end, VIETNAM_ZONE));
        Vehicle vehicle = vehicleWithSeats(9);

        DrivingLicenseEligibilityPolicy.requireEligibleForBooking(renter, vehicle, start, end);

        renter.setLicenseExpiry(LocalDate.ofInstant(end, VIETNAM_ZONE).minusDays(1));
        assertThatThrownBy(() ->
                DrivingLicenseEligibilityPolicy.requireEligibleForBooking(renter, vehicle, start, end))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("còn hạn đến hết");

        Renter underageRenter = eligibleRenter(LocalDate.ofInstant(end, VIETNAM_ZONE));
        underageRenter.setLicenseDateOfBirth(
                LocalDate.ofInstant(start, VIETNAM_ZONE).minusYears(18).plusDays(1));
        assertThatThrownBy(() ->
                DrivingLicenseEligibilityPolicy.requireEligibleForBooking(underageRenter, vehicle, start, end))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đủ 18 tuổi");
    }

    @Test
    void bookingRejectsUnverifiedLicenseAndUnsupportedVehicleSize() {
        Instant start = LocalDate.now(VIETNAM_ZONE).plusDays(5)
                .atStartOfDay(VIETNAM_ZONE).toInstant();
        Instant end = start.plusSeconds(365L * 24 * 60 * 60);
        Renter renter = eligibleRenter(LocalDate.ofInstant(end, VIETNAM_ZONE));
        Vehicle vehicle = vehicleWithSeats(9);

        renter.setLicenseVerified(false);
        assertThatThrownBy(() ->
                DrivingLicenseEligibilityPolicy.requireEligibleForBooking(renter, vehicle, start, end))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("OCR/QR");

        renter.setLicenseVerified(true);
        vehicle.setSeatCount(10);
        assertThatThrownBy(() ->
                DrivingLicenseEligibilityPolicy.requireEligibleForBooking(renter, vehicle, start, end))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tối đa 8 hành khách");
    }

    private Renter eligibleRenter(LocalDate expiry) {
        Renter renter = new Renter();
        renter.setLicenseVerified(true);
        renter.setLicenseClass("B");
        renter.setLicenseIssueDate(LocalDate.of(2025, 1, 1));
        renter.setLicenseExpiry(expiry);
        renter.setLicenseDateOfBirth(LocalDate.now(VIETNAM_ZONE).minusYears(25));
        return renter;
    }

    private Vehicle vehicleWithSeats(int seatCount) {
        Vehicle vehicle = new Vehicle();
        vehicle.setSeatCount(seatCount);
        return vehicle;
    }
}
