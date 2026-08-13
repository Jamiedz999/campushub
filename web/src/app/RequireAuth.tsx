import { Navigate, Outlet } from "react-router";
import { LoadingScreen } from "../components/LoadingScreen";
import { useCurrentActor } from "../lib/auth";

/** Route guard: redirects to /sign-in unless a session is already established. */
export function RequireAuth() {
  const currentActor = useCurrentActor();

  if (currentActor.status === "pending") {
    return <LoadingScreen />;
  }

  if (currentActor.status === "error") {
    return <Navigate to="/sign-in" replace />;
  }

  return <Outlet />;
}
