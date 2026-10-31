package com.fptufa26.ecomotion.booking.specification;

import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import com.fptufa26.ecomotion.booking.enums.TripView;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Reusable specifications for dynamic filtering on {@link Booking} entities.
 */
public final class BookingSpecification {

    private BookingSpecification() {
    }

    /**
     * Filters bookings belonging to a specific provider.
     * Enforces strict tenant isolation: if providerId is null, returns disjunction (matches nothing).
     */
    public static Specification<Booking> hasProvider(Long providerId) {
        return (root, query, cb) -> {
            if (providerId == null) {
                return cb.disjunction();
            }
            return cb.equal(root.get("vehicle").get("provider").get("id"), providerId);
        };
    }

    /**
     * Filters bookings by status. Blank or null status matches everything.
     */
    public static Specification<Booking> hasStatus(BookingStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Booking> hasDriver(Long driverId) {
        return (root, query, cb) -> {
            if (driverId == null) {
                return cb.disjunction();
            }
            return cb.equal(root.get("driver").get("id"), driverId);
        };
    }

    public static Specification<Booking> hasStatusIn(Collection<BookingStatus> statuses) {
        return (root, query, cb) -> (statuses == null || statuses.isEmpty()) ? null : root.get("status").in(statuses);
    }

    public static Specification<Booking> pickupTimeBetween(Instant fromInclusive, Instant toExclusive) {
        return (root, query, cb) -> {
            if (fromInclusive != null && toExclusive != null) {
                return cb.and(
                        cb.greaterThanOrEqualTo(root.get("pickupTime"), fromInclusive),
                        cb.lessThan(root.get("pickupTime"), toExclusive)
                );
            } else if (fromInclusive != null) {
                return cb.greaterThanOrEqualTo(root.get("pickupTime"), fromInclusive);
            } else if (toExclusive != null) {
                return cb.lessThan(root.get("pickupTime"), toExclusive);
            }
            return null;
        };
    }

    /**
     * Filters bookings created within an optional time range.
     */
    public static Specification<Booking> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"), from, to);
            } else if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            } else if (to != null) {
                return cb.lessThanOrEqualTo(root.get("createdAt"), to);
            }
            return null;
        };
    }

    /**
     * Optional case-insensitive search across booking code, license plate, vehicle brand/model, or renter name.
     */
    public static Specification<Booking> searchKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("bookingCode")), pattern),
                    cb.like(cb.lower(root.get("vehicle").get("licensePlate")), pattern),
                    cb.like(cb.lower(root.get("vehicle").get("brand")), pattern),
                    cb.like(cb.lower(root.get("vehicle").get("model")), pattern),
                    cb.like(cb.lower(root.get("renter").get("user").get("fullName")), pattern)
            );
        };
    }

    /**
     * Combines provider isolation with optional status filter for the provider queue.
     */
    public static Specification<Booking> forProviderQueue(Long providerId, BookingStatus status) {
        Specification<Booking> spec = Specification.where(hasProvider(providerId));
        if (status == null) {
            return spec;
        }
        if (status == BookingStatus.CONFIRMED) {
            return spec.and((root, query, cb) -> root.get("status").in(List.of(
                    BookingStatus.CONFIRMED,
                    BookingStatus.DRIVER_ASSIGNED,
                    BookingStatus.DRIVER_CONFIRMED,
                    BookingStatus.DRIVER_ARRIVED
            )));
        }
        if (status == BookingStatus.COMPLETED) {
            return spec.and((root, query, cb) -> root.get("status").in(List.of(
                    BookingStatus.COMPLETED,
                    BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION
            )));
        }
        return spec.and(hasStatus(status));
    }

    public static Specification<Booking> forDriverSchedule(
            Long driverId,
            TripView view,
            Collection<BookingStatus> statuses,
            Instant fromInclusive,
            Instant toExclusive,
            Instant startOfToday,
            Instant startOfTomorrow) {
        Specification<Booking> spec = Specification.where(hasDriver(driverId));

        if (fromInclusive != null && toExclusive != null) {
            spec = spec.and(pickupTimeBetween(fromInclusive, toExclusive));
            if (statuses != null && !statuses.isEmpty()) {
                spec = spec.and(hasStatusIn(statuses));
            }
            return spec;
        }

        if (view == TripView.TODAY) {
            if (statuses != null && !statuses.isEmpty()) {
                spec = spec.and(pickupTimeBetween(startOfToday, startOfTomorrow)).and(hasStatusIn(statuses));
            } else {
                spec = spec.and((root, query, cb) -> cb.or(
                        cb.and(
                                cb.greaterThanOrEqualTo(root.get("pickupTime"), startOfToday),
                                cb.lessThan(root.get("pickupTime"), startOfTomorrow),
                                root.get("status").in(List.of(
                                        BookingStatus.ACCEPTED,
                                        BookingStatus.CONFIRMED,
                                        BookingStatus.DRIVER_ASSIGNED,
                                        BookingStatus.DRIVER_CONFIRMED,
                                        BookingStatus.DRIVER_ARRIVED,
                                        BookingStatus.IN_PROGRESS
                                ))
                        ),
                        root.get("status").in(List.of(
                                BookingStatus.DRIVER_CONFIRMED,
                                BookingStatus.DRIVER_ARRIVED,
                                BookingStatus.IN_PROGRESS
                        ))
                ));
            }
        } else if (view == TripView.UPCOMING) {
            if (statuses != null && !statuses.isEmpty()) {
                spec = spec.and(pickupTimeBetween(startOfTomorrow, null)).and(hasStatusIn(statuses));
            } else {
                spec = spec.and((root, query, cb) -> cb.and(
                        cb.greaterThanOrEqualTo(root.get("pickupTime"), startOfTomorrow),
                        root.get("status").in(List.of(
                                BookingStatus.ACCEPTED,
                                BookingStatus.CONFIRMED,
                                BookingStatus.DRIVER_ASSIGNED,
                                BookingStatus.DRIVER_CONFIRMED,
                                BookingStatus.DRIVER_ARRIVED
                        ))
                ));
            }
        } else if (view == TripView.HISTORY) {
            if (statuses != null && !statuses.isEmpty()) {
                spec = spec.and(hasStatusIn(statuses));
            } else {
                spec = spec.and(hasStatusIn(List.of(
                        BookingStatus.COMPLETED,
                        BookingStatus.CANCELLED,
                        BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION
                )));
            }
        } else if (view == TripView.ACTIVE) {
            spec = spec.and((root, query, cb) -> root.get("status").in(List.of(
                    BookingStatus.DRIVER_CONFIRMED,
                    BookingStatus.DRIVER_ARRIVED,
                    BookingStatus.IN_PROGRESS
            )));
        } else {
            if (statuses != null && !statuses.isEmpty()) {
                spec = spec.and(hasStatusIn(statuses));
            }
        }

        return spec;
    }
}
