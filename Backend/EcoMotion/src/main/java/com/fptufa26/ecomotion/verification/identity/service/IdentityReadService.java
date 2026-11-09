package com.fptufa26.ecomotion.verification.identity.service;

import com.fptufa26.ecomotion.verification.identity.dto.response.IdentityConstraintsResponse;
import java.util.Map;

public interface IdentityReadService {
    IdentityConstraintsResponse constraints();
    com.fptufa26.ecomotion.verification.identity.dto.response.IdentityStatusResponse status(Long userId);
}
