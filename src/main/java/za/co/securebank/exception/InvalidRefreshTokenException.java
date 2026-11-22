package za.co.securebank.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }

    public InvalidRefreshTokenException() {
        this("Refresh token is invalid, expired or has been revoked");
    }
}
