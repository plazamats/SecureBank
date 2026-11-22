package za.co.securebank.exception;

import org.springframework.http.HttpStatus;

public class AccountLockedException extends ApiException {

    public AccountLockedException(String message) {
        super(HttpStatus.LOCKED, message);
    }

    public AccountLockedException() {
        this("Account is temporarily locked after too many failed login attempts");
    }
}
