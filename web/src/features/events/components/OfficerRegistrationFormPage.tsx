import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router";
import type {
  RegistrationFormField,
} from "../../../types/registrationForm";
import { useOfficerEvent } from "../hooks/useOfficerEvent";
import { useUpdateRegistrationForm } from "../hooks/useUpdateRegistrationForm";
import type { EventOfficerView } from "../types";

type FieldType = RegistrationFormField["type"];

const FIELD_TYPES: { value: FieldType; label: string }[] = [
  { value: "SHORT_TEXT", label: "Short text" },
  { value: "LONG_TEXT", label: "Long text" },
  { value: "SINGLE_CHOICE", label: "Single choice" },
  { value: "MULTIPLE_CHOICE", label: "Multiple choice" },
  { value: "NUMBER", label: "Number" },
];

function isFieldType(value: string): value is FieldType {
  return FIELD_TYPES.some((type) => type.value === value);
}

function newObjectId() {
  const timestamp = Math.floor(Date.now() / 1_000).toString(16).padStart(8, "0");
  const random = Array.from(crypto.getRandomValues(new Uint8Array(8)), (byte) =>
    byte.toString(16).padStart(2, "0"),
  ).join("");
  return timestamp + random;
}

function newField(type: FieldType): RegistrationFormField {
  const common = {
    fieldId: newObjectId(),
    label: "Untitled question",
    helpText: "",
    required: false,
  };
  switch (type) {
    case "SHORT_TEXT":
      return { ...common, type, maxLength: 100 };
    case "LONG_TEXT":
      return { ...common, type, maxLength: 1_000 };
    case "SINGLE_CHOICE":
      return { ...common, type, options: [] };
    case "MULTIPLE_CHOICE":
      return { ...common, type, options: [] };
    case "NUMBER":
      return { ...common, type, minimum: null, maximum: null };
  }
}

function withLabel(field: RegistrationFormField, label: string): RegistrationFormField {
  return { ...field, label };
}

function withHelpText(field: RegistrationFormField, helpText: string): RegistrationFormField {
  return { ...field, helpText };
}

function withRequired(field: RegistrationFormField, required: boolean): RegistrationFormField {
  return { ...field, required };
}

function lines(value: string) {
  return value
    .split("\n")
    .map((option) => option.trim())
    .filter((option) => option !== "");
}

