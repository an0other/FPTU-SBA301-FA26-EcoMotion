package com.fptufa26.ecomotion.auth.captcha;

public interface CaptchaVerifier {

    void verify(String token, String action, String remoteIp);
}
