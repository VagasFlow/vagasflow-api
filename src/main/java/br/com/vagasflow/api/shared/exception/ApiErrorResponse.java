package br.com.vagasflow.api.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {

    public ApiErrorResponse(int status, String error, String message, String path) {
        this(status, error, message, path, Instant.now());
    }
}
