package com.fptufa26.ecomotion.user.service;

import com.fptufa26.ecomotion.booking.service.BookingQueryService;
import com.fptufa26.ecomotion.common.exception.BadRequestException;
import com.fptufa26.ecomotion.user.entity.Driver;
import com.fptufa26.ecomotion.user.entity.Provider;
import com.fptufa26.ecomotion.user.entity.User;
import com.fptufa26.ecomotion.user.enums.DriverOperationalStatus;
import com.fptufa26.ecomotion.user.enums.DriverStatus;
import com.fptufa26.ecomotion.user.repository.DriverRepository;
import com.fptufa26.ecomotion.user.repository.ProviderRepository;
import com.fptufa26.ecomotion.user.repository.UserRepository;
import com.fptufa26.ecomotion.user.service.implement.ProviderDriverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderDriverUpdatedTimeTest {
    @Mock DriverRepository drivers;
    @Mock UserRepository users;
    @Mock ProviderRepository providers;
    @Mock BookingQueryService bookings;
    @Mock DriverStatusService statuses;
    @Mock PasswordEncoder passwords;
    ProviderDriverService service;
    final LocalDate day = LocalDate.of(2026, 10, 28);

    @BeforeEach
    void setup() {
        service = new ProviderDriverServiceImpl(drivers, users, providers, bookings, statuses, passwords,
                Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC));
        Provider provider = new Provider();
        provider.setId(10L);
        lenient().when(providers.findByUser_Id(1L)).thenReturn(Optional.of(provider));
        lenient().when(statuses.calculateOperationalStatus(any())).thenReturn(DriverOperationalStatus.AVAILABLE);
        lenient().when(drivers.findByProvider_IdWithUser(10L)).thenReturn(List.of(
                driver(1, "2026-10-27T16:59:59Z", "An", DriverStatus.APPROVED),
                driver(2, "2026-10-27T17:00:00Z", "Binh", DriverStatus.APPROVED),
                driver(3, "2026-10-28T16:59:59.999999Z", "An", DriverStatus.APPROVED),
                driver(4, "2026-10-28T16:59:59.999999Z", "An", DriverStatus.PENDING),
                driver(5, "2026-10-28T17:00:00Z", "Dung", DriverStatus.APPROVED),
                driver(6, null, "An", DriverStatus.APPROVED)));
    }

    @Test
    void defaultSortUsesUpdatedAtWithStableIdAcrossPagesAndNullLast() {
        var first = service.getDriversPaged(1L, null, null, null, null, null, null, 0, 2);
        var second = service.getDriversPaged(1L, null, null, null, null, null, null, 2, 2);
        var last = service.getDriversPaged(1L, null, null, null, null, null, null, 4, 2);
        assertThat(first.items()).extracting(d -> d.id()).containsExactly(5L, 4L);
        assertThat(second.items()).extracting(d -> d.id()).containsExactly(3L, 2L);
        assertThat(last.items()).extracting(d -> d.id()).containsExactly(1L, 6L);
        assertThat(first.total()).isEqualTo(6);
        assertThat(first.hasMore()).isTrue();
        assertThat(last.hasMore()).isFalse();
    }

    @Test
    void oldestSortKeepsNullLastAndStableTies() {
        var page = service.getDriversPaged(1L, null, null, null, "updated_asc", null, null, 0, 100);
        assertThat(page.items()).extracting(d -> d.id()).containsExactly(1L, 2L, 4L, 3L, 5L, 6L);
    }

    @Test
    void vietnamDayIncludesMidnightAndLastFractionOfSecondButNotNextDayOrNull() {
        var page = service.getDriversPaged(1L, null, null, null, null, day, day, 0, 100);
        assertThat(page.items()).extracting(d -> d.id()).containsExactly(4L, 3L, 2L);
    }

    @Test
    void searchStatusOperationalStatusAndDatesRunBeforePagination() {
        var page = service.getDriversPaged(1L, " AN ", DriverStatus.APPROVED,
                DriverOperationalStatus.AVAILABLE, "updated_desc", day, day, 0, 1);
        assertThat(page.items()).extracting(d -> d.id()).containsExactly(3L);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void openEndedRangesWork() {
        assertThat(service.getDriversPaged(1L, null, null, null, null, day, null, 0, 100).items())
                .extracting(d -> d.id()).containsExactly(5L, 4L, 3L, 2L);
        assertThat(service.getDriversPaged(1L, null, null, null, null, null, day, 0, 100).items())
                .extracting(d -> d.id()).containsExactly(4L, 3L, 2L, 1L);
    }

    @Test
    void noResultsAndOffsetsBeyondEndReturnEmptyPages() {
        var empty = service.getDriversPaged(1L, "no-match", null, null, null, day, day, 0, 12);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.total()).isZero();
        assertThat(empty.hasMore()).isFalse();
        var beyond = service.getDriversPaged(1L, null, null, null, null, null, null, 20, 12);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(6);
    }

    @Test
    void rejectsReversedDatesAndInvalidPagination() {
        assertThatThrownBy(() -> service.getDriversPaged(1L, null, null, null, null, day.plusDays(1), day, 0, 12))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getDriversPaged(1L, null, null, null, null, null, null, -1, 12))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getDriversPaged(1L, null, null, null, null, null, null, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void readingDoesNotWriteTimestamps() {
        service.getDriversPaged(1L, null, null, null, null, null, null, 0, 12);
        verify(drivers, never()).save(any());
        verify(drivers, never()).saveAndFlush(any());
    }

    private Driver driver(long id, String updatedAt, String name, DriverStatus status) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setStatus(status);
        driver.setUpdatedAt(updatedAt == null ? null : Instant.parse(updatedAt));
        driver.setStatusUpdatedAt(Instant.parse("2027-01-01T00:00:00Z").minusSeconds(id));
        User user = new User();
        user.setFullName(name);
        driver.setUser(user);
        return driver;
    }
}
