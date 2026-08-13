import { QueryClient } from "@tanstack/react-query";

/** The one QueryClient for the app — defaults set once, here, not per-call. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});
