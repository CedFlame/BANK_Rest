package com.example.bankcards.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Builder
public class ErrorResponse {
    private final OffsetDateTime timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;

    @JsonInclude(Include.NON_NULL)
    private final String path;

    @JsonInclude(Include.NON_NULL)
    private final Map<String, String> fields;
}
