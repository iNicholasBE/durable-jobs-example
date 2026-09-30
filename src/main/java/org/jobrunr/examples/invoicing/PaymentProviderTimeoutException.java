package org.jobrunr.examples.invoicing;

public class PaymentProviderTimeoutException extends RuntimeException {

    public PaymentProviderTimeoutException(String message) {
        super(message);
    }
}
