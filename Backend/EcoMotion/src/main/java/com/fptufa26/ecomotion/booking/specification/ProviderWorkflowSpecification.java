package com.fptufa26.ecomotion.booking.specification;

import com.fptufa26.ecomotion.booking.dto.request.BookingQueueFilter;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.entity.PaymentTransaction;
import com.fptufa26.ecomotion.booking.enums.BookingStatus;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.fptufa26.ecomotion.finance.entity.PaymentRequest;
import com.fptufa26.ecomotion.finance.enums.PaymentRequestStatus;

public final class ProviderWorkflowSpecification {
    private ProviderWorkflowSpecification() {}
    private static final Map<String, List<BookingStatus>> ACTIONS = Map.of(
            "APPROVAL", List.of(BookingStatus.PENDING),
            "PAYMENT", List.of(BookingStatus.ACCEPTED, BookingStatus.AWAITING_DEPOSIT),
            "ASSIGN_DRIVER", List.of(BookingStatus.CONFIRMED),
            "DRIVER_RESPONSE", List.of(BookingStatus.DRIVER_ASSIGNED),
            "UPCOMING", List.of(BookingStatus.DRIVER_CONFIRMED, BookingStatus.DRIVER_ARRIVED),
            "ACTIVE", List.of(BookingStatus.IN_PROGRESS, BookingStatus.AWAITING_START_CONFIRMATION, BookingStatus.AWAITING_END_CONFIRMATION),
            "SETTLEMENT", List.of(BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION, BookingStatus.AWAITING_FINAL_PAYMENT),
            "COMPLETED", List.of(BookingStatus.COMPLETED),
            "CLOSED", List.of(BookingStatus.CANCELLED, BookingStatus.REJECTED));

    public static final String PAYMENT_CONFIRM = "PAYMENT_CONFIRM";

    public static jakarta.persistence.criteria.Subquery<Long> awaitingPaymentConfirmation(
            jakarta.persistence.criteria.Root<Booking> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        var sub = query.subquery(Long.class);
        var request = sub.from(PaymentRequest.class);
        sub.select(request.get("id")).where(cb.equal(request.get("booking"), root),
                cb.equal(request.get("status"), PaymentRequestStatus.PROOF_SUBMITTED));
        return sub;
    }

    public static Specification<Booking> filters(BookingQueueFilter f) {
        LocalDate from = date(f.dateFrom()); LocalDate to = date(f.dateTo());
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("Invalid date range");
        String action = f.action();
        if (action != null && !action.equals("ALL") && !action.equals("NEEDS_ATTENTION") && !action.equals(PAYMENT_CONFIRM)
                && !ACTIONS.containsKey(action))
            throw new IllegalArgumentException("Invalid workflow action");
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Specification<Booking> spec = BookingSpecification.searchKeyword(f.search()).and(BookingSpecification.pickupTimeBetween(
                from == null ? null : from.atStartOfDay(zone).toInstant(), to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant()));
        return spec.and((root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (f.vehicle() != null && !f.vehicle().equals("ALL")) predicates.add(cb.equal(
                    cb.concat(cb.concat(root.get("vehicle").get("brand"), " "), root.get("vehicle").get("model")), f.vehicle()));
            if ("ASSIGNED".equals(f.driver())) predicates.add(cb.isNotNull(root.get("driver")));
            else if ("UNASSIGNED".equals(f.driver())) predicates.add(cb.isNull(root.get("driver")));
            else if ("PENDING_DRIVER".equals(f.driver())) predicates.add(cb.equal(root.get("status"), BookingStatus.DRIVER_ASSIGNED));
            else if (f.driver() != null && !f.driver().equals("ALL")) throw new IllegalArgumentException("Invalid driver filter");
            if (PAYMENT_CONFIRM.equals(action)) {
                predicates.add(cb.exists(awaitingPaymentConfirmation(root, query, cb)));
            } else if (action != null && !action.equals("ALL")) {
                var trip = query.subquery(Long.class); var previous = trip.from(Booking.class);
                trip.select(previous.get("id")).where(cb.equal(previous.get("vehicle"), root.get("vehicle")),
                        cb.notEqual(previous.get("id"), root.get("id")), cb.lessThan(previous.get("pickupTime"), root.get("pickupTime")),
                        cb.isNull(previous.get("actualEndAt")), previous.get("status").in(List.of(BookingStatus.IN_PROGRESS,
                                BookingStatus.AWAITING_END_CONFIRMATION, BookingStatus.AWAITING_EXTRA_CHARGE_CONFIRMATION, BookingStatus.AWAITING_FINAL_PAYMENT)));
                var payment = query.subquery(Long.class); var transaction = payment.from(PaymentTransaction.class);
                payment.select(transaction.get("id")).where(cb.equal(transaction.get("booking"), root),
                        cb.like(transaction.get("processingNote"), "RECONCILIATION_REQUIRED%"));
                var attention = cb.or(cb.exists(trip), cb.exists(payment), cb.equal(root.get("status"), BookingStatus.CANCELLATION_REVIEW),
                        cb.isTrue(cb.coalesce(root.get("driverReplacementRequired"), false)));
                predicates.add(action.equals("NEEDS_ATTENTION") ? attention : cb.and(cb.not(attention), root.get("status").in(ACTIONS.get(action))));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        });
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (java.time.format.DateTimeParseException ex) { throw new IllegalArgumentException("Invalid date"); }
    }
}
