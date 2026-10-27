package com.fptufa26.ecomotion.location.dto.response;

import java.math.BigDecimal;

public record AddressSearchResult(String address, BigDecimal latitude, BigDecimal longitude) {
}
