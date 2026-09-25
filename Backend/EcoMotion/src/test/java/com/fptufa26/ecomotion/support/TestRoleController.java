package com.fptufa26.ecomotion.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints that exist purely so the URL-based authorisation rules
 * ({@code /api/admin/**}, {@code /api/provider/**}, {@code /api/renter/**}) can be exercised
 * end-to-end with a real 200 response.
 *
 * <p>Lives in test sources, so it is never part of the shipped application.</p>
 */
@RestController
@RequestMapping("/api")
public class TestRoleController {

    @GetMapping("/admin/ping")
    public String adminPing() {
        return "admin";
    }

    @GetMapping("/provider/ping")
    public String providerPing() {
        return "provider";
    }

    @GetMapping("/renter/ping")
    public String renterPing() {
        return "renter";
    }
}
