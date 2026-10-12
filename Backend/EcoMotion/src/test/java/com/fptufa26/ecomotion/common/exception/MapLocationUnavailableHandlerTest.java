package com.fptufa26.ecomotion.common.exception;

import com.fptufa26.ecomotion.vehicle.exception.MapLocationUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapLocationUnavailableHandlerTest {

    @Test
    void connectionFailureIsReportedAsServiceUnavailable() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/vehicles/map/search");

        var response = new GlobalExceptionHandler().handleMapLocationUnavailable(
                new MapLocationUnavailableException("Address lookup unavailable"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(503);
        assertThat(response.getBody().path()).isEqualTo("/api/vehicles/map/search");
    }
}
