import { createBrowserRouter } from "react-router";
import { EventsBrowsePage } from "../features/events/components/EventsBrowsePage";
import { OfficerCapacityPage } from "../features/events/components/OfficerCapacityPage";
import { OfficerRegistrationFormPage } from "../features/events/components/OfficerRegistrationFormPage";
import { EventRegistrationPage } from "../features/registration/components/EventRegistrationPage";
import { MyEventsPage } from "../features/registration/components/MyEventsPage";
import { OfficerRegistrationAnswersPage } from "../features/registration/components/OfficerRegistrationAnswersPage";
import { OfficerVenuePage } from "../features/venues/components/OfficerVenuePage";
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
      {
        path: "/officer/events/:eventId/venue",
        element: <OfficerVenuePage />,
      },
      {
        path: "/officer/events/:eventId/capacity",
        element: <OfficerCapacityPage />,
      },
      {
        path: "/officer/events/:eventId/registration-form",
        element: <OfficerRegistrationFormPage />,
      },
      {
        path: "/officer/events/:eventId/registration-answers",
        element: <OfficerRegistrationAnswersPage />,
      },
    ],
  },
]);
