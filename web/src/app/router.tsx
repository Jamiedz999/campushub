import { createBrowserRouter } from "react-router";
import { SystemStatusPage } from "./SystemStatusPage";

export const router = createBrowserRouter([
  {
    path: "/",
    element: <SystemStatusPage />,
  },
]);
