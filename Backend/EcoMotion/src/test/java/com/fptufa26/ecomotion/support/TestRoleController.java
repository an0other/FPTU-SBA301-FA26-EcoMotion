package com.fptufa26.ecomotion.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
