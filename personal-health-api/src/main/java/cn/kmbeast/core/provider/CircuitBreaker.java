package cn.kmbeast.core.provider;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SEC-09: lightweight count-based circuit breaker (no external deps).
 *
 * <p>States: CLOSED (normal) -> OPEN (tripped) -> HALF_OPEN (probe) -> CLOSED/OPEN.
 * <ul>
 *   <li>CLOSED: every call counted; if failureRate exceeds {@code threshold} within
 *       the rolling {@code windowMs}, trips to OPEN.</li>
 *   <li>OPEN: all calls fail fast for {@code openTimeoutMs}, then HALF_OPEN.</li>
 *   <li>HALF_OPEN: a single probe call decides: success -> CLOSED, failure -> OPEN again.</li>
 * </ul>
 *
 * <p>Thread-safe. Used by {@link LLMProvider} implementations to guard upstream LLM calls.
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int thresholdPercent;   // failure rate % that trips the breaker
    private final long windowMs;          // rolling window
    private final long openTimeoutMs;     // how long to stay OPEN
    private final int minCallsToTrip;     // min calls before tripping is considered

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger failure = new AtomicInteger();
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong openedAt = new AtomicLong(0);
    private final AtomicInteger probeInFlight = new AtomicInteger(0);

    public CircuitBreaker(String name, int thresholdPercent, long windowMs, long openTimeoutMs, int minCallsToTrip) {
        this.name = name;
        this.thresholdPercent = Math.max(1, Math.min(100, thresholdPercent));
        this.windowMs = Math.max(1_000, windowMs);
        this.openTimeoutMs = Math.max(1_000, openTimeoutMs);
        this.minCallsToTrip = Math.max(1, minCallsToTrip);
    }

    public static CircuitBreaker defaultFor(String name) {
        return new CircuitBreaker(name, 50, 60_000, 30_000, 5);
    }

    public State getState() {
        return state.get();
    }

    public String getName() {
        return name;
    }

    /** Call before executing the guarded operation. Throws if the breaker is OPEN. */
    public void beforeCall() {
        if (state.get() == State.OPEN && System.currentTimeMillis() - openedAt.get() >= openTimeoutMs) {
            // timeout elapsed - allow one probe
            if (probeInFlight.compareAndSet(0, 1)) {
                state.set(State.HALF_OPEN);
            }
        }
        if (state.get() == State.OPEN) {
            throw new IllegalStateException("circuit open for [" + name + "], fast-fail");
        }
    }

    /** Call after a successful operation. */
    public void onSuccess() {
        if (state.get() == State.HALF_OPEN) {
            // probe succeeded - close the circuit
            reset();
            return;
        }
        success.incrementAndGet();
        rollWindowIfNeeded();
    }

    /** Call after a failed operation. */
    public void onFailure() {
        if (state.get() == State.HALF_OPEN) {
            // probe failed - open again
            open();
            probeInFlight.set(0);
            return;
        }
        failure.incrementAndGet();
        rollWindowIfNeeded();
        maybeTrip();
    }

    private void maybeTrip() {
        int s = success.get();
        int f = failure.get();
        int total = s + f;
        if (total < minCallsToTrip) {
            return;
        }
        int rate = (int) Math.round(100.0 * f / total);
        if (rate >= thresholdPercent) {
            open();
        }
    }

    private void open() {
        state.set(State.OPEN);
        openedAt.set(System.currentTimeMillis());
    }

    private void rollWindowIfNeeded() {
        long now = System.currentTimeMillis();
        long start = windowStart.get();
        if (now - start > windowMs) {
            if (windowStart.compareAndSet(start, now)) {
                success.set(0);
                failure.set(0);
            }
        }
    }

    private void reset() {
        success.set(0);
        failure.set(0);
        windowStart.set(System.currentTimeMillis());
        probeInFlight.set(0);
        state.set(State.CLOSED);
    }
}
