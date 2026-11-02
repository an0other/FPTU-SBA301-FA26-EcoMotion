package com.fptufa26.ecomotion.finance.service.implement;

import com.fptufa26.ecomotion.finance.config.VietQrProperties;
import com.fptufa26.ecomotion.finance.service.VietQrCallbackAuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class VietQrCallbackAuthServiceImpl implements VietQrCallbackAuthService {
    private final VietQrProperties properties;
    private final Clock clock;

    private void configured() {
        if (!properties.isEnabled() || properties.getCallbackUsername().isBlank()
                || properties.getCallbackPassword().isBlank() || properties.getCallbackSecret().getBytes(StandardCharsets.UTF_8).length < 32)
            throw new ResponseStatusException(UNAUTHORIZED, "VietQR callback authentication unavailable");
    }

    @Override
    public Map<String, Object> issueToken(String authorization) {
        configured();
        String expected = "Basic " + Base64.getEncoder().encodeToString((properties.getCallbackUsername()
                + ":" + properties.getCallbackPassword()).getBytes(StandardCharsets.UTF_8));
        if (authorization == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(UNAUTHORIZED);
        var now = clock.instant();
        String token = Jwts.builder().issuer("ecomotion-vietqr-callback").subject("vietqr-transaction-sync")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(properties.getCallbackTokenTtl())))
                .signWith(Keys.hmacShaKeyFor(properties.getCallbackSecret().getBytes(StandardCharsets.UTF_8))).compact();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", properties.getCallbackTokenTtl().toSeconds());
    }

    @Override
    public void authenticate(String authorization) {
        configured();
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
            var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(properties.getCallbackSecret().getBytes(StandardCharsets.UTF_8)))
                    .requireIssuer("ecomotion-vietqr-callback").requireSubject("vietqr-transaction-sync")
                    .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(authorization.substring(7)).getPayload();
            if (claims.getExpiration() == null) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid VietQR callback token");
        }
    }
}
