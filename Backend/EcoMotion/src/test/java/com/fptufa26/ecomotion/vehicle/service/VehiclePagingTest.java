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

        List<Long> whole = idsOf(list(null, null, null, null, null, VehicleSort.DEFAULT, 0, 100).items());

        assertThat(walked).doesNotHaveDuplicates().containsExactlyElementsOf(whole);
        assertThat(walked).hasSize((int) FLEET_SIZE);
    }

    @Test
    @DisplayName("last page reports hasMore=false")
    void lastPageHasNoMore() {
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
        PageResponse<VehicleCardResponse> result =
                list(null, "Nguy", null, null, null, VehicleSort.DEFAULT, 0, 100);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.items())
                .allSatisfy(card -> assertThat(card.providerDepotAddress()).contains("Nguy"));
    }

    @Test
    @DisplayName("the price ceiling is read on the scale of the filtered plan")
    void priceCeilingFollowsSelectedPlan() {
        BigDecimal ceiling = new BigDecimal("1000000");

        assertThat(list(null, null, null, "HOURLY", ceiling, VehicleSort.DEFAULT, 0, 100).total())
                .isEqualTo(FLEET_SIZE);
        assertThat(list(null, null, null, "MONTHLY", ceiling, VehicleSort.DEFAULT, 0, 100).total())
                .isZero();
        assertThat(list(null, null, null, null, ceiling, VehicleSort.DEFAULT, 0, 100).total())
                .isEqualTo(FLEET_SIZE);
    }


    @Test
    @DisplayName("the default order is the fleet id order")
    void defaultSortKeepsIdOrder() {
        assertThat(idsOf(list(null, null, null, null, null, VehicleSort.DEFAULT, 0, 100).items()))
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    @Test
    @DisplayName("sorting happens before paging, so page 1 holds the fleet's cheapest cars")
    void sortAppliesBeforePaging() {
        List<Long> cheapestTwo = idsOf(list(null, null, null, null, null,
                VehicleSort.PRICE_ASC, 0, 100).items()).subList(0, PAGE);

        PageResponse<VehicleCardResponse> first = list(null, null, null, null, null,
                VehicleSort.PRICE_ASC, 0, PAGE);
        PageResponse<VehicleCardResponse> second = list(null, null, null, null, null,
                VehicleSort.PRICE_ASC, PAGE, PAGE);

        assertThat(idsOf(first.items())).containsExactlyElementsOf(cheapestTwo);
        assertThat(pricesOf(first.items(), null)).isSorted();
        assertThat(pricesOf(second.items(), null)).isSorted();
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

        assertThat(cards).last().extracting(VehicleCardResponse::id).isEqualTo(8L);
    }
}
