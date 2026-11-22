package za.co.securebank.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public InsufficientFundsException() {
        this("Insufficient funds");
    }
}
