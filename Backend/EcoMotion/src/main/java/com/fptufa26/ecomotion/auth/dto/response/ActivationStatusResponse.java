package com.fptufa26.ecomotion.auth.dto.response;

/**
 * Current activation progress, so the activation wizard can jump straight to the first step that is
 * still outstanding after a reload or a resume.
 *
 * <p>Every flag is derived from persisted state — {@code users.phone}, {@code renter.cccd_no} and
 * {@code users.status} — so nothing is lost when the backend restarts. The face step has no flag of
 * its own: it is the last step and activates the account in the same request, so "face matched" is
 * simply {@code status == ACTIVE}.</p>
 */
public record ActivationStatusResponse(
        String email,
        String role,
        String status,
        String ekycStatus,
        boolean phoneVerified,
        boolean cccdScanned,
        String fullName,
        String cccdNo,
        String dateOfBirth,
        String gender,
        String address
) {
}
