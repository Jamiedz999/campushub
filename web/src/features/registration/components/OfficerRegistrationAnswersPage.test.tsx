import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import type { OfficerAnswersView } from "../types";
import { OfficerRegistrationAnswersPage } from "./OfficerRegistrationAnswersPage";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

const report: OfficerAnswersView = {
  eventId: "event-1",
  eventTitle: "Robotics Night",
  registrationForm: {
    fields: [
      {
        type: "SINGLE_CHOICE",
        fieldId: "level",
        label: "Experience",
        helpText: "",
        required: false,
        options: ["Beginner", "Advanced"],
      },
      {
        type: "SHORT_TEXT",
        fieldId: "nickname",
        label: "Nickname",
        helpText: "",
        required: false,
        maxLength: 80,
      },
      {
        type: "MULTIPLE_CHOICE",
        fieldId: "topics",
        label: "Topics",
        helpText: "",
        required: false,
        options: ["AI", "Robotics"],
      },
      {
        type: "NUMBER",
        fieldId: "teamSize",
        label: "Team size",
        helpText: "",
        required: false,
        minimum: 1,
        maximum: 5,
      },
    ],
  },
  items: [
    {
      studentId: "student-1",
      studentDisplayName: "Student One",
      enrollmentVia: "DIRECT",
      enrolledAt: "2026-03-02T10:00:00Z",
      answersSaved: false,
      answers: {},
    },
    {
      studentId: "student-2",
      studentDisplayName: "Student Two",
      enrollmentVia: "PROMOTED",
      enrolledAt: "2026-03-03T10:00:00Z",
      answersSaved: true,
      answers: {},
    },
    {
      studentId: "student-3",
      studentDisplayName: "Student Three",
      enrollmentVia: "DIRECT",
      enrolledAt: "2026-03-04T10:00:00Z",
      answersSaved: true,
      answers: { level: "Beginner", nickname: "", topics: ["AI", "Robotics"], teamSize: 3 },
    },
  ],
  page: 0,
  size: 20,
  total: 3,
  optionCounts: [
    { fieldId: "level", option: "Beginner", count: 1 },
    { fieldId: "level", option: "Advanced", count: 0 },
    { fieldId: "topics", option: "AI", count: 1 },
    { fieldId: "topics", option: "Robotics", count: 1 },
  ],
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/officer/events/event-1/registration-answers"]}>
        <Routes>
          <Route
            path="/officer/events/:eventId/registration-answers"
            element={<OfficerRegistrationAnswersPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("OfficerRegistrationAnswersPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("distinguishes missing answer writes from a deliberately empty saved form", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(report));

    renderPage();

    const missingRow = await screen.findByRole("row", { name: /student one/i });
    const emptyRow = screen.getByRole("row", { name: /student two/i });
    const answeredRow = screen.getByRole("row", { name: /student three/i });
    expect(within(missingRow).getByText("Missing — retry needed")).toBeInTheDocument();
    expect(within(emptyRow).getByText("Saved (empty)")).toBeInTheDocument();
    expect(within(answeredRow).getByText("Beginner")).toBeInTheDocument();
    expect(within(answeredRow).getByText("AI, Robotics")).toBeInTheDocument();
    expect(within(answeredRow).getByText("3")).toBeInTheDocument();
  });

  it("shows option counts and offers the full CSV export", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(report));

    renderPage();

    expect(await screen.findByText("Beginner: 1")).toBeInTheDocument();
    expect(screen.getByText("Advanced: 0")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Download CSV" })).toHaveAttribute(
      "href",
      "/api/events/event-1/registration-answers/csv",
    );
  });

  it("renders the empty state, query failures and both pagination actions", async () => {
    const user = userEvent.setup();
    const getSpy = vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse({ ...report, items: [], total: 21 }),
    );

    const first = renderPage();
    expect(await screen.findByText("No Students are enrolled yet.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(2));
    expect(getSpy.mock.calls[1]?.[1]).toMatchObject({ params: { page: 1 } });
    await user.click(screen.getByRole("button", { name: "Previous" }));
    await waitFor(() => expect(getSpy).toHaveBeenCalledTimes(3));
    expect(getSpy.mock.calls[2]?.[1]).toMatchObject({ params: { page: 0 } });
    first.unmount();

    getSpy.mockRejectedValue(
      new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "missing" }),
    );
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent("NOT_FOUND");
  });
});
