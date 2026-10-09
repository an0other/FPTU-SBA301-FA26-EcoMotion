package com.fptufa26.ecomotion.auth.otp;

public interface OtpSender {

    void send(String email, String phone, String code);
}
