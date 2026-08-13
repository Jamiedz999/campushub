import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useParams } from "react-router";
import { describePhase } from "../describePhase";
import { describeRegistrationError } from "../describeRegistrationError";
import { useEventRegistration } from "../hooks/useEventRegistration";
import { useRegisterForEvent } from "../hooks/useRegisterForEvent";
import { useRetryRegistrationAnswers } from "../hooks/useRetryRegistrationAnswers";
import { useWithdrawFromEvent } from "../hooks/useWithdrawFromEvent";
import type { Phase } from "../../../types/phase";
import type {
  RegistrationAnswer,
  RegistrationAnswers,
  RegistrationFieldErrors,
} from "../../../types/registrationForm";
import { validateRegistrationAnswers } from "../validateRegistrationAnswers";
import { RegistrationFormFields } from "./RegistrationFormFields";

function canWithdraw(phase: Phase) {
  return ["SCHEDULED", "REGISTRATION_OPEN", "FULL", "REGISTRATION_CLOSED"].includes(phase);
}

export function EventRegistrationPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const query = useEventRegistration(eventId ?? "");
  const registrationMutation = useRegisterForEvent(eventId ?? "");
  const retryMutation = useRetryRegistrationAnswers(eventId ?? "");
  const withdrawalMutation = useWithdrawFromEvent(eventId ?? "");
  const [answers, setAnswers] = useState<RegistrationAnswers>({});
  const [fieldErrors, setFieldErrors] = useState<RegistrationFieldErrors>({});

  function updateAnswer(fieldId: string, answer: RegistrationAnswer) {
    setAnswers((current) => ({ ...current, [fieldId]: answer }));
    setFieldErrors((current) => {
      const { [fieldId]: ignored, ...remaining } = current;
      void ignored;
      return remaining;
    });
  }

  function submitRegistration(formEvent?: FormEvent<HTMLFormElement>) {
    formEvent?.preventDefault();
    if (query.data === undefined) {
      return;
    }
    const clientErrors = validateRegistrationAnswers(query.data.registrationForm, answers);
    setFieldErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }
    registrationMutation.mutate(answers, {
      onError: (error) => setFieldErrors(error.fieldErrors),
    });
  }

  function retryAnswers(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault();
    if (query.data === undefined) {
      return;
    }
    const clientErrors = validateRegistrationAnswers(query.data.registrationForm, answers);
    setFieldErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }
    retryMutation.mutate(answers, {
      onError: (error) => setFieldErrors(error.fieldErrors),
    });
  }

  if (eventId === undefined) {
    return <p role="alert">No Event was specified.</p>;
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-4 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>

      {query.status === "pending" && <p role="status">Loading event…</p>}

      {query.status === "error" && <p role="alert">{describeRegistrationError(query.error.code)}</p>}

      {query.status === "success" && (
        <>
          <h1 className="text-xl font-semibold">{query.data.title}</h1>
          <p className="text-sm text-slate-600">{query.data.description}</p>
          <p className="text-sm">{describePhase(query.data)}</p>

          {query.data.enrolled ? (
            <>
              <p className="font-medium text-emerald-700">
                {query.data.enrollmentVia === "PROMOTED"
                  ? "You were on the Waitlist — you’re in."
                  : "You’re registered for this Event."}
              </p>
              {query.data.answersSaved === false && (
                <form
                  onSubmit={retryAnswers}
                  noValidate
                  className="flex flex-col gap-4 rounded border border-amber-300 p-4"
                >
                  <p className="font-medium text-amber-900">
                    Your Seat is safe, but your answers were not saved.
                  </p>
                  <RegistrationFormFields
                    form={query.data.registrationForm}
                    answers={answers}
                    fieldErrors={fieldErrors}
                    disabled={retryMutation.isPending}
                    onAnswer={updateAnswer}
                  />
                  <button
                    type="submit"
                    disabled={retryMutation.isPending}
                    className="w-fit rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
                  >
                    {retryMutation.isPending ? "Saving…" : "Retry saving answers"}
                  </button>
                </form>
              )}
              {query.data.answersSaved === true && query.data.registrationForm.fields.length > 0 && (
                <section className="flex flex-col gap-4 rounded border p-4">
                  <h2 className="font-medium">Your answers</h2>
                  <RegistrationFormFields
                    form={query.data.registrationForm}
                    answers={query.data.answers}
                    disabled
                    onAnswer={() => undefined}
                  />
                </section>
              )}
            </>
          ) : query.data.waitlistPosition !== null ? (
            <>
              <p className="font-medium text-indigo-700">
                You&rsquo;re number {query.data.waitlistPosition} on the Waitlist.
              </p>
              {query.data.registrationForm.fields.length > 0 && (
                <div className="flex flex-col gap-3 rounded border p-4">
                  <p className="text-sm text-slate-600">
                    These answers are still kept in this browser if a Seat opens up.
                  </p>
                  <RegistrationFormFields
                    form={query.data.registrationForm}
                    answers={answers}
                    fieldErrors={fieldErrors}
                    onAnswer={updateAnswer}
                  />
                </div>
              )}
            </>
          ) : (
            (query.data.phase === "REGISTRATION_OPEN" || query.data.phase === "FULL") && (
              query.data.registrationForm.fields.length === 0 ? (
                <button
                  type="button"
                  onClick={() => submitRegistration()}
                  disabled={registrationMutation.isPending}
                  className="w-fit rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
                >
                  {registrationMutation.isPending
                    ? "Registering…"
                    : query.data.phase === "FULL"
                      ? "Join the Waitlist"
                      : "Register"}
                </button>
              ) : (
                <form onSubmit={submitRegistration} noValidate className="flex flex-col gap-4 rounded border p-4">
                  <RegistrationFormFields
                    form={query.data.registrationForm}
                    answers={answers}
                    fieldErrors={fieldErrors}
                    disabled={registrationMutation.isPending}
                    onAnswer={updateAnswer}
                  />
                  <button
                    type="submit"
                    disabled={registrationMutation.isPending}
                    className="w-fit rounded bg-slate-900 px-4 py-2 text-white disabled:opacity-50"
                  >
                    {registrationMutation.isPending
                      ? "Registering…"
                      : query.data.phase === "FULL"
                        ? "Join the Waitlist"
                        : "Register"}
                  </button>
                </form>
              )
            )
          )}

          {(query.data.enrolled || query.data.waitlistPosition !== null) && canWithdraw(query.data.phase) && (
            <button
              type="button"
              onClick={() => withdrawalMutation.mutate()}
              disabled={withdrawalMutation.isPending}
              className="w-fit rounded border px-4 py-2 disabled:opacity-50"
            >
              {query.data.enrolled
                ? withdrawalMutation.isPending
                  ? "Withdrawing…"
                  : "Withdraw from Event"
                : withdrawalMutation.isPending
                  ? "Leaving…"
                  : "Leave the Waitlist"}
            </button>
          )}

          {registrationMutation.status === "error" && (
            <p role="alert">{describeRegistrationError(registrationMutation.error.code)}</p>
          )}

          {retryMutation.status === "error" && Object.keys(retryMutation.error.fieldErrors).length === 0 && (
            <p role="alert">{describeRegistrationError(retryMutation.error.code)}</p>
          )}

          {withdrawalMutation.status === "error" && (
            <p role="alert">{describeRegistrationError(withdrawalMutation.error.code)}</p>
          )}
        </>
      )}
    </main>
  );
}
