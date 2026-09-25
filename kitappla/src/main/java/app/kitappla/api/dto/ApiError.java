package app.kitappla.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String error,
        String message,
        String code
) {
    public ApiError(String error) {
        this(error, error, null);
    }

    public ApiError(String error, String code) {
        this(error, error, code);
    }
}
