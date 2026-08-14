import { createBrowserRouter } from "react-router";
import { OfficerDoorPage } from "../features/checkin/components/OfficerDoorPage";
import { StudentCheckInPage } from "../features/checkin/components/StudentCheckInPage";
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
        // One route for both of the dashboard's views: which one the caller gets is decided by their
        // grants on the server, not by the URL they typed. See docs/adr/09-define-attendance-dashboard.md.
        //
        // Loaded on demand, unlike every other route: ECharts is by far the largest dependency in the
        // app, and the Students who never open a dashboard should not download a charting library to
        // register for a talk.
        path: "/dashboard",
        lazy: async () => ({
          Component: (await import("../features/dashboard/components/DashboardPage")).DashboardPage,
        }),
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
        // Where the door's QR code points. Behind RequireAuth like every other route: a Student who
        // scans while signed out signs in first and then scans the screen again, which costs them one
        // rotation and keeps the rule that identity always comes from an established session.
        path: "/checkin/:eventId",
        element: <StudentCheckInPage />,
      },
      {
        path: "/officer/events/:eventId/door",
        element: <OfficerDoorPage />,
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
