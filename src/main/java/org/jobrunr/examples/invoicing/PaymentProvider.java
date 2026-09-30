package org.jobrunr.examples.invoicing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in for an external payment provider.
 * <p>
 * Flip {@link #setOutage(boolean)} to make every charge time out after two seconds, which is the
 * incident the demo is built around. The provider also deduplicates on the idempotency key, the
 * way a real one does: charging twice with the same key returns the first charge.
 */
@Component
public class PaymentProvider {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AtomicBoolean outage = new AtomicBoolean(false);
    private final AtomicInteger attempts = new AtomicInteger();
    private final Map<String, String> chargesByIdempotencyKey = new ConcurrentHashMap<>();

    public String charge(String customerId, String idempotencyKey) throws InterruptedException {
        attempts.incrementAndGet();
        if (outage.get()) {
            Thread.sleep(2000); // we wait for the provider until our HTTP client gives up
            throw new PaymentProviderTimeoutException("Payment provider did not answer within 2s for " + customerId);
        }
        Thread.sleep(ThreadLocalRandom.current().nextLong(300, 700));
        String chargeId = chargesByIdempotencyKey.computeIfAbsent(idempotencyKey,
                key -> "ch_" + UUID.nameUUIDFromBytes(key.getBytes()).toString().substring(0, 8));
        logger.info("Charged {} (idempotency key {}): {}", customerId, idempotencyKey, chargeId);
        return chargeId;
    }

    public void setOutage(boolean enabled) {
        outage.set(enabled);
        logger.warn("Payment provider outage simulation is now {}", enabled ? "ON" : "OFF");
    }

    public boolean isOutage() {
        return outage.get();
    }

    /** How many charges actually went through, deduplication applied. */
    public int chargeCount() {
        return chargesByIdempotencyKey.size();
    }

    /** How many times the provider was called, including the calls that timed out. */
    public int attemptCount() {
        return attempts.get();
    }
}
