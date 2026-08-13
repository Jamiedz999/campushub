import { useState } from "react";
import { Link, useParams } from "react-router";
import type { RegistrationAnswer } from "../../../types/registrationForm";
import { useOfficerRegistrationAnswers } from "../hooks/useOfficerRegistrationAnswers";
import type { OfficerAnswer } from "../types";

const PAGE_SIZE = 20;

function answerText(answer: RegistrationAnswer | undefined) {
  if (answer === undefined || answer === "" || (Array.isArray(answer) && answer.length === 0)) {
    return "—";
  }
  return Array.isArray(answer) ? answer.join(", ") : String(answer);
}

function answerStatus(answer: OfficerAnswer) {
  if (!answer.answersSaved) {
    return "Missing — retry needed";
  }
  return Object.keys(answer.answers).length === 0 ? "Saved (empty)" : "Saved";
}

function enrollmentRoute(route: OfficerAnswer["enrollmentVia"]) {
  return route === "PROMOTED" ? "Promoted from Waitlist" : "Direct";
}

export function OfficerRegistrationAnswersPage() {
  const { eventId } = useParams<{ eventId: string }>();
  const [page, setPage] = useState(0);
  const query = useOfficerRegistrationAnswers(eventId ?? "", { page, size: PAGE_SIZE });

  if (eventId === undefined) {
    return <p role="alert">No Event was specified.</p>;
  }

  const pageCount = Math.max(1, Math.ceil((query.data?.total ?? 0) / PAGE_SIZE));

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-5 p-6">
      <Link to="/events" className="text-sm text-slate-600">
        &larr; Back to events
      </Link>
      {query.status === "pending" && <p role="status">Loading registration answers…</p>}
      {query.status === "error" && <p role="alert">Could not load answers ({query.error.code}).</p>}
      {query.status === "success" && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h1 className="text-xl font-semibold">Registration answers · {query.data.eventTitle}</h1>
            <a
              href={`/api/events/${eventId}/registration-answers/csv`}
              className="rounded border px-4 py-2 text-sm font-medium"
            >
              Download CSV
            </a>
          </div>

          {query.data.optionCounts.length > 0 && (
            <section className="rounded border p-4">
              <h2 className="mb-3 font-semibold">Choice counts</h2>
              <div className="flex flex-col gap-3">
                {query.data.registrationForm.fields.map((field) => {
                  if (field.type !== "SINGLE_CHOICE" && field.type !== "MULTIPLE_CHOICE") {
                    return null;
                  }
                  return (
                    <div key={field.fieldId}>
                      <h3 className="text-sm font-medium">{field.label}</h3>
                      <ul className="flex flex-wrap gap-3 text-sm">
                        {query.data.optionCounts
                          .filter((count) => count.fieldId === field.fieldId)
                          .map((count) => (
                            <li key={count.option}>
                              {count.option}: {count.count}
                            </li>
                          ))}
                      </ul>
                    </div>
                  );
                })}
              </div>
            </section>
          )}

          <div className="overflow-x-auto">
            <table className="min-w-full border-collapse text-left text-sm">
              <thead>
                <tr>
                  <th className="border p-2">Student</th>
                  <th className="border p-2">Route in</th>
                  <th className="border p-2">Answers status</th>
                  {query.data.registrationForm.fields.map((field) => (
                    <th key={field.fieldId} className="border p-2">
                      {field.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {query.data.items.map((item) => (
                  <tr key={item.studentId}>
                    <td className="border p-2">{item.studentDisplayName}</td>
                    <td className="border p-2">{enrollmentRoute(item.enrollmentVia)}</td>
                    <td className="border p-2">{answerStatus(item)}</td>
                    {query.data.registrationForm.fields.map((field) => (
                      <td key={field.fieldId} className="border p-2">
                        {answerText(item.answers[field.fieldId])}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {query.data.items.length === 0 && <p>No Students are enrolled yet.</p>}

          <nav className="flex items-center gap-3" aria-label="Pagination">
            <button
              type="button"
              onClick={() => setPage((current) => current - 1)}
              disabled={page === 0}
              className="rounded border px-3 py-1 disabled:opacity-50"
            >
              Previous
            </button>
            <span>
              Page {page + 1} of {pageCount}
            </span>
            <button
              type="button"
              onClick={() => setPage((current) => current + 1)}
              disabled={page + 1 >= pageCount}
              className="rounded border px-3 py-1 disabled:opacity-50"
            >
              Next
            </button>
          </nav>
        </>
      )}
    </main>
  );
}
