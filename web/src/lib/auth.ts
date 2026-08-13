import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "./apiError";
import { httpClient } from "./httpClient";

export type SystemRole = "STUDENT" | "UNIVERSITY_ADMIN";

export interface CurrentActor {
  accountId: string;
  email: string;
  displayName: string;
  systemRole: SystemRole;
  officerClubIds: string[];
}

interface LoginCredentials {
  email: string;
  password: string;
}

const CURRENT_ACTOR_QUERY_KEY = ["auth", "me"];

async function fetchCurrentActor(): Promise<CurrentActor> {
  const response = await httpClient.get<CurrentActor>("/auth/me");
  return response.data;
}

/**
 * Who is signed in, or an ApiError with code UNAUTHENTICATED when nobody is. This is how the app
 * knows whether to show the sign-in page — see RequireAuth.
 */
export function useCurrentActor() {
  return useQuery<CurrentActor, ApiError>({
    queryKey: CURRENT_ACTOR_QUERY_KEY,
    queryFn: fetchCurrentActor,
    retry: false,
  });
}

async function login(credentials: LoginCredentials): Promise<void> {
  await httpClient.post(
    "/auth/login",
    new URLSearchParams({ email: credentials.email, password: credentials.password }),
  );
}

/** On success, invalidates useCurrentActor so the app re-learns who is now signed in. */
export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, LoginCredentials>({
    mutationFn: login,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CURRENT_ACTOR_QUERY_KEY }),
  });
}

async function logout(): Promise<void> {
  await httpClient.post("/auth/logout");
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation<void, ApiError, void>({
    mutationFn: logout,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: CURRENT_ACTOR_QUERY_KEY }),
  });
}
