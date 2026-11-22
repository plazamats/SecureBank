package za.co.securebank.security.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Fixed window limiter. Thread safe, intended for single instance deployments and development. */
public class InMemoryRateLimiter implements RateLimiter {

    private static final int EVICTION_THRESHOLD = 10_000;

    private record Window(long startMillis, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;

    public InMemoryRateLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, System::currentTimeMillis);
    }

    public InMemoryRateLimiter(int limit, long windowMillis, LongSupplier clock) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        Window window = windows.compute(key, (k, current) ->
                (current == null || now - current.startMillis() >= windowMillis)
                        ? new Window(now, new AtomicInteger(0))
                        : current);
        boolean allowed = window.count().incrementAndGet() <= limit;
        if (windows.size() > EVICTION_THRESHOLD) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startMillis() >= windowMillis);
        }
        return allowed;
    }
}
