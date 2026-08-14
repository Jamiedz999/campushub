package com.campushub.event.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RegistrationFormDefinitionValidatorTest {

    @Test
    void acceptsEveryFieldTypeAtItsSupportedBoundaries() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField(fieldId(1), "Short", null, true, 1),
                new LongTextField(fieldId(2), "Long", "Help", false, 10_000),
                new SingleChoiceField(fieldId(3), "Single", null, false, List.of("One")),
                new MultipleChoiceField(fieldId(4), "Multiple", null, false, List.of("One", "Two")),
                new NumberField(
                        fieldId(5), "Number", null, false, BigDecimal.ONE, BigDecimal.ONE),
                new NumberField(
                        fieldId(6), "Maximum only", null, false, null, BigDecimal.TEN),
                new NumberField(
                        fieldId(7), "Minimum only", null, false, BigDecimal.ONE, null)));

        assertThatCode(() -> RegistrationFormDefinitionValidator.validate(form))
                .doesNotThrowAnyException();
    }

    @Test
    void nullCollectionsBecomeEmptyImmutableFormParts() {
        assertThatThrownBy(() -> new RegistrationForm(null).fields().add(
                        new ShortTextField("field", "Field", null, false, 20)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new SingleChoiceField("single", "Single", null, false, null)
                        .options()
                        .add("One"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new MultipleChoiceField("multiple", "Multiple", null, false, null)
                        .options()
                        .add("One"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMoreThanOneHundredFields() {
        List<FormField> fields = IntStream.rangeClosed(0, 100)
                .mapToObj(index -> (FormField) new ShortTextField(
                        fieldId(index + 1), "Field " + index, null, false, 20))
                .toList();

        assertInvalid(new RegistrationForm(fields), "fields", "Use no more than 100 fields.");
    }

    @Test
    void rejectsMissingAndOversizedCommonProperties() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField(null, "Missing id", null, false, 20),
                new ShortTextField("x".repeat(101), "Long id", null, false, 20),
                new ShortTextField(fieldId(1), " ", null, false, 20),
                new ShortTextField(fieldId(2), "x".repeat(201), null, false, 20),
                new ShortTextField(fieldId(3), "Help", "x".repeat(1_001), false, 20)));

        assertThatThrownBy(() -> RegistrationFormDefinitionValidator.validate(form))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> {
                    FormValidationException validation = (FormValidationException) exception;
                    org.assertj.core.api.Assertions.assertThat(validation.fieldErrors())
                            .containsKeys(
                                    "fields[0]",
                                    "x".repeat(101),
                                    fieldId(1),
                                    fieldId(2),
                                    fieldId(3));
                });
    }

    @Test
    void rejectsAUuidBecauseApiIdentifiersAreMongoObjectIdStrings() {
        String uuid = "00000000-0000-4000-8000-000000000001";

        assertInvalid(
                new RegistrationForm(List.of(
                        new ShortTextField(uuid, "Field", null, false, 20))),
                uuid,
                "A 24-character hexadecimal field id is required.");
    }

    @Test
    void rejectsDuplicateIdsAndTextLengthsOutsideTheSupportedRange() {
        RegistrationForm form = new RegistrationForm(List.of(
                new ShortTextField(fieldId(1), "First", null, false, 20),
                new LongTextField(fieldId(1), "Second", null, false, 20),
                new ShortTextField(fieldId(2), "Too short", null, false, 0),
                new LongTextField(fieldId(3), "Too long", null, false, 10_001)));

        assertThatThrownBy(() -> RegistrationFormDefinitionValidator.validate(form))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                                ((FormValidationException) exception).fieldErrors())
                        .containsEntry(fieldId(1), "Field ids must be unique.")
                        .containsEntry(fieldId(2), "Text length must be between 1 and 10,000.")
                        .containsEntry(fieldId(3), "Text length must be between 1 and 10,000."));
    }

    @Test
    void rejectsInvalidChoiceLists() {
        List<String> tooManyOptions = IntStream.rangeClosed(0, 100)
                .mapToObj(index -> "Option " + index)
                .toList();
        RegistrationForm form = new RegistrationForm(List.of(
                new SingleChoiceField(fieldId(1), "Empty", null, false, List.of()),
                new MultipleChoiceField(fieldId(2), "Too many", null, false, tooManyOptions),
                new SingleChoiceField(fieldId(3), "Blank", null, false, List.of(" ")),
                new MultipleChoiceField(fieldId(4), "Too long", null, false, List.of("x".repeat(201))),
                new SingleChoiceField(fieldId(5), "Duplicate", null, false, List.of("One", "One"))));

        assertThatThrownBy(() -> RegistrationFormDefinitionValidator.validate(form))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                                ((FormValidationException) exception).fieldErrors())
                        .containsKeys(fieldId(1), fieldId(2), fieldId(3), fieldId(4), fieldId(5)));
    }

    @Test
    void rejectsANumberWhoseMinimumExceedsItsMaximum() {
        RegistrationForm form = new RegistrationForm(List.of(new NumberField(
                fieldId(1), "Number", null, false, BigDecimal.TEN, BigDecimal.ONE)));

        assertInvalid(form, fieldId(1), "The minimum cannot exceed the maximum.");
    }

    private static void assertInvalid(RegistrationForm form, String fieldId, String message) {
        assertThatThrownBy(() -> RegistrationFormDefinitionValidator.validate(form))
                .isInstanceOf(FormValidationException.class)
                .satisfies(exception -> {
                    FormValidationException validation = (FormValidationException) exception;
                    org.assertj.core.api.Assertions.assertThat(validation.code())
                            .isEqualTo(ErrorCode.FORM_VALIDATION_FAILED);
                    org.assertj.core.api.Assertions.assertThat(validation.fieldErrors())
                            .containsEntry(fieldId, message);
                });
    }

    private static String fieldId(int value) {
        return "%024x".formatted(value);
    }
}
