import type {
  RegistrationAnswer,
  RegistrationAnswers,
  RegistrationFieldErrors,
  RegistrationForm,
} from "../../types/registrationForm";

function isEmpty(answer: RegistrationAnswer | undefined) {
  return answer === undefined || answer === "" || (Array.isArray(answer) && answer.length === 0);
}

export function validateRegistrationAnswers(
  form: RegistrationForm,
  answers: RegistrationAnswers,
): RegistrationFieldErrors {
  const errors: RegistrationFieldErrors = {};
  for (const field of form.fields) {
    const answer = answers[field.fieldId];
    if (isEmpty(answer)) {
      if (field.required) {
        errors[field.fieldId] = "Required.";
      }
      continue;
    }
    switch (field.type) {
      case "SHORT_TEXT":
      case "LONG_TEXT":
        if (typeof answer !== "string") {
          errors[field.fieldId] = "Enter text.";
        } else if (answer.length > field.maxLength) {
          errors[field.fieldId] = `Use ${field.maxLength} characters or fewer.`;
        }
        break;
      case "SINGLE_CHOICE":
        if (typeof answer !== "string" || !field.options.includes(answer)) {
          errors[field.fieldId] = "Choose one of the available options.";
        }
        break;
      case "MULTIPLE_CHOICE":
        if (!Array.isArray(answer) || answer.some((option) => !field.options.includes(option))) {
          errors[field.fieldId] = "Choose only available options.";
        }
        break;
      case "NUMBER":
        if (typeof answer !== "number" || !Number.isFinite(answer)) {
          errors[field.fieldId] = "Enter a number.";
        } else if (field.minimum !== null && answer < field.minimum) {
          errors[field.fieldId] = `Enter a number no less than ${field.minimum}.`;
        } else if (field.maximum !== null && answer > field.maximum) {
          errors[field.fieldId] = `Enter a number no greater than ${field.maximum}.`;
        }
        break;
    }
  }
  return errors;
}
