package com.campushub.event.internal;

import com.campushub.event.EventModule.FormField;
import com.campushub.event.EventModule.LongTextField;
import com.campushub.event.EventModule.MultipleChoiceField;
import com.campushub.event.EventModule.NumberField;
import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.event.EventModule.ShortTextField;
import com.campushub.event.EventModule.SingleChoiceField;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.FormValidationException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;

final class RegistrationFormDefinitionValidator {

    private static final int MAX_FIELDS = 100;
    private static final int MAX_LABEL_LENGTH = 200;
    private static final int MAX_HELP_TEXT_LENGTH = 1_000;
    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final int MAX_OPTIONS = 100;
    private static final int MAX_OPTION_LENGTH = 200;

    private RegistrationFormDefinitionValidator() {}

    static void validate(RegistrationForm form) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (form.fields().size() > MAX_FIELDS) {
            errors.put("fields", "Use no more than 100 fields.");
        }

        Set<String> fieldIds = new HashSet<>();
        for (int index = 0; index < form.fields().size(); index++) {
            FormField field = form.fields().get(index);
            String errorKey = errorKey(field, index);
            if (isBlank(field.fieldId()) || !ObjectId.isValid(field.fieldId())) {
                errors.put(errorKey, "A 24-character hexadecimal field id is required.");
            } else if (!fieldIds.add(field.fieldId())) {
                errors.put(errorKey, "Field ids must be unique.");
            }
            if (isBlank(field.label()) || field.label().length() > MAX_LABEL_LENGTH) {
                errors.put(errorKey, "A label between 1 and 200 characters is required.");
            }
            if (field.helpText() != null && field.helpText().length() > MAX_HELP_TEXT_LENGTH) {
                errors.put(errorKey, "Help text must use 1,000 characters or fewer.");
            }

            switch (field) {
                case ShortTextField text -> validateText(errorKey, text.maxLength(), errors);
                case LongTextField text -> validateText(errorKey, text.maxLength(), errors);
                case SingleChoiceField choice -> validateOptions(errorKey, choice.options(), errors);
                case MultipleChoiceField choice -> validateOptions(errorKey, choice.options(), errors);
                case NumberField number -> {
                    if (number.minimum() != null
                            && number.maximum() != null
                            && number.minimum().compareTo(number.maximum()) > 0) {
                        errors.put(errorKey, "The minimum cannot exceed the maximum.");
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new FormValidationException(ErrorCode.FORM_VALIDATION_FAILED, errors);
        }
    }

    private static void validateText(String errorKey, int maxLength, Map<String, String> errors) {
        if (maxLength < 1 || maxLength > MAX_TEXT_LENGTH) {
            errors.put(errorKey, "Text length must be between 1 and 10,000.");
        }
    }

    private static void validateOptions(
            String errorKey, List<String> options, Map<String, String> errors) {
        Set<String> unique = new HashSet<>();
        if (options.isEmpty() || options.size() > MAX_OPTIONS) {
            errors.put(errorKey, "Use between 1 and 100 options.");
            return;
        }
        for (String option : options) {
            if (isBlank(option) || option.length() > MAX_OPTION_LENGTH || !unique.add(option)) {
                errors.put(errorKey, "Options must be unique and use between 1 and 200 characters.");
                return;
            }
        }
    }

    private static String errorKey(FormField field, int index) {
        return isBlank(field.fieldId()) ? "fields[" + index + "]" : field.fieldId();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
