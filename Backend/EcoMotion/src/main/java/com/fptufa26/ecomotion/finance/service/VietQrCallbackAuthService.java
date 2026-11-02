package com.fptufa26.ecomotion.finance.service;

import java.util.Map;

public interface VietQrCallbackAuthService {
    Map<String, Object> issueToken(String authorization);
    void authenticate(String authorization);
}