function optionalNumber(value: string) {
  if (value === "") {
    return null;
  }
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function definitionError(fields: RegistrationFormField[]) {
  const ids = new Set<string>();
  for (const field of fields) {
    if (field.label.trim() === "") {
      return "Every field needs a label.";
    }
    if (ids.has(field.fieldId)) {
      return "Every field needs a unique id.";
    }
    ids.add(field.fieldId);
    if ((field.type === "SHORT_TEXT" || field.type === "LONG_TEXT") && field.maxLength < 1) {
      return "Text limits must be at least 1.";
    }
    if (field.type === "SINGLE_CHOICE" || field.type === "MULTIPLE_CHOICE") {
      if (field.options.length === 0 || new Set(field.options).size !== field.options.length) {
        return "Choice fields need at least one option and no duplicates.";
      }
    }
    if (
      field.type === "NUMBER" &&
      field.minimum !== null &&
      field.maximum !== null &&
      field.minimum > field.maximum
    ) {
      return "A number field's minimum cannot exceed its maximum.";
    }
  }
  return null;
}

interface FormBuilderProps {
  event: EventOfficerView;
}

function FormBuilder({ event }: FormBuilderProps) {
  const mutation = useUpdateRegistrationForm(event.id);
  const [fields, setFields] = useState<RegistrationFormField[]>(event.registrationForm.fields);
  const [fieldType, setFieldType] = useState<FieldType>("SHORT_TEXT");
  const [validationError, setValidationError] = useState<string | null>(null);

  function replace(index: number, update: (field: RegistrationFormField) => RegistrationFormField) {
    setFields((current) => current.map((field, fieldIndex) => (fieldIndex === index ? update(field) : field)));
  }

  function move(index: number, offset: -1 | 1) {
    setFields((current) => {
      const destination = index + offset;
      if (destination < 0 || destination >= current.length) {
        return current;
      }
      return current.map((field, fieldIndex) => {
        if (fieldIndex === index) {
          return current[destination] ?? field;
        }
        if (fieldIndex === destination) {
          return current[index] ?? field;
        }
        return field;
      });
    });
  }

  function save(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    const error = definitionError(fields);
    setValidationError(error);
    if (error === null) {
      mutation.mutate({ fields });
    }
  }

  if (event.registrationFormLocked) {
    return (
      <section className="flex flex-col gap-3 rounded border border-amber-300 bg-amber-50 p-4">
        <p className="font-medium text-amber-900">
          This form is locked because a Student has already registered.
        </p>
        {event.registrationForm.fields.length === 0 ? (
          <p>This Event has no custom questions.</p>
        ) : (
          <ol className="list-decimal pl-5">
            {event.registrationForm.fields.map((field) => (
              <li key={field.fieldId}>{field.label}</li>
            ))}
          </ol>
        )}
      </section>
    );
  }

  return (
    <form onSubmit={save} noValidate className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1">
          Field type
          <select
            value={fieldType}
            onChange={(changeEvent) => {
              if (isFieldType(changeEvent.target.value)) {
                setFieldType(changeEvent.target.value);
              }
            }}
            className="rounded border px-3 py-2"
          >
            {FIELD_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          onClick={() => setFields((current) => [...current, newField(fieldType)])}
          className="rounded border px-4 py-2"
        >
          Add field
        </button>
      </div>

      {fields.length === 0 && <p>No custom questions. Students will register in one action.</p>}

      {fields.map((field, index) => (
        <fieldset
          key={field.fieldId}
          aria-label={`Field ${index + 1}`}
          className="flex flex-col gap-3 rounded border p-4"
        >
          <div className="flex flex-wrap gap-2">
            <span className="mr-auto text-sm font-medium text-slate-600">
              {FIELD_TYPES.find((type) => type.value === field.type)?.label}
            </span>
            <button type="button" onClick={() => move(index, -1)} disabled={index === 0} className="text-sm">
              Move up
            </button>
            <button
              type="button"
              onClick={() => move(index, 1)}
              disabled={index === fields.length - 1}
              className="text-sm"
            >
              Move down
            </button>
            <button
              type="button"
              onClick={() => setFields((current) => current.filter((entry) => entry.fieldId !== field.fieldId))}
              className="text-sm text-red-700"
            >
              Remove
            </button>
          </div>
          <label className="flex flex-col gap-1">
            Label
            <input
              value={field.label}
              onChange={(changeEvent) => replace(index, (entry) => withLabel(entry, changeEvent.target.value))}
              className="rounded border px-3 py-2"
            />
          </label>
          <label className="flex flex-col gap-1">
            Help text
            <input
              value={field.helpText ?? ""}
              onChange={(changeEvent) => replace(index, (entry) => withHelpText(entry, changeEvent.target.value))}
              className="rounded border px-3 py-2"
            />
          </label>
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={field.required}
              onChange={(changeEvent) => replace(index, (entry) => withRequired(entry, changeEvent.target.checked))}
            />
            Required
          </label>

          {(field.type === "SHORT_TEXT" || field.type === "LONG_TEXT") && (
            <label className="flex flex-col gap-1">
              Maximum length
              <input
                type="number"
                min="1"
                value={field.maxLength}
                onChange={(changeEvent) =>
                  replace(index, (entry) =>
                    entry.type === "SHORT_TEXT" || entry.type === "LONG_TEXT"
                      ? { ...entry, maxLength: Number(changeEvent.target.value) }
                      : entry,
                  )
                }
                className="w-40 rounded border px-3 py-2"
              />
            </label>
          )}

          {(field.type === "SINGLE_CHOICE" || field.type === "MULTIPLE_CHOICE") && (
            <label className="flex flex-col gap-1">
              Options, one per line
              <textarea
                value={field.options.join("\n")}
                onChange={(changeEvent) =>
                  replace(index, (entry) =>
                    entry.type === "SINGLE_CHOICE" || entry.type === "MULTIPLE_CHOICE"
                      ? { ...entry, options: lines(changeEvent.target.value) }
                      : entry,
                  )
                }
                className="min-h-24 rounded border px-3 py-2"
              />
            </label>
          )}

          {field.type === "NUMBER" && (
            <div className="flex flex-wrap gap-3">
              <label className="flex flex-col gap-1">
                Minimum
                <input
                  type="number"
                  value={field.minimum ?? ""}
                  onChange={(changeEvent) =>
                    replace(index, (entry) =>
                      entry.type === "NUMBER"
                        ? { ...entry, minimum: optionalNumber(changeEvent.target.value) }
                        : entry,
                    )
                  }
                  className="w-40 rounded border px-3 py-2"
                />
              </label>
              <label className="flex flex-col gap-1">
                Maximum
                <input
                  type="number"
                  value={field.maximum ?? ""}
                  onChange={(changeEvent) =>
                    replace(index, (entry) =>
                      entry.type === "NUMBER"
                        ? { ...entry, maximum: optionalNumber(changeEvent.target.value) }
                        : entry,
                    )
                  }
                  className="w-40 rounded border px-3 py-2"
                />
              </label>
            </div>
          )}
        </fieldset>
      ))}

      {validationError !== null && <p role="alert">{validationError}</p>}
      {mutation.isError && mutation.error.code !== "FORM_LOCKED" && (
        <p role="alert">Could not save the form ({mutation.error.code}).</p>
      )}
      {mutation.isSuccess && <p className="text-emerald-700">Registration form saved.</p>}
      <button
        type="submit"
        disabled={mutation.isPending}
        className="w-fit rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
      >
        {mutation.isPending ? "Saving…" : "Save registration form"}
      </button>
    </form>
  );
}

export function OfficerRegistrationFormPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const query = useOfficerEvent(eventId ?? "");

  if (eventId === undefined) {
    return <p role="alert">No Event was specified.</p>;
  }

  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-5 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>
      {query.status === "pending" && <p role="status">Loading registration form…</p>}
      {query.status === "error" && <p role="alert">Could not load Event ({query.error.code}).</p>}
      {query.status === "success" && (
        <>
          <h1 className="text-xl font-semibold">Registration form · {query.data.title}</h1>
          <FormBuilder event={query.data} />
        </>
      )}
    </main>
  );
}
