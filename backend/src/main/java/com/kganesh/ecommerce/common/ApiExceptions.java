package com.kganesh.ecommerce.common;

import org.springframework.http.HttpStatus;

/** Exceptions that map to RFC 9457 problem responses in {@link ApiExceptionHandler}. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    public abstract static class ApiException extends RuntimeException {
        private final HttpStatus status;
        private final String code;

        protected ApiException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public HttpStatus status() {
            return status;
        }

        public String code() {
            return code;
        }
    }

    public static final class NotFound extends ApiException {
        public NotFound(String message) {
            super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
        }
    }

    public static final class Conflict extends ApiException {
        public Conflict(String code, String message) {
            super(HttpStatus.CONFLICT, code, message);
        }
    }

    public static final class BadRequest extends ApiException {
        public BadRequest(String code, String message) {
            super(HttpStatus.BAD_REQUEST, code, message);
        }
    }
}
