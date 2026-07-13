package com.onclick.domain.instagram.service;

public class InstagramProviderException extends RuntimeException {

    private final boolean retryable;
    private final boolean deliveryUncertain;

    public InstagramProviderException(String message, boolean retryable) {
        this(message, retryable, false, null);
    }

    public InstagramProviderException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, false, cause);
    }

    public InstagramProviderException(
            String message,
            boolean retryable,
            boolean deliveryUncertain,
            Throwable cause
    ) {
        super(message, cause);
        this.retryable = retryable;
        this.deliveryUncertain = deliveryUncertain;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isDeliveryUncertain() {
        return deliveryUncertain;
    }
}
