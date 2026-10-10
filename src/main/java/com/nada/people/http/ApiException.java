package com.nada.people.http;

/** An error that maps directly to an HTTP status and an {@code {"error": "..."}} body. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public int status() {
        return status;
    }
}
