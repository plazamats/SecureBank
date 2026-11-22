package za.co.securebank.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all expected, client facing errors. The status is mapped to the HTTP response by the
 * global exception handler.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
