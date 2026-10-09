package com.fptufa26.ecomotion.verification.ekyc;

public interface EkycService {

    OcrResult ocrIdCard(byte[] image, String filename);

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
