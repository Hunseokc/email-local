package com.dorazibe02.imap;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}