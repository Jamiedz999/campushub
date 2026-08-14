package com.campushub.registration.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campushub.event.EventModule.LongTextField;
import com.campushub.event.EventModule.MultipleChoiceField;
import com.campushub.event.EventModule.NumberField;
import com.campushub.event.EventModule.RegistrationForm;
import com.campushub.event.EventModule.ShortTextField;
import com.campushub.event.EventModule.SingleChoiceField;
import com.campushub.shared.ErrorCode;
import com.campushub.shared.FormValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormAnswersValidatorTest {

    @Test
    void acceptsAndNormalizesEveryAnswerType() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("short", "Short", null, true, 20),
                new LongTextField("long", "Long", null, true, 100),
                new SingleChoiceField("single", "Single", null, true, List.of("One", "Two")),
                new MultipleChoiceField("multiple", "Multiple", null, true, List.of("One", "Two")),
                new NumberField(
                        "number", "Number", null, true, BigDecimal.ONE, BigDecimal.TEN),
                new NumberField("unbounded", "Unbounded", null, true, null, null)));

        Map<String, Object> answers = FormAnswersValidator.validate(form, Map.of(
                "short", "Short answer",
                "long", "Long answer",
                "single", "One",
                "multiple", List.of("One", "One", "Two"),
                "number", 5,
                "unbounded", 12.5));

        assertThat(answers)
                .containsEntry("short", "Short answer")
                .containsEntry("long", "Long answer")
                .containsEntry("single", "One")
                .containsEntry("multiple", List.of("One", "Two"))
                .containsEntry("number", new BigDecimal("5"))
                .containsEntry("unbounded", new BigDecimal("12.5"));
    }

    @Test
    void treatsNullSubmittedAnswersAsAnEmptyForm() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("required", "Required", null, true, 20)));

        assertInvalid(
                form,
                null,
                ErrorCode.FORM_VALIDATION_FAILED,
                "required",
                "Required.");
    }

    @Test
    void omitsBlankOptionalAnswers() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("blank", "Blank", null, false, 20),
                new MultipleChoiceField("empty", "Empty", null, false, List.of("One")),
                new LongTextField("missing", "Missing", null, false, 100)));

        assertThat(FormAnswersValidator.validate(
                        form, Map.of("blank", " ", "empty", List.of())))
                .isEmpty();
    }

    @Test
    void reportsWrongRuntimeTypesPerField() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField("short", "Short", null, false, 20),
                new SingleChoiceField("single", "Single", null, false, List.of("One")),
                new MultipleChoiceField("multiple", "Multiple", null, false, List.of("One")),
                new NumberField("number", "Number", null, false, null, null)));

        assertThatThrownBy(() -> FormAnswersValidator.validate(form, Map.of(
                        "short", 1,
                        "single", List.of("One"),
                        "multiple", "One",
                        "number", "1")))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> assertThat(((FormValidationException) exception).fieldErrors())
                        .containsEntry("short", "Enter text.")
                        .containsEntry("single", "Choose one option.")
                        .containsEntry("multiple", "Choose one or more options.")
                        .containsEntry("number", "Enter a number."));
    }

    @Test
    void rejectsANonTextItemInsideAMultipleChoiceAnswer() {
        RegistrationForm form = new RegistrationForm(List.of(new MultipleChoiceField(
                "multiple", "Multiple", null, false, List.of("One"))));

        assertInvalid(
                form,
                Map.of("multiple", List.of("One", 2)),
                ErrorCode.FORM_VALIDATION_FAILED,
                "multiple",
                "Choose one or more options.");
    }

    @Test
    void enforcesBothNumberBounds() {
        RegistrationForm form = new RegistrationForm(List.of(new NumberField(
                "number", "Number", null, true, BigDecimal.ONE, BigDecimal.TEN)));

        assertInvalid(
                form,
                Map.of("number", 0),
                ErrorCode.FORM_VALIDATION_FAILED,
                "number",
                "Enter 1 or more.");
        assertInvalid(
                form,
                Map.of("number", 11),
                ErrorCode.FORM_VALIDATION_FAILED,
                "number",
                "Enter 10 or less.");
    }

    private static void assertInvalid(
            RegistrationForm form,
            Map<String, Object> answers,
            ErrorCode code,
            String fieldId,
            String message) {
        assertThatThrownBy(() -> FormAnswersValidator.validate(form, answers))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> {
                    FormValidationException validation = (FormValidationException) exception;
                    assertThat(validation.code()).isEqualTo(code);
                    assertThat(validation.fieldErrors()).containsEntry(fieldId, message);
                });
    }
}
