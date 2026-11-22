package za.co.securebank.security.ratelimit;

/**
 * Integration point for rate limiting. The default implementation is in memory and per instance. To run several
 * application instances, provide another implementation (for example Redis or Bucket4j backed) as a Spring bean
 * of this type and remove the default bean declaration.
 */
public interface RateLimiter {

    /**
     * Attempts to consume one request for the given key.
     *
     * @return true when the request is allowed, false when the limit has been exceeded
     */
    boolean tryAcquire(String key);
}
