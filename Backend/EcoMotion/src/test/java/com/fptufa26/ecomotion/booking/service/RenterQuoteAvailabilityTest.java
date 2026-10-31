package com.fptufa26.ecomotion.booking.service;
import com.fptufa26.ecomotion.booking.dto.request.RentalQuoteRequest;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse;
import com.fptufa26.ecomotion.booking.dto.response.RentalQuoteResponse.Option;
import com.fptufa26.ecomotion.booking.entity.Booking;
import com.fptufa26.ecomotion.booking.repository.BookingRepository;
import com.fptufa26.ecomotion.booking.service.implement.BookingServiceImpl;
import com.fptufa26.ecomotion.user.entity.Renter;
import com.fptufa26.ecomotion.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class RenterQuoteAvailabilityTest {
    @Mock BookingRepository bookings;
    @Mock UserService users;
    @Mock RentalPricingService pricing;
    @InjectMocks BookingServiceImpl service;
    final Instant start=Instant.parse("2030-11-01T00:00:00Z");
    final Instant end=start.plusSeconds(5*3600);
    private void setup(Instant busyStart, Instant busyEnd) {
        Renter renter=new Renter(); renter.setId(7L);
        when(users.requireRenterByUserId(12L)).thenReturn(renter);
        Booking busy=new Booking(); busy.setPickupTime(busyStart); busy.setPlannedEndAt(busyEnd);
        when(bookings.findByRenter_IdAndStatusIn(eq(7L),any())).thenReturn(List.of(busy));
        when(pricing.quote(any())).thenReturn(new RentalQuoteResponse(true,null,start,end,List.of(),List.of(
                new Option(end,5,1L,BigDecimal.valueOf(500),BigDecimal.ZERO,List.of()),
                new Option(start.plusSeconds(24*3600),24,2L,BigDecimal.valueOf(1000),BigDecimal.ZERO,List.of()))));
    }
    @Test void reportsAnExistingRentalAtQuoteTime() {
        setup(start.plusSeconds(3600),end.plusSeconds(3600));
        assertThatThrownBy(() -> service.quoteRental(12L,new RentalQuoteRequest(1L,start,end)))
                .hasMessageContaining("Bạn đã có đơn thuê trùng");
    }
    @Test void excludesOnlyTheRoundedChoiceIfItOverlapsTheRentersNextBooking() {
        setup(end,end.plusSeconds(3600));
        var quote=service.quoteRental(12L,new RentalQuoteRequest(1L,start,end));
        assertThat(quote.options()).extracting(Option::coveredHours).containsExactly(5);
        assertThat(quote.reason()).contains("làm tròn trùng lịch");
    }
    @Test void adjacentBookingsDoNotConflictAndTheAuthenticatedRenterIsUsed() {
        setup(start.minusSeconds(3600),start);
        var quote=service.quoteRental(12L,new RentalQuoteRequest(1L,start,end));
        assertThat(quote.options()).hasSize(2);
        assertThat(quote.reason()).isNull();
        verify(users).requireRenterByUserId(12L);
    }
}
