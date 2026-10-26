package com.fptufa26.ecomotion.finance.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayIpnResponse(
        @JsonProperty("RspCode")
        String rspCode,

        @JsonProperty("Message")
        String message
) {
}
