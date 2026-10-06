package com.fptufa26.ecomotion.auth.ekyc;

/**
 * Identity verification used on the activation page. Two providers are wired:
 * <ul>
 *     <li>{@code mock} (default) — no network, always succeeds, returns sample data.</li>
 *     <li>{@code fptai} — calls the FPT.AI eKYC sandbox (CCCD OCR + face match).</li>
 * </ul>
 */
public interface EkycService {

    /**
     * Reads the CCCD front image and returns the identity fields printed on it. These fields are
     * used to auto-fill the renter/provider/driver profile, so the record carries everything the
     * profile tables need.
     */
    OcrResult ocrIdCard(byte[] image, String filename);

    /** Compares the portrait photo on the CCCD with the uploaded selfie. */
    FaceMatchResult faceMatch(byte[] idPhoto, String idFilename, byte[] portrait, String portraitFilename);

    record OcrResult(
            String fullName,
            String idNumber,
            String dateOfBirth,
            String gender,
            String address
    ) {
    }

    record FaceMatchResult(boolean match, double similarity) {
    }
}
