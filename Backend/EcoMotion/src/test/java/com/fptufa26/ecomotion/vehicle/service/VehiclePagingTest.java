package com.fptufa26.ecomotion.vehicle.service;

import com.fptufa26.ecomotion.common.response.PageResponse;
import com.fptufa26.ecomotion.vehicle.dto.response.VehicleCardResponse;
import com.fptufa26.ecomotion.vehicle.entity.PlanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the offset/limit paging, filtering and ordering of the renter fleet.
 *
 * <p>The dev seed creates 8 approved vehicles (ids 1-4 for provider A, 5-8 for provider B), each
 * with its own rating and prices. Page size 2 is used so the fleet spans four pages and the
 * boundary cases are reachable.
 */
@SpringBootTest
@ActiveProfiles("dev")
class VehiclePagingTest {

    private static final int PAGE = 2;
    private static final long FLEET_SIZE = 8;

    @Autowired
    private VehicleService vehicleService;

    private PageResponse<VehicleCardResponse> list(String keyword, String location, String fuelType,
                                                   String planType, BigDecimal maxPrice,
                                                   VehicleSort sort, int offset, int limit) {
        return vehicleService.listRentableVehicles(keyword, location, fuelType, planType, maxPrice,
                sort, offset, limit);
    }

    private PageResponse<VehicleCardResponse> page(int offset) {
        return list(null, null, null, null, null, VehicleSort.DEFAULT, offset, PAGE);
    }

    /** The price the card quotes: the plan's own price, or the cheapest plan when none is filtered. */
    private static BigDecimal cardPrice(VehicleCardResponse card, PlanType planType) {
        return card.plans().stream()
                .filter(plan -> planType == null || plan.planType() == planType)
                .map(VehicleCardResponse.PlanPrice::slotPrice)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private static List<BigDecimal> pricesOf(List<VehicleCardResponse> cards, PlanType planType) {
        return cards.stream().map(card -> cardPrice(card, planType)).toList();
    }

    private static List<Long> idsOf(List<VehicleCardResponse> cards) {
        return cards.stream().map(VehicleCardResponse::id).toList();
    }

    // ------------------------------------------------------------------ paging

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
        List<Long> whole = idsOf(list(null, null, null, null, null, VehicleSort.DEFAULT, 0, 100).items());

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

    // ------------------------------------------------------------------ filtering

    @Test
    @DisplayName("total reflects the filters, not the raw fleet size")
    void totalHonoursFilters() {
        // Provider B owns exactly one BMW, so a keyword on its brand must shrink `total` to 1
        // while the paging envelope still reports a single page with hasMore=false.
        PageResponse<VehicleCardResponse> result =
                list("BMW", null, null, null, null, VehicleSort.DEFAULT, 0, PAGE);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName("every card carries its provider depot address")
    void cardsExposeDepotAddress() {
        PageResponse<VehicleCardResponse> result =
                list(null, null, null, null, null, VehicleSort.DEFAULT, 0, 100);

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
                list(null, "Nguy", null, null, null, VehicleSort.DEFAULT, 0, 100);

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

        assertThat(list(null, null, null, "HOURLY", ceiling, VehicleSort.DEFAULT, 0, 100).total())
                .isEqualTo(FLEET_SIZE);
        assertThat(list(null, null, null, "MONTHLY", ceiling, VehicleSort.DEFAULT, 0, 100).total())
                .isZero();
        // Without a plan filter the ceiling keeps following the cheapest plan.
        assertThat(list(null, null, null, null, ceiling, VehicleSort.DEFAULT, 0, 100).total())
                .isEqualTo(FLEET_SIZE);
    }

    // ------------------------------------------------------------------ ordering

    @Test
    @DisplayName("the default order is the fleet id order")
    void defaultSortKeepsIdOrder() {
        assertThat(idsOf(list(null, null, null, null, null, VehicleSort.DEFAULT, 0, 100).items()))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    /**
     * The regression this ordering was moved server-side for.
     *
     * <p>The sort used to run in the browser over the six cards already on screen, so "price low to
     * high" merely reshuffled the current page: page 2 bore no relation to page 1, and the cheapest
     * car in the fleet could sit on the last page. Sorting before the slice is what fixes it.
     */
    @Test
    @DisplayName("sorting happens before paging, so page 1 holds the fleet's cheapest cars")
    void sortAppliesBeforePaging() {
        List<Long> cheapestTwo = idsOf(list(null, null, null, null, null,
                VehicleSort.PRICE_ASC, 0, 100).items()).subList(0, PAGE);

        PageResponse<VehicleCardResponse> first = list(null, null, null, null, null,
                VehicleSort.PRICE_ASC, 0, PAGE);
        PageResponse<VehicleCardResponse> second = list(null, null, null, null, null,
                VehicleSort.PRICE_ASC, PAGE, PAGE);

        // Page 0 must be the two cheapest cars of the WHOLE fleet. In id order those two would be
        // 180.000 then 150.000, i.e. unsorted - so this fails outright if the sort is skipped.
        assertThat(idsOf(first.items())).containsExactlyElementsOf(cheapestTwo);
        assertThat(pricesOf(first.items(), null)).isSorted();
        assertThat(pricesOf(second.items(), null)).isSorted();
        // And the page boundary is respected: page 1 must not start cheaper than page 0 ended.
        assertThat(cardPrice(first.items().get(PAGE - 1), null))
                .isLessThanOrEqualTo(cardPrice(second.items().get(0), null));
    }

    @Test
    @DisplayName("the price sort reads the selected plan's price, not the cheapest one")
    void priceSortFollowsTheSelectedPlan() {
        List<VehicleCardResponse> byHour =
                list(null, null, null, "HOURLY", null, VehicleSort.PRICE_ASC, 0, 100).items();
        List<VehicleCardResponse> byMonth =
                list(null, null, null, "MONTHLY", null, VehicleSort.PRICE_ASC, 0, 100).items();

        assertThat(pricesOf(byHour, PlanType.HOURLY)).isSorted();
        assertThat(pricesOf(byMonth, PlanType.MONTHLY)).isSorted();

        // The two orders genuinely differ in the seed: the Porsche is the dearest by the hour but
        // only mid-pack by the month (its long-term rate is discounted hard). That is what gives
        // this test teeth - a sort that ignored the plan filter and always compared the hourly
        // figure would return the same order twice. Keep that spread when editing the seed.
        assertThat(idsOf(byHour)).isNotEqualTo(idsOf(byMonth));
    }

    @Test
    @DisplayName("highest rated first, with the unrated car last")
    void ratingSortPutsTheUnratedCarLast() {
        List<VehicleCardResponse> cards =
                list(null, null, null, null, null, VehicleSort.RATING, 0, 100).items();

        List<BigDecimal> ratings =
                cards.stream().map(VehicleCardResponse::ratingAvg).toList();
        assertThat(ratings).isSortedAccordingTo(Comparator.<BigDecimal>reverseOrder());

        // Vehicle 8 is seeded unrated (rating_count = 0 with rating_avg = 0, because the schema
        // forbids a NULL average). It must sink to the bottom, not top the list on a zero.
        assertThat(cards).last().extracting(VehicleCardResponse::id).isEqualTo(8L);
    }
}
