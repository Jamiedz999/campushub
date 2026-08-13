package com.campushub.shared;

import java.util.Map;

/** A form refusal with stable machine-readable field errors for the renderer. */
public class FormValidationException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public FormValidationException(ErrorCode code, Map<String, String> fieldErrors) {
        super("Registration form validation failed.");
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
