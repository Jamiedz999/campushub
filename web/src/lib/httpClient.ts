import axios from "axios";
import { normalizeApiError } from "./apiError";

const UNSAFE_METHODS = new Set(["post", "put", "patch", "delete"]);
const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  const value = match?.[1];
  return value === undefined ? null : decodeURIComponent(value);
}

/**
 * The one shared axios instance — same-origin `/api` base, the session
 * cookie, the CSRF header on unsafe requests, and error normalisation.
 * Every feature reads and writes through this instance rather than
 * creating its own.
 */
export const httpClient = axios.create({
  baseURL: "/api",
  withCredentials: true,
});

httpClient.interceptors.request.use((config) => {
  const method = config.method?.toLowerCase();
  if (method !== undefined && UNSAFE_METHODS.has(method)) {
    const token = readCookie(CSRF_COOKIE_NAME);
    if (token !== null) {
      config.headers.set(CSRF_HEADER_NAME, token);
    }
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(normalizeApiError(error)),
);
