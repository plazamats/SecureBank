package za.co.securebank.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import za.co.securebank.dto.response.ErrorResponse;

/** Throttles the unauthenticated authentication endpoints per client address (brute force protection). */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PROTECTED_PREFIX = "/api/v1/auth/";

    private final RateLimiter rateLimiter;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, boolean enabled, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.enabled = enabled;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (rateLimiter.tryAcquire(request.getRemoteAddr())) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.",
                        request.getRequestURI()));
    }
}
