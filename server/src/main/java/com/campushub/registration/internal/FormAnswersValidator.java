package com.campushub.registration.internal;

import com.campushub.event.EventModule.FormField;
import com.campushub.event.EventModule.LongTextField;
import com.campushub.event.EventModule.MultipleChoiceField;
import com.campushub.event.EventModule.NumberField;
import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.event.EventModule.ShortTextField;
import com.campushub.event.EventModule.SingleChoiceField;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.FormValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FormAnswersValidator {

    private FormAnswersValidator() {}

    static Map<String, Object> validate(RegistrationForm form, Map<String, Object> submitted) {
        Map<String, Object> answers = submitted == null ? Map.of() : submitted;
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> undefinedOptions = new LinkedHashMap<>();
        Map<String, Object> normalized = new LinkedHashMap<>();
        Set<String> fieldIds = new HashSet<>();

        for (FormField field : form.fields()) {
            fieldIds.add(field.fieldId());
            validateField(field, answers.get(field.fieldId()), normalized, errors, undefinedOptions);
        }
        for (String submittedFieldId : answers.keySet()) {
            if (!fieldIds.contains(submittedFieldId)) {
                errors.put(submittedFieldId, "This field is not defined.");
            }
        }
        if (!undefinedOptions.isEmpty()) {
            throw new FormValidationException(ErrorCode.UNDEFINED_OPTION, undefinedOptions);
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(ErrorCode.FORM_VALIDATION_FAILED, errors);
        }
        return Map.copyOf(normalized);
    }

    private static void validateField(
            FormField field,
            Object value,
            Map<String, Object> normalized,
            Map<String, String> errors,
            Map<String, String> undefinedOptions) {
        if (isEmpty(value)) {
            if (field.required()) {
                errors.put(field.fieldId(), "Required.");
            }
            return;
        }
        switch (field) {
            case ShortTextField text -> validateText(text.fieldId(), value, text.maxLength(), normalized, errors);
            case LongTextField text -> validateText(text.fieldId(), value, text.maxLength(), normalized, errors);
            case SingleChoiceField choice ->
                validateSingleChoice(choice, value, normalized, errors, undefinedOptions);
            case MultipleChoiceField choice ->
                validateMultipleChoice(choice, value, normalized, errors, undefinedOptions);
            case NumberField number -> validateNumber(number, value, normalized, errors);
        }
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || value instanceof String text && text.isBlank()
                || value instanceof List<?> values && values.isEmpty();
    }

    private static void validateText(
            String fieldId,
            Object value,
            int maxLength,
            Map<String, Object> normalized,
            Map<String, String> errors) {
        if (!(value instanceof String text)) {
            errors.put(fieldId, "Enter text.");
        } else if (text.length() > maxLength) {
            errors.put(fieldId, "Use " + maxLength + " characters or fewer.");
        } else {
            normalized.put(fieldId, text);
        }
    }

    private static void validateSingleChoice(
            SingleChoiceField field,
            Object value,
            Map<String, Object> normalized,
            Map<String, String> errors,
            Map<String, String> undefinedOptions) {
        if (!(value instanceof String option)) {
            errors.put(field.fieldId(), "Choose one option.");
        } else if (!field.options().contains(option)) {
            undefinedOptions.put(field.fieldId(), "The option '" + option + "' is not defined.");
        } else {
            normalized.put(field.fieldId(), option);
        }
    }

    private static void validateMultipleChoice(
            MultipleChoiceField field,
            Object value,
            Map<String, Object> normalized,
            Map<String, String> errors,
            Map<String, String> undefinedOptions) {
        if (!(value instanceof List<?> rawOptions)
                || rawOptions.stream().anyMatch(option -> !(option instanceof String))) {
            errors.put(field.fieldId(), "Choose one or more options.");
            return;
        }
        List<String> options = new ArrayList<>();
        for (Object rawOption : rawOptions) {
            String option = (String) rawOption;
            if (!field.options().contains(option)) {
                undefinedOptions.put(field.fieldId(), "The option '" + option + "' is not defined.");
            } else if (!options.contains(option)) {
                options.add(option);
            }
        }
        if (!undefinedOptions.containsKey(field.fieldId())) {
            normalized.put(field.fieldId(), List.copyOf(options));
        }
    }

    private static void validateNumber(
            NumberField field,
            Object value,
            Map<String, Object> normalized,
            Map<String, String> errors) {
        if (!(value instanceof Number number)) {
            errors.put(field.fieldId(), "Enter a number.");
            return;
        }
        BigDecimal decimal = new BigDecimal(number.toString());
        if (field.minimum() != null && decimal.compareTo(field.minimum()) < 0) {
            errors.put(field.fieldId(), "Enter " + field.minimum().toPlainString() + " or more.");
        } else if (field.maximum() != null && decimal.compareTo(field.maximum()) > 0) {
            errors.put(field.fieldId(), "Enter " + field.maximum().toPlainString() + " or less.");
        } else {
            normalized.put(field.fieldId(), decimal);
        }
    }
}
