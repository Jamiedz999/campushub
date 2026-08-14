export interface RegistrationForm {
  fields: RegistrationFormField[];
}

interface FormFieldBase {
  fieldId: string;
  label: string;
  helpText: string | null;
  required: boolean;
}

export interface ShortTextField extends FormFieldBase {
  type: "SHORT_TEXT";
  maxLength: number;
}

export interface LongTextField extends FormFieldBase {
  type: "LONG_TEXT";
  maxLength: number;
}

export interface SingleChoiceField extends FormFieldBase {
  type: "SINGLE_CHOICE";
  options: string[];
}

export interface MultipleChoiceField extends FormFieldBase {
  type: "MULTIPLE_CHOICE";
  options: string[];
}

export interface NumberField extends FormFieldBase {
  type: "NUMBER";
  minimum: number | null;
  maximum: number | null;
}

export type RegistrationFormField =
  | ShortTextField
  | LongTextField
  | SingleChoiceField
  | MultipleChoiceField
  | NumberField;

export type RegistrationAnswer = string | string[] | number;
export type RegistrationAnswers = Record<string, RegistrationAnswer>;
export type RegistrationFieldErrors = Record<string, string>;
