package com.fptufa26.ecomotion.booking.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fptufa26.ecomotion.booking.policy.VehicleChangePolicy.Segment;
import com.fptufa26.ecomotion.vehicle.entity.Vehicle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VehicleChangePolicyTest {
    private static BigDecimal n(long v) { return BigDecimal.valueOf(v); }
    private static Vehicle car(int seats, int year) {
        Vehicle v = new Vehicle(); v.setSeatCount(seats); v.setManufactureYear(year); return v;
    }

    @Test void pricierReplacementCostsRenterNothing() {
        assertThat(VehicleChangePolicy.discount(n(1_000_000), n(1_500_000))).isEqualByComparingTo("0");
    }
    @Test void cheaperReplacementIsDeducted() {
        assertThat(VehicleChangePolicy.discount(n(1_000_000), n(800_000))).isEqualByComparingTo("200000");
    }
    @Test void equalPriceHasNoDifference() {
        assertThat(VehicleChangePolicy.discount(n(1_000_000), n(1_000_000))).isEqualByComparingTo("0");
    }

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2027, 1, 1); // 365 ngày

    @Test void yearlyDiscountOnlyCoversDaysOnCheaperVehicle() {
        // 365.000.000 hợp đồng, xe mới 255.500.000 (rẻ hơn 109.500.000) dùng 73 ngày cuối: 109.5M*73/365 = 21.9M
        var seg = List.of(new Segment(n(255_500_000), END.minusDays(73), END));
        assertThat(VehicleChangePolicy.yearlyDiscount(n(365_000_000), START, END, seg)).isEqualByComparingTo("21900000");
    }
    @Test void yearlyDiscountStopsAtEarlyReturn() {
        var seg = List.of(new Segment(n(255_500_000), START.plusDays(100), START.plusDays(150)));
        // trả xe ngày 150: 109.5M * 50 / 365 = 15.0M
        assertThat(VehicleChangePolicy.yearlyDiscount(n(365_000_000), START, END, seg)).isEqualByComparingTo("15000000");
    }
    @Test void yearlyPricierSegmentContributesNothingAndChainDoesNotDoubleCount() {
        var seg = List.of(
                new Segment(n(400_000_000), START.plusDays(50), START.plusDays(100)),   // đắt hơn: 0
                new Segment(n(255_500_000), START.plusDays(100), START.plusDays(173))); // rẻ hơn 73 ngày: 21.9M
        assertThat(VehicleChangePolicy.yearlyDiscount(n(365_000_000), START, END, seg)).isEqualByComparingTo("21900000");
    }
    @Test void yearlyDiscountIsDeterministicAcrossRepeatedSettlements() {
        var seg = List.of(new Segment(n(255_500_000), START.plusDays(100), END));
        var first = VehicleChangePolicy.yearlyDiscount(n(365_000_000), START, END, seg);
        assertThat(VehicleChangePolicy.yearlyDiscount(n(365_000_000), START, END, seg)).isEqualByComparingTo(first);
    }
    @Test void lowerQualityDetectsFewerSeatsAndOlderYear() {
        assertThat(VehicleChangePolicy.qualityDowngrades(car(7, 2024), car(4, 2020))).hasSize(2);
        assertThat(VehicleChangePolicy.qualityDowngrades(car(4, 2020), car(7, 2024))).isEmpty();
        assertThat(VehicleChangePolicy.qualityDowngrades(car(4, 2020), car(4, 2020))).isEmpty();
    }
}
