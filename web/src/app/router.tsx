import { createBrowserRouter } from "react-router";
import { EventsBrowsePage } from "../features/events/components/EventsBrowsePage";
import { EventRegistrationPage } from "../features/registration/components/EventRegistrationPage";
import { MyEventsPage } from "../features/registration/components/MyEventsPage";
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
      {
        path: "/events",
        element: <EventsBrowsePage />,
      },
      {
        path: "/events/mine",
        element: <MyEventsPage />,
      },
      {
        path: "/events/:eventId",
        element: <EventRegistrationPage />,
      },
    ],
  },
]);
