package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.finance.config.VietQrProperties;
import com.fptufa26.ecomotion.finance.service.VietQrTokenService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class VietQrTokenServiceImpl implements VietQrTokenService {
    private final RestClient client;
    private final VietQrProperties properties;
    private final Clock clock;
    private String token;
    private Instant refreshAt = Instant.MIN;

    public VietQrTokenServiceImpl(@Qualifier("vietQrRestClient") RestClient client,
                                  VietQrProperties properties, Clock clock) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public synchronized String getToken() {
        if (!properties.isEnabled() || properties.getUsername().isBlank() || properties.getPassword().isBlank())
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "VietQR chưa được cấu hình");
        if (token != null && refreshAt.isAfter(clock.instant())) return token;
        try {
            Map<?, ?> result = client.post().uri("/vqr/api/token_generate")
                    .headers(h -> h.setBasicAuth(properties.getUsername(), properties.getPassword()))
                    .contentType(MediaType.APPLICATION_JSON).retrieve().body(Map.class);
            if (result == null || !(result.get("access_token") instanceof String value) || value.isBlank()
                    || !"Bearer".equals(result.get("token_type"))) throw new IllegalStateException();
            long seconds = Long.parseLong(result.get("expires_in").toString());
            if (seconds <= 0) throw new IllegalStateException();
            token = value;
            refreshAt = clock.instant().plusSeconds(Math.max(0, seconds - properties.getTokenSkew().toSeconds()));
            return token;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Không thể lấy token VietQR; vui lòng thử lại");
        }
    }
}
