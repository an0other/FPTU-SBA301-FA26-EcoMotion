package com.fptufa26.ecomotion.vehicle.dto.response;

import java.math.BigDecimal;

public record AddressSearchResult(String address, BigDecimal latitude, BigDecimal longitude) {
}
