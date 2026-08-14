import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import type { EventRegistrationView } from "../types";
import { accessibilityViolations } from "../../../testAccessibility";
import { EventRegistrationPage } from "./EventRegistrationPage";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function view(overrides: Partial<EventRegistrationView>): EventRegistrationView {
  return {
    id: "event-1",
    clubId: "club-1",
    title: "Robotics Night",
    description: "Build a small robot",
    phase: "REGISTRATION_OPEN",
    registrationOpensAt: "2026-03-01T00:00:00Z",
    registrationClosesAt: "2026-03-10T00:00:00Z",
    startsAt: "2026-03-20T00:00:00Z",
    endsAt: "2026-03-20T02:00:00Z",
    capacity: 40,
    enrolledCount: 28,
    waitlistCount: 0,
    enrolled: false,
    enrollmentVia: null,
    waitlistPosition: null,
    registrationForm: { fields: [] },
    answersSaved: null,
    answers: {},
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/events/event-1"]}>
        <Routes>
          <Route path="/events/:eventId" element={<EventRegistrationPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("EventRegistrationPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders a loading state while the request is in flight", () => {
    vi.spyOn(httpClient, "get").mockReturnValue(new Promise(() => {}));

    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent(/loading/i);
  });

  it("shows the Register button when registration is open and the Student is not enrolled", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({})));

    renderPage();

    await waitFor(() => expect(screen.getByText("Robotics Night")).toBeInTheDocument());
    expect(screen.getByText("12 of 40 seats left")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Register" })).toBeInTheDocument();
  });

  it("shows a confirmation instead of a button once already enrolled", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({ enrolled: true })));

    renderPage();

    await waitFor(() => expect(screen.getByText(/you.re registered/i)).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Register" })).not.toBeInTheDocument();
  });

  it("shows an enrolled Student their own saved answers", async () => {
    const customForm = {
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "teamName",
          label: "Team name",
          helpText: null,
          required: true,
          maxLength: 20,
        },
      ],
    };
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(
        view({
          enrolled: true,
          answersSaved: true,
          registrationForm: customForm,
          answers: { teamName: "Circuit Breakers" },
        }),
      ),
    );

    renderPage();

    expect(await screen.findByText("Your answers")).toBeInTheDocument();
    expect(screen.getByLabelText("Team name *")).toHaveValue("Circuit Breakers");
    expect(screen.getByLabelText("Team name *")).toBeDisabled();
  });

  it("offers the Waitlist once the Event is full", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({ phase: "FULL" })));

    renderPage();

    await waitFor(() => expect(screen.getByText("This Event is full")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Join the Waitlist" })).toBeInTheDocument();
  });

  it("shows the Students position and lets them leave the Waitlist", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ phase: "FULL", waitlistCount: 4, waitlistPosition: 3 })),
    );
    const deleteSpy = vi
      .spyOn(httpClient, "delete")
      .mockResolvedValue(axiosResponse(view({ phase: "FULL", waitlistCount: 3 })));

    renderPage();

    await waitFor(() => expect(screen.getByText("You’re number 3 on the Waitlist.")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: "Leave the Waitlist" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "Join the Waitlist" })).toBeInTheDocument());
    expect(deleteSpy).toHaveBeenCalledWith("/events/event-1/registration");
  });

  it("shows the promotion badge when the Student came off the Waitlist", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ enrolled: true, enrollmentVia: "PROMOTED" })),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByText("You were on the Waitlist — you’re in.")).toBeInTheDocument();
    });
  });

  it("lets an enrolled Student withdraw and renders the fresh registration view", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ enrolled: true, enrollmentVia: "DIRECT" })),
    );
    const deleteSpy = vi.spyOn(httpClient, "delete").mockResolvedValue(axiosResponse(view({})));

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Withdraw from Event" }));

    expect(await screen.findByRole("button", { name: "Register" })).toBeInTheDocument();
    expect(deleteSpy).toHaveBeenCalledWith("/events/event-1/registration");
  });

  it("shows the shared error when a withdrawal reaches the start-time freeze", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ enrolled: true, enrollmentVia: "DIRECT" })),
    );
    vi.spyOn(httpClient, "delete").mockRejectedValue(
      new ApiError({ code: "EVENT_STARTED", status: 409, title: "Conflict", detail: "started" }),
    );

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Withdraw from Event" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("This Event has already started.");
  });

  it("renders the load error from the error's code", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "no such event" }),
    );

    renderPage();

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("This Event could not be found.");
    });
  });

  it("registers on click and shows the fresh, enrolled view", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({})));
    const postSpy = vi
      .spyOn(httpClient, "post")
      .mockResolvedValue(axiosResponse(view({ enrolled: true, enrolledCount: 29, answersSaved: true })));

    renderPage();
    await waitFor(() => expect(screen.getByRole("button", { name: "Register" })).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() => expect(screen.getByText(/you.re registered/i)).toBeInTheDocument());
    expect(postSpy).toHaveBeenCalledWith("/events/event-1/registration", { answers: {} });
  });

  it("validates and submits a custom form before taking a Seat", async () => {
    const user = userEvent.setup();
    const customForm = {
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "teamName",
          label: "Team name",
          helpText: "Your public team name",
          required: true,
          maxLength: 20,
        },
      ],
    };
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ registrationForm: customForm })),
    );
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValue(
      axiosResponse(view({ registrationForm: customForm, enrolled: true, answersSaved: true })),
    );

    renderPage();
    expect((await screen.findByLabelText("Team name *")).closest("form")).toHaveAttribute("novalidate");
    await user.click(await screen.findByRole("button", { name: "Register" }));

    expect(await screen.findByText("Required.")).toBeInTheDocument();
    expect(postSpy).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText("Team name *"), "Circuit Breakers");
    await user.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith("/events/event-1/registration", {
        answers: { teamName: "Circuit Breakers" },
      }),
    );
  });

  it("keeps answers visible when a full-Event race puts the Student on the Waitlist", async () => {
    const user = userEvent.setup();
    const customForm = {
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "teamName",
          label: "Team name",
          helpText: "",
          required: true,
          maxLength: 20,
        },
      ],
    };
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({ registrationForm: customForm })));
    vi.spyOn(httpClient, "post").mockResolvedValue(
      axiosResponse(
        view({
          phase: "FULL",
          waitlistPosition: 1,
          registrationForm: customForm,
          answersSaved: null,
        }),
      ),
    );

    renderPage();
    await user.type(await screen.findByLabelText("Team name *"), "Circuit Breakers");
    await user.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByText("You’re number 1 on the Waitlist.")).toBeInTheDocument();
    expect(screen.getByLabelText("Team name *")).toHaveValue("Circuit Breakers");
  });

  it("makes a failed answer write explicit and retries answers without touching the Seat", async () => {
    const user = userEvent.setup();
    const customForm = {
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "teamName",
          label: "Team name",
          helpText: "",
          required: true,
          maxLength: 20,
        },
      ],
    };
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({ registrationForm: customForm })));
    vi.spyOn(httpClient, "post").mockResolvedValue(
      axiosResponse(
        view({ registrationForm: customForm, enrolled: true, answersSaved: false, enrolledCount: 29 }),
      ),
    );
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(
      axiosResponse(view({ registrationForm: customForm, enrolled: true, answersSaved: true })),
    );

    renderPage();
    await user.type(await screen.findByLabelText("Team name *"), "Circuit Breakers");
    await user.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByText(/seat is safe.*answers were not saved/i)).toBeInTheDocument();
    expect(screen.getByLabelText("Team name *")).toHaveValue("Circuit Breakers");
    expect(screen.getByLabelText("Team name *").closest("form")).toHaveAttribute("novalidate");
    await user.click(screen.getByRole("button", { name: "Retry saving answers" }));

    await waitFor(() =>
      expect(putSpy).toHaveBeenCalledWith("/events/event-1/registration/answers", {
        answers: { teamName: "Circuit Breakers" },
      }),
    );
    expect(await screen.findByText(/you.re registered/i)).toBeInTheDocument();
  });

  it("shows the matching message when the registration attempt is refused", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({})));
    vi.spyOn(httpClient, "post").mockRejectedValue(
      new ApiError({ code: "EVENT_FULL", status: 409, title: "Conflict", detail: "full" }),
    );

    renderPage();
    await waitFor(() => expect(screen.getByRole("button", { name: "Register" })).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: "Register" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("This Event is full.");
    });
  });

  it("shows server-side field errors beside the refreshed custom form", async () => {
    const user = userEvent.setup();
    const customForm = {
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "teamName",
          label: "Team name",
          helpText: "",
          required: true,
          maxLength: 20,
        },
      ],
    };
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({ registrationForm: customForm })));
    vi.spyOn(httpClient, "post").mockRejectedValue(
      new ApiError({
        code: "FORM_VALIDATION_FAILED",
        status: 400,
        title: "Form Validation Failed",
        detail: "invalid",
        fieldErrors: { teamName: "The form changed; check this answer." },
      }),
    );

    renderPage();
    await user.type(await screen.findByLabelText("Team name *"), "Robots");
    await user.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByText("The form changed; check this answer.")).toBeInTheDocument();
    expect(screen.getByLabelText("Team name *")).toHaveValue("Robots");
  });

  it("uses the custom-form Waitlist action and shows a retry transport failure", async () => {
    const user = userEvent.setup();
    const customForm = {
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "teamName",
          label: "Team name",
          helpText: "",
          required: true,
          maxLength: 20,
        },
      ],
    };
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(
        view({ phase: "FULL", registrationForm: customForm, enrolled: true, answersSaved: false }),
      ),
    );
    vi.spyOn(httpClient, "put").mockRejectedValue(
      new ApiError({ code: "INTERNAL_ERROR", status: 500, title: "Error", detail: "boom" }),
    );

    renderPage();
    await user.type(await screen.findByLabelText("Team name *"), "Robots");
    await user.click(screen.getByRole("button", { name: "Retry saving answers" }));

    expect(await screen.findByText("Something went wrong. Please try again.")).toBeInTheDocument();
  });

  it("shows in-flight wording for both withdrawal routes", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ enrolled: true, enrollmentVia: "DIRECT", answersSaved: true })),
    );
    vi.spyOn(httpClient, "delete").mockReturnValue(new Promise(() => {}));

    const enrolledPage = renderPage();
    await user.click(await screen.findByRole("button", { name: "Withdraw from Event" }));
    expect(screen.getByRole("button", { name: "Withdrawing…" })).toBeDisabled();
    enrolledPage.unmount();

    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ phase: "FULL", waitlistPosition: 2 })),
    );
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Leave the Waitlist" }));
    expect(screen.getByRole("button", { name: "Leaving…" })).toBeDisabled();
  });

  // The registration form's own structure is checked field by field in RegistrationFormFields.test.tsx;
  // what this adds is the page around it — its heading, its landmark and the Seat counts beside them.
  it("has no accessibility violations around the form it wraps", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view({})));

    const { container } = renderPage();

    await screen.findByText("Robotics Night");
    expect(await accessibilityViolations(container)).toEqual([]);
  });
});
