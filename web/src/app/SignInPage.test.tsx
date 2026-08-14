import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../lib/apiError";
import { httpClient } from "../lib/httpClient";
import { accessibilityViolations } from "../testSupport/accessibility";
import { SignInPage } from "./SignInPage";

const UNAUTHENTICATED = new ApiError({
  code: "UNAUTHENTICATED",
  status: 401,
  title: "Unauthenticated",
  detail: "no session",
});

const SIGNED_IN_ACTOR = {
  accountId: "account-1",
  email: "officer@demo.campushub",
  displayName: "Demo Officer",
  systemRole: "STUDENT",
  officerClubIds: [],
};

function renderSignInPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/sign-in"]}>
        <Routes>
          <Route path="/sign-in" element={<SignInPage />} />
          <Route path="/" element={<p>Home page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("SignInPage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("submits the form and navigates home once the session is established", async () => {
    const user = userEvent.setup();
    const getSpy = vi.spyOn(httpClient, "get").mockRejectedValue(UNAUTHENTICATED);
    const postSpy = vi.spyOn(httpClient, "post").mockResolvedValue({ data: undefined });

    renderSignInPage();

    await user.type(await screen.findByLabelText(/email/i), "officer@demo.campushub");
    await user.type(screen.getByLabelText(/password/i), "123456");
    getSpy.mockResolvedValue({ data: SIGNED_IN_ACTOR });

    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(postSpy).toHaveBeenCalledTimes(1);
    const [path, body] = postSpy.mock.calls[0] ?? [];
    expect(path).toBe("/auth/login");
    expect(body).toBeInstanceOf(URLSearchParams);
    expect(await screen.findByText("Home page")).toBeInTheDocument();
  });

  it("shows an error message when the credentials are rejected", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockRejectedValue(UNAUTHENTICATED);
    vi.spyOn(httpClient, "post").mockRejectedValue(
      new ApiError({ code: "INVALID_CREDENTIALS", status: 401, title: "Invalid Credentials", detail: "bad" }),
    );

    renderSignInPage();

    await user.type(await screen.findByLabelText(/email/i), "officer@demo.campushub");
    await user.type(screen.getByLabelText(/password/i), "wrong-password");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/incorrect email or password/i);
  });

  it("shows a generic error message for a failure that is not invalid credentials", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockRejectedValue(UNAUTHENTICATED);
    vi.spyOn(httpClient, "post").mockRejectedValue(
      new ApiError({ code: "NETWORK_ERROR", status: 0, title: "Network Error", detail: "offline" }),
    );

    renderSignInPage();

    await user.type(await screen.findByLabelText(/email/i), "officer@demo.campushub");
    await user.type(screen.getByLabelText(/password/i), "123456");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/something went wrong/i);
  });

  it("redirects home immediately when already signed in", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue({ data: SIGNED_IN_ACTOR });

    renderSignInPage();

    expect(await screen.findByText("Home page")).toBeInTheDocument();
  });

  // The first surface a Student ever meets, and the one they meet on a phone.
  it("has no accessibility violations, sitting idle or showing a refusal", async () => {
    const user = userEvent.setup();
    vi.spyOn(httpClient, "get").mockRejectedValue(UNAUTHENTICATED);
    vi.spyOn(httpClient, "post").mockRejectedValue(
      new ApiError({ code: "INVALID_CREDENTIALS", status: 401, title: "Unauthenticated", detail: "no" }),
    );

    const { container } = renderSignInPage();
    expect(await accessibilityViolations(container)).toEqual([]);

    await user.type(await screen.findByLabelText(/email/i), "student@demo.campushub");
    await user.type(screen.getByLabelText(/password/i), "wrong");
    await user.click(screen.getByRole("button", { name: /sign in/i }));

    await screen.findByRole("alert");
    expect(await accessibilityViolations(container)).toEqual([]);
  });
});
