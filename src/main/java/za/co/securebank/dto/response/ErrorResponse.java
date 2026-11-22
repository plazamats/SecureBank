package za.co.securebank.dto.response;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Uniform error body returned by every failing request. It never contains stack traces or internal details.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors) {

    public record FieldErrorDetail(String field, String message) {
    }

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, null);
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
