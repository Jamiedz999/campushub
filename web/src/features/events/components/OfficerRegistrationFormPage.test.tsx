import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AxiosHeaders } from "axios";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../lib/apiError";
import { httpClient } from "../../../lib/httpClient";
import type { RegistrationFormField } from "../../../types/registrationForm";
import type { EventOfficerView } from "../types";
import { OfficerRegistrationFormPage } from "./OfficerRegistrationFormPage";

function axiosResponse<T>(data: T) {
  return {
    data,
    status: 200,
    statusText: "OK",
    headers: new AxiosHeaders(),
    config: { headers: new AxiosHeaders() },
  };
}

function view(overrides: Partial<EventOfficerView> = {}): EventOfficerView {
  return {
    id: "event-1",
    clubId: "club-1",
    title: "Robotics Night",
    description: "Build a robot",
    status: "PUBLISHED",
    phase: "REGISTRATION_OPEN",
    registrationOpensAt: "2026-03-01T00:00:00Z",
    registrationClosesAt: "2026-03-10T00:00:00Z",
    startsAt: "2026-03-20T00:00:00Z",
    endsAt: "2026-03-20T02:00:00Z",
    capacity: 40,
    enrolledCount: 0,
    waitlistCount: 0,
    promotedCount: 0,
    everQueuedCount: 0,
    waitlistConversion: 0,
    registrationForm: { fields: [] },
    registrationFormLocked: false,
    ...overrides,
  };
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/officer/events/event-1/registration-form"]}>
        <Routes>
          <Route path="/officer/events/:eventId/registration-form" element={<OfficerRegistrationFormPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function withFields(fields: RegistrationFormField[]) {
  return view({ registrationForm: { fields } });
}

describe("OfficerRegistrationFormPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("builds an ordered form while keeping generated field ids stable", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view()));
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(axiosResponse(view()));

    renderPage();
    await user.selectOptions(await screen.findByLabelText("Field type"), "Short text");
    await user.click(screen.getByRole("button", { name: "Add field" }));
    await user.selectOptions(screen.getByLabelText("Field type"), "Single choice");
    await user.click(screen.getByRole("button", { name: "Add field" }));

    const fieldCards = screen.getAllByRole("group", { name: /field \d/i });
    await user.clear(within(fieldCards[0]!).getByLabelText("Label"));
    await user.type(within(fieldCards[0]!).getByLabelText("Label"), "Team name");
    await user.clear(within(fieldCards[1]!).getByLabelText("Label"));
    await user.type(within(fieldCards[1]!).getByLabelText("Label"), "Experience");
    fireEvent.change(within(fieldCards[1]!).getByLabelText("Options, one per line"), {
      target: { value: "Beginner\nAdvanced" },
    });
    await user.click(within(fieldCards[1]!).getByRole("button", { name: "Move up" }));
    await user.click(screen.getByRole("button", { name: "Save registration form" }));

    await waitFor(() => expect(putSpy).toHaveBeenCalledTimes(1));
    expect(putSpy.mock.calls[0]?.[1]).toEqual({
      fields: [
        {
          type: "SINGLE_CHOICE",
          fieldId: expect.stringMatching(/^[0-9a-f]{24}$/),
          label: "Experience",
          helpText: "",
          required: false,
          options: ["Beginner", "Advanced"],
        },
        {
          type: "SHORT_TEXT",
          fieldId: expect.stringMatching(/^[0-9a-f]{24}$/),
          label: "Team name",
          helpText: "",
          required: false,
          maxLength: 100,
        },
      ],
    });
  });

  it("explains that the form is locked after the first Seat and offers no save action", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(view({ registrationFormLocked: true, enrolledCount: 1 })),
    );

    renderPage();

    expect(await screen.findByText(/locked because a student has already registered/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save registration form" })).not.toBeInTheDocument();
  });

  it("adds and edits every type-specific constraint, then removes and reorders fields", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(
        withFields([
          {
            type: "LONG_TEXT",
            fieldId: "long-1",
            label: "Project idea",
            helpText: "",
            required: false,
            maxLength: 200,
          },
          {
            type: "MULTIPLE_CHOICE",
            fieldId: "multi-1",
            label: "Topics",
            helpText: "",
            required: false,
            options: ["AI"],
          },
          {
            type: "NUMBER",
            fieldId: "number-1",
            label: "Team size",
            helpText: "",
            required: false,
            minimum: null,
            maximum: null,
          },
        ]),
      ),
    );
    const putSpy = vi.spyOn(httpClient, "put").mockResolvedValue(axiosResponse(view()));

    renderPage();
    const cards = await screen.findAllByRole("group", { name: /field \d/i });
    await user.type(within(cards[0]!).getByLabelText("Help text"), "Give us the details");
    await user.click(within(cards[0]!).getByLabelText("Required"));
    fireEvent.change(within(cards[0]!).getByLabelText("Maximum length"), { target: { value: "500" } });
    fireEvent.change(within(cards[1]!).getByLabelText("Options, one per line"), {
      target: { value: "AI\nRobotics" },
    });
    fireEvent.change(within(cards[2]!).getByLabelText("Minimum"), { target: { value: "1" } });
    fireEvent.change(within(cards[2]!).getByLabelText("Maximum"), { target: { value: "5" } });
    await user.click(within(cards[0]!).getByRole("button", { name: "Move down" }));
    const reordered = screen.getAllByRole("group", { name: /field \d/i });
    await user.click(within(reordered[0]!).getByRole("button", { name: "Remove" }));
    await user.click(screen.getByRole("button", { name: "Save registration form" }));

    await waitFor(() => expect(putSpy).toHaveBeenCalledTimes(1));
    expect(putSpy.mock.calls[0]?.[1]).toEqual({
      fields: [
        {
          type: "LONG_TEXT",
          fieldId: "long-1",
          label: "Project idea",
          helpText: "Give us the details",
          required: true,
          maxLength: 500,
        },
        {
          type: "NUMBER",
          fieldId: "number-1",
          label: "Team size",
          helpText: "",
          required: false,
          minimum: 1,
          maximum: 5,
        },
      ],
    });
  });

  it("constructs long-text, multiple-choice and number fields", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(view()));

    renderPage();
    const typeSelect = await screen.findByLabelText("Field type");
    for (const type of ["Long text", "Multiple choice", "Number"]) {
      await user.selectOptions(typeSelect, type);
      await user.click(screen.getByRole("button", { name: "Add field" }));
    }

    expect(screen.getAllByRole("group", { name: /field \d/i })).toHaveLength(3);
    expect(screen.getByLabelText("Maximum length")).toHaveValue(1_000);
    expect(screen.getByLabelText("Options, one per line")).toHaveValue("");
    expect(screen.getByLabelText("Minimum")).toHaveValue(null);
  });

  it.each([
    {
      name: "a blank label",
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "short-1",
          label: "",
          helpText: "",
          required: false,
          maxLength: 100,
        },
      ],
      message: "Every field needs a label.",
    },
    {
      name: "duplicate ids",
      fields: [
        {
          type: "SHORT_TEXT" as const,
          fieldId: "same",
          label: "One",
          helpText: "",
          required: false,
          maxLength: 100,
        },
        {
          type: "LONG_TEXT" as const,
          fieldId: "same",
          label: "Two",
          helpText: "",
          required: false,
          maxLength: 100,
        },
      ],
      message: "Every field needs a unique id.",
    },
    {
      name: "an invalid text limit",
      fields: [
        {
          type: "LONG_TEXT" as const,
          fieldId: "long-1",
          label: "Idea",
          helpText: "",
          required: false,
          maxLength: 0,
        },
      ],
      message: "Text limits must be at least 1.",
    },
    {
      name: "an empty choice list",
      fields: [
        {
          type: "MULTIPLE_CHOICE" as const,
          fieldId: "topics",
          label: "Topics",
          helpText: "",
          required: false,
          options: [],
        },
      ],
      message: "Choice fields need at least one option and no duplicates.",
    },
    {
      name: "an inverted number range",
      fields: [
        {
          type: "NUMBER" as const,
          fieldId: "score",
          label: "Score",
          helpText: "",
          required: false,
          minimum: 10,
          maximum: 1,
        },
      ],
      message: "A number field's minimum cannot exceed its maximum.",
    },
  ])("refuses $name before sending it", async ({ fields, message }) => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockResolvedValue(axiosResponse(withFields(fields)));
    const putSpy = vi.spyOn(httpClient, "put");

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Save registration form" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(putSpy).not.toHaveBeenCalled();
  });

  it("renders the current locked questions", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue(
      axiosResponse(
        view({
          registrationFormLocked: true,
          registrationForm: {
            fields: [
              {
                type: "SHORT_TEXT",
                fieldId: "name",
                label: "Preferred name",
                helpText: "",
                required: false,
                maxLength: 80,
              },
            ],
          },
        }),
      ),
    );

    renderPage();

    expect(await screen.findByText("Preferred name")).toBeInTheDocument();
  });

  it("refreshes into a clear locked view when the first Registration wins the save race", async () => {
    const getSpy = vi.spyOn(httpClient, "get")
      .mockResolvedValueOnce(axiosResponse(view()))
      .mockResolvedValue(axiosResponse(view({ registrationFormLocked: true, enrolledCount: 1 })));
    vi.spyOn(httpClient, "put").mockRejectedValue(
      new ApiError({ code: "FORM_LOCKED", status: 409, title: "Conflict", detail: "locked" }),
    );
    const user = userEvent.setup();

    renderPage();
    await user.click(await screen.findByRole("button", { name: "Save registration form" }));

    expect(await screen.findByText(/locked because a student has already registered/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save registration form" })).not.toBeInTheDocument();
    expect(getSpy).toHaveBeenCalledTimes(2);
  });

  it("shows a load failure", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "NOT_FOUND", status: 404, title: "Not Found", detail: "missing" }),
    );

    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent("NOT_FOUND");
  });
});
