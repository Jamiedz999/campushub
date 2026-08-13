import { createBrowserRouter } from "react-router";
import { RequireAuth } from "./RequireAuth";
import { SignInPage } from "./SignInPage";
import { SystemStatusPage } from "./SystemStatusPage";

export const router = createBrowserRouter([
  {
    path: "/sign-in",
    element: <SignInPage />,
  },
  {
    element: <RequireAuth />,
    children: [
      {
        path: "/",
        element: <SystemStatusPage />,
      },
    ],
  },
]);
