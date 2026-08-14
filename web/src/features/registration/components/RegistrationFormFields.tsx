import type {
  MultipleChoiceField,
  NumberField,
  RegistrationAnswer,
  RegistrationAnswers,
  RegistrationFieldErrors,
  RegistrationForm,
  RegistrationFormField,
  SingleChoiceField,
} from "../../../types/registrationForm";

interface RegistrationFormFieldsProps {
  form: RegistrationForm;
  answers: RegistrationAnswers;
  fieldErrors?: RegistrationFieldErrors;
  disabled?: boolean;
  onAnswer: (fieldId: string, answer: RegistrationAnswer) => void;
}

/**
 * Every control below carries `required`, `aria-describedby` and `aria-invalid` so that a Student
 * reading the form one control at a time is told the same things a Student reading down the page is.
 *
 * `required` is an accessibility annotation here and not a validation mechanism: both forms that
 * render these fields set `noValidate`, so the browser's own bubble never appears and the server's
 * refusal — which is the only authority on whether an answer is acceptable — still reaches the field
 * it belongs to. Removing `noValidate` would swap one for the other, quietly.
 */
interface FieldControlProps {
  field: RegistrationFormField;
  answer: RegistrationAnswer | undefined;
  disabled: boolean;
  /** The help text and the validation message for this field, if either is on screen. */
  describedBy: string | undefined;
  invalid: boolean;
  onAnswer: (fieldId: string, answer: RegistrationAnswer) => void;
}

function textAnswer(answer: RegistrationAnswer | undefined) {
  return typeof answer === "string" ? answer : "";
}

function numberAnswer(answer: RegistrationAnswer | undefined) {
  return typeof answer === "number" ? answer : "";
}

function multipleAnswer(answer: RegistrationAnswer | undefined) {
  return Array.isArray(answer) ? answer : [];
}

function SingleChoiceControl({
  field,
  answer,
  disabled,
  describedBy,
  invalid,
  onAnswer,
}: FieldControlProps & { field: SingleChoiceField }) {
  return (
    <fieldset
      role="radiogroup"
      aria-labelledby={`${field.fieldId}-label`}
      aria-describedby={describedBy}
      aria-required={field.required}
      aria-invalid={invalid}
      className="flex flex-col gap-2"
    >
      <legend id={`${field.fieldId}-label`} className="font-medium">
        {field.label}
        {field.required && " *"}
      </legend>
      {field.options.map((option) => (
        <label key={option} className="flex items-center gap-2">
          <input
            type="radio"
            name={field.fieldId}
            value={option}
            checked={answer === option}
            disabled={disabled}
            onChange={() => onAnswer(field.fieldId, option)}
          />
          {option}
        </label>
      ))}
    </fieldset>
  );
}

function MultipleChoiceControl({
  field,
  answer,
  disabled,
  describedBy,
  invalid,
  onAnswer,
}: FieldControlProps & { field: MultipleChoiceField }) {
  const selected = multipleAnswer(answer);
  // No aria-required here, unlike every other control: a checkbox group is a plain `group`, and ARIA
  // does not allow aria-required on one — asserting it anyway is a violation rather than a courtesy.
  // "You have to pick something" is carried by the asterisk in the legend and, if they do not, by the
  // refusal that arrives tied to this fieldset through aria-describedby.
  return (
    <fieldset
      aria-labelledby={`${field.fieldId}-label`}
      aria-describedby={describedBy}
      aria-invalid={invalid}
      className="flex flex-col gap-2"
    >
      <legend id={`${field.fieldId}-label`} className="font-medium">
        {field.label}
        {field.required && " *"}
      </legend>
      {field.options.map((option) => (
        <label key={option} className="flex items-center gap-2">
          <input
            type="checkbox"
            value={option}
            checked={selected.includes(option)}
            disabled={disabled}
            onChange={(event) =>
              onAnswer(
                field.fieldId,
                event.target.checked ? [...selected, option] : selected.filter((value) => value !== option),
              )
            }
          />
          {option}
        </label>
      ))}
    </fieldset>
  );
}

function NumberControl({
  field,
  answer,
  disabled,
  describedBy,
  invalid,
  onAnswer,
}: FieldControlProps & { field: NumberField }) {
  return (
    <label className="flex flex-col gap-1 font-medium">
      {field.label}
      {field.required && " *"}
      <input
        type="number"
        step="any"
        value={numberAnswer(answer)}
        min={field.minimum ?? undefined}
        max={field.maximum ?? undefined}
        disabled={disabled}
        required={field.required}
        aria-describedby={describedBy}
        aria-invalid={invalid}
        onChange={(event) =>
          onAnswer(field.fieldId, event.target.value === "" ? "" : Number(event.target.value))
        }
        className="rounded border px-3 py-2 font-normal"
      />
    </label>
  );
}

function FieldControl(props: FieldControlProps) {
  const { field, answer, disabled, describedBy, invalid, onAnswer } = props;
  switch (field.type) {
    case "SHORT_TEXT":
      return (
        <label className="flex flex-col gap-1 font-medium">
          {field.label}
          {field.required && " *"}
          <input
            type="text"
            value={textAnswer(answer)}
            maxLength={field.maxLength}
            disabled={disabled}
            required={field.required}
            aria-describedby={describedBy}
            aria-invalid={invalid}
            onChange={(event) => onAnswer(field.fieldId, event.target.value)}
            className="rounded border px-3 py-2 font-normal"
          />
        </label>
      );
    case "LONG_TEXT":
      return (
        <label className="flex flex-col gap-1 font-medium">
          {field.label}
          {field.required && " *"}
          <textarea
            value={textAnswer(answer)}
            maxLength={field.maxLength}
            disabled={disabled}
            required={field.required}
            aria-describedby={describedBy}
            aria-invalid={invalid}
            onChange={(event) => onAnswer(field.fieldId, event.target.value)}
            className="min-h-28 rounded border px-3 py-2 font-normal"
          />
        </label>
      );
    case "SINGLE_CHOICE":
      return <SingleChoiceControl {...props} field={field} />;
    case "MULTIPLE_CHOICE":
      return <MultipleChoiceControl {...props} field={field} />;
    case "NUMBER":
      return <NumberControl {...props} field={field} />;
  }
}

export function RegistrationFormFields({
  form,
  answers,
  fieldErrors = {},
  disabled = false,
  onAnswer,
}: RegistrationFormFieldsProps) {
  return (
    <div className="flex flex-col gap-5">
      {form.fields.map((field) => {
        const help = field.helpText !== null && field.helpText !== "" ? field.helpText : undefined;
        const error = fieldErrors[field.fieldId];
        // The help text and the refusal are tied to the control they belong to rather than merely
        // sitting under it. Read down the page they were always in the right order; read one control
        // at a time, which is how a screen reader moves through a form, they were nowhere.
        const describedBy =
          [help === undefined ? undefined : `${field.fieldId}-help`, error === undefined ? undefined : `${field.fieldId}-error`]
            .filter((id) => id !== undefined)
            .join(" ") || undefined;
        return (
          <div key={field.fieldId} className="flex flex-col gap-1">
            <FieldControl
              field={field}
              answer={answers[field.fieldId]}
              disabled={disabled}
              describedBy={describedBy}
              invalid={error !== undefined}
              onAnswer={onAnswer}
            />
            {help !== undefined && (
              <p id={`${field.fieldId}-help`} className="text-sm text-slate-700">
                {help}
              </p>
            )}
            {error !== undefined && (
              <p id={`${field.fieldId}-error`} role="alert" className="text-sm text-red-800">
                {error}
              </p>
            )}
          </div>
        );
      })}
    </div>
  );
}
