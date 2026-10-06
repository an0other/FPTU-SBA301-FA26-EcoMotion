package com.fptufa26.ecomotion.auth.exception;

/**
 * Raised when a PENDING (not yet activated) account tries to log in. Distinct from
 * {@link AccountInactiveException} (LOCKED) so the client can redirect the user to the activation
 * page instead of just showing a generic error.
 */
public class AccountPendingException extends RuntimeException {

    public static final String MESSAGE = "Account is pending activation";

    public AccountPendingException() {
        super(MESSAGE);
    }
}
