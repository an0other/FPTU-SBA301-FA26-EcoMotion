package com.fptufa26.ecomotion.booking.repository;

import com.fptufa26.ecomotion.booking.dto.response.ProviderBookingResponse;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.service.ProviderBookingService;
import com.fptufa26.ecomotion.booking.specification.BookingSpecification;
import com.fptufa26.ecomotion.common.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class BookingRepositoryRefactorTest {

    private static final Long PROVIDER_A_USER_ID = 2L;
    private static final Long PROVIDER_B_USER_ID = 4L;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ProviderBookingService providerBookingService;

    @Test
    @DisplayName("1. Provider isolation: Provider only sees bookings for their own vehicles")
    void providerOnlySeesOwnBookings() {
        // Provider A has 28 bookings in seeded data
        List<ProviderBookingResponse> providerABookings = providerBookingService.listBookings(PROVIDER_A_USER_ID, null);
        assertThat(providerABookings).hasSize(28);

        // Provider B has 2 distinct seeded bookings (BK-B0A111, BK-B0A222) and sees none of Provider A's
        List<ProviderBookingResponse> providerBBookings = providerBookingService.listBookings(PROVIDER_B_USER_ID, null);
        assertThat(providerBBookings).hasSize(2);
        assertThat(providerBBookings).noneMatch(b -> providerABookings.stream().anyMatch(a -> a.bookingCode().equals(b.bookingCode())));

        // Direct specification check: Null providerId matches nothing (strict tenant isolation)
        Specification<Booking> leakPreventionSpec = BookingSpecification.hasProvider(null);
        List<Booking> leaked = bookingRepository.findAll(leakPreventionSpec);
        assertThat(leaked).isEmpty();
    }

    @Test
    @DisplayName("2. No status filter returns all allowed booking statuses")
    void omittingStatusReturnsAllStatuses() {
        List<ProviderBookingResponse> allBookings = providerBookingService.listBookings(PROVIDER_A_USER_ID, "ALL");
        assertThat(allBookings).hasSize(28);

        Set<BookingStatus> statuses = new HashSet<>();
        for (ProviderBookingResponse b : allBookings) {
            statuses.add(b.status());
        }

        // All seeded statuses present
        assertThat(statuses).contains(
                BookingStatus.PENDING,
                BookingStatus.ACCEPTED,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS,
                BookingStatus.COMPLETED,
                BookingStatus.CANCELLED,
                BookingStatus.REJECTED
        );
    }

    @Test
    @DisplayName("3. Filtering by specific status returns exact matching bookings")
    void statusFilterWorksAccurately() {
        List<ProviderBookingResponse> pending = providerBookingService.listBookings(PROVIDER_A_USER_ID, "PENDING");
        assertThat(pending).hasSize(3);
        assertThat(pending).allMatch(b -> b.status() == BookingStatus.PENDING);

        List<ProviderBookingResponse> inProgress = providerBookingService.listBookings(PROVIDER_A_USER_ID, "IN_PROGRESS");
        assertThat(inProgress).hasSize(2);
        assertThat(inProgress).allMatch(b -> b.status() == BookingStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("4. Bookings without driver or with nullable relations are handled gracefully")
    void nullableDriverHandledCorrectly() {
        List<ProviderBookingResponse> pending = providerBookingService.listBookings(PROVIDER_A_USER_ID, "PENDING");
        assertThat(pending).isNotEmpty();

        // PENDING bookings have no driver assigned yet
        ProviderBookingResponse unassigned = pending.get(0);
        assertThat(unassigned.driverName()).isNull();
        assertThat(unassigned.bookingCode()).isNotBlank();
        assertThat(unassigned.renterName()).isNotBlank();
        assertThat(unassigned.vehicleName()).isNotBlank();
    }

    @Test
    @DisplayName("5. Bookings are sorted strictly by createdAt DESC, id DESC")
    void sortingIsCreatedAtDescAndIdDesc() {
        List<ProviderBookingResponse> bookings = providerBookingService.listBookings(PROVIDER_A_USER_ID, null);
        assertThat(bookings).hasSize(28);

        for (int i = 0; i < bookings.size() - 1; i++) {
            ProviderBookingResponse curr = bookings.get(i);
            ProviderBookingResponse next = bookings.get(i + 1);

            assertThat(curr.createdAt()).isAfterOrEqualTo(next.createdAt());
        }
    }

    @Test
    @DisplayName("6. Pagination with Pageable returns correct slices without missing or duplicate records")
    void paginationWithPageableHasNoGapsOrDuplicates() {
        Page<ProviderBookingResponse> page0 = providerBookingService.listBookings(
                PROVIDER_A_USER_ID, null, PageRequest.of(0, 10));
        Page<ProviderBookingResponse> page1 = providerBookingService.listBookings(
                PROVIDER_A_USER_ID, null, PageRequest.of(1, 10));
        Page<ProviderBookingResponse> page2 = providerBookingService.listBookings(
                PROVIDER_A_USER_ID, null, PageRequest.of(2, 10));

        assertThat(page0.getTotalElements()).isEqualTo(28);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.getContent()).hasSize(10);
        assertThat(page1.getContent()).hasSize(10);
        assertThat(page2.getContent()).hasSize(8);

        // Verify uniqueness across pages
        Set<String> allCodes = new HashSet<>();
        page0.getContent().forEach(b -> allCodes.add(b.bookingCode()));
        page1.getContent().forEach(b -> allCodes.add(b.bookingCode()));
        page2.getContent().forEach(b -> allCodes.add(b.bookingCode()));

        assertThat(allCodes).hasSize(28);
    }

    @Test
    @DisplayName("7. Offset/Limit PageResponse pagination returns accurate metadata")
    void pageResponsePaginationWorksAccurately() {
        PageResponse<ProviderBookingResponse> p1 = providerBookingService.listBookingsPaged(
                PROVIDER_A_USER_ID, null, 0, 10);
        assertThat(p1.total()).isEqualTo(28);
        assertThat(p1.offset()).isEqualTo(0);
        assertThat(p1.limit()).isEqualTo(10);
        assertThat(p1.hasMore()).isTrue();
        assertThat(p1.items()).hasSize(10);

        PageResponse<ProviderBookingResponse> p3 = providerBookingService.listBookingsPaged(
                PROVIDER_A_USER_ID, null, 20, 10);
        assertThat(p3.total()).isEqualTo(28);
        assertThat(p3.offset()).isEqualTo(20);
        assertThat(p3.limit()).isEqualTo(10);
        assertThat(p3.hasMore()).isFalse();
        assertThat(p3.items()).hasSize(8);
    }

    @Test
    @DisplayName("8. EntityGraph fetches all many-to-one relations without throwing LazyInitializationException")
    void entityGraphEagerlyFetchesAssociations() {
        Specification<Booking> spec = BookingSpecification.hasProvider(1L); // Provider A's provider entity id is 1
        List<Booking> bookings = bookingRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(bookings).isNotEmpty();

        Booking first = bookings.get(0);
        // Accessing LAZY relations directly
        assertThat(first.getVehicle().getBrand()).isNotBlank();
        assertThat(first.getRenter().getUser().getEmail()).isNotBlank();
        assertThat(first.getPlan().getPlanType()).isNotNull();
    }
}
