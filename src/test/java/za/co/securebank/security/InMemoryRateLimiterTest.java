package za.co.securebank.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import za.co.securebank.security.ratelimit.InMemoryRateLimiter;

class InMemoryRateLimiterTest {

    @Test
    void allowsRequestsUpToTheLimitThenBlocks() {
        AtomicLong clock = new AtomicLong(0);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(3, 60_000, clock::get);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));
    }

    @Test
    void limitsAreTrackedPerKey() {
        AtomicLong clock = new AtomicLong(0);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(1, 60_000, clock::get);

        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.2"));
    }

    @Test
    void windowResetsAfterItExpires() {
        AtomicLong clock = new AtomicLong(0);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(1, 60_000, clock::get);

        assertTrue(limiter.tryAcquire("k"));
        assertFalse(limiter.tryAcquire("k"));
        clock.set(60_001);
        assertTrue(limiter.tryAcquire("k"));
    }
}
