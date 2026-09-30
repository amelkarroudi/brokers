package com.brokers.api.common;

import org.springframework.http.HttpStatus;

/**
 * Business error with a stable machine readable {@code code} that clients can switch on.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", resource + " not found");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
