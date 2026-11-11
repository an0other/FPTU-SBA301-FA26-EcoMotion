package com.fptufa26.ecomotion.verification.identity.service;

public interface IdentityFingerprintService {

    String normalizeCccdNumber(String cccdNumber);

    String fingerprint(String cccdNumber);

    int keyVersion();
}
