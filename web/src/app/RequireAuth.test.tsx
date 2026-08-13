import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../lib/apiError";
import { httpClient } from "../lib/httpClient";
import { RequireAuth } from "./RequireAuth";

function renderGuarded() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/"]}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/" element={<p>Protected content</p>} />
          </Route>
          <Route path="/sign-in" element={<p>Sign in page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("RequireAuth", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows a loading state while the session check is pending", () => {
    vi.spyOn(httpClient, "get").mockReturnValue(new Promise(() => {}));

    renderGuarded();

    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("renders the protected route once a session is established", async () => {
    vi.spyOn(httpClient, "get").mockResolvedValue({
      data: {
        accountId: "account-1",
        email: "student@demo.campushub",
        displayName: "Demo Student",
        systemRole: "STUDENT",
        officerClubIds: [],
      },
    });

    renderGuarded();

    expect(await screen.findByText("Protected content")).toBeInTheDocument();
  });

  it("redirects to /sign-in when nobody is signed in", async () => {
    vi.spyOn(httpClient, "get").mockRejectedValue(
      new ApiError({ code: "UNAUTHENTICATED", status: 401, title: "Unauthenticated", detail: "no session" }),
    );

    renderGuarded();

    expect(await screen.findByText("Sign in page")).toBeInTheDocument();
  });
});
