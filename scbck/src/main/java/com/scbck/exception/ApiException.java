package com.scbck.exception;

import org.springframework.http.HttpStatus;

/**
 * Application-level failure that carries the HTTP status the client should
 * see. Thrown by services and translated to a JSON body by
 * {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** 400 - the request itself is wrong (duplicate value, missing id, ...). */
    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** 404 - the addressed record does not exist. */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    /** 403 - authenticated, but lacking the module privilege. */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    /** 409 - the request conflicts with existing state. */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
