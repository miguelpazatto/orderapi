package com.miguelpazatto.orderapi.core.exceptions;

public class ExternalIntegrationException extends RuntimeException {
    public ExternalIntegrationException(String message) {
        super(message);
    }
}