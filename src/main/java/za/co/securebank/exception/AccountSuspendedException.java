package za.co.securebank.exception;

import org.springframework.http.HttpStatus;

public class AccountSuspendedException extends ApiException {

    public AccountSuspendedException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }

    public AccountSuspendedException() {
        this("This account has been suspended");
    }
}
