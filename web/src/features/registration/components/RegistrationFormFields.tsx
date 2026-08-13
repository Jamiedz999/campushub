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

interface FieldControlProps {
  field: RegistrationFormField;
  answer: RegistrationAnswer | undefined;
  disabled: boolean;
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
  onAnswer,
}: FieldControlProps & { field: SingleChoiceField }) {
  return (
    <fieldset role="radiogroup" aria-labelledby={`${field.fieldId}-label`} className="flex flex-col gap-2">
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
  onAnswer,
}: FieldControlProps & { field: MultipleChoiceField }) {
  const selected = multipleAnswer(answer);
  return (
    <fieldset aria-labelledby={`${field.fieldId}-label`} className="flex flex-col gap-2">
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

function NumberControl({ field, answer, disabled, onAnswer }: FieldControlProps & { field: NumberField }) {
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
        onChange={(event) =>
          onAnswer(field.fieldId, event.target.value === "" ? "" : Number(event.target.value))
        }
        className="rounded border px-3 py-2 font-normal"
      />
    </label>
  );
}

function FieldControl(props: FieldControlProps) {
  const { field, answer, disabled, onAnswer } = props;
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
      {form.fields.map((field) => (
        <div key={field.fieldId} className="flex flex-col gap-1">
          <FieldControl
            field={field}
            answer={answers[field.fieldId]}
            disabled={disabled}
            onAnswer={onAnswer}
          />
          {field.helpText !== null && field.helpText !== "" && (
            <p className="text-sm text-slate-600">{field.helpText}</p>
          )}
          {fieldErrors[field.fieldId] !== undefined && (
            <p role="alert" className="text-sm text-red-700">
              {fieldErrors[field.fieldId]}
            </p>
          )}
        </div>
      ))}
    </div>
  );
}
