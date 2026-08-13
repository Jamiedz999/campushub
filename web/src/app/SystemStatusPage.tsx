import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "../lib/apiError";
import { httpClient } from "../lib/httpClient";

interface SystemStatus {
  status: string;
  version: string;
  buildTime: string;
  serverTime: string;
}

async function fetchSystemStatus(): Promise<SystemStatus> {
  const response = await httpClient.get<SystemStatus>("/system");
  return response.data;
}

/** Proves the API contract end to end: fetches GET /api/system and renders it. */
export function SystemStatusPage() {
  const query = useQuery<SystemStatus, ApiError>({
    queryKey: ["system-status"],
    queryFn: fetchSystemStatus,
  });

  if (query.status === "pending") {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p role="status">Loading system status…</p>
      </main>
    );
  }

  if (query.status === "error") {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p role="alert">Could not load system status ({query.error.code}).</p>
      </main>
    );
  }

  const system = query.data;

  return (
    <main className="flex min-h-screen items-center justify-center">
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        <dt className="font-semibold">Status</dt>
        <dd>{system.status}</dd>
        <dt className="font-semibold">Version</dt>
        <dd>{system.version}</dd>
        <dt className="font-semibold">Build time</dt>
        <dd>{system.buildTime}</dd>
        <dt className="font-semibold">Server time</dt>
        <dd>{system.serverTime}</dd>
      </dl>
    </main>
  );
}
