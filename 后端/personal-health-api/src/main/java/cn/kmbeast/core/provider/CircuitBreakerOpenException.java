package cn.kmbeast.core.provider;

/**
 * 熔断器打开异常
 */
public class CircuitBreakerOpenException extends RuntimeException {
    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
