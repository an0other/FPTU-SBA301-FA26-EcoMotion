package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the offset/limit paging of the renter fleet.
 *
 * <p>The dev seed creates 8 approved vehicles (ids 1-4 for provider A, 5-8 for provider B). Page
 * size 2 is used so the fleet spans four pages and the boundary cases are reachable.
 */
@SpringBootTest
@ActiveProfiles("dev")
class VehiclePagingTest {

    private static final int PAGE = 2;
    private static final long FLEET_SIZE = 8;

    @Autowired
    private VehicleService vehicleService;

    private PageResponse<VehicleCardResponse> page(int offset) {
        return vehicleService.listRentableVehicles(null, null, null, null, null, offset, PAGE);
    }

    @Test
    @DisplayName("first page returns only `limit` items but reports the full total")
    void firstPage() {
        PageResponse<VehicleCardResponse> result = page(0);

        assertThat(result.items()).hasSize(PAGE);
        assertThat(result.total()).isEqualTo(FLEET_SIZE);
        assertThat(result.offset()).isZero();
        assertThat(result.limit()).isEqualTo(PAGE);
        assertThat(result.hasMore()).isTrue();
    }

    @Test
    @DisplayName("consecutive pages do not overlap and together cover the fleet")
    void pagesAreContiguous() {
        List<Long> walked = new ArrayList<>();
        int offset = 0;
        while (true) {
            PageResponse<VehicleCardResponse> result = page(offset);
            result.items().stream().map(VehicleCardResponse::id).forEach(walked::add);
            if (!result.hasMore()) {
                break;
            }
            offset += PAGE;
        }

        // Paging must reproduce exactly the fleet returned in a single large page.
        List<Long> whole = vehicleService.listRentableVehicles(null, null, null, null, null, 0, 100)
                .items().stream().map(VehicleCardResponse::id).toList();

        assertThat(walked).doesNotHaveDuplicates().containsExactlyElementsOf(whole);
        assertThat(walked).hasSize((int) FLEET_SIZE);
    }

    @Test
    @DisplayName("last page reports hasMore=false")
    void lastPageHasNoMore() {
        // Four pages of two, so the last starts at offset 6.
        PageResponse<VehicleCardResponse> last = page(6);

        assertThat(last.items()).hasSize(2);
        assertThat(last.hasMore()).isFalse();
        assertThat(last.total()).isEqualTo(FLEET_SIZE);
    }

    @Test
    @DisplayName("an offset past the end yields an empty page instead of throwing")
    void offsetBeyondEndIsEmpty() {
        PageResponse<VehicleCardResponse> result = page(999);

        assertThat(result.items()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.total()).isEqualTo(FLEET_SIZE);
    }

    @Test
    @DisplayName("a negative offset is clamped to the first page")
    void negativeOffsetClampsToStart() {
        PageResponse<VehicleCardResponse> result = page(-10);

        assertThat(result.offset()).isZero();
        assertThat(result.items()).hasSize(PAGE);
    }

    @Test
    @DisplayName("total reflects the filters, not the raw fleet size")
    void totalHonoursFilters() {
        // Provider B owns exactly one BMW, so a keyword on its brand must shrink `total` to 1
        // while the paging envelope still reports a single page with hasMore=false.
        PageResponse<VehicleCardResponse> result =
                vehicleService.listRentableVehicles("BMW", null, null, null, null, 0, PAGE);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName("every card carries its provider depot address")
    void cardsExposeDepotAddress() {
        PageResponse<VehicleCardResponse> result =
                vehicleService.listRentableVehicles(null, null, null, null, null, 0, 100);

        assertThat(result.items()).isNotEmpty();
        assertThat(result.items())
                .allSatisfy(card -> assertThat(card.providerDepotAddress()).isNotBlank());
    }

    @Test
    @DisplayName("location filters on the provider depot address")
    void locationMatchesDepotAddress() {
        // Provider A's depot sits on "Nguyen Hue" (Quan 1) and owns vehicles 1-4; provider B's is
        // on "Le Loi" (Quan 3). The needle is deliberately diacritic-free so this source file
        // stays ASCII-only - the address itself is stored decoded and still contains "Nguy".
        PageResponse<VehicleCardResponse> result =
                vehicleService.listRentableVehicles(null, "Nguy", null, null, null, 0, 100);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.items())
                .allSatisfy(card -> assertThat(card.providerDepotAddress()).contains("Nguy"));
    }

    @Test
    @DisplayName("the price ceiling is read on the scale of the filtered plan")
    void priceCeilingFollowsSelectedPlan() {
        // The seed varies prices per car, but the bands do not overlap: every hourly plan is under
        // 1.000.000 (150.000-700.000) while every monthly plan is far above it (13.500.000+). A
        // 1.000.000 ceiling therefore keeps the whole fleet on the hourly scale and drops all of it
        // on the monthly one - the exact bug this guards: the ceiling used to always be compared
        // against the cheapest plan, so a monthly filter was judged against an hourly price.
        BigDecimal ceiling = new BigDecimal("1000000");

        assertThat(vehicleService
                .listRentableVehicles(null, null, null, "HOURLY", ceiling, 0, 100).total())
                .isEqualTo(FLEET_SIZE);
        assertThat(vehicleService
                .listRentableVehicles(null, null, null, "MONTHLY", ceiling, 0, 100).total())
                .isZero();
        // Without a plan filter the ceiling keeps following the cheapest plan.
        assertThat(vehicleService
                .listRentableVehicles(null, null, null, null, ceiling, 0, 100).total())
                .isEqualTo(FLEET_SIZE);
    }
}
